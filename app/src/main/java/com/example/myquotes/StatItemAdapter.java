package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** A ranked list that shows only its first {@link #COLLAPSED_ROWS} items until expanded. */
public class StatItemAdapter extends RecyclerView.Adapter<StatItemAdapter.ViewHolder> {
    static final int COLLAPSED_ROWS = 10;

    private final List<StatItem> items = new ArrayList<>();
    private final QuoteQuery.Field field;
    private boolean expanded;

    public StatItemAdapter(QuoteQuery.Field field) {
        this.field = field;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setItems(List<StatItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        if (overflowCount() == 0) return;
        if (expanded) {
            notifyItemRangeInserted(COLLAPSED_ROWS, overflowCount());
        } else {
            notifyItemRangeRemoved(COLLAPSED_ROWS, overflowCount());
        }
    }

    /** The items beyond the collapsed rows, shown only while expanded. */
    public int overflowCount() {
        return Math.max(0, items.size() - COLLAPSED_ROWS);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_stat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StatItem item = items.get(position);
        holder.bind(item, position + 1);
    }

    @Override
    public int getItemCount() {
        return expanded ? items.size() : Math.min(items.size(), COLLAPSED_ROWS);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textRank;
        private final TextView textName;
        private final TextView textCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textRank = itemView.findViewById(R.id.text_rank);
            textName = itemView.findViewById(R.id.text_name);
            textCount = itemView.findViewById(R.id.text_count);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    StatItem item = items.get(position);

                    QuoteQuery query = field == QuoteQuery.Field.HASHTAGS
                            ? QuoteQuery.forTag(item.name)
                            : QuoteQuery.forField(field, item.name);
                    android.content.Intent intent = query.toIntent(itemView.getContext());
                    itemView.getContext().startActivity(intent);
                }
            });
        }

        public void bind(StatItem item, int rank) {
            textRank.setText(String.valueOf(rank));
            textName.setText(field == QuoteQuery.Field.HASHTAGS ? Hashtag.display(item.name) : item.name);
            textCount.setText(String.valueOf(item.count));
        }
    }
}
