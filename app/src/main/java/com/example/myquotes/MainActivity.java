package com.example.myquotes;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
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

    private boolean isFirstDeckLoad = true;
    private int pendingQuoteId = -1;
    private boolean isFabHidden = false;

    private androidx.activity.result.ActivityResultLauncher<Intent> searchActivityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.apply(this, binding.statusBarScrim);

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
        readingSession = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new ReadingSession(quoteCollection);
            }
        }).get(ReadingSession.class);

        viewPager = findViewById(R.id.quotes_viewpager);
        pagerAdapter = new QuotePagerAdapter(new QuotePagerAdapter.QuoteInteractionListener() {
            @Override
            public void onToggleFavorite(Quote quote) {
                toggleFavorite(quote);
            }

            @Override
            public void onShareQuote(Quote quote) {
                QuoteSharer.share(MainActivity.this, quote);
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

    private void toggleFavorite(Quote quote) {
        if (quote != null) {
            boolean isFavorite = quoteCollection.toggleFavorite(quote.getId());
            String message = isFavorite ? "Added to favorites" : "Removed from favorites";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Quote #" + quote.getId() + " favorite: " + isFavorite);
        }
    }

    // Seed-from-CSV fallback for a first launch with nothing stored; removed in #32.
    // Stored quotes are loaded by MyApplication (QuoteCollection.loadFromStore).
    private void loadQuotesIfNeeded() {
        if (MyApplication.getInstance().getQuoteStore().hasStoredQuotes()) {
            return;
        }
        Log.d(TAG, "Loading from CSV...");
        loadQuotesFromCsv();
    }

    private void loadQuotesFromCsv() {
        QuotePreferences prefs = new QuotePreferences(this);
        final boolean isFirstLaunch = prefs.isFirstLaunch();

        new Thread(() -> {
            List<Quote> quotes = CsvLoader.loadQuotesFromRaw(
                    MainActivity.this,
                    R.raw.zitate
            );

            runOnUiThread(() -> {
                if (quotes != null && !quotes.isEmpty()) {
                    quoteCollection.setList(quotes);
                    quoteCollection.trimFields();

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
            Intent intent = QuoteQuery.forField(QuoteQuery.Field.AUTHOR, quote.getAuthor()).toIntent(this);
            searchActivityLauncher.launch(intent);
        }
    }

    private void searchBySource(Quote quote) {
        if (quote != null && !quote.getSource().isEmpty()) {
            Intent intent = QuoteQuery.forField(QuoteQuery.Field.SOURCE, quote.getSource()).toIntent(this);
            searchActivityLauncher.launch(intent);
        }
    }

    private void searchByCategory(Quote quote) {
        if (quote != null && !quote.getCategory().isEmpty()) {
            Intent intent = QuoteQuery.forField(QuoteQuery.Field.CATEGORY, quote.getCategory()).toIntent(this);
            searchActivityLauncher.launch(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Boolean enabled = QuoteNotifications.onPermissionResult(this, requestCode, grantResults);
        if (enabled != null) {
            Toast.makeText(this, enabled ? "Notifications enabled" : "Notification permission denied",
                    Toast.LENGTH_SHORT).show();
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

}
