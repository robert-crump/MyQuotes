package com.example.myquotes;

import java.util.Objects;

public class Quote {
    private Integer id;
    private String author;
    private String quoteText;
    private String source;
    private String category;

    private int rating = 0;
    private boolean isFavorite = false;
    private long favoritedAt = 0L;    // Timestamp when marked as favorite
    private long lastShown = 0L;      // Timestamp of last display
    private int timesShown = 0;       // Number of times displayed

    // Konstruktoren
    public Quote() {
        // Default constructor
    }

    public Quote(Integer id, String author, String quoteText, String source) {
        this.id = id;
        this.author = author;
        this.quoteText = quoteText;
        this.source = source;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getAuthor() { return author != null ? author : ""; }
    public void setAuthor(String author) { this.author = author; }

    public String getQuoteText() { return quoteText != null ? quoteText : ""; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }

    public String getSource() { return source != null ? source : ""; }
    public void setSource(String source) { this.source = source; }

    public String getCategory() { return category != null ? category : ""; }
    public void setCategory(String category) { this.category = category; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public long getFavoritedAt() { return favoritedAt; }
    public void setFavoritedAt(long favoritedAt) { this.favoritedAt = favoritedAt; }

    public long getLastShown() { return lastShown; }
    public void setLastShown(long lastShown) { this.lastShown = lastShown; }

    public int getTimesShown() { return timesShown; }
    public void setTimesShown(int timesShown) { this.timesShown = timesShown; }

    public void incrementRating() {
        this.rating++;
    }

    public void decrementRating() {
        this.rating--;
    }

    public void toggleFavorite() {
        this.isFavorite = !this.isFavorite;
        if (this.isFavorite) {
            this.favoritedAt = System.currentTimeMillis();
        } else {
            this.favoritedAt = 0L;
        }
    }

    public void recordView() {
        this.timesShown++;
        this.lastShown = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Quote otherQuote = (Quote) obj;
        return Objects.equals(id, otherQuote.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Quote{" +
                "id=" + id +
                ", author='" + author + '\'' +
                ", rating=" + rating +
                ", favorite=" + isFavorite +
                '}';
    }
}