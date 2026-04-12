package com.example.myquotes;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AddEditActivity extends AppCompatActivity {
    private static final String TAG = "AddEditActivity";
    public static final String EXTRA_ACTION = "ACTION";
    public static final String EXTRA_QUOTE_ID = "QUOTE_ID";
    public static final String ACTION_ADD = "ADD_QUOTE";
    public static final String ACTION_EDIT = "EDIT_QUOTE";

    private EditText editTextAuthor;
    private EditText editTextQuote;
    private EditText editTextSource;
    private android.widget.AutoCompleteTextView editTextCategory;
    private QuoteViewModel quoteViewModel;
    private int quoteId = -1;
    private boolean isEditMode = false;

    private static final String[] CATEGORIES = {
            "Achtsamkeit und Meditation",
            "Führung und Zusammenarbeit",
            "Literarische Passagen",
            "Philosophie und Lebenssinn",
            "Produktivität und Zeitmanagement",
            "Psychologie und Verhalten"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_quote);

        setupToolbar();
        setupViewModel();
        setupViews();
        handleIntent();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupViewModel() {
        quoteViewModel = MyApplication.getInstance().getQuoteViewModel();
    }

    private void setupViews() {
        editTextAuthor = findViewById(R.id.edit_text_author);
        editTextQuote = findViewById(R.id.edit_text_quote);
        editTextSource = findViewById(R.id.edit_text_source);
        editTextCategory = findViewById(R.id.edit_text_category);

        // Setup Category Dropdown
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                CATEGORIES
        );
        editTextCategory.setAdapter(adapter);
        editTextCategory.setDropDownHeight(600);

        // Dismiss keyboard and show dropdown when category field is focused
        editTextCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                // Zeige Dropdown
                editTextCategory.showDropDown();
            }
        });

        // On category selection: move focus to the quote field
        editTextCategory.setOnItemClickListener((parent, view, position, id) -> {
            // Short delay for better UX
            editTextCategory.postDelayed(() -> {
                editTextQuote.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editTextQuote, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        });

        editTextCategory.setOnClickListener(v -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            editTextCategory.showDropDown();
        });

        editTextAuthor.requestFocus();
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
        Quote quote = quoteViewModel.getQuoteById(id);
        if (quote != null) {
            editTextAuthor.setText(quote.getAuthor());
            editTextQuote.setText(quote.getQuoteText());
            editTextSource.setText(quote.getSource());
            editTextCategory.setText(quote.getCategory());
            Log.d(TAG, "Loaded quote #" + id);
        } else {
            Log.w(TAG, "Quote not found: " + id);
            Toast.makeText(this, "Quote not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public void saveButtonOnClick(View view) {
        if (!validateInput()) {
            return;
        }

        Quote quote = createQuoteFromInput();

        if (isEditMode) {
            quote.setId(quoteId);
            quoteViewModel.updateQuote(quote);
            Toast.makeText(this, "Quote updated", Toast.LENGTH_SHORT).show();
        } else {
            quoteViewModel.addQuote(quote);
            Toast.makeText(this, "Quote added", Toast.LENGTH_SHORT).show();
        }

        quoteViewModel.setCurrentlyDisplayedQuote(quote);
        finish();
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

    private Quote createQuoteFromInput() {
        Quote quote = new Quote();
        quote.setAuthor(editTextAuthor.getText().toString().trim());
        quote.setQuoteText(editTextQuote.getText().toString().trim());
        quote.setSource(editTextSource.getText().toString().trim());
        quote.setCategory(editTextCategory.getText().toString().trim());
        return quote;
    }

    public void dismissButtonOnClick(View view) {
        Toast.makeText(this, "Changes discarded", Toast.LENGTH_SHORT).show();
        finish();
    }
}