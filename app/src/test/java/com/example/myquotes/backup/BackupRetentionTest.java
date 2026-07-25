package com.example.myquotes.backup;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackupRetentionTest {

    private static final Comparator<Long> ASCENDING = Comparator.naturalOrder();

    @Test
    public void keepsEverythingUnderTheCap() {
        assertTrue(BackupRetention.selectForDeletion(daysList(1, 5), ASCENDING).isEmpty());
    }

    @Test
    public void keepsExactlyNineAtTheCap() {
        assertTrue(BackupRetention.selectForDeletion(daysList(1, 9), ASCENDING).isEmpty());
    }

    @Test
    public void deletesOnlyTheOldestBackupBeyondTheCap() {
        List<Long> toDelete = BackupRetention.selectForDeletion(daysList(1, 10), ASCENDING);
        assertEquals(1, toDelete.size());
        assertEquals(Long.valueOf(1L), toDelete.get(0));
    }

    @Test
    public void deletesAllButTheNineMostRecent() {
        List<Long> toDelete = BackupRetention.selectForDeletion(daysList(1, 15), ASCENDING);
        assertEquals(6, toDelete.size());
        assertTrue(toDelete.containsAll(daysList(1, 6)));
    }

    @Test
    public void convergesToNineFilesOverManySimulatedDays() {
        List<Long> backups = new ArrayList<>();
        for (long day = 1; day <= 60; day++) {
            backups.add(day);
            backups.removeAll(BackupRetention.selectForDeletion(backups, ASCENDING));
            assertTrue("day " + day, backups.size() <= BackupRetention.TOTAL_COUNT);
        }
        assertEquals(BackupRetention.TOTAL_COUNT, backups.size());
        // The 9 survivors are always the most recent 9 days once history exceeds the cap.
        assertEquals(daysList(52, 60), sorted(backups));
    }

    @Test
    public void shorterHistoryConvergesToFewerThanNine() {
        List<Long> backups = new ArrayList<>();
        for (long day = 1; day <= 4; day++) {
            backups.add(day);
            backups.removeAll(BackupRetention.selectForDeletion(backups, ASCENDING));
        }
        assertEquals(4, backups.size());
    }

    private static List<Long> daysList(int from, int to) {
        List<Long> list = new ArrayList<>();
        for (int day = from; day <= to; day++) list.add((long) day);
        return list;
    }

    private static List<Long> sorted(List<Long> list) {
        List<Long> copy = new ArrayList<>(list);
        copy.sort(ASCENDING);
        return copy;
    }
}
