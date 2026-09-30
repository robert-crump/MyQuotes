package com.example.myquotes;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.myquotes.backup.BackupFilename;
import com.example.myquotes.backup.LocalBackup;
import com.example.myquotes.databinding.ActivitySettingsBinding;
import com.example.myquotes.drive.DriveAuth;
import com.example.myquotes.drive.DriveBackup;
import com.example.myquotes.notifications.QuoteNotifications;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings, laid out like Hue and You's: a bold title above each group (Library, General,
 * Backup), a group being one outlined box of {@link SettingsRow}s. Rows open a screen or a
 * dialog, or toggle a switch.
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    /** App theme dialog entries, in this order. */
    private static final int[] THEME_MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES};
    private static final int[] THEME_LABELS = {
            R.string.settings_theme_system, R.string.settings_theme_light, R.string.settings_theme_dark};

    private ActivitySettingsBinding binding;
    private QuoteCollection quoteCollection;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;
    private ActivityResultLauncher<Intent> backupFolderLauncher;
    private ActivityResultLauncher<IntentSenderRequest> driveAuthorizationLauncher;

    private SettingsRow themeRow;
    private SettingsRow dailyNotificationRow;
    private SettingsRow localBackupRow;
    private SettingsRow driveBackupRow;
    private String pendingDriveEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.apply(this, binding.statusBarScrim);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        quoteCollection = MyApplication.getInstance().getQuoteCollection();

        // Initialize ActivityResultLaunchers
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            exportQuotesToJson(uri);
                        }
                    }
                }
        );

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            importQuotesFromJson(uri);
                        }
                    }
                }
        );

        backupFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Uri treeUri = (result.getResultCode() == RESULT_OK && result.getData() != null)
                            ? result.getData().getData() : null;
                    if (treeUri != null) {
                        LocalBackup.setFolder(this, treeUri);
                        LocalBackup.setEnabled(this, true);
                        updateLastBackupText();
                        Toast.makeText(this, R.string.local_backup_enabled_toast, Toast.LENGTH_SHORT).show();
                    } else {
                        localBackupRow.setCheckedSilently(false);
                    }
                }
        );

        driveAuthorizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            DriveAuth.completeAuthorizationResult(this, result.getData());
                            DriveBackup.connect(this, pendingDriveEmail);
                            updateDriveSummary();
                            Toast.makeText(this, R.string.drive_connected_toast, Toast.LENGTH_SHORT).show();
                        } catch (ApiException e) {
                            Log.w(TAG, "Drive authorization consent failed", e);
                            driveBackupRow.setCheckedSilently(false);
                            Toast.makeText(this, R.string.drive_authorization_failed_toast, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        driveBackupRow.setCheckedSilently(false);
                    }
                    pendingDriveEmail = null;
                }
        );

        setupLibrarySection();
        setupGeneralSection();
        setupBackupSection();
    }

    // Library rows: Hashtags and Statistics, each with a live count.
    private void setupLibrarySection() {
        SettingsRow hashtagsRow = new SettingsRow(binding.rowHashtags,
                R.drawable.ic_tag_24dp, R.string.settings_hashtags)
                .onClick(v -> startActivity(new Intent(this, HashtagsActivity.class)));
        MyApplication.getInstance().getHashtags().getHashtags().observe(this, tags -> {
            int count = tags != null ? tags.size() : 0;
            hashtagsRow.setSummary(
                    getResources().getQuantityString(R.plurals.settings_hashtag_count, count, count));
        });

        SettingsRow statisticsRow = new SettingsRow(binding.rowStatistics,
                R.drawable.ic_bar_chart_24dp, R.string.settings_statistics)
                .onClick(v -> startActivity(new Intent(this, StatisticsActivity.class)));
        quoteCollection.getQuoteList().observe(this, quotes -> {
            int count = quotes != null ? quotes.size() : 0;
            statisticsRow.setSummary(
                    getResources().getQuantityString(R.plurals.settings_quote_count, count, count));
        });
    }

    // General rows: the app theme (a choice dialog, like Hue and You's) and the daily notification.
    private void setupGeneralSection() {
        themeRow = new SettingsRow(binding.rowTheme, R.drawable.ic_palette_24dp, R.string.settings_theme)
                .onClick(v -> showThemeDialog());
        themeRow.setSummary(THEME_LABELS[themeIndex()]);

        dailyNotificationRow = new SettingsRow(binding.rowDailyNotification,
                R.drawable.ic_notifications_24dp, R.string.settings_daily_notification)
                .withSwitch(QuoteNotifications.isEnabled(this), (buttonView, isChecked) -> {
                    if (isChecked) {
                        Toast.makeText(this, "Daily notifications enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Daily notifications disabled", Toast.LENGTH_SHORT).show();
                    }
                    QuoteNotifications.setEnabled(this, isChecked);
                });
        dailyNotificationRow.setSummary(R.string.settings_daily_notification_summary);
    }

    /** Picking an option applies it at once (recreating the activity) and closes the dialog. */
    private void showThemeDialog() {
        CharSequence[] labels = new CharSequence[THEME_LABELS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getText(THEME_LABELS[i]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels, themeIndex(), (dialog, which) -> {
                    dialog.dismiss();
                    themeRow.setSummary(THEME_LABELS[which]);
                    MyApplication.getInstance().setThemeMode(THEME_MODES[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int themeIndex() {
        int mode = MyApplication.getInstance().getThemeMode();
        for (int i = 0; i < THEME_MODES.length; i++) {
            if (THEME_MODES[i] == mode) return i;
        }
        return 0;
    }

    // Backup rows: the two auto-backup switches, then manual export and import.
    private void setupBackupSection() {
        localBackupRow = new SettingsRow(binding.rowLocalBackup,
                R.drawable.ic_folder_24dp, R.string.switch_local_backup)
                .withSwitch(LocalBackup.isEnabled(this), (buttonView, isChecked) -> {
                    if (isChecked) {
                        if (LocalBackup.hasFolderSelected(this)) {
                            LocalBackup.setEnabled(this, true);
                            Toast.makeText(this, R.string.local_backup_enabled_toast, Toast.LENGTH_SHORT).show();
                        } else {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                            backupFolderLauncher.launch(intent);
                        }
                    } else {
                        LocalBackup.setEnabled(this, false);
                        Toast.makeText(this, R.string.local_backup_disabled_toast, Toast.LENGTH_SHORT).show();
                    }
                });
        updateLastBackupText();

        // Switching Drive off disconnects the account.
        driveBackupRow = new SettingsRow(binding.rowDriveBackup,
                R.drawable.ic_cloud_24dp, R.string.switch_drive_backup)
                .withSwitch(DriveAuth.isEnabled(this), (buttonView, isChecked) -> {
                    if (isChecked) {
                        startDriveConnect();
                    } else {
                        DriveBackup.disconnect(this);
                        updateDriveSummary();
                        Toast.makeText(this, R.string.drive_disconnected_toast, Toast.LENGTH_SHORT).show();
                    }
                });
        updateDriveSummary();

        SettingsRow exportRow = new SettingsRow(binding.rowExport,
                R.drawable.ic_upload_24dp, R.string.settings_export)
                .onClick(v -> startExport());
        exportRow.setSummary(R.string.settings_export_summary);

        SettingsRow importRow = new SettingsRow(binding.rowImport,
                R.drawable.ic_download_24dp, R.string.settings_import)
                .onClick(v -> startImport());
        importRow.setSummary(R.string.settings_import_summary);
    }

    private void updateLastBackupText() {
        long lastBackupTime = LocalBackup.getLastBackupTime(this);
        localBackupRow.setSummary(lastBackupTime == 0
                ? getString(R.string.local_backup_never)
                : formatLastBackup(R.string.local_backup_last_format, lastBackupTime));
    }

    /** The connected account and the last Drive backup, or "Not connected". */
    private void updateDriveSummary() {
        String email = DriveAuth.getConnectedAccountEmail(this);
        if (!DriveAuth.isEnabled(this) || email == null) {
            driveBackupRow.setSummary(R.string.drive_not_connected);
            return;
        }
        long lastBackupTime = DriveBackup.getLastBackupTime(this);
        String lastBackup = lastBackupTime == 0
                ? getString(R.string.drive_backup_never)
                : formatLastBackup(R.string.drive_backup_last_format, lastBackupTime);
        driveBackupRow.setSummary(getString(R.string.drive_connected_as_format, email) + "\n" + lastBackup);
    }

    private String formatLastBackup(int formatRes, long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        return getString(formatRes, sdf.format(new Date(timeMillis)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Without re-firing the switch listener, which would toast and re-write the flag.
        dailyNotificationRow.setCheckedSilently(QuoteNotifications.isEnabled(this));
        updateLastBackupText();
        updateDriveSummary();
    }

    private void startDriveConnect() {
        DriveAuth.signIn(this, new DriveAuth.AccountCallback() {
            @Override
            public void onSuccess(String accountEmail) {
                pendingDriveEmail = accountEmail;
                DriveAuth.authorizeDriveAccess(SettingsActivity.this, new DriveAuth.AuthorizationCallback() {
                    @Override
                    public void onGranted() {
                        DriveBackup.connect(SettingsActivity.this, pendingDriveEmail);
                        pendingDriveEmail = null;
                        updateDriveSummary();
                        Toast.makeText(SettingsActivity.this, R.string.drive_connected_toast, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResolutionRequired(PendingIntent pendingIntent) {
                        IntentSender intentSender = pendingIntent.getIntentSender();
                        driveAuthorizationLauncher.launch(new IntentSenderRequest.Builder(intentSender).build());
                    }

                    @Override
                    public void onFailed(Exception e) {
                        Log.w(TAG, "Drive authorization failed", e);
                        pendingDriveEmail = null;
                        driveBackupRow.setCheckedSilently(false);
                        Toast.makeText(SettingsActivity.this, R.string.drive_authorization_failed_toast, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled() {
                driveBackupRow.setCheckedSilently(false);
            }

            @Override
            public void onFailed(Exception e) {
                Log.w(TAG, "Google sign-in failed", e);
                driveBackupRow.setCheckedSilently(false);
                Toast.makeText(SettingsActivity.this, R.string.drive_sign_in_failed_toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startExport() {
        String filename = BackupFilename.forTimestamp(System.currentTimeMillis());

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        exportLauncher.launch(intent);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");

        importLauncher.launch(intent);
    }

    private void exportQuotesToJson(Uri uri) {
        ioExecutor.execute(() -> {
            try {
                List<Quote> quotes = quoteCollection.getQuoteList().getValue();
                if (quotes == null || quotes.isEmpty()) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "No quotes to export", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                QuoteExporter.writeToUri(this, uri,
                        new BackupDocument(quotes, QuoteNotifications.loadHistory(this)));

                final int count = quotes.size();
                runOnUiThread(() ->
                        Toast.makeText(this, count + " quotes exported", Toast.LENGTH_SHORT).show()
                );
                Log.d(TAG, "Successfully exported " + quotes.size() + " quotes");
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void importQuotesFromJson(Uri uri) {
        ioExecutor.execute(() -> {
            try {
                BackupDocument imported = QuoteImporter.readFromUri(this, uri);

                runOnUiThread(() -> confirmImport(imported));
            } catch (QuoteCodecException e) {
                Log.e(TAG, "JSON parsing failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Invalid JSON format", Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    /** The file parsed; replacing the collection is asked first, unless it's empty. */
    private void confirmImport(BackupDocument imported) {
        if (isFinishing() || isDestroyed()) return;
        List<Quote> current = quoteCollection.getQuoteList().getValue();
        int currentCount = current != null ? current.size() : 0;
        if (currentCount == 0) {
            applyImport(imported);
            return;
        }
        int importedCount = imported.quotes.size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_confirm_title)
                .setMessage(getResources().getQuantityString(R.plurals.import_confirm_message,
                        currentCount, currentCount,
                        getResources().getQuantityString(R.plurals.settings_quote_count,
                                importedCount, importedCount)))
                .setPositiveButton(R.string.import_confirm_replace, (dialog, which) -> applyImport(imported))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyImport(BackupDocument imported) {
        int totalQuotes = imported.quotes.size();
        quoteCollection.setList(imported.quotes);
        // Replaced, not merged: a file without history clears it (#37).
        QuoteNotifications.replaceHistory(this, imported.history);
        Toast.makeText(this,
                "Import replaced database with " + totalQuotes + " quotes",
                Toast.LENGTH_LONG).show();
        Log.d(TAG, "Import successful: replaced database with " + totalQuotes + " quotes");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}