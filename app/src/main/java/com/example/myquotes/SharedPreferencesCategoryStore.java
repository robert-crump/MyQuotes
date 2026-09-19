package com.example.myquotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class SharedPreferencesCategoryStore implements CategoryStore {
    private static final String TAG = "CategoryStore";
    // Names kept from the old CategoriesActivity so existing users' lists survive.
    private static final String PREFS_NAME = "CategoryPrefs";
    private static final String KEY_CATEGORIES = "saved_categories";

    private final SharedPreferences prefs;

    public SharedPreferencesCategoryStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public List<String> load() {
        List<String> names = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_CATEGORIES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                names.add(array.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading saved categories", e);
        }
        return names;
    }

    @Override
    public void save(List<String> names) {
        JSONArray array = new JSONArray();
        for (String name : names) {
            array.put(name);
        }
        prefs.edit().putString(KEY_CATEGORIES, array.toString()).apply();
    }
}
