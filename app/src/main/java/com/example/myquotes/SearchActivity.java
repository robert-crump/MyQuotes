package com.example.myquotes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myquotes.notifications.QuoteNotifications;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity implements SearchResultsAdapter.OnQuoteClickListener {
    private static final String TAG = "SearchActivity";

    private Chip filterQuote;
    private Chip filterAuthor;
    private Chip filterSource;
    private Chip filterCategory;

    private EditText searchEditText;
    private TextView searchResultsCountTextView;
    private RecyclerView searchResultsRecyclerView;
    private QuoteCollection quoteCollection;
    private SearchResultsAdapter adapter;
    private List<Quote> allQuotes;

    private QuoteQuery query = QuoteQuery.all("");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeUtils.apply(this, findViewById(R.id.status_bar_scrim));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Search");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Setup Views
        searchEditText = findViewById(R.id.searchEditText);
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView);
        searchResultsCountTextView = findViewById(R.id.searchResultsCountTextView);
        filterQuote = findViewById(R.id.filter_quote);
        filterAuthor = findViewById(R.id.filter_author);
        filterSource = findViewById(R.id.filter_source);
        filterCategory = findViewById(R.id.filter_category);

        quoteCollection = MyApplication.getInstance().getQuoteCollection();

        quoteCollection.getQuoteList().observe(this, quotes -> {
            allQuotes = quotes;
            if (allQuotes == null) {
                allQuotes = new ArrayList<>();
            }

            rerunSearch();
        });

        // Setup RecyclerView
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultsAdapter(new ArrayList<>(), this);
        searchResultsRecyclerView.setAdapter(adapter);

        // Setup Filter Buttons
        setupFilterButtons();

        // Setup Search Input
        setupSearchInput();

        // Check if launched with a pre-set search query (from author/source/category click)
        handleIntentExtras();

        // Show keyboard only when opened from Search icon (no pre-set query)
        if (QuoteQuery.fromIntent(getIntent()) == null) {
            // Opened via search icon — show keyboard
            searchEditText.requestFocus();
            searchEditText.setSelection(0);
            searchEditText.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        } else {
            // Opened from author/source/category click — no keyboard
            searchEditText.clearFocus();
        }
    }

    private void handleIntentExtras() {
        QuoteQuery initial = QuoteQuery.fromIntent(getIntent());
        if (initial != null) {
            query = initial;
            searchEditText.setText(initial.getText());
            updateFilterButtonStates();
            rerunSearch();
        }
    }

    private void setupSearchInput() {
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                rerunSearch();
                return true;
            }
            return false;
        });

        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                rerunSearch();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    private void setupFilterButtons() {
        setupChip(filterQuote, QuoteQuery.Field.QUOTE_TEXT);
        setupChip(filterAuthor, QuoteQuery.Field.AUTHOR);
        setupChip(filterSource, QuoteQuery.Field.SOURCE);
        setupChip(filterCategory, QuoteQuery.Field.CATEGORY);
        updateFilterButtonStates();
    }

    private void setupChip(com.google.android.material.chip.Chip chip, QuoteQuery.Field field) {
        chip.setOnClickListener(v -> {
            query = query.toggled(field);
            updateFilterButtonStates();
            rerunSearch();
        });
        chip.setCheckable(false);
    }

    private void updateFilterButtonStates() {
        updateChipStyle(filterQuote, query.hasField(QuoteQuery.Field.QUOTE_TEXT));
        updateChipStyle(filterAuthor, query.hasField(QuoteQuery.Field.AUTHOR));
        updateChipStyle(filterSource, query.hasField(QuoteQuery.Field.SOURCE));
        updateChipStyle(filterCategory, query.hasField(QuoteQuery.Field.CATEGORY));
    }

    private void updateChipStyle(com.google.android.material.chip.Chip chip, boolean isEnabled) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
        int primaryColor = typedValue.data;

        android.content.res.ColorStateList primaryList = android.content.res.ColorStateList.valueOf(primaryColor);
        android.content.res.ColorStateList whiteList = android.content.res.ColorStateList.valueOf(
                getResources().getColor(android.R.color.white, getTheme())
        );
        android.content.res.ColorStateList transparentList = android.content.res.ColorStateList.valueOf(
                getResources().getColor(android.R.color.transparent, getTheme())
        );

        if (isEnabled) {
            chip.setChipBackgroundColor(primaryList);
            chip.setTextColor(whiteList);
            chip.setCloseIconVisible(true);
            chip.setCloseIconTint(whiteList);
            chip.setChipStrokeColor(primaryList);
            chip.setChipStrokeWidth(0);
        } else {
            chip.setChipBackgroundColor(whiteList);
            chip.setTextColor(primaryList);
            chip.setCloseIconVisible(false);
            chip.setChipStrokeColor(primaryList);
            chip.setChipStrokeWidth(2);
        }
    }

    private void rerunSearch() {
        query = query.withText(searchEditText.getText().toString());
        List<Quote> results = query.filter(allQuotes);
        adapter.updateResults(results, query);
        updateResultCount(results.size());
    }

    private void updateResultCount(int count) {
        String message = count + " search result" + (count != 1 ? "s" : "");
        searchResultsCountTextView.setText(message);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }
    }

    @Override
    public void onQuoteClick(Quote quote) {
        // Verify the quote still exists (may have been deleted in another screen)
        Quote currentQuote = quoteCollection.findById(quote.getId());

        if (currentQuote == null) {
            Log.w(TAG, "Quote #" + quote.getId() + " no longer exists - refreshing results");

            allQuotes = quoteCollection.getQuoteList().getValue();
            if (allQuotes == null) {
                allQuotes = new ArrayList<>();
            }

            rerunSearch();

            Toast.makeText(this, "Quote no longer exists - results updated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bring MainActivity to front (keeps SearchActivity in back stack)
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(QuoteNotifications.EXTRA_QUOTE_ID, quote.getId());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Refresh list in case it changed while this activity was in the background
        List<Quote> currentQuotes = quoteCollection.getQuoteList().getValue();
        if (currentQuotes != null && !currentQuotes.equals(allQuotes)) {
            allQuotes = currentQuotes;
            rerunSearch();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}