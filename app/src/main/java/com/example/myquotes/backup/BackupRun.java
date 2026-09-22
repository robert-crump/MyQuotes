package com.example.myquotes.backup;

import com.example.myquotes.Quote;
import com.example.myquotes.QuoteCodec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One backup of the quote list to one {@link BackupDestination}: encode, hash the bytes that will
 * be written, skip if that matches the most recent backup already at the destination, otherwise
 * write a timestamped file and prune to {@link BackupRetention}. The "unchanged" comparison reads
 * the destination's own latest file rather than trusting locally cached state, so a stale local
 * cache (e.g. state that didn't get persisted before the process died) can't cause a duplicate.
 * Deliberately free of Android state: recording the outcome is the caller's job.
 */
public final class BackupRun {
    public enum Kind { SKIPPED_UNCHANGED, WRITTEN, FAILED }

    public static final class Outcome {
        public final Kind kind;
        /** SHA-256 of the written bytes; set only for {@link Kind#WRITTEN}. */
        public final String hash;
        public final String filename;
        public final Exception cause;

        private Outcome(Kind kind, String hash, String filename, Exception cause) {
            this.kind = kind;
            this.hash = hash;
            this.filename = filename;
            this.cause = cause;
        }
    }

    private BackupRun() {}

    public static Outcome run(List<Quote> quotes, BackupDestination destination, long nowMillis) {
        try {
            // Pretty-printed to match the manual export; the hash covers exactly these bytes.
            byte[] bytes = QuoteCodec.encodePretty(quotes).getBytes(StandardCharsets.UTF_8);
            String hash = sha256(bytes);
            destination.checkAvailable();

            BackupDestination.Entry latest = latestBackup(destination);
            // A new account or a hand-deleted folder has no latest backup, so it gets one even
            // though the content matches what used to be there.
            if (latest != null && hash.equals(sha256(destination.read(latest)))) {
                return new Outcome(Kind.SKIPPED_UNCHANGED, null, null, null);
            }

            String filename = BackupFilename.forTimestamp(nowMillis);
            destination.write(filename, bytes);
            prune(destination);
            return new Outcome(Kind.WRITTEN, hash, filename, null);
        } catch (Exception e) {
            return new Outcome(Kind.FAILED, null, null, e);
        }
    }

    private static BackupDestination.Entry latestBackup(BackupDestination destination) throws Exception {
        BackupDestination.Entry latest = null;
        long latestTimestamp = Long.MIN_VALUE;
        for (BackupDestination.Entry entry : destination.list()) {
            Long timestamp = BackupFilename.parseTimestamp(entry.name);
            if (timestamp != null && timestamp > latestTimestamp) {
                latestTimestamp = timestamp;
                latest = entry;
            }
        }
        return latest;
    }

    private static void prune(BackupDestination destination) throws Exception {
        Map<BackupDestination.Entry, Long> timestamps = new HashMap<>();
        List<BackupDestination.Entry> backups = new ArrayList<>();
        for (BackupDestination.Entry entry : destination.list()) {
            Long timestamp = BackupFilename.parseTimestamp(entry.name);
            if (timestamp != null) {
                backups.add(entry);
                timestamps.put(entry, timestamp);
            }
        }

        for (BackupDestination.Entry entry : BackupRetention.selectForDeletion(
                backups, Comparator.comparingLong(timestamps::get))) {
            destination.delete(entry);
        }
    }

    private static String sha256(byte[] input) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
