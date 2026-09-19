package com.example.myquotes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SourceSuggestionsTest {
    private static final List<String> SOME = Collections.singletonList("x");
    private static final List<String> NONE = Collections.emptyList();

    @Test
    public void hasSplitOnlyWhenBothListsNonEmpty() {
        assertTrue(new SourceSuggestions(SOME, SOME).hasSplit());
        assertFalse(new SourceSuggestions(SOME, NONE).hasSplit());
        assertFalse(new SourceSuggestions(NONE, SOME).hasSplit());
        assertFalse(new SourceSuggestions(NONE, NONE).hasSplit());
    }
}
