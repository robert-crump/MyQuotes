package com.example.myquotes;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Add/Edit "Suggested:" row ({@code hashtag_suggestions}) under the hashtag field (#50): up
 * to 3 tags from {@link HashtagSuggester}, as assist chips with a +. A tap adds the tag as a chip.
 * The model is fitted off the main thread whenever the Quote Collection emits (so on opening),
 * leaving out the quote being edited; the row refreshes 300 ms after the text, author or source
 * last changed, and at once when the quote's tags change. Hidden when nothing qualifies.
 * The info button next to the label explains all current suggestions in one dialog (#51, #52):
 * how suggesting works, then each tag's reasons with counts, then the tags not suggested for this
 * quote. A long-press on a chip explains that one suggestion and offers "Don't suggest for this
 * quote" (#55): the tag joins the form's rejected tags at once ({@link #getRejected}; saved with
 * the quote) and the next-best tag can take its place.
 */
final class HashtagSuggestionRow {
    private static final long DEBOUNCE_MILLIS = 300;

    private final View row;
    private final ChipGroup group;
    private final EditText author;
    private final EditText text;
    private final EditText source;
    private final HashtagChipField field;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService fitter = Executors.newSingleThreadExecutor();
    private final Runnable refresh = this::refresh;
    private final QuoteCollection collection;
    private int editedQuoteId;
    private HashtagSuggester model;
    private List<String> shown = Collections.emptyList();
    private final ArrayList<String> rejected = new ArrayList<>();
    // The tag shown first whenever eligible (#56: a "Suggested for" run); null for none.
    private String pinned;

    /** {@code editedQuoteId} is left out of the model; -1 when adding. */
    HashtagSuggestionRow(View root, LifecycleOwner owner, QuoteCollection collection, int editedQuoteId,
                         EditText author, EditText text, EditText source, HashtagChipField field) {
        row = root.findViewById(R.id.hashtag_suggestions);
        group = root.findViewById(R.id.chip_group_suggestions);
        this.author = author;
        this.text = text;
        this.source = source;
        this.field = field;
        this.collection = collection;
        this.editedQuoteId = editedQuoteId;

        TextWatcher debounced = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                main.removeCallbacks(refresh);
                main.postDelayed(refresh, DEBOUNCE_MILLIS);
            }
        };
        author.addTextChangedListener(debounced);
        text.addTextChangedListener(debounced);
        source.addTextChangedListener(debounced);
        field.setOnTagsChangedListener(this::refresh);
        root.findViewById(R.id.button_suggestions_info).setOnClickListener(v -> showExplanation());

        collection.getQuoteList().observe(owner, this::fit);
    }

    /** The editor now shows another quote (the tagging run, #53): refits without it. */
    void setEditedQuoteId(int quoteId) {
        editedQuoteId = quoteId;
        model = null;
        refresh();
        fit(collection.getQuoteList().getValue());
    }

    /** Shows {@code tag} first whenever it is eligible (#56), still at most 3 chips; null for none. */
    void setPinned(String tag) {
        pinned = tag;
        refresh();
    }

    /** The form's rejected tags (#55): the loaded quote's, or the saved state's. */
    void setRejected(Collection<String> tags) {
        rejected.clear();
        rejected.addAll(Hashtag.normalizeAll(tags));
        refresh();
    }

    /** The form's rejected tags, including any the quote has since been given by hand. */
    ArrayList<String> getRejected() {
        return new ArrayList<>(rejected);
    }

    private void fit(List<Quote> quotes) {
        if (fitter.isShutdown() || quotes == null) return;
        int leftOut = editedQuoteId;
        List<Quote> others = new ArrayList<>();
        for (Quote quote : quotes) {
            if (quote.getId() == null || quote.getId() != leftOut) others.add(quote);
        }
        fitter.execute(() -> {
            HashtagSuggester fitted = HashtagSuggester.fit(others);
            main.post(() -> {
                if (fitter.isShutdown() || leftOut != editedQuoteId) return;
                model = fitted;
                refresh();
            });
        });
    }

    /** Stops the fitting thread and pending refreshes (from the Activity's onDestroy). */
    void release() {
        main.removeCallbacksAndMessages(null);
        fitter.shutdownNow();
    }

    private void refresh() {
        main.removeCallbacks(refresh);
        List<String> suggestions = model == null ? Collections.emptyList()
                : model.suggest(text.getText().toString(), author.getText().toString(),
                        source.getText().toString(), field.getTags(), rejected, pinned);
        shown = suggestions;
        group.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (String tag : suggestions) {
            Chip chip = (Chip) inflater.inflate(R.layout.chip_hashtag_suggestion, group, false);
            chip.setText(Hashtag.display(tag));
            chip.setContentDescription("Add " + Hashtag.display(tag));
            chip.setOnClickListener(v -> field.addTag(tag));
            chip.setOnLongClickListener(v -> {
                showSuggestion(tag);
                return true;
            });
            group.addView(chip);
        }
        row.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private HashtagSuggester.Explanation explain(String tag) {
        return model.explain(text.getText().toString(), author.getText().toString(),
                source.getText().toString(), field.getTags(), tag);
    }

    /** One suggestion's reasons, with "Don't suggest for this quote" (#55). */
    private void showSuggestion(String tag) {
        if (model == null) return;
        HashtagSuggester.Explanation explanation = explain(tag);
        new MaterialAlertDialogBuilder(row.getContext())
                .setTitle(Hashtag.display(tag))
                .setMessage(explanation == null ? null : HashtagSuggestionText.explanation(explanation))
                .setPositiveButton("Don't suggest for this quote", (dialog, which) -> {
                    if (!Hashtag.containsIgnoreCase(rejected, tag)) rejected.add(tag);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * One dialog explaining every shown suggestion: the intro, then "#Love" and "heart (6/8 quotes),
     * ...", then "Not suggested for this quote: #Hope" when the form has rejected tags.
     */
    private void showExplanation() {
        if (model == null || shown.isEmpty()) return;
        SpannableStringBuilder message = new SpannableStringBuilder(HashtagSuggestionText.INTRO);
        for (String tag : shown) {
            HashtagSuggester.Explanation explanation = explain(tag);
            if (explanation == null) continue;
            message.append("\n\n");
            int start = message.length();
            message.append(Hashtag.display(tag));
            message.setSpan(new StyleSpan(Typeface.BOLD), start, message.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            message.append("\n").append(HashtagSuggestionText.explanation(explanation));
        }
        String notSuggested = HashtagSuggestionText.notSuggestedLine(rejected, field.getTags());
        if (notSuggested != null) message.append("\n\n").append(notSuggested);
        new MaterialAlertDialogBuilder(row.getContext())
                .setTitle("Why these hashtags")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
