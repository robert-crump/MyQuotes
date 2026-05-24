package com.example.myquotes;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myquotes.databinding.ActivityMainBinding;
import com.example.myquotes.notifications.QuoteNotifications;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    private androidx.viewpager2.widget.ViewPager2 viewPager;
    private QuotePagerAdapter pagerAdapter;
    private TextView quoteCounter;

    private QuoteCollection quoteCollection;
    private ReadingSession readingSession;
    private Quote currentQuote;

    private boolean quotesLoaded = false;
    private boolean isFirstDeckLoad = true;
    private int pendingQuoteId = -1;
    private boolean isFabHidden = false;

    private androidx.activity.result.ActivityResultLauncher<Intent> searchActivityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        searchActivityLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int quoteId = result.getData().getIntExtra(QuoteNotifications.EXTRA_QUOTE_ID, -1);
                        if (quoteId != -1) {
                            navigateToQuote(quoteId);
                        }
                    }
                }
        );

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Quotes");
        }

        QuoteNotifications.requestPostNotificationsPermission(this);

        quoteCollection = MyApplication.getInstance().getQuoteCollection();
        readingSession = new ViewModelProvider(this).get(ReadingSession.class);

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

        QuoteNotifications.promptBackgroundPermissionIfNeeded(this);

        viewPager.setAdapter(pagerAdapter);

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

        quoteCounter = findViewById(R.id.quote_counter);

        binding.fabAddQuote.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditActivity.class);
            intent.putExtra(AddEditActivity.EXTRA_ACTION, AddEditActivity.ACTION_ADD);
            startActivity(intent);
        });

        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                readingSession.setPosition(position);
                updateQuoteCounter(position);
                showFab();
            }
        });

        // Observe deck: update adapter and restore position on structural changes.
        readingSession.getDeck().observe(this, deck -> {
            if (deck == null || deck.isEmpty()) return;
            pagerAdapter.setQuotes(deck);

            if (isFirstDeckLoad) {
                isFirstDeckLoad = false;
                if (pendingQuoteId != -1) {
                    if (!readingSession.navigateTo(pendingQuoteId)) {
                        Toast.makeText(this, "Quote no longer exists", Toast.LENGTH_SHORT).show();
                    }
                    pendingQuoteId = -1;
                }
                int pos = readingSession.getCurrentPosition();
                viewPager.setCurrentItem(pos, false);
                updateQuoteCounter(pos);
            } else {
                int pos = readingSession.getCurrentPosition();
                if (viewPager.getCurrentItem() != pos) {
                    viewPager.setCurrentItem(pos, false);
                }
                updateQuoteCounter(pos);
            }
        });

        // Observe currentQuote: keep local field in sync for menu actions (edit/delete).
        readingSession.getCurrentQuote().observe(this, quote -> currentQuote = quote);

        Intent intent = getIntent();
        if (intent.hasExtra(QuoteNotifications.EXTRA_QUOTE_ID)) {
            pendingQuoteId = intent.getIntExtra(QuoteNotifications.EXTRA_QUOTE_ID, -1);
            Log.d(TAG, "Opened from notification with quote ID: " + pendingQuoteId);
        }

        loadQuotesIfNeeded();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(QuoteNotifications.EXTRA_QUOTE_ID)) {
            int quoteId = intent.getIntExtra(QuoteNotifications.EXTRA_QUOTE_ID, -1);
            if (quoteId != -1) {
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
                    quoteCollection.deleteById(quote.getId());
                    List<Quote> deck = readingSession.getDeck().getValue();
                    if (deck == null || deck.isEmpty()) {
                        currentQuote = null;
                        updateQuoteCounter(0);
                        Toast.makeText(this, "No more quotes", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Quote deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rateQuote(Quote quote) {
        if (quote != null) {
            quote.setRating(quote.getRating() + 1);
            quoteCollection.update(quote);
            Log.d(TAG, "Quote #" + quote.getId() + " rated to: " + quote.getRating());
        }
    }

    private void toggleFavorite(Quote quote) {
        if (quote != null) {
            quote.toggleFavorite();
            quoteCollection.update(quote);
            String message = quote.isFavorite() ? "Added to favorites" : "Removed from favorites";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Quote #" + quote.getId() + " favorite: " + quote.isFavorite());
        }
    }

    private void loadQuotesIfNeeded() {
        // If QuoteCollection already has quotes (e.g. after config change), skip reload.
        if (!quoteCollection.getCurrentList().isEmpty()) {
            quotesLoaded = true;
            return;
        }

        QuotePreferences prefs = new QuotePreferences(this);

        if (prefs.hasPersistedQuotes()) {
            Log.d(TAG, "Loading quotes from persistent storage...");
            List<Quote> quotes = prefs.loadQuotes();
            if (quotes != null && !quotes.isEmpty()) {
                quotesLoaded = true;
                quoteCollection.setList(quotes);
                quoteCollection.trimFields();
                Log.d(TAG, "Loaded " + quotes.size() + " quotes from storage");
                return;
            }
        }

        if (prefs.isFirstLaunch() || !prefs.isInitialCsvLoaded()) {
            Log.d(TAG, "First launch detected, loading from CSV...");
            loadQuotesFromCsv();
        } else {
            Log.d(TAG, "No quotes in collection, falling back to CSV...");
            loadQuotesFromCsv();
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
                    quoteCollection.setList(quotes);
                    quoteCollection.trimFields();

                    prefs.saveQuotes(quotes);
                    prefs.setInitialCsvLoaded(true);
                    prefs.setFirstLaunchComplete();

                    if (isFirstLaunch) {
                        Toast.makeText(MainActivity.this,
                                quotes.size() + " quotes loaded",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this,
                            "Failed to load quotes",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void navigateToQuote(int quoteId) {
        if (!readingSession.navigateTo(quoteId)) {
            Log.w(TAG, "Quote #" + quoteId + " not found in deck");
            Toast.makeText(this, "Quote no longer exists", Toast.LENGTH_SHORT).show();
            return;
        }
        int pos = readingSession.getCurrentPosition();
        viewPager.setCurrentItem(pos, false);
        updateQuoteCounter(pos);
        Log.d(TAG, "Navigated to quote #" + quoteId + " at position " + pos);
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
        if (requestCode == QuoteNotifications.REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                QuoteNotifications.setEnabled(this, true);
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
            startActivity(new Intent(this, StatisticsActivity.class));
            return true;

        } else if (id == R.id.action_favorites) {
            startActivity(new Intent(this, FavoritesActivity.class));
            return true;

        } else if (id == R.id.action_categories) {
            startActivity(new Intent(this, CategoriesActivity.class));
            return true;

        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
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
        List<Quote> deck = readingSession.getDeck().getValue();
        if (deck != null && !deck.isEmpty() && quoteCounter != null) {
            quoteCounter.setText((position + 1) + " of " + deck.size());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
