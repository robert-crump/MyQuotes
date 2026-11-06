package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
                if (!actual.contains(key)) continue;
                tagResult.hits++;
                result.hits++;
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
            return HashtagSuggester.fit(others).suggest(quote.getQuoteText(), quote.getAuthor(),
                    quote.getSource(), Collections.emptyList(), quote.getRejectedTags());
        };
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
