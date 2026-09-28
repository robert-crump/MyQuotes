package com.example.myquotes;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

/** Quotes tab: the main browse flow, a pager over the Reading Session's Deck. */
public class QuotesFragment extends Fragment {
    private static final String TAG = "QuotesFragment";

    /** What the tab needs from MainActivity. */
    public interface Host {
        void showSearch(QuoteQuery query);
        void showFab();
        void hideFab();
    }

    private ViewPager2 viewPager;
    private QuotePagerAdapter pagerAdapter;
    private TextView quoteCounter;

    private QuoteCollection quoteCollection;
    private ReadingSession readingSession;

    private boolean isFirstDeckLoad = true;
    private int pendingQuoteId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quotes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Host host = (Host) requireActivity();
        quoteCollection = MyApplication.getInstance().getQuoteCollection();
        // Scoped to MainActivity, so the deck and position outlive this fragment's view.
        readingSession = new ViewModelProvider(requireActivity(), new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new ReadingSession(quoteCollection);
            }
        }).get(ReadingSession.class);
        isFirstDeckLoad = true;

        viewPager = view.findViewById(R.id.quotes_viewpager);
        quoteCounter = view.findViewById(R.id.quote_counter);

        pagerAdapter = new QuotePagerAdapter(new QuotePagerAdapter.QuoteInteractionListener() {
            @Override
            public void onToggleFavorite(Quote quote) {
                toggleFavorite(quote);
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

        pagerAdapter.setScrollDirectionListener(new QuotePagerAdapter.ScrollDirectionListener() {
            @Override
            public void onScrollDown() {
                host.hideFab();
            }

            @Override
            public void onScrollUp() {
                host.showFab();
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                readingSession.setPosition(position);
                updateQuoteCounter(position);
                host.showFab();
            }
        });

        // Observe deck: update adapter and restore position on structural changes.
        readingSession.getDeck().observe(getViewLifecycleOwner(), deck -> {
            if (deck == null) return;
            if (deck.isEmpty()) {
                // Before the first load the deck is just the empty placeholder; after it, an empty
                // deck means the last quote was deleted (e.g. from the editor), so clear the pager.
                if (!isFirstDeckLoad) {
                    pagerAdapter.setQuotes(deck);
                    quoteCounter.setText("");
                }
                return;
            }
            pagerAdapter.setQuotes(deck);

            if (isFirstDeckLoad) {
                isFirstDeckLoad = false;
                if (pendingQuoteId != -1) {
                    if (!readingSession.navigateTo(pendingQuoteId)) {
                        Toast.makeText(requireContext(), "Quote no longer exists", Toast.LENGTH_SHORT).show();
                    }
                    pendingQuoteId = -1;
                }
            }
            int pos = readingSession.getCurrentPosition();
            if (viewPager.getCurrentItem() != pos) {
                viewPager.setCurrentItem(pos, false);
            }
            updateQuoteCounter(pos);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewPager = null;
        quoteCounter = null;
    }

    /** Shows the quote with this id; deferred until the deck has loaded if called earlier. */
    public void navigateTo(int quoteId) {
        if (viewPager == null || isFirstDeckLoad) {
            pendingQuoteId = quoteId;
            return;
        }
        if (!readingSession.navigateTo(quoteId)) {
            Log.w(TAG, "Quote #" + quoteId + " not found in deck");
            Toast.makeText(requireContext(), "Quote no longer exists", Toast.LENGTH_SHORT).show();
            return;
        }
        int pos = readingSession.getCurrentPosition();
        viewPager.setCurrentItem(pos, false);
        updateQuoteCounter(pos);
        Log.d(TAG, "Navigated to quote #" + quoteId + " at position " + pos);
    }

    private void toggleFavorite(Quote quote) {
        if (quote != null) {
            boolean isFavorite = quoteCollection.toggleFavorite(quote.getId());
            String message = isFavorite ? "Added to favorites" : "Removed from favorites";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Quote #" + quote.getId() + " favorite: " + isFavorite);
        }
    }

    private void updateQuoteCounter(int position) {
        List<Quote> deck = readingSession.getDeck().getValue();
        if (deck != null && !deck.isEmpty() && quoteCounter != null) {
            quoteCounter.setText((position + 1) + " of " + deck.size());
        }
    }
}
