package com.example.myquotes;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;
import org.tartarus.snowball.ext.germanStemmer;

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
 *       {@link #MIN_WORD_LENGTH} and English/German stopwords dropped; the rest stemmed with the
 *       Snowball stemmer of the quote's language (German when it has more German than English
 *       stopwords, else English). Plus one {@code author:<name>} and one {@code source:<name>}
 *       token. A quote counts each token once. In {@link #fit}, each of the quote's
 *       tags adds a {@code tag:<key>} token; in {@link #suggest}, each of {@code existingTags}.</li>
 *   <li><b>Reasons</b>: a word of the quote is a reason for tag t when at least
 *       {@link #MIN_WORD_WITH_TAG} quotes contain it and have t, and that is at least
 *       {@link #MIN_SHARE_PERCENT}% of the quotes containing it. The author is a reason when at
 *       least {@link #MIN_AUTHOR_WITH_TAG} of their quotes have t, and that is at least
 *       {@link #MIN_SHARE_PERCENT}% of their quotes; the source likewise, at least
 *       {@link #MIN_SOURCE_WITH_TAG} of its quotes. A tag u of the quote is a reason when at least
 *       {@link #MIN_TAG_WITH_TAG} quotes have both u and t, and that is at least
 *       {@link #MIN_SHARE_PERCENT}% of the quotes with u. A word whose stem is the stem of t's
 *       name (English or German) is a reason too, regardless of counts, but the weakest: below
 *       every counted reason.</li>
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
 * <p>
 * The "No hashtag" list (#53) ranks untagged quotes by {@link #suggestionsFor}: the same
 * suggestions, each with its strongest reason, for a quote that is part of the fitted collection,
 * counted as if it were left out (as the editor fits without the quote being edited).
 */
public final class HashtagSuggester {
    static final int MIN_QUOTES_PER_TAG = 5;
    static final int MIN_WORD_WITH_TAG = 3;
    static final int MIN_AUTHOR_WITH_TAG = 2;
    static final int MIN_SOURCE_WITH_TAG = 2;
    static final int MIN_TAG_WITH_TAG = 4;
    static final int MIN_SHARE_PERCENT = 40;
    static final int MAX_SUGGESTIONS = 3;
    static final int MIN_WORD_LENGTH = 3;
    static final String AUTHOR_PREFIX = "author:";
    static final String SOURCE_PREFIX = "source:";
    static final String TAG_PREFIX = "tag:";

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

    // Quotes per token (document frequency).
    private final Map<String, Integer> tokenCounts = new HashMap<>();
    // Per eligible tag (lowercase key): quotes carrying it, and per token the quotes with both.
    private final Map<String, TagStats> tags = new HashMap<>();
    // Quotes per tag (lowercase key), eligible or not, and its spelling.
    private final Map<String, Integer> tagQuotes = new HashMap<>();
    private final Map<String, String> tagSpellings = new HashMap<>();
    // Per stem, how often each full word occurred; per author or source token, its first spelling.
    private final Map<String, Map<String, Integer>> surfaceWords = new HashMap<>();
    private final Map<String, String> authorNames = new HashMap<>();
    private final Map<String, String> sourceNames = new HashMap<>();

    /**
     * One piece of evidence: {@code withTag} of the {@code total} quotes that contain the word
     * (or are by the author) carry the tag.
     */
    public static final class Evidence {
        public final String label;
        public final int withTag;
        public final int total;
        /** The quote contains the tag's name ({@code label} is the stem); no counts. */
        public final boolean nameMatch;

        Evidence(String label, int withTag, int total) {
            this(label, withTag, total, false);
        }

        private Evidence(String label, int withTag, int total, boolean nameMatch) {
            this.label = label;
            this.withTag = withTag;
            this.total = total;
            this.nameMatch = nameMatch;
        }

        /** The quote contains the tag's name, as the word stem {@code stem}. */
        static Evidence nameMatch(String stem) {
            return new Evidence(stem, 0, 0, true);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Evidence)) return false;
            Evidence other = (Evidence) o;
            return label.equals(other.label) && withTag == other.withTag && total == other.total
                    && nameMatch == other.nameMatch;
        }

        @Override
        public int hashCode() {
            return ((label.hashCode() * 31 + withTag) * 31 + total) * 31 + (nameMatch ? 1 : 0);
        }

        @Override
        public String toString() {
            return nameMatch ? label + " (name)" : label + " " + withTag + "/" + total;
        }
    }

    /** Counted evidence before a name match; then higher share first, then higher count; equal strength is 0. */
    static int byStrength(Evidence a, Evidence b) {
        if (a.nameMatch != b.nameMatch) return a.nameMatch ? 1 : -1;
        int byShare = Long.compare((long) b.withTag * a.total, (long) a.withTag * b.total);
        return byShare != 0 ? byShare : b.withTag - a.withTag;
    }

    /** Strongest first (higher share, then higher count), then by label. */
    static final Comparator<Evidence> STRONGEST_FIRST = (a, b) -> {
        int byStrength = byStrength(a, b);
        return byStrength != 0 ? byStrength : a.label.compareTo(b.label);
    };

    /** Why a tag is suggested for one quote: its reason words, author, source and tags (strongest first). */
    public static final class Explanation {
        public final String tag;
        public final List<Evidence> words;
        /** The author when they are a reason for the tag; else null. */
        public final Evidence author;
        /** The source when it is a reason for the tag; else null. */
        public final Evidence source;
        /** The quote's tags that are a reason for the tag, labelled {@code #Tag}. */
        public final List<Evidence> tags;
        /** The quote contains the tag's name. */
        public final boolean containsName;

        Explanation(String tag, List<Evidence> words, Evidence author, Evidence source, List<Evidence> tags,
                    boolean containsName) {
            this.tag = tag;
            this.words = words;
            this.author = author;
            this.source = source;
            this.tags = tags;
            this.containsName = containsName;
        }
    }

    /** A suggested tag and its strongest reason ("#Love (6/8)" in the "No hashtag" list). */
    public static final class Suggestion {
        public final String tag;
        public final Evidence strongest;

        Suggestion(String tag, Evidence strongest) {
            this.tag = tag;
            this.strongest = strongest;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Suggestion)) return false;
            Suggestion other = (Suggestion) o;
            return tag.equals(other.tag) && strongest.equals(other.strongest);
        }

        @Override
        public int hashCode() {
            return tag.hashCode() * 31 + strongest.hashCode();
        }

        @Override
        public String toString() {
            return tag + " (" + strongest + ")";
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
        /** Every source that is a reason for the tag, strongest first. */
        public final List<Evidence> sources;
        /** Every other tag that is a reason for the tag, labelled {@code #Tag}, strongest first. */
        public final List<Evidence> tags;

        TagProfile(String tag, int quotes, boolean suggestable, List<Evidence> words, List<Evidence> authors,
                List<Evidence> sources, List<Evidence> tags) {
            this.tag = tag;
            this.quotes = quotes;
            this.suggestable = suggestable;
            this.words = words;
            this.authors = authors;
            this.sources = sources;
            this.tags = tags;
        }
    }

    private static final class TagStats {
        final String spelling;
        int quotes;
        final Map<String, Integer> tokens = new HashMap<>();
        // The stems of the tag's name, English and German (set for eligible tags).
        final Set<String> nameStems = new HashSet<>();

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
            Set<String> tokens = tokens(quote.getQuoteText(), quote.getAuthor(), quote.getSource());
            addTagTokens(tokens, quote.getTags());
            quoteTokens.add(tokens);
            for (String token : tokens) model.tokenCounts.merge(token, 1, Integer::sum);
            forEachWord(quote.getQuoteText(), (stem, word) -> model.surfaceWords
                    .computeIfAbsent(stem, k -> new HashMap<>()).merge(word, 1, Integer::sum));
            String authorToken = authorToken(quote.getAuthor());
            if (authorToken != null) model.authorNames.putIfAbsent(authorToken, quote.getAuthor().trim());
            String sourceToken = sourceToken(quote.getSource());
            if (sourceToken != null) model.sourceNames.putIfAbsent(sourceToken, quote.getSource().trim());
            for (String tag : quote.getTags()) {
                all.computeIfAbsent(Hashtag.key(tag), k -> new TagStats(tag)).quotes++;
            }
        }
        for (Map.Entry<String, TagStats> entry : all.entrySet()) {
            model.tagQuotes.put(entry.getKey(), entry.getValue().quotes);
            model.tagSpellings.put(entry.getKey(), entry.getValue().spelling);
            if (entry.getValue().quotes >= MIN_QUOTES_PER_TAG) {
                model.tags.put(entry.getKey(), entry.getValue());
                entry.getValue().nameStems.addAll(nameStems(entry.getKey()));
            }
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

    /**
     * Up to {@link #MAX_SUGGESTIONS} tags for the quote, best first; never one of
     * {@code existingTags}, which are reasons for other tags.
     */
    public List<String> suggest(String text, String author, String source, Collection<String> existingTags) {
        Set<String> tokens = tokens(text, author, source);
        addTagTokens(tokens, existingTags);
        Set<String> excluded = new HashSet<>();
        if (existingTags != null) {
            for (String tag : existingTags) excluded.add(Hashtag.key(Hashtag.normalize(tag)));
        }

        List<String> tags = new ArrayList<>();
        for (Suggestion suggestion : suggestions(tokens, excluded, 0)) tags.add(suggestion.tag);
        return tags;
    }

    /**
     * What {@link #suggest} gives {@code quote}, an untagged quote of the fitted collection, if it
     * were fitted without it: up to {@link #MAX_SUGGESTIONS}, best first, each with its strongest
     * reason. Leaving an untagged quote out only lowers the counts of its own tokens by one.
     */
    public List<Suggestion> suggestionsFor(Quote quote) {
        if (!quote.getTags().isEmpty()) {
            throw new IllegalArgumentException("Quote #" + quote.getId() + " has hashtags");
        }
        return suggestions(tokens(quote.getQuoteText(), quote.getAuthor(), quote.getSource()),
                Collections.emptySet(), 1);
    }

    /**
     * The best tags for {@code tokens}, none of {@code excluded}, with {@code self} quotes left out
     * of each token's count.
     */
    private List<Suggestion> suggestions(Set<String> tokens, Set<String> excluded, int self) {
        List<Suggestion> ranked = new ArrayList<>();
        for (Map.Entry<String, TagStats> entry : tags.entrySet()) {
            if (excluded.contains(entry.getKey())) continue;
            TagStats stats = entry.getValue();
            Evidence best = null;
            for (String token : tokens) {
                if (!isReason(stats, token, self)) continue;
                Evidence evidence = evidence(stats, token, token, self);
                if (best == null || byStrength(evidence, best) < 0) best = evidence;
            }
            if (best == null) {
                String name = nameIn(stats, tokens);
                if (name != null) best = Evidence.nameMatch(name);
            }
            if (best != null) ranked.add(new Suggestion(stats.spelling, best));
        }
        ranked.sort((a, b) -> {
            int byStrength = byStrength(a.strongest, b.strongest);
            return byStrength != 0 ? byStrength : Hashtag.ORDER.compare(a.tag, b.tag);
        });
        return new ArrayList<>(ranked.subList(0, Math.min(MAX_SUGGESTIONS, ranked.size())));
    }

    /**
     * Why {@code tag} is suggested for this quote: its reason words, strongest first, shown as
     * they are written in {@code text}, the author and the source when they are a reason, and those
     * of the quote's tags ({@code quoteTags}) that are a reason, strongest first. Null when the tag
     * can't be suggested at all (on too few quotes).
     */
    public Explanation explain(String text, String author, String source, Collection<String> quoteTags,
                               String tag) {
        TagStats stats = tags.get(Hashtag.key(Hashtag.normalize(tag)));
        if (stats == null) return null;
        Map<String, String> ownWords = new HashMap<>();
        forEachWord(text, ownWords::putIfAbsent);

        List<Evidence> words = new ArrayList<>();
        Set<String> wordTokens = tokens(text, null, null);
        for (String token : wordTokens) {
            if (isReason(stats, token)) {
                words.add(evidence(stats, token, ownWords.getOrDefault(token, displayWord(token))));
            }
        }
        words.sort(STRONGEST_FIRST);
        String authorToken = authorToken(author);
        Evidence authorEvidence = null;
        if (isReason(stats, authorToken)) authorEvidence = evidence(stats, authorToken, author.trim());
        String sourceToken = sourceToken(source);
        Evidence sourceEvidence = null;
        if (isReason(stats, sourceToken)) sourceEvidence = evidence(stats, sourceToken, source.trim());
        Set<String> tagTokens = new LinkedHashSet<>();
        addTagTokens(tagTokens, quoteTags);
        List<Evidence> tagEvidence = new ArrayList<>();
        for (String token : tagTokens) {
            if (isTagReason(stats, token)) tagEvidence.add(evidence(stats, token, tagLabel(token)));
        }
        tagEvidence.sort(STRONGEST_FIRST);
        return new Explanation(stats.spelling, words, authorEvidence, sourceEvidence, tagEvidence,
                nameIn(stats, wordTokens) != null);
    }

    /** What the model learned about {@code tag}; a tag no quote has gets a profile with 0 quotes. */
    public TagProfile profile(String tag) {
        String key = Hashtag.key(Hashtag.normalize(tag));
        int quotes = tagQuotes.getOrDefault(key, 0);
        TagStats stats = tags.get(key);
        if (stats == null) {
            return new TagProfile(tag, quotes, false, Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());
        }
        List<Evidence> words = new ArrayList<>();
        List<Evidence> authors = new ArrayList<>();
        List<Evidence> sources = new ArrayList<>();
        List<Evidence> tagEvidence = new ArrayList<>();
        for (String token : stats.tokens.keySet()) {
            if (token.startsWith(TAG_PREFIX)) {
                if (isTagReason(stats, token)) tagEvidence.add(evidence(stats, token, tagLabel(token)));
            } else if (!isReason(stats, token)) {
                continue;
            } else if (token.startsWith(AUTHOR_PREFIX)) {
                authors.add(evidence(stats, token, authorNames.get(token)));
            } else if (token.startsWith(SOURCE_PREFIX)) {
                sources.add(evidence(stats, token, sourceNames.get(token)));
            } else {
                words.add(evidence(stats, token, displayWord(token)));
            }
        }
        words.sort(STRONGEST_FIRST);
        authors.sort(STRONGEST_FIRST);
        sources.sort(STRONGEST_FIRST);
        tagEvidence.sort(STRONGEST_FIRST);
        return new TagProfile(stats.spelling, quotes, true, words, authors, sources, tagEvidence);
    }

    /** The first of {@code tokens} that is a stem of the tag's name; null when there is none. */
    private static String nameIn(TagStats stats, Set<String> tokens) {
        for (String token : tokens) {
            if (stats.nameStems.contains(token)) return token;
        }
        return null;
    }

    /** The English and German stems of a tag's name ({@code key}, lowercase); none when it is too short. */
    private static Set<String> nameStems(String key) {
        Set<String> stems = new HashSet<>();
        if (key.codePointCount(0, key.length()) < MIN_WORD_LENGTH) return stems;
        for (SnowballStemmer stemmer : new SnowballStemmer[]{new englishStemmer(), new germanStemmer()}) {
            stemmer.setCurrent(key);
            stemmer.stem();
            stems.add(stemmer.getCurrent());
        }
        return stems;
    }

    /** Whether the tag token is a reason for the tag; a tag is never a reason for itself. */
    private boolean isTagReason(TagStats stats, String token) {
        return !token.equals(TAG_PREFIX + Hashtag.key(stats.spelling)) && isReason(stats, token);
    }

    /** "#Death" for {@code tag:death}, in the collection's spelling. */
    private String tagLabel(String token) {
        String key = token.substring(TAG_PREFIX.length());
        return Hashtag.display(tagSpellings.getOrDefault(key, key));
    }

    private Evidence evidence(TagStats stats, String token, String label) {
        return evidence(stats, token, label, 0);
    }

    private Evidence evidence(TagStats stats, String token, String label, int self) {
        return new Evidence(label, stats.tokens.getOrDefault(token, 0), tokenCounts.getOrDefault(token, 0) - self);
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
     * Whether {@code token} (a word stem, the author or source token or a tag token) is a reason for
     * the tag: on at least {@link #MIN_WORD_WITH_TAG} (author: {@link #MIN_AUTHOR_WITH_TAG}, source:
     * {@link #MIN_SOURCE_WITH_TAG}, tag: {@link #MIN_TAG_WITH_TAG}) of the tag's quotes, and the tag is on at least
     * {@link #MIN_SHARE_PERCENT}% of the quotes with the token.
     */
    private boolean isReason(TagStats stats, String token) {
        return isReason(stats, token, 0);
    }

    /** {@link #isReason(TagStats, String)} with {@code self} quotes left out of the token's count. */
    private boolean isReason(TagStats stats, String token, int self) {
        if (token == null) return false;
        int together = stats.tokens.getOrDefault(token, 0);
        int min = token.startsWith(AUTHOR_PREFIX) ? MIN_AUTHOR_WITH_TAG
                : token.startsWith(SOURCE_PREFIX) ? MIN_SOURCE_WITH_TAG
                : token.startsWith(TAG_PREFIX) ? MIN_TAG_WITH_TAG : MIN_WORD_WITH_TAG;
        if (together < min) return false;
        return together * 100 >= MIN_SHARE_PERCENT * (tokenCounts.getOrDefault(token, 0) - self);
    }

    /** The quote's tokens: stemmed content words of {@code text}, plus the author and source tokens. */
    static Set<String> tokens(String text, String author, String source) {
        Set<String> tokens = new LinkedHashSet<>();
        forEachWord(text, (stem, word) -> tokens.add(stem));
        String authorToken = authorToken(author);
        if (authorToken != null) tokens.add(authorToken);
        String sourceToken = sourceToken(source);
        if (sourceToken != null) tokens.add(sourceToken);
        return tokens;
    }

    /**
     * Each content word of {@code text} (lowercased; stopwords and words under
     * {@link #MIN_WORD_LENGTH} letters dropped) with its stem, from the stemmer of the text's
     * language ({@link #stemmerFor}).
     */
    private static void forEachWord(String text, BiConsumer<String, String> action) {
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

    /** Adds a {@code tag:<key>} token for each of {@code tags} (normalized; null is none). */
    private static void addTagTokens(Set<String> tokens, Collection<String> tags) {
        if (tags == null) return;
        for (String tag : tags) {
            String normalized = Hashtag.normalize(tag);
            if (!normalized.isEmpty()) tokens.add(TAG_PREFIX + Hashtag.key(normalized));
        }
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
