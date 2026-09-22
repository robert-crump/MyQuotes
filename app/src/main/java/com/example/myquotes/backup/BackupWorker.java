package com.example.myquotes.backup;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myquotes.Quote;
import com.example.myquotes.MyApplication;
import com.example.myquotes.drive.DriveBackup;

import java.util.List;

/** Runs one {@link BackupRun} for the {@link BackupTarget} named in the input data, then re-arms. */
// Must be public: WorkManager instantiates it via reflection.
public class BackupWorker extends Worker {
    private static final String TAG = "BackupWorker";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        BackupTarget target = BackupTarget.fromId(getInputData().getString(BackupScheduler.KEY_TARGET));
        if (target == null) return Result.success();

        BackupDestination destination = target == BackupTarget.LOCAL
                ? LocalBackup.destinationIfReady(context)
                : DriveBackup.destinationIfConnected(context);
        if (destination == null) {
            Log.d(TAG, target.id + " backup is disabled or not set up, ending chain");
            return Result.success();
        }

        List<Quote> quotes = MyApplication.getInstance().getQuoteStore().load();
        if (!quotes.isEmpty()) {
            BackupRun.Outcome outcome = BackupRun.run(quotes, destination, System.currentTimeMillis());
            switch (outcome.kind) {
                case WRITTEN:
                    BackupState.record(context, target, System.currentTimeMillis());
                    BackupNotifications.clearFailed(context, target);
                    Log.d(TAG, target.id + " backup written: " + outcome.filename);
                    break;
                case SKIPPED_UNCHANGED:
                    Log.d(TAG, "No changes since last " + target.id + " backup, skipping");
                    break;
                case FAILED:
                    Log.e(TAG, target.id + " backup failed", outcome.cause);
                    BackupNotifications.notifyFailed(context, target);
                    break;
            }
        } else {
            Log.d(TAG, "No quotes to back up, skipping");
        }

        // Always re-arm, and always report success: a failed run must not break the chain (the
        // appended successor would be cancelled with it). Failure is surfaced via notification.
        BackupScheduler.rearmFromWorker(context, target);
        return Result.success();
    }
}
