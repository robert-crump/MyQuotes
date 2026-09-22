package com.example.myquotes.backup;

import com.example.myquotes.Quote;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BackupRunTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    // Minute-aligned so the filename (minute resolution) round-trips exactly.
    private static final long NOW = 1_700_000_000_000L - (1_700_000_000_000L % 60_000L);

    private static class InMemoryDestination implements BackupDestination {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        boolean failWrites;

        @Override public void checkAvailable() {}

        @Override public void write(String filename, byte[] bytes) throws IOException {
            if (failWrites) throw new IOException("disk full");
            files.put(filename, bytes);
        }

        @Override public List<Entry> list() {
            List<Entry> entries = new ArrayList<>();
            for (String name : files.keySet()) entries.add(new Entry(name, name));
            return entries;
        }

        @Override public void delete(Entry entry) { files.remove((String) entry.handle); }

        @Override public byte[] read(Entry entry) { return files.get((String) entry.handle); }
    }

    private static List<Quote> quotes(String text) {
        return Collections.singletonList(new Quote(1, "Author", text, "Source"));
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    public void writesFileNamedForTheClockTimestamp() {
        InMemoryDestination dest = new InMemoryDestination();

        BackupRun.Outcome outcome = BackupRun.run(quotes("a"), dest, NOW);

        assertEquals(BackupRun.Kind.WRITTEN, outcome.kind);
        assertEquals(1, dest.files.size());
        assertEquals(outcome.filename, dest.files.keySet().iterator().next());
        assertEquals(Long.valueOf(NOW), BackupFilename.parseTimestamp(outcome.filename));
    }

    @Test
    public void recordedHashIsTheHashOfTheBytesTheDestinationReceived() throws Exception {
        InMemoryDestination dest = new InMemoryDestination();

        BackupRun.Outcome outcome = BackupRun.run(quotes("a"), dest, NOW);

        byte[] written = dest.files.get(outcome.filename);
        assertNotNull(written);
        assertEquals(sha256(written), outcome.hash);
        // Pretty-printed, matching the manual export.
        assertTrue(new String(written, StandardCharsets.UTF_8).contains("\n"));
    }

    @Test
    public void skipsWhenContentMatchesTheDestinationsLatestBackup() {
        InMemoryDestination dest = new InMemoryDestination();
        BackupRun.run(quotes("a"), dest, NOW);

        BackupRun.Outcome second = BackupRun.run(quotes("a"), dest, NOW + DAY);

        assertEquals(BackupRun.Kind.SKIPPED_UNCHANGED, second.kind);
        assertEquals(1, dest.files.size());
    }

    @Test
    public void skipsEvenWithoutLocallyCachedStateAsLongAsTheDestinationHasTheSameContent() {
        // Simulates a process death between a successful write and recording local state: no
        // local cache survives, but the destination itself still shows the same content, so the
        // next run must not write a duplicate.
        InMemoryDestination dest = new InMemoryDestination();
        BackupRun.run(quotes("a"), dest, NOW);

        BackupRun.Outcome retry = BackupRun.run(quotes("a"), dest, NOW + 60_000);

        assertEquals(BackupRun.Kind.SKIPPED_UNCHANGED, retry.kind);
        assertEquals(1, dest.files.size());
    }

    @Test
    public void writesWhenContentDiffersFromTheDestinationsLatestBackup() {
        InMemoryDestination dest = new InMemoryDestination();
        BackupRun.run(quotes("a"), dest, NOW);

        BackupRun.Outcome second = BackupRun.run(quotes("b"), dest, NOW + DAY);

        assertEquals(BackupRun.Kind.WRITTEN, second.kind);
        assertEquals(2, dest.files.size());
    }

    @Test
    public void writesDespiteMatchingContentWhenDestinationHasNoBackups() {
        InMemoryDestination previousAccount = new InMemoryDestination();
        BackupRun.run(quotes("a"), previousAccount, NOW);

        InMemoryDestination newAccount = new InMemoryDestination();
        BackupRun.Outcome outcome = BackupRun.run(quotes("a"), newAccount, NOW + DAY);

        assertEquals(BackupRun.Kind.WRITTEN, outcome.kind);
        assertEquals(1, newAccount.files.size());
    }

    @Test
    public void pruneConvergesOnNineFilesOverSimulatedDays() {
        InMemoryDestination dest = new InMemoryDestination();
        for (int day = 0; day < 15; day++) {
            BackupRun.Outcome outcome = BackupRun.run(quotes("v" + day), dest, NOW + day * DAY);
            assertEquals(BackupRun.Kind.WRITTEN, outcome.kind);
            assertTrue(dest.files.size() <= BackupRetention.TOTAL_COUNT);
        }
        assertEquals(BackupRetention.TOTAL_COUNT, dest.files.size());
        // The newest backup survives; the oldest ones are gone.
        assertTrue(dest.files.containsKey(BackupFilename.forTimestamp(NOW + 14 * DAY)));
        assertTrue(!dest.files.containsKey(BackupFilename.forTimestamp(NOW)));
    }

    @Test
    public void writeFailureYieldsFailedOutcomeAndNoHash() {
        InMemoryDestination dest = new InMemoryDestination();
        dest.failWrites = true;

        BackupRun.Outcome outcome = BackupRun.run(quotes("a"), dest, NOW);

        assertEquals(BackupRun.Kind.FAILED, outcome.kind);
        assertNotNull(outcome.cause);
        assertEquals(null, outcome.hash);
        assertTrue(dest.files.isEmpty());
    }
}
