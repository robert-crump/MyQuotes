package com.example.myquotes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;

public class BackgroundPermissionHelper {

    public static boolean isBackgroundUnrestricted(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static void showBackgroundPermissionDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.bg_permission_title)
                .setMessage(R.string.bg_permission_message)
                .setPositiveButton(R.string.bg_permission_ok, (dialog, which) -> openBatterySettings(context))
                .setNegativeButton(R.string.bg_permission_ignore, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private static void openBatterySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
    }
}
