package com.example.myquotes;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myquotes.backup.BackupFilename;
import com.example.myquotes.backup.LocalBackup;
import com.example.myquotes.databinding.ActivitySettingsBinding;
import com.example.myquotes.drive.DriveAuth;
import com.example.myquotes.drive.DriveBackup;
import com.example.myquotes.notifications.QuoteNotifications;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private ActivitySettingsBinding binding;
    private QuoteCollection quoteCollection;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;
    private ActivityResultLauncher<Intent> backupFolderLauncher;
    private ActivityResultLauncher<IntentSenderRequest> driveAuthorizationLauncher;
    private android.widget.TextView lastBackupTextView;

    private SwitchMaterial switchDriveBackup;
    private CompoundButton.OnCheckedChangeListener driveSwitchListener;
    private SwitchMaterial switchDailyNotification;
    private CompoundButton.OnCheckedChangeListener dailyNotificationListener;
    private TextView driveAccountTextView;
    private TextView driveLastBackupTextView;
    private Button btnDriveDisconnect;
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

        SwitchMaterial switchLocalBackup = findViewById(R.id.switch_local_backup);
        lastBackupTextView = findViewById(R.id.text_last_backup);
        updateLastBackupText();

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
                        switchLocalBackup.setChecked(false);
                    }
                }
        );

        // Setup buttons
        Button btnExport = findViewById(R.id.btn_export);
        Button btnImport = findViewById(R.id.btn_import);

        btnExport.setOnClickListener(v -> startExport());
        btnImport.setOnClickListener(v -> startImport());

        // Setup Quote Counter (observes LiveData)
        android.widget.TextView quoteCountTextView = findViewById(R.id.quote_count_text);
        quoteCollection.getQuoteList().observe(this, quotes -> {
            if (quotes != null) {
                quoteCountTextView.setText("Total quotes: " + quotes.size());
            }
        });

        // Setup Daily Notification Switch
        switchDailyNotification = findViewById(R.id.switch_daily_notification);

        // Set initial state
        switchDailyNotification.setChecked(QuoteNotifications.isEnabled(this));

        // Set listener
        dailyNotificationListener = (buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, "Daily notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Daily notifications disabled", Toast.LENGTH_SHORT).show();
            }

            QuoteNotifications.setEnabled(this, isChecked);
        };
        switchDailyNotification.setOnCheckedChangeListener(dailyNotificationListener);

        // Setup Local Auto-backup Switch

        // Set initial state
        switchLocalBackup.setChecked(LocalBackup.isEnabled(this));

        // Set listener
        switchLocalBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
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

        // Setup Google Drive Switch
        switchDriveBackup = findViewById(R.id.switch_drive_backup);
        driveAccountTextView = findViewById(R.id.text_drive_account);
        driveLastBackupTextView = findViewById(R.id.text_drive_last_backup);
        btnDriveDisconnect = findViewById(R.id.btn_drive_disconnect);
        updateDriveLastBackupText();

        driveAuthorizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            DriveAuth.completeAuthorizationResult(this, result.getData());
                            DriveBackup.connect(this, pendingDriveEmail);
                            updateDriveConnectionUi();
                            Toast.makeText(this, R.string.drive_connected_toast, Toast.LENGTH_SHORT).show();
                        } catch (ApiException e) {
                            Log.w(TAG, "Drive authorization consent failed", e);
                            setDriveSwitchChecked(false);
                            Toast.makeText(this, R.string.drive_authorization_failed_toast, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        setDriveSwitchChecked(false);
                    }
                    pendingDriveEmail = null;
                }
        );

        switchDriveBackup.setChecked(DriveAuth.isEnabled(this));
        updateDriveConnectionUi();

        driveSwitchListener = (buttonView, isChecked) -> {
            if (isChecked) {
                startDriveConnect();
            } else {
                DriveBackup.disconnect(this);
                updateDriveConnectionUi();
                Toast.makeText(this, R.string.drive_disconnected_toast, Toast.LENGTH_SHORT).show();
            }
        };
        switchDriveBackup.setOnCheckedChangeListener(driveSwitchListener);

        btnDriveDisconnect.setOnClickListener(v -> {
            DriveBackup.disconnect(this);
            setDriveSwitchChecked(false);
            updateDriveConnectionUi();
            Toast.makeText(this, R.string.drive_disconnected_toast, Toast.LENGTH_SHORT).show();
        });

        // Setup Theme RadioGroup
        android.widget.RadioGroup radioGroupTheme = findViewById(R.id.radio_group_theme);
        android.widget.RadioButton radioLight = findViewById(R.id.radio_theme_light);
        android.widget.RadioButton radioDark = findViewById(R.id.radio_theme_dark);
        android.widget.RadioButton radioSystem = findViewById(R.id.radio_theme_system);

        // Set initial state based on current theme
        int currentTheme = MyApplication.getInstance().getThemeMode();
        if (currentTheme == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) {
            radioLight.setChecked(true);
        } else if (currentTheme == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) {
            radioDark.setChecked(true);
        } else {
            radioSystem.setChecked(true);
        }

        // Set listener
        radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int newMode;
            if (checkedId == R.id.radio_theme_light) {
                newMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.radio_theme_dark) {
                newMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                newMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }
            MyApplication.getInstance().setThemeMode(newMode);
        });
    }

    private void updateLastBackupText() {
        long lastBackupTime = LocalBackup.getLastBackupTime(this);
        if (lastBackupTime == 0) {
            lastBackupTextView.setText(R.string.local_backup_never);
        } else {
            lastBackupTextView.setText(formatLastBackup(R.string.local_backup_last_format, lastBackupTime));
        }
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
        setDailyNotificationChecked(QuoteNotifications.isEnabled(this));
        updateLastBackupText();
        updateDriveLastBackupText();
    }

    /** Reflects the flag without firing dailyNotificationListener (which would toast and re-write it). */
    private void setDailyNotificationChecked(boolean checked) {
        switchDailyNotification.setOnCheckedChangeListener(null);
        switchDailyNotification.setChecked(checked);
        switchDailyNotification.setOnCheckedChangeListener(dailyNotificationListener);
    }

    private void updateDriveLastBackupText() {
        long lastBackupTime = DriveBackup.getLastBackupTime(this);
        if (lastBackupTime == 0) {
            driveLastBackupTextView.setText(R.string.drive_backup_never);
        } else {
            driveLastBackupTextView.setText(formatLastBackup(R.string.drive_backup_last_format, lastBackupTime));
        }
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
                        updateDriveConnectionUi();
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
                        setDriveSwitchChecked(false);
                        Toast.makeText(SettingsActivity.this, R.string.drive_authorization_failed_toast, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled() {
                setDriveSwitchChecked(false);
            }

            @Override
            public void onFailed(Exception e) {
                Log.w(TAG, "Google sign-in failed", e);
                setDriveSwitchChecked(false);
                Toast.makeText(SettingsActivity.this, R.string.drive_sign_in_failed_toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Sets the switch's checked state without re-triggering driveSwitchListener's connect/disconnect side effects. */
    private void setDriveSwitchChecked(boolean checked) {
        switchDriveBackup.setOnCheckedChangeListener(null);
        switchDriveBackup.setChecked(checked);
        switchDriveBackup.setOnCheckedChangeListener(driveSwitchListener);
    }

    private void updateDriveConnectionUi() {
        String email = DriveAuth.getConnectedAccountEmail(this);
        if (DriveAuth.isEnabled(this) && email != null) {
            driveAccountTextView.setText(getString(R.string.drive_connected_as_format, email));
            btnDriveDisconnect.setVisibility(android.view.View.VISIBLE);
        } else {
            driveAccountTextView.setText(R.string.drive_not_connected);
            btnDriveDisconnect.setVisibility(android.view.View.GONE);
        }
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

                QuoteExporter.writeToUri(this, uri, quotes);

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
                List<Quote> importedQuotes = QuoteImporter.readFromUri(this, uri);

                final int totalQuotes = importedQuotes.size();
                runOnUiThread(() -> {
                    quoteCollection.setList(importedQuotes);
                    Toast.makeText(this,
                            "Import replaced database with " + totalQuotes + " quotes",
                            Toast.LENGTH_LONG).show();
                });
                Log.d(TAG, "Import successful: replaced database with " + totalQuotes + " quotes");

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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}