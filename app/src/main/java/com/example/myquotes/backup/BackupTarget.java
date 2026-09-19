package com.example.myquotes.backup;

import com.example.myquotes.R;

/**
 * The per-destination constants: state file, work name, failure channel and notification. Ids and
 * pref file names are frozen so upgrading users keep their state.
 */
public enum BackupTarget {
    LOCAL("local", "LocalBackupPrefs", "backup_local", "local_daily_backup",
            "backup_failure_channel", "Backup Alerts", "Alerts when an automatic quote backup fails",
            1002, R.string.local_backup_failed_title, R.string.local_backup_failed_message, false),
    DRIVE("drive", "DriveBackupPrefs", "backup_drive", "drive_daily_backup",
            "drive_backup_failure_channel", "Drive Backup Alerts",
            "Alerts when an automatic Google Drive backup fails",
            1003, R.string.drive_backup_failed_title, R.string.drive_backup_failed_message, true);

    final String id;
    final String prefsName;
    final String workName;
    /** Name of the retired PeriodicWorkRequest, cancelled on startup. */
    final String legacyWorkName;
    final String channelId;
    final String channelName;
    final String channelDescription;
    final int notificationId;
    final int failedTitleRes;
    final int failedMessageRes;
    final boolean requiresNetwork;

    BackupTarget(String id, String prefsName, String workName, String legacyWorkName,
                 String channelId, String channelName, String channelDescription,
                 int notificationId, int failedTitleRes, int failedMessageRes, boolean requiresNetwork) {
        this.id = id;
        this.prefsName = prefsName;
        this.workName = workName;
        this.legacyWorkName = legacyWorkName;
        this.channelId = channelId;
        this.channelName = channelName;
        this.channelDescription = channelDescription;
        this.notificationId = notificationId;
        this.failedTitleRes = failedTitleRes;
        this.failedMessageRes = failedMessageRes;
        this.requiresNetwork = requiresNetwork;
    }

    static BackupTarget fromId(String id) {
        for (BackupTarget target : values()) {
            if (target.id.equals(id)) return target;
        }
        return null;
    }
}
