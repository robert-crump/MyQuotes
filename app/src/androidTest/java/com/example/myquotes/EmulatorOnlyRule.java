package com.example.myquotes;

import static org.junit.Assume.assumeTrue;

import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Guards tests that overwrite the app's data. Skips unless the instrumentation argument
 * {@code readmeScreenshots=true} is set, so a plain connectedAndroidTest never runs them, and
 * fails on anything that isn't an emulator, before any other rule or setup touches the device.
 * Put it outermost in the rule chain.
 */
public class EmulatorOnlyRule implements TestRule {
    static final String ARGUMENT = "readmeScreenshots";

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assumeTrue("Only runs with -e " + ARGUMENT + " true (./gradlew readmeScreenshots)",
                        "true".equals(InstrumentationRegistry.getArguments().getString(ARGUMENT)));
                if (!isEmulator()) {
                    throw new AssertionError("README screenshots replace the quote collection, so they"
                            + " only run on an emulator. This device (" + Build.MANUFACTURER + " "
                            + Build.MODEL + ") was left untouched.");
                }
                base.evaluate();
            }
        };
    }

    static boolean isEmulator() {
        return Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")
                || Build.PRODUCT.contains("sdk");
    }
}
