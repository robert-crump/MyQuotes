package com.example.myquotes;

import java.util.List;

// Persistence seam under Categories: the user-managed list of category names.
public interface CategoryStore {
    List<String> load();

    void save(List<String> names);
}
