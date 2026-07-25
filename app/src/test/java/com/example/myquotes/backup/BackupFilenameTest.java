package com.example.myquotes.backup;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BackupFilenameTest {

    @Test
    public void formatsTimestampWithExpectedSuffix() {
        long millis = new GregorianCalendar(2026, Calendar.JULY, 25, 9, 43).getTimeInMillis();
        assertEquals("260725-0943 MyQuotes.json", BackupFilename.forTimestamp(millis));
    }

    @Test
    public void parsesItsOwnFormattedFilename() {
        long millis = new GregorianCalendar(2026, Calendar.JULY, 25, 9, 43).getTimeInMillis();
        String filename = BackupFilename.forTimestamp(millis);
        assertEquals(Long.valueOf(millis), BackupFilename.parseTimestamp(filename));
    }

    @Test
    public void rejectsUnrelatedFilenames() {
        assertNull(BackupFilename.parseTimestamp("readme.txt"));
        assertNull(BackupFilename.parseTimestamp("MyQuotes-backup.json"));
    }

    @Test
    public void rejectsMalformedTimestampPrefix() {
        assertNull(BackupFilename.parseTimestamp("not-a-date MyQuotes.json"));
    }

    @Test
    public void rejectsNullFilename() {
        assertNull(BackupFilename.parseTimestamp(null));
    }
}
