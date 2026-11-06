package com.example.myquotes.embedding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The XLM-R SentencePiece unigram tokenizer, in pure Java, read from a Hugging Face
 * {@code tokenizer.json} (#60). Matches the official {@code tokenizers} library on the golden
 * sentences in the test resources.
 *
 * <p>Pipeline: normalizers (Precompiled charsmap, Replace), pre-tokenizers (WhitespaceSplit,
 * Metaspace), then per piece the Viterbi best segmentation over the vocabulary scores, with
 * unknown characters scored {@code minScore - 10} and runs of them fused into one {@code <unk>},
 * then {@code <s> … </s>}. Only the components these models use are supported; anything else
 * fails at load time. Literal special tokens in the text (like "&lt;mask&gt;") are not matched as
 * special tokens, which quotes don't need.
 */
public final class UnigramTokenizer {

    private static final double UNK_PENALTY = 10.0;
    private static final char METASPACE = '▁';

    private interface Normalizer {
        String apply(String text);
    }

    private interface PreTokenizer {
        List<String> apply(String piece);
    }

    private final List<Normalizer> normalizers;
    private final List<PreTokenizer> preTokenizers;
    private final Map<String, Integer> ids;
    private final double[] scores;
    private final int maxPieceChars;
    private final int unkId;
    private final double unkScore;
    private final int bosId;
    private final int eosId;

    private UnigramTokenizer(List<Normalizer> normalizers, List<PreTokenizer> preTokenizers,
                             Map<String, Integer> ids, double[] scores, int maxPieceChars,
                             int unkId, int bosId, int eosId) {
        this.normalizers = normalizers;
        this.preTokenizers = preTokenizers;
        this.ids = ids;
        this.scores = scores;
        this.maxPieceChars = maxPieceChars;
        this.unkId = unkId;
        this.bosId = bosId;
        this.eosId = eosId;
        double min = Double.POSITIVE_INFINITY;
        for (double score : scores) min = Math.min(min, score);
        this.unkScore = min - UNK_PENALTY;
    }

    public static UnigramTokenizer fromJson(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1 << 16];
        int n;
        while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
        try {
            return fromJson(new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
        } catch (JSONException e) {
            throw new IOException("Not a valid tokenizer.json", e);
        }
    }

    static UnigramTokenizer fromJson(JSONObject json) throws JSONException {
        JSONObject model = json.getJSONObject("model");
        if (!"Unigram".equals(model.getString("type"))) {
            throw new IllegalArgumentException("Not a Unigram model: " + model.getString("type"));
        }
        JSONArray vocab = model.getJSONArray("vocab");
        Map<String, Integer> ids = new HashMap<>(vocab.length() * 2);
        double[] scores = new double[vocab.length()];
        int maxPieceChars = 1;
        for (int i = 0; i < vocab.length(); i++) {
            JSONArray entry = vocab.getJSONArray(i);
            String piece = entry.getString(0);
            if (!ids.containsKey(piece)) ids.put(piece, i);
            scores[i] = entry.getDouble(1);
            maxPieceChars = Math.max(maxPieceChars, piece.length());
        }
        int unkId = model.getInt("unk_id");
        return new UnigramTokenizer(
                normalizers(json.optJSONObject("normalizer")),
                preTokenizers(json.optJSONObject("pre_tokenizer")),
                ids, scores, maxPieceChars, unkId, specialId(ids, "<s>"), specialId(ids, "</s>"));
    }

    private static int specialId(Map<String, Integer> ids, String token) {
        Integer id = ids.get(token);
        if (id == null) throw new IllegalArgumentException("Missing special token " + token);
        return id;
    }

    /**
     * Token IDs for {@code text}, wrapped in {@code <s> … </s>} and cut off after
     * {@code maxTokens} IDs in all (the end token is kept). Never rejects long text.
     */
    public int[] encode(String text, int maxTokens) {
        if (maxTokens < 2) throw new IllegalArgumentException("maxTokens must leave room for <s> and </s>");
        List<Integer> pieces = encodePieces(text);
        int kept = Math.min(pieces.size(), maxTokens - 2);
        int[] out = new int[kept + 2];
        out[0] = bosId;
        for (int i = 0; i < kept; i++) out[i + 1] = pieces.get(i);
        out[kept + 1] = eosId;
        return out;
    }

    /** Token IDs for {@code text} with {@code <s> … </s>} and no length limit. */
    public int[] encode(String text) {
        return encode(text, Integer.MAX_VALUE);
    }

    private List<Integer> encodePieces(String text) {
        String normalized = text;
        for (Normalizer normalizer : normalizers) normalized = normalizer.apply(normalized);
        List<String> pieces = Collections.singletonList(normalized);
        for (PreTokenizer preTokenizer : preTokenizers) {
            List<String> next = new ArrayList<>();
            for (String piece : pieces) next.addAll(preTokenizer.apply(piece));
            pieces = next;
        }
        List<Integer> out = new ArrayList<>();
        for (String piece : pieces) viterbi(piece, out);
        return out;
    }

    /** Appends the best segmentation of {@code piece}, like {@code Unigram::encode_optimized}. */
    private void viterbi(String piece, List<Integer> out) {
        int size = piece.length();
        if (size == 0) return;
        double[] best = new double[size + 1];
        int[] startsAt = new int[size + 1];
        int[] idAt = new int[size + 1];
        java.util.Arrays.fill(startsAt, -1);
        startsAt[0] = 0;

        int start = 0;
        while (start < size) {
            double scoreHere = best[start];
            int charLength = Character.charCount(piece.codePointAt(start));
            boolean hasSingle = false;
            int maxEnd = Math.min(size, start + maxPieceChars);
            for (int end = start + 1; end <= maxEnd; end++) {
                Integer id = ids.get(piece.substring(start, end));
                if (id == null) continue;
                double candidate = scores[id] + scoreHere;
                if (startsAt[end] < 0 || candidate > best[end]) {
                    best[end] = candidate;
                    startsAt[end] = start;
                    idAt[end] = id;
                }
                if (end - start == charLength) hasSingle = true;
            }
            if (!hasSingle) {
                int end = start + charLength;
                double candidate = unkScore + scoreHere;
                if (startsAt[end] < 0 || candidate > best[end]) {
                    best[end] = candidate;
                    startsAt[end] = start;
                    idAt[end] = unkId;
                }
            }
            start += charLength;
        }

        List<Integer> reversed = new ArrayList<>();
        boolean inUnk = false;
        for (int end = size; end > 0; end = startsAt[end]) {
            int id = idAt[end];
            if (id == unkId) {
                if (!inUnk) reversed.add(unkId);     // a run of unknowns is one <unk>
                inUnk = true;
            } else {
                reversed.add(id);
                inUnk = false;
            }
        }
        for (int i = reversed.size() - 1; i >= 0; i--) out.add(reversed.get(i));
    }

    // --- tokenizer.json components ---

    private static List<Normalizer> normalizers(JSONObject json) throws JSONException {
        List<Normalizer> out = new ArrayList<>();
        if (json == null) return out;
        String type = json.getString("type");
        switch (type) {
            case "Sequence": {
                JSONArray list = json.getJSONArray("normalizers");
                for (int i = 0; i < list.length(); i++) out.addAll(normalizers(list.getJSONObject(i)));
                return out;
            }
            case "Precompiled": {
                final PrecompiledCharsMap map = new PrecompiledCharsMap(json.getString("precompiled_charsmap"));
                out.add(map::normalize);
                return out;
            }
            case "Replace": {
                final Pattern pattern = pattern(json.getJSONObject("pattern"));
                final String content = json.getString("content");
                out.add(text -> pattern.matcher(text).replaceAll(java.util.regex.Matcher.quoteReplacement(content)));
                return out;
            }
            default:
                throw new IllegalArgumentException("Unsupported normalizer " + type);
        }
    }

    private static Pattern pattern(JSONObject json) throws JSONException {
        if (json.has("Regex")) return Pattern.compile(json.getString("Regex"));
        return Pattern.compile(Pattern.quote(json.getString("String")));
    }

    private static List<PreTokenizer> preTokenizers(JSONObject json) throws JSONException {
        List<PreTokenizer> out = new ArrayList<>();
        if (json == null) return out;
        String type = json.getString("type");
        switch (type) {
            case "Sequence": {
                JSONArray list = json.getJSONArray("pretokenizers");
                for (int i = 0; i < list.length(); i++) out.addAll(preTokenizers(list.getJSONObject(i)));
                return out;
            }
            case "WhitespaceSplit":
                out.add(UnigramTokenizer::splitOnWhitespace);
                return out;
            case "Metaspace": {
                String replacement = json.optString("replacement", String.valueOf(METASPACE));
                if (replacement.length() != 1) throw new IllegalArgumentException("Unsupported Metaspace " + replacement);
                final char metaspace = replacement.charAt(0);
                String scheme = json.has("prepend_scheme")
                        ? json.getString("prepend_scheme")
                        : json.optBoolean("add_prefix_space", true) ? "always" : "never";
                if (!"always".equals(scheme) && !"never".equals(scheme)) {
                    throw new IllegalArgumentException("Unsupported prepend_scheme " + scheme);
                }
                final boolean prepend = "always".equals(scheme);
                final boolean split = json.optBoolean("split", true);
                out.add(piece -> metaspace(piece, metaspace, prepend, split));
                return out;
            }
            default:
                throw new IllegalArgumentException("Unsupported pre-tokenizer " + type);
        }
    }

    /** Splits on Unicode White_Space (Rust's {@code char::is_whitespace}) and drops it. */
    private static List<String> splitOnWhitespace(String piece) {
        List<String> out = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < piece.length(); i++) {
            if (isWhitespace(piece.charAt(i))) {
                if (start >= 0) out.add(piece.substring(start, i));
                start = -1;
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) out.add(piece.substring(start));
        return out;
    }

    private static boolean isWhitespace(char c) {
        return (c >= 0x09 && c <= 0x0D) || c == ' ' || c == 0x85 || c == 0xA0 || c == 0x1680
                || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F
                || c == 0x205F || c == 0x3000;
    }

    /** Spaces become ▁, a ▁ is prepended, and each ▁ starts a new piece. */
    private static List<String> metaspace(String piece, char metaspace, boolean prepend, boolean split) {
        if (piece.isEmpty()) return Collections.emptyList();
        String replaced = piece.replace(' ', metaspace);
        if (prepend && replaced.charAt(0) != metaspace) replaced = metaspace + replaced;
        if (!split) return Collections.singletonList(replaced);
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < replaced.length(); i++) {
            if (replaced.charAt(i) == metaspace) {
                out.add(replaced.substring(start, i));
                start = i;
            }
        }
        out.add(replaced.substring(start));
        return out;
    }
}
