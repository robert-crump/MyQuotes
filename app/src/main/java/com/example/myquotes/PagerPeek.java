package com.example.myquotes;

import android.view.View;

import androidx.annotation.DimenRes;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Makes a quote pager show the edges of its previous and next cards, scaled down and faded, as a
 * cue that the deck can be swiped.
 */
public final class PagerPeek {
    private static final float MIN_SCALE = 0.92f;
    private static final float MIN_ALPHA = 0.6f;

    private PagerPeek() {}

    /**
     * @param peek   how much of each neighbour card is visible at the screen edge
     * @param margin the gap between two cards
     */
    public static void apply(ViewPager2 pager, @DimenRes int peek, @DimenRes int margin) {
        int peekPx = pager.getResources().getDimensionPixelOffset(peek);
        int marginPx = pager.getResources().getDimensionPixelOffset(margin);

        RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
        // MarginPageTransformer pushes neighbours out by the margin, so pad by peek + margin.
        recyclerView.setPadding(peekPx + marginPx, 0, peekPx + marginPx, 0);
        recyclerView.setClipToPadding(false);
        pager.setOffscreenPageLimit(1);

        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(marginPx));
        transformer.addTransformer(PagerPeek::recede);
        pager.setPageTransformer(transformer);
        // Freshly bound pages are only transformed on the next scroll; do it after every layout.
        recyclerView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> pager.requestTransform());
    }

    /** Scales and fades a page by its distance from the centre, keeping its inner edge fixed. */
    private static void recede(View page, float position) {
        float distance = Math.min(1f, Math.abs(position));
        boolean rtl = page.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        boolean onRight = (position > 0) != rtl;
        // Pivot on the edge facing the centre card, so the visible peek stays the same width.
        page.setPivotX(position == 0 ? page.getWidth() / 2f : onRight ? 0 : page.getWidth());
        page.setPivotY(page.getHeight() / 2f);
        float scale = 1f - (1f - MIN_SCALE) * distance;
        page.setScaleX(scale);
        page.setScaleY(scale);
        page.setAlpha(1f - (1f - MIN_ALPHA) * distance);
    }
}
