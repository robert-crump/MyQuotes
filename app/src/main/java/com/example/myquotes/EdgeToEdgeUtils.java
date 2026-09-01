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
 * setContentView to keep the toolbar's colorPrimary background extending up under the
 * status bar, and to pad the rest of the content away from the navigation bar / side cutouts.
 */
final class EdgeToEdgeUtils {

    private EdgeToEdgeUtils() {}

    static void apply(Activity activity, View toolbar) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        View content = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);

        // Toolbar: keep its own left/right/bottom padding, grow its top padding by the status
        // bar inset so the toolbar's colorPrimary background paints behind the transparent bar
        // while its title/icons still land below it.
        final int toolbarLeft = toolbar.getPaddingLeft();
        final int toolbarRight = toolbar.getPaddingRight();
        final int toolbarBottom = toolbar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(toolbarLeft, systemBars.top, toolbarRight, toolbarBottom);
            return insets;
        });

        // Root content: keep its own top padding (the toolbar already accounts for the status
        // bar), pad left/right/bottom by the system bar insets so nothing sits under the
        // navigation bar or a side display cutout.
        final int contentTop = content.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, contentTop, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
