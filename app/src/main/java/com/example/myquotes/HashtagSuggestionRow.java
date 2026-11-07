package com.example.myquotes;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Add/Edit "Suggested:" row ({@code hashtag_suggestions}) under the hashtag field (#50): up
 * to 3 tags from {@link HashtagSuggester}, as assist chips with a +. A tap adds the tag as a chip.
 * The model comes from the shared {@link SuggestionModel} (#65), refitted there without the quote
 * being edited whenever a newer fit is published (so on opening); the row refreshes 300 ms after the text, author or source
 * last changed, and at once when the quote's tags change. Hidden when nothing qualifies.
 * The info button next to the label explains all current suggestions in one dialog (#51, #52):
 * how suggesting works, then each tag's reasons with counts, then the tags not suggested for this
 * quote. A long-press on a chip explains that one suggestion and offers "Don't suggest for this
 * quote" (#55): the tag joins the form's rejected tags at once ({@link #getRejected}; saved with
 * the quote) and the next-best tag can take its place. In both dialogs a "similar to ..." part
 * is a link that shows and hides the neighbour quotes with the tag (#63).
 * With {@link QuoteEmbeddings} (#62) the model has the similar-quotes reason: it refits when
 * vectors are added (a newer shared fit), and on each refresh the typed text is embedded by
 * {@link QuoteEmbeddings#embedLatest} (#67: on the cache's own thread, only the latest text); until its
 * vector arrives the previous text's vector is used.
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
    private final Runnable refresh = this::refresh;
    private final QuoteCollection collection;
    private final QuoteEmbeddings embeddings;
    private final SuggestionModel suggestionModel;
    // The latest shared fit (#65), and the leave-one-out fit being computed from it.
    private SuggestionModel.Fitted base;
    private SuggestionModel.Request fitting;
    // The vector of vectorText, the quote text last embedded; null for none.
    private float[] textVector;
    private String vectorText;
    // The text whose vector is being computed and its request; a newer text cancels it.
    private String embeddingText;
    private QuoteEmbeddings.Request embedding;
    private boolean released;
    private int editedQuoteId;
    private HashtagSuggester model;
    private List<String> shown = Collections.emptyList();
    private final ArrayList<String> rejected = new ArrayList<>();
    // The tag shown first whenever eligible (#56: a "Suggested for" run); null for none.
    private String pinned;

    /** {@code editedQuoteId} is left out of the model; -1 when adding. */
    HashtagSuggestionRow(View root, LifecycleOwner owner, QuoteCollection collection, QuoteEmbeddings embeddings,
                         SuggestionModel suggestionModel, int editedQuoteId, EditText author, EditText text, EditText source,
                         HashtagChipField field) {
        row = root.findViewById(R.id.hashtag_suggestions);
        group = root.findViewById(R.id.chip_group_suggestions);
        this.author = author;
        this.text = text;
        this.source = source;
        this.field = field;
        this.collection = collection;
        this.embeddings = embeddings;
        this.suggestionModel = suggestionModel;
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

        suggestionModel.getFitted().observe(owner, fitted -> {
            base = fitted;
            fit();
        });
    }

    /** The editor now shows another quote (the tagging run, #53): refits without it. */
    void setEditedQuoteId(int quoteId) {
        editedQuoteId = quoteId;
        model = null;
        refresh();
        fit();
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

    /**
     * The model from the latest shared fit: as it is when adding, else refitted without the edited
     * quote on the Suggestion model's thread.
     */
    private void fit() {
        if (fitting != null) fitting.cancel();
        fitting = null;
        if (base == null) return;
        if (editedQuoteId == -1) {
            model = base.model;
            refresh();
            return;
        }
        fitting = suggestionModel.fitWithout(base, editedQuoteId, fitted -> {
            fitting = null;
            model = fitted;
            refresh();
        });
    }

    /** Drops the pending fit, refreshes and embedding (from the Activity's onDestroy). */
    void release() {
        main.removeCallbacksAndMessages(null);
        if (fitting != null) fitting.cancel();
        if (embedding != null) embedding.cancel();
        released = true;
    }

    /**
     * Brings {@link #textVector} up to the typed quote text: at once from the cache, else through
     * the Quote Embeddings' thread, which refreshes again when the vector arrives.
     */
    private void updateTextVector() {
        String current = text.getText().toString();
        if (released || current.equals(vectorText) || current.equals(embeddingText)) return;
        if (embedding != null) embedding.cancel(); // typed on meanwhile
        embedding = null;
        embeddingText = current;
        QuoteEmbeddings.Request request = embeddings.embedLatest(current, vector -> {
            // Null while embedLatest answers at once: the running refresh uses the vector.
            boolean later = embedding != null;
            embedding = null;
            embeddingText = null;
            textVector = vector;
            vectorText = current;
            if (later) refresh();
        });
        if (current.equals(embeddingText)) embedding = request; // not answered yet
    }

    private void refresh() {
        main.removeCallbacks(refresh);
        updateTextVector();
        List<String> suggestions = model == null ? Collections.emptyList()
                : model.suggest(text.getText().toString(), author.getText().toString(),
                        source.getText().toString(), field.getTags(), rejected, pinned, textVector);
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
                source.getText().toString(), field.getTags(), tag, textVector);
    }

    /** One suggestion's reasons, with "Don't suggest for this quote" (#55). */
    private void showSuggestion(String tag) {
        if (model == null) return;
        HashtagSuggester.Explanation explanation = explain(tag);
        WhyMessage message = new WhyMessage(null, explanation == null ? Collections.emptyList()
                : Collections.singletonList(explanation), false, null);
        message.attach(new MaterialAlertDialogBuilder(row.getContext())
                .setTitle(Hashtag.display(tag))
                .setMessage(explanation == null ? null : message.build())
                .setPositiveButton("Don't suggest for this quote", (dialog, which) -> {
                    if (!Hashtag.containsIgnoreCase(rejected, tag)) rejected.add(tag);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    /**
     * One dialog explaining every shown suggestion: the intro, then "#Love" and "heart (6/8 quotes),
     * ...", then "Not suggested for this quote: #Hope" when the form has rejected tags.
     */
    private void showExplanation() {
        if (model == null || shown.isEmpty()) return;
        List<HashtagSuggester.Explanation> explanations = new ArrayList<>();
        for (String tag : shown) {
            HashtagSuggester.Explanation explanation = explain(tag);
            if (explanation != null) explanations.add(explanation);
        }
        WhyMessage message = new WhyMessage(HashtagSuggestionText.INTRO, explanations, true,
                HashtagSuggestionText.notSuggestedLine(rejected, field.getTags()));
        message.attach(new MaterialAlertDialogBuilder(row.getContext())
                .setTitle("Why these hashtags")
                .setMessage(message.build())
                .setPositiveButton("OK", null)
                .show());
    }

    /**
     * A dialog message of explanation lines whose "similar to N of the K closest tagged quotes" part
     * is a link (#63): a tap shows the neighbour quotes with the tag under the line (a snippet each,
     * most similar first), another tap hides them. Neighbours are looked up in the Quote Collection
     * when the dialog opens; a deleted one is skipped.
     */
    private final class WhyMessage {
        private final String intro;
        private final List<HashtagSuggester.Explanation> explanations;
        private final boolean headings;
        private final String footer;
        private final Map<Integer, String> texts = new HashMap<>();
        // The tags whose neighbour quotes are shown.
        private final Set<String> expanded = new HashSet<>();
        private TextView view;

        /** {@code intro} and {@code footer} may be null; {@code headings} puts the tag above each line. */
        WhyMessage(String intro, List<HashtagSuggester.Explanation> explanations, boolean headings,
                   String footer) {
            this.intro = intro;
            this.explanations = explanations;
            this.headings = headings;
            this.footer = footer;
            List<Quote> quotes = collection.getQuoteList().getValue();
            if (quotes != null) {
                for (Quote quote : quotes) {
                    if (quote.getId() != null) texts.put(quote.getId(), quote.getQuoteText());
                }
            }
        }

        /** Makes the shown dialog's links tappable. */
        void attach(AlertDialog dialog) {
            view = dialog.findViewById(android.R.id.message);
            if (view != null) view.setMovementMethod(LinkMovementMethod.getInstance());
        }

        CharSequence build() {
            SpannableStringBuilder message = new SpannableStringBuilder();
            if (intro != null) message.append(intro);
            for (HashtagSuggester.Explanation explanation : explanations) {
                if (message.length() > 0) message.append("\n\n");
                if (headings) {
                    int start = message.length();
                    message.append(Hashtag.display(explanation.tag));
                    message.setSpan(new StyleSpan(Typeface.BOLD), start, message.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    message.append("\n");
                }
                int lineStart = message.length();
                String line = HashtagSuggestionText.explanation(explanation);
                message.append(line);
                if (explanation.similar == null) continue;
                String similar = HashtagSuggestionText.similar(explanation.similar);
                int at = line.indexOf(similar);
                if (at < 0) continue;
                message.setSpan(new Toggle(explanation), lineStart + at, lineStart + at + similar.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (expanded.contains(explanation.tag)) {
                    for (String neighbour : neighbours(explanation)) message.append("\n").append(neighbour);
                }
            }
            if (footer != null) message.append("\n\n").append(footer);
            return message;
        }

        private List<String> neighbours(HashtagSuggester.Explanation explanation) {
            return HashtagSuggestionText.neighbourLines(explanation.similarQuoteIds, texts);
        }

        private final class Toggle extends ClickableSpan {
            private final HashtagSuggester.Explanation explanation;

            Toggle(HashtagSuggester.Explanation explanation) {
                this.explanation = explanation;
            }

            @Override
            public void onClick(@NonNull View widget) {
                String tag = explanation.tag;
                boolean show = expanded.add(tag);
                if (!show) expanded.remove(tag);
                if (view == null) return;
                view.setText(build());
                int count = neighbours(explanation).size();
                view.announceForAccessibility(show
                        ? "Showing " + HashtagSuggestionText.quotes(count) + " with " + Hashtag.display(tag)
                        : "Hid the quotes with " + Hashtag.display(tag));
            }
        }
    }
}
