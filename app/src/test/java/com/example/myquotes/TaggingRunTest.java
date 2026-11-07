package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The after-save rules for each run kind (#69): what a save decides, before and after Continue,
 * what the run does with the next quote, and the run's texts.
 */
public class TaggingRunTest {
    private static final List<String> NONE = Collections.emptyList();
    private static final int ID = 7;
    private static final Quote UNTAGGED = quote(ID, NONE);
    private static final Quote TAGGED = quote(ID, Collections.singletonList("Life"));
    private static final Quote NEXT = quote(8, NONE);

    private static Quote quote(int id, List<String> tags) {
        Quote quote = new Quote(id, "Author", "Some text", "");
        quote.setTags(tags);
        return quote;
    }

    private static TaggingRun run(QuoteQuery query, Quote loaded, boolean continued) {
        TaggingRun run = new TaggingRun(query);
        run.onQuoteLoaded(loaded);
        if (continued) run.setContinued();
        return run;
    }

    private static void assertStep(TaggingRun.Step step, TaggingRun.Step.Action action, String message) {
        assertEquals(action, step.action);
        assertEquals(message, step.message);
    }

    // "No hashtag"

    @Test
    public void noHashtag_taggingAnUntaggedQuoteFindsTheNextAndAsksOnce() {
        TaggingRun run = run(QuoteQuery.untagged(), UNTAGGED, false);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, Arrays.asList("Life"), NONE));
        assertTrue(run.isSkipped(ID));
        assertStep(run.afterSaveNext(NEXT), TaggingRun.Step.Action.ASK_CONTINUE, "Continue with next untagged quote?");
    }

    @Test
    public void noHashtag_afterContinueATaggingSaveShowsTheNextWithAToast() {
        TaggingRun run = run(QuoteQuery.untagged(), UNTAGGED, true);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, Arrays.asList("Life"), NONE));
        assertStep(run.afterSaveNext(NEXT), TaggingRun.Step.Action.SHOW, "Quote updated");
    }

    @Test
    public void noHashtag_anUntaggedSaveGoesBackBeforeContinue() {
        TaggingRun run = run(QuoteQuery.untagged(), UNTAGGED, false);
        assertEquals(TaggingRun.AfterSave.BACK_TO_LIST, run.afterSave(ID, NONE, Arrays.asList("Hope")));
        assertFalse(run.isSkipped(ID));
    }

    @Test
    public void noHashtag_anUntaggedSaveIsSkippedAfterContinue() {
        TaggingRun run = run(QuoteQuery.untagged(), UNTAGGED, true);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, NONE, NONE));
        assertTrue(run.isSkipped(ID));
    }

    @Test
    public void noHashtag_aQuoteTaggedWhenLoadedGoesBackEvenAfterContinue() {
        for (boolean continued : new boolean[] {false, true}) {
            TaggingRun run = run(QuoteQuery.untagged(), TAGGED, continued);
            assertEquals(TaggingRun.AfterSave.BACK_TO_LIST, run.afterSave(ID, Arrays.asList("Life", "Love"), NONE));
            assertFalse(run.isSkipped(ID));
        }
    }

    // "Has suggestions"

    @Test
    public void hasSuggestions_everySaveIsDecidedBeforeAndAfterContinue() {
        TaggingRun run = run(QuoteQuery.suggested(), TAGGED, false);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, Arrays.asList("Life"), NONE));
        assertTrue(run.isSkipped(ID));
        assertStep(run.afterSaveNext(NEXT), TaggingRun.Step.Action.ASK_CONTINUE, "Continue with the next quote?");

        run = run(QuoteQuery.suggested(), UNTAGGED, true);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, NONE, NONE));
        assertTrue(run.isSkipped(ID));
        assertStep(run.afterSaveNext(NEXT), TaggingRun.Step.Action.SHOW, "Quote updated");
    }

    // "Suggested for"

    @Test
    public void suggestedFor_addingOrRejectingTheTagIsDecided() {
        TaggingRun run = run(QuoteQuery.suggestedFor("Stoicism"), TAGGED, false);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, Arrays.asList("Life", "stoicism"), NONE));
        assertTrue(run.isSkipped(ID));
        assertStep(run.afterSaveNext(NEXT), TaggingRun.Step.Action.ASK_CONTINUE,
                "Continue with the next quote for #Stoicism?");

        run = run(QuoteQuery.suggestedFor("Stoicism"), TAGGED, true);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, NONE, Arrays.asList("STOICISM")));
        assertTrue(run.isSkipped(ID));
        assertStep(run.afterSaveNext(NEXT), TaggingRun.Step.Action.SHOW, "Quote updated");
    }

    @Test
    public void suggestedFor_aSaveDecidingNeitherGoesBackBeforeContinueAndIsSkippedAfter() {
        TaggingRun run = run(QuoteQuery.suggestedFor("Stoicism"), TAGGED, false);
        assertEquals(TaggingRun.AfterSave.BACK_TO_LIST, run.afterSave(ID, Arrays.asList("Life", "Love"), Arrays.asList("Hope")));
        assertFalse(run.isSkipped(ID));

        run = run(QuoteQuery.suggestedFor("Stoicism"), TAGGED, true);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(ID, Arrays.asList("Life", "Love"), NONE));
        assertTrue(run.isSkipped(ID));
    }

    // With no quote left

    @Test
    public void noNextQuoteAfterTheFirstDecidingSaveIsJustAnUpdate() {
        TaggingRun run = run(QuoteQuery.untagged(), UNTAGGED, false);
        run.afterSave(ID, Arrays.asList("Life"), NONE);
        assertStep(run.afterSaveNext(null), TaggingRun.Step.Action.FINISH, "Quote updated");
    }

    @Test
    public void noNextQuoteAfterContinueEndsTheRunWithTheKindsMessage() {
        assertStep(run(QuoteQuery.untagged(), UNTAGGED, true).afterSaveNext(null),
                TaggingRun.Step.Action.FINISH, "No more untagged quotes");
        assertStep(run(QuoteQuery.suggested(), UNTAGGED, true).afterSaveNext(null),
                TaggingRun.Step.Action.FINISH, "No more suggestions");
        assertStep(run(QuoteQuery.suggestedFor("Stoicism"), UNTAGGED, true).afterSaveNext(null),
                TaggingRun.Step.Action.FINISH, "No more suggestions for #Stoicism");
    }

    // Skip and Delete

    @Test
    public void moveOnCountsAsContinueAndShowsTheNextWithoutAToast() {
        TaggingRun run = run(QuoteQuery.untagged(), UNTAGGED, false);
        run.skip(ID);
        run.moveOn();
        assertTrue(run.isContinued());
        assertTrue(run.isSkipped(ID));
        assertStep(run.afterMoveOn(NEXT), TaggingRun.Step.Action.SHOW, null);
        assertStep(run.afterMoveOn(null), TaggingRun.Step.Action.FINISH, "No more untagged quotes");
        // A later untagged save now counts as Skip without asking.
        run.onQuoteLoaded(NEXT);
        assertEquals(TaggingRun.AfterSave.FIND_NEXT, run.afterSave(8, NONE, NONE));
        assertStep(run.afterSaveNext(quote(9, NONE)), TaggingRun.Step.Action.SHOW, "Quote updated");
    }

    // Texts per kind

    @Test
    public void skipTitlesAndPinnedTagPerKind() {
        assertEquals("Skip to next untagged quote", new TaggingRun(QuoteQuery.untagged()).skipTitle());
        assertEquals("Skip to next quote", new TaggingRun(QuoteQuery.suggested()).skipTitle());
        assertEquals("Skip to next quote", new TaggingRun(QuoteQuery.suggestedFor("Stoicism")).skipTitle());
        assertNull(new TaggingRun(QuoteQuery.untagged()).pinnedTag());
        assertNull(new TaggingRun(QuoteQuery.suggested()).pinnedTag());
        assertEquals("Stoicism", new TaggingRun(QuoteQuery.suggestedFor("Stoicism")).pinnedTag());
    }
}
