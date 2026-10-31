package com.example.myquotes;

import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

public class StatisticsActivity extends AppCompatActivity {
    private static final String STATE_EXPANDED = "expanded";

    private QuoteCollection quoteCollection;

    private TextView textTotalQuotes;
    private TextView textFavorites;
    private TextView textNoHashtags;
    private TextView textUnknownAdded;
    private MonthlyBarChartView chartAddedPerMonth;

    private final StatItemAdapter authorAdapter = new StatItemAdapter(QuoteQuery.Field.AUTHOR);
    private final StatItemAdapter sourceAdapter = new StatItemAdapter(QuoteQuery.Field.SOURCE);
    private final StatItemAdapter hashtagAdapter = new StatItemAdapter(QuoteQuery.Field.HASHTAGS);
    private List<ShowMoreToggle> toggles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        setupToolbar();
        setupViewModel();
        setupViews();
        if (savedInstanceState != null) restoreExpanded(savedInstanceState.getBooleanArray(STATE_EXPANDED));

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
        textNoHashtags = findViewById(R.id.text_no_hashtags);
        textUnknownAdded = findViewById(R.id.text_unknown_added);
        chartAddedPerMonth = findViewById(R.id.chart_added_per_month);

        toggles = Arrays.asList(
                bindList(R.id.recycler_top_authors, R.id.toggle_top_authors, authorAdapter, R.plurals.stats_more_authors),
                bindList(R.id.recycler_top_sources, R.id.toggle_top_sources, sourceAdapter, R.plurals.stats_more_sources),
                bindList(R.id.recycler_hashtags, R.id.toggle_hashtags, hashtagAdapter, R.plurals.stats_more_hashtags));
    }

    private ShowMoreToggle bindList(int recyclerId, int toggleId, StatItemAdapter adapter, int morePlural) {
        RecyclerView recycler = findViewById(recyclerId);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        return new ShowMoreToggle(findViewById(toggleId), adapter, morePlural);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        boolean[] expanded = new boolean[toggles.size()];
        for (int i = 0; i < expanded.length; i++) expanded[i] = toggles.get(i).adapter.isExpanded();
        outState.putBooleanArray(STATE_EXPANDED, expanded);
    }

    private void restoreExpanded(boolean[] expanded) {
        if (expanded == null) return;
        for (int i = 0; i < expanded.length && i < toggles.size(); i++) {
            toggles.get(i).adapter.setExpanded(expanded[i]);
        }
    }

    private void showStatistics(List<Quote> quotes) {
        QuoteStatistics stats = QuoteStatistics.of(quotes);
        if (stats.total == 0) {
            textTotalQuotes.setText("No quotes available");
            return;
        }
        textTotalQuotes.setText("Total Quotes: " + stats.total);
        textFavorites.setText("Favorites: " + stats.favoriteCount);
        textNoHashtags.setText("Without hashtags: " + stats.withoutHashtagsCount);
        authorAdapter.setItems(stats.authors);
        sourceAdapter.setItems(stats.sources);
        hashtagAdapter.setItems(stats.hashtags);
        for (ShowMoreToggle toggle : toggles) toggle.refresh();
        chartAddedPerMonth.setMonths(stats.addedPerMonth);
        textUnknownAdded.setVisibility(stats.unknownAddedCount > 0 ? View.VISIBLE : View.GONE);
        textUnknownAdded.setText(stats.unknownAddedCount
                + (stats.unknownAddedCount == 1 ? " quote" : " quotes")
                + " added before tracking began");
    }

    /**
     * The toggle under a ranked list: "+ 3 more authors" while collapsed and "Show less" while
     * expanded, each with a chevron. Hidden while the list fits in its collapsed rows.
     */
    private static final class ShowMoreToggle {
        final StatItemAdapter adapter;
        private final View view;
        private final TextView label;
        private final View chevron;
        private final int morePlural;

        ShowMoreToggle(View view, StatItemAdapter adapter, int morePlural) {
            this.view = view;
            this.adapter = adapter;
            this.morePlural = morePlural;
            label = view.findViewById(R.id.text_show_more);
            chevron = view.findViewById(R.id.icon_show_more);
            view.setOnClickListener(v -> {
                // Animates the card's height, like Compose's animateContentSize
                TransitionManager.beginDelayedTransition((ViewGroup) view.getRootView());
                adapter.setExpanded(!adapter.isExpanded());
                refresh();
            });
        }

        void refresh() {
            int overflow = adapter.overflowCount();
            view.setVisibility(overflow > 0 ? View.VISIBLE : View.GONE);
            label.setText(adapter.isExpanded()
                    ? view.getContext().getString(R.string.stats_show_less)
                    : view.getResources().getQuantityString(morePlural, overflow, overflow));
            chevron.setRotation(adapter.isExpanded() ? 180 : 0);
        }
    }
}
