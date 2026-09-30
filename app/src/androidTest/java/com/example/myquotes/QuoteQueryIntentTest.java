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
    public void exactTagQuerySurvivesIntentAndBundle() {
        QuoteQuery tag = QuoteQuery.forTag("AI");

        QuoteQuery viaIntent = QuoteQuery.fromIntent(tag.toIntent(context));
        QuoteQuery viaBundle = QuoteQuery.fromBundle(tag.toBundle());

        assertEquals(tag, viaIntent);
        assertEquals(tag, viaBundle);
        assertTrue(viaIntent.isExact());
        assertEquals("#AI", viaIntent.getText());
        assertEquals(QuoteQuery.Field.HASHTAGS, viaIntent.singleField());
    }

    @Test
    public void typedQueriesStayTyped() {
        QuoteQuery hashtags = QuoteQuery.forField(QuoteQuery.Field.HASHTAGS, "wis");
        QuoteQuery all = QuoteQuery.all("seneca");

        assertEquals(hashtags, QuoteQuery.fromIntent(hashtags.toIntent(context)));
        assertFalse(QuoteQuery.fromBundle(hashtags.toBundle()).isExact());
        assertEquals(all, QuoteQuery.fromBundle(all.toBundle()));
    }

    @Test
    public void bundleWithoutTheExactFlagReadsAsTyped() {
        Bundle old = QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "Seneca").toBundle();
        old.remove(QuoteQuery.EXTRA_EXACT);
        assertFalse(QuoteQuery.fromBundle(old).isExact());
    }

    @Test
    public void intentWithoutAQueryReadsAsNone() {
        assertNull(QuoteQuery.fromIntent(new Intent(context, SearchActivity.class)));
    }
}
