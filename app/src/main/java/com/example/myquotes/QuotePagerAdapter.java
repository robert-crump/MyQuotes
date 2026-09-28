package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class QuotePagerAdapter extends RecyclerView.Adapter<QuotePagerAdapter.QuoteViewHolder> {

    private List<Quote> quotes = new ArrayList<>();
    private final QuoteInteractionListener listener;

    public interface QuoteInteractionListener {
        void onToggleFavorite(Quote quote);
        void onShareQuote(Quote quote);
        void onAuthorClick(Quote quote);
        void onSourceClick(Quote quote);
        void onCategoryClick(Quote quote);
        void onEditQuote(Quote quote);
    }

    public interface ScrollDirectionListener {
        void onScrollDown();
        void onScrollUp();
    }

    private ScrollDirectionListener scrollDirectionListener;

    public void setScrollDirectionListener(ScrollDirectionListener listener) {
        this.scrollDirectionListener = listener;
    }

    public QuotePagerAdapter(QuoteInteractionListener listener) {
        this.listener = listener;
    }

    public void setQuotes(List<Quote> quotes) {
        this.quotes = quotes;
        notifyDataSetChanged();
    }

    /** The quote list index shown at this pager position (the pager loops, see {@link LoopingPositions}). */
    public int indexOf(int pagerPosition) {
        return LoopingPositions.indexOf(pagerPosition, quotes.size());
    }

    /** The pager position showing quote list {@code index}, near pager position {@code around}. */
    public int pagerPositionOf(int index, int around) {
        return LoopingPositions.pagerPositionOf(index, quotes.size(), around);
    }

    /**
     * Scrolls every page but the current one back to its top once a swipe settles, so a card
     * swiped away (and still peeking) starts at the top when it comes back.
     */
    public static void resetScrollOnPageChange(ViewPager2 pager) {
        RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) return;
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View page = recyclerView.getChildAt(i);
                    if (recyclerView.getChildAdapterPosition(page) != pager.getCurrentItem()) {
                        page.scrollTo(0, 0);
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quote_page, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        Quote quote = quotes.get(indexOf(position));
        // A recycled page keeps its old scroll offset.
        holder.itemView.scrollTo(0, 0);
        holder.bind(quote);
    }

    @Override
    public int getItemCount() {
        return LoopingPositions.count(quotes.size());
    }

    class QuoteViewHolder extends RecyclerView.ViewHolder {
        private final TextView textQuote;
        private final TextView textAuthor;
        private final TextView textSource;
        private final TextView textCategory;
        private final ImageButton buttonFavorite;
        private final ImageButton buttonShare;
        private final ImageButton buttonEdit;

        public QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textQuote = itemView.findViewById(R.id.text_quote);
            textAuthor = itemView.findViewById(R.id.text_author);
            textSource = itemView.findViewById(R.id.text_source);
            textCategory = itemView.findViewById(R.id.text_category);
            buttonFavorite = itemView.findViewById(R.id.button_favorite);
            buttonShare = itemView.findViewById(R.id.button_share);
            buttonEdit = itemView.findViewById(R.id.button_edit);
        }

        public void bind(Quote quote) {
            textQuote.setText(quote.getQuoteText());
            bindOptional(textAuthor, quote.getAuthor().isEmpty() ? "" : "— " + quote.getAuthor());
            bindOptional(textSource, quote.getSource());
            bindOptional(textCategory, quote.getCategory());

            buttonFavorite.setImageResource(quote.isFavorite()
                    ? R.drawable.ic_favorite_heart_filled
                    : R.drawable.ic_favorite_heart);

            // Click Listeners
            buttonFavorite.setOnClickListener(v -> listener.onToggleFavorite(quote));
            buttonShare.setOnClickListener(v -> listener.onShareQuote(quote));
            buttonEdit.setOnClickListener(v -> listener.onEditQuote(quote));

            // Delegate clicks to the listener so the hosting activity handles navigation
            textAuthor.setOnClickListener(v -> listener.onAuthorClick(quote));

            // Source Click
            textSource.setOnClickListener(v -> listener.onSourceClick(quote));

            // Category Click
            textCategory.setOnClickListener(v -> listener.onCategoryClick(quote));

            // Double-tap to toggle favorite
            final long[] lastTapTime = {0};
            textQuote.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastTapTime[0] <= 300) {
                        listener.onToggleFavorite(quote);
                        lastTapTime[0] = 0;
                    } else {
                        lastTapTime[0] = currentTime;
                    }
                }
                return false;
            });

            // Scroll direction listener for FAB hide/show
            ScrollView scrollView = (ScrollView) itemView;
            scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollDirectionListener != null) {
                    if (scrollY == 0) {
                        // Always show FAB when at the top
                        scrollDirectionListener.onScrollUp();
                    } else {
                        int dy = scrollY - oldScrollY;
                        if (dy > 0) {
                            scrollDirectionListener.onScrollDown();
                        } else if (dy < 0) {
                            scrollDirectionListener.onScrollUp();
                        }
                    }
                }
            });
        }

        /** Shows {@code text} in {@code view}, or hides the view when the text is empty. */
        private void bindOptional(TextView view, String text) {
            view.setText(text);
            view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}