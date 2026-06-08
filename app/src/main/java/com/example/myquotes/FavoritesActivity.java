package com.example.myquotes;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.myquotes.databinding.ActivityFavoritesBinding;

import java.util.ArrayList;
import java.util.List;

public class FavoritesActivity extends AppCompatActivity {
    private static final String TAG = "FavoritesActivity";
    private ActivityFavoritesBinding binding;
    private ViewPager2 viewPager;
    private QuotePagerAdapter pagerAdapter;
    private QuoteCollection quoteCollection;
    private List<Quote> favoriteQuotes;
    private TextView favoriteCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityFavoritesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Favorites");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        quoteCollection = MyApplication.getInstance().getQuoteCollection();
        favoriteCounter = binding.favoriteCounter;

        // Setup ViewPager
        viewPager = binding.favoritesViewpager;
        pagerAdapter = new QuotePagerAdapter(new QuotePagerAdapter.QuoteInteractionListener() {
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
                if (quote != null && !quote.getAuthor().isEmpty()) {
                    Intent intent = new Intent(FavoritesActivity.this, SearchActivity.class);
                    intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, quote.getAuthor());
                    intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, "author");
                    startActivity(intent);
                }
            }

            @Override
            public void onSourceClick(Quote quote) {
                if (quote != null && !quote.getSource().isEmpty()) {
                    Intent intent = new Intent(FavoritesActivity.this, SearchActivity.class);
                    intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, quote.getSource());
                    intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, "source");
                    startActivity(intent);
                }
            }

            @Override
            public void onCategoryClick(Quote quote) {
                if (quote != null && quote.getCategory() != null && !quote.getCategory().isEmpty()) {
                    Intent intent = new Intent(FavoritesActivity.this, SearchActivity.class);
                    intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, quote.getCategory());
                    intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, "category");
                    startActivity(intent);
                }
            }
        });

        viewPager.setAdapter(pagerAdapter);

        // ViewPager Page Change Callback
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateCounter(position);
            }
        });

        loadFavorites();
    }

    private void loadFavorites() {
        loadFavorites(true);
    }

    private void loadFavorites(boolean resetPosition) {
        favoriteQuotes = quoteCollection.getFavorites();

        if (favoriteQuotes.isEmpty()) {
            favoriteCounter.setText("No favorites yet");
            return;
        }

        int previousPosition = viewPager.getCurrentItem();
        pagerAdapter.setQuotes(favoriteQuotes);

        if (resetPosition) {
            // Zeige neuestes Favorit (Position 0)
            viewPager.setCurrentItem(0, false);
            updateCounter(0);
        } else {
            int newPosition = Math.min(previousPosition, favoriteQuotes.size() - 1);
            viewPager.setCurrentItem(newPosition, false);
            updateCounter(newPosition);
        }

        Log.d(TAG, "Loaded " + favoriteQuotes.size() + " favorites");
    }

    private void updateCounter(int position) {
        if (favoriteQuotes != null && !favoriteQuotes.isEmpty()) {
            favoriteCounter.setText((position + 1) + " of " + favoriteQuotes.size());
        }
    }

    private void toggleFavorite(Quote quote) {
        if (quote != null) {
            quoteCollection.toggleFavorite(quote.getId());

            // Remove from list when un-favorited
            if (!quote.isFavorite()) {
                int currentPosition = viewPager.getCurrentItem();
                favoriteQuotes.remove(currentPosition);
                pagerAdapter.setQuotes(favoriteQuotes);

                if (favoriteQuotes.isEmpty()) {
                    favoriteCounter.setText("No favorites yet");
                    finish();
                } else {
                    int newPosition = Math.min(currentPosition, favoriteQuotes.size() - 1);
                    viewPager.setCurrentItem(newPosition, false);
                    updateCounter(newPosition);
                }
            }

            Log.d(TAG, "Toggled favorite for quote #" + quote.getId() +
                    ", is favorite: " + quote.isFavorite());
        }
    }

    private void shareQuote(Quote quote) {
        if (quote != null) {
            String shareText = "\"" + quote.getQuoteText() + "\"\n\n" +
                    "— " + quote.getAuthor();

            if (!quote.getSource().isEmpty()) {
                shareText += " (" + quote.getSource() + ")";
            }

            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Quote from My Quotes");

            startActivity(android.content.Intent.createChooser(shareIntent, "Share quote via"));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload favorites in case they changed in another screen
        loadFavorites(false);
    }
}