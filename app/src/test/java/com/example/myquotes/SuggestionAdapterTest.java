package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/** The adapter's rows; the Context is only used to inflate views, so null is fine here. */
public class SuggestionAdapterTest {

    @Test
    public void sourcesOfOnlyTheAuthorAreListed() {
        SourceSuggestions s = new SourceSuggestions(
                Arrays.asList("Letters", "On Anger"), Collections.emptyList());
        SuggestionAdapter adapter = SuggestionAdapter.forSources(null, s);
        assertEquals(2, adapter.getCount());
        assertEquals("Letters", adapter.getStringAt(0));
        assertEquals("On Anger", adapter.getStringAt(1));
    }

    @Test
    public void sourcesWithoutAnAuthorAreListed() {
        SourceSuggestions s = new SourceSuggestions(
                Collections.emptyList(), Arrays.asList("Republic"));
        SuggestionAdapter adapter = SuggestionAdapter.forSources(null, s);
        assertEquals(1, adapter.getCount());
        assertEquals("Republic", adapter.getStringAt(0));
    }

    @Test
    public void authorSourcesComeFirstThenADivider() {
        SourceSuggestions s = new SourceSuggestions(
                Arrays.asList("Letters"), Arrays.asList("Republic"));
        SuggestionAdapter adapter = SuggestionAdapter.forSources(null, s);
        assertEquals(3, adapter.getCount());
        assertEquals("Letters", adapter.getStringAt(0));
        assertNull(adapter.getStringAt(1));
        assertEquals("Republic", adapter.getStringAt(2));
        assertEquals(2, adapter.getSuggestionCount());
    }
}
