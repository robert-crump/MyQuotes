package com.example.myquotes;

/**
 * Maps a quote list onto a looping pager: the list repeats {@link #LAPS} times, so there are cards
 * on both sides of every card, including the first. Lists of 0 or 1 quotes don't loop.
 */
public final class LoopingPositions {
    static final int LAPS = 1000;

    private LoopingPositions() {}

    /** The pager's item count for a list of this size. */
    public static int count(int size) {
        return size <= 1 ? size : size * LAPS;
    }

    /** The list index shown at this pager position. */
    public static int indexOf(int pagerPosition, int size) {
        return size <= 1 ? pagerPosition : pagerPosition % size;
    }

    /**
     * The pager position showing list {@code index} in the same lap as {@code around} (usually the
     * current item). Near either end of the range it starts over from the middle lap instead, so
     * there is always room to swipe both ways.
     */
    public static int pagerPositionOf(int index, int size, int around) {
        if (size <= 1) return index;
        int lap = around / size;
        if (lap <= 0 || lap >= LAPS - 1) lap = LAPS / 2;
        return lap * size + index;
    }
}
