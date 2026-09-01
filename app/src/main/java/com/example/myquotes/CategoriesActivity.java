package com.example.myquotes;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myquotes.databinding.ActivityCategoriesBinding;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoriesActivity extends AppCompatActivity {
    private static final String TAG = "CategoriesActivity";
    private static final String PREFS_NAME = "CategoryPrefs";
    private static final String KEY_CATEGORIES = "saved_categories";

    private ActivityCategoriesBinding binding;
    private QuoteCollection quoteCollection;
    private CategoriesAdapter adapter;
    private List<String> categories;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.Theme_MyQuotes_NoActionBar);

        binding = ActivityCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.apply(this, binding.statusBarScrim);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Categories");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        quoteCollection = MyApplication.getInstance().getQuoteCollection();

        // Setup RecyclerView
        RecyclerView recyclerView = binding.categoriesRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new CategoriesAdapter(new ArrayList<>(), new CategoriesAdapter.CategoryActionListener() {
            @Override
            public void onRenameCategory(String oldName) {
                showRenameCategoryDialog(oldName);
            }

            @Override
            public void onDeleteCategory(String categoryName) {
                showDeleteCategoryDialog(categoryName);
            }

            @Override
            public void onCategoryClick(String categoryName) {
                openSearchForCategory(categoryName);
            }
        });
        recyclerView.setAdapter(adapter);

        // Setup FAB
        binding.fabAddCategory.setOnClickListener(v -> showAddCategoryDialog());

        // Load categories
        loadCategories();
    }

    private void loadCategories() {
        List<Quote> allQuotes = quoteCollection.getCurrentList();
        categories = new ArrayList<>();

        // 1. Load saved categories from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedCategoriesJson = prefs.getString(KEY_CATEGORIES, "[]");
        try {
            JSONArray jsonArray = new JSONArray(savedCategoriesJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                String category = jsonArray.getString(i);
                if (!categories.contains(category)) {
                    categories.add(category);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading saved categories", e);
        }

        // 2. Collect any additional categories from the quotes themselves
        for (Quote quote : allQuotes) {
            String category = quote.getCategory();
            if (!category.trim().isEmpty() && !categories.contains(category)) {
                categories.add(category);
            }
        }

        // 3. Sort alphabetically
        Collections.sort(categories);

        // 4. Update adapter
        adapter.updateCategories(categories);

        Log.d(TAG, "Loaded " + categories.size() + " categories");
    }

    private void saveCategoriesToPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();

        for (String category : categories) {
            jsonArray.put(category);
        }

        prefs.edit().putString(KEY_CATEGORIES, jsonArray.toString()).apply();
        Log.d(TAG, "Saved " + categories.size() + " categories to preferences");
    }

    private void showAddCategoryDialog() {
        EditText input = new EditText(this);
        input.setHint("Category name");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int marginHorizontal = (int) (20 * getResources().getDisplayMetrics().density);
        params.leftMargin = marginHorizontal;
        params.rightMargin = marginHorizontal;
        input.setLayoutParams(params);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Category")
                .setMessage("Enter name for new category")
                .setView(container)
                .setPositiveButton("Add", (dialogInterface, which) -> {
                    String categoryName = input.getText().toString().trim();
                    if (categoryName.isEmpty()) {
                        return;
                    }
                    if (categories.contains(categoryName)) {
                        return;
                    }
                    addCategory(categoryName);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        dialog.show();

        input.requestFocus();
    }

    private void addCategory(String categoryName) {
        categories.add(categoryName);
        Collections.sort(categories);

        categories = new ArrayList<>(categories);
        adapter.updateCategories(categories);
        saveCategoriesToPreferences();

        Log.d(TAG, "Added category: " + categoryName);
    }

    private void showRenameCategoryDialog(String oldName) {
        EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int marginHorizontal = (int) (20 * getResources().getDisplayMetrics().density); // 20dp Margin
        params.leftMargin = marginHorizontal;
        params.rightMargin = marginHorizontal;
        input.setLayoutParams(params);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename Category")
                .setMessage("Enter new name for category \"" + oldName + "\"")
                .setView(container)
                .setPositiveButton("Rename", (dialogInterface, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        return;
                    }
                    if (newName.equals(oldName)) {
                        return; // nothing changed
                    }
                    renameCategory(oldName, newName);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        dialog.show();

        input.requestFocus();
        input.selectAll();
    }

    private void renameCategory(String oldName, String newName) {
        List<Quote> allQuotes = quoteCollection.getCurrentList();
        int updatedCount = 0;

        // Update Quotes
        for (Quote quote : allQuotes) {
            if (oldName.equals(quote.getCategory())) {
                quote.setCategory(newName);
                quoteCollection.update(quote);
                updatedCount++;
            }
        }

        int index = categories.indexOf(oldName);
        if (index >= 0) {
            categories.set(index, newName);
            Collections.sort(categories);
            adapter.updateCategories(categories);
            saveCategoriesToPreferences();
        }

        Log.d(TAG, "Renamed category in " + updatedCount + " quotes");
    }

    private void showDeleteCategoryDialog(String categoryName) {
        List<Quote> allQuotes = quoteCollection.getCurrentList();
        int affectedQuotes = 0;

        for (Quote quote : allQuotes) {
            if (categoryName.equals(quote.getCategory())) {
                affectedQuotes++;
            }
        }

        final int count = affectedQuotes;

        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Delete category \"" + categoryName + "\"?\n\n" +
                        "This will remove the category from " + count + " quote(s).")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteCategory(categoryName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategory(String categoryName) {
        List<Quote> allQuotes = quoteCollection.getCurrentList();
        int updatedCount = 0;

        for (Quote quote : allQuotes) {
            if (categoryName.equals(quote.getCategory())) {
                quote.setCategory(null);
                quoteCollection.update(quote);
                updatedCount++;
            }
        }

        categories.remove(categoryName);
        adapter.updateCategories(categories);
        saveCategoriesToPreferences();

        Log.d(TAG, "Removed category from " + updatedCount + " quotes");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openSearchForCategory(String categoryName) {
        Intent intent = new Intent(this, SearchActivity.class);
        intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, categoryName);
        intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, "category");
        startActivity(intent);
    }
}