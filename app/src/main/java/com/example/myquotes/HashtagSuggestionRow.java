package com.example.myquotes;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Add/Edit "Suggested:" row ({@code hashtag_suggestions}) under the hashtag field (#50): up
 * to 3 tags from {@link HashtagSuggester}, as assist chips with a +. A tap adds the tag as a chip.
 * The model is fitted off the main thread whenever the Quote Collection emits (so on opening),
 * leaving out the quote being edited; the row refreshes 300 ms after the text or author last
 * changed, and at once when the quote's tags change. Hidden when nothing qualifies.
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
    private HashtagSuggester model;

    /** {@code editedQuoteId} is left out of the model; -1 when adding. */
    HashtagSuggestionRow(View root, LifecycleOwner owner, QuoteCollection collection, int editedQuoteId,
                         EditText author, EditText text, EditText source, HashtagChipField field) {
        row = root.findViewById(R.id.hashtag_suggestions);
        group = root.findViewById(R.id.chip_group_suggestions);
        this.author = author;
        this.text = text;
        this.source = source;
        this.field = field;

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
        field.setOnTagsChangedListener(this::refresh);

        collection.getQuoteList().observe(owner, quotes -> {
            if (fitter.isShutdown()) return;
            List<Quote> others = new ArrayList<>();
            for (Quote quote : quotes) {
                if (quote.getId() == null || quote.getId() != editedQuoteId) others.add(quote);
            }
            fitter.execute(() -> {
                HashtagSuggester fitted = HashtagSuggester.fit(others);
                main.post(() -> {
                    if (fitter.isShutdown()) return;
                    model = fitted;
                    refresh();
                });
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
                        source.getText().toString(), field.getTags());
        group.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (String tag : suggestions) {
            Chip chip = (Chip) inflater.inflate(R.layout.chip_hashtag_suggestion, group, false);
            chip.setText(Hashtag.display(tag));
            chip.setContentDescription("Add " + Hashtag.display(tag));
            chip.setOnClickListener(v -> field.addTag(tag));
            group.addView(chip);
        }
        row.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
