package com.example.myquotes;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

/**
 * Favorites tab: a pager over the favorited quotes, most recently favorited first. Observes the
 * Quote Collection, so a ♥ tapped on another tab shows up here immediately.
 */
public class FavoritesFragment extends Fragment {
    private static final String TAG = "FavoritesFragment";
    private static final String STATE_QUOTE_ID = "favorites_quote_id";

    /** What the tab needs from MainActivity. */
    public interface Host {
        void showSearch(QuoteQuery query);
    }

    private ViewPager2 viewPager;
    private QuotePagerAdapter pagerAdapter;
    private View emptyState;

    private QuoteCollection quoteCollection;
    private List<Quote> favoriteQuotes = new ArrayList<>();
    // The quote to keep in view across list changes and recreation; -1 = start at the newest.
    private int restoreQuoteId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Host host = (Host) requireActivity();
        quoteCollection = MyApplication.getInstance().getQuoteCollection();
        if (savedInstanceState != null) {
            restoreQuoteId = savedInstanceState.getInt(STATE_QUOTE_ID, -1);
        }

        emptyState = view.findViewById(R.id.favorites_empty);
        viewPager = view.findViewById(R.id.favorites_viewpager);
        PagerPeek.apply(viewPager, R.dimen.pager_peek, R.dimen.pager_page_margin);

        pagerAdapter = new QuotePagerAdapter(new QuotePagerAdapter.QuoteInteractionListener() {
            @Override
            public void onToggleFavorite(Quote quote) {
                if (quote != null) {
                    // The list refreshes through the Collection observer below.
                    boolean isFavorite = quoteCollection.toggleFavorite(quote.getId());
                    Log.d(TAG, "Toggled favorite for quote #" + quote.getId() +
                            ", is favorite: " + isFavorite);
                }
            }

            @Override
            public void onShareQuote(Quote quote) {
                QuoteSharer.share(requireContext(), quote);
            }

            @Override
            public void onAuthorClick(Quote quote) {
                if (quote != null && !quote.getAuthor().isEmpty()) {
                    host.showSearch(QuoteQuery.forField(QuoteQuery.Field.AUTHOR, quote.getAuthor()));
                }
            }

            @Override
            public void onSourceClick(Quote quote) {
                if (quote != null && !quote.getSource().isEmpty()) {
                    host.showSearch(QuoteQuery.forField(QuoteQuery.Field.SOURCE, quote.getSource()));
                }
            }

            @Override
            public void onCategoryClick(Quote quote) {
                if (quote != null && !quote.getCategory().isEmpty()) {
                    host.showSearch(QuoteQuery.forField(QuoteQuery.Field.CATEGORY, quote.getCategory()));
                }
            }

            @Override
            public void onEditQuote(Quote quote) {
                startActivity(AddEditActivity.editIntent(requireContext(), quote.getId()));
            }
        });
        viewPager.setAdapter(pagerAdapter);
        QuotePagerAdapter.resetScrollOnPageChange(viewPager);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                int index = pagerAdapter.indexOf(position);
                if (index < favoriteQuotes.size()) {
                    restoreQuoteId = favoriteQuotes.get(index).getId();
                }
            }
        });

        quoteCollection.getQuoteList().observe(getViewLifecycleOwner(), quotes -> showFavorites());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_QUOTE_ID, restoreQuoteId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewPager = null;
        emptyState = null;
    }

    private void showFavorites() {
        // Keep the shown quote in view; if it was un-favorited, stay at the same index.
        int previousPosition = pagerAdapter.indexOf(viewPager.getCurrentItem());
        int keepId = restoreQuoteId; // setQuotes may fire onPageSelected, which overwrites it
        favoriteQuotes = quoteCollection.getFavorites();

        boolean empty = favoriteQuotes.isEmpty();
        viewPager.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        pagerAdapter.setQuotes(favoriteQuotes);
        if (empty) return;

        int position = Math.min(previousPosition, favoriteQuotes.size() - 1);
        for (int i = 0; i < favoriteQuotes.size(); i++) {
            if (favoriteQuotes.get(i).getId() == keepId) {
                position = i;
                break;
            }
        }
        viewPager.setCurrentItem(pagerAdapter.pagerPositionOf(position, viewPager.getCurrentItem()), false);
        restoreQuoteId = favoriteQuotes.get(position).getId();
    }
}
