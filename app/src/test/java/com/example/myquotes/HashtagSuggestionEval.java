package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Measures hashtag suggestions on a collection (#59): for every quote, the suggester's top
 * {@link HashtagSuggester#MAX_SUGGESTIONS} are compared with the tags the quote really has.
 * Pure; the suggester is a parameter so later models and parameter sweeps plug in unchanged.
 * The suggester is expected to leave the quote out itself (see {@link #leaveOneOut}).
 * <p>
 * Over tagged quotes only (an untagged quote has no ground truth):
 * <ul>
 *   <li><b>precision@3</b>: suggested tags the quote has / all suggested tags</li>
 *   <li><b>recall@3, eligible</b>: the quote's tags that were suggested / its tags that can be
 *       suggested at all, i.e. on at least {@link HashtagSuggester#MIN_QUOTES_PER_TAG} quotes
 *       when the quote is left out</li>
 *   <li><b>recall@3, unrestricted</b>: the same over all its tags</li>
 * </ul>
 * Coverage (quotes with at least one suggestion) is counted for tagged and untagged quotes
 * separately, and precision and recall per tag.
 */
final class HashtagSuggestionEval {

    /** Counts for one tag; the ratios are computed from them. */
    static final class TagResult {
        final String tag;
        /** Tagged quotes the tag was suggested for. */
        int suggested;
        /** Of those, the quotes that have the tag. */
        int hits;
        /** Quotes that have the tag. */
        int quotes;
        /** Quotes that have the tag while it is eligible for them (left out, still on enough quotes). */
        int eligible;
        /** Hits among the eligible quotes (every hit is eligible for a model that respects the minimum). */
        int eligibleHits;

        TagResult(String tag) {
            this.tag = tag;
        }

        double precision() {
            return ratio(hits, suggested);
        }

        double recall() {
            return ratio(eligibleHits, eligible);
        }
    }

    static final class Result {
        int taggedQuotes;
        int untaggedQuotes;
        int taggedCovered;
        int untaggedCovered;
        int suggested;
        int hits;
        int tags;
        int eligibleTags;
        int eligibleHits;
        /** By tag key, sorted. */
        final Map<String, TagResult> perTag = new TreeMap<>();
        /**
         * #64: the quotes' tags by bucket of the tag's count on the other quotes ({@link #bucket}),
         * and how many of them were suggested.
         */
        final int[] bucketTags = new int[BUCKETS.length];
        final int[] bucketHits = new int[BUCKETS.length];
        /** Suggestions of small tags (on 1–4 other quotes) and how many the quote has. */
        int smallSuggested;
        int smallHits;

        double recallInBucket(int bucket) {
            return ratio(bucketHits[bucket], bucketTags[bucket]);
        }

        double smallPrecision() {
            return ratio(smallHits, smallSuggested);
        }

        double precision() {
            return ratio(hits, suggested);
        }

        double recallEligible() {
            return ratio(eligibleHits, eligibleTags);
        }

        double recallUnrestricted() {
            return ratio(hits, tags);
        }

        double coverageTagged() {
            return ratio(taggedCovered, taggedQuotes);
        }

        double coverageUntagged() {
            return ratio(untaggedCovered, untaggedQuotes);
        }
    }

    /** #64: tag-size buckets by the tag's count on the other quotes. */
    static final String[] BUCKETS = {"0", "1-4", "5-9", "10+"};
    static final int SMALL = 1;

    static int bucket(int onOtherQuotes) {
        if (onOtherQuotes <= 0) return 0;
        if (onOtherQuotes < HashtagSuggester.MIN_QUOTES_PER_TAG) return SMALL;
        return onOtherQuotes < 10 ? 2 : 3;
    }

    private HashtagSuggestionEval() {
    }

    /** Runs {@code suggester} on every quote and compares its top suggestions with the quote's tags. */
    static Result evaluate(List<Quote> quotes, Function<Quote, List<String>> suggester) {
        Map<String, Integer> quotesPerTag = new HashMap<>();
        for (Quote quote : quotes) {
            for (String tag : quote.getTags()) quotesPerTag.merge(Hashtag.key(tag), 1, Integer::sum);
        }
        Result result = new Result();
        for (Quote quote : quotes) {
            List<String> suggestions = suggester.apply(quote);
            List<String> top = suggestions.subList(0, Math.min(HashtagSuggester.MAX_SUGGESTIONS, suggestions.size()));
            boolean tagged = !quote.getTags().isEmpty();
            if (!tagged) {
                result.untaggedQuotes++;
                if (!top.isEmpty()) result.untaggedCovered++;
                continue;
            }
            result.taggedQuotes++;
            if (!top.isEmpty()) result.taggedCovered++;

            Set<String> actual = new HashSet<>();
            for (String tag : quote.getTags()) {
                String key = Hashtag.key(tag);
                actual.add(key);
                TagResult tagResult = tagResult(result, key, tag);
                tagResult.quotes++;
                result.tags++;
                result.bucketTags[bucket(quotesPerTag.get(key) - 1)]++;
                if (quotesPerTag.get(key) - 1 >= HashtagSuggester.MIN_QUOTES_PER_TAG) {
                    tagResult.eligible++;
                    result.eligibleTags++;
                }
            }
            Set<String> seen = new HashSet<>();
            for (String tag : top) {
                String key = Hashtag.key(tag);
                if (!seen.add(key)) continue;
                TagResult tagResult = tagResult(result, key, tag);
                tagResult.suggested++;
                result.suggested++;
                int others = quotesPerTag.getOrDefault(key, 0) - (actual.contains(key) ? 1 : 0);
                boolean small = bucket(others) == SMALL;
                if (small) result.smallSuggested++;
                if (!actual.contains(key)) continue;
                tagResult.hits++;
                result.hits++;
                result.bucketHits[bucket(others)]++;
                if (small) result.smallHits++;
                if (quotesPerTag.get(key) - 1 >= HashtagSuggester.MIN_QUOTES_PER_TAG) {
                    tagResult.eligibleHits++;
                    result.eligibleHits++;
                }
            }
        }
        return result;
    }

    /**
     * The current model, exactly as the editor runs it on a quote being added: fitted without the
     * quote, its tags hidden (no chips), its rejected tags left out. Refits once per quote, which
     * is fine for a collection of a few thousand quotes.
     */
    static Function<Quote, List<String>> leaveOneOut(List<Quote> quotes) {
        return quote -> {
            List<Quote> others = new ArrayList<>(quotes.size());
            for (Quote other : quotes) {
                if (other != quote) others.add(other);
            }
            return HashtagSuggester.fit(others).suggest(HashtagSuggester.Draft.of(quote.getQuoteText(), quote.getAuthor(), quote.getSource(), Collections.emptyList()).rejecting(quote.getRejectedTags()));
        };
    }

    /**
     * Suggestions from one leave-one-out pass (#61): the current model ({@link #baseline}) and the
     * similar-quotes reason with each parameter set. The model is fitted once per quote and shared by
     * every parameter set, so a sweep costs about as much as {@link #leaveOneOut}.
     */
    static final class Sweep {
        private final Map<Quote, List<String>> baseline = new IdentityHashMap<>();
        private final Map<HashtagSuggester.Knn, Map<Quote, List<String>>> byKnn = new LinkedHashMap<>();

        Function<Quote, List<String>> baseline() {
            return baseline::get;
        }

        Function<Quote, List<String>> with(HashtagSuggester.Knn knn) {
            return byKnn.get(knn)::get;
        }
    }

    /**
     * {@link #leaveOneOut} with quote vectors: the quote is left out of the fit, and its own vector
     * is the query. The baseline is the same fit without a query vector, i.e. the current model.
     */
    static Sweep sweep(List<Quote> quotes, Map<Integer, float[]> vectors, List<HashtagSuggester.Knn> configs) {
        Sweep sweep = new Sweep();
        for (HashtagSuggester.Knn knn : configs) sweep.byKnn.put(knn, new IdentityHashMap<>());
        for (Quote quote : quotes) {
            HashtagSuggester fitted = HashtagSuggester.fit(without(quotes, quote), vectors);
            float[] vector = quote.getId() == null ? null : vectors.get(quote.getId());
            sweep.baseline.put(quote, suggestAsNew(fitted, quote, null));
            for (HashtagSuggester.Knn knn : configs) {
                sweep.byKnn.get(knn).put(quote, suggestAsNew(fitted.withKnn(knn), quote, vector));
            }
        }
        return sweep;
    }

    /** #64: one small-tag variant, the name text its name vectors come from and its parameters. */
    static final class ZeroShotConfig {
        final HashtagSuggester.NameText text;
        final HashtagSuggester.ZeroShot zeroShot;

        ZeroShotConfig(HashtagSuggester.NameText text, HashtagSuggester.ZeroShot zeroShot) {
            this.text = text;
            this.zeroShot = zeroShot;
        }

        @Override
        public String toString() {
            return zeroShot.meaning ? text.name().toLowerCase(Locale.ROOT) + " " + zeroShot : zeroShot.toString();
        }
    }

    /**
     * Suggestions from one leave-one-out pass (#64): the #61 model with the quote's vector
     * ({@link Sweep#baseline}) and each small-tag variant on top of it. {@code nameVectors} maps
     * each name text to tag key → vector.
     */
    static Map<ZeroShotConfig, Map<Quote, List<String>>> zeroShotSweep(List<Quote> quotes,
            Map<Integer, float[]> vectors, Map<HashtagSuggester.NameText, Map<String, float[]>> nameVectors,
            List<ZeroShotConfig> configs, Map<Quote, List<String>> baselineOut) {
        Map<ZeroShotConfig, Map<Quote, List<String>>> out = new LinkedHashMap<>();
        for (ZeroShotConfig config : configs) out.put(config, new IdentityHashMap<>());
        for (Quote quote : quotes) {
            HashtagSuggester fitted = HashtagSuggester.fit(without(quotes, quote), vectors);
            float[] vector = quote.getId() == null ? null : vectors.get(quote.getId());
            baselineOut.put(quote, suggestAsNew(fitted, quote, vector));
            Map<HashtagSuggester.NameText, HashtagSuggester> named = new HashMap<>();
            for (ZeroShotConfig config : configs) {
                HashtagSuggester model = named.computeIfAbsent(config.text,
                        text -> fitted.withNames(nameVectors.get(text)));
                out.get(config).put(quote, suggestAsNew(model.withZeroShot(config.zeroShot), quote, vector));
            }
        }
        return out;
    }

    /**
     * #64, the per-tag "Suggested for" pass: for each quote q and each of its tags t that is small
     * without q (on 1–4 other quotes), t is hidden on q (q stays in the collection, as an older
     * quote the user hasn't tagged yet), the model is fitted, and t's pass is every quote
     * {@link HashtagSuggester#suggestionFor} gives t for. A hit is q in the pass. Precision counts only
     * q as right, so it understates (the spot-check judges the rest).
     */
    static final class PassResult {
        /** Hidden (quote, tag) pairs, and those found in the tag's pass. */
        int hidden;
        int found;
        /** Sum of the passes' sizes, and passes that were not empty. */
        int passQuotes;
        int nonEmpty;
        /** Every pass member that isn't the hidden quote, for the spot-check. */
        final List<PassMember> others = new ArrayList<>();

        double recall() {
            return ratio(found, hidden);
        }

        double precision() {
            return ratio(found, passQuotes);
        }

        double meanSize() {
            return ratio(passQuotes, hidden);
        }
    }

    /** A quote in a small tag's pass that doesn't have the tag, with the reason. */
    static final class PassMember {
        final Quote quote;
        final String tag;
        final HashtagSuggester.Evidence evidence;

        PassMember(Quote quote, String tag, HashtagSuggester.Evidence evidence) {
            this.quote = quote;
            this.tag = tag;
            this.evidence = evidence;
        }
    }

    static Map<ZeroShotConfig, PassResult> tagPasses(List<Quote> quotes, Map<Integer, float[]> vectors,
            Map<HashtagSuggester.NameText, Map<String, float[]>> nameVectors, List<ZeroShotConfig> configs) {
        Map<String, Integer> quotesPerTag = new HashMap<>();
        for (Quote quote : quotes) {
            for (String tag : quote.getTags()) quotesPerTag.merge(Hashtag.key(tag), 1, Integer::sum);
        }
        Map<ZeroShotConfig, PassResult> out = new LinkedHashMap<>();
        for (ZeroShotConfig config : configs) out.put(config, new PassResult());
        for (int i = 0; i < quotes.size(); i++) {
            Quote quote = quotes.get(i);
            for (String tag : quote.getTags()) {
                if (bucket(quotesPerTag.get(Hashtag.key(tag)) - 1) != SMALL) continue;
                List<Quote> hiddenOn = new ArrayList<>(quotes);
                Quote copy = withoutTag(quote, tag);
                hiddenOn.set(i, copy);
                HashtagSuggester fitted = HashtagSuggester.fit(hiddenOn, vectors);
                Map<HashtagSuggester.NameText, HashtagSuggester> named = new HashMap<>();
                for (ZeroShotConfig config : configs) {
                    HashtagSuggester model = named.computeIfAbsent(config.text,
                            text -> fitted.withNames(nameVectors.get(text))).withZeroShot(config.zeroShot);
                    PassResult result = out.get(config);
                    result.hidden++;
                    int size = 0;
                    for (int j = 0; j < hiddenOn.size(); j++) {
                        HashtagSuggester.Suggestion suggestion = model.suggestionFor(hiddenOn.get(j), tag);
                        if (suggestion == null) continue;
                        size++;
                        if (j == i) {
                            result.found++;
                        } else {
                            result.others.add(new PassMember(quotes.get(j), tag, suggestion.strongest));
                        }
                    }
                    result.passQuotes += size;
                    if (size > 0) result.nonEmpty++;
                }
            }
        }
        return out;
    }

    /** A copy of {@code quote} (same id, text, author, source, rejected tags) without {@code tag}. */
    static Quote withoutTag(Quote quote, String tag) {
        Quote copy = new Quote(quote.getId(), quote.getAuthor(), quote.getQuoteText(), quote.getSource());
        List<String> tags = new ArrayList<>(quote.getTags());
        tags.removeIf(t -> Hashtag.key(t).equals(Hashtag.key(tag)));
        copy.setTags(tags);
        copy.setRejectedTags(quote.getRejectedTags());
        return copy;
    }

    /** #64: per bucket recall@3, small-tag precision, and P@3 against the #61 model. */
    static String formatBuckets(Result baseline, Map<String, Result> results) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%-36s %7s %7s %8s %8s %8s %8s %13s %7s%n", "", "P@3", "dP",
                "R[1-4]", "R[5-9]", "R[10+]", "covUnt", "small P (n)", "dP<=5"));
        out.append(bucketLine("#61 model (baseline)", baseline, baseline));
        for (Map.Entry<String, Result> entry : results.entrySet()) {
            out.append(bucketLine(entry.getKey(), baseline, entry.getValue()));
        }
        out.append(String.format(Locale.ROOT, "Tags per bucket (by count on the other quotes): 0: %d, 1-4: %d, "
                        + "5-9: %d, 10+: %d%n", baseline.bucketTags[0], baseline.bucketTags[1], baseline.bucketTags[2],
                baseline.bucketTags[3]));
        return out.toString();
    }

    private static String bucketLine(String label, Result baseline, Result result) {
        double dP = result.precision() - baseline.precision();
        return String.format(Locale.ROOT, "%-36s %7s %7s %8s %8s %8s %8s %13s %7s%n", label,
                percent(result.precision()), points(dP), percent(result.recallInBucket(1)),
                percent(result.recallInBucket(2)), percent(result.recallInBucket(3)),
                percent(result.coverageUntagged()),
                percent(result.smallPrecision()) + " (" + result.smallSuggested + ")",
                result == baseline ? "" : dP >= -0.05 - 1e-9 ? "ok" : "FAIL");
    }

    /** #64: the per-tag passes, one line per variant. */
    static String formatPasses(Map<String, PassResult> results) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%-36s %8s %10s %10s %10s%n", "", "recall", "precision",
                "mean size", "non-empty"));
        for (Map.Entry<String, PassResult> entry : results.entrySet()) {
            PassResult r = entry.getValue();
            out.append(String.format(Locale.ROOT, "%-36s %8s %10s %10s %6d/%-4d%n", entry.getKey(),
                    percent(r.recall()), percent(r.precision()),
                    Double.isNaN(r.meanSize()) ? "-" : String.format(Locale.ROOT, "%.1f", r.meanSize()),
                    r.nonEmpty, r.hidden));
        }
        return out.toString();
    }

    static List<Quote> without(List<Quote> quotes, Quote quote) {
        List<Quote> others = new ArrayList<>(quotes.size());
        for (Quote other : quotes) {
            if (other != quote) others.add(other);
        }
        return others;
    }

    /** The editor on a new quote: no chips, the quote's rejected tags left out. */
    private static List<String> suggestAsNew(HashtagSuggester model, Quote quote, float[] vector) {
        return model.suggest(HashtagSuggester.Draft.of(quote.getQuoteText(), quote.getAuthor(), quote.getSource(), Collections.emptyList()).rejecting(quote.getRejectedTags()).withVector(vector));
    }

    /**
     * The #61 success bar against the baseline: recall@3 (eligible) up at least 5 points or coverage
     * of untagged quotes up at least 10 points, with precision@3 down at most 5 points.
     */
    static boolean passes(Result baseline, Result candidate) {
        double precision = candidate.precision() - baseline.precision();
        double recall = candidate.recallEligible() - baseline.recallEligible();
        double coverage = candidate.coverageUntagged() - baseline.coverageUntagged();
        return precision >= -0.05 - 1e-9 && (recall >= 0.05 - 1e-9 || coverage >= 0.10 - 1e-9);
    }

    /** One line per labelled result: the metrics, their change from {@code baseline}, and the bar. */
    static String formatComparison(Result baseline, Map<String, Result> results) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%-34s %7s %7s %7s %7s %7s %7s %7s %7s  %s%n", "", "P@3",
                "R@3", "covTag", "covUnt", "dP", "dR", "dCovTag", "dCovUnt", "bar"));
        out.append(comparisonLine("baseline (current model)", baseline, baseline, false));
        for (Map.Entry<String, Result> entry : results.entrySet()) {
            out.append(comparisonLine(entry.getKey(), baseline, entry.getValue(), true));
        }
        return out.toString();
    }

    private static String comparisonLine(String label, Result baseline, Result result, boolean withBar) {
        return String.format(Locale.ROOT, "%-34s %7s %7s %7s %7s %7s %7s %7s %7s  %s%n", label,
                percent(result.precision()), percent(result.recallEligible()), percent(result.coverageTagged()),
                percent(result.coverageUntagged()),
                points(result.precision() - baseline.precision()),
                points(result.recallEligible() - baseline.recallEligible()),
                points(result.coverageTagged() - baseline.coverageTagged()),
                points(result.coverageUntagged() - baseline.coverageUntagged()),
                withBar ? (passes(baseline, result) ? "PASS" : "-") : "");
    }

    /** Per tag (most frequent first): suggestions, hits, precision and recall before and after; changed tags only. */
    static String formatTagDeltas(Result baseline, Result candidate) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%-28s %6s %11s %9s %15s %15s%n", "Tag", "quotes", "suggested",
                "hits", "precision", "recall"));
        List<TagResult> tags = new ArrayList<>(candidate.perTag.values());
        for (Map.Entry<String, TagResult> entry : baseline.perTag.entrySet()) {
            if (!candidate.perTag.containsKey(entry.getKey())) tags.add(entry.getValue());
        }
        tags.sort((a, b) -> a.quotes != b.quotes ? b.quotes - a.quotes : a.tag.compareToIgnoreCase(b.tag));
        TagResult none = new TagResult("");
        for (TagResult tag : tags) {
            String key = Hashtag.key(tag.tag);
            TagResult before = baseline.perTag.getOrDefault(key, none);
            TagResult after = candidate.perTag.getOrDefault(key, none);
            if (before.suggested == after.suggested && before.hits == after.hits) continue;
            out.append(String.format(Locale.ROOT, "%-28s %6d %5d->%-5d %4d->%-4d %7s->%-7s %7s->%-7s%n",
                    Hashtag.display(tag.tag), Math.max(before.quotes, after.quotes), before.suggested,
                    after.suggested, before.hits, after.hits, percent(before.precision()),
                    percent(after.precision()), percent(before.recall()), percent(after.recall())));
        }
        return out.toString();
    }

    /** A tag in the candidate's top 3 for a quote but not in the baseline's, and not on the quote. */
    static final class Addition {
        final Quote quote;
        final String tag;

        Addition(Quote quote, String tag) {
            this.quote = quote;
            this.tag = tag;
        }
    }

    /**
     * Every {@link Addition}: the suggestions the candidate adds that the eval counts as wrong. Some
     * may be right and only missing from the quote, which is what the manual spot-check is for.
     */
    static List<Addition> additions(List<Quote> quotes, Function<Quote, List<String>> baseline,
                                    Function<Quote, List<String>> candidate) {
        List<Addition> additions = new ArrayList<>();
        for (Quote quote : quotes) {
            Set<String> before = keys(top(baseline.apply(quote)));
            Set<String> own = keys(quote.getTags());
            for (String tag : top(candidate.apply(quote))) {
                String key = Hashtag.key(tag);
                if (!before.contains(key) && !own.contains(key)) additions.add(new Addition(quote, tag));
            }
        }
        return additions;
    }

    private static List<String> top(List<String> suggestions) {
        return suggestions.subList(0, Math.min(HashtagSuggester.MAX_SUGGESTIONS, suggestions.size()));
    }

    private static Set<String> keys(List<String> tags) {
        Set<String> keys = new HashSet<>();
        for (String tag : tags) keys.add(Hashtag.key(tag));
        return keys;
    }

    private static String points(double delta) {
        return Double.isNaN(delta) ? "-" : String.format(Locale.ROOT, "%+.1f", delta * 100);
    }

    /** A plain-text report: overall numbers, then one line per tag (most frequent first). */
    static String format(Result result) {
        StringBuilder out = new StringBuilder();
        out.append("Hashtag suggestion eval (leave-one-out, top ").append(HashtagSuggester.MAX_SUGGESTIONS)
                .append(")\n\n");
        out.append(String.format(Locale.ROOT, "Tagged quotes:          %d%n", result.taggedQuotes));
        out.append(String.format(Locale.ROOT, "Untagged quotes:        %d%n", result.untaggedQuotes));
        out.append(String.format(Locale.ROOT, "precision@3:            %s  (%d of %d suggestions)%n",
                percent(result.precision()), result.hits, result.suggested));
        out.append(String.format(Locale.ROOT, "recall@3 (eligible):    %s  (%d of %d tags)%n",
                percent(result.recallEligible()), result.eligibleHits, result.eligibleTags));
        out.append(String.format(Locale.ROOT, "recall@3 (all tags):    %s  (%d of %d tags)%n",
                percent(result.recallUnrestricted()), result.hits, result.tags));
        out.append(String.format(Locale.ROOT, "coverage (tagged):      %s  (%d of %d quotes)%n",
                percent(result.coverageTagged()), result.taggedCovered, result.taggedQuotes));
        out.append(String.format(Locale.ROOT, "coverage (untagged):    %s  (%d of %d quotes)%n",
                percent(result.coverageUntagged()), result.untaggedCovered, result.untaggedQuotes));

        List<TagResult> tags = new ArrayList<>(result.perTag.values());
        tags.sort((a, b) -> a.quotes != b.quotes ? b.quotes - a.quotes : a.tag.compareToIgnoreCase(b.tag));
        out.append(String.format(Locale.ROOT, "%n%-28s %6s %8s %9s %5s %9s %7s%n",
                "Tag", "quotes", "eligible", "suggested", "hits", "precision", "recall"));
        for (TagResult tag : tags) {
            out.append(String.format(Locale.ROOT, "%-28s %6d %8d %9d %5d %9s %7s%n",
                    Hashtag.display(tag.tag), tag.quotes, tag.eligible, tag.suggested, tag.hits,
                    percent(tag.precision()), percent(tag.recall())));
        }
        return out.toString();
    }

    private static TagResult tagResult(Result result, String key, String spelling) {
        return result.perTag.computeIfAbsent(key, k -> new TagResult(spelling));
    }

    static double ratio(int part, int whole) {
        return whole == 0 ? Double.NaN : (double) part / whole;
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
}
