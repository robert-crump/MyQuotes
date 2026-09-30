package com.example.myquotes;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Owns the Hashtag set: the union of the tags on all quotes, sorted alphabetically. A tag
// exists only while in use; there is no stored list. Rename and delete go through one batched
// QuoteCollection.replaceTag each.
public class Hashtags {
    private final QuoteCollection collection;
    private final MediatorLiveData<List<String>> set = new MediatorLiveData<>();

    public Hashtags(QuoteCollection collection) {
        this.collection = collection;
        set.addSource(collection.getQuoteList(), quotes -> set.setValue(all()));
    }

    // Live Hashtag set; re-emits when any quote changes.
    public LiveData<List<String>> getHashtags() {
        return set;
    }

    public List<String> all() {
        return new ArrayList<>(counts(collection.getCurrentList()).keySet());
    }

    // Quotes per tag in one pass (a quote counts once per tag).
    public Map<String, Integer> quoteCounts() {
        return new HashMap<>(counts(collection.getCurrentList()));
    }

    // Quotes carrying at least one of `tags` (compared ignoring case).
    public int quotesWithAny(Collection<String> tags) {
        List<String> wanted = new ArrayList<>(tags);
        int count = 0;
        for (Quote quote : collection.getCurrentList()) {
            for (String tag : quote.getTags()) {
                if (Hashtag.containsIgnoreCase(wanted, tag)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    // The spelling `tag` already has in the collection, or `tag` itself.
    public String canonical(String tag) {
        return Hashtag.canonical(tag, all());
    }

    // The existing tag (other than `oldTag`) that `newTag` would merge into, or null.
    public String mergeTarget(String oldTag, String newTag) {
        for (String tag : all()) {
            if (tag.equalsIgnoreCase(newTag) && !tag.equalsIgnoreCase(oldTag)) return tag;
        }
        return null;
    }

    // Renames `oldTag` on every quote. An existing tag's spelling wins (merge); renaming to a
    // different case of the same tag respells it.
    public void rename(String oldTag, String newTag) {
        newTag = Hashtag.normalize(newTag);
        if (newTag.isEmpty() || newTag.equals(oldTag)) return;
        String target = mergeTarget(oldTag, newTag);
        collection.replaceTag(oldTag, target != null ? target : newTag);
    }

    // Removes `tag` from every quote; the quotes stay.
    public void delete(String tag) {
        collection.replaceTag(tag, null);
    }

    // Alphabetical tag → quote count. Quote tags are already deduplicated ignoring case and
    // the collection keeps one spelling per tag.
    static Map<String, Integer> counts(List<Quote> quotes) {
        Map<String, Integer> counts = new TreeMap<>(Hashtag.ORDER);
        for (Quote quote : quotes) {
            for (String tag : quote.getTags()) counts.merge(tag, 1, Integer::sum);
        }
        return counts;
    }
}
