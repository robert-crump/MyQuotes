package com.example.myquotes;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.example.myquotes.drive.DriveAuth;
import com.example.myquotes.notifications.QuoteNotifications;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.After;
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
 * copies the PNGs to {@code docs/screenshots/}. Every screen is taken in the light theme and again
 * in the dark one ({@code <name>-dark.png}); the app's theme setting is restored afterwards.
 * Replaces the app's quotes (and so its hashtags), so {@link EmulatorOnlyRule} skips it without the
 * argument and refuses real devices.
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
    // The user's theme setting, put back after the run; null until it has been read.
    private Integer previousThemeMode;
    // Appended to every screenshot name: "" for the light set, "-dark" for the dark one.
    private String suffix = "";

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        previousThemeMode = MyApplication.getInstance().getThemeMode();
        screenshots = ReadmeScreenshotCapture.cleared(context);
        // Otherwise MainActivity asks to ignore battery optimizations on every start.
        shell("dumpsys deviceidle whitelist +" + context.getPackageName());
        demoQuotes = DemoCollection.load(System.currentTimeMillis());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> DemoCollection.install(context, demoQuotes));
    }

    /** Runs even when a capture fails, so the emulator app isn't left in the screenshot theme. */
    @After
    public void restoreTheme() {
        if (previousThemeMode != null) setThemeMode(previousThemeMode);
    }

    @Test
    public void captureReadmeScreenshots() throws Exception {
        // Forced light, not the setting as found: "Follow system" could be dark on the emulator.
        setThemeMode(AppCompatDelegate.MODE_NIGHT_NO);
        suffix = "";
        captureAllScreens();

        setThemeMode(AppCompatDelegate.MODE_NIGHT_YES);
        suffix = "-dark";
        captureAllScreens();
    }

    /** Each screen gets its own Activity, closed even when its capture fails. */
    private void captureAllScreens() throws Exception {
        captureQuotesTab();
        captureFavoritesTab();
        captureSearchAndQuoteDialog();
        captureStatistics();
        captureHashtags();
        captureSettings();
    }

    private void captureQuotesTab() throws Exception {
        try (ActivityScenario<MainActivity> ignored = launchMainAt(HEADLINE)) {
            capture("quotes");
        }
    }

    /** Opens at the most recently favorited demo quote. */
    private void captureFavoritesTab() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launchMainAt(HEADLINE)) {
            scenario.onActivity(activity -> activity.<BottomNavigationView>findViewById(R.id.bottom_nav)
                    .setSelectedItemId(R.id.tab_favorites));
            capture("favorites");
        }
    }

    /**
     * The query goes in the way an author tap puts it there, so the field has no focus: no
     * keyboard and no suggestion popup over the results.
     */
    private void captureSearchAndQuoteDialog() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launchMainAt(HEADLINE)) {
            scenario.onActivity(activity -> activity.showSearch(SEARCH));
            capture("search");

            scenario.onActivity(activity -> {
                RecyclerView results = activity.findViewById(R.id.search_scroll);
                RecyclerView.ViewHolder result = results.findViewHolderForAdapterPosition(DIALOG_RESULT);
                assertNotNull("No search result #" + (DIALOG_RESULT + 1) + " for " + SEARCH.getText(), result);
                result.itemView.performClick();
            });
            capture("quote-dialog");
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
            capture("statistics");
        }
    }

    private void captureHashtags() throws Exception {
        try (ActivityScenario<HashtagsActivity> ignored = ActivityScenario.launch(HashtagsActivity.class)) {
            capture("hashtags");
        }
    }

    /** Refuses to run with a Drive account connected, whose email Settings would show. */
    private void captureSettings() throws Exception {
        String email = DriveAuth.getConnectedAccountEmail(context);
        assertTrue("A Drive account is connected on this emulator; its email would end up in"
                + " settings.png. Disconnect it in Settings first.", email == null || email.isEmpty());
        try (ActivityScenario<SettingsActivity> ignored = ActivityScenario.launch(SettingsActivity.class)) {
            capture("settings");
        }
    }

    private void capture(String name) throws Exception {
        screenshots.capture(name + suffix);
    }

    /**
     * Through the app's own setting, as the Settings screen does. Activities launched afterwards
     * start in that mode; none is open in between, since every screen closes its scenario.
     */
    private static void setThemeMode(int mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> MyApplication.getInstance().setThemeMode(mode));
    }

    /**
     * Starts MainActivity on the Quotes tab at the given quote, with a freshly seeded deck.
     * Opening at a quote also keeps the swipe nudge from playing. The deck is reseeded on every
     * launch, so the light and dark screenshots show the same quotes.
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
