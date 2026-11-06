package com.example.myquotes.embedding;

import java.io.File;

/**
 * One candidate text embedding model of the semantic search bake-off (#58, #60): which files to
 * load and how to build its input. Both candidates are int8-quantized ONNX exports with the XLM-R
 * unigram tokenizer, mean pooling and 384 dimensions.
 *
 * <p>The files are not committed; {@code ./gradlew downloadEmbeddingModels} fetches them into
 * {@code <models dir>/<name>/<revision>/}. Keep {@link #name} and {@link #revision} in sync with
 * that task in {@code app/build.gradle}.
 */
public final class TextEmbeddingModel {

    public static final TextEmbeddingModel MULTILINGUAL_E5_SMALL = new TextEmbeddingModel(
            "multilingual-e5-small", "761b726dd34fb83930e26aab4e9ac3899aa1fa78", "query: ", 128, 384);

    public static final TextEmbeddingModel PARAPHRASE_MULTILINGUAL_MINILM_L12 = new TextEmbeddingModel(
            "paraphrase-multilingual-MiniLM-L12-v2", "2c4055b12046f11709e9df2c122e59ffbdc2f900", "", 128, 384);

    public static final String MODEL_FILE = "model_quantized.onnx";
    public static final String TOKENIZER_FILE = "tokenizer.json";

    public final String name;
    /** The Hugging Face commit the files are pinned to. */
    public final String revision;
    /** Put before the quote text ("query: " for e5, which was trained with it). */
    public final String prefix;
    /** Input length cap, special tokens included; longer text is cut off. */
    public final int maxTokens;
    public final int dimensions;

    private TextEmbeddingModel(String name, String revision, String prefix, int maxTokens, int dimensions) {
        this.name = name;
        this.revision = revision;
        this.prefix = prefix;
        this.maxTokens = maxTokens;
        this.dimensions = dimensions;
    }

    /** Name and pinned revision, e.g. "multilingual-e5-small@761b726d". */
    public String id() {
        return name + "@" + revision.substring(0, 8);
    }

    public File directory(File modelsDir) {
        return new File(new File(modelsDir, name), revision);
    }

    @Override
    public String toString() {
        return id();
    }
}
