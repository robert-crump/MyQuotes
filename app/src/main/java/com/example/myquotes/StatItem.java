package com.example.myquotes;

/** One ranked row in a statistics list: a name and how many quotes carry it. */
public final class StatItem {
    public final String name;
    public final int count;

    public StatItem(String name, int count) {
        this.name = name;
        this.count = count;
    }
}
