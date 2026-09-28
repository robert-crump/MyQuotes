package com.example.myquotes;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Category rows with long-press selection: a long-press selects a row and starts selection
 * mode, taps then toggle rows, and deselecting the last one ends it. Outside selection mode a
 * tap opens the category.
 */
public class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder> {
    private final List<String> categories = new ArrayList<>();
    private Map<String, Integer> counts = Collections.emptyMap();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Listener listener;

    public interface Listener {
        void onCategoryClick(String categoryName);
        void onSelectionChanged(Set<String> selected);
    }

    public CategoriesAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Replaces the rows; selected names that no longer exist (renamed, deleted) are dropped. */
    public void submit(List<String> newCategories, Map<String, Integer> newCounts) {
        categories.clear();
        categories.addAll(newCategories);
        counts = newCounts;
        boolean selectionChanged = selected.retainAll(newCategories);
        notifyDataSetChanged();
        if (selectionChanged) listener.onSelectionChanged(getSelected());
    }

    public Set<String> getSelected() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    public void setSelected(Collection<String> names) {
        selected.clear();
        for (String name : names) {
            if (categories.contains(name)) selected.add(name);
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(getSelected());
    }

    public void clearSelection() {
        setSelected(Collections.emptyList());
    }

    private void toggle(int position) {
        String name = categories.get(position);
        if (!selected.remove(name)) selected.add(name);
        notifyItemChanged(position);
        listener.onSelectionChanged(getSelected());
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        CategoryViewHolder holder = new CategoryViewHolder(view);
        view.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            if (selected.isEmpty()) listener.onCategoryClick(categories.get(position));
            else toggle(position);
        });
        view.setOnLongClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return false;
            toggle(position);
            return true;
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        String category = categories.get(position);
        Integer count = counts.get(category);
        holder.bind(category, count != null ? count : 0, selected.contains(category));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static String countLabel(int count) {
        if (count == 0) return "No quotes yet";
        return count == 1 ? "1 quote" : count + " quotes";
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView initial;
        private final ImageView check;
        private final TextView name;
        private final TextView count;
        private final int selectedColor;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            initial = itemView.findViewById(R.id.category_initial);
            check = itemView.findViewById(R.id.category_check);
            name = itemView.findViewById(R.id.category_name);
            count = itemView.findViewById(R.id.category_count);
            TypedValue value = new TypedValue();
            itemView.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorSecondaryContainer, value, true);
            selectedColor = value.data;
        }

        void bind(String category, int quoteCount, boolean isSelected) {
            name.setText(category);
            count.setText(countLabel(quoteCount));
            initial.setText(category.substring(0, category.offsetByCodePoints(0, 1)).toUpperCase());
            initial.setVisibility(isSelected ? View.INVISIBLE : View.VISIBLE);
            check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            itemView.setSelected(isSelected);
            itemView.setBackgroundColor(isSelected ? selectedColor : Color.TRANSPARENT);
        }
    }
}
