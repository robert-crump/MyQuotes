package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;

public class SuggestionModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    /** The fitting thread, run by hand. */
    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        int executed;

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                executed++;
                tasks.poll().run();
            }
        }
    }

    private final ManualExecutor fitter = new ManualExecutor();
    private final MutableLiveData<Integer> vectorChanges = new MutableLiveData<>(0);
    private final Map<Integer, float[]> vectors = new HashMap<>();
    private QuoteCollection collection;
    private SuggestionModel suggestionModel;
    private final List<SuggestionModel.Fitted> published = new ArrayList<>();

    private static List<Quote> demo() {
        List<Quote> quotes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) quotes.add(quote(i, "Author " + i, "heart and rose " + i, "Love"));
        for (int i = 6; i <= 10; i++) quotes.add(quote(i, "Writer " + i, "forest walk " + i, "Nature"));
        quotes.add(quote(11, "Someone", "my heart sings", null));
        quotes.add(quote(12, "Other", "a forest at dawn", null));
        return quotes;
    }

    private static Quote quote(int id, String author, String text, String tag) {
        Quote quote = new Quote(id, author, text, "");
        quote.setTags(tag == null ? Collections.emptyList() : Collections.singletonList(tag));
        return quote;
    }

    @Before
    public void setUp() {
        collection = new QuoteCollection(new InMemoryQuoteStore(demo()));
        collection.loadFromStore();
        suggestionModel = new SuggestionModel(collection.getQuoteList(), vectorChanges,
                quotes -> Collections.unmodifiableMap(new HashMap<>(vectors)), fitter, Runnable::run);
        suggestionModel.getFitted().observeForever(published::add);
    }

    private List<Quote> currentList() {
        return collection.getQuoteList().getValue();
    }

    @Test
    public void fitsTheCollectionOnceAndPublishesIt() {
        fitter.runAll();
        assertEquals(1, fitter.executed);
        assertEquals(1, published.size());
        SuggestionModel.Fitted fitted = published.get(0);
        assertSame(currentList(), fitted.quotes);
        assertEquals(Integer.valueOf(0), fitted.vectorsVersion);
    }

    @Test
    public void eachCollectionEmissionFitsOnce() {
        fitter.runAll();
        collection.deleteById(12);
        fitter.runAll();
        collection.deleteById(11);
        fitter.runAll();
        assertEquals(3, fitter.executed);
        assertEquals(3, published.size());
        assertSame(currentList(), published.get(2).quotes);
    }

    @Test
    public void quickEmissionsPublishOnlyTheNewestFit() {
        fitter.runAll();
        published.clear();
        fitter.executed = 0;
        collection.deleteById(12); // starts a fit
        collection.deleteById(11); // waits for it
        collection.deleteById(10); // replaces the waiting one
        fitter.runAll();
        // The running fit (an older list) is dropped, then one fit of the newest list.
        assertEquals(2, fitter.executed);
        assertEquals(1, published.size());
        assertSame(currentList(), published.get(0).quotes);
        assertEquals(9, published.get(0).quotes.size());
    }

    @Test
    public void aResultForAnOlderListNeverPublishes() {
        fitter.runAll();
        published.clear();
        collection.deleteById(12);
        Runnable stale = fitter.tasks.poll(); // the fit of the list without 12
        collection.deleteById(11);
        stale.run();
        assertTrue(published.isEmpty());
        fitter.runAll();
        assertEquals(1, published.size());
        assertSame(currentList(), published.get(0).quotes);
    }

    @Test
    public void anEmbeddingsChangeRefitsTheSameListWithTheNewVectors() {
        fitter.runAll();
        vectors.put(1, new float[]{1, 0});
        vectorChanges.setValue(1);
        fitter.runAll();
        assertEquals(2, published.size());
        assertSame(published.get(0).quotes, published.get(1).quotes);
        assertEquals(Integer.valueOf(1), published.get(1).vectorsVersion);
        assertNotNull(published.get(1).vectors.get(1));
    }

    @Test
    public void nothingNewIsNotRefitted() {
        fitter.runAll();
        vectorChanges.setValue(0); // same version
        fitter.runAll();
        assertEquals(1, fitter.executed);
    }

    @Test
    public void fittedCountsMatchSuggestionRankingOnTheSameQuotes() {
        fitter.runAll();
        SuggestionModel.Fitted fitted = published.get(0);
        HashtagSuggester model = HashtagSuggester.fit(currentList());
        Map<Integer, List<HashtagSuggester.Suggestion>> expected = SuggestionRanking.suggestionsById(currentList(), model);
        assertEquals(expected, fitted.suggestionsById);
        assertEquals(SuggestionRanking.countWithSuggestions(expected), fitted.countWithSuggestions);
        assertTrue(fitted.countWithSuggestions > 0);
    }

    @Test
    public void theTagPassMatchesSuggestionForAndIsCached() {
        fitter.runAll();
        SuggestionModel.Fitted fitted = published.get(0);
        List<SuggestionModel.TagPass> passes = new ArrayList<>();
        suggestionModel.tagPass(fitted, "love", passes::add);
        assertTrue(passes.isEmpty()); // computed on the fitting thread
        fitter.runAll();
        SuggestionModel.TagPass pass = passes.get(0);
        HashtagSuggester model = HashtagSuggester.fit(currentList());
        assertEquals(SuggestionRanking.countSuggestedFor(currentList(), model, "Love"), pass.count());
        for (Quote quote : currentList()) {
            assertEquals(model.suggestionFor(quote, "Love"), pass.suggestions.get(quote.getId()));
            if (pass.suggestions.containsKey(quote.getId())) {
                assertEquals(HashtagSuggestionText.suggestedForLine(model.explain(quote, "Love")),
                        pass.lines.get(quote.getId()));
            }
        }
        assertTrue(pass.suggestions.containsKey(11));
        assertTrue(pass.profile.suggestable);

        int executed = fitter.executed;
        suggestionModel.tagPass(fitted, "Love", passes::add);
        assertEquals(2, passes.size()); // answered at once
        assertSame(pass, passes.get(1));
        assertEquals(executed, fitter.executed);
    }

    @Test
    public void fitWithoutLeavesTheQuoteOut() {
        fitter.runAll();
        List<HashtagSuggester> models = new ArrayList<>();
        suggestionModel.fitWithout(published.get(0), 1, models::add);
        fitter.runAll();
        // Without quote 1, #Love is on 4 quotes: below MIN_QUOTES_PER_TAG.
        assertEquals(4, models.get(0).profile("Love").quotes);
        assertEquals(5, published.get(0).model.profile("Love").quotes);
    }

    @Test
    public void aCancelledRequestNeverAnswers() {
        fitter.runAll();
        List<HashtagSuggester> models = new ArrayList<>();
        suggestionModel.fitWithout(published.get(0), 1, models::add).cancel();
        List<SuggestionModel.TagPass> passes = new ArrayList<>();
        suggestionModel.tagPass(published.get(0), "Love", passes::add).cancel();
        fitter.runAll();
        assertTrue(models.isEmpty());
        assertTrue(passes.isEmpty());
    }

    @Test
    public void onCurrentWaitsForAFitOfTheCollectionAfterASave() {
        fitter.runAll();
        collection.deleteById(12); // the save: a fit of the new list starts
        List<Integer> sizes = new ArrayList<>();
        suggestionModel.onCurrent(fitted -> fitted.quotes.size(), sizes::add);
        assertTrue(sizes.isEmpty());
        fitter.runAll();
        assertEquals(Collections.singletonList(11), sizes);
    }

    @Test
    public void onCurrentRunsAtOnceOnAFitOfTheCurrentList() {
        fitter.runAll();
        List<SuggestionModel.Fitted> seen = new ArrayList<>();
        suggestionModel.onCurrent(fitted -> fitted, seen::add);
        fitter.runAll();
        assertEquals(1, seen.size());
        assertSame(published.get(0), seen.get(0));
    }

    @Test
    public void cancelledOnCurrentNeverAnswers() {
        fitter.runAll();
        collection.deleteById(12);
        List<Integer> sizes = new ArrayList<>();
        suggestionModel.onCurrent(fitted -> fitted.quotes.size(), sizes::add).cancel();
        fitter.runAll();
        assertTrue(sizes.isEmpty());
    }

    @Test
    public void noFitWithoutQuotes() {
        MutableLiveData<List<Quote>> none = new MutableLiveData<>();
        ManualExecutor other = new ManualExecutor();
        SuggestionModel empty = new SuggestionModel(none, vectorChanges, q -> Collections.emptyMap(), other,
                Runnable::run);
        assertTrue(other.tasks.isEmpty());
        assertNull(empty.getFitted().getValue());
        none.setValue(Arrays.asList(quote(1, "A", "text", null)));
        other.runAll();
        assertNotNull(empty.getFitted().getValue());
    }
}
