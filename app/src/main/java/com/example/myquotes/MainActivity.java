package com.example.myquotes;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myquotes.databinding.ActivityMainBinding;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    private androidx.viewpager2.widget.ViewPager2 viewPager;
    private QuotePagerAdapter pagerAdapter;
    private TextView quoteCounter;

    private QuoteViewModel quoteViewModel;
    private Quote currentQuote;

    private boolean quotesLoaded = false;
    private int pendingQuoteId = -1; // Quote ID passed in from a notification
    private boolean isFabHidden = false;

    private androidx.activity.result.ActivityResultLauncher<Intent> searchActivityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Register SearchActivity Launcher
        searchActivityLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int quoteId = result.getData().getIntExtra(QuoteNotificationReceiver.EXTRA_QUOTE_ID, -1);
                        if (quoteId != -1 && quotesLoaded) {
                            navigateToQuote(quoteId);
                        }
                    }
                }
        );

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Quotes");
        }

        // Request notification permission on first launch (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean isIgnoring = pm.isIgnoringBatteryOptimizations(getPackageName());
        Log.d(TAG, "Battery optimization ignored: " + isIgnoring);

        quoteViewModel = MyApplication.getInstance().getQuoteViewModel();

        // Setup ViewPager
        viewPager = findViewById(R.id.quotes_viewpager);
        pagerAdapter = new QuotePagerAdapter(new QuotePagerAdapter.QuoteInteractionListener() {
            @Override
            public void onRateQuote(Quote quote, int delta) {
                rateQuote(quote);
            }

            @Override
            public void onToggleFavorite(Quote quote) {
                toggleFavorite(quote);
            }

            @Override
            public void onShareQuote(Quote quote) {
                shareQuote(quote);
            }

            @Override
            public void onAuthorClick(Quote quote) {
                searchByAuthor(quote);
            }

            @Override
            public void onSourceClick(Quote quote) {
                searchBySource(quote);
            }

            @Override
            public void onCategoryClick(Quote quote) {
                searchByCategory(quote);
            }
        });

        if (!BackgroundPermissionHelper.isBackgroundUnrestricted(this)) {
            BackgroundPermissionHelper.showBackgroundPermissionDialog(this);
        }

        viewPager.setAdapter(pagerAdapter);

        // FAB scroll behavior
        pagerAdapter.setScrollDirectionListener(new QuotePagerAdapter.ScrollDirectionListener() {
            @Override
            public void onScrollDown() {
                hideFab();
            }

            @Override
            public void onScrollUp() {
                showFab();
            }
        });

        // Initialize quote counter
        quoteCounter = findViewById(R.id.quote_counter);

        // Setup FAB
        binding.fabAddQuote.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditActivity.class);
            intent.putExtra(AddEditActivity.EXTRA_ACTION, AddEditActivity.ACTION_ADD);
            startActivity(intent);
        });

        // ViewPager Page Change Callback
        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                quoteViewModel.setCurrentQuoteIndex(position);
                currentQuote = quoteViewModel.getCurrentQuoteFromShuffledList();
                if (currentQuote != null) {
                    Log.d(TAG, "Page changed to quote #" + currentQuote.getId() + " at position " + position);
                }
                updateQuoteCounter(position);
                showFab();
            }
        });

        // Check if opened from notification
        Intent intent = getIntent();
        if (intent.hasExtra(QuoteNotificationReceiver.EXTRA_QUOTE_ID)) {
            pendingQuoteId = intent.getIntExtra(QuoteNotificationReceiver.EXTRA_QUOTE_ID, -1);
            Log.d(TAG, "Opened from notification with quote ID: " + pendingQuoteId);
        }

        loadQuotesIfNeeded();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Handle notification click when app is already running
        if (intent.hasExtra(QuoteNotificationReceiver.EXTRA_QUOTE_ID)) {
            int quoteId = intent.getIntExtra(QuoteNotificationReceiver.EXTRA_QUOTE_ID, -1);
            if (quoteId != -1 && quotesLoaded) {
                navigateToQuote(quoteId);
            }
        }
    }

    private void shareQuote(Quote quote) {
        if (quote != null) {
            String shareText = "\"" + quote.getQuoteText() + "\"\n\n" +
                    "— " + quote.getAuthor();

            if (!quote.getSource().isEmpty()) {
                shareText += " (" + quote.getSource() + ")";
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Quote from My Quotes");

            startActivity(Intent.createChooser(shareIntent, "Share quote via"));
        }
    }

    private void showDeleteConfirmationDialog(Quote quote) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Quote")
                .setMessage("Are you sure you want to delete this quote?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    int deletedQuoteId = quote.getId();
                    int currentPosition = viewPager.getCurrentItem();

                    quoteViewModel.deleteQuoteById(deletedQuoteId);

                    List<Quote> shuffledQuotes = quoteViewModel.getShuffledQuoteList();

                    if (shuffledQuotes.isEmpty()) {
                        pagerAdapter.setQuotes(shuffledQuotes);
                        currentQuote = null;
                        quoteViewModel.setCurrentlyDisplayedQuote(null);
                        updateQuoteCounter(0);
                        Toast.makeText(this, "No more quotes", Toast.LENGTH_SHORT).show();
                    } else {
                        pagerAdapter.setQuotes(shuffledQuotes);

                        int newPosition = Math.min(currentPosition, shuffledQuotes.size() - 1);
                        viewPager.setCurrentItem(newPosition, false);

                        currentQuote = shuffledQuotes.get(newPosition);
                        quoteViewModel.setCurrentlyDisplayedQuote(currentQuote);
                        quoteViewModel.setCurrentQuoteIndex(newPosition);

                        updateQuoteCounter(newPosition);
                        Toast.makeText(this, "Quote deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rateQuote(Quote quote) {
        if (quote != null) {
            int newRating = quote.getRating() + 1;
            quote.setRating(newRating);

            // Update in ViewModel
            quoteViewModel.updateQuote(quote);

            // Update UI
            int currentPosition = viewPager.getCurrentItem();
            pagerAdapter.notifyItemChanged(currentPosition);

            Log.d(TAG, "Quote #" + quote.getId() + " rated to: " + newRating);
        }
    }

    private void toggleFavorite(Quote quote) {
        if (quote != null) {
            quote.toggleFavorite();
            boolean newFavoriteStatus = quote.isFavorite();

            // Update in ViewModel
            quoteViewModel.updateQuote(quote);

            // Update UI
            int currentPosition = viewPager.getCurrentItem();
            pagerAdapter.notifyItemChanged(currentPosition);

            String message = newFavoriteStatus ? "Added to favorites" : "Removed from favorites";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            Log.d(TAG, "Quote #" + quote.getId() + " favorite: " + newFavoriteStatus);
        }
    }

    private void loadQuotesIfNeeded() {
        QuotePreferences prefs = new QuotePreferences(this);

        // Try loading from persistent storage first
        if (prefs.hasPersistedQuotes()) {
            Log.d(TAG, "Loading quotes from persistent storage...");
            List<Quote> quotes = prefs.loadQuotes();

            if (quotes != null && !quotes.isEmpty()) {
                quotesLoaded = true;
                quoteViewModel.updateQuoteList(quotes);
                Log.d(TAG, "Loaded " + quotes.size() + " quotes from storage");

                // Short delay to allow the ViewModel to post its value before we read it
                new android.os.Handler().postDelayed(() -> {
                    displayShuffledQuotes();
                }, 100);
                return;
            }
        }

        // First launch: seed from bundled CSV
        if (prefs.isFirstLaunch() || !prefs.isInitialCsvLoaded()) {
            Log.d(TAG, "First launch detected, loading from CSV...");
            loadQuotesFromCsv();
        } else {
            // Fallback: ViewModel is empty despite storage being marked as loaded
            List<Quote> currentQuotes = quoteViewModel.getQuoteList().getValue();
            if (currentQuotes == null || currentQuotes.isEmpty()) {
                Log.d(TAG, "No quotes in ViewModel, falling back to CSV...");
                loadQuotesFromCsv();
            } else {
                Log.d(TAG, "Quotes already in ViewModel: " + currentQuotes.size());
                quotesLoaded = true;

                new android.os.Handler().postDelayed(() -> {
                    displayShuffledQuotes();
                }, 100);
            }
        }
    }

    private void loadQuotesFromCsv() {
        QuotePreferences prefs = new QuotePreferences(this);
        final boolean isFirstLaunch = prefs.isFirstLaunch();

        new Thread(() -> {
            List<Quote> quotes = CsvLoader.loadQuotesFromRaw(
                    MainActivity.this,
                    R.raw.zitate_260123
            );

            runOnUiThread(() -> {
                if (quotes != null && !quotes.isEmpty()) {
                    quotesLoaded = true;
                    quoteViewModel.updateQuoteList(quotes);

                    prefs.saveQuotes(quotes);
                    prefs.setInitialCsvLoaded(true);
                    prefs.setFirstLaunchComplete();

                    if (isFirstLaunch) {
                        Toast.makeText(MainActivity.this,
                                quotes.size() + " quotes loaded",
                                Toast.LENGTH_SHORT).show();
                    }

                    // Short delay to allow ViewModel to post its value before we read it
                    new android.os.Handler().postDelayed(() -> {
                        displayShuffledQuotes();
                    }, 100);

                } else {
                    Toast.makeText(MainActivity.this,
                            "Failed to load quotes",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void displayShuffledQuotes() {
        quoteViewModel.trimQuoteFields();
        quoteViewModel.initializeShuffledList();
        List<Quote> shuffledQuotes = quoteViewModel.getShuffledQuoteList();

        if (shuffledQuotes == null || shuffledQuotes.isEmpty()) {
            Log.w(TAG, "No shuffled quotes available to display");
            Toast.makeText(this, "No quotes available", Toast.LENGTH_SHORT).show();
            return;
        }

        pagerAdapter.setQuotes(shuffledQuotes);

        // Update counter
        updateQuoteCounter(0);

        // Check if we need to navigate to a specific quote (from notification)
        if (pendingQuoteId != -1) {
            navigateToQuote(pendingQuoteId);
            pendingQuoteId = -1; // Reset
        } else {
            // Normal start: show first quote
            viewPager.setCurrentItem(0, false);
            currentQuote = shuffledQuotes.get(0);
            quoteViewModel.setCurrentlyDisplayedQuote(currentQuote);
        }

        Log.d(TAG, "Displayed " + shuffledQuotes.size() + " quotes in shuffled order");
    }

    private void navigateToQuote(int quoteId) {
        List<Quote> shuffledQuotes = quoteViewModel.getShuffledQuoteList();

        for (int i = 0; i < shuffledQuotes.size(); i++) {
            if (shuffledQuotes.get(i).getId() == quoteId) {
                viewPager.setCurrentItem(i, false);
                currentQuote = shuffledQuotes.get(i);
                quoteViewModel.setCurrentlyDisplayedQuote(currentQuote);
                Log.d(TAG, "Navigated to quote #" + quoteId + " at position " + i);
                return;
            }
        }

        // Quote not found (likely deleted)
        Log.w(TAG, "Quote #" + quoteId + " not found in shuffled list");
        Toast.makeText(this, "Quote no longer exists", Toast.LENGTH_SHORT).show();

        int currentPosition = viewPager.getCurrentItem();
        if (currentPosition < shuffledQuotes.size()) {
            currentQuote = shuffledQuotes.get(currentPosition);
            quoteViewModel.setCurrentlyDisplayedQuote(currentQuote);
        }
    }

    private void searchByAuthor(Quote quote) {
        if (quote != null && !quote.getAuthor().isEmpty()) {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, quote.getAuthor());
            intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, "author");
            searchActivityLauncher.launch(intent);
        }
    }

    private void searchBySource(Quote quote) {
        if (quote != null && !quote.getSource().isEmpty()) {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, quote.getSource());
            intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, "source");
            searchActivityLauncher.launch(intent);
        }
    }

    private void searchByCategory(Quote quote) {
        if (quote != null && quote.getCategory() != null && !quote.getCategory().isEmpty()) {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, quote.getCategory());
            intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, "category");
            searchActivityLauncher.launch(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, enable notifications
                QuoteNotificationScheduler.setNotificationsEnabled(this, true);
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_search) {
            Intent intent = new Intent(this, SearchActivity.class);
            searchActivityLauncher.launch(intent);
            return true;

        } else if (id == R.id.action_edit) {
            if (currentQuote != null) {
                Intent intent = new Intent(this, AddEditActivity.class);
                intent.putExtra(AddEditActivity.EXTRA_ACTION, AddEditActivity.ACTION_EDIT);
                intent.putExtra(AddEditActivity.EXTRA_QUOTE_ID, currentQuote.getId());
                startActivity(intent);
            } else {
                Toast.makeText(this, "No quote to edit", Toast.LENGTH_SHORT).show();
            }
            return true;

        } else if (id == R.id.action_delete) {
            if (currentQuote != null) {
                showDeleteConfirmationDialog(currentQuote);
            } else {
                Toast.makeText(this, "No quote to delete", Toast.LENGTH_SHORT).show();
            }
            return true;

        } else if (id == R.id.action_statistics) {
            Intent intent = new Intent(this, StatisticsActivity.class);
            startActivity(intent);
            return true;

        } else if (id == R.id.action_favorites) {
            Intent intent = new Intent(this, FavoritesActivity.class);
            startActivity(intent);
            return true;

        } else if (id == R.id.action_categories) {
            Intent intent = new Intent(this, CategoriesActivity.class);
            startActivity(intent);
            return true;

        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (quotesLoaded) {
            // Refresh adapter with latest shuffled list (includes newly added quotes)
            List<Quote> shuffledQuotes = quoteViewModel.getShuffledQuoteList();
            int currentPosition = viewPager.getCurrentItem();

            pagerAdapter.setQuotes(shuffledQuotes);

            if (currentQuote != null) {
                // Update current quote with latest data
                Quote updatedQuote = quoteViewModel.getQuoteById(currentQuote.getId());
                if (updatedQuote != null) {
                    currentQuote = updatedQuote;

                    // Refresh current page to show updated data
                    if (currentPosition < shuffledQuotes.size()) {
                        pagerAdapter.notifyItemChanged(currentPosition);
                    }
                }
            }

            updateQuoteCounter(currentPosition);
        }
    }

    private void hideFab() {
        if (!isFabHidden) {
            isFabHidden = true;
            binding.fabAddQuote.animate()
                    .translationY(binding.fabAddQuote.getHeight() +
                            ((android.view.ViewGroup.MarginLayoutParams) binding.fabAddQuote.getLayoutParams()).bottomMargin)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .start();
        }
    }

    private void showFab() {
        if (isFabHidden) {
            isFabHidden = false;
            binding.fabAddQuote.animate()
                    .translationY(0)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void updateQuoteCounter(int position) {
        List<Quote> shuffledQuotes = quoteViewModel.getShuffledQuoteList();
        if (shuffledQuotes != null && !shuffledQuotes.isEmpty() && quoteCounter != null) {
            quoteCounter.setText((position + 1) + " of " + shuffledQuotes.size());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}