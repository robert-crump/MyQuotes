package com.example.myquotes;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
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

import java.util.ArrayList;

public class CategoriesActivity extends AppCompatActivity {
    private ActivityCategoriesBinding binding;
    private Categories categories;
    private CategoriesAdapter adapter;

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

        categories.getCategories().observe(this, adapter::updateCategories);
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

    private void showDeleteCategoryDialog(String categoryName) {
        int count = categories.countQuotes(categoryName);

        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Delete category \"" + categoryName + "\"?\n\n" +
                        "This will remove the category from " + count + " quote(s).")
                .setPositiveButton("Delete", (dialog, which) -> categories.delete(categoryName))
                .setNegativeButton("Cancel", null)
                .show();
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