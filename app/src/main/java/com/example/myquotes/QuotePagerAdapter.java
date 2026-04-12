package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class QuotePagerAdapter extends RecyclerView.Adapter<QuotePagerAdapter.QuoteViewHolder> {

    private List<Quote> quotes = new ArrayList<>();
    private final QuoteInteractionListener listener;

    public interface QuoteInteractionListener {
        void onRateQuote(Quote quote, int delta);
        void onToggleFavorite(Quote quote);
        void onShareQuote(Quote quote);
        void onAuthorClick(Quote quote);
        void onSourceClick(Quote quote);
        void onCategoryClick(Quote quote);
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
        private final TextView textSource;
        private final TextView textCategory;
        private final TextView textRatingInfo;
        private final ImageButton buttonFavorite;
        private final ImageButton buttonShare;
        private final MaterialButton buttonThumbsUp;
        private final MaterialButton buttonThumbsDown;

        public QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textQuote = itemView.findViewById(R.id.text_quote);
            textAuthor = itemView.findViewById(R.id.text_author);
            textSource = itemView.findViewById(R.id.text_source);
            textCategory = itemView.findViewById(R.id.text_category);
            textRatingInfo = itemView.findViewById(R.id.text_rating_info);
            buttonFavorite = itemView.findViewById(R.id.button_favorite);
            buttonShare = itemView.findViewById(R.id.button_share);
            buttonThumbsUp = itemView.findViewById(R.id.button_thumbs_up);
            buttonThumbsDown = itemView.findViewById(R.id.button_thumbs_down);
        }

        public void bind(Quote quote) {
            textQuote.setText(quote.getQuoteText());
            textAuthor.setText(quote.getAuthor());

            // Source visibility
            if (quote.getSource() != null && !quote.getSource().isEmpty()) {
                textSource.setText(quote.getSource());
                textSource.setVisibility(View.VISIBLE);
            } else {
                textSource.setVisibility(View.GONE);
            }

            // Category visibility
            if (quote.getCategory() != null && !quote.getCategory().isEmpty()) {
                textCategory.setText(quote.getCategory());
                textCategory.setVisibility(View.VISIBLE);
            } else {
                textCategory.setVisibility(View.GONE);
            }

            // Rating
            textRatingInfo.setText("Rating: " + quote.getRating());

            // Favorite Icon
            if (quote.isFavorite()) {
                buttonFavorite.setImageResource(R.drawable.ic_favorite_heart_filled);
            } else {
                buttonFavorite.setImageResource(R.drawable.ic_favorite_heart);
            }

            // Click Listeners
            buttonThumbsUp.setOnClickListener(v -> listener.onRateQuote(quote, 1));
            buttonThumbsDown.setOnClickListener(v -> listener.onRateQuote(quote, -1));
            buttonFavorite.setOnClickListener(v -> listener.onToggleFavorite(quote));
            buttonShare.setOnClickListener(v -> listener.onShareQuote(quote));

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
    }
}