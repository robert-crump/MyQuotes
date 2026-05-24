package com.example.myquotes;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.myquotes.databinding.ActivitySettingsBinding;
import com.example.myquotes.notifications.QuoteNotifications;

import java.util.ArrayList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private ActivitySettingsBinding binding;
    private QuoteCollection quoteCollection;

    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
        com.google.android.material.switchmaterial.SwitchMaterial switchDailyNotification =
                findViewById(R.id.switch_daily_notification);

        // Set initial state
        switchDailyNotification.setChecked(QuoteNotifications.isEnabled(this));

        // Set listener
        switchDailyNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, "Daily notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Daily notifications disabled", Toast.LENGTH_SHORT).show();
            }

            QuoteNotifications.setEnabled(this, isChecked);
        });

        // Setup Delete Negative Quotes Button
        Button btnDeleteNegative = findViewById(R.id.btn_delete_negative_quotes);
        btnDeleteNegative.setOnClickListener(v -> showDeleteNegativeQuotesDialog());

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

    private void startExport() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd-HHmm", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        String filename = timestamp + " MyQuotes.json";

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
        new Thread(() -> {
            try {
                List<Quote> quotes = quoteCollection.getQuoteList().getValue();
                if (quotes == null || quotes.isEmpty()) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "No quotes to export", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                String json = QuoteCodec.encodePretty(quotes);

                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                    outputStream.close();

                    final int count = quotes.size();
                    runOnUiThread(() ->
                            Toast.makeText(this, count + " quotes exported", Toast.LENGTH_SHORT).show()
                    );
                    Log.d(TAG, "Successfully exported " + quotes.size() + " quotes");
                }
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void importQuotesFromJson(Uri uri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                StringBuilder jsonString = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonString.append(line);
                }
                reader.close();
                inputStream.close();

                List<Quote> importedQuotes = QuoteCodec.decode(jsonString.toString());
                List<Quote> currentQuotes = quoteCollection.getCurrentList();

                int importedCount = 0;
                int updatedCount = 0;

                for (Quote quote : importedQuotes) {
                    Quote existingQuote = null;
                    for (Quote q : currentQuotes) {
                        if (q.getId() == quote.getId()) {
                            existingQuote = q;
                            break;
                        }
                    }

                    if (existingQuote != null) {
                        existingQuote.setAuthor(quote.getAuthor());
                        existingQuote.setQuoteText(quote.getQuoteText());
                        existingQuote.setSource(quote.getSource());
                        existingQuote.setCategory(quote.getCategory());
                        existingQuote.setRating(quote.getRating());
                        existingQuote.setFavorite(quote.isFavorite());
                        existingQuote.setFavoritedAt(quote.getFavoritedAt());
                        existingQuote.setLastShown(quote.getLastShown());
                        existingQuote.setTimesShown(quote.getTimesShown());
                        updatedCount++;
                    } else {
                        currentQuotes.add(quote);
                        importedCount++;
                    }
                }

                quoteCollection.setList(currentQuotes);

                final int finalImported = importedCount;
                final int finalUpdated = updatedCount;
                final int totalQuotes = currentQuotes.size();
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Import: " + finalImported + " new, " + finalUpdated + " updated. Total: " + totalQuotes + " quotes",
                                Toast.LENGTH_LONG).show()
                );
                Log.d(TAG, "Import successful: " + importedCount + " new, " + updatedCount + " updated");

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
        }).start();
    }

    private void showDeleteNegativeQuotesDialog() {
        List<Quote> allQuotes = quoteCollection.getCurrentList();
        List<Quote> negativeQuotes = new ArrayList<>();

        for (Quote quote : allQuotes) {
            if (quote.getRating() < 0) {
                negativeQuotes.add(quote);
            }
        }

        if (negativeQuotes.isEmpty()) {
            Toast.makeText(this, "No quotes with negative rating found", Toast.LENGTH_SHORT).show();
            return;
        }

        final int count = negativeQuotes.size();

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Negative Quotes")
                .setMessage("Delete all quotes with negative rating?\n\n" +
                        count + " quote(s) found with negative rating.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteNegativeQuotes(negativeQuotes);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteNegativeQuotes(List<Quote> quotesToDelete) {
        List<Quote> allQuotes = quoteCollection.getCurrentList();
        List<Quote> updatedQuotes = new ArrayList<>();
        int deletedCount = 0;

        for (Quote quote : allQuotes) {
            boolean shouldDelete = false;
            for (Quote toDelete : quotesToDelete) {
                if (quote.getId() == toDelete.getId()) {
                    shouldDelete = true;
                    deletedCount++;
                    break;
                }
            }
            if (!shouldDelete) {
                updatedQuotes.add(quote);
            }
        }

        quoteCollection.setList(updatedQuotes);

        Toast.makeText(this, "Deleted " + deletedCount + " quotes with negative rating",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}