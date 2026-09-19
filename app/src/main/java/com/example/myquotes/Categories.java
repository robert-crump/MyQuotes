package com.example.myquotes;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

// Owns the Category set: the user-managed names unioned with every category in use
// on a quote, sorted. Rename and delete cascade to the Quote Collection in one batch.
public class Categories {
    private final CategoryStore store;
    private final QuoteCollection collection;
    private final MutableLiveData<List<String>> saved = new MutableLiveData<>();
    private final MediatorLiveData<List<String>> set = new MediatorLiveData<>();

    public Categories(CategoryStore store, QuoteCollection collection) {
        this.store = store;
        this.collection = collection;
        saved.setValue(normalize(store.load()));
        set.addSource(saved, names -> set.setValue(all()));
        set.addSource(collection.getQuoteList(), quotes -> set.setValue(all()));
    }

    // Live category set; re-emits when the saved names or any quote changes.
    public LiveData<List<String>> getCategories() {
        return set;
    }

    public List<String> all() {
        TreeSet<String> names = new TreeSet<>(savedNames());
        for (Quote quote : collection.getCurrentList()) {
            String category = quote.getCategory().trim();
            if (!category.isEmpty()) names.add(category);
        }
        return new ArrayList<>(names);
    }

    public int countQuotes(String name) {
        int count = 0;
        for (Quote quote : collection.getCurrentList()) {
            if (name.equals(quote.getCategory())) count++;
        }
        return count;
    }

    public void add(String name) {
        name = name.trim();
        if (name.isEmpty()) return;
        List<String> names = savedNames();
        if (names.contains(name)) return;
        names.add(name);
        persist(names);
    }

    public void rename(String oldName, String newName) {
        newName = newName.trim();
        if (newName.isEmpty() || newName.equals(oldName)) return;
        collection.replaceCategory(oldName, newName);
        List<String> names = savedNames();
        names.remove(oldName);
        if (!names.contains(newName)) names.add(newName);
        persist(names);
    }

    public void delete(String name) {
        collection.replaceCategory(name, "");
        List<String> names = savedNames();
        if (names.remove(name)) persist(names);
        else saved.setValue(names);
    }

    private List<String> savedNames() {
        List<String> names = saved.getValue();
        return names != null ? new ArrayList<>(names) : new ArrayList<>();
    }

    private void persist(List<String> names) {
        Collections.sort(names);
        store.save(names);
        saved.setValue(names);
    }

    private static List<String> normalize(List<String> names) {
        TreeSet<String> unique = new TreeSet<>();
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) unique.add(name.trim());
        }
        return new ArrayList<>(unique);
    }
}
