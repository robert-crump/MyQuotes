package com.example.myquotes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SuggestionAdapter extends BaseAdapter {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_DIVIDER = 1;
    private static final Object DIVIDER = new Object();

    private final Context context;
    private final List<Object> items;

    private SuggestionAdapter(Context context, List<Object> items) {
        this.context = context;
        this.items = items;
    }

    public static SuggestionAdapter forAuthors(Context context, List<String> authors) {
        return new SuggestionAdapter(context, new ArrayList<>(authors));
    }

    public static SuggestionAdapter forSources(Context context, SourceSuggestions suggestions) {
        List<Object> items = new ArrayList<>();
        if (suggestions.hasSplit()) {
            items.addAll(suggestions.authorSources);
            items.add(DIVIDER);
            items.addAll(suggestions.otherSources);
        } else {
            items.addAll(suggestions.otherSources);
        }
        return new SuggestionAdapter(context, items);
    }

    /** Returns the suggestion string at position, or null if it is the divider row. */
    public String getStringAt(int position) {
        Object item = items.get(position);
        return item == DIVIDER ? null : (String) item;
    }

    /** Count of actual suggestion items, excluding the divider. */
    public int getSuggestionCount() {
        int count = 0;
        for (Object item : items) {
            if (item != DIVIDER) count++;
        }
        return count;
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int pos) { return items.get(pos); }
    @Override public long getItemId(int pos) { return pos; }
    @Override public int getViewTypeCount() { return 2; }

    @Override
    public int getItemViewType(int pos) {
        return items.get(pos) == DIVIDER ? TYPE_DIVIDER : TYPE_ITEM;
    }

    @Override
    public boolean isEnabled(int pos) {
        return items.get(pos) != DIVIDER;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        if (getItemViewType(pos) == TYPE_DIVIDER) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                        .inflate(R.layout.item_suggestion_divider, parent, false);
            }
            return convertView;
        }
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_suggestion, parent, false);
        }
        ((TextView) convertView.findViewById(R.id.suggestion_text))
                .setText((String) items.get(pos));
        return convertView;
    }
}
