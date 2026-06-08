package com.example.myquotes;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.*;
import java.util.stream.Collectors;

public class StatisticsActivity extends AppCompatActivity {
    private QuoteCollection quoteCollection;

    private TextView textTotalQuotes;
    private TextView textFavorites;
    private RecyclerView recyclerTopAuthors;
    private RecyclerView recyclerTopSources;
    private RecyclerView recyclerCategories;
    private TextView textNoCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        setupToolbar();
        setupViewModel();
        setupViews();

        quoteCollection.getQuoteList().observe(this, this::loadStatistics);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        setTitle("Statistics");
    }

    private void setupViewModel() {
        quoteCollection = MyApplication.getInstance().getQuoteCollection();
    }

    private void setupViews() {
        textTotalQuotes = findViewById(R.id.text_total_quotes);
        textFavorites = findViewById(R.id.text_favorites);
        recyclerTopAuthors = findViewById(R.id.recycler_top_authors);
        recyclerTopSources = findViewById(R.id.recycler_top_sources);
        recyclerCategories = findViewById(R.id.recycler_categories);
        textNoCategory = findViewById(R.id.text_no_category);

        recyclerTopAuthors.setLayoutManager(new LinearLayoutManager(this));
        recyclerTopSources.setLayoutManager(new LinearLayoutManager(this));
        recyclerCategories.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadStatistics(List<Quote> quotes)   {
        if (quotes == null || quotes.isEmpty()) {
            textTotalQuotes.setText("No quotes available");
            return;
        }

        // Basis-Statistiken
        int totalQuotes = quotes.size();
        int favoriteCount = (int) quotes.stream().filter(Quote::isFavorite).count();

        textTotalQuotes.setText("Total Quotes: " + totalQuotes);
        textFavorites.setText("Favorites: " + favoriteCount);

        // Top 10 Autoren
        Map<String, Integer> authorCounts = new HashMap<>();
        for (Quote quote : quotes) {
            String author = quote.getAuthor();
            authorCounts.put(author, authorCounts.getOrDefault(author, 0) + 1);
        }

        List<StatItem> topAuthors = authorCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .map(e -> new StatItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // Top 10 Quellen
        Map<String, Integer> sourceCounts = new HashMap<>();
        for (Quote quote : quotes) {
            String source = quote.getSource();
            if (!source.isEmpty()) {
                sourceCounts.put(source, sourceCounts.getOrDefault(source, 0) + 1);
            }
        }

        List<StatItem> topSources = sourceCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .map(e -> new StatItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // Kategorien-Statistik
        Map<String, Integer> categoryCounts = new HashMap<>();
        int noCategoryCount = 0;

        for (Quote quote : quotes) {
            String category = quote.getCategory();
            if (category != null && !category.isEmpty()) {
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
            } else {
                noCategoryCount++;
            }
        }

        List<StatItem> topCategories = categoryCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .map(e -> new StatItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // Anzahl ohne Kategorie anzeigen
        textNoCategory.setText("Without category: " + noCategoryCount);

        StatItemAdapter authorAdapter = new StatItemAdapter(topAuthors, "Author");
        recyclerTopAuthors.setAdapter(authorAdapter);

        StatItemAdapter sourceAdapter = new StatItemAdapter(topSources, "Source");
        recyclerTopSources.setAdapter(sourceAdapter);

        StatItemAdapter categoryAdapter = new StatItemAdapter(topCategories, "Category");
        recyclerCategories.setAdapter(categoryAdapter);
    }

    public static class StatItem {
        public final String name;
        public final int count;

        public StatItem(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}