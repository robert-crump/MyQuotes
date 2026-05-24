package com.example.myquotes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity implements SearchResultsAdapter.OnQuoteClickListener {
    private static final String TAG = "SearchActivity";
    private static final int MIN_QUERY_LENGTH = 3;

    public static final String EXTRA_SEARCH_QUERY = "search_query";
    public static final String EXTRA_FILTER_TYPE = "filter_type";

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

    // Active filter states
    private boolean filterQuoteEnabled = true;
    private boolean filterAuthorEnabled = true;
    private boolean filterSourceEnabled = true;
    private boolean filterCategoryEnabled = true;

    // Current search query, kept for result highlighting
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
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

            // Re-run any active search to reflect the updated list
            String currentQuery = searchEditText.getText().toString();
            if (!currentQuery.isEmpty() && currentQuery.length() >= MIN_QUERY_LENGTH) {
                performSearch(currentQuery);
            }
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
        String searchQuery = getIntent().getStringExtra(EXTRA_SEARCH_QUERY);
        if (searchQuery == null || searchQuery.isEmpty()) {
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
        Intent intent = getIntent();

        String searchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY);
        if (searchQuery != null && !searchQuery.isEmpty()) {
            searchEditText.setText(searchQuery);

            // Disable all filters, then enable only the requested one
            filterQuoteEnabled = false;
            filterAuthorEnabled = false;
            filterSourceEnabled = false;
            filterCategoryEnabled = false;

            String filterType = intent.getStringExtra(EXTRA_FILTER_TYPE);
            if (filterType != null) {
                switch (filterType) {
                    case "author":
                        filterAuthorEnabled = true;
                        break;
                    case "source":
                        filterSourceEnabled = true;
                        break;
                    case "category":
                        filterCategoryEnabled = true;
                        break;
                }
            }

            // Update die Chip-Styles basierend auf den neuen boolean-Werten
            updateFilterButtonStates();

            performSearch(searchQuery);
        }
    }

    private void setupSearchInput() {
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                performSearch(searchEditText.getText().toString());
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
                if (s.length() >= MIN_QUERY_LENGTH) {
                    performSearch(s.toString());
                } else {
                    adapter.updateResults(new ArrayList<>(), "");
                    updateResultCount(0);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    private void setupFilterButtons() {
        setupChip(filterQuote, filterQuoteEnabled, () -> {
            filterQuoteEnabled = !filterQuoteEnabled;
            updateFilterButtonStates();
            performSearch(searchEditText.getText().toString());
        });

        setupChip(filterAuthor, filterAuthorEnabled, () -> {
            filterAuthorEnabled = !filterAuthorEnabled;
            updateFilterButtonStates();
            performSearch(searchEditText.getText().toString());
        });

        setupChip(filterSource, filterSourceEnabled, () -> {
            filterSourceEnabled = !filterSourceEnabled;
            updateFilterButtonStates();
            performSearch(searchEditText.getText().toString());
        });

        setupChip(filterCategory, filterCategoryEnabled, () -> {
            filterCategoryEnabled = !filterCategoryEnabled;
            updateFilterButtonStates();
            performSearch(searchEditText.getText().toString());
        });

        updateFilterButtonStates();
    }

    private void setupChip(com.google.android.material.chip.Chip chip, boolean isEnabled, Runnable onToggle) {
        chip.setOnClickListener(v -> onToggle.run());
        chip.setCheckable(false); // Deaktiviere das Standard-Checkbox-Verhalten
    }

    private void updateFilterButtonStates() {
        updateChipStyle(filterQuote, filterQuoteEnabled);
        updateChipStyle(filterAuthor, filterAuthorEnabled);
        updateChipStyle(filterSource, filterSourceEnabled);
        updateChipStyle(filterCategory, filterCategoryEnabled);
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

    private void performSearch(String query) {
        String searchQuery = query.toLowerCase().trim();
        List<Quote> results = new ArrayList<>();

        if (allQuotes == null) {
            Log.w(TAG, "performSearch called but allQuotes is null");
            allQuotes = new ArrayList<>();
            adapter.updateResults(results, "");
            updateResultCount(0);
            return;
        }

        currentSearchQuery = searchQuery;

        if (searchQuery.length() < MIN_QUERY_LENGTH) {
            adapter.updateResults(results, "");
            updateResultCount(0);
            return;
        }

        for (Quote quote : allQuotes) {
            boolean matches = false;

            if (filterQuoteEnabled && quote.getQuoteText().toLowerCase().contains(searchQuery)) {
                matches = true;
            }
            if (filterAuthorEnabled && quote.getAuthor().toLowerCase().contains(searchQuery)) {
                matches = true;
            }
            if (filterSourceEnabled && quote.getSource().toLowerCase().contains(searchQuery)) {
                matches = true;
            }
            if (filterCategoryEnabled && quote.getCategory() != null &&
                    quote.getCategory().toLowerCase().contains(searchQuery)) {
                matches = true;
            }

            if (matches) {
                results.add(quote);
            }
        }

        Log.d(TAG, "Search for '" + searchQuery + "' found " + results.size() + " results");

        adapter.updateResults(results, currentSearchQuery);
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

            String currentQuery = searchEditText.getText().toString();
            if (!currentQuery.isEmpty() && currentQuery.length() >= MIN_QUERY_LENGTH) {
                performSearch(currentQuery);
            }

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

            String currentQuery = searchEditText.getText().toString();
            if (!currentQuery.isEmpty() && currentQuery.length() >= MIN_QUERY_LENGTH) {
                performSearch(currentQuery);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}