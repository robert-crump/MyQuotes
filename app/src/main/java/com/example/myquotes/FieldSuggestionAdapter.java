package com.example.myquotes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Suggestions for the search field: existing values of the filtered field (author, source,
 * category) with their quote counts. Offers nothing while searching all fields or quote text.
 */
class FieldSuggestionAdapter extends ArrayAdapter<SuggestionProvider.FieldSuggestion> {
    static final int MAX_SUGGESTIONS = 6;

    private final SuggestionProvider provider = new SuggestionProvider();
    // Read by the filter on a worker thread; replaced (never mutated) on the main thread.
    private volatile List<Quote> quotes = Collections.emptyList();
    private volatile QuoteQuery.Field field;

    FieldSuggestionAdapter(Context context) {
        super(context, R.layout.item_field_suggestion, new ArrayList<>());
    }

    void setQuotes(List<Quote> quotes) {
        this.quotes = quotes;
    }

    /** The searched field; null while searching all fields. */
    void setField(@Nullable QuoteQuery.Field field) {
        this.field = field;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView != null ? convertView
                : LayoutInflater.from(getContext()).inflate(R.layout.item_field_suggestion, parent, false);
        SuggestionProvider.FieldSuggestion suggestion = getItem(position);
        if (suggestion != null) {
            ((TextView) view.findViewById(R.id.suggestion_value)).setText(suggestion.value);
            ((TextView) view.findViewById(R.id.suggestion_count)).setText(String.valueOf(suggestion.count));
        }
        return view;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return filter;
    }

    private final Filter filter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            QuoteQuery.Field current = field;
            List<SuggestionProvider.FieldSuggestion> suggestions = current == null || constraint == null
                    ? Collections.emptyList()
                    : provider.getFieldSuggestions(quotes, current, constraint.toString(), MAX_SUGGESTIONS);
            FilterResults results = new FilterResults();
            results.values = suggestions;
            results.count = suggestions.size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            clear();
            if (results.values != null) {
                addAll((List<SuggestionProvider.FieldSuggestion>) results.values);
            }
            notifyDataSetChanged();
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            return ((SuggestionProvider.FieldSuggestion) resultValue).value;
        }
    };
}
