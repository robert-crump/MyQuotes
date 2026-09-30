package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 * The tags on the Hashtags screen ({@code #Tag} over {@code Nx}) with long-press selection: a
 * long-press selects a tag and starts selection mode, taps then toggle tags, and deselecting the
 * last one ends it. Outside selection mode a tap opens the tag.
 */
public class HashtagsAdapter extends RecyclerView.Adapter<HashtagsAdapter.HashtagViewHolder> {
    private final List<String> hashtags = new ArrayList<>();
    private Map<String, Integer> counts = Collections.emptyMap();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Listener listener;

    public interface Listener {
        void onHashtagClick(String tag);
        void onSelectionChanged(Set<String> selected);
    }

    public HashtagsAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Replaces the tags; selected ones that no longer exist (renamed, deleted) are dropped. */
    @SuppressWarnings("NotifyDataSetChanged")
    public void submit(List<String> newHashtags, Map<String, Integer> newCounts) {
        hashtags.clear();
        hashtags.addAll(newHashtags);
        counts = newCounts;
        boolean selectionChanged = selected.retainAll(newHashtags);
        notifyDataSetChanged();
        if (selectionChanged) listener.onSelectionChanged(getSelected());
    }

    public Set<String> getSelected() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setSelected(Collection<String> tags) {
        selected.clear();
        for (String tag : tags) {
            if (hashtags.contains(tag)) selected.add(tag);
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(getSelected());
    }

    public void clearSelection() {
        setSelected(Collections.emptyList());
    }

    private void toggle(int position) {
        String tag = hashtags.get(position);
        if (!selected.remove(tag)) selected.add(tag);
        notifyItemChanged(position);
        listener.onSelectionChanged(getSelected());
    }

    @NonNull
    @Override
    public HashtagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hashtag, parent, false);
        HashtagViewHolder holder = new HashtagViewHolder(view);
        view.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            if (selected.isEmpty()) listener.onHashtagClick(hashtags.get(position));
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
    public void onBindViewHolder(@NonNull HashtagViewHolder holder, int position) {
        String tag = hashtags.get(position);
        Integer count = counts.get(tag);
        holder.bind(tag, count != null ? count : 0, selected.contains(tag));
    }

    @Override
    public int getItemCount() {
        return hashtags.size();
    }

    static class HashtagViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView count;

        HashtagViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.hashtag_name);
            count = itemView.findViewById(R.id.hashtag_count);
        }

        void bind(String tag, int quoteCount, boolean isSelected) {
            name.setText(Hashtag.display(tag));
            count.setText(quoteCount + "x");
            itemView.setSelected(isSelected);
            itemView.setContentDescription(Hashtag.display(tag) + ", "
                    + (quoteCount == 1 ? "1 quote" : quoteCount + " quotes")
                    + (isSelected ? ", selected" : ""));
        }
    }
}
