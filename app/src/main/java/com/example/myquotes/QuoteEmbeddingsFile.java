package com.example.myquotes;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link QuoteEmbeddings}' vectors in one small binary file (#62): a header, the model ID, then
 * text hash and floats per vector. Written to a temporary file and renamed, so a crash leaves the
 * old file. A file that can't be read counts as no cache.
 */
final class QuoteEmbeddingsFile implements QuoteEmbeddings.Store {
    private static final String TAG = "QuoteEmbeddingsFile";
    private static final int MAGIC = 0x4D515645; // "MQVE"
    private static final int FORMAT = 1;

    private final File file;

    QuoteEmbeddingsFile(File file) {
        this.file = file;
    }

    @Nullable
    @Override
    public QuoteEmbeddings.Saved load() {
        if (!file.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) return null;
            String modelId = in.readUTF();
            int dimensions = in.readInt();
            int count = in.readInt();
            Map<Long, float[]> vectors = new HashMap<>();
            for (int i = 0; i < count; i++) {
                long key = in.readLong();
                float[] vector = new float[dimensions];
                for (int d = 0; d < dimensions; d++) vector[d] = in.readFloat();
                vectors.put(key, vector);
            }
            return new QuoteEmbeddings.Saved(modelId, vectors);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Ignoring an unreadable embeddings file", e);
            return null;
        }
    }

    @Override
    public void save(String modelId, Map<Long, float[]> vectors) {
        int dimensions = vectors.isEmpty() ? 0 : vectors.values().iterator().next().length;
        File temp = new File(file.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeUTF(modelId);
            out.writeInt(dimensions);
            out.writeInt(vectors.size());
            for (Map.Entry<Long, float[]> entry : vectors.entrySet()) {
                out.writeLong(entry.getKey());
                for (float value : entry.getValue()) out.writeFloat(value);
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not save the embeddings", e);
            temp.delete();
            return;
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.w(TAG, "Could not replace " + file, e);
            temp.delete();
        }
    }
}
