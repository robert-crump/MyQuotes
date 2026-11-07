package com.example.myquotes.embedding;

import com.example.myquotes.Quote;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quote vectors for the hashtag suggestion eval (#61), computed with the #60 embedder and cached
 * in {@code <cacheDir>/<model id>.tsv} by the SHA-256 of the text, so parameter sweeps don't
 * re-embed. The cache holds no quote text, only hashes and vectors. Skips the calling test when
 * the model files are missing or ONNX Runtime can't run on this JVM.
 * <p>
 * Chunked (an experiment of #61): a text longer than the model's input cap is split at sentence
 * ends into pieces that fit (a sentence too long on its own at word boundaries), each piece is
 * embedded, and the vectors are averaged, weighted by token count, and normalized again. Text that
 * fits gets the same vector as unchunked.
 */
public final class QuoteVectors {

    private QuoteVectors() {
    }

    /** Quote id to normalized vector of its text, for every quote with an id. */
    public static Map<Integer, float[]> load(TextEmbeddingModel model, List<Quote> quotes, File cacheDir)
            throws IOException {
        return load(model, quotes, cacheDir, false);
    }

    /** {@link #load(TextEmbeddingModel, List, File)}, long texts chunked when {@code chunked}. */
    public static Map<Integer, float[]> load(TextEmbeddingModel model, List<Quote> quotes, File cacheDir,
                                             boolean chunked) throws IOException {
        File cacheFile = new File(cacheDir, model.id().replace('@', '_') + (chunked ? "-chunked" : "") + ".tsv");
        UnigramTokenizer tokenizer = chunked ? EmbeddingModelFiles.tokenizer(model) : null;
        Map<String, float[]> cache = read(cacheFile);
        int before = cache.size();
        OnnxEmbedder embedder = null;
        Map<Integer, float[]> vectors = new HashMap<>();
        try {
            for (Quote quote : quotes) {
                if (quote.getId() == null) continue;
                String hash = sha256(quote.getQuoteText());
                float[] vector = cache.get(hash);
                if (vector == null) {
                    if (embedder == null) embedder = EmbeddingModelFiles.embedder(model);
                    vector = chunked ? embedChunked(embedder, tokenizer, model, quote.getQuoteText())
                            : embedder.embed(quote.getQuoteText());
                    cache.put(hash, vector);
                }
                vectors.put(quote.getId(), vector);
            }
        } finally {
            if (embedder != null) {
                try {
                    embedder.close();
                } catch (Exception ignored) {
                    // Native memory is freed when the JVM exits anyway.
                }
            }
        }
        if (cache.size() != before) write(cacheFile, cache);
        return vectors;
    }

    /**
     * Each of {@code texts} (tag names, #64) to its normalized vector, through the same cache as
     * the quotes (never chunked).
     */
    public static Map<String, float[]> loadTexts(TextEmbeddingModel model, Iterable<String> texts, File cacheDir)
            throws IOException {
        File cacheFile = new File(cacheDir, model.id().replace('@', '_') + ".tsv");
        Map<String, float[]> cache = read(cacheFile);
        int before = cache.size();
        OnnxEmbedder embedder = null;
        Map<String, float[]> vectors = new HashMap<>();
        try {
            for (String text : texts) {
                String hash = sha256(text);
                float[] vector = cache.get(hash);
                if (vector == null) {
                    if (embedder == null) embedder = EmbeddingModelFiles.embedder(model);
                    vector = embedder.embed(text);
                    cache.put(hash, vector);
                }
                vectors.put(text, vector);
            }
        } finally {
            if (embedder != null) {
                try {
                    embedder.close();
                } catch (Exception ignored) {
                    // Native memory is freed when the JVM exits anyway.
                }
            }
        }
        if (cache.size() != before) write(cacheFile, cache);
        return vectors;
    }

    /** How many quotes are longer than the model's input cap (prefix and special tokens included). */
    public static int cutOff(TextEmbeddingModel model, List<Quote> quotes) throws IOException {
        UnigramTokenizer tokenizer = EmbeddingModelFiles.tokenizer(model);
        int count = 0;
        for (Quote quote : quotes) {
            if (tokenizer.encode(model.prefix + quote.getQuoteText()).length > model.maxTokens) count++;
        }
        return count;
    }

    /** The token-weighted mean of the chunks' vectors, normalized. */
    private static float[] embedChunked(Embedder embedder, UnigramTokenizer tokenizer, TextEmbeddingModel model,
                                        String text) {
        List<String> chunks = chunks(tokenizer, model, text);
        if (chunks.size() == 1) return embedder.embed(text);
        double[] sum = new double[embedder.dimensions()];
        for (String chunk : chunks) {
            float[] vector = embedder.embed(chunk);
            int weight = tokens(tokenizer, model, chunk);
            for (int i = 0; i < sum.length; i++) sum[i] += weight * vector[i];
        }
        double norm = 0;
        for (double v : sum) norm += v * v;
        norm = Math.sqrt(norm);
        float[] out = new float[sum.length];
        for (int i = 0; i < out.length; i++) out[i] = (float) (norm > 0 ? sum[i] / norm : 0);
        return out;
    }

    /** {@code text} packed into pieces that fit the model's cap, split at sentence ends (else words). */
    static List<String> chunks(UnigramTokenizer tokenizer, TextEmbeddingModel model, String text) {
        List<String> chunks = new ArrayList<>();
        if (tokens(tokenizer, model, text) <= model.maxTokens) {
            chunks.add(text);
            return chunks;
        }
        List<String> units = new ArrayList<>();
        for (String sentence : text.trim().split("(?<=[.!?\u2026;:])\\s+|\\s*\\n\\s*")) {
            if (sentence.isEmpty()) continue;
            if (tokens(tokenizer, model, sentence) <= model.maxTokens) {
                units.add(sentence);
            } else {
                for (String word : sentence.split("\\s+")) if (!word.isEmpty()) units.add(word);
            }
        }
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            String joined = current.length() == 0 ? unit : current + " " + unit;
            if (current.length() > 0 && tokens(tokenizer, model, joined) > model.maxTokens) {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(unit);
            } else {
                current.setLength(0);
                current.append(joined);
            }
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    private static int tokens(UnigramTokenizer tokenizer, TextEmbeddingModel model, String text) {
        return tokenizer.encode(model.prefix + text).length;
    }

    private static Map<String, float[]> read(File file) throws IOException {
        Map<String, float[]> cache = new HashMap<>();
        if (!file.isFile()) return cache;
        try (BufferedReader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String[] parts = line.substring(tab + 1).split(",");
                float[] vector = new float[parts.length];
                for (int i = 0; i < parts.length; i++) vector[i] = Float.parseFloat(parts[i]);
                cache.put(line.substring(0, tab), vector);
            }
        }
        return cache;
    }

    private static void write(File file, Map<String, float[]> cache) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, float[]> entry : cache.entrySet()) {
                StringBuilder line = new StringBuilder(entry.getKey()).append('\t');
                float[] vector = entry.getValue();
                for (int i = 0; i < vector.length; i++) {
                    if (i > 0) line.append(',');
                    line.append(Float.toString(vector[i]));
                }
                out.write(line.append('\n').toString());
            }
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
