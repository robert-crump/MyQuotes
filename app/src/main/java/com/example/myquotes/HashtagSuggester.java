package com.example.myquotes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Suggests hashtags for a quote being added or edited (#50), learned from the user's own
 * collection. Pure and on-device: {@link #fit} counts, per tag, which tokens its quotes contain;
 * {@code suggest} looks for reasons to suggest each tag for a quote (#52). Two subjects are judged (#70): a {@link Draft}, the editor's text against the full model, and a saved {@link Quote} of the fitted collection, counted as if it were left out.
 * <ul>
 *   <li><b>Tokens</b>: the quote text lowercased and split on non-letters; words shorter than
 *       {@link QuoteTokens#MIN_WORD_LENGTH} and English/German stopwords dropped; the rest stemmed
 *       with the Snowball stemmer of the quote's language (see {@link QuoteTokens}). Plus one
 *       {@code author:<name>}, one {@code source:<name>} and one {@code tag:<key>} token per tag
 *       (the draft's chips for a draft). A quote counts each token once.</li>
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
 *   <li><b>Similar quotes</b> (#61): with quote vectors ({@link #fit(List, Map)}, normalized, so
 *       cosine is a dot product), the quote's neighbours are the {@link #KNN_K} tagged quotes most
 *       similar to it, each at least {@link #KNN_MIN_SIMILARITY}. Untagged quotes are never
 *       neighbours: a missing tag is missing data, not evidence against it. t is a reason when at
 *       least {@link #KNN_MIN_WITH_TAG} neighbours have it; the evidence is "withTag of the
 *       neighbours", ranked like every counted reason. A quote without a vector has no such
 *       reason.</li>
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
 * The ranked "No hashtag" and "Has suggestions" lists (#53, #55) use {@link #suggest(Quote)}: the
 * same suggestions, each with its strongest reason, for a quote that is part of the fitted
 * collection, counted as if it were left out (as the editor fits without the quote being edited).
 * <p>
 * Rejected tags (#55): the tags the user said not to suggest for a quote are left out like its
 * own tags; they don't change any count.
 * <p>
 * One tag (#56): {@link #suggestionFor} gives that tag for a quote of the fitted collection, counted
 * like {@link #suggest(Quote)} but whether or not it is in the top {@link #MAX_SUGGESTIONS}
 * ("Suggested for N quotes"); {@link #explain(Quote, String)} explains it with the same counts; and
 * {@link Draft#pinning} puts it first in the editor's row during that tag's tagging run.
 */
public final class HashtagSuggester {
    static final int MIN_QUOTES_PER_TAG = 5;
    static final int MIN_WORD_WITH_TAG = 3;
    static final int MIN_AUTHOR_WITH_TAG = 2;
    static final int MIN_SOURCE_WITH_TAG = 2;
    static final int MIN_TAG_WITH_TAG = 4;
    static final int MIN_SHARE_PERCENT = 40;
    static final int MAX_SUGGESTIONS = 3;
    static final int KNN_K = 10;
    static final int KNN_MIN_WITH_TAG = 3;
    // Tuned for paraphrase-multilingual-MiniLM-L12-v2 (ADR-006); another model needs its own floor.
    static final float KNN_MIN_SIMILARITY = 0.40f;
    static final String SIMILAR_LABEL = "similar quotes";
    static final String MEANING_LABEL = "meaning";

    /**
     * The similar-quotes reason's parameters (#61): {@link #DEFAULT} in the app, swept by the eval.
     * {@code belowCounted} ranks similar-quotes evidence below every counted reason (above a name
     * match), so it only fills slots the other reasons leave empty.
     */
    static final class Knn {
        static final Knn DEFAULT = new Knn(KNN_K, KNN_MIN_WITH_TAG, KNN_MIN_SIMILARITY, false);

        final int k;
        final int minWithTag;
        final float minSimilarity;
        final boolean belowCounted;

        Knn(int k, int minWithTag, float minSimilarity, boolean belowCounted) {
            this.k = k;
            this.minWithTag = minWithTag;
            this.minSimilarity = minSimilarity;
            this.belowCounted = belowCounted;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "k=%d m=%d floor=%.2f%s", k, minWithTag, minSimilarity,
                    belowCounted ? " below" : "");
        }
    }

    /**
     * How a small tag's name is written for the embedder (#64): {@code LifeWisdom} as "life wisdom"
     * ({@link #BARE}) or "A quote about life wisdom." ({@link #TEMPLATE}).
     */
    enum NameText {
        BARE, TEMPLATE;

        String of(String tag) {
            String words = splitName(tag);
            return this == BARE ? words : "A quote about " + words + ".";
        }
    }

    /** {@code LifeWisdom} → "life wisdom", {@code AIEthics} → "ai ethics"; underscores and dashes are spaces. */
    static String splitName(String tag) {
        return Hashtag.normalize(tag)
                .replaceAll("([\\p{Ll}\\p{Nd}])(\\p{Lu})", "$1 $2")
                .replaceAll("(\\p{Lu})(\\p{Lu}\\p{Ll})", "$1 $2")
                .replaceAll("[_\\-]+", " ")
                .trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Reasons for small tags (#64), on 1 to {@link #MIN_QUOTES_PER_TAG} − 1 quotes, ranked below
     * every other reason: {@code stem}, the quote contains the tag's name (as for eligible tags);
     * {@code meaning}, the quote's vector is close to the name's vector: cosine at least
     * {@code minSimilarity} and among the {@code topN} collection quotes without the tag closest to
     * the name. {@link #OFF} in the app until the eval gate passes; swept by the eval.
     */
    static final class ZeroShot {
        static final ZeroShot OFF = new ZeroShot(false, false, 0f, 0);

        final boolean stem;
        final boolean meaning;
        final float minSimilarity;
        final int topN;

        ZeroShot(boolean stem, boolean meaning, float minSimilarity, int topN) {
            this.stem = stem;
            this.meaning = meaning;
            this.minSimilarity = minSimilarity;
            this.topN = topN;
        }

        boolean on() {
            return stem || meaning;
        }

        @Override
        public String toString() {
            if (!meaning) return stem ? "stem" : "off";
            return String.format(Locale.ROOT, "%sfloor=%.2f N=%d", stem ? "stem+meaning " : "meaning ",
                    minSimilarity, topN);
        }
    }

    // Quotes per token (document frequency).
    private final Map<String, Integer> tokenCounts;
    // Per eligible tag (lowercase key): quotes carrying it, and per token the quotes with both.
    private final Map<String, TagStats> tags;
    // Quotes per tag (lowercase key), eligible or not, and its spelling.
    private final Map<String, Integer> tagQuotes;
    private final Map<String, String> tagSpellings;
    // Per stem, how often each full word occurred; per author or source token, its first spelling.
    private final Map<String, Map<String, Integer>> surfaceWords;
    private final Map<String, String> authorNames;
    private final Map<String, String> sourceNames;
    // Similar quotes (#61): the vectors by quote id, and the tagged quotes with a vector.
    private final Map<Integer, float[]> vectors;
    private final List<Candidate> candidates;
    private final Knn knn;
    // Small tags (#64): per key, its spelling and name stems; every quote with a vector (tagged or
    // not) to rank against a name; per small tag with a name vector, the ranking; the parameters.
    private final Map<String, SmallTag> smallTags;
    private final List<Candidate> withVectors;
    private final Map<String, NameRank> names;
    private final ZeroShot zeroShot;

    /** A tag on fewer than {@link #MIN_QUOTES_PER_TAG} quotes. */
    private static final class SmallTag {
        final String spelling;
        final Set<String> nameStems;

        SmallTag(String spelling, Set<String> nameStems) {
            this.spelling = spelling;
            this.nameStems = nameStems;
        }
    }

    /** A small tag's name vector and the cosines of the collection quotes without the tag, highest first. */
    private static final class NameRank {
        final float[] vector;
        final float[] similarities;

        NameRank(float[] vector, float[] similarities) {
            this.vector = vector;
            this.similarities = similarities;
        }

        /** The lowest cosine that is a reason: the floor, or the {@code topN}th quote's, whichever is higher. */
        float cutoff(ZeroShot zeroShot) {
            float nth = similarities.length >= zeroShot.topN && zeroShot.topN > 0
                    ? similarities[zeroShot.topN - 1] : Float.NEGATIVE_INFINITY;
            return Math.max(zeroShot.minSimilarity, nth);
        }
    }

    /** A tagged quote with a vector: a possible neighbour. */
    private static final class Candidate {
        final Integer id;
        final float[] vector;
        final Set<String> tagKeys;

        Candidate(Integer id, float[] vector, Set<String> tagKeys) {
            this.id = id;
            this.vector = vector;
            this.tagKeys = tagKeys;
        }
    }

    /** A quote's neighbours, most similar first. */
    private static final class Neighbours {
        static final Neighbours NONE = new Neighbours(Collections.emptyList());
        final List<Candidate> quotes;

        Neighbours(List<Candidate> quotes) {
            this.quotes = quotes;
        }

        int withTag(String key) {
            int count = 0;
            for (Candidate candidate : quotes) {
                if (candidate.tagKeys.contains(key)) count++;
            }
            return count;
        }
    }

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
        /** {@code withTag} of the quote's {@code total} most similar quotes carry the tag (#61). */
        public final boolean similar;
        /** The quote is close in meaning to a small tag's name (#64); no counts. */
        public final boolean meaning;
        /** For {@link #meaning}: the cosine of the quote and the name; ranks meaning evidence. */
        final float closeness;
        // 0: counted, 1: similar quotes ranked below counted reasons, 2: name match,
        // 3: a small tag's name match, 4: close in meaning to a small tag's name (#64).
        private final int tier;

        Evidence(String label, int withTag, int total) {
            this(label, withTag, total, false, false, 0);
        }

        private Evidence(String label, int withTag, int total, boolean nameMatch, boolean similar, int tier) {
            this(label, withTag, total, nameMatch, similar, false, 0f, tier);
        }

        private Evidence(String label, int withTag, int total, boolean nameMatch, boolean similar, boolean meaning,
                         float closeness, int tier) {
            this.label = label;
            this.withTag = withTag;
            this.total = total;
            this.nameMatch = nameMatch;
            this.similar = similar;
            this.meaning = meaning;
            this.closeness = closeness;
            this.tier = tier;
        }

        /** The quote contains the tag's name, as the word stem {@code stem}. */
        static Evidence nameMatch(String stem) {
            return new Evidence(stem, 0, 0, true, false, 2);
        }

        /** The quote contains a small tag's name (#64), as the word stem {@code stem}. */
        static Evidence smallNameMatch(String stem) {
            return new Evidence(stem, 0, 0, true, false, 3);
        }

        /** The quote is close in meaning to a small tag's name (#64), at cosine {@code closeness}. */
        static Evidence meaning(float closeness) {
            return new Evidence(MEANING_LABEL, 0, 0, false, false, true, closeness, 4);
        }

        /** {@code withTag} of the quote's {@code total} neighbours carry the tag. */
        static Evidence similar(int withTag, int total) {
            return similar(withTag, total, false);
        }

        static Evidence similar(int withTag, int total, boolean belowCounted) {
            return new Evidence(SIMILAR_LABEL, withTag, total, false, true, belowCounted ? 1 : 0);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Evidence)) return false;
            Evidence other = (Evidence) o;
            return label.equals(other.label) && withTag == other.withTag && total == other.total
                    && nameMatch == other.nameMatch && similar == other.similar && meaning == other.meaning
                    && Float.compare(closeness, other.closeness) == 0 && tier == other.tier;
        }

        @Override
        public int hashCode() {
            return ((((((label.hashCode() * 31 + withTag) * 31 + total) * 31 + (nameMatch ? 1 : 0)) * 31
                    + (similar ? 1 : 0)) * 31 + (meaning ? 1 : 0)) * 31 + Float.hashCode(closeness)) * 31 + tier;
        }

        @Override
        public String toString() {
            if (meaning) return String.format(Locale.ROOT, "%s %.2f", label, closeness);
            return nameMatch ? label + " (name)" : label + " " + withTag + "/" + total;
        }
    }

    /**
     * Counted evidence before a name match (and before similar quotes ranked below counted
     * reasons), then a small tag's name match, then closeness in meaning to a small tag's name
     * (#64, higher cosine first); otherwise higher share first, then higher count; equal strength is 0.
     */
    static int byStrength(Evidence a, Evidence b) {
        if (a.tier != b.tier) return a.tier - b.tier;
        if (a.meaning) return Float.compare(b.closeness, a.closeness);
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
        /** Similar quotes when they are a reason for the tag (#61); else null. */
        public final Evidence similar;
        /** The ids of the neighbours with the tag, most similar first; empty when {@link #similar} is null. */
        public final List<Integer> similarQuoteIds;

        Explanation(String tag, List<Evidence> words, Evidence author, Evidence source, List<Evidence> tags,
                    boolean containsName) {
            this(tag, words, author, source, tags, containsName, null, Collections.emptyList());
        }

        Explanation(String tag, List<Evidence> words, Evidence author, Evidence source, List<Evidence> tags,
                    boolean containsName, Evidence similar, List<Integer> similarQuoteIds) {
            this.tag = tag;
            this.words = words;
            this.author = author;
            this.source = source;
            this.tags = tags;
            this.containsName = containsName;
            this.similar = similar;
            this.similarQuoteIds = similarQuoteIds;
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

    private HashtagSuggester(Map<Integer, float[]> vectors, Knn knn) {
        tokenCounts = new HashMap<>();
        tags = new HashMap<>();
        tagQuotes = new HashMap<>();
        tagSpellings = new HashMap<>();
        surfaceWords = new HashMap<>();
        authorNames = new HashMap<>();
        sourceNames = new HashMap<>();
        candidates = new ArrayList<>();
        this.vectors = vectors;
        this.knn = knn;
        smallTags = new HashMap<>();
        withVectors = new ArrayList<>();
        names = Collections.emptyMap();
        zeroShot = ZeroShot.OFF;
    }

    /** {@code fitted}'s counts, shared, with other similar-quotes and small-tag parameters. */
    private HashtagSuggester(HashtagSuggester fitted, Knn knn, Map<String, NameRank> names, ZeroShot zeroShot) {
        tokenCounts = fitted.tokenCounts;
        tags = fitted.tags;
        tagQuotes = fitted.tagQuotes;
        tagSpellings = fitted.tagSpellings;
        surfaceWords = fitted.surfaceWords;
        authorNames = fitted.authorNames;
        sourceNames = fitted.sourceNames;
        candidates = fitted.candidates;
        vectors = fitted.vectors;
        this.knn = knn;
        smallTags = fitted.smallTags;
        withVectors = fitted.withVectors;
        this.names = names;
        this.zeroShot = zeroShot;
    }

    /**
     * {@link #fit(List, Map)} without vectors (no similar-quotes reason). Tests and the eval only:
     * the app always passes {@link QuoteEmbeddings}' vectors (#62).
     */
    static HashtagSuggester fit(List<Quote> quotes) {
        return fit(quotes, Collections.emptyMap());
    }

    /**
     * Learns from {@code quotes}, with the similar-quotes reason: {@code vectors} maps quote ids to
     * normalized vectors (read only, not copied). Quotes without one are never neighbours; with no
     * vectors at all the model is the same as before #61. For Edit, pass the collection without the
     * quote being edited.
     */
    public static HashtagSuggester fit(List<Quote> quotes, Map<Integer, float[]> vectors) {
        return fit(quotes, vectors, Knn.DEFAULT);
    }

    static HashtagSuggester fit(List<Quote> quotes, Map<Integer, float[]> vectors, Knn knn) {
        HashtagSuggester model = new HashtagSuggester(vectors, knn);
        Map<String, TagStats> all = new HashMap<>();
        List<Set<String>> quoteTokens = new ArrayList<>(quotes.size());
        for (Quote quote : quotes) {
            Set<String> tokens = QuoteTokens.of(quote.getQuoteText(), quote.getAuthor(), quote.getSource(),
                    quote.getTags());
            quoteTokens.add(tokens);
            for (String token : tokens) model.tokenCounts.merge(token, 1, Integer::sum);
            QuoteTokens.forEachWord(quote.getQuoteText(), (stem, word) -> model.surfaceWords
                    .computeIfAbsent(stem, k -> new HashMap<>()).merge(word, 1, Integer::sum));
            String authorToken = QuoteTokens.authorToken(quote.getAuthor());
            if (authorToken != null) model.authorNames.putIfAbsent(authorToken, quote.getAuthor().trim());
            String sourceToken = QuoteTokens.sourceToken(quote.getSource());
            if (sourceToken != null) model.sourceNames.putIfAbsent(sourceToken, quote.getSource().trim());
            for (String tag : quote.getTags()) {
                all.computeIfAbsent(Hashtag.key(tag), k -> new TagStats(tag)).quotes++;
            }
            float[] vector = quote.getId() == null ? null : vectors.get(quote.getId());
            if (vector != null) {
                Set<String> keys = new HashSet<>();
                for (String tag : quote.getTags()) keys.add(Hashtag.key(tag));
                Candidate candidate = new Candidate(quote.getId(), vector, keys);
                model.withVectors.add(candidate);
                if (!keys.isEmpty()) model.candidates.add(candidate);
            }
        }
        for (Map.Entry<String, TagStats> entry : all.entrySet()) {
            model.tagQuotes.put(entry.getKey(), entry.getValue().quotes);
            model.tagSpellings.put(entry.getKey(), entry.getValue().spelling);
            if (entry.getValue().quotes >= MIN_QUOTES_PER_TAG) {
                model.tags.put(entry.getKey(), entry.getValue());
                entry.getValue().nameStems.addAll(QuoteTokens.nameStems(entry.getKey()));
            } else {
                model.smallTags.put(entry.getKey(),
                        new SmallTag(entry.getValue().spelling, QuoteTokens.nameStems(entry.getKey())));
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

    /** This model with other similar-quotes parameters, sharing the fitted counts (the eval's sweep). */
    HashtagSuggester withKnn(Knn knn) {
        return new HashtagSuggester(this, knn, names, zeroShot);
    }

    /**
     * This model with the small tags' name vectors (#64): {@code nameVectors} maps tag keys to the
     * normalized vector of the tag's name text ({@link NameText}); tags without one have no meaning
     * reason. Ranks every quote with a vector and without the tag against each name, once.
     */
    HashtagSuggester withNames(Map<String, float[]> nameVectors) {
        Map<String, NameRank> ranks = new HashMap<>();
        for (String key : smallTags.keySet()) {
            float[] name = nameVectors.get(key);
            if (name == null) continue;
            float[] similarities = new float[withVectors.size()];
            int n = 0;
            for (Candidate quote : withVectors) {
                if (!quote.tagKeys.contains(key)) similarities[n++] = dot(name, quote.vector);
            }
            ranks.put(key, new NameRank(name, sortedDescending(Arrays.copyOf(similarities, n))));
        }
        return new HashtagSuggester(this, knn, ranks, zeroShot);
    }

    /** {@code values}, sorted in place, highest first. */
    private static float[] sortedDescending(float[] values) {
        Arrays.sort(values);
        for (int i = 0, j = values.length - 1; i < j; i++, j--) {
            float swap = values[i];
            values[i] = values[j];
            values[j] = swap;
        }
        return values;
    }

    /** This model with other small-tag parameters (#64), sharing everything else (the eval's sweep). */
    HashtagSuggester withZeroShot(ZeroShot zeroShot) {
        return new HashtagSuggester(this, knn, names, zeroShot);
    }

    /**
     * The small tag {@code key} for a quote with {@code tokens} and {@code vector} (null: none):
     * its name in the quote, else closeness to its name; null when neither is a reason (#64).
     */
    private Suggestion smallSuggestion(String key, SmallTag small, Set<String> tokens, float[] vector) {
        if (zeroShot.stem) {
            for (String token : tokens) {
                if (small.nameStems.contains(token)) return new Suggestion(small.spelling, Evidence.smallNameMatch(token));
            }
        }
        if (zeroShot.meaning && vector != null) {
            NameRank rank = names.get(key);
            if (rank != null) {
                float closeness = dot(vector, rank.vector);
                if (closeness >= rank.cutoff(zeroShot)) {
                    return new Suggestion(small.spelling, Evidence.meaning(closeness));
                }
            }
        }
        return null;
    }

    /**
     * The {@link Knn#k} candidates most similar to {@code vector}, each at least
     * {@link Knn#minSimilarity}, never the quote {@code selfId}; none without a vector.
     */
    private Neighbours neighbours(float[] vector, Integer selfId) {
        if (vector == null || candidates.isEmpty()) return Neighbours.NONE;
        List<Candidate> found = new ArrayList<>();
        List<Float> similarities = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (selfId != null && selfId.equals(candidate.id)) continue;
            float similarity = dot(vector, candidate.vector);
            if (similarity < knn.minSimilarity) continue;
            int at = found.size();
            while (at > 0 && similarities.get(at - 1) < similarity) at--;
            if (at >= knn.k) continue;
            found.add(at, candidate);
            similarities.add(at, similarity);
            if (found.size() > knn.k) {
                found.remove(knn.k);
                similarities.remove(knn.k);
            }
        }
        return new Neighbours(found);
    }

    private static float dot(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) sum += a[i] * b[i];
        return sum;
    }

    /** The similar-quotes evidence for the tag {@code key}, or null when it isn't a reason. */
    private Evidence similarEvidence(Neighbours neighbours, String key) {
        int withTag = neighbours.withTag(key);
        if (withTag == 0 || withTag < knn.minWithTag) return null;
        return Evidence.similar(withTag, neighbours.quotes.size(), knn.belowCounted);
    }

    /**
     * What the editor is showing, the first subject the model judges (#70), judged against the full
     * model. Immutable; {@link #of} gives one without rejected tags, pinned tag or vector, and the
     * withers add them.
     */
    public static final class Draft {
        private final String text;
        private final String author;
        private final String source;
        private final Collection<String> tags;
        private final Collection<String> rejectedTags;
        private final String pinned;
        private final float[] vector;

        private Draft(String text, String author, String source, Collection<String> tags,
                      Collection<String> rejectedTags, String pinned, float[] vector) {
            this.text = text;
            this.author = author;
            this.source = source;
            this.tags = tags;
            this.rejectedTags = rejectedTags;
            this.pinned = pinned;
            this.vector = vector;
        }

        /** A draft with the form's text, author, source and the chips ({@code tags}; null is none). */
        public static Draft of(String text, String author, String source, Collection<String> tags) {
            return new Draft(text, author, source, tags, null, null, null);
        }

        /** This draft, never suggesting {@code rejectedTags} (the form's rejected tags; null is none). */
        public Draft rejecting(Collection<String> rejectedTags) {
            return new Draft(text, author, source, tags, rejectedTags, pinned, vector);
        }

        /**
         * This draft with {@code pinned} first whenever it is eligible, even when stronger tags would
         * push it out (#56: the tagging run for one tag); null pins nothing.
         */
        public Draft pinning(String pinned) {
            return new Draft(text, author, source, tags, rejectedTags, pinned, vector);
        }

        /** This draft with the text's normalized vector, for the similar-quotes reason (null: no such reason). */
        public Draft withVector(float[] vector) {
            return new Draft(text, author, source, tags, rejectedTags, pinned, vector);
        }
    }

    /**
     * Up to {@link #MAX_SUGGESTIONS} tags for the draft, best first; never one of its tags (which are
     * reasons for other tags) or rejected tags; its pinned tag first when eligible.
     */
    public List<String> suggest(Draft draft) {
        Set<String> tokens = QuoteTokens.of(draft.text, draft.author, draft.source, draft.tags);
        List<String> tags = new ArrayList<>();
        for (Suggestion suggestion : suggestions(tokens, neighbours(draft.vector, null), draft.vector,
                excluded(draft.tags, draft.rejectedTags), 0, draft.pinned)) {
            tags.add(suggestion.tag);
        }
        return tags;
    }

    /**
     * What {@link #suggest(Draft)} gives {@code quote}, a quote of the fitted collection, with its tags and
     * rejected tags, if it were fitted without it: up to {@link #MAX_SUGGESTIONS}, best first, each
     * with its strongest reason. Leaving the quote out lowers the counts of its own tokens (its
     * tags' tokens included) by one; it changes nothing else a suggestion depends on, since only
     * its own tags, which are never suggested for it, count it among their quotes.
     */
    public List<Suggestion> suggest(Quote quote) {
        Set<String> tokens = QuoteTokens.of(quote.getQuoteText(), quote.getAuthor(), quote.getSource(),
                quote.getTags());
        return suggestions(tokens, neighboursOf(quote), vectorOf(quote),
                excluded(quote.getTags(), quote.getRejectedTags()), 1, null);
    }

    private float[] vectorOf(Quote quote) {
        return quote.getId() == null ? null : vectors.get(quote.getId());
    }

    /**
     * {@code tag} for {@code quote}, a quote of the fitted collection, counted as in
     * {@link #suggest(Quote)} but whether or not stronger tags would push it out of the top
     * {@link #MAX_SUGGESTIONS} (#56): with its strongest reason, or null when it isn't eligible
     * (on too few quotes, no reason, one of the quote's tags or rejected tags).
     */
    public Suggestion suggestionFor(Quote quote, String tag) {
        String key = Hashtag.key(Hashtag.normalize(tag));
        TagStats stats = tags.get(key);
        SmallTag small = zeroShot.on() ? smallTags.get(key) : null;
        if ((stats == null && small == null)
                || excluded(quote.getTags(), quote.getRejectedTags()).contains(key)) return null;
        if (small != null) {
            return smallSuggestion(key, small,
                    QuoteTokens.of(quote.getQuoteText(), quote.getAuthor(), quote.getSource(), null), vectorOf(quote));
        }
        Set<String> tokens = QuoteTokens.of(quote.getQuoteText(), quote.getAuthor(), quote.getSource(),
                quote.getTags());
        return suggestion(key, stats, tokens, neighboursOf(quote), 1);
    }

    /** The neighbours of {@code quote}, a quote of the fitted collection; never itself. */
    private Neighbours neighboursOf(Quote quote) {
        Integer id = quote.getId();
        return neighbours(id == null ? null : vectors.get(id), id);
    }

    /** The keys of {@code existingTags} and {@code rejectedTags} (null is none). */
    private static Set<String> excluded(Collection<String> existingTags, Collection<String> rejectedTags) {
        Set<String> excluded = new HashSet<>();
        for (Collection<String> tags : Arrays.asList(existingTags, rejectedTags)) {
            if (tags == null) continue;
            for (String tag : tags) excluded.add(Hashtag.key(Hashtag.normalize(tag)));
        }
        return excluded;
    }

    /**
     * The best tags for {@code tokens}, none of {@code excluded}, with {@code self} quotes left out
     * of each token's count.
     */
    private List<Suggestion> suggestions(Set<String> tokens, Neighbours neighbours, float[] vector,
                                         Set<String> excluded, int self, String pinned) {
        List<Suggestion> ranked = new ArrayList<>();
        for (Map.Entry<String, TagStats> entry : tags.entrySet()) {
            if (excluded.contains(entry.getKey())) continue;
            Suggestion suggestion = suggestion(entry.getKey(), entry.getValue(), tokens, neighbours, self);
            if (suggestion != null) ranked.add(suggestion);
        }
        if (zeroShot.on()) {
            for (Map.Entry<String, SmallTag> entry : smallTags.entrySet()) {
                if (excluded.contains(entry.getKey())) continue;
                Suggestion suggestion = smallSuggestion(entry.getKey(), entry.getValue(), tokens, vector);
                if (suggestion != null) ranked.add(suggestion);
            }
        }
        ranked.sort((a, b) -> {
            int byStrength = byStrength(a.strongest, b.strongest);
            return byStrength != 0 ? byStrength : Hashtag.ORDER.compare(a.tag, b.tag);
        });
        if (pinned != null) {
            String pinnedKey = Hashtag.key(Hashtag.normalize(pinned));
            for (int i = 1; i < ranked.size(); i++) {
                if (Hashtag.key(ranked.get(i).tag).equals(pinnedKey)) {
                    ranked.add(0, ranked.remove(i));
                    break;
                }
            }
        }
        return new ArrayList<>(ranked.subList(0, Math.min(MAX_SUGGESTIONS, ranked.size())));
    }

    /** The tag {@code key} for {@code tokens} and {@code neighbours} with its strongest reason, or null when it has none. */
    private Suggestion suggestion(String key, TagStats stats, Set<String> tokens, Neighbours neighbours, int self) {
        Evidence best = similarEvidence(neighbours, key);
        for (String token : tokens) {
            if (!isReason(stats, token, self)) continue;
            Evidence evidence = evidence(stats, token, token, self);
            if (best == null || byStrength(evidence, best) < 0) best = evidence;
        }
        if (best == null) {
            String name = nameIn(stats, tokens);
            if (name != null) best = Evidence.nameMatch(name);
        }
        return best == null ? null : new Suggestion(stats.spelling, best);
    }

    /**
     * Why {@code tag} is suggested for the draft: its reason words, strongest first, shown as they are
     * written in the text, the author and the source when they are a reason, and those of the draft's
     * tags that are a reason, strongest first. Null when the tag can't be suggested at all (on too
     * few quotes).
     */
    public Explanation explain(Draft draft, String tag) {
        return explain(draft.text, draft.author, draft.source, draft.tags, tag, neighbours(draft.vector, null), 0);
    }

    /**
     * {@link #explain(Draft, String)} for {@code quote}, a quote of the fitted collection, counted as
     * if it were left out (like {@link #suggestionFor}): the "Suggested for" list's line (#56).
     */
    public Explanation explain(Quote quote, String tag) {
        return explain(quote.getQuoteText(), quote.getAuthor(), quote.getSource(), quote.getTags(), tag,
                neighboursOf(quote), 1);
    }

    private Explanation explain(String text, String author, String source, Collection<String> quoteTags,
                                String tag, Neighbours neighbours, int self) {
        String key = Hashtag.key(Hashtag.normalize(tag));
        TagStats stats = tags.get(key);
        if (stats == null) return null;
        Map<String, String> ownWords = new HashMap<>();
        QuoteTokens.forEachWord(text, ownWords::putIfAbsent);

        List<Evidence> words = new ArrayList<>();
        Set<String> wordTokens = QuoteTokens.of(text, null, null, null);
        for (String token : wordTokens) {
            if (isReason(stats, token, self)) {
                words.add(evidence(stats, token, ownWords.getOrDefault(token, displayWord(token)), self));
            }
        }
        words.sort(STRONGEST_FIRST);
        String authorToken = QuoteTokens.authorToken(author);
        Evidence authorEvidence = null;
        if (isReason(stats, authorToken, self)) authorEvidence = evidence(stats, authorToken, author.trim(), self);
        String sourceToken = QuoteTokens.sourceToken(source);
        Evidence sourceEvidence = null;
        if (isReason(stats, sourceToken, self)) sourceEvidence = evidence(stats, sourceToken, source.trim(), self);
        Set<String> tagTokens = QuoteTokens.of(null, null, null, quoteTags);
        List<Evidence> tagEvidence = new ArrayList<>();
        for (String token : tagTokens) {
            if (isTagReason(stats, token, self)) tagEvidence.add(evidence(stats, token, tagLabel(token), self));
        }
        tagEvidence.sort(STRONGEST_FIRST);
        Evidence similar = similarEvidence(neighbours, key);
        List<Integer> similarIds = new ArrayList<>();
        if (similar != null) {
            for (Candidate candidate : neighbours.quotes) {
                if (candidate.tagKeys.contains(key)) similarIds.add(candidate.id);
            }
        }
        return new Explanation(stats.spelling, words, authorEvidence, sourceEvidence, tagEvidence,
                nameIn(stats, wordTokens) != null, similar, similarIds);
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
            if (token.startsWith(QuoteTokens.TAG_PREFIX)) {
                if (isTagReason(stats, token)) tagEvidence.add(evidence(stats, token, tagLabel(token)));
            } else if (!isReason(stats, token)) {
                continue;
            } else if (token.startsWith(QuoteTokens.AUTHOR_PREFIX)) {
                authors.add(evidence(stats, token, authorNames.get(token)));
            } else if (token.startsWith(QuoteTokens.SOURCE_PREFIX)) {
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

    /** Whether the tag token is a reason for the tag; a tag is never a reason for itself. */
    private boolean isTagReason(TagStats stats, String token) {
        return isTagReason(stats, token, 0);
    }

    private boolean isTagReason(TagStats stats, String token, int self) {
        return !token.equals(QuoteTokens.TAG_PREFIX + Hashtag.key(stats.spelling)) && isReason(stats, token, self);
    }

    /** "#Death" for {@code tag:death}, in the collection's spelling. */
    private String tagLabel(String token) {
        String key = token.substring(QuoteTokens.TAG_PREFIX.length());
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
        if (together < minWithTag(token)) return false;
        return together * 100 >= MIN_SHARE_PERCENT * (tokenCounts.getOrDefault(token, 0) - self);
    }

    /** The quotes with both {@code token} and the tag that make the token a reason, by kind of token. */
    private static int minWithTag(String token) {
        if (token.startsWith(QuoteTokens.AUTHOR_PREFIX)) return MIN_AUTHOR_WITH_TAG;
        if (token.startsWith(QuoteTokens.SOURCE_PREFIX)) return MIN_SOURCE_WITH_TAG;
        if (token.startsWith(QuoteTokens.TAG_PREFIX)) return MIN_TAG_WITH_TAG;
        return MIN_WORD_WITH_TAG;
    }

}
