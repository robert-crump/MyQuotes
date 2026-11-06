package com.example.myquotes.embedding;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Java tokenizer against the official Hugging Face tokenizer, on the sentences in
 * {@code embedding/tokenizer-golden.json} (written by {@code tools/generate_tokenizer_golden.py}).
 * Skipped when the models aren't downloaded.
 */
public class UnigramTokenizerGoldenTest {

    @Test
    public void e5MatchesTheOfficialTokenizer() throws Exception {
        assertMatchesGolden(TextEmbeddingModel.MULTILINGUAL_E5_SMALL, "e5");
    }

    @Test
    public void miniLmMatchesTheOfficialTokenizer() throws Exception {
        assertMatchesGolden(TextEmbeddingModel.PARAPHRASE_MULTILINGUAL_MINILM_L12, "minilm");
    }

    @Test
    public void longTextIsCutOffAtMaxTokensKeepingTheEndToken() throws Exception {
        for (TextEmbeddingModel model : new TextEmbeddingModel[]{
                TextEmbeddingModel.MULTILINGUAL_E5_SMALL, TextEmbeddingModel.PARAPHRASE_MULTILINGUAL_MINILM_L12}) {
            UnigramTokenizer tokenizer = EmbeddingModelFiles.tokenizer(model);
            JSONArray cases = golden().getJSONArray("cases");
            String longText = cases.getJSONObject(cases.length() - 1).getString("text");
            int[] full = tokenizer.encode(longText);
            assertTrue(full.length > 128);

            int[] cut = tokenizer.encode(longText, model.maxTokens);

            assertEquals(128, cut.length);
            assertArrayEquals(Arrays.copyOf(full, 127), Arrays.copyOf(cut, 127));
            assertEquals(full[full.length - 1], cut[127]);
        }
    }

    @Test
    public void shortTextIsNotPadded() throws Exception {
        UnigramTokenizer tokenizer = EmbeddingModelFiles.tokenizer(TextEmbeddingModel.MULTILINGUAL_E5_SMALL);
        assertArrayEquals(tokenizer.encode("Hello world"), tokenizer.encode("Hello world", 128));
    }

    private static void assertMatchesGolden(TextEmbeddingModel model, String key) throws Exception {
        UnigramTokenizer tokenizer = EmbeddingModelFiles.tokenizer(model);
        JSONArray cases = golden().getJSONArray("cases");
        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            JSONArray expectedJson = c.getJSONArray(key);
            int[] expected = new int[expectedJson.length()];
            for (int j = 0; j < expected.length; j++) expected[j] = expectedJson.getInt(j);
            int[] actual = tokenizer.encode(c.getString("text"));
            if (!Arrays.equals(expected, actual)) {
                mismatches.add(escape(c.getString("text")) + "\n  expected " + Arrays.toString(expected)
                        + "\n  actual   " + Arrays.toString(actual));
            }
        }
        assertTrue(cases.length() >= 50);
        assertTrue(mismatches.size() + " of " + cases.length() + " differ:\n" + String.join("\n", mismatches),
                mismatches.isEmpty());
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : (text.length() > 80 ? text.substring(0, 80) + "…" : text).toCharArray()) {
            if (c < 0x20 || (c >= 0x7F && c < 0xA0)) out.append("<" + Integer.toHexString(c) + ">");
            else out.append(c);
        }
        return out.toString();
    }

    private static JSONObject golden() throws IOException {
        try (InputStream in = UnigramTokenizerGoldenTest.class.getResourceAsStream("/embedding/tokenizer-golden.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
            return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }
}
