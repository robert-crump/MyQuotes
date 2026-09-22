package com.example.myquotes.backup;

import java.io.IOException;
import java.util.List;

/** Where a {@link BackupRun} puts its files: a SAF folder or a Drive folder. */
public interface BackupDestination {
    /** An existing file at the destination; {@code handle} is opaque and only meaningful to {@link #delete}. */
    final class Entry {
        public final String name;
        public final Object handle;

        public Entry(String name, Object handle) {
            this.name = name;
            this.handle = handle;
        }
    }

    /** Throws if the folder is no longer accessible / the account is no longer authorized. */
    void checkAvailable() throws IOException;

    void write(String filename, byte[] bytes) throws IOException;

    List<Entry> list() throws IOException;

    byte[] read(Entry entry) throws IOException;

    void delete(Entry entry) throws IOException;
}
