package com.example.myquotes;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.myquotes.notifications.QuoteNotifications;

/**
 * Thin pushed host of {@link SearchFragment}, opened only through {@link QuoteQuery#toIntent}
 * (Statistics, Categories); Back returns there. The Search tab in MainActivity is the main entry.
 */
public class SearchActivity extends AppCompatActivity implements SearchFragment.Host {

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

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.search_container, SearchFragment.newInstance(QuoteQuery.fromIntent(getIntent())))
                    .commit();
        }
    }

    @Override
    public void onSearchResultClick(int quoteId) {
        // Back to the (single) MainActivity, which opens the Quotes tab at this quote.
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(QuoteNotifications.EXTRA_QUOTE_ID, quoteId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
