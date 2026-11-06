package com.example.myquotes.embedding;

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds the models fetched by {@code ./gradlew downloadEmbeddingModels} (system property
 * {@code myquotes.embeddingModels}) and skips the calling test when they are missing.
 */
final class EmbeddingModelFiles {

    private static final Map<TextEmbeddingModel, UnigramTokenizer> TOKENIZERS = new HashMap<>();

    private EmbeddingModelFiles() {
    }

    static File file(TextEmbeddingModel model, String fileName) {
        String dir = System.getProperty("myquotes.embeddingModels");
        assumeTrue("myquotes.embeddingModels is not set; run through Gradle", dir != null);
        File file = new File(model.directory(new File(dir)), fileName);
        assumeTrue(file + " is missing; run ./gradlew downloadEmbeddingModels", file.isFile());
        return file;
    }

    static synchronized UnigramTokenizer tokenizer(TextEmbeddingModel model) throws IOException {
        UnigramTokenizer tokenizer = TOKENIZERS.get(model);
        if (tokenizer == null) {
            try (InputStream in = new FileInputStream(file(model, TextEmbeddingModel.TOKENIZER_FILE))) {
                tokenizer = UnigramTokenizer.fromJson(in);
            }
            TOKENIZERS.put(model, tokenizer);
        }
        return tokenizer;
    }

    /** Also skipped on a JVM whose MSVC runtime would crash ONNX Runtime (see app/build.gradle). */
    static OnnxEmbedder embedder(TextEmbeddingModel model) throws IOException {
        String skipReason = System.getProperty("myquotes.onnxSkipReason");
        assumeTrue(skipReason, skipReason == null);
        File modelFile = file(model, TextEmbeddingModel.MODEL_FILE);
        return new OnnxEmbedder(model, modelFile, tokenizer(model));
    }
}
