package com.example.myquotes;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * The first-run swipe nudge on the Quotes tab: the current card slides a little toward the next
 * one and springs back, on every app start until the user has swiped the deck once by hand.
 */
public final class SwipeHint {
    static final String PREFS_NAME = "UiPrefs";
    static final String KEY_HAS_SWIPED = "has_swiped";

    private static final float NUDGE_FRACTION = 0.15f;
    private static final long START_DELAY_MS = 600;
    private static final long DURATION_MS = 900;

    private SwipeHint() {}

    /** Whether the nudge should play for a Quotes tab showing a deck of this size. */
    public static boolean shouldShow(boolean hasSwiped, boolean fromNotification, int deckSize,
                                     boolean animationsEnabled) {
        return !hasSwiped && !fromNotification && deckSize >= 2 && animationsEnabled;
    }

    public static boolean hasSwiped(Context context) {
        return prefs(context).getBoolean(KEY_HAS_SWIPED, false);
    }

    public static void markSwiped(Context context) {
        prefs(context).edit().putBoolean(KEY_HAS_SWIPED, true).apply();
    }

    /** False when the user has turned animations off (Developer options / reduced motion). */
    public static boolean animationsEnabled(Context context) {
        return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }

    /**
     * Fake-drags the current page {@link #NUDGE_FRACTION} of the way to the next one and back.
     * The page never changes, so no onPageSelected fires. Returns the animator so it can be
     * cancelled when the view goes away.
     */
    public static Animator play(ViewPager2 pager) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f, 0f);
        animator.setStartDelay(START_DELAY_MS);
        animator.setDuration(DURATION_MS);
        // Eases out at the end, so endFakeDrag sees no fling velocity and settles on this page.
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        float distance = pager.getWidth() * NUDGE_FRACTION;
        float[] dragged = {0f};
        boolean[] began = {false};
        animator.addUpdateListener(animation -> {
            if (!began[0]) {
                // Begun on the first frame, after the start delay; fails if the user is dragging.
                began[0] = true;
                if (!pager.beginFakeDrag()) {
                    animation.cancel();
                    return;
                }
            }
            if (!pager.isFakeDragging()) return;
            float target = -distance * (float) animation.getAnimatedValue(); // negative = next page
            pager.fakeDragBy(target - dragged[0]);
            dragged[0] = target;
        });
        // A fake drag makes no touch events, so watch for a real finger and get out of its way.
        RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
        RecyclerView.OnItemTouchListener touchCancels = new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) animator.cancel();
                return false;
            }
        };
        recyclerView.addOnItemTouchListener(touchCancels);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                recyclerView.removeOnItemTouchListener(touchCancels);
                if (pager.isFakeDragging()) pager.endFakeDrag();
            }
        });
        animator.start();
        return animator;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
