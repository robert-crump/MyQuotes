package com.example.myquotes;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;
import org.tartarus.snowball.ext.germanStemmer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The token pipeline of the Hashtag suggestions (#70): the quote text lowercased and split on
 * non-letters; words shorter than {@link #MIN_WORD_LENGTH} and English/German stopwords dropped; the
 * rest stemmed with the Snowball stemmer of the quote's language (German when it has more German than
 * English stopwords, else English). Plus one {@code author:<name>} and one {@code source:<name>}
 * token (lowercased, whitespace collapsed, none when empty) and one {@code tag:<key>} token per tag.
 * A quote counts each token once.
 */
final class QuoteTokens {
    static final int MIN_WORD_LENGTH = 3;
    static final String AUTHOR_PREFIX = "author:";
    static final String SOURCE_PREFIX = "source:";
    static final String TAG_PREFIX = "tag:";

    private QuoteTokens() {
    }

    private static final Set<String> ENGLISH_STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her",
            "was", "one", "our", "out", "has", "have", "him", "his", "how", "man", "its", "may",
            "who", "did", "get", "let", "she", "too", "use", "they", "them", "their", "theirs",
            "there", "then", "than", "that", "this", "these", "those", "with", "without", "from",
            "into", "onto", "upon", "about", "above", "below", "after", "before", "again",
            "against", "between", "through", "during", "under", "over", "off", "own", "same",
            "such", "only", "very", "just", "also", "more", "most", "some", "much", "many",
            "each", "every", "both", "few", "other", "what", "which", "whom", "whose", "when",
            "where", "why", "while", "will", "would", "shall", "should", "could", "must", "might",
            "been", "being", "were", "does", "doing", "done", "your", "yours", "yourself",
            "yourselves", "ours", "ourselves", "hers", "herself", "himself", "itself", "themselves",
            "myself", "because", "until", "here", "nor", "yet", "ever", "never", "always",
            "don", "doesn", "didn", "isn", "aren", "wasn", "weren", "hasn", "haven", "hadn",
            "won", "wouldn", "couldn", "shouldn", "cannot", "now", "well", "even",
            "still", "like", "make", "made", "thus", "thee", "thou", "thy", "thine",
            "unto", "whether", "within", "whatever", "whoever", "whenever", "wherever"));
    private static final Set<String> GERMAN_STOPWORDS = new HashSet<>(Arrays.asList(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "eines", "einem",
            "einen", "und", "oder", "aber", "doch", "denn", "sondern", "nicht", "kein", "keine",
            "keiner", "keines", "keinem", "keinen", "ich", "du", "sie", "wir", "ihr", "mich",
            "dich", "sich", "uns", "euch", "mir", "dir", "ihm", "ihn", "ihnen", "mein", "meine",
            "meiner", "meines", "meinem", "meinen", "dein", "deine", "deiner", "deines", "deinem",
            "deinen", "sein", "seine", "seiner", "seines", "seinem", "seinen", "ihre", "ihrer",
            "ihres", "ihrem", "ihren", "unser", "unsere", "euer", "eure", "ist", "sind", "war",
            "waren", "bin", "bist", "seid", "wird", "werden", "wurde", "wurden", "worden", "hat",
            "habe", "hast", "haben", "hatte", "hatten", "kann", "kannst", "können", "konnte",
            "muss", "musst", "müssen", "musste", "soll", "sollte", "sollen", "will", "wollen",
            "wollte", "darf", "dürfen", "mag", "möchte", "auf", "aus", "bei", "mit", "nach", "von",
            "vor", "zum", "zur", "über", "unter", "durch", "für", "gegen", "ohne", "um", "bis",
            "seit", "als", "wie", "wenn", "weil", "dass", "daß", "damit", "dann", "noch", "schon",
            "nur", "auch", "sehr", "mehr", "viel", "viele", "alle", "alles", "allem", "allen",
            "aller", "was", "wer", "wem", "wen", "wo", "warum", "hier", "dort", "da",
            "so", "nun", "immer", "nie", "niemals", "jetzt", "diese", "dieser", "dieses", "diesem",
            "diesen", "jede", "jeder", "jedes", "jedem", "jeden", "selbst", "etwas", "nichts",
            "zwischen", "während", "wieder", "ganz", "hin", "her", "ins", "im", "am",
            "vom", "beim", "zu", "an", "in", "es", "er", "ja", "nein", "sei", "wäre", "hätte",
            "würde", "einmal", "gibt", "gar", "eben", "halt", "mal", "wohl", "dies", "jener",
            "jene", "welche", "welcher", "welches", "solche", "solcher", "andere", "anderen"));
    private static final Set<String> STOPWORDS = new HashSet<>(ENGLISH_STOPWORDS);

    static {
        STOPWORDS.addAll(GERMAN_STOPWORDS);
    }

    /** The quote's tokens: its words, the author and source tokens, then one tag token per tag (null is none). */
    static Set<String> of(String text, String author, String source, Collection<String> tags) {
        Set<String> tokens = new LinkedHashSet<>();
        forEachWord(text, (stem, word) -> tokens.add(stem));
        String authorToken = authorToken(author);
        if (authorToken != null) tokens.add(authorToken);
        String sourceToken = sourceToken(source);
        if (sourceToken != null) tokens.add(sourceToken);
        if (tags != null) {
            for (String tag : tags) {
                String normalized = Hashtag.normalize(tag);
                if (!normalized.isEmpty()) tokens.add(TAG_PREFIX + Hashtag.key(normalized));
            }
        }
        return tokens;
    }

    /** The English and German stems of a tag's name ({@code key}, lowercase); none when it is too short. */
    static Set<String> nameStems(String key) {
        Set<String> stems = new HashSet<>();
        if (key.codePointCount(0, key.length()) < MIN_WORD_LENGTH) return stems;
        for (SnowballStemmer stemmer : new SnowballStemmer[]{new englishStemmer(), new germanStemmer()}) {
            stemmer.setCurrent(key);
            stemmer.stem();
            stems.add(stemmer.getCurrent());
        }
        return stems;
    }

    /**
     * Each content word of {@code text} (lowercased; stopwords and words under
     * {@link #MIN_WORD_LENGTH} letters dropped) with its stem, from the stemmer of the text's
     * language ({@link #stemmerFor}).
     */
    static void forEachWord(String text, BiConsumer<String, String> action) {
        if (text == null) return;
        List<String> words = words(text);
        SnowballStemmer stemmer = stemmerFor(words);
        for (String w : words) {
            if (w.codePointCount(0, w.length()) >= MIN_WORD_LENGTH && !STOPWORDS.contains(w)) {
                stemmer.setCurrent(w);
                stemmer.stem();
                action.accept(stemmer.getCurrent(), w);
            }
        }
    }

    /** Every word of {@code text}: its runs of letters, lowercased. */
    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i <= lower.length(); ) {
            int cp = i < lower.length() ? lower.codePointAt(i) : ' ';
            if (Character.isLetter(cp)) {
                word.appendCodePoint(cp);
            } else if (word.length() > 0) {
                words.add(word.toString());
                word.setLength(0);
            }
            i += Character.charCount(cp);
        }
        return words;
    }

    /** German when {@code words} hold more German than English stopwords, else English. */
    private static SnowballStemmer stemmerFor(List<String> words) {
        int english = 0;
        int german = 0;
        for (String w : words) {
            if (ENGLISH_STOPWORDS.contains(w)) english++;
            if (GERMAN_STOPWORDS.contains(w)) german++;
        }
        return german > english ? new germanStemmer() : new englishStemmer();
    }

    /** {@code author:<name>}, lowercased with whitespace collapsed; null when there is no author. */
    static String authorToken(String author) {
        return nameToken(AUTHOR_PREFIX, author);
    }

    /** {@code source:<name>}, like {@link #authorToken}; null when there is no source. */
    static String sourceToken(String source) {
        return nameToken(SOURCE_PREFIX, source);
    }

    private static String nameToken(String prefix, String name) {
        if (name == null) return null;
        String key = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return key.isEmpty() ? null : prefix + key;
    }
}
