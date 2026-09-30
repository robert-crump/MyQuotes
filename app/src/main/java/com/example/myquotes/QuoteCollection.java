package com.example.myquotes;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuoteCollection {
    private static final String TAG = "QuoteCollection";

    private final MutableLiveData<List<Quote>> liveQuoteList = new MutableLiveData<>(new ArrayList<>());
    private final QuoteStore store;

    public QuoteCollection(QuoteStore store) {
        this.store = store;
    }

    // ========== BOOTSTRAP ==========

    // Loads the stored quotes into the collection. Called once from MyApplication.onCreate.
    // Trims fields and unifies tag spellings; saves once if that changed anything or the store
    // held an older format (e.g. categories from before #49).
    public void loadFromStore() {
        List<Quote> stored = store.load();
        if (stored.isEmpty()) return;
        boolean changed = trim(stored);
        changed |= unifyTagSpellings(stored);
        liveQuoteList.setValue(stored);
        if (changed || store.isOldFormat()) {
            save(stored);
            Log.d(TAG, "Saved the loaded quotes in the current format");
        }
    }

    // ========== OBSERVATION ==========

    public LiveData<List<Quote>> getQuoteList() {
        return liveQuoteList;
    }

    // ========== CRUD ==========

    public void add(Quote quote) {
        if (quote == null) {
            Log.w(TAG, "Attempted to add null quote");
            return;
        }
        List<Quote> current = getCurrentList();
        int maxId = current.stream().mapToInt(Quote::getId).max().orElse(0);
        quote.setId(maxId + 1);
        quote.setTags(canonicalTags(quote.getTags(), current, null));
        quote.setAddedAt(System.currentTimeMillis());
        List<Quote> updated = new ArrayList<>(current);
        updated.add(quote);
        liveQuoteList.setValue(updated);
        save(updated);
        Log.d(TAG, "Added quote with ID: " + quote.getId());
    }

    public void update(Quote updatedQuote) {
        if (updatedQuote == null || updatedQuote.getId() == null) {
            Log.w(TAG, "Attempted to update invalid quote");
            return;
        }
        List<Quote> quotes = liveQuoteList.getValue();
        if (quotes == null) return;
        List<Quote> updated = new ArrayList<>(quotes);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).getId().equals(updatedQuote.getId())) {
                updated.set(i, updatedQuote);
                liveQuoteList.setValue(updated);
                save(updated);
                Log.d(TAG, "Updated quote with ID: " + updatedQuote.getId());
                return;
            }
        }
        Log.w(TAG, "Quote with ID " + updatedQuote.getId() + " not found");
    }

    // Replaces the editable fields, keeping favorite, view state and addedAt.
    public void edit(int id, String author, String text, String source, Collection<String> tags) {
        Quote stored = findById(id);
        if (stored == null) {
            Log.w(TAG, "Quote with ID " + id + " not found");
            return;
        }
        Quote edited = new Quote(id, author, text, source);
        edited.setTags(canonicalTags(Hashtag.normalizeAll(tags), getCurrentList(), id));
        edited.setFavorite(stored.isFavorite());
        edited.setFavoritedAt(stored.getFavoritedAt());
        edited.setTimesShown(stored.getTimesShown());
        edited.setLastShown(stored.getLastShown());
        edited.setAddedAt(stored.getAddedAt());
        update(edited);
    }

    // Renames tag `oldTag` (compared ignoring case) on every quote, or removes it when `newTag`
    // is null or empty. Renaming to a tag that already exists merges: a quote holding both ends
    // up with it once. One emission and one save regardless of how many quotes match. Returns
    // the match count.
    public int replaceTag(String oldTag, String newTag) {
        List<Quote> updated = getCurrentList();
        String replacement = newTag == null ? "" : Hashtag.normalize(newTag);
        int count = 0;
        for (Quote quote : updated) {
            List<String> tags = new ArrayList<>(quote.getTags());
            int index = Hashtag.indexOf(tags, oldTag);
            if (index < 0) continue;
            tags.remove(index);
            if (!replacement.isEmpty()) tags.add(replacement);
            quote.setTags(tags);
            count++;
        }
        if (count > 0) {
            liveQuoteList.setValue(updated);
            save(updated);
            Log.d(TAG, "Replaced tag on " + count + " quotes");
        }
        return count;
    }

    public void deleteById(int id) {
        List<Quote> updated = getCurrentList();
        if (updated.removeIf(q -> q.getId() == id)) {
            liveQuoteList.setValue(updated);
            save(updated);
            Log.d(TAG, "Deleted quote with ID: " + id);
        } else {
            Log.w(TAG, "Quote with ID " + id + " not found");
        }
    }

    public void setList(List<Quote> quotes) {
        if (quotes == null) quotes = new ArrayList<>();
        unifyTagSpellings(quotes);
        liveQuoteList.setValue(quotes);
        save(quotes);
        Log.d(TAG, "Set quote list: " + quotes.size() + " quotes");
    }

    // ========== VIEW RECORDING ==========

    // Increments view count and persists without firing LiveData observers —
    // avoids a full deck diff on every swipe.
    public void recordView(int quoteId) {
        List<Quote> quotes = liveQuoteList.getValue();
        if (quotes == null) return;
        for (Quote q : quotes) {
            if (q.getId() == quoteId) {
                q.recordView();
                save(quotes);
                return;
            }
        }
    }

    // ========== QUERIES ==========

    public Quote findById(int id) {
        return getCurrentList().stream()
                .filter(q -> q.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public List<Quote> getFavorites() {
        List<Quote> favorites = getCurrentList().stream()
                .filter(Quote::isFavorite)
                .collect(Collectors.toList());
        favorites.sort((q1, q2) -> Long.compare(q2.getFavoritedAt(), q1.getFavoritedAt()));
        return favorites;
    }

    // The only favorite path. Returns the new favorite state (false if the quote is unknown).
    public boolean toggleFavorite(int quoteId) {
        Quote quote = findById(quoteId);
        if (quote == null) {
            Log.w(TAG, "Cannot toggle favorite - quote not found: " + quoteId);
            return false;
        }
        quote.toggleFavorite();
        update(quote);
        return quote.isFavorite();
    }

    // Trims author and source; returns whether anything changed.
    private static boolean trim(List<Quote> quotes) {
        boolean changed = false;
        for (Quote quote : quotes) {
            String author = quote.getAuthor();
            String source = quote.getSource();
            if (!author.equals(author.trim())) {
                quote.setAuthor(author.trim());
                changed = true;
            }
            if (!source.equals(source.trim())) {
                quote.setSource(source.trim());
                changed = true;
            }
        }
        return changed;
    }

    // Gives every tag the spelling it first has in the list, so "love" and "Love" never
    // coexist (old categories, imported files). Returns whether anything changed.
    private static boolean unifyTagSpellings(List<Quote> quotes) {
        Map<String, String> spelling = new HashMap<>();
        boolean changed = false;
        for (Quote quote : quotes) {
            List<String> tags = new ArrayList<>();
            boolean respelled = false;
            for (String tag : quote.getTags()) {
                String canonical = spelling.computeIfAbsent(Hashtag.key(tag), k -> tag);
                tags.add(canonical);
                respelled |= !canonical.equals(tag);
            }
            if (respelled) {
                quote.setTags(tags);
                changed = true;
            }
        }
        return changed;
    }

    // `tags` with the spelling each already has on another quote in `quotes` (not `exceptId`).
    private static List<String> canonicalTags(List<String> tags, List<Quote> quotes, Integer exceptId) {
        Map<String, String> spelling = new HashMap<>();
        for (Quote quote : quotes) {
            if (exceptId != null && exceptId.equals(quote.getId())) continue;
            for (String tag : quote.getTags()) spelling.putIfAbsent(Hashtag.key(tag), tag);
        }
        List<String> result = new ArrayList<>();
        for (String tag : tags) result.add(spelling.getOrDefault(Hashtag.key(tag), tag));
        return result;
    }

    // ========== HELPERS ==========

    List<Quote> getCurrentList() {
        List<Quote> quotes = liveQuoteList.getValue();
        return quotes != null ? new ArrayList<>(quotes) : new ArrayList<>();
    }

    private void save(List<Quote> quotes) {
        store.save(quotes);
    }
}
