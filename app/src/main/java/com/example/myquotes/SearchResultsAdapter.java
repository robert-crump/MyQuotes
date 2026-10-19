package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {
    private List<Quote> quotes = new ArrayList<>();
    private QuoteQuery query = QuoteQuery.all("");
    // Per quote id, its hashtag suggestions (the "No hashtag" list, #53); null elsewhere.
    private Map<Integer, List<HashtagSuggester.Suggestion>> suggestions;
    private OnQuoteClickListener clickListener;

    public interface OnQuoteClickListener {
        void onQuoteClick(Quote quote);
    }

    public SearchResultsAdapter(List<Quote> quotes, OnQuoteClickListener listener) {
        this.quotes = quotes;
        this.clickListener = listener;
    }

    public void updateResults(List<Quote> newQuotes, QuoteQuery query,
                              Map<Integer, List<HashtagSuggester.Suggestion>> suggestions) {
        this.quotes = newQuotes;
        this.query = query;
        this.suggestions = suggestions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Quote quote = quotes.get(position);
        holder.bind(quote, position + 1, query);
    }

    @Override
    public int getItemCount() {
        return quotes.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView searchResultNumber;
        private final TextView authorSourceTextView;
        private final TextView quoteSnippetTextView;
        private final TextView suggestionsTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            searchResultNumber = itemView.findViewById(R.id.text_search_result_number);
            authorSourceTextView = itemView.findViewById(R.id.text_author_source);
            quoteSnippetTextView = itemView.findViewById(R.id.text_quote_snippet);
            suggestionsTextView = itemView.findViewById(R.id.text_suggestions);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onQuoteClick(quotes.get(position));
                }
            });
        }

        public void bind(Quote quote, int position, QuoteQuery query) {
            searchResultNumber.setText(String.valueOf(position));

            String author = quote.getAuthor();
            String source = quote.getSource();
            String authorSource = author + " - " + source;
            authorSourceTextView.setText(authorSource);

            String snippet = query.snippet(quote);
            quoteSnippetTextView.setText(snippet);

            List<HashtagSuggester.Suggestion> quoteSuggestions =
                    suggestions == null ? null : suggestions.get(quote.getId());
            String line = quoteSuggestions == null ? null : HashtagSuggestionText.suggestedLine(quoteSuggestions);
            suggestionsTextView.setText(line);
            suggestionsTextView.setVisibility(line == null ? View.GONE : View.VISIBLE);
        }
    }
}