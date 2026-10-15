package com.example.myquotes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Suggests hashtags for a quote being added or edited (#50), learned from the user's own
 * collection. Pure and on-device: {@link #fit} counts, per tag, which tokens its quotes contain;
 * {@link #suggest} looks for reasons to suggest each tag for a new quote (#52).
 * <ul>
 *   <li><b>Tokens</b>: the quote text lowercased and split on non-letters; words shorter than
 *       {@link #MIN_WORD_LENGTH} and English/German stopwords dropped; the rest cut to their first
 *       {@link #STEM_LENGTH} letters as a cheap stem. Plus one {@code author:<name>} token. The
 *       source is not used. A quote counts each token once.</li>
 *   <li><b>Reasons</b>: a word of the quote is a reason for tag t when at least
 *       {@link #MIN_WORD_WITH_TAG} quotes contain it and have t, and that is at least
 *       {@link #MIN_SHARE_PERCENT}% of the quotes containing it. The author is a reason when at
 *       least {@link #MIN_AUTHOR_WITH_TAG} of their quotes have t, and that is at least
 *       {@link #MIN_SHARE_PERCENT}% of their quotes.</li>
 *   <li><b>Eligibility</b>: t is on at least {@link #MIN_QUOTES_PER_TAG} quotes and the quote
 *       has at least one reason for it.</li>
 * </ul>
 * At most {@link #MAX_SUGGESTIONS}, ranked by each tag's strongest reason (higher share, then
 * higher count), ties by tag name; tags the quote already has are left out (ignoring case).
 * <p>
 * The why (#51): {@link #explain} gives a suggestion's reasons with their counts ("6 of the 9
 * quotes with <i>gratitude</i> have the tag"), {@link #profile} every reason the model knows for
 * a tag. Stems are shown as a real word: the quote's own word in {@code explain}, otherwise the
 * collection's most frequent word with that stem.
 */
public final class HashtagSuggester {
    static final int MIN_QUOTES_PER_TAG = 5;
    static final int MIN_WORD_WITH_TAG = 4;
    static final int MIN_AUTHOR_WITH_TAG = 2;
    static final int MIN_SHARE_PERCENT = 50;
    static final int MAX_SUGGESTIONS = 3;
    static final int MIN_WORD_LENGTH = 3;
    static final int STEM_LENGTH = 6;
    static final String AUTHOR_PREFIX = "author:";

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            // English
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
            "unto", "whether", "within", "whatever", "whoever", "whenever", "wherever",
            // German
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

    // Quotes per token (document frequency).
    private final Map<String, Integer> tokenCounts = new HashMap<>();
    // Per eligible tag (lowercase key): quotes carrying it, and per token the quotes with both.
    private final Map<String, TagStats> tags = new HashMap<>();
    // Quotes per tag (lowercase key), eligible or not.
    private final Map<String, Integer> tagQuotes = new HashMap<>();
    // Per stem, how often each full word occurred; per author token, its first spelling.
    private final Map<String, Map<String, Integer>> surfaceWords = new HashMap<>();
    private final Map<String, String> authorNames = new HashMap<>();

    /**
     * One piece of evidence: {@code withTag} of the {@code total} quotes that contain the word
     * (or are by the author) carry the tag.
     */
    public static final class Evidence {
        public final String label;
        public final int withTag;
        public final int total;

        Evidence(String label, int withTag, int total) {
            this.label = label;
            this.withTag = withTag;
            this.total = total;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Evidence)) return false;
            Evidence other = (Evidence) o;
            return label.equals(other.label) && withTag == other.withTag && total == other.total;
        }

        @Override
        public int hashCode() {
            return (label.hashCode() * 31 + withTag) * 31 + total;
        }

        @Override
        public String toString() {
            return label + " " + withTag + "/" + total;
        }
    }

    /** Higher share first, then higher count; equal strength is 0. */
    private static int byStrength(Evidence a, Evidence b) {
        int byShare = Long.compare((long) b.withTag * a.total, (long) a.withTag * b.total);
        return byShare != 0 ? byShare : b.withTag - a.withTag;
    }

    /** Strongest first (higher share, then higher count), then by label. */
    static final Comparator<Evidence> STRONGEST_FIRST = (a, b) -> {
        int byStrength = byStrength(a, b);
        return byStrength != 0 ? byStrength : a.label.compareTo(b.label);
    };

    /** Why a tag is suggested for one quote: its reason words (strongest first) and author. */
    public static final class Explanation {
        public final String tag;
        public final List<Evidence> words;
        /** The author when they are a reason for the tag; else null. */
        public final Evidence author;

        Explanation(String tag, List<Evidence> words, Evidence author) {
            this.tag = tag;
            this.words = words;
            this.author = author;
        }
    }

    /** What the model learned about one tag (the Hashtags sheet). */
    public static final class TagProfile {
        public final String tag;
        public final int quotes;
        /** On at least {@link #MIN_QUOTES_PER_TAG} quotes, so it can be suggested at all. */
        public final boolean suggestable;
        /** Every word that is a reason for the tag, strongest first. */
        public final List<Evidence> words;
        /** Every author who is a reason for the tag, strongest first. */
        public final List<Evidence> authors;

        TagProfile(String tag, int quotes, boolean suggestable, List<Evidence> words, List<Evidence> authors) {
            this.tag = tag;
            this.quotes = quotes;
            this.suggestable = suggestable;
            this.words = words;
            this.authors = authors;
        }
    }

    private static final class TagStats {
        final String spelling;
        int quotes;
        final Map<String, Integer> tokens = new HashMap<>();

        TagStats(String spelling) {
            this.spelling = spelling;
        }
    }

    private HashtagSuggester() {
    }

    /** Learns from {@code quotes}. For Edit, pass the collection without the quote being edited. */
    public static HashtagSuggester fit(List<Quote> quotes) {
        HashtagSuggester model = new HashtagSuggester();
        Map<String, TagStats> all = new HashMap<>();
        List<Set<String>> quoteTokens = new ArrayList<>(quotes.size());
        for (Quote quote : quotes) {
            Set<String> tokens = tokens(quote.getQuoteText(), quote.getAuthor());
            quoteTokens.add(tokens);
            for (String token : tokens) model.tokenCounts.merge(token, 1, Integer::sum);
            forEachWord(quote.getQuoteText(), (stem, word) -> model.surfaceWords
                    .computeIfAbsent(stem, k -> new HashMap<>()).merge(word, 1, Integer::sum));
            String authorToken = authorToken(quote.getAuthor());
            if (authorToken != null) model.authorNames.putIfAbsent(authorToken, quote.getAuthor().trim());
            for (String tag : quote.getTags()) {
                all.computeIfAbsent(Hashtag.key(tag), k -> new TagStats(tag)).quotes++;
            }
        }
        for (Map.Entry<String, TagStats> entry : all.entrySet()) {
            model.tagQuotes.put(entry.getKey(), entry.getValue().quotes);
            if (entry.getValue().quotes >= MIN_QUOTES_PER_TAG) model.tags.put(entry.getKey(), entry.getValue());
        }
        for (int i = 0; i < quotes.size(); i++) {
            for (String tag : quotes.get(i).getTags()) {
                TagStats stats = model.tags.get(Hashtag.key(tag));
                if (stats == null) continue;
                for (String token : quoteTokens.get(i)) stats.tokens.merge(token, 1, Integer::sum);
            }
        }
        return model;
    }

    /** Up to {@link #MAX_SUGGESTIONS} tags for the quote, best first; never one of {@code existingTags}. */
    public List<String> suggest(String text, String author, String source, Collection<String> existingTags) {
        Set<String> tokens = tokens(text, author);
        Set<String> excluded = new HashSet<>();
        if (existingTags != null) {
            for (String tag : existingTags) excluded.add(Hashtag.key(Hashtag.normalize(tag)));
        }

        List<String> ranked = new ArrayList<>();
        Map<String, Evidence> strongest = new HashMap<>();
        for (Map.Entry<String, TagStats> entry : tags.entrySet()) {
            if (excluded.contains(entry.getKey())) continue;
            TagStats stats = entry.getValue();
            Evidence best = null;
            for (String token : tokens) {
                if (!isReason(stats, token)) continue;
                Evidence evidence = evidence(stats, token, token);
                if (best == null || byStrength(evidence, best) < 0) best = evidence;
            }
            if (best != null) {
                ranked.add(stats.spelling);
                strongest.put(stats.spelling, best);
            }
        }
        ranked.sort((a, b) -> {
            int byStrength = byStrength(strongest.get(a), strongest.get(b));
            return byStrength != 0 ? byStrength : Hashtag.ORDER.compare(a, b);
        });
        return new ArrayList<>(ranked.subList(0, Math.min(MAX_SUGGESTIONS, ranked.size())));
    }

    /**
     * Why {@code tag} is suggested for this quote: its reason words, strongest first, shown as
     * they are written in {@code text}, and the author when they are a reason. Null when the tag
     * can't be suggested at all (on too few quotes).
     */
    public Explanation explain(String text, String author, String tag) {
        TagStats stats = tags.get(Hashtag.key(Hashtag.normalize(tag)));
        if (stats == null) return null;
        Map<String, String> ownWords = new HashMap<>();
        forEachWord(text, ownWords::putIfAbsent);

        List<Evidence> words = new ArrayList<>();
        for (String token : tokens(text, null)) {
            if (isReason(stats, token)) {
                words.add(evidence(stats, token, ownWords.getOrDefault(token, displayWord(token))));
            }
        }
        words.sort(STRONGEST_FIRST);
        String authorToken = authorToken(author);
        Evidence authorEvidence = null;
        if (isReason(stats, authorToken)) authorEvidence = evidence(stats, authorToken, author.trim());
        return new Explanation(stats.spelling, words, authorEvidence);
    }

    /** What the model learned about {@code tag}; a tag no quote has gets a profile with 0 quotes. */
    public TagProfile profile(String tag) {
        String key = Hashtag.key(Hashtag.normalize(tag));
        int quotes = tagQuotes.getOrDefault(key, 0);
        TagStats stats = tags.get(key);
        if (stats == null) {
            return new TagProfile(tag, quotes, false, Collections.emptyList(), Collections.emptyList());
        }
        List<Evidence> words = new ArrayList<>();
        List<Evidence> authors = new ArrayList<>();
        for (String token : stats.tokens.keySet()) {
            if (!isReason(stats, token)) continue;
            if (token.startsWith(AUTHOR_PREFIX)) {
                authors.add(evidence(stats, token, authorNames.get(token)));
            } else {
                words.add(evidence(stats, token, displayWord(token)));
            }
        }
        words.sort(STRONGEST_FIRST);
        authors.sort(STRONGEST_FIRST);
        return new TagProfile(stats.spelling, quotes, true, words, authors);
    }

    private Evidence evidence(TagStats stats, String token, String label) {
        return new Evidence(label, stats.tokens.getOrDefault(token, 0), tokenCounts.getOrDefault(token, 0));
    }

    /** The collection's most frequent full word for {@code stem} (ties alphabetical), or the stem. */
    String displayWord(String stem) {
        Map<String, Integer> words = surfaceWords.get(stem);
        if (words == null) return stem;
        String best = stem;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : words.entrySet()) {
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && entry.getKey().compareTo(best) < 0)) {
                best = entry.getKey();
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * Whether {@code token} (a word stem or the author token) is a reason for the tag: on at least
     * {@link #MIN_WORD_WITH_TAG} (author: {@link #MIN_AUTHOR_WITH_TAG}) of the tag's quotes, and
     * the tag is on at least {@link #MIN_SHARE_PERCENT}% of the quotes with the token.
     */
    private boolean isReason(TagStats stats, String token) {
        if (token == null) return false;
        int together = stats.tokens.getOrDefault(token, 0);
        int min = token.startsWith(AUTHOR_PREFIX) ? MIN_AUTHOR_WITH_TAG : MIN_WORD_WITH_TAG;
        if (together < min) return false;
        return together * 100 >= MIN_SHARE_PERCENT * tokenCounts.getOrDefault(token, 0);
    }

    /** The quote's tokens: stemmed content words of {@code text}, plus the author token. */
    static Set<String> tokens(String text, String author) {
        Set<String> tokens = new LinkedHashSet<>();
        forEachWord(text, (stem, word) -> tokens.add(stem));
        String authorToken = authorToken(author);
        if (authorToken != null) tokens.add(authorToken);
        return tokens;
    }

    /**
     * Each content word of {@code text} (lowercased; stopwords and words under
     * {@link #MIN_WORD_LENGTH} letters dropped) with its stem, its first {@link #STEM_LENGTH}
     * letters.
     */
    private static void forEachWord(String text, BiConsumer<String, String> action) {
        if (text == null) return;
        StringBuilder word = new StringBuilder();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i <= lower.length(); ) {
            int cp = i < lower.length() ? lower.codePointAt(i) : ' ';
            if (Character.isLetter(cp)) {
                word.appendCodePoint(cp);
            } else if (word.length() > 0) {
                String w = word.toString();
                int length = w.codePointCount(0, w.length());
                if (length >= MIN_WORD_LENGTH && !STOPWORDS.contains(w)) {
                    action.accept(length <= STEM_LENGTH ? w : w.substring(0, w.offsetByCodePoints(0, STEM_LENGTH)), w);
                }
                word.setLength(0);
            }
            i += Character.charCount(cp);
        }
    }

    /** {@code author:<name>}, lowercased with whitespace collapsed; null when there is no author. */
    static String authorToken(String author) {
        if (author == null) return null;
        String name = author.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return name.isEmpty() ? null : AUTHOR_PREFIX + name;
    }
}
