package com.example.myquotes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.myquotes.embedding.Embedder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class QuoteEmbeddingsTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** A 2-D vector from the text's length; records every text it embeds. */
    private static final class FakeEmbedder implements Embedder {
        final String modelId;
        final List<String> embedded = new ArrayList<>();
        boolean broken;
        // Runs after each embedding, on the embedding thread.
        Runnable afterEmbed = () -> {};

        FakeEmbedder(String modelId) {
            this.modelId = modelId;
        }

        @Override
        public float[] embed(String text) {
            if (broken) throw new IllegalStateException("no model");
            embedded.add(text);
            afterEmbed.run();
            return new float[]{text.length(), 1};
        }

        @Override
        public String modelId() {
            return modelId;
        }

        @Override
        public int dimensions() {
            return 2;
        }
    }

    private static final class MemoryStore implements QuoteEmbeddings.Store {
        QuoteEmbeddings.Saved saved;
        int saves;

        @Override
        public QuoteEmbeddings.Saved load() {
            return saved;
        }

        @Override
        public void save(String modelId, Map<Long, float[]> vectors) {
            saved = new QuoteEmbeddings.Saved(modelId, new HashMap<>(vectors));
            saves++;
        }
    }

    private final MemoryStore store = new MemoryStore();
    private QuoteCollection collection;
    private QuoteEmbeddings embeddings;
    private int emissions;

    private static Quote quote(int id, String text) {
        return new Quote(id, "Author", text, "");
    }

    /** A worker that runs its tasks only when told to. */
    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            while (!tasks.isEmpty()) tasks.poll().run();
        }
    }

    /** Collects the vectors {@link QuoteEmbeddings#embedLatest} answers with. */
    private static final class Answers {
        final List<String> texts = new ArrayList<>();
        final List<float[]> vectors = new ArrayList<>();

        java.util.function.Consumer<float[]> forText(String text) {
            return vector -> {
                texts.add(text);
                vectors.add(vector);
            };
        }
    }

    private QuoteEmbeddings start(FakeEmbedder embedder, Quote... quotes) {
        return start(embedder, Runnable::run, quotes);
    }

    private QuoteEmbeddings start(FakeEmbedder embedder, Executor worker, Quote... quotes) {
        collection = new QuoteCollection(new InMemoryQuoteStore(Arrays.asList(quotes)));
        collection.loadFromStore();
        embeddings = new QuoteEmbeddings(collection, embedder, store, worker, Runnable::run);
        emissions = 0;
        embeddings.getChanges().observeForever(count -> emissions++);
        emissions = 0; // ignore the initial delivery
        return embeddings;
    }

    @Test
    public void embedsEveryQuoteAndSavesWithTheModelId() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        start(embedder, quote(1, "ab"), quote(2, "abcd"));
        Map<Integer, float[]> snapshot = embeddings.snapshot();
        assertEquals(2, snapshot.size());
        assertArrayEquals(new float[]{4, 1}, snapshot.get(2), 0f);
        assertEquals(Arrays.asList("ab", "abcd"), embedder.embedded);
        assertEquals("m1", store.saved.modelId);
        assertEquals(2, store.saved.vectors.size());
    }

    @Test
    public void aLaterStartUsesTheCacheWithoutEmbedding() {
        start(new FakeEmbedder("m1"), quote(1, "ab"), quote(2, "abcd"));
        int saves = store.saves;
        FakeEmbedder second = new FakeEmbedder("m1");
        start(second, quote(1, "ab"), quote(2, "abcd"));
        assertTrue(second.embedded.isEmpty());
        assertEquals(2, embeddings.snapshot().size());
        assertEquals(saves + 1, store.saves); // the loaded vectors are a change; nothing else
    }

    @Test
    public void anotherModelIdThrowsTheCacheAway() {
        start(new FakeEmbedder("m1"), quote(1, "ab"));
        store.saved.vectors.put(QuoteEmbeddings.hash("not in the collection"), new float[]{9, 9});
        FakeEmbedder other = new FakeEmbedder("m2");
        start(other, quote(1, "ab"));
        assertEquals(Collections.singletonList("ab"), other.embedded);
        assertEquals("m2", store.saved.modelId);
        assertEquals(1, store.saved.vectors.size());
    }

    @Test
    public void aChangedTextIsEmbeddedAgainAndTheOldVectorDropped() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        start(embedder, quote(1, "ab"), quote(2, "abcd"));
        embedder.embedded.clear();
        collection.edit(1, "Author", "abcdef", "", Collections.emptyList());
        assertEquals(Collections.singletonList("abcdef"), embedder.embedded);
        assertArrayEquals(new float[]{6, 1}, embeddings.snapshot().get(1), 0f);
        assertNull(embeddings.cached("ab"));
        assertFalse(store.saved.vectors.containsKey(QuoteEmbeddings.hash("ab")));
    }

    @Test
    public void aRemovedQuoteIsDropped() {
        start(new FakeEmbedder("m1"), quote(1, "ab"), quote(2, "abcd"));
        collection.deleteById(2);
        assertEquals(Collections.singleton(1), embeddings.snapshot().keySet());
        assertEquals(1, store.saved.vectors.size());
    }

    @Test
    public void quotesWithTheSameTextShareOneVector() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        start(embedder, quote(1, "same"), quote(2, "same"));
        assertEquals(Collections.singletonList("same"), embedder.embedded);
        assertEquals(2, embeddings.snapshot().size());
    }

    @Test
    public void theSnapshotIsImmutableAndDoesNotFollowLaterChanges() {
        start(new FakeEmbedder("m1"), quote(1, "ab"));
        Map<Integer, float[]> snapshot = embeddings.snapshot();
        try {
            snapshot.put(2, new float[]{1, 1});
            fail("snapshot is modifiable");
        } catch (UnsupportedOperationException expected) {
        }
        collection.add(quote(0, "abc"));
        assertEquals(1, snapshot.size());
        assertEquals(2, embeddings.snapshot().size());
    }

    @Test
    public void emitsWhenVectorsChangeOnly() {
        start(new FakeEmbedder("m1"), quote(1, "ab"));
        collection.add(quote(0, "abc"));
        assertEquals(1, emissions);
        collection.toggleFavorite(1); // same texts
        assertEquals(1, emissions);
    }

    @Test
    public void vectorsForOnlyCoversQuotesWithAVector() {
        start(new FakeEmbedder("m1"), quote(1, "ab"), quote(2, "   "));
        Map<Integer, float[]> vectors = embeddings.vectorsFor(collection.getCurrentList());
        assertEquals(Collections.singleton(1), vectors.keySet());
    }

    @Test
    public void aBrokenEmbedderLeavesSuggestionsWithoutVectors() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        embedder.broken = true;
        start(embedder, quote(1, "ab"));
        assertTrue(embeddings.snapshot().isEmpty());
        Answers answers = new Answers();
        embeddings.embedLatest("typed text", answers.forText("typed text"));
        assertEquals(Collections.singletonList("typed text"), answers.texts);
        assertNull(answers.vectors.get(0));
    }

    @Test
    public void anEditorRequestWhoseEmbedderFailsAnswersNull() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        ManualExecutor worker = new ManualExecutor();
        start(embedder, worker, quote(1, "ab"));
        worker.runAll();
        embedder.broken = true;
        Answers answers = new Answers();
        embeddings.embedLatest("typed", answers.forText("typed"));
        assertTrue(answers.texts.isEmpty());
        worker.runAll();
        assertEquals(Collections.singletonList("typed"), answers.texts);
        assertNull(answers.vectors.get(0));
    }

    @Test
    public void aCachedTextAnswersAtOnceWithoutEmbedding() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        ManualExecutor worker = new ManualExecutor();
        start(embedder, worker, quote(1, "ab"));
        worker.runAll();
        embedder.embedded.clear();
        Answers answers = new Answers();
        embeddings.embedLatest("ab", answers.forText("ab"));
        assertTrue(worker.tasks.isEmpty());
        assertTrue(embedder.embedded.isEmpty());
        assertArrayEquals(new float[]{2, 1}, answers.vectors.get(0), 0f);
        embeddings.embedLatest(" ", answers.forText(" "));
        assertNull(answers.vectors.get(1));
        assertTrue(embedder.embedded.isEmpty());
    }

    @Test
    public void quickRequestsEmbedOnlyTheLatestText() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        ManualExecutor worker = new ManualExecutor();
        start(embedder, worker, quote(1, "ab"));
        worker.runAll();
        embedder.embedded.clear();
        Answers answers = new Answers();
        embeddings.embedLatest("t", answers.forText("t"));
        embeddings.embedLatest("ty", answers.forText("ty"));
        embeddings.embedLatest("typ", answers.forText("typ"));
        worker.runAll();
        assertEquals(Collections.singletonList("typ"), embedder.embedded);
        assertEquals(Collections.singletonList("typ"), answers.texts);
        assertArrayEquals(new float[]{3, 1}, answers.vectors.get(0), 0f);
    }

    @Test
    public void anEditorRequestDuringAPassIsServedBeforeTheRemainingTexts() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        ManualExecutor worker = new ManualExecutor();
        start(embedder, worker, quote(1, "a1"), quote(2, "b22"), quote(3, "c333"));
        Answers answers = new Answers();
        embedder.afterEmbed = () -> {
            if (embedder.embedded.size() == 1) embeddings.embedLatest("typed", answers.forText("typed"));
        };
        worker.runAll();
        assertEquals(Arrays.asList("a1", "typed", "b22", "c333"), embedder.embedded);
        assertEquals(Collections.singletonList("typed"), answers.texts);
        assertEquals(3, embeddings.snapshot().size());
    }

    @Test
    public void theEditorsVectorIsNotCached() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        ManualExecutor worker = new ManualExecutor();
        start(embedder, worker, quote(1, "ab"));
        worker.runAll();
        Answers answers = new Answers();
        embeddings.embedLatest("typed", answers.forText("typed"));
        worker.runAll();
        assertArrayEquals(new float[]{5, 1}, answers.vectors.get(0), 0f);
        assertNull(embeddings.cached("typed"));
        assertFalse(store.saved.vectors.containsKey(QuoteEmbeddings.hash("typed")));
    }

    @Test
    public void aCancelledRequestIsNeitherEmbeddedNorAnswered() {
        FakeEmbedder embedder = new FakeEmbedder("m1");
        ManualExecutor worker = new ManualExecutor();
        start(embedder, worker, quote(1, "ab"));
        worker.runAll();
        embedder.embedded.clear();
        Answers answers = new Answers();
        embeddings.embedLatest("typed", answers.forText("typed")).cancel();
        worker.runAll();
        assertTrue(embedder.embedded.isEmpty());
        assertTrue(answers.texts.isEmpty());
    }

    @Test
    public void theFileStoreRoundTrips() throws Exception {
        File file = new File(temp.getRoot(), "vectors.bin");
        QuoteEmbeddingsFile fileStore = new QuoteEmbeddingsFile(file);
        assertNull(fileStore.load());
        Map<Long, float[]> vectors = new HashMap<>();
        vectors.put(7L, new float[]{0.5f, -0.25f});
        vectors.put(-3L, new float[]{1f, 0f});
        fileStore.save("m1", vectors);
        fileStore.save("m1", vectors); // replacing an existing file
        QuoteEmbeddings.Saved saved = fileStore.load();
        assertNotNull(saved);
        assertEquals("m1", saved.modelId);
        assertEquals(2, saved.vectors.size());
        assertArrayEquals(new float[]{0.5f, -0.25f}, saved.vectors.get(7L), 0f);
    }

    @Test
    public void anUnreadableFileIsNoCache() throws Exception {
        File file = new File(temp.getRoot(), "vectors.bin");
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        assertNull(new QuoteEmbeddingsFile(file).load());
    }
}
