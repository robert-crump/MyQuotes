package com.example.myquotes.embedding;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * An {@link Embedder} that runs a {@link TextEmbeddingModel} with ONNX Runtime (#60): the
 * model's prefix plus the text, cut off at its max tokens, then mean pooling over the attention
 * mask and L2 normalization. The session is created on first use and is safe to share between
 * threads. Close it to free the native memory.
 */
public final class OnnxEmbedder implements Embedder, AutoCloseable {

    /** Where the model's bytes come from: a file (tests), or a memory-mapped APK asset (the app, #62). */
    public interface ModelSource {
        OrtSession open(OrtEnvironment env, OrtSession.SessionOptions options) throws OrtException;
    }

    private final TextEmbeddingModel model;
    private final ModelSource source;
    private final UnigramTokenizer tokenizer;
    private OrtSession session;

    public OnnxEmbedder(TextEmbeddingModel model, File modelFile, UnigramTokenizer tokenizer) {
        this(model, (env, options) -> env.createSession(modelFile.getAbsolutePath(), options), tokenizer);
    }

    /** {@code modelBytes} is a direct buffer, e.g. the uncompressed asset mapped from the APK. */
    public OnnxEmbedder(TextEmbeddingModel model, ByteBuffer modelBytes, UnigramTokenizer tokenizer) {
        this(model, (env, options) -> env.createSession(modelBytes, options), tokenizer);
    }

    public OnnxEmbedder(TextEmbeddingModel model, ModelSource source, UnigramTokenizer tokenizer) {
        this.model = model;
        this.source = source;
        this.tokenizer = tokenizer;
    }

    @Override
    public String modelId() {
        return model.id();
    }

    @Override
    public int dimensions() {
        return model.dimensions;
    }

    @Override
    public float[] embed(String text) {
        int[] ids = tokenizer.encode(model.prefix + text, model.maxTokens);
        long[][] inputIds = new long[1][ids.length];
        long[][] mask = new long[1][ids.length];
        for (int i = 0; i < ids.length; i++) {
            inputIds[0][i] = ids[i];
            mask[0][i] = 1;
        }
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            OrtSession session = session();
            Map<String, OnnxTensor> inputs = new HashMap<>();
            try {
                inputs.put("input_ids", OnnxTensor.createTensor(env, inputIds));
                inputs.put("attention_mask", OnnxTensor.createTensor(env, mask));
                if (session.getInputNames().contains("token_type_ids")) {
                    inputs.put("token_type_ids", OnnxTensor.createTensor(env, new long[1][ids.length]));
                }
                try (OrtSession.Result result = session.run(inputs)) {
                    float[][][] hidden = (float[][][]) result.get(0).getValue();
                    return meanPoolAndNormalize(hidden[0], mask[0]);
                }
            } finally {
                for (OnnxTensor tensor : inputs.values()) tensor.close();
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Embedding with " + model.id() + " failed", e);
        }
    }

    static float[] meanPoolAndNormalize(float[][] tokens, long[] mask) {
        int dims = tokens[0].length;
        double[] sum = new double[dims];
        long count = 0;
        for (int t = 0; t < tokens.length; t++) {
            if (mask[t] == 0) continue;
            count++;
            for (int d = 0; d < dims; d++) sum[d] += tokens[t][d];
        }
        double norm = 0;
        for (int d = 0; d < dims; d++) {
            sum[d] /= Math.max(count, 1);
            norm += sum[d] * sum[d];
        }
        norm = Math.sqrt(norm);
        float[] out = new float[dims];
        for (int d = 0; d < dims; d++) out[d] = (float) (norm > 0 ? sum[d] / norm : 0);
        return out;
    }

    private synchronized OrtSession session() throws OrtException {
        if (session == null) {
            session = source.open(OrtEnvironment.getEnvironment(), new OrtSession.SessionOptions());
        }
        return session;
    }

    @Override
    public synchronized void close() throws OrtException {
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
