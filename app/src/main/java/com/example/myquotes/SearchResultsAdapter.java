package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {
    private List<Quote> quotes = new ArrayList<>();
    private String searchQuery = "";
    private OnQuoteClickListener clickListener;

    public interface OnQuoteClickListener {
        void onQuoteClick(Quote quote);
    }

    public SearchResultsAdapter(List<Quote> quotes, OnQuoteClickListener listener) {
        this.quotes = quotes;
        this.clickListener = listener;
    }

    public void updateResults(List<Quote> newQuotes, String query) {
        this.quotes = newQuotes;
        this.searchQuery = query;
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
        holder.bind(quote, position + 1, searchQuery);
    }

    @Override
    public int getItemCount() {
        return quotes.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView searchResultNumber;
        private final TextView authorSourceTextView;
        private final TextView quoteSnippetTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            searchResultNumber = itemView.findViewById(R.id.text_search_result_number);
            authorSourceTextView = itemView.findViewById(R.id.text_author_source);
            quoteSnippetTextView = itemView.findViewById(R.id.text_quote_snippet);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onQuoteClick(quotes.get(position));
                }
            });
        }

        public void bind(Quote quote, int position, String query) {
            searchResultNumber.setText(String.valueOf(position));

            String author = quote.getAuthor();
            String source = quote.getSource();
            String authorSource = author + " - " + source;
            authorSourceTextView.setText(authorSource);

            String snippet = getSnippet(quote.getQuoteText(), query);
            quoteSnippetTextView.setText(snippet);
        }

        private String getSnippet(String text, String query) {
            if (query.isEmpty()) {
                int maxLength = 100;
                if (text.length() <= maxLength) {
                    return text;
                } else {
                    return text.substring(0, maxLength) + "...";
                }
            }

            int index = text.toLowerCase().indexOf(query.toLowerCase());
            if (index == -1) {
                int maxLength = 100;
                if (text.length() <= maxLength) {
                    return text;
                } else {
                    return text.substring(0, maxLength) + "...";
                }
            }

            // Zeige Kontext um den Suchbegriff
            int start = Math.max(0, index - 40);
            int end = Math.min(text.length(), index + query.length() + 60);

            String snippet = text.substring(start, end);
            if (start > 0) snippet = "..." + snippet;
            if (end < text.length()) snippet = snippet + "...";

            return snippet;
        }
    }
}