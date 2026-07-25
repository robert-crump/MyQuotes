package com.example.myquotes.backup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses which backups to delete once more than {@link #TOTAL_COUNT} exist for a destination.
 * There's no separate weekly/monthly write: the most recent {@link #DAILY_COUNT} are always
 * kept, and the weekly/monthly slots are simply the next 2 most recent backups after that,
 * naturally filled by backups aging out of the daily window as newer ones displace them.
 */
final class BackupRetention {
    static final int DAILY_COUNT = 7;
    static final int WEEKLY_COUNT = 1;
    static final int MONTHLY_COUNT = 1;
    static final int TOTAL_COUNT = DAILY_COUNT + WEEKLY_COUNT + MONTHLY_COUNT;

    private BackupRetention() {}

    /** Returns the subset of {@code backups} that should be deleted to enforce {@link #TOTAL_COUNT}. */
    static <T> List<T> selectForDeletion(List<T> backups, Comparator<T> byTimestampAscending) {
        if (backups.size() <= TOTAL_COUNT) return Collections.emptyList();

        List<T> newestFirst = new ArrayList<>(backups);
        newestFirst.sort(byTimestampAscending.reversed());
        return new ArrayList<>(newestFirst.subList(TOTAL_COUNT, newestFirst.size()));
    }
}
