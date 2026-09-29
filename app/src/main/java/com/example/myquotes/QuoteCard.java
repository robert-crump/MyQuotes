package com.example.myquotes;

import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * Binds a Quote to the quote card ({@code view_quote_card}): text, attribution, favorite state,
 * and the card's actions, which go to a {@link Listener}. Used by the quote pagers and by the
 * search result dialog.
 */
public final class QuoteCard {

    /** What the card's buttons and author/source/category links do. */
    public interface Listener {
        void onToggleFavorite(Quote quote);
        void onShareQuote(Quote quote);
        void onAuthorClick(Quote quote);
        void onSourceClick(Quote quote);
        void onCategoryClick(Quote quote);
        void onEditQuote(Quote quote);
    }

    private static final long DOUBLE_TAP_MS = 300;

    private final TextView textQuote;
    private final TextView textAuthor;
    private final TextView textSource;
    private final TextView textCategory;
    private final ImageButton buttonFavorite;
    private final ImageButton buttonShare;
    private final ImageButton buttonEdit;

    /** @param root the card, or any view containing it */
    public QuoteCard(View root) {
        textQuote = root.findViewById(R.id.text_quote);
        textAuthor = root.findViewById(R.id.text_author);
        textSource = root.findViewById(R.id.text_source);
        textCategory = root.findViewById(R.id.text_category);
        buttonFavorite = root.findViewById(R.id.button_favorite);
        buttonShare = root.findViewById(R.id.button_share);
        buttonEdit = root.findViewById(R.id.button_edit);
    }

    public void bind(Quote quote, Listener listener) {
        textQuote.setText(quote.getQuoteText());
        bindOptional(textAuthor, quote.getAuthor().isEmpty() ? "" : "— " + quote.getAuthor());
        bindOptional(textSource, quote.getSource());
        bindOptional(textCategory, quote.getCategory());

        buttonFavorite.setImageResource(quote.isFavorite()
                ? R.drawable.ic_favorite_heart_filled
                : R.drawable.ic_favorite_heart);

        buttonFavorite.setOnClickListener(v -> listener.onToggleFavorite(quote));
        buttonShare.setOnClickListener(v -> listener.onShareQuote(quote));
        buttonEdit.setOnClickListener(v -> listener.onEditQuote(quote));
        textAuthor.setOnClickListener(v -> listener.onAuthorClick(quote));
        textSource.setOnClickListener(v -> listener.onSourceClick(quote));
        textCategory.setOnClickListener(v -> listener.onCategoryClick(quote));

        // Double-tap the text to toggle favorite
        final long[] lastTapTime = {0};
        textQuote.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTapTime[0] <= DOUBLE_TAP_MS) {
                    listener.onToggleFavorite(quote);
                    lastTapTime[0] = 0;
                } else {
                    lastTapTime[0] = currentTime;
                }
            }
            return false;
        });
    }

    /** Shows {@code text} in {@code view}, or hides the view when the text is empty. */
    private static void bindOptional(TextView view, String text) {
        view.setText(text);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
