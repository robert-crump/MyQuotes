package com.example.myquotes;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.example.myquotes.drive.DriveAuth;
import com.example.myquotes.notifications.QuoteNotifications;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;

/**
 * Screenshots for the README, taken from the public-domain demo collection on an emulator.
 * Run through {@code ./gradlew readmeScreenshots}, which also sets up a clean status bar and
 * copies the PNGs to {@code docs/screenshots/}. Replaces the app's quotes and categories, so
 * {@link EmulatorOnlyRule} skips it without the argument and refuses real devices.
 */
@RunWith(AndroidJUnit4.class)
public class ReadmeScreenshots {
    // Seeds the Reading Session shuffle, so the headline's neighbours are the same on every run.
    private static final long DECK_SEED = 1843;
    private static final String HEADLINE = "We are all in the gutter";
    // Four demo quotes, as if the author line of one of them had been tapped.
    private static final QuoteQuery SEARCH = QuoteQuery.forField(QuoteQuery.Field.AUTHOR, "Jane Austen");
    // Pride and Prejudice: a different quote than the one the Favorites tab opens at.
    private static final int DIALOG_RESULT = 3;
    // Of the 12 months in the Statistics chart, at least this many must have bars.
    private static final int MIN_FILLED_MONTHS = 10;

    @Rule
    public final RuleChain rules = RuleChain
            .outerRule(new EmulatorOnlyRule())
            .around(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS));

    private Context context;
    private List<Quote> demoQuotes;
    private ReadmeScreenshotCapture screenshots;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        screenshots = ReadmeScreenshotCapture.cleared(context);
        // Otherwise MainActivity asks to ignore battery optimizations on every start.
        shell("dumpsys deviceidle whitelist +" + context.getPackageName());
        demoQuotes = DemoCollection.load(System.currentTimeMillis());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> DemoCollection.install(context, demoQuotes));
    }

    @Test
    public void captureReadmeScreenshots() throws Exception {
        // Each screen gets its own Activity, closed even when its capture fails.
        captureQuotesTab();
        captureFavoritesTab();
        captureSearchAndQuoteDialog();
        captureStatistics();
        captureCategories();
        captureSettings();
    }

    private void captureQuotesTab() throws Exception {
        try (ActivityScenario<MainActivity> ignored = launchMainAt(HEADLINE)) {
            screenshots.capture("quotes");
        }
    }

    /** Opens at the most recently favorited demo quote. */
    private void captureFavoritesTab() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launchMainAt(HEADLINE)) {
            scenario.onActivity(activity -> activity.<BottomNavigationView>findViewById(R.id.bottom_nav)
                    .setSelectedItemId(R.id.tab_favorites));
            screenshots.capture("favorites");
        }
    }

    /**
     * The query goes in the way an author tap puts it there, so the field has no focus: no
     * keyboard and no suggestion popup over the results.
     */
    private void captureSearchAndQuoteDialog() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launchMainAt(HEADLINE)) {
            scenario.onActivity(activity -> activity.showSearch(SEARCH));
            screenshots.capture("search");

            scenario.onActivity(activity -> {
                RecyclerView results = activity.findViewById(R.id.search_scroll);
                RecyclerView.ViewHolder result = results.findViewHolderForAdapterPosition(DIALOG_RESULT);
                assertNotNull("No search result #" + (DIALOG_RESULT + 1) + " for " + SEARCH.getText(), result);
                result.itemView.performClick();
            });
            screenshots.capture("quote-dialog");
        }
    }

    private void captureStatistics() throws Exception {
        List<Quote> installed = MyApplication.getInstance().getQuoteCollection().getQuoteList().getValue();
        int filled = 0;
        for (QuoteStatistics.MonthCount month : QuoteStatistics.of(installed).addedPerMonth) {
            if (month.count > 0) filled++;
        }
        assertTrue("Only " + filled + " of 12 months have demo quotes; the chart would look empty",
                filled >= MIN_FILLED_MONTHS);
        try (ActivityScenario<StatisticsActivity> ignored = ActivityScenario.launch(StatisticsActivity.class)) {
            screenshots.capture("statistics");
        }
    }

    private void captureCategories() throws Exception {
        try (ActivityScenario<CategoriesActivity> ignored = ActivityScenario.launch(CategoriesActivity.class)) {
            screenshots.capture("categories");
        }
    }

    /** Refuses to run with a Drive account connected, whose email Settings would show. */
    private void captureSettings() throws Exception {
        String email = DriveAuth.getConnectedAccountEmail(context);
        assertTrue("A Drive account is connected on this emulator; its email would end up in"
                + " settings.png. Disconnect it in Settings first.", email == null || email.isEmpty());
        try (ActivityScenario<SettingsActivity> ignored = ActivityScenario.launch(SettingsActivity.class)) {
            screenshots.capture("settings");
        }
    }

    /**
     * Starts MainActivity on the Quotes tab at the given quote, with a freshly seeded deck.
     * Opening at a quote also keeps the swipe nudge from playing.
     */
    private ActivityScenario<MainActivity> launchMainAt(String quoteTextStart) {
        MyApplication.getInstance().setShuffleRandom(new Random(DECK_SEED));
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(QuoteNotifications.EXTRA_QUOTE_ID,
                        DemoCollection.find(demoQuotes, quoteTextStart).getId());
        return ActivityScenario.launch(intent);
    }

    private static void shell(String command) throws IOException {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // Reading the output to the end waits for the command to finish.
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) { /* drain */ }
        }
    }
}
