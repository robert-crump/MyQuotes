package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TaggingRunTest {
    private static final List<String> NONE = Collections.emptyList();

    @Test
    public void aSuggestedForRunIsDecidedOnceTheTagIsAddedOrRejected() {
        TaggingRun run = new TaggingRun(QuoteQuery.suggestedFor("Stoicism"));
        assertEquals("Stoicism", run.getSuggestedTag());
        assertFalse(run.isSuggestionsRun());
        assertFalse(run.isDecided(Arrays.asList("Life", "Love"), Collections.singletonList("Hope")));
        assertTrue(run.isDecided(Arrays.asList("Life", "stoicism"), NONE));
        assertTrue(run.isDecided(NONE, Collections.singletonList("STOICISM")));
    }

    @Test
    public void otherRunsHaveNoTag() {
        assertNull(new TaggingRun(QuoteQuery.suggested()).getSuggestedTag());
        assertNull(new TaggingRun(QuoteQuery.untagged()).getSuggestedTag());
        assertFalse(new TaggingRun(QuoteQuery.suggested()).isDecided(Collections.singletonList("Stoicism"), NONE));
    }

    @Test
    public void aSuggestedForRunNamesTheTag() {
        TaggingRun run = new TaggingRun(QuoteQuery.suggestedFor("Stoicism"));
        assertEquals("Continue with the next quote for #Stoicism?", run.continuePrompt());
        assertEquals("No more suggestions for #Stoicism", run.noMoreMessage());
        assertEquals("Skip to next quote", run.skipTitle());
        assertEquals("Continue with the next quote?", new TaggingRun(QuoteQuery.suggested()).continuePrompt());
        assertEquals("Skip to next untagged quote", new TaggingRun(QuoteQuery.untagged()).skipTitle());
    }
}
