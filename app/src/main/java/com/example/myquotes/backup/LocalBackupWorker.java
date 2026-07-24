package com.example.myquotes.backup;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myquotes.Quote;
import com.example.myquotes.QuoteCodec;
import com.example.myquotes.QuoteExporter;
import com.example.myquotes.QuotePreferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

// Must be public: WorkManager instantiates it via reflection.
public class LocalBackupWorker extends Worker {
    private static final String TAG = "LocalBackupWorker";
    private static final String BACKUP_FILENAME = "MyQuotes-backup.json";

    public LocalBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!LocalBackup.isEnabled(context)) {
            Log.d(TAG, "Auto-backup is disabled, skipping");
            return Result.success();
        }

        Uri folderUri = LocalBackup.getFolderUri(context);
        if (folderUri == null) {
            Log.w(TAG, "No backup folder selected, skipping");
            return Result.success();
        }

        // Load quotes directly from SharedPreferences (NOT QuoteCollection -- fixes race condition)
        QuotePreferences quotePrefs = new QuotePreferences(context);
        List<Quote> quotes = quotePrefs.loadQuotes();
        if (quotes == null || quotes.isEmpty()) {
            Log.d(TAG, "No quotes to back up, skipping");
            return Result.success();
        }

        try {
            String hash = sha256(QuoteCodec.encode(quotes));
            if (hash.equals(LocalBackup.getLastBackupHash(context))) {
                Log.d(TAG, "No changes since last backup, skipping");
                return Result.success();
            }

            DocumentFile folder = DocumentFile.fromTreeUri(context, folderUri);
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                throw new IOException("Backup folder is not accessible");
            }

            DocumentFile file = folder.findFile(BACKUP_FILENAME);
            if (file == null) {
                file = folder.createFile("application/json", BACKUP_FILENAME);
            }
            if (file == null) {
                throw new IOException("Could not create backup file");
            }

            QuoteExporter.writeToUri(context, file.getUri(), quotes);
            LocalBackup.recordSuccessfulBackup(context, hash);
            Log.d(TAG, "Backup written: " + BACKUP_FILENAME);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            LocalBackup.notifyBackupFailed(context);
            return Result.failure();
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
