package com.example.myquotes.embedding;

/**
 * Turns a quote's text into a vector (#60). Vectors are L2-normalized, so the cosine of two of
 * them is their dot product. Pure Java callers depend only on this; tests can use a fake.
 */
public interface Embedder {

    float[] embed(String text);

    /** The model and its pinned revision; a change means stored vectors are stale. */
    String modelId();

    int dimensions();
}
