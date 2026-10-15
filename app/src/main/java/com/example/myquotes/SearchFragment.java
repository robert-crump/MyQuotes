package com.example.myquotes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Search over the Quote Collection: the Search tab in MainActivity, and the body of the pushed
 * SearchActivity. Holds one {@link QuoteQuery}; a tapped result opens in a {@link QuoteDialogFragment}.
 * The filter icon in the search field picks what the text searches: all fields or one; for
 * author, source and hashtags the field suggests existing values. The hashtag filter (a tag or
 * "No hashtag", #51) shows as a chip under the field and combines with the text: a tapped
 * hashtag arrives as {@link QuoteQuery#forTag} with an empty field; the filter dialog's
 * "Hashtag" button picks one, the chip's x removes it.
 */
public class SearchFragment extends Fragment
        implements SearchResultsAdapter.OnQuoteClickListener, QuoteDialogFragment.Host {
    private static final String TAG = "SearchFragment";
    private static final String STATE_QUERY = "search_query_state";

    /** Filter dialog entries; null is "all fields". */
    private static final QuoteQuery.Field[] FILTERS = {
            null, QuoteQuery.Field.QUOTE_TEXT, QuoteQuery.Field.AUTHOR,
            QuoteQuery.Field.SOURCE, QuoteQuery.Field.HASHTAGS};
    private static final String[] FILTER_LABELS = {"All fields", "Quote", "Author", "Source", "Hashtags"};
    private static final String[] FILTER_HINTS = {
            "Search all fields", "Search in quote text", "Search in authors",
            "Search in sources", "Search in hashtags"};

    private TextInputLayout searchInputLayout;
    private MaterialAutoCompleteTextView searchEditText;
    private FieldSuggestionAdapter suggestionAdapter;
    private TextView searchResultsCountTextView;
    private Chip hashtagFilterChip;
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
        // The filter is all fields or exactly one; older multi-field states widen to all.
        if (restored != null) query = restored.singleField() != null ? restored : restored.scopedTo(null);
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
        searchInputLayout = view.findViewById(R.id.searchInputLayout);
        searchEditText = view.findViewById(R.id.searchEditText);
        searchResultsCountTextView = view.findViewById(R.id.searchResultsCountTextView);
        hashtagFilterChip = view.findViewById(R.id.hashtagFilterChip);
        hashtagFilterChip.setOnCloseIconClickListener(v -> {
            query = query.withoutTagFilter();
            renderFilter();
            rerunSearch();
        });
        hashtagFilterChip.setOnClickListener(v -> showHashtagFilterDialog());

        quoteCollection = MyApplication.getInstance().getQuoteCollection();

        RecyclerView searchResultsRecyclerView = view.findViewById(R.id.search_scroll);
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchResultsAdapter(new ArrayList<>(), this);
        searchResultsRecyclerView.setAdapter(adapter);

        suggestionAdapter = new FieldSuggestionAdapter(requireContext());
        searchEditText.setAdapter(suggestionAdapter);
        // Set before the watcher is attached; a restored view state rewrites the same text.
        searchEditText.setText(query.getText(), false);
        searchInputLayout.setStartIconOnClickListener(v -> showFilterDialog());
        renderFilter();
        setupSearchInput();

        quoteCollection.getQuoteList().observe(getViewLifecycleOwner(), quotes -> {
            allQuotes = quotes != null ? quotes : new ArrayList<>();
            suggestionAdapter.setQuotes(allQuotes);
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
        searchInputLayout = null;
        hashtagFilterChip = null;
    }

    /** Replaces the current query (text and filter), as an author/source/hashtag tap does. */
    @Override
    public void applyQuery(QuoteQuery newQuery) {
        query = newQuery;
        if (searchEditText == null) return; // the view picks the query up when it is created
        renderFilter();
        searchEditText.setText(newQuery.getText(), false);
        searchEditText.clearFocus();
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
                searchEditText.dismissDropDown();
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

        // A picked suggestion fills the field (the watcher reruns the search); show the results.
        searchEditText.setOnItemClickListener((parent, view, position, id) -> hideKeyboard());
    }

    /** "Select a filter, then enter text": picking a filter focuses the field for typing. */
    private void showFilterDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Search in")
                .setSingleChoiceItems(FILTER_LABELS, filterIndex(), (dialog, which) -> {
                    dialog.dismiss();
                    query = query.scopedTo(FILTERS[which]);
                    renderFilter();
                    rerunSearch();
                    focusSearchField();
                })
                .setNeutralButton("Hashtag…", (dialog, which) -> showHashtagFilterDialog())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Picks the hashtag filter: "No hashtag" or one tag, each with its quote count. */
    private void showHashtagFilterDialog() {
        Map<String, Integer> counts = Hashtags.counts(allQuotes);
        int untagged = 0;
        for (Quote quote : allQuotes) {
            if (quote.getTags().isEmpty()) untagged++;
        }
        List<String> tags = new ArrayList<>(counts.keySet());
        String[] labels = new String[tags.size() + 1];
        labels[0] = "No hashtag (" + untagged + ")";
        int checked = query.isUntagged() ? 0 : -1;
        for (int i = 0; i < tags.size(); i++) {
            labels[i + 1] = Hashtag.display(tags.get(i)) + " (" + counts.get(tags.get(i)) + ")";
            if (tags.get(i).equalsIgnoreCase(query.getTag())) checked = i + 1;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter by hashtag")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    query = which == 0 ? query.withUntagged() : query.withTag(tags.get(which - 1));
                    renderFilter();
                    rerunSearch();
                })
                .setNegativeButton("Cancel", null);
        if (query.hasTagFilter()) {
            builder.setNeutralButton("No filter", (dialog, which) -> {
                query = query.withoutTagFilter();
                renderFilter();
                rerunSearch();
            });
        }
        builder.show();
    }

    private int filterIndex() {
        QuoteQuery.Field field = query.singleField();
        for (int i = 0; i < FILTERS.length; i++) {
            if (FILTERS[i] == field) return i;
        }
        return 0;
    }

    /** The field label names the filter; the filter icon is tinted while it narrows the search. */
    private void renderFilter() {
        int index = filterIndex();
        searchInputLayout.setHint(FILTER_HINTS[index]);
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(index == 0
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : androidx.appcompat.R.attr.colorPrimary, value, true);
        searchInputLayout.setStartIconTintList(ColorStateList.valueOf(value.data));
        suggestionAdapter.setField(FILTERS[index]);

        String tag = query.getTag();
        hashtagFilterChip.setVisibility(query.hasTagFilter() ? View.VISIBLE : View.GONE);
        hashtagFilterChip.setText(tag != null ? Hashtag.display(tag) : "No hashtag");
        hashtagFilterChip.setCloseIconContentDescription("Remove hashtag filter");
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
        QuoteDialogFragment.newInstance(quote.getId())
                .show(getChildFragmentManager(), QuoteDialogFragment.TAG);
    }
}
