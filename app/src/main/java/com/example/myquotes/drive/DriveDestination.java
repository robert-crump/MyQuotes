package com.example.myquotes.drive;

import android.content.Context;

import com.example.myquotes.backup.BackupDestination;

import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The app-owned "MyQuotes Backups" folder under My Drive. Network and auth happen lazily in
 * {@link #checkAvailable}, which creates the folder if it is missing.
 */
final class DriveDestination implements BackupDestination {
    private static final String BACKUP_FOLDER_NAME = "MyQuotes Backups";

    private final Context context;
    private DriveRestClient client;
    private String folderId;

    DriveDestination(Context context) {
        this.context = context;
    }

    @Override
    public void checkAvailable() throws IOException {
        try {
            client = new DriveRestClient(DriveAuth.getAccessToken(context));
            folderId = client.findOrCreateFolder(BACKUP_FOLDER_NAME);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Drive is not available: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(String filename, byte[] bytes) throws IOException {
        try {
            client.uploadFile(folderId, filename, bytes, "application/json");
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Entry> list() throws IOException {
        try {
            List<Entry> entries = new ArrayList<>();
            for (DriveRestClient.DriveFile file : client.listFiles(folderId)) {
                entries.add(new Entry(file.name, file));
            }
            return entries;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(Entry entry) throws IOException {
        client.deleteFile(((DriveRestClient.DriveFile) entry.handle).id);
    }
}
