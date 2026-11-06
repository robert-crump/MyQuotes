package com.example.myquotes;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myquotes.embedding.Embedder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A vector per quote for the similar-quotes reason (#62), owned by {@link MyApplication} like
 * {@link Hashtags}. A cache that can always be rebuilt: it is keyed by a hash of the quote text,
 * saved through a {@link Store} that records the {@link Embedder#modelId()} (another model throws it
 * away), and is neither in the Quote Store nor in the Backup Document.
 *
 * <p>It observes the Quote Collection. A quote whose text is new or changed is embedded on the one
 * {@code worker} thread; texts no quote has any more are dropped. Every {@link #BATCH} vectors and
 * at the end of a pass it saves and {@link #getChanges() emits}, so open lists re-rank as the
 * cache fills. Until then suggestions work without the reason. When the embedder fails (no model
 * in the APK), the cache stays as it is and nothing is embedded for the rest of the process.
 */
public final class QuoteEmbeddings {
    private static final String TAG = "QuoteEmbeddings";
    static final int BATCH = 64;

    /** Where the vectors live between app starts. */
    public interface Store {
        /** The saved vectors, or null when there are none (or the file can't be read). */
        @Nullable Saved load();

        void save(String modelId, Map<Long, float[]> vectors);
    }

    public static final class Saved {
        final String modelId;
        final Map<Long, float[]> vectors;

        public Saved(String modelId, Map<Long, float[]> vectors) {
            this.modelId = modelId;
            this.vectors = vectors;
        }
    }

    private final Embedder embedder;
    private final Store store;
    private final Executor worker;
    private final Executor main;
    private final Map<Long, float[]> byText = new ConcurrentHashMap<>();
    private final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private final AtomicInteger requested = new AtomicInteger();
    private volatile List<Quote> quotes = Collections.emptyList();
    private volatile boolean unavailable;
    // Worker thread only.
    private boolean loaded;

    /** {@code main} runs emissions on the main thread; {@code worker} is a single thread. */
    public QuoteEmbeddings(QuoteCollection collection, Embedder embedder, Store store, Executor worker,
                           Executor main) {
        this.embedder = embedder;
        this.store = store;
        this.worker = worker;
        this.main = main;
        collection.getQuoteList().observeForever(this::update);
    }

    /** Emits (a growing count) when vectors were added or dropped; observe to re-rank. */
    public LiveData<Integer> getChanges() {
        return changes;
    }

    /** Quote id → vector for the collection as last seen; only quotes with a vector. Immutable. */
    public Map<Integer, float[]> snapshot() {
        return vectorsFor(quotes);
    }

    /** Quote id → vector for {@code quotes}; only quotes with a vector. Immutable. */
    public Map<Integer, float[]> vectorsFor(List<Quote> quotes) {
        Map<Integer, float[]> vectors = new HashMap<>();
        for (Quote quote : quotes) {
            if (quote.getId() == null) continue;
            float[] vector = byText.get(hash(quote.getQuoteText()));
            if (vector != null) vectors.put(quote.getId(), vector);
        }
        return Collections.unmodifiableMap(vectors);
    }

    /** The cached vector of {@code text} (a quote's text), or null; never embeds. */
    @Nullable
    public float[] cached(String text) {
        return byText.get(hash(text));
    }

    /**
     * The vector of {@code text} (the editor's text): the cached one, else embedded now. Blocks;
     * call off the main thread. Null for empty text or when the embedder is unavailable. The
     * result is not cached, since typed text is mostly never saved.
     */
    @Nullable
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        float[] vector = cached(text);
        if (vector != null || unavailable) return vector;
        try {
            return embedder.embed(text);
        } catch (RuntimeException | LinkageError e) {
            markUnavailable(e);
            return null;
        }
    }

    private void update(List<Quote> current) {
        if (current == null) return;
        quotes = current;
        List<String> texts = new ArrayList<>(current.size());
        for (Quote quote : current) texts.add(quote.getQuoteText());
        int generation = requested.incrementAndGet();
        worker.execute(() -> sync(texts, generation));
    }

    /** Worker thread: loads the cache once, embeds missing texts, drops unused ones. */
    private void sync(List<String> texts, int generation) {
        long start = System.nanoTime();
        boolean changed = false;
        if (!loaded) {
            loaded = true;
            Saved saved = store.load();
            if (saved != null && embedder.modelId().equals(saved.modelId)) {
                byText.putAll(saved.vectors);
                changed = !saved.vectors.isEmpty();
            } else {
                changed = saved != null; // another model's vectors: rewrite the file
            }
        }
        Set<Long> keys = new HashSet<>();
        int added = 0;
        for (String text : texts) {
            long key = hash(text);
            keys.add(key);
            if (byText.containsKey(key) || unavailable || text == null || text.trim().isEmpty()) continue;
            // A newer collection is waiting: leave the rest to its pass.
            if (generation != requested.get()) break;
            float[] vector;
            try {
                vector = embedder.embed(text);
            } catch (RuntimeException | LinkageError e) {
                markUnavailable(e);
                break;
            }
            byText.put(key, vector);
            changed = true;
            if (++added % BATCH == 0) {
                save();
                emit();
            }
        }
        if (generation == requested.get() && byText.keySet().retainAll(keys)) changed = true;
        if (changed) {
            save();
            emit();
        }
        if (added > 0) {
            Log.i(TAG, "Embedded " + added + " texts in " + (System.nanoTime() - start) / 1_000_000 + " ms; "
                    + byText.size() + " cached");
        }
    }

    private void save() {
        store.save(embedder.modelId(), new HashMap<>(byText));
    }

    private void emit() {
        main.execute(() -> {
            Integer count = changes.getValue();
            changes.setValue(count == null ? 1 : count + 1);
        });
    }

    private void markUnavailable(Throwable e) {
        if (!unavailable) Log.w(TAG, "Embedding is unavailable; similar quotes are off", e);
        unavailable = true;
    }

    /** The first 8 bytes of the text's SHA-256. */
    static long hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            long hash = 0;
            for (int i = 0; i < 8; i++) hash = (hash << 8) | (digest[i] & 0xFF);
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
