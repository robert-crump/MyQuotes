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
        EdgeToEdgeUtils.apply(this, binding.statusBarScrim);

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
                QuoteSharer.share(FavoritesActivity.this, quote);
            }

            @Override
            public void onAuthorClick(Quote quote) {
                if (quote != null && !quote.getAuthor().isEmpty()) {
                    Intent intent = QuoteQuery.forField(QuoteQuery.Field.AUTHOR, quote.getAuthor()).toIntent(FavoritesActivity.this);
                    startActivity(intent);
                }
            }

            @Override
            public void onSourceClick(Quote quote) {
                if (quote != null && !quote.getSource().isEmpty()) {
                    Intent intent = QuoteQuery.forField(QuoteQuery.Field.SOURCE, quote.getSource()).toIntent(FavoritesActivity.this);
                    startActivity(intent);
                }
            }

            @Override
            public void onCategoryClick(Quote quote) {
                if (quote != null && !quote.getCategory().isEmpty()) {
                    Intent intent = QuoteQuery.forField(QuoteQuery.Field.CATEGORY, quote.getCategory()).toIntent(FavoritesActivity.this);
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
            boolean isFavorite = quoteCollection.toggleFavorite(quote.getId());

            // Remove from list when un-favorited
            if (!isFavorite) {
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
                    ", is favorite: " + isFavorite);
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