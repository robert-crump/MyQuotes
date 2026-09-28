package com.example.myquotes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LoopingPositionsTest {

    @Test
    public void count_doesNotLoopEmptyOrSingleLists() {
        assertEquals(0, LoopingPositions.count(0));
        assertEquals(1, LoopingPositions.count(1));
    }

    @Test
    public void count_repeatsLongerListsForEveryLap() {
        assertEquals(5 * LoopingPositions.LAPS, LoopingPositions.count(5));
    }

    @Test
    public void indexOf_roundTripsThroughPagerPositionOf() {
        int around = LoopingPositions.pagerPositionOf(0, 5, 0);
        for (int index = 0; index < 5; index++) {
            int pos = LoopingPositions.pagerPositionOf(index, 5, around);
            assertEquals(index, LoopingPositions.indexOf(pos, 5));
        }
    }

    @Test
    public void indexOf_wrapsAcrossLaps() {
        assertEquals(4, LoopingPositions.indexOf(5 * 10 - 1, 5));
        assertEquals(0, LoopingPositions.indexOf(5 * 10, 5));
    }

    @Test
    public void pagerPositionOf_staysInTheLapOfAround() {
        int around = 5 * 300 + 2;
        assertEquals(5 * 300 + 4, LoopingPositions.pagerPositionOf(4, 5, around));
    }

    @Test
    public void pagerPositionOf_startsFromTheMiddleLapNearEitherEnd() {
        int middle = 5 * (LoopingPositions.LAPS / 2);
        assertEquals(middle + 3, LoopingPositions.pagerPositionOf(3, 5, 0));
        assertEquals(middle + 3, LoopingPositions.pagerPositionOf(3, 5, 4));
        assertEquals(middle + 3, LoopingPositions.pagerPositionOf(3, 5, LoopingPositions.count(5) - 1));
    }

    @Test
    public void pagerPositionOf_isTheIndexForSingleLists() {
        assertEquals(0, LoopingPositions.pagerPositionOf(0, 1, 0));
    }
}
