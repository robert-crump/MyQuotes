package com.example.myquotes;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myquotes.databinding.ActivityCategoriesBinding;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class CategoriesActivity extends AppCompatActivity {
    private static final String STATE_SELECTED = "selected_categories";

    private ActivityCategoriesBinding binding;
    private Categories categories;
    private CategoriesAdapter adapter;
    // Selection mode is "some row selected": the app bar turns into a contextual bar
    // (count, close, rename for exactly one, delete) and Back clears the selection.
    private final OnBackPressedCallback exitSelection = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            adapter.clearSelection();
        }
    };
    // Selection saved across recreation, applied once the first category set arrives.
    private ArrayList<String> pendingSelection;

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

        categories = MyApplication.getInstance().getCategories();

        // Setup RecyclerView
        RecyclerView recyclerView = binding.categoriesRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        MaterialDividerItemDecoration divider =
                new MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        divider.setDividerInsetStart((int) (72 * getResources().getDisplayMetrics().density));
        divider.setLastItemDecorated(false);
        recyclerView.addItemDecoration(divider);

        adapter = new CategoriesAdapter(new CategoriesAdapter.Listener() {
            @Override
            public void onCategoryClick(String categoryName) {
                openSearchForCategory(categoryName);
            }

            @Override
            public void onSelectionChanged(Set<String> selected) {
                renderSelectionMode(selected);
            }
        });
        recyclerView.setAdapter(adapter);
        getOnBackPressedDispatcher().addCallback(this, exitSelection);

        // Setup FAB
        binding.fabAddCategory.setOnClickListener(v -> showAddCategoryDialog());

        if (savedInstanceState != null) {
            pendingSelection = savedInstanceState.getStringArrayList(STATE_SELECTED);
        }
        categories.getCategories().observe(this, names -> {
            adapter.submit(names, categories.quoteCounts());
            if (pendingSelection != null) {
                adapter.setSelected(pendingSelection);
                pendingSelection = null;
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(adapter.getSelected()));
    }

    private void renderSelectionMode(Set<String> selected) {
        boolean selecting = !selected.isEmpty();
        exitSelection.setEnabled(selecting);

        int barColor = Color.TRANSPARENT; // the AppBarLayout's own appBarColor shows through
        if (selecting) {
            TypedValue value = new TypedValue();
            getTheme().resolveAttribute(R.attr.selectionBarColor, value, true);
            barColor = value.data;
        }
        binding.toolbar.setBackgroundColor(barColor);
        binding.statusBarScrim.setBackgroundColor(barColor);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(selecting ? selected.size() + " selected" : "Categories");
            actionBar.setHomeAsUpIndicator(selecting ? R.drawable.ic_close : 0);
            actionBar.setHomeActionContentDescription(selecting ? "Clear selection" : null);
        }
        if (selecting) binding.fabAddCategory.hide();
        else binding.fabAddCategory.show();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!adapter.getSelected().isEmpty()) {
            getMenuInflater().inflate(R.menu.menu_category_selection, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem rename = menu.findItem(R.id.action_rename_category);
        if (rename != null) rename.setVisible(adapter.getSelected().size() == 1);
        return super.onPrepareOptionsMenu(menu);
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
                    categories.add(categoryName);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        dialog.show();

        input.requestFocus();
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
                    categories.rename(oldName, newName);
                    adapter.clearSelection();
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

    private void showDeleteCategoriesDialog(Set<String> names) {
        Map<String, Integer> counts = categories.quoteCounts();
        int quoteCount = 0;
        for (String name : names) {
            Integer count = counts.get(name);
            if (count != null) quoteCount += count;
        }
        boolean single = names.size() == 1;
        String question = single
                ? "Delete category \"" + names.iterator().next() + "\"?"
                : "Delete " + names.size() + " categories?";

        new AlertDialog.Builder(this)
                .setTitle(single ? "Delete Category" : "Delete Categories")
                .setMessage(question + "\n\nThis will remove " + (single ? "the category" : "them")
                        + " from " + quoteCount + " quote(s).")
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (String name : names) categories.delete(name);
                    adapter.clearSelection();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Set<String> selected = adapter.getSelected();
        if (id == android.R.id.home) {
            if (selected.isEmpty()) finish();
            else adapter.clearSelection();
            return true;
        } else if (id == R.id.action_rename_category && selected.size() == 1) {
            showRenameCategoryDialog(selected.iterator().next());
            return true;
        } else if (id == R.id.action_delete_categories && !selected.isEmpty()) {
            showDeleteCategoriesDialog(selected);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openSearchForCategory(String categoryName) {
        Intent intent = QuoteQuery.forField(QuoteQuery.Field.CATEGORY, categoryName).toIntent(this);
        startActivity(intent);
    }
}