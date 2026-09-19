package com.example.myquotes;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StatisticsActivity extends AppCompatActivity {
    private QuoteCollection quoteCollection;

    private TextView textTotalQuotes;
    private TextView textFavorites;
    private TextView textNoCategory;

    private final StatItemAdapter authorAdapter = new StatItemAdapter(QuoteQuery.Field.AUTHOR);
    private final StatItemAdapter sourceAdapter = new StatItemAdapter(QuoteQuery.Field.SOURCE);
    private final StatItemAdapter categoryAdapter = new StatItemAdapter(QuoteQuery.Field.CATEGORY);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        setupToolbar();
        setupViewModel();
        setupViews();

        quoteCollection.getQuoteList().observe(this, this::showStatistics);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeUtils.apply(this, findViewById(R.id.status_bar_scrim));
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
        textNoCategory = findViewById(R.id.text_no_category);

        bindList(R.id.recycler_top_authors, authorAdapter);
        bindList(R.id.recycler_top_sources, sourceAdapter);
        bindList(R.id.recycler_categories, categoryAdapter);
    }

    private void bindList(int recyclerId, StatItemAdapter adapter) {
        RecyclerView recycler = findViewById(recyclerId);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
    }

    private void showStatistics(List<Quote> quotes) {
        QuoteStatistics stats = QuoteStatistics.of(quotes);
        if (stats.total == 0) {
            textTotalQuotes.setText("No quotes available");
            return;
        }
        textTotalQuotes.setText("Total Quotes: " + stats.total);
        textFavorites.setText("Favorites: " + stats.favoriteCount);
        textNoCategory.setText("Without category: " + stats.withoutCategoryCount);
        authorAdapter.setItems(stats.topAuthors);
        sourceAdapter.setItems(stats.topSources);
        categoryAdapter.setItems(stats.categories);
    }
}
