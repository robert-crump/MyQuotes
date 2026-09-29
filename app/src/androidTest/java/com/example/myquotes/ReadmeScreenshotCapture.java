package com.example.myquotes;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.espresso.Espresso;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Saves whole-screen PNGs, status bar included, to the app's external files dir under
 * {@code readme-screenshots/}, where {@code ./gradlew readmeScreenshots} pulls them from.
 */
final class ReadmeScreenshotCapture {
    static final String DIRECTORY = "readme-screenshots";

    // Lets the pager's page transforms and any ripple or fade finish after the UI is idle.
    private static final long SETTLE_MS = 800;

    private final File directory;

    private ReadmeScreenshotCapture(File directory) {
        this.directory = directory;
    }

    /** An empty screenshot folder: whatever an earlier run left there is deleted. */
    static ReadmeScreenshotCapture cleared(Context context) throws IOException {
        File directory = new File(context.getExternalFilesDir(null), DIRECTORY);
        File[] old = directory.listFiles();
        if (old != null) {
            for (File file : old) {
                if (!file.delete()) throw new IOException("Could not delete " + file);
            }
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }
        return new ReadmeScreenshotCapture(directory);
    }

    /** Waits for the UI to settle, then saves the screen as {@code <name>.png}. */
    void capture(String name) throws IOException, InterruptedException {
        Espresso.onIdle();
        Thread.sleep(SETTLE_MS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap screen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (screen == null) throw new IOException("Screenshot of " + name + " failed");
        File file = new File(directory, name + ".png");
        try (OutputStream out = new FileOutputStream(file)) {
            if (!screen.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Could not write " + file);
            }
        } finally {
            screen.recycle();
        }
    }
}
