package com.example.myquotes.backup;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Runs one backup ({@link Backups#runOnce}) for the {@link BackupTarget} named in the input data, then re-arms. */
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

        if (!Backups.isReady(context, target)) {
            Log.d(TAG, target.id + " backup is disabled or not set up, ending chain");
            return Result.success();
        }

        BackupRun.Outcome outcome = Backups.runOnce(context, target);
        if (outcome == null) {
            Log.d(TAG, "No quotes to back up, skipping");
        } else {
            switch (outcome.kind) {
                case WRITTEN:
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
        }

        // Always re-arm, and always report success: a failed run must not break the chain (the
        // appended successor would be cancelled with it). Failure is surfaced via notification.
        BackupScheduler.rearmFromWorker(context, target);
        return Result.success();
    }
}
