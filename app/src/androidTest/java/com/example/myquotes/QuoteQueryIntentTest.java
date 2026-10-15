package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** The {@link QuoteQuery} intent and bundle round trips, on real Intents and Bundles. */
@RunWith(AndroidJUnit4.class)
public class QuoteQueryIntentTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void tagFilterSurvivesIntentAndBundleWithEmptyText() {
        QuoteQuery tag = QuoteQuery.forTag("AI");

        QuoteQuery viaIntent = QuoteQuery.fromIntent(tag.toIntent(context));
        QuoteQuery viaBundle = QuoteQuery.fromBundle(tag.toBundle());

        assertEquals(tag, viaIntent);
        assertEquals(tag, viaBundle);
        assertEquals("AI", viaIntent.getTag());
        assertEquals("", viaIntent.getText());
    }

    @Test
    public void untaggedFilterSurvivesIntentAndBundle() {
        QuoteQuery untagged = QuoteQuery.untagged().withText("seneca");

        assertEquals(untagged, QuoteQuery.fromIntent(untagged.toIntent(context)));
        assertTrue(QuoteQuery.fromBundle(untagged.toBundle()).isUntagged());
    }

    @Test
    public void queriesWithoutAFilterStayUnfiltered() {
        QuoteQuery hashtags = QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "wis");
        QuoteQuery all = QuoteQuery.all("seneca");

        assertEquals(hashtags, QuoteQuery.fromIntent(hashtags.toIntent(context)));
        assertFalse(QuoteQuery.fromBundle(hashtags.toBundle()).hasTagFilter());
        assertEquals(all, QuoteQuery.fromBundle(all.toBundle()));
    }

    @Test
    public void bundleWithoutTheTagReadsAsUnfiltered() {
        Bundle old = QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "Seneca").withTag("Stoic").toBundle();
        old.remove(QuoteQuery.EXTRA_TAG);
        assertFalse(QuoteQuery.fromBundle(old).hasTagFilter());
    }

    @Test
    public void intentWithoutAQueryReadsAsNone() {
        assertNull(QuoteQuery.fromIntent(new Intent(context, SearchActivity.class)));
    }
}
