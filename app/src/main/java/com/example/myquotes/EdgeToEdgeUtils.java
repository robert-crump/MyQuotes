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
 * setContentView to size a dedicated status-bar scrim strip (?attr/statusBarScrimColor, a
 * darker "variant" of colorPrimary — see themes.xml) behind the transparent status bar, keeping
 * the pre-edge-to-edge two-tone look instead of the toolbar's own colorPrimary bleeding
 * straight through, and to pad the rest of the content away from the navigation bar / side
 * cutouts.
 */
final class EdgeToEdgeUtils {

    private EdgeToEdgeUtils() {}

    static void apply(Activity activity, View statusBarScrim) {
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

        // Root content: pad left/right/bottom by the system bar insets so nothing sits under
        // the navigation bar or a side display cutout. Top is handled by the scrim above.
        View content = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        final int contentTop = content.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, contentTop, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
