package com.example.myquotes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.List;

public class AddEditActivity extends AppCompatActivity {
    private static final String TAG = "AddEditActivity";
    public static final String EXTRA_ACTION = "ACTION";
    public static final String EXTRA_QUOTE_ID = "QUOTE_ID";
    public static final String ACTION_ADD = "ADD_QUOTE";
    public static final String ACTION_EDIT = "EDIT_QUOTE";

    /** Intent that opens the editor for the quote with {@code quoteId}. */
    public static Intent editIntent(Context context, int quoteId) {
        return new Intent(context, AddEditActivity.class)
                .putExtra(EXTRA_ACTION, ACTION_EDIT)
                .putExtra(EXTRA_QUOTE_ID, quoteId);
    }

    private EditText editTextAuthor;
    private EditText editTextQuote;
    private EditText editTextSource;
    private android.widget.AutoCompleteTextView editTextCategory;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_quote);

        setupToolbar();
        setupViewModel();
        setupViews();
        handleIntent();
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
        editTextCategory = findViewById(R.id.edit_text_category);

        UnfilteredAdapter adapter = new UnfilteredAdapter(this);
        editTextCategory.setAdapter(adapter);
        MyApplication.getInstance().getCategories().getCategories().observe(this, names -> {
            adapter.clear();
            adapter.addAll(names);
        });
        editTextCategory.setDropDownHeight(600);

        editTextCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showCategoryPicker(v);
        });

        editTextCategory.setOnItemClickListener((parent, view, position, id) -> {
            editTextCategory.postDelayed(() -> {
                editTextQuote.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editTextQuote, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        });

        editTextCategory.setOnClickListener(this::showCategoryPicker);

        // The quote field is a fixed 6 lines and scrolls internally; keep drags inside it from
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
                    showDiscardDialog();
                }
            }
        });
    }

    private void showDiscardDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setPositiveButton("Discard", (dialog, which) -> finish())
                .setNegativeButton("Keep editing", null)
                .show();
    }

    // Deleting closes the editor straight away: the quote is gone, so any unsaved edits are moot.
    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Quote")
                .setMessage("Are you sure you want to delete this quote?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    quoteCollection.deleteById(quoteId);
                    Toast.makeText(this, "Quote deleted", Toast.LENGTH_SHORT).show();
                    finish();
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
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveQuote();
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

    private void showCategoryPicker(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        editTextCategory.showDropDown();
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

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        String action = intent.getStringExtra(EXTRA_ACTION);

        if (ACTION_EDIT.equals(action)) {
            isEditMode = true;
            quoteId = intent.getIntExtra(EXTRA_QUOTE_ID, -1);

            if (quoteId != -1) {
                loadQuote(quoteId);
                setTitle("Edit quote");
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
            editTextCategory.setText(quote.getCategory(), false);
            isLoadingQuote = false;
            baseline = QuoteFormSnapshot.of(quote);
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

        Quote quote = createQuoteFromInput();

        if (isEditMode) {
            quoteCollection.edit(quoteId, quote.getAuthor(), quote.getQuoteText(),
                    quote.getSource(), quote.getCategory());
            Toast.makeText(this, "Quote updated", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            quoteCollection.add(quote);
            Toast.makeText(this, "Quote added", Toast.LENGTH_SHORT).show();
            clearForm();
        }
    }

    private void clearForm() {
        authorPopup.dismiss();
        sourcePopup.dismiss();
        editTextAuthor.setText("");
        editTextQuote.setText("");
        editTextSource.setText("");
        editTextCategory.setText("", false);
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
                editTextCategory.getText().toString());
    }

    private Quote createQuoteFromInput() {
        Quote quote = new Quote();
        quote.setAuthor(editTextAuthor.getText().toString().trim());
        quote.setQuoteText(editTextQuote.getText().toString().trim());
        quote.setSource(editTextSource.getText().toString().trim());
        quote.setCategory(editTextCategory.getText().toString().trim());
        return quote;
    }

    /**
     * The category picker is a pick-list, not an autocomplete: it always offers the whole
     * category set. A plain ArrayAdapter would filter it by the field's current text, so an
     * edited quote's own category hid every other one.
     */
    private static final class UnfilteredAdapter extends android.widget.ArrayAdapter<String> {
        private final android.widget.Filter noFilter = new android.widget.Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                return null;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };

        UnfilteredAdapter(Context context) {
            super(context, android.R.layout.simple_dropdown_item_1line, new java.util.ArrayList<>());
        }

        @Override
        public android.widget.Filter getFilter() {
            return noFilter;
        }
    }
}
