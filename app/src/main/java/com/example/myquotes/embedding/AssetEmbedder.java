package com.example.myquotes.embedding;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * The app's {@link Embedder} (#62): the model shipped in the APK's assets under
 * {@code embedding/}, stored uncompressed so it is memory-mapped straight from the APK. The
 * tokenizer and the ONNX session are loaded on the first {@link #embed} and kept for the process
 * (about 150-250 MB while loaded). {@link #modelId()} needs neither, so the cache can be checked
 * without loading the model. Without the files in the APK, {@link #embed} throws.
 */
public final class AssetEmbedder implements Embedder {
    public static final String ASSET_DIR = "embedding/";

    private final AssetManager assets;
    private final TextEmbeddingModel model;
    private OnnxEmbedder loaded;

    public AssetEmbedder(AssetManager assets, TextEmbeddingModel model) {
        this.assets = assets;
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        return embedder().embed(text);
    }

    @Override
    public String modelId() {
        return model.id();
    }

    @Override
    public int dimensions() {
        return model.dimensions;
    }

    private synchronized OnnxEmbedder embedder() {
        if (loaded == null) {
            try {
                UnigramTokenizer tokenizer;
                try (InputStream in = assets.open(ASSET_DIR + TextEmbeddingModel.TOKENIZER_FILE)) {
                    tokenizer = UnigramTokenizer.fromJson(in);
                }
                MappedByteBuffer modelBytes;
                try (AssetFileDescriptor fd = assets.openFd(ASSET_DIR + TextEmbeddingModel.MODEL_FILE);
                     FileInputStream in = fd.createInputStream()) {
                    modelBytes = in.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(),
                            fd.getDeclaredLength());
                }
                loaded = new OnnxEmbedder(model, modelBytes, tokenizer);
            } catch (IOException e) {
                throw new IllegalStateException("The embedding model " + model.id() + " is not in the APK", e);
            }
        }
        return loaded;
    }
}
