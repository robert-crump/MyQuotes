package com.example.myquotes;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;

/**
 * A search result opened in place: the quote card over the dimmed screen, closed by its X, Back
 * or a tap outside the card. The card is live: it follows the Quote Collection (favorite, edits)
 * and closes when the quote is deleted. An author/source/hashtag tap closes it and hands that
 * query to the {@link Host} (the parent fragment). Opening a quote here is not a view: it
 * doesn't bump {@code timesShown}/{@code lastShown}.
 */
public class QuoteDialogFragment extends DialogFragment implements QuoteCard.Listener {
    public static final String TAG = "QuoteDialogFragment";
    private static final String ARG_QUOTE_ID = "quote_id";

    /** Runs the query of a tapped author, source or hashtag. */
    public interface Host {
        void applyQuery(QuoteQuery query);
    }

    private QuoteCollection quoteCollection;

    public static QuoteDialogFragment newInstance(int quoteId) {
        QuoteDialogFragment fragment = new QuoteDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_QUOTE_ID, quoteId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_MyQuotes_QuoteDialog);
        quoteCollection = MyApplication.getInstance().getQuoteCollection();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_quote, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The window spans the screen, so a tap outside the card lands on the root.
        view.setOnClickListener(v -> dismiss());
        view.findViewById(R.id.button_close).setOnClickListener(v -> dismiss());
        keepClearOfSystemBars(view);

        QuoteCard card = new QuoteCard(view);
        int quoteId = requireArguments().getInt(ARG_QUOTE_ID);
        quoteCollection.getQuoteList().observe(getViewLifecycleOwner(), quotes -> {
            Quote quote = quoteCollection.findById(quoteId);
            if (quote == null) {
                dismiss(); // deleted, e.g. from the editor opened here
            } else {
                card.bind(quote, this);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /** Adds the system bar insets to the layout's own padding (the window is edge-to-edge). */
    private static void keepClearOfSystemBars(View root) {
        int start = root.getPaddingStart();
        int top = root.getPaddingTop();
        int end = root.getPaddingEnd();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPaddingRelative(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    public void onToggleFavorite(Quote quote) {
        // The card rebinds through the Collection observer.
        quoteCollection.toggleFavorite(quote.getId());
    }

    @Override
    public void onShareQuote(Quote quote) {
        QuoteSharer.share(requireContext(), quote);
    }

    @Override
    public void onAuthorClick(Quote quote) {
        showField(QuoteQuery.Field.AUTHOR, quote.getAuthor());
    }

    @Override
    public void onSourceClick(Quote quote) {
        showField(QuoteQuery.Field.SOURCE, quote.getSource());
    }

    @Override
    public void onTagClick(Quote quote, String tag) {
        ((Host) requireParentFragment()).applyQuery(QuoteQuery.forTag(tag));
        dismiss();
    }

    @Override
    public void onEditQuote(Quote quote) {
        startActivity(AddEditActivity.editIntent(requireContext(), quote.getId()));
    }

    private void showField(QuoteQuery.Field field, String value) {
        if (value.isEmpty()) return;
        ((Host) requireParentFragment()).applyQuery(QuoteQuery.forField(field, value));
        dismiss();
    }
}
