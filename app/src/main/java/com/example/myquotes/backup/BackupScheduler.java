package com.example.myquotes.backup;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;

import com.example.myquotes.scheduling.WorkChain;

import java.util.concurrent.TimeUnit;

/** Runs a destination's backups as a {@link WorkChain}: one run soon after arming, then every 24 h. */
public final class BackupScheduler {
    private static final String TAG = "BackupScheduler";
    static final String KEY_TARGET = "target";
    private static final long INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24);

    private BackupScheduler() {}

    /** Entry-point arm (enable / app start): runs right away if nothing is pending. */
    public static void arm(Context context, BackupTarget target) {
        WorkChain.arm(context, target.workName, BackupWorker.class, 0, input(target), constraints(target));
        Log.d(TAG, "Armed " + target.id + " backup");
    }

    /** Called by the worker after each run to schedule the next one a day out. */
    static void rearmFromWorker(Context context, BackupTarget target) {
        WorkChain.rearmFromWorker(context, target.workName, BackupWorker.class,
                INTERVAL_MILLIS, input(target), constraints(target));
    }

    public static void cancel(Context context, BackupTarget target) {
        WorkChain.cancel(context, target.workName);
        Log.d(TAG, "Cancelled " + target.id + " backup");
    }

    /** Wire up the channel and retire the pre-#23 periodic job, whatever the enabled state. */
    public static void prepare(Context context, BackupTarget target) {
        BackupNotifications.createChannel(context, target);
        WorkChain.cancel(context, target.legacyWorkName);
    }

    private static Data input(BackupTarget target) {
        return new Data.Builder().putString(KEY_TARGET, target.id).build();
    }

    private static Constraints constraints(BackupTarget target) {
        return target.requiresNetwork
                ? new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                : null;
    }
}
