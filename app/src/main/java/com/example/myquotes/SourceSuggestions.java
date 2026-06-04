package com.example.myquotes;

import java.util.List;

public class SourceSuggestions {
    public final List<String> authorSources;
    public final List<String> otherSources;

    public SourceSuggestions(List<String> authorSources, List<String> otherSources) {
        this.authorSources = authorSources;
        this.otherSources = otherSources;
    }

    public boolean hasSplit() {
        return !authorSources.isEmpty();
    }
}
