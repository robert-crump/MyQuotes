package com.example.myquotes.scheduling;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * The self-rescheduling one-shot chain from ADR-002's amendment, shared by the daily notification
 * and every backup destination. WorkManager's PeriodicWorkRequest can silently lose its re-arm
 * (#21), so recurring jobs are a single OneTimeWorkRequest that re-enqueues its successor.
 *
 * <ul>
 *   <li>{@link #arm}: for entry points (app open, boot, enable). {@code KEEP} leaves a pending
 *       schedule alone but heals a missing one.</li>
 *   <li>{@link #rearmFromWorker}: for the worker itself, after a run. The running request still
 *       counts as unfinished work, so {@code KEEP} would be a no-op here; the successor is
 *       appended behind it instead. The worker must return success for the successor to run.</li>
 * </ul>
 */
public final class WorkChain {
    private WorkChain() {}

    public static void arm(Context context, String workName, Class<? extends ListenableWorker> worker,
                           long delayMillis, Data input, Constraints constraints) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                workName, ExistingWorkPolicy.KEEP, build(worker, delayMillis, input, constraints));
    }

    public static void rearmFromWorker(Context context, String workName,
                                       Class<? extends ListenableWorker> worker,
                                       long delayMillis, Data input, Constraints constraints) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                workName, ExistingWorkPolicy.APPEND_OR_REPLACE,
                build(worker, delayMillis, input, constraints));
    }

    public static void cancel(Context context, String workName) {
        WorkManager.getInstance(context).cancelUniqueWork(workName);
    }

    private static OneTimeWorkRequest build(Class<? extends ListenableWorker> worker, long delayMillis,
                                            Data input, Constraints constraints) {
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(worker)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS);
        if (input != null) builder.setInputData(input);
        if (constraints != null) builder.setConstraints(constraints);
        return builder.build();
    }
}
