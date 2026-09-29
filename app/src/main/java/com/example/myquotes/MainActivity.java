package com.example.myquotes;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.myquotes.databinding.ActivityMainBinding;
import com.example.myquotes.notifications.QuoteNotifications;

/**
 * Bottom-navigation host: one app bar, the Quotes / Search / Favorites tab fragments (added
 * once, switched with show/hide so each keeps its state) and the add-quote FAB (Quotes only).
 */
public class MainActivity extends AppCompatActivity
        implements QuotesFragment.Host, FavoritesFragment.Host {
    private static final String TAG = "MainActivity";
    private static final String STATE_TAB = "selected_tab";

    private static final String TAG_QUOTES = "tab_quotes";
    private static final String TAG_SEARCH = "tab_search";
    private static final String TAG_FAVORITES = "tab_favorites";

    private ActivityMainBinding binding;

    private QuotesFragment quotesFragment;
    private SearchFragment searchFragment;
    private FavoritesFragment favoritesFragment;

    private int selectedTab = R.id.tab_quotes;
    private boolean isFabHidden = false;

    private final OnBackPressedCallback backToQuotes = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            selectTab(R.id.tab_quotes);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // The bottom nav pads itself for the navigation bar.
        EdgeToEdgeUtils.applyAboveBottomNav(this, binding.statusBarScrim);
        setSupportActionBar(binding.toolbar);

        QuoteNotifications.requestPostNotificationsPermission(this);
        QuoteNotifications.promptBackgroundPermissionIfNeeded(this);

        // Reuse the fragments restored after recreation instead of adding duplicates.
        FragmentManager fm = getSupportFragmentManager();
        quotesFragment = (QuotesFragment) fm.findFragmentByTag(TAG_QUOTES);
        searchFragment = (SearchFragment) fm.findFragmentByTag(TAG_SEARCH);
        favoritesFragment = (FavoritesFragment) fm.findFragmentByTag(TAG_FAVORITES);
        if (quotesFragment == null) {
            quotesFragment = new QuotesFragment();
            searchFragment = SearchFragment.newInstance(null);
            favoritesFragment = new FavoritesFragment();
            fm.beginTransaction()
                    .add(R.id.tab_container, quotesFragment, TAG_QUOTES)
                    .add(R.id.tab_container, searchFragment, TAG_SEARCH)
                    .add(R.id.tab_container, favoritesFragment, TAG_FAVORITES)
                    .hide(searchFragment)
                    .hide(favoritesFragment)
                    .commitNow();
        }

        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_TAB, R.id.tab_quotes);
        }
        getOnBackPressedDispatcher().addCallback(this, backToQuotes);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.tab_search) searchFragment.focusSearchField();
        });
        binding.bottomNav.setSelectedItemId(selectedTab);
        showTab(selectedTab);

        binding.fabAddQuote.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditActivity.class);
            intent.putExtra(AddEditActivity.EXTRA_ACTION, AddEditActivity.ACTION_ADD);
            startActivity(intent);
        });

        Intent intent = getIntent();
        QuoteNotifications.recordOpenedFromNotification(this, intent, savedInstanceState);
        if (savedInstanceState == null && intent.hasExtra(QuoteNotifications.EXTRA_QUOTE_ID)) {
            int quoteId = intent.getIntExtra(QuoteNotifications.EXTRA_QUOTE_ID, -1);
            Log.d(TAG, "Opened from notification with quote ID: " + quoteId);
            if (quoteId != -1) showQuote(quoteId);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        QuoteNotifications.recordOpenedFromNotification(this, intent, null);
        if (intent.hasExtra(QuoteNotifications.EXTRA_QUOTE_ID)) {
            int quoteId = intent.getIntExtra(QuoteNotifications.EXTRA_QUOTE_ID, -1);
            if (quoteId != -1) showQuote(quoteId);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }

    /** Selects a tab through the bottom nav, which calls back into {@link #showTab}. */
    private void selectTab(int tabId) {
        binding.bottomNav.setSelectedItemId(tabId);
    }

    private void showTab(int tabId) {
        if (tabId != R.id.tab_search) searchFragment.hideKeyboard();
        selectedTab = tabId;

        Fragment shown = tabId == R.id.tab_search ? searchFragment
                : tabId == R.id.tab_favorites ? favoritesFragment
                : quotesFragment;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        for (Fragment f : new Fragment[]{quotesFragment, searchFragment, favoritesFragment}) {
            if (f == shown) tx.show(f); else tx.hide(f);
        }
        tx.commitNow();

        boolean onQuotes = tabId == R.id.tab_quotes;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(onQuotes ? getString(R.string.tab_quotes)
                    : tabId == R.id.tab_search ? getString(R.string.tab_search)
                    : getString(R.string.tab_favorites));
        }
        binding.fabAddQuote.setVisibility(onQuotes ? View.VISIBLE : View.GONE);
        backToQuotes.setEnabled(!onQuotes);
        binding.appBar.setLiftOnScrollTargetViewId(
                tabId == R.id.tab_search ? R.id.search_scroll : View.NO_ID);
        binding.appBar.setLifted(false);
        invalidateOptionsMenu();
    }

    /** Opens the Quotes tab at this quote (notification). */
    private void showQuote(int quoteId) {
        selectTab(R.id.tab_quotes);
        quotesFragment.navigateTo(quoteId);
    }

    @Override
    public void showSearch(QuoteQuery query) {
        searchFragment.applyQuery(query);
        selectTab(R.id.tab_search);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Boolean enabled = QuoteNotifications.onPermissionResult(this, requestCode, grantResults);
        if (enabled != null) {
            Toast.makeText(this, enabled ? "Notifications enabled" : "Notification permission denied",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_settings).setVisible(selectedTab == R.id.tab_quotes);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void hideFab() {
        if (!isFabHidden) {
            isFabHidden = true;
            binding.fabAddQuote.animate()
                    .translationY(binding.fabAddQuote.getHeight() +
                            ((ViewGroup.MarginLayoutParams) binding.fabAddQuote.getLayoutParams()).bottomMargin)
                    .setDuration(200)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
    }

    @Override
    public void showFab() {
        if (isFabHidden) {
            isFabHidden = false;
            binding.fabAddQuote.animate()
                    .translationY(0)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
