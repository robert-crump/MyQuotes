package com.example.myquotes.drive;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myquotes.Quote;
import com.example.myquotes.QuoteCodec;
import com.example.myquotes.QuotePreferences;
import com.example.myquotes.backup.BackupFilename;
import com.example.myquotes.backup.BackupRetention;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Must be public: WorkManager instantiates it via reflection.
public class DriveBackupWorker extends Worker {
    private static final String TAG = "DriveBackupWorker";

    /** The app-owned folder name created under My Drive on first upload. */
    private static final String BACKUP_FOLDER_NAME = "MyQuotes Backups";

    public DriveBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!DriveAuth.isEnabled(context)) {
            Log.d(TAG, "Drive is not connected, skipping");
            return Result.success();
        }

        // Load quotes directly from SharedPreferences (NOT QuoteCollection -- same race-avoidance
        // as LocalBackupWorker).
        QuotePreferences quotePrefs = new QuotePreferences(context);
        List<Quote> quotes = quotePrefs.loadQuotes();
        if (quotes == null || quotes.isEmpty()) {
            Log.d(TAG, "No quotes to back up, skipping");
            return Result.success();
        }

        try {
            String encoded = QuoteCodec.encode(quotes);
            String hash = sha256(encoded);
            if (hash.equals(DriveBackup.getLastBackupHash(context))) {
                Log.d(TAG, "No changes since last Drive backup, skipping");
                return Result.success();
            }

            String accessToken = DriveAuth.getAccessToken(context);
            DriveRestClient client = new DriveRestClient(accessToken);

            String folderId = client.findOrCreateFolder(BACKUP_FOLDER_NAME);

            String filename = BackupFilename.forTimestamp(System.currentTimeMillis());
            client.uploadFile(folderId, filename, encoded.getBytes(StandardCharsets.UTF_8), "application/json");

            pruneOldBackups(client, folderId);

            DriveBackup.recordSuccessfulBackup(context, hash);
            Log.d(TAG, "Drive backup written: " + filename);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Drive backup failed", e);
            DriveBackup.notifyBackupFailed(context);
            return Result.failure();
        }
    }

    /** Enforces the 7-daily + 1-weekly + 1-monthly cap by deleting the oldest excess backups. */
    private static void pruneOldBackups(DriveRestClient client, String folderId) throws Exception {
        List<DriveRestClient.DriveFile> files = client.listFiles(folderId);

        Map<DriveRestClient.DriveFile, Long> timestamps = new HashMap<>();
        List<DriveRestClient.DriveFile> backups = new ArrayList<>();
        for (DriveRestClient.DriveFile file : files) {
            Long timestamp = BackupFilename.parseTimestamp(file.name);
            if (timestamp != null) {
                backups.add(file);
                timestamps.put(file, timestamp);
            }
        }

        List<DriveRestClient.DriveFile> toDelete = BackupRetention.selectForDeletion(
                backups, Comparator.comparingLong(timestamps::get));
        for (DriveRestClient.DriveFile file : toDelete) {
            client.deleteFile(file.id);
        }
    }

    private static String sha256(String input) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
