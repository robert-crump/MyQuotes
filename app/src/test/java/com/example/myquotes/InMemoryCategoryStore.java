package com.example.myquotes;

import java.util.ArrayList;
import java.util.List;

class InMemoryCategoryStore implements CategoryStore {
    List<String> saved = new ArrayList<>();
    int saveCount = 0;

    InMemoryCategoryStore(String... initial) {
        for (String name : initial) saved.add(name);
    }

    @Override
    public List<String> load() {
        return new ArrayList<>(saved);
    }

    @Override
    public void save(List<String> names) {
        saved = new ArrayList<>(names);
        saveCount++;
    }
}
