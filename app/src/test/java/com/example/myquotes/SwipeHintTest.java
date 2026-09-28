package com.example.myquotes;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SwipeHintTest {

    @Test
    public void showsOnNormalStartBeforeFirstSwipe() {
        assertTrue(SwipeHint.shouldShow(false, false, 2, true));
    }

    @Test
    public void neverShowsAfterUserHasSwiped() {
        assertFalse(SwipeHint.shouldShow(true, false, 100, true));
    }

    @Test
    public void doesNotShowWhenOpenedFromNotification() {
        assertFalse(SwipeHint.shouldShow(false, true, 100, true));
    }

    @Test
    public void needsAtLeastTwoQuotes() {
        assertFalse(SwipeHint.shouldShow(false, false, 0, true));
        assertFalse(SwipeHint.shouldShow(false, false, 1, true));
    }

    @Test
    public void respectsDisabledAnimations() {
        assertFalse(SwipeHint.shouldShow(false, false, 100, false));
    }
}
