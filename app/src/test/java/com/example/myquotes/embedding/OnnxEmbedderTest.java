package com.example.myquotes.embedding;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Smoke tests on the real models; skipped when they aren't downloaded. */
public class OnnxEmbedderTest {

    private static final TextEmbeddingModel[] MODELS = {
            TextEmbeddingModel.MULTILINGUAL_E5_SMALL, TextEmbeddingModel.PARAPHRASE_MULTILINGUAL_MINILM_L12};

    @Test
    public void returnsNormalized384DimensionalVectors() throws Exception {
        for (TextEmbeddingModel model : MODELS) {
            try (OnnxEmbedder embedder = EmbeddingModelFiles.embedder(model)) {
                float[] vector = embedder.embed("The only thing we have to fear is fear itself.");

                assertEquals(384, vector.length);
                assertEquals(384, embedder.dimensions());
                assertEquals(model.id(), 1.0, dot(vector, vector), 1e-4);
            }
        }
    }

    @Test
    public void paraphrasesAreCloserThanUnrelatedQuotes() throws Exception {
        String[][] english = {
                {"Love conquers all things.", "Love overcomes everything."},
                {"The weather report says it will rain tomorrow.", "Love conquers all things."}};
        String[][] german = {
                {"Die Liebe besiegt alles.", "Liebe überwindet jedes Hindernis."},
                {"Morgen soll es laut Wetterbericht regnen.", "Die Liebe besiegt alles."}};
        for (TextEmbeddingModel model : MODELS) {
            try (OnnxEmbedder embedder = EmbeddingModelFiles.embedder(model)) {
                for (String[][] pairs : new String[][][]{english, german}) {
                    double paraphrase = cosine(embedder, pairs[0][0], pairs[0][1]);
                    double unrelated = cosine(embedder, pairs[1][0], pairs[1][1]);
                    assertTrue(model.id() + ": paraphrase " + paraphrase + " vs unrelated " + unrelated
                            + " for " + pairs[0][0], paraphrase > unrelated);
                }
            }
        }
    }

    @Test
    public void textLongerThanMaxTokensIsCutOffNotRejected() throws Exception {
        StringBuilder sentence = new StringBuilder();
        for (int i = 0; i < 100; i++) sentence.append("It was the best of times, it was the worst of times. ");
        String longText = sentence.toString();
        for (TextEmbeddingModel model : MODELS) {
            try (OnnxEmbedder embedder = EmbeddingModelFiles.embedder(model)) {
                float[] vector = embedder.embed(longText);
                float[] longer = embedder.embed(longText + "Something entirely different at the very end.");

                assertEquals(384, vector.length);
                assertArrayEquals("only the first 128 tokens count", vector, longer, 0f);
            }
        }
    }

    @Test
    public void modelIdIncludesThePinnedRevision() {
        assertEquals("multilingual-e5-small@761b726d", TextEmbeddingModel.MULTILINGUAL_E5_SMALL.id());
        assertEquals("paraphrase-multilingual-MiniLM-L12-v2@2c4055b1",
                TextEmbeddingModel.PARAPHRASE_MULTILINGUAL_MINILM_L12.id());
    }

    @Test
    public void meanPoolingSkipsMaskedTokensAndNormalizes() {
        float[][] tokens = {{1, 0}, {3, 4}, {100, 100}};
        long[] mask = {1, 1, 0};

        float[] pooled = OnnxEmbedder.meanPoolAndNormalize(tokens, mask);

        // mean (2, 2) → (1/√2, 1/√2)
        assertEquals(Math.sqrt(0.5), pooled[0], 1e-6);
        assertEquals(Math.sqrt(0.5), pooled[1], 1e-6);
    }

    private static double cosine(Embedder embedder, String a, String b) {
        return dot(embedder.embed(a), embedder.embed(b));
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }
}
