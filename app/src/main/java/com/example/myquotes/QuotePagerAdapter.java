package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

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

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quote_page, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        Quote quote = quotes.get(position);
        holder.bind(quote);
    }

    @Override
    public int getItemCount() {
        return quotes.size();
    }

    class QuoteViewHolder extends RecyclerView.ViewHolder {
        private final TextView textQuote;
        private final TextView textAuthor;
        private final ChipGroup chipGroupMeta;
        private final Chip textSource;
        private final Chip textCategory;
        private final ImageButton buttonFavorite;
        private final ImageButton buttonShare;
        private final ImageButton buttonEdit;

        public QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textQuote = itemView.findViewById(R.id.text_quote);
            textAuthor = itemView.findViewById(R.id.text_author);
            textSource = itemView.findViewById(R.id.text_source);
            chipGroupMeta = itemView.findViewById(R.id.chip_group_meta);
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
            chipGroupMeta.setVisibility(quote.getSource().isEmpty() && quote.getCategory().isEmpty()
                    ? View.GONE : View.VISIBLE);

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
            android.widget.ScrollView scrollView = (android.widget.ScrollView) itemView;
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