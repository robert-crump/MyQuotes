package com.example.myquotes;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge system bar insets helper.
 *
 * Apps targeting API 35 (Android 15) have edge-to-edge enforced by the platform: the system
 * draws the status/navigation bars as a transparent overlay and ignores
 * android:statusBarColor/navigationBarColor, regardless of what the theme sets. Every screen's
 * root layout also has android:fitsSystemWindows removed (it no longer reserves space for the
 * bars once edge-to-edge is enforced), so each Activity calls {@link #apply} after
 * setContentView to size a dedicated status-bar scrim strip behind the transparent status bar
 * (?attr/statusBarScrimColor, the surface colour — see themes.xml; inside an AppBarLayout the
 * strip is transparent so the bar's lift-on-scroll colour reaches the top edge), and to pad the
 * rest of the content away from the navigation bar / side cutouts.
 */
final class EdgeToEdgeUtils {

    private EdgeToEdgeUtils() {}

    static void apply(Activity activity, View statusBarScrim) {
        apply(activity, statusBarScrim, false);
    }

    /**
     * @param padForIme also pad the bottom by the on-screen keyboard's height, so a screen
     *                  declared with adjustResize shrinks its content above the keyboard (with
     *                  edge-to-edge the window no longer resizes for the IME on its own).
     */
    static void apply(Activity activity, View statusBarScrim, boolean padForIme) {
        applyStatusBarScrim(activity, statusBarScrim);

        // Root content: pad left/right/bottom by the system bar insets so nothing sits under
        // the navigation bar or a side display cutout. Top is handled by the scrim above.
        View content = rootContent(activity);
        final int contentTop = content.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottom = systemBars.bottom;
            if (padForIme) {
                bottom = Math.max(bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            }
            v.setPadding(systemBars.left, contentTop, systemBars.right, bottom);
            return insets;
        });
    }

    /**
     * For a screen whose root ends in a BottomNavigationView: the root pads only left/right,
     * and the bottom nav pads itself for the navigation bar, so its background reaches the
     * bottom edge. Left/right are consumed here so the bottom nav doesn't pad them twice.
     */
    static void applyAboveBottomNav(Activity activity, View statusBarScrim) {
        applyStatusBarScrim(activity, statusBarScrim);

        View content = rootContent(activity);
        final int contentTop = content.getPaddingTop();
        final int contentBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, contentTop, systemBars.right, contentBottom);
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(),
                            Insets.of(0, systemBars.top, 0, systemBars.bottom))
                    .build();
        });
    }

    private static void applyStatusBarScrim(Activity activity, View statusBarScrim) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        // Status bar scrim: an initially-zero-height strip above the toolbar, grown to exactly
        // the status bar's inset height so it reads as a distinct strip, not the toolbar's own
        // background bleeding upward.
        ViewCompat.setOnApplyWindowInsetsListener(statusBarScrim, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params.height != systemBars.top) {
                params.height = systemBars.top;
                v.setLayoutParams(params);
            }
            return insets;
        });
    }

    private static View rootContent(Activity activity) {
        return ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
    }
}
