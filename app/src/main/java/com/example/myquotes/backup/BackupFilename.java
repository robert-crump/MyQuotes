package com.example.myquotes.backup;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates and parses the timestamped filenames used for auto-backup files, matching the
 * manual-export naming convention ("yyMMdd-HHmm MyQuotes.json"). Shared across backup
 * destinations (local folder, Google Drive).
 */
public final class BackupFilename {
    private static final String PATTERN = "yyMMdd-HHmm";
    private static final String SUFFIX = " MyQuotes.json";

    private BackupFilename() {}

    public static String forTimestamp(long epochMillis) {
        return format().format(new Date(epochMillis)) + SUFFIX;
    }

    /**
     * Returns the backup's timestamp in millis, or null if {@code filename} doesn't match the
     * expected pattern (e.g. an unrelated file sitting in the backup folder).
     */
    public static Long parseTimestamp(String filename) {
        if (filename == null || !filename.endsWith(SUFFIX)) return null;

        String prefix = filename.substring(0, filename.length() - SUFFIX.length());
        SimpleDateFormat sdf = format();
        sdf.setLenient(false);
        try {
            Date date = sdf.parse(prefix);
            // Reject partial matches (e.g. trailing garbage SimpleDateFormat silently ignored).
            return date != null && sdf.format(date).equals(prefix) ? date.getTime() : null;
        } catch (ParseException e) {
            return null;
        }
    }

    private static SimpleDateFormat format() {
        return new SimpleDateFormat(PATTERN, Locale.getDefault());
    }
}
