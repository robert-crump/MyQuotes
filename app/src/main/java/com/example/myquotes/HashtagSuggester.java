package com.example.myquotes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Suggests hashtags for a quote being added or edited (#50), learned from the user's own
 * collection. Pure and on-device: {@link #fit} counts, per tag, which tokens its quotes contain;
 * {@link #suggest} scores each tag for a new quote's tokens.
 * <ul>
 *   <li><b>Tokens</b>: the quote text lowercased and split on non-letters; words shorter than
 *       {@link #MIN_WORD_LENGTH} and English/German stopwords dropped; the rest cut to their first
 *       {@link #STEM_LENGTH} letters as a cheap stem. Plus one {@code author:<name>} token. The
 *       source is not used. A quote counts each token once.</li>
 *   <li><b>Score</b>: per tag t, the sum of log-lift {@code P(w|t) / P(w)} (add-one smoothing)
 *       over the quote's tokens that occur with t and have positive lift (the supporting
 *       tokens).</li>
 *   <li><b>Eligibility</b>: t is on at least {@link #MIN_QUOTES_PER_TAG} quotes and has at least
 *       {@link #MIN_SUPPORTING_TOKENS} supporting tokens, or the author exception holds: the
 *       author has at least {@link #AUTHOR_MIN_QUOTES} quotes and at least
 *       {@link #AUTHOR_MIN_PERCENT}% of them carry t.</li>
 * </ul>
 * At most {@link #MAX_SUGGESTIONS}, best score first, ties by tag name; tags the quote already
 * has are left out (ignoring case).
 */
public final class HashtagSuggester {
    static final int MIN_QUOTES_PER_TAG = 5;
    static final int MIN_SUPPORTING_TOKENS = 2;
    static final int AUTHOR_MIN_QUOTES = 3;
    static final int AUTHOR_MIN_PERCENT = 60;
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

    private final int quoteCount;
    // Quotes per token (document frequency).
    private final Map<String, Integer> tokenCounts = new HashMap<>();
    // Per eligible tag (lowercase key): quotes carrying it, and per token the quotes with both.
    private final Map<String, TagStats> tags = new HashMap<>();

    private static final class TagStats {
        final String spelling;
        int quotes;
        final Map<String, Integer> tokens = new HashMap<>();

        TagStats(String spelling) {
            this.spelling = spelling;
        }
    }

    private HashtagSuggester(int quoteCount) {
        this.quoteCount = quoteCount;
    }

    /** Learns from {@code quotes}. For Edit, pass the collection without the quote being edited. */
    public static HashtagSuggester fit(List<Quote> quotes) {
        HashtagSuggester model = new HashtagSuggester(quotes.size());
        Map<String, TagStats> all = new HashMap<>();
        List<Set<String>> quoteTokens = new ArrayList<>(quotes.size());
        for (Quote quote : quotes) {
            Set<String> tokens = tokens(quote.getQuoteText(), quote.getAuthor());
            quoteTokens.add(tokens);
            for (String token : tokens) model.tokenCounts.merge(token, 1, Integer::sum);
            for (String tag : quote.getTags()) {
                all.computeIfAbsent(Hashtag.key(tag), k -> new TagStats(tag)).quotes++;
            }
        }
        for (Map.Entry<String, TagStats> entry : all.entrySet()) {
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
        String authorToken = authorToken(author);

        List<String> ranked = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, TagStats> entry : tags.entrySet()) {
            if (excluded.contains(entry.getKey())) continue;
            TagStats stats = entry.getValue();
            double score = 0;
            int supporting = 0;
            for (String token : tokens) {
                double lift = logLift(stats, token);
                if (lift > 0) {
                    score += lift;
                    supporting++;
                }
            }
            if (supporting >= MIN_SUPPORTING_TOKENS || authorException(stats, authorToken)) {
                ranked.add(stats.spelling);
                scores.put(stats.spelling, score);
            }
        }
        ranked.sort((a, b) -> {
            int byScore = Double.compare(scores.get(b), scores.get(a));
            return byScore != 0 ? byScore : Hashtag.ORDER.compare(a, b);
        });
        return new ArrayList<>(ranked.subList(0, Math.min(MAX_SUGGESTIONS, ranked.size())));
    }

    /** Why {@code tag} would be suggested: the quote's tokens with positive lift for it (for tests). */
    List<String> supportingTokens(String text, String author, String tag) {
        TagStats stats = tags.get(Hashtag.key(tag));
        if (stats == null) return Collections.emptyList();
        List<String> supporting = new ArrayList<>();
        for (String token : tokens(text, author)) {
            if (logLift(stats, token) > 0) supporting.add(token);
        }
        Collections.sort(supporting);
        return supporting;
    }

    /** log(P(w|t) / P(w)) with add-one smoothing; 0 for a token never seen with t. */
    private double logLift(TagStats stats, String token) {
        Integer together = stats.tokens.get(token);
        if (together == null) return 0;
        int overall = tokenCounts.getOrDefault(token, 0);
        double pGivenTag = (together + 1.0) / (stats.quotes + 2.0);
        double p = (overall + 1.0) / (quoteCount + 2.0);
        return Math.log(pGivenTag / p);
    }

    private boolean authorException(TagStats stats, String authorToken) {
        if (authorToken == null) return false;
        int authorQuotes = tokenCounts.getOrDefault(authorToken, 0);
        if (authorQuotes < AUTHOR_MIN_QUOTES) return false;
        int tagged = stats.tokens.getOrDefault(authorToken, 0);
        return tagged * 100 >= AUTHOR_MIN_PERCENT * authorQuotes;
    }

    /** The quote's tokens: stemmed content words of {@code text}, plus the author token. */
    static Set<String> tokens(String text, String author) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text != null) {
            StringBuilder word = new StringBuilder();
            String lower = text.toLowerCase(Locale.ROOT);
            for (int i = 0; i <= lower.length(); ) {
                int cp = i < lower.length() ? lower.codePointAt(i) : ' ';
                if (Character.isLetter(cp)) {
                    word.appendCodePoint(cp);
                } else if (word.length() > 0) {
                    addWord(tokens, word.toString());
                    word.setLength(0);
                }
                i += Character.charCount(cp);
            }
        }
        String authorToken = authorToken(author);
        if (authorToken != null) tokens.add(authorToken);
        return tokens;
    }

    private static void addWord(Set<String> tokens, String word) {
        int length = word.codePointCount(0, word.length());
        if (length < MIN_WORD_LENGTH || STOPWORDS.contains(word)) return;
        tokens.add(length <= STEM_LENGTH ? word : word.substring(0, word.offsetByCodePoints(0, STEM_LENGTH)));
    }

    /** {@code author:<name>}, lowercased with whitespace collapsed; null when there is no author. */
    static String authorToken(String author) {
        if (author == null) return null;
        String name = author.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return name.isEmpty() ? null : AUTHOR_PREFIX + name;
    }
}
