package com.example.myquotes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.ListPopupWindow;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Adds or edits a quote. Opened from the "No hashtag" list ({@link #taggingRunIntent}) it runs a
 * tagging run (#53, {@link TaggingRun}): saving a quote that had no hashtags with some asks once
 * "Continue with next untagged quote?", and from then on each save loads the next quote in place
 * (best suggestions first, re-ranked with the refitted model). Skip moves on without saving, and
 * so does Delete after deleting; a save that leaves the quote untagged skips it. Back ends the run.
 */
public class AddEditActivity extends AppCompatActivity {
    private static final String TAG = "AddEditActivity";
    public static final String EXTRA_ACTION = "ACTION";
    public static final String EXTRA_QUOTE_ID = "QUOTE_ID";
    public static final String ACTION_ADD = "ADD_QUOTE";
    public static final String ACTION_EDIT = "EDIT_QUOTE";
    static final String EXTRA_TAGGING_RUN = "TAGGING_RUN";

    /** Intent that opens the editor for the quote with {@code quoteId}. */
    public static Intent editIntent(Context context, int quoteId) {
        return new Intent(context, AddEditActivity.class)
                .putExtra(EXTRA_ACTION, ACTION_EDIT)
                .putExtra(EXTRA_QUOTE_ID, quoteId);
    }

    /** The editor for {@code quoteId} as the start of a tagging run through {@code query}'s untagged quotes. */
    public static Intent taggingRunIntent(Context context, int quoteId, QuoteQuery query) {
        return editIntent(context, quoteId).putExtra(EXTRA_TAGGING_RUN, new TaggingRun(query).toBundle());
    }

    private EditText editTextAuthor;
    private EditText editTextQuote;
    private EditText editTextSource;
    private static final String STATE_TAGS = "hashtags";
    private static final String STATE_QUOTE_ID = "quote_id";
    private static final String STATE_RUN = "tagging_run";

    private HashtagChipField hashtagField;
    private HashtagSuggestionRow hashtagSuggestions;
    private QuoteCollection quoteCollection;
    private SuggestionProvider suggestionProvider;
    private ListPopupWindow authorPopup;
    private ListPopupWindow sourcePopup;
    private int quoteId = -1;
    private boolean isEditMode = false;
    private boolean isLoadingQuote = false;
    // What the form held when opened (empty for Add, the loaded quote for Edit); closing with a
    // form that differs from it asks for confirmation.
    private QuoteFormSnapshot baseline = QuoteFormSnapshot.EMPTY;
    // The tagging run when opened from the "No hashtag" list; else null.
    private TaggingRun run;
    private boolean loadedUntagged;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService runFitter = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_quote);

        setupToolbar();
        setupViewModel();
        setupViews();
        run = TaggingRun.fromBundle(savedInstanceState != null
                ? savedInstanceState.getBundle(STATE_RUN)
                : getIntent().getBundleExtra(EXTRA_TAGGING_RUN));
        handleIntent(savedInstanceState);
        hashtagSuggestions = new HashtagSuggestionRow(findViewById(R.id.hashtag_field), this,
                quoteCollection, quoteId, editTextAuthor, editTextQuote, editTextSource, hashtagField);
        if (savedInstanceState != null) {
            List<String> tags = savedInstanceState.getStringArrayList(STATE_TAGS);
            if (tags != null) hashtagField.setTags(tags);
        }
        setupBackHandling();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeUtils.apply(this, findViewById(R.id.status_bar_scrim), true);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupViewModel() {
        quoteCollection = MyApplication.getInstance().getQuoteCollection();
        suggestionProvider = new SuggestionProvider();
    }

    private void setupViews() {
        editTextAuthor = findViewById(R.id.edit_text_author);
        editTextQuote = findViewById(R.id.edit_text_quote);
        editTextSource = findViewById(R.id.edit_text_source);
        hashtagField = new HashtagChipField(findViewById(R.id.hashtag_field), this,
                quoteCollection, MyApplication.getInstance().getHashtags());

        // The quote field grows from 6 to 10 lines, then scrolls internally; keep drags inside it from
        // being taken over by the surrounding ScrollView while it can still scroll.
        editTextQuote.setOnTouchListener((v, event) -> {
            if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                v.getParent().requestDisallowInterceptTouchEvent(
                        event.getActionMasked() != MotionEvent.ACTION_UP
                                && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
            }
            return false;
        });

        setupAuthorSuggestions();
        setupSourceSuggestions();
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (readForm().equals(baseline)) {
                    finish();
                } else {
                    showDiscardDialog(AddEditActivity.this::finish);
                }
            }
        });
    }

    private void showDiscardDialog(Runnable onDiscard) {
        new AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setPositiveButton("Discard", (dialog, which) -> onDiscard.run())
                .setNegativeButton("Keep editing", null)
                .show();
    }

    // Deleting closes the editor straight away, or in a tagging run moves on like Skip: the quote
    // is gone, so any unsaved edits are moot.
    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Quote")
                .setMessage("Are you sure you want to delete this quote?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    quoteCollection.deleteById(quoteId);
                    Toast.makeText(this, "Quote deleted", Toast.LENGTH_SHORT).show();
                    if (run != null) {
                        moveOn();
                    } else {
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_add_edit, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_delete).setVisible(isEditMode);
        menu.findItem(R.id.action_skip).setVisible(run != null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveQuote();
            return true;
        } else if (item.getItemId() == R.id.action_skip) {
            if (readForm().equals(baseline)) {
                skipQuote();
            } else {
                showDiscardDialog(this::skipQuote);
            }
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            showDeleteDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupAuthorSuggestions() {
        authorPopup = new ListPopupWindow(this);
        authorPopup.setAnchorView(editTextAuthor);
        authorPopup.setModal(false);

        authorPopup.setOnItemClickListener((parent, view, position, id) -> {
            String selected = ((SuggestionAdapter) parent.getAdapter()).getStringAt(position);
            if (selected == null) return;
            editTextAuthor.setText(selected);
            editTextAuthor.setSelection(selected.length());
            authorPopup.dismiss();
            editTextSource.requestFocus();
        });

        editTextAuthor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isLoadingQuote) return;
                String input = s.toString().trim();
                if (input.length() < 2) {
                    authorPopup.dismiss();
                    return;
                }
                List<Quote> quotes = quoteCollection.getCurrentList();
                List<String> suggestions = suggestionProvider.getAuthorSuggestions(quotes, input);
                if (suggestions.isEmpty()) {
                    authorPopup.dismiss();
                    return;
                }
                SuggestionAdapter suggestionAdapter = SuggestionAdapter.forAuthors(AddEditActivity.this, suggestions);
                authorPopup.setAdapter(suggestionAdapter);
                authorPopup.setWidth(editTextAuthor.getWidth());
                authorPopup.setHeight(calcPopupHeight(suggestionAdapter.getSuggestionCount(), false));
                authorPopup.show();
            }
        });

        editTextAuthor.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) authorPopup.dismiss();
        });
    }

    private void setupSourceSuggestions() {
        sourcePopup = new ListPopupWindow(this);
        sourcePopup.setAnchorView(editTextSource);
        sourcePopup.setModal(false);

        sourcePopup.setOnItemClickListener((parent, view, position, id) -> {
            String selected = ((SuggestionAdapter) parent.getAdapter()).getStringAt(position);
            if (selected == null) return;
            editTextSource.setText(selected);
            editTextSource.setSelection(selected.length());
            sourcePopup.dismiss();
        });

        editTextSource.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isLoadingQuote) return;
                String input = s.toString().trim();
                if (input.length() < 2) {
                    sourcePopup.dismiss();
                    return;
                }
                String authorContext = editTextAuthor.getText().toString().trim();
                List<Quote> quotes = quoteCollection.getCurrentList();
                SourceSuggestions suggestions = suggestionProvider.getSourceSuggestions(quotes, input, authorContext);
                if (suggestions.authorSources.isEmpty() && suggestions.otherSources.isEmpty()) {
                    sourcePopup.dismiss();
                    return;
                }
                SuggestionAdapter suggestionAdapter = SuggestionAdapter.forSources(AddEditActivity.this, suggestions);
                sourcePopup.setAdapter(suggestionAdapter);
                sourcePopup.setWidth(editTextSource.getWidth());
                sourcePopup.setHeight(calcPopupHeight(suggestionAdapter.getSuggestionCount(), suggestions.hasSplit()));
                sourcePopup.show();
            }
        });

        editTextSource.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) sourcePopup.dismiss();
        });
    }

    private int calcPopupHeight(int suggestionCount, boolean hasDivider) {
        float density = getResources().getDisplayMetrics().density;
        int itemHeightPx = Math.round(48 * density);
        int dividerHeightPx = Math.round(density); // 1dp
        int visibleItems = Math.min(suggestionCount, 4);
        int height = visibleItems * itemHeightPx;
        if (hasDivider) height += dividerHeightPx;
        return height;
    }

    private void handleIntent(Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (intent == null) return;

        String action = intent.getStringExtra(EXTRA_ACTION);

        if (ACTION_EDIT.equals(action)) {
            isEditMode = true;
            quoteId = intent.getIntExtra(EXTRA_QUOTE_ID, -1);
            // A tagging run may have moved on to another quote.
            if (savedInstanceState != null) quoteId = savedInstanceState.getInt(STATE_QUOTE_ID, quoteId);

            if (quoteId != -1) {
                loadQuote(quoteId);
                updateTitle();
            }
        } else {
            setTitle("Add quote");
        }
    }

    private void loadQuote(int id) {
        Quote quote = quoteCollection.findById(id);
        if (quote != null) {
            isLoadingQuote = true;
            editTextAuthor.setText(quote.getAuthor());
            editTextQuote.setText(quote.getQuoteText());
            editTextSource.setText(quote.getSource());
            hashtagField.setTags(quote.getTags());
            isLoadingQuote = false;
            baseline = QuoteFormSnapshot.of(quote);
            loadedUntagged = quote.getTags().isEmpty();
            Log.d(TAG, "Loaded quote #" + id);
        } else {
            Log.w(TAG, "Quote not found: " + id);
            Toast.makeText(this, "Quote not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveQuote() {
        if (!validateInput()) {
            return;
        }

        hashtagField.commitPending();
        Quote quote = createQuoteFromInput();

        if (isEditMode) {
            quoteCollection.edit(quoteId, quote.getAuthor(), quote.getQuoteText(),
                    quote.getSource(), quote.getTags());
            if (run != null && loadedUntagged) {
                continueRun(!quote.getTags().isEmpty());
                return;
            }
            finishWith("Quote updated");
        } else {
            quoteCollection.add(quote);
            Toast.makeText(this, "Quote added", Toast.LENGTH_SHORT).show();
            clearForm();
        }
    }

    /** "Edit quote", or during a tagging run "Edit quote · 12 left". */
    private void updateTitle() {
        setTitle(run == null ? "Edit quote"
                : "Edit quote · " + run.remaining(quoteCollection.getCurrentList()) + " left");
    }

    private void finishWith(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * After a save in a tagging run: a quote that got hashtags asks once whether to continue, then
     * the next quote opens; one left untagged is skipped once the user has said to continue.
     */
    private void continueRun(boolean tagged) {
        if (!tagged) {
            if (!run.isContinued()) {
                finishWith("Quote updated");
                return;
            }
            run.skip(quoteId);
        }
        baseline = readForm();
        withNextQuote(next -> {
            if (next == null) {
                finishWith(run.isContinued() ? "No more untagged quotes" : "Quote updated");
            } else if (run.isContinued()) {
                Toast.makeText(this, "Quote updated", Toast.LENGTH_SHORT).show();
                showQuote(next);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Continue with next untagged quote?")
                        .setPositiveButton("Continue", (dialog, which) -> {
                            run.setContinued();
                            showQuote(next);
                        })
                        .setNegativeButton("Back to list", (dialog, which) -> finishWith("Quote updated"))
                        .setOnCancelListener(dialog -> finishWith("Quote updated"))
                        .show();
            }
        });
    }

    /** Leaves the quote untagged for the rest of the run and opens the next one. */
    private void skipQuote() {
        run.skip(quoteId);
        moveOn();
    }

    /** Opens the run's next quote without asking (Skip, Delete), or ends the run when none is left. */
    private void moveOn() {
        run.setContinued();
        withNextQuote(next -> {
            if (next == null) {
                finishWith("No more untagged quotes");
            } else {
                showQuote(next);
            }
        });
    }

    /** Fits the model on the collection as it is now (after the save), then picks the run's next quote. */
    private void withNextQuote(Consumer<Quote> action) {
        List<Quote> quotes = quoteCollection.getCurrentList();
        runFitter.execute(() -> {
            HashtagSuggester model = HashtagSuggester.fit(quotes);
            main.post(() -> {
                if (isDestroyed()) return;
                action.accept(run.next(quotes, model));
            });
        });
    }

    /** Loads {@code next} into the form in place of the current quote. */
    private void showQuote(Quote next) {
        authorPopup.dismiss();
        sourcePopup.dismiss();
        hashtagField.dismissDropDown();
        editTextAuthor.setError(null);
        editTextQuote.setError(null);
        quoteId = next.getId();
        loadQuote(quoteId);
        hashtagSuggestions.setEditedQuoteId(quoteId);
        ((ScrollView) findViewById(R.id.scroll_form)).scrollTo(0, 0);
        updateTitle();
    }

    private void clearForm() {
        authorPopup.dismiss();
        sourcePopup.dismiss();
        editTextAuthor.setText("");
        editTextQuote.setText("");
        editTextSource.setText("");
        hashtagField.dismissDropDown();
        hashtagField.setTags(Collections.emptyList());
        editTextAuthor.requestFocus();
    }

    private boolean validateInput() {
        if (editTextQuote.getText().toString().trim().isEmpty()) {
            editTextQuote.setError("Quote text is required");
            editTextQuote.requestFocus();
            return false;
        }

        if (editTextAuthor.getText().toString().trim().isEmpty()) {
            editTextAuthor.setError("Author is required");
            editTextAuthor.requestFocus();
            return false;
        }

        return true;
    }

    private QuoteFormSnapshot readForm() {
        return new QuoteFormSnapshot(editTextAuthor.getText().toString(),
                editTextQuote.getText().toString(), editTextSource.getText().toString(),
                hashtagField.tagsWithPending());
    }

    private Quote createQuoteFromInput() {
        Quote quote = new Quote();
        quote.setAuthor(editTextAuthor.getText().toString().trim());
        quote.setQuoteText(editTextQuote.getText().toString().trim());
        quote.setSource(editTextSource.getText().toString().trim());
        quote.setTags(hashtagField.tagsWithPending());
        return quote;
    }

    @Override
    protected void onDestroy() {
        if (hashtagSuggestions != null) hashtagSuggestions.release();
        runFitter.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_TAGS, hashtagField.getTags());
        outState.putInt(STATE_QUOTE_ID, quoteId);
        if (run != null) outState.putBundle(STATE_RUN, run.toBundle());
    }
}
