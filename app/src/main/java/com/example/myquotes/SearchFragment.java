package com.example.myquotes;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * Search over the Quote Collection: the Search tab in MainActivity, and the body of the pushed
 * SearchActivity. Holds one {@link QuoteQuery}; a tapped result goes to the {@link Host}.
 */
public class SearchFragment extends Fragment implements SearchResultsAdapter.OnQuoteClickListener {
    private static final String TAG = "SearchFragment";
    private static final String STATE_QUERY = "search_query_state";

    /** Where a tapped (still existing) result is shown. */
    public interface Host {
        void onSearchResultClick(int quoteId);
    }

    private Chip filterQuote;
    private Chip filterAuthor;
    private Chip filterSource;
    private Chip filterCategory;

    private EditText searchEditText;
    private TextView searchResultsCountTextView;
    private QuoteCollection quoteCollection;
    private SearchResultsAdapter adapter;
    private List<Quote> allQuotes = new ArrayList<>();

    private QuoteQuery query = QuoteQuery.all("");

    /** A search screen opened on {@code initial} (null: empty query over all fields). */
    public static SearchFragment newInstance(@Nullable QuoteQuery initial) {
        SearchFragment fragment = new SearchFragment();
        if (initial != null) fragment.setArguments(initial.toBundle());
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        QuoteQuery restored = savedInstanceState != null
                ? QuoteQuery.fromBundle(savedInstanceState.getBundle(STATE_QUERY))
                : QuoteQuery.fromBundle(getArguments());
        if (restored != null) query = restored;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchEditText = view.findViewById(R.id.searchEditText);
        searchResultsCountTextView = view.findViewById(R.id.searchResultsCountTextView);
        filterQuote = view.findViewById(R.id.filter_quote);
        filterAuthor = view.findViewById(R.id.filter_author);
        filterSource = view.findViewById(R.id.filter_source);
        filterCategory = view.findViewById(R.id.filter_category);

        quoteCollection = MyApplication.getInstance().getQuoteCollection();

        RecyclerView searchResultsRecyclerView = view.findViewById(R.id.search_scroll);
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchResultsAdapter(new ArrayList<>(), this);
        searchResultsRecyclerView.setAdapter(adapter);

        // Set before the watcher is attached; a restored view state rewrites the same text.
        searchEditText.setText(query.getText());
        setupFilterButtons();
        setupSearchInput();

        quoteCollection.getQuoteList().observe(getViewLifecycleOwner(), quotes -> {
            allQuotes = quotes != null ? quotes : new ArrayList<>();
            rerunSearch();
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(STATE_QUERY, query.toBundle());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchEditText = null;
    }

    /** Replaces the current query (text and fields), as an author/source/category tap does. */
    public void applyQuery(QuoteQuery newQuery) {
        query = newQuery;
        if (searchEditText == null) return; // the view picks the query up when it is created
        searchEditText.setText(newQuery.getText());
        searchEditText.clearFocus();
        updateFilterButtonStates();
        rerunSearch();
    }

    /** Focuses the search field and shows the keyboard (re-selecting the Search tab). */
    public void focusSearchField() {
        if (searchEditText == null) return;
        searchEditText.requestFocus();
        searchEditText.setSelection(searchEditText.getText().length());
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** Hides the keyboard and drops focus, e.g. when leaving the Search tab. */
    public void hideKeyboard() {
        if (searchEditText == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }
        searchEditText.clearFocus();
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

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                rerunSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {
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

    private void setupChip(Chip chip, QuoteQuery.Field field) {
        chip.setOnClickListener(v -> {
            query = query.toggled(field);
            updateFilterButtonStates();
            // Let the chip repaint first; the search itself runs on the next loop pass.
            chip.post(this::rerunSearch);
        });
    }

    /** Checked chips (styled filled via their colour selectors) are the searched fields. */
    private void updateFilterButtonStates() {
        filterQuote.setChecked(query.hasField(QuoteQuery.Field.QUOTE_TEXT));
        filterAuthor.setChecked(query.hasField(QuoteQuery.Field.AUTHOR));
        filterSource.setChecked(query.hasField(QuoteQuery.Field.SOURCE));
        filterCategory.setChecked(query.hasField(QuoteQuery.Field.CATEGORY));
    }

    private void rerunSearch() {
        if (searchEditText == null) return;
        query = query.withText(searchEditText.getText().toString());
        List<Quote> results = query.filter(allQuotes);
        adapter.updateResults(results, query);
        updateResultCount(results.size());
    }

    private void updateResultCount(int count) {
        String noun = query.isActive() ? " search result" : " quote";
        String message = count + noun + (count != 1 ? "s" : "");
        searchResultsCountTextView.setText(message);
    }

    @Override
    public void onQuoteClick(Quote quote) {
        // Verify the quote still exists (may have been deleted in another screen)
        if (quoteCollection.findById(quote.getId()) == null) {
            Log.w(TAG, "Quote #" + quote.getId() + " no longer exists - refreshing results");
            List<Quote> current = quoteCollection.getQuoteList().getValue();
            allQuotes = current != null ? current : new ArrayList<>();
            rerunSearch();
            Toast.makeText(requireContext(), "Quote no longer exists - results updated", Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        ((Host) requireActivity()).onSearchResultClick(quote.getId());
    }
}
