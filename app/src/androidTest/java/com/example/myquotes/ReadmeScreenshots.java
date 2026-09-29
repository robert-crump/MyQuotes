package com.example.myquotes;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.example.myquotes.notifications.QuoteNotifications;

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
        captureQuotesTab();
    }

    private void captureQuotesTab() throws Exception {
        try (ActivityScenario<MainActivity> ignored = launchMainAt(HEADLINE)) {
            screenshots.capture("quotes");
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
