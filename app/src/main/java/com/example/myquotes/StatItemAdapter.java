package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StatItemAdapter extends RecyclerView.Adapter<StatItemAdapter.ViewHolder> {
    private final List<StatisticsActivity.StatItem> items;
    private final String label;

    public StatItemAdapter(List<StatisticsActivity.StatItem> items, String label) {
        this.items = items;
        this.label = label;
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
        StatisticsActivity.StatItem item = items.get(position);
        holder.bind(item, position + 1);
    }

    @Override
    public int getItemCount() {
        return items.size();
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
                    StatisticsActivity.StatItem item = items.get(position);

                    android.content.Intent intent = new android.content.Intent(itemView.getContext(), SearchActivity.class);
                    intent.putExtra(SearchActivity.EXTRA_SEARCH_QUERY, item.name);
                    intent.putExtra(SearchActivity.EXTRA_FILTER_TYPE, label.toLowerCase());
                    itemView.getContext().startActivity(intent);
                }
            });
        }

        public void bind(StatisticsActivity.StatItem item, int rank) {
            textRank.setText(String.valueOf(rank));
            textName.setText(item.name);
            textCount.setText(String.valueOf(item.count));
        }
    }
}