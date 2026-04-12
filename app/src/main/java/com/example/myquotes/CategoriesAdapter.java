package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder> {
    private List<String> categories;
    private CategoryActionListener listener;

    public interface CategoryActionListener {
        void onRenameCategory(String categoryName);
        void onDeleteCategory(String categoryName);
        void onCategoryClick(String categoryName);
    }

    public CategoriesAdapter(List<String> categories, CategoryActionListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    public void updateCategories(List<String> newCategories) {
        this.categories.clear();
        this.categories.addAll(newCategories);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        String category = categories.get(position);
        holder.bind(category, listener);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private TextView categoryName;
        private ImageButton btnRename;
        private ImageButton btnDelete;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.category_name);
            btnRename = itemView.findViewById(R.id.btn_rename_category);
            btnDelete = itemView.findViewById(R.id.btn_delete_category);
        }

        public void bind(String category, CategoryActionListener listener) {
            categoryName.setText(category);

            btnRename.setOnClickListener(v -> listener.onRenameCategory(category));
            btnDelete.setOnClickListener(v -> listener.onDeleteCategory(category));
            itemView.setOnClickListener(v -> listener.onCategoryClick(category));
        }
    }
}