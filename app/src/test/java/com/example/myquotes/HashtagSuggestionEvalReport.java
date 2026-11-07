package com.example.myquotes;

import static org.junit.Assume.assumeTrue;

import com.example.myquotes.embedding.QuoteVectors;
import com.example.myquotes.embedding.TextEmbeddingModel;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Prints the hashtag suggestion eval (#59) for the user's real collection: a Backup Document
 * exported from the phone and copied to {@code app/src/test/resources/private/backup.json}
 * (gitignored), or the file named by the {@code hashtagEval.backup} system property or the
 * {@code HASHTAG_EVAL_BACKUP} environment variable. Skipped when there is no such file, so CI and
 * fresh clones stay green. The report contains no quote text, but the backup does: never commit it.
 * <p>
 * {@code ./gradlew testDebugUnitTest --tests '*HashtagSuggestionEvalReport*'} prints the report and
 * writes it to {@code app/build/reports/hashtag-eval.txt}.
 */
public class HashtagSuggestionEvalReport {
    private static final String DEFAULT_PATH = "src/test/resources/private/backup.json";

    @Test
    public void currentModel() throws Exception {
        File backup = backupFile();
        assumeTrue("No private backup at " + backup.getAbsolutePath() + "; eval skipped", backup.isFile());

        List<Quote> quotes = BackupDocument.decode(
                new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8)).quotes;
        long start = System.nanoTime();
        HashtagSuggestionEval.Result result = HashtagSuggestionEval.evaluate(quotes,
                HashtagSuggestionEval.leaveOneOut(quotes));
        long millis = (System.nanoTime() - start) / 1_000_000;

        String report = HashtagSuggestionEval.format(result) + "\n(" + quotes.size() + " quotes, " + millis + " ms)\n";
        System.out.println(report);
        write(new File("build/reports/hashtag-eval.txt"), report);
    }

    /**
     * The similar-quotes bake-off (#61): for each candidate embedding model, the baseline against
     * the similar-quotes reason over a k/m/floor/ranking sweep, the per-tag changes of the best
     * parameter set, and 20 random suggestions it adds that the eval counts as wrong, for the manual
     * spot-check. Numbers go to {@code build/reports/hashtag-eval-knn.txt}; the spot-check lists
     * quote text and goes to {@code build/reports/hashtag-spotcheck-<model>.txt} only (gitignored).
     * Vectors are cached in {@code build/embedding-cache}.
     */
    @Test
    public void similarQuotesBakeOff() throws Exception {
        File backup = backupFile();
        assumeTrue("No private backup at " + backup.getAbsolutePath() + "; eval skipped", backup.isFile());
        List<Quote> quotes = BackupDocument.decode(
                new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8)).quotes;

        StringBuilder report = new StringBuilder("Similar-quotes bake-off (#61), leave-one-out, top 3, "
                + quotes.size() + " quotes\n");
        for (TextEmbeddingModel model : new TextEmbeddingModel[]{TextEmbeddingModel.MULTILINGUAL_E5_SMALL,
                TextEmbeddingModel.PARAPHRASE_MULTILINGUAL_MINILM_L12}) {
            long start = System.nanoTime();
            Map<Integer, float[]> vectors = QuoteVectors.load(model, quotes, new File("build/embedding-cache"));
            long embedMillis = (System.nanoTime() - start) / 1_000_000;
            List<HashtagSuggester.Knn> configs = configs(model);

            start = System.nanoTime();
            HashtagSuggestionEval.Sweep sweep = HashtagSuggestionEval.sweep(quotes, vectors, configs);
            long sweepMillis = (System.nanoTime() - start) / 1_000_000;
            HashtagSuggestionEval.Result baseline = HashtagSuggestionEval.evaluate(quotes, sweep.baseline());
            Map<HashtagSuggester.Knn, HashtagSuggestionEval.Result> results = new LinkedHashMap<>();
            for (HashtagSuggester.Knn knn : configs) {
                results.put(knn, HashtagSuggestionEval.evaluate(quotes, sweep.with(knn)));
            }
            HashtagSuggester.Knn best = best(baseline, results);

            report.append("\n=== ").append(model.id()).append(" ===\n");
            report.append(String.format(Locale.ROOT, "Cut off at %d tokens: %d of %d quotes%n", model.maxTokens,
                    QuoteVectors.cutOff(model, quotes), quotes.size()));
            report.append(String.format(Locale.ROOT, "Vectors: %d ms; sweep of %d parameter sets: %d ms%n",
                    embedMillis, configs.size(), sweepMillis));
            report.append(String.format(Locale.ROOT, "Neighbour similarity: %s%n", similarityStats(quotes, vectors)));

            report.append("\nDecided k=10 m=3, by floor:\n");
            Map<String, HashtagSuggestionEval.Result> decided = new LinkedHashMap<>();
            for (Map.Entry<HashtagSuggester.Knn, HashtagSuggestionEval.Result> entry : results.entrySet()) {
                HashtagSuggester.Knn knn = entry.getKey();
                if (knn.k == HashtagSuggester.KNN_K && knn.minWithTag == HashtagSuggester.KNN_MIN_WITH_TAG) {
                    decided.put(knn.toString(), entry.getValue());
                }
            }
            report.append(HashtagSuggestionEval.formatComparison(baseline, decided));

            report.append("\nLong quotes chunked (sentence-packed pieces, token-weighted mean) vs cut off, k=10 m=3:\n");
            Map<Integer, float[]> chunkedVectors = QuoteVectors.load(model, quotes, new File("build/embedding-cache"),
                    true);
            List<HashtagSuggester.Knn> decidedConfigs = new ArrayList<>();
            for (HashtagSuggester.Knn knn : configs) {
                if (knn.k == HashtagSuggester.KNN_K && knn.minWithTag == HashtagSuggester.KNN_MIN_WITH_TAG
                        && !knn.belowCounted) {
                    decidedConfigs.add(knn);
                }
            }
            HashtagSuggestionEval.Sweep chunkedSweep = HashtagSuggestionEval.sweep(quotes, chunkedVectors,
                    decidedConfigs);
            Map<String, HashtagSuggestionEval.Result> chunkedRows = new LinkedHashMap<>();
            for (HashtagSuggester.Knn knn : decidedConfigs) {
                chunkedRows.put("cut off  " + knn, results.get(knn));
                chunkedRows.put("chunked  " + knn, HashtagSuggestionEval.evaluate(quotes, chunkedSweep.with(knn)));
            }
            report.append(HashtagSuggestionEval.formatComparison(baseline, chunkedRows));

            report.append("\nWhole sweep, best first (passing the bar, then P@3 + R@3):\n");
            List<HashtagSuggester.Knn> ranked = new ArrayList<>(configs);
            ranked.sort((a, b) -> compare(baseline, results.get(b), results.get(a)));
            Map<String, HashtagSuggestionEval.Result> all = new LinkedHashMap<>();
            for (HashtagSuggester.Knn knn : ranked) all.put(knn.toString(), results.get(knn));
            report.append(HashtagSuggestionEval.formatComparison(baseline, all));

            report.append("\nPer-tag changes, ").append(best).append(":\n");
            report.append(HashtagSuggestionEval.formatTagDeltas(baseline, results.get(best)));

            List<HashtagSuggestionEval.Addition> additions = HashtagSuggestionEval.additions(quotes,
                    sweep.baseline(), sweep.with(best));
            int onUntagged = 0;
            for (HashtagSuggestionEval.Addition addition : additions) {
                if (addition.quote.getTags().isEmpty()) onUntagged++;
            }
            report.append(String.format(Locale.ROOT,
                    "%nAdded suggestions counted as wrong (%s): %d (%d on tagged quotes, %d on untagged)%n",
                    best, additions.size(), additions.size() - onUntagged, onUntagged));
            write(new File("build/reports/hashtag-spotcheck-" + model.name + ".txt"),
                    spotCheck(model, best, quotes, vectors, additions));
        }
        System.out.println(report);
        write(new File("build/reports/hashtag-eval-knn.txt"), report.toString());
    }

    /**
     * The zero-shot reason for small tags (#64), on top of the #61 model (MiniLM, decided k/m/floor):
     * per tag-size bucket, recall@3 and P@3 against it; the per-tag "Suggested for" passes; and two
     * spot-check lists (20 stem matches, 20 meaning matches of the best variant) that go to
     * {@code build/reports/hashtag-spotcheck-zeroshot.txt} only (quote text, gitignored). Numbers go to
     * {@code build/reports/hashtag-eval-zeroshot.txt}.
     */
    @Test
    public void zeroShotReason() throws Exception {
        File backup = backupFile();
        assumeTrue("No private backup at " + backup.getAbsolutePath() + "; eval skipped", backup.isFile());
        List<Quote> quotes = BackupDocument.decode(
                new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8)).quotes;
        TextEmbeddingModel model = TextEmbeddingModel.PARAPHRASE_MULTILINGUAL_MINILM_L12;
        File cache = new File("build/embedding-cache");
        Map<Integer, float[]> vectors = QuoteVectors.load(model, quotes, cache);

        Map<String, String> spellings = new java.util.TreeMap<>();
        for (Quote quote : quotes) {
            for (String tag : quote.getTags()) spellings.putIfAbsent(Hashtag.key(tag), tag);
        }
        Map<HashtagSuggester.NameText, Map<String, float[]>> nameVectors = new java.util.EnumMap<>(
                HashtagSuggester.NameText.class);
        for (HashtagSuggester.NameText text : HashtagSuggester.NameText.values()) {
            List<String> texts = new ArrayList<>();
            for (String spelling : spellings.values()) texts.add(text.of(spelling));
            Map<String, float[]> byText = QuoteVectors.loadTexts(model, texts, cache);
            Map<String, float[]> byKey = new HashMap<>();
            for (Map.Entry<String, String> entry : spellings.entrySet()) {
                byKey.put(entry.getKey(), byText.get(text.of(entry.getValue())));
            }
            nameVectors.put(text, byKey);
        }

        StringBuilder report = new StringBuilder("Zero-shot reason for small tags (#64), " + model.id()
                + ", leave-one-out, " + quotes.size() + " quotes\n");
        for (HashtagSuggester.NameText text : HashtagSuggester.NameText.values()) {
            report.append(String.format(Locale.ROOT, "Name-to-quote cosine, %s (e.g. \"%s\"): %s%n",
                    text.name().toLowerCase(Locale.ROOT), text.of("LifeWisdom"),
                    nameStats(quotes, vectors, nameVectors.get(text))));
        }

        List<HashtagSuggestionEval.ZeroShotConfig> configs = zeroShotConfigs();
        long start = System.nanoTime();
        Map<Quote, List<String>> baselineSuggestions = new java.util.IdentityHashMap<>();
        Map<HashtagSuggestionEval.ZeroShotConfig, Map<Quote, List<String>>> sweep =
                HashtagSuggestionEval.zeroShotSweep(quotes, vectors, nameVectors, configs, baselineSuggestions);
        HashtagSuggestionEval.Result baseline = HashtagSuggestionEval.evaluate(quotes, baselineSuggestions::get);
        Map<HashtagSuggestionEval.ZeroShotConfig, HashtagSuggestionEval.Result> results = new LinkedHashMap<>();
        for (HashtagSuggestionEval.ZeroShotConfig config : configs) {
            results.put(config, HashtagSuggestionEval.evaluate(quotes, sweep.get(config)::get));
        }
        long sweepMillis = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        Map<HashtagSuggestionEval.ZeroShotConfig, HashtagSuggestionEval.PassResult> passes =
                HashtagSuggestionEval.tagPasses(quotes, vectors, nameVectors, configs);
        long passMillis = (System.nanoTime() - start) / 1_000_000;
        report.append(String.format(Locale.ROOT, "Top-3 sweep: %d ms; per-tag passes: %d ms; %d variants%n",
                sweepMillis, passMillis, configs.size()));

        report.append("\nTop 3 per quote (editor), recall by the tag's count on the other quotes:\n");
        Map<String, HashtagSuggestionEval.Result> rows = new LinkedHashMap<>();
        for (HashtagSuggestionEval.ZeroShotConfig config : configs) rows.put(config.toString(), results.get(config));
        report.append(HashtagSuggestionEval.formatBuckets(baseline, rows));

        report.append("\nPer-tag \"Suggested for\" pass, small tags (one of its quotes hidden; precision counts "
                + "only that quote as right):\n");
        Map<String, HashtagSuggestionEval.PassResult> passRows = new LinkedHashMap<>();
        for (HashtagSuggestionEval.ZeroShotConfig config : configs) passRows.put(config.toString(), passes.get(config));
        report.append(HashtagSuggestionEval.formatPasses(passRows));

        HashtagSuggestionEval.ZeroShotConfig stem = configs.get(0);
        HashtagSuggestionEval.ZeroShotConfig best = null;
        for (HashtagSuggestionEval.ZeroShotConfig config : configs) {
            if (!config.zeroShot.meaning) continue;
            if (results.get(config).precision() - baseline.precision() < -0.05 - 1e-9) continue;
            if (best == null || passScore(passes.get(config)) > passScore(passes.get(best))) best = config;
        }
        report.append("\nBest meaning variant passing the P@3 guard (by F1 of the pass): ")
                .append(best == null ? "none" : best.toString()).append('\n');
        if (best != null) {
            HashtagSuggestionEval.ZeroShotConfig other = null;
            for (HashtagSuggestionEval.ZeroShotConfig config : configs) {
                if (config.zeroShot.meaning && config.zeroShot.stem != best.zeroShot.stem && config.text == best.text
                        && config.zeroShot.minSimilarity == best.zeroShot.minSimilarity
                        && config.zeroShot.topN == best.zeroShot.topN) {
                    other = config;
                }
            }
            report.append("Same parameters, ").append(best.zeroShot.stem ? "meaning only: " : "with the stem: ")
                    .append(other).append('\n');
            report.append("\nPer-tag changes (top 3), ").append(best).append(":\n");
            report.append(HashtagSuggestionEval.formatTagDeltas(baseline, results.get(best)));
        }

        StringBuilder spot = new StringBuilder("Spot-check (#64): " + model.id() + "\n"
                + "Quotes in a small tag's \"Suggested for\" pass that don't have the tag (found while one of the "
                + "tag's quotes was hidden). For each: would you want this tag on the quote? (yes / no / maybe)\n"
                + "Gate: at least 10 of 20 yes, per list.\nPRIVATE: contains quote text. Never commit.\n");
        spot.append("\n=== A. Stem: the quote contains the small tag's name (").append(stem).append(") ===\n");
        spot.append(spotList(quotes, passes.get(stem).others, false));
        if (best != null) {
            spot.append("\n=== B. Meaning: close to the small tag's name (").append(best).append(") ===\n");
            spot.append(spotList(quotes, passes.get(best).others, true));
        }
        write(new File("build/reports/hashtag-spotcheck-zeroshot.txt"), spot.toString());

        System.out.println(report);
        write(new File("build/reports/hashtag-eval-zeroshot.txt"), report.toString());
    }

    /** Stem only, then name text × floor × N, meaning only and with the stem. */
    private static List<HashtagSuggestionEval.ZeroShotConfig> zeroShotConfigs() {
        List<HashtagSuggestionEval.ZeroShotConfig> configs = new ArrayList<>();
        configs.add(new HashtagSuggestionEval.ZeroShotConfig(HashtagSuggester.NameText.BARE,
                new HashtagSuggester.ZeroShot(true, false, 0f, 0)));
        for (boolean withStem : new boolean[]{false, true}) {
            for (HashtagSuggester.NameText text : HashtagSuggester.NameText.values()) {
                for (float floor : ZERO_SHOT_FLOORS) {
                    for (int n : new int[]{5, 10, 20}) {
                        configs.add(new HashtagSuggestionEval.ZeroShotConfig(text,
                                new HashtagSuggester.ZeroShot(withStem, true, floor, n)));
                    }
                }
            }
        }
        return configs;
    }

    private static final float[] ZERO_SHOT_FLOORS = {0f, 0.30f, 0.40f, 0.45f, 0.50f};

    /** F1 of the pass's recall and (understated) precision. */
    private static double passScore(HashtagSuggestionEval.PassResult pass) {
        double r = pass.recall();
        double p = pass.precision();
        return Double.isNaN(r) || Double.isNaN(p) || r + p == 0 ? 0 : 2 * r * p / (r + p);
    }

    /** Percentiles of each tag name's cosine to its closest and 10th closest quote, and to all quotes. */
    private static String nameStats(List<Quote> quotes, Map<Integer, float[]> vectors, Map<String, float[]> names) {
        List<Float> first = new ArrayList<>();
        List<Float> tenth = new ArrayList<>();
        List<Float> all = new ArrayList<>();
        for (float[] name : names.values()) {
            List<Float> sims = new ArrayList<>();
            for (Quote quote : quotes) {
                float[] v = vectors.get(quote.getId());
                if (v == null) continue;
                float dot = 0;
                for (int i = 0; i < v.length; i++) dot += v[i] * name[i];
                sims.add(dot);
            }
            all.addAll(sims);
            sims.sort(Collections.reverseOrder());
            first.add(sims.get(0));
            tenth.add(sims.get(Math.min(9, sims.size() - 1)));
        }
        return "closest " + percentiles(first) + "; 10th closest " + percentiles(tenth) + "; all "
                + percentiles(all);
    }

    /** 20 random pass members (fixed seed, one per quote and tag) of the wanted kind, with the tag's own quotes. */
    private static String spotList(List<Quote> quotes, List<HashtagSuggestionEval.PassMember> members,
                                   boolean meaning) {
        Map<String, HashtagSuggestionEval.PassMember> unique = new LinkedHashMap<>();
        for (HashtagSuggestionEval.PassMember member : members) {
            if (member.evidence.meaning != meaning) continue;
            unique.putIfAbsent(member.quote.getId() + "|" + Hashtag.key(member.tag), member);
        }
        List<HashtagSuggestionEval.PassMember> sample = new ArrayList<>(unique.values());
        Collections.shuffle(sample, new Random(64));
        sample = sample.subList(0, Math.min(20, sample.size()));
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "(%d distinct candidates)%n", unique.size()));
        int n = 0;
        for (HashtagSuggestionEval.PassMember member : sample) {
            Quote quote = member.quote;
            out.append(String.format(Locale.ROOT, "%n%2d. \"%s\" (%s)%n", ++n, start(quote.getQuoteText(), 110),
                    quote.getAuthor()));
            out.append("    has: ").append(quote.getTags().isEmpty() ? "(no hashtags)" : tags(quote.getTags()))
                    .append('\n');
            out.append("    adds: ").append(Hashtag.display(member.tag)).append("   (")
                    .append(member.evidence.meaning ? String.format(Locale.ROOT, "cosine %.2f", member.evidence.closeness)
                            : "word \"" + member.evidence.label + "\"")
                    .append(")\n");
            int shown = 0;
            for (Quote other : quotes) {
                if (shown == 2 || other == quote) continue;
                for (String tag : other.getTags()) {
                    if (Hashtag.key(tag).equals(Hashtag.key(member.tag))) {
                        out.append("      tagged: \"").append(start(other.getQuoteText(), 90)).append("\"\n");
                        shown++;
                        break;
                    }
                }
            }
            out.append("    verdict: \n");
        }
        return out.toString();
    }

    /** k, m, floor and ranking; the floors span each model's cosine range (e5's is narrow, see #60). */
    private static List<HashtagSuggester.Knn> configs(TextEmbeddingModel model) {
        float[] floors = model == TextEmbeddingModel.MULTILINGUAL_E5_SMALL
                ? new float[]{0f, 0.80f, 0.82f, 0.84f, 0.86f, 0.88f}
                : new float[]{0f, 0.30f, 0.40f, 0.50f, 0.60f, 0.70f};
        List<HashtagSuggester.Knn> configs = new ArrayList<>();
        for (boolean below : new boolean[]{false, true}) {
            for (int k : new int[]{5, 10, 20}) {
                for (int m : new int[]{2, 3, 4}) {
                    if (m > k) continue;
                    for (float floor : floors) configs.add(new HashtagSuggester.Knn(k, m, floor, below));
                }
            }
        }
        return configs;
    }

    /** Passing the bar first, then higher precision@3 + recall@3, then higher recall@3. */
    private static int compare(HashtagSuggestionEval.Result baseline, HashtagSuggestionEval.Result a,
                               HashtagSuggestionEval.Result b) {
        boolean passA = HashtagSuggestionEval.passes(baseline, a);
        boolean passB = HashtagSuggestionEval.passes(baseline, b);
        if (passA != passB) return passA ? 1 : -1;
        int bySum = Double.compare(a.recallEligible() + a.precision(), b.recallEligible() + b.precision());
        if (bySum != 0) return bySum;
        int byRecall = Double.compare(a.recallEligible(), b.recallEligible());
        return byRecall != 0 ? byRecall : Double.compare(a.precision(), b.precision());
    }

    private static HashtagSuggester.Knn best(HashtagSuggestionEval.Result baseline,
                                             Map<HashtagSuggester.Knn, HashtagSuggestionEval.Result> results) {
        HashtagSuggester.Knn best = null;
        for (Map.Entry<HashtagSuggester.Knn, HashtagSuggestionEval.Result> entry : results.entrySet()) {
            if (best == null || compare(baseline, entry.getValue(), results.get(best)) > 0) best = entry.getKey();
        }
        return best;
    }

    /** Percentiles of each quote's 1st and 10th most similar other quote, to place the floors. */
    private static String similarityStats(List<Quote> quotes, Map<Integer, float[]> vectors) {
        List<Float> first = new ArrayList<>();
        List<Float> tenth = new ArrayList<>();
        for (Quote quote : quotes) {
            float[] v = vectors.get(quote.getId());
            if (v == null) continue;
            List<Float> sims = new ArrayList<>();
            for (Quote other : quotes) {
                float[] w = vectors.get(other.getId());
                if (other == quote || w == null) continue;
                float dot = 0;
                for (int i = 0; i < v.length; i++) dot += v[i] * w[i];
                sims.add(dot);
            }
            sims.sort(Collections.reverseOrder());
            first.add(sims.get(0));
            tenth.add(sims.get(Math.min(9, sims.size() - 1)));
        }
        return "nearest " + percentiles(first) + "; 10th nearest " + percentiles(tenth);
    }

    private static String percentiles(List<Float> values) {
        Collections.sort(values);
        return String.format(Locale.ROOT, "p10 %.2f, median %.2f, p90 %.2f", values.get(values.size() / 10),
                values.get(values.size() / 2), values.get(values.size() * 9 / 10));
    }

    /** 20 random additions (fixed seed) with the evidence and the 3 closest neighbours that have the tag. */
    private static String spotCheck(TextEmbeddingModel model, HashtagSuggester.Knn knn, List<Quote> quotes,
                                    Map<Integer, float[]> vectors, List<HashtagSuggestionEval.Addition> additions) {
        Map<Integer, Quote> byId = new HashMap<>();
        for (Quote quote : quotes) byId.put(quote.getId(), quote);
        List<HashtagSuggestionEval.Addition> sample = new ArrayList<>(additions);
        Collections.shuffle(sample, new Random(61));
        sample = sample.subList(0, Math.min(20, sample.size()));

        StringBuilder out = new StringBuilder("Spot-check (#61): " + model.id() + ", " + knn + "\n"
                + "Suggestions the similar-quotes reason adds that the quote does not have. "
                + "For each: would you want this tag on the quote? (yes / no / maybe)\n"
                + "PRIVATE: contains quote text. Never commit.\n");
        int n = 0;
        for (HashtagSuggestionEval.Addition addition : sample) {
            Quote quote = addition.quote;
            HashtagSuggester fitted = HashtagSuggester.fit(HashtagSuggestionEval.without(quotes, quote), vectors, knn);
            HashtagSuggester.Explanation why = fitted.explain(HashtagSuggester.Draft.of(quote.getQuoteText(), quote.getAuthor(), quote.getSource(), Collections.emptyList()).withVector(vectors.get(quote.getId())), addition.tag);
            out.append(String.format(Locale.ROOT, "%n%2d. \"%s\" (%s)%n", ++n, start(quote.getQuoteText(), 110),
                    quote.getAuthor()));
            out.append("    has: ").append(quote.getTags().isEmpty() ? "(no hashtags)" : tags(quote.getTags()))
                    .append('\n');
            out.append("    adds: ").append(Hashtag.display(addition.tag));
            if (why != null && why.similar != null) {
                out.append(String.format(Locale.ROOT, "   (%d of %d similar quotes)", why.similar.withTag,
                        why.similar.total));
            }
            out.append('\n');
            if (why != null) {
                for (int i = 0; i < Math.min(3, why.similarQuoteIds.size()); i++) {
                    Quote neighbour = byId.get(why.similarQuoteIds.get(i));
                    out.append("      ~ \"").append(start(neighbour.getQuoteText(), 90)).append("\"\n");
                }
            }
            out.append("    verdict: \n");
        }
        return out.toString();
    }

    private static String start(String text, int length) {
        String line = text.replaceAll("\\s+", " ").trim();
        return line.length() <= length ? line : line.substring(0, length) + "...";
    }

    private static String tags(List<String> tags) {
        List<String> shown = new ArrayList<>();
        for (String tag : tags) shown.add(Hashtag.display(tag));
        return String.join(" ", shown);
    }

    private static File backupFile() {
        String path = System.getProperty("hashtagEval.backup");
        if (path == null || path.isEmpty()) path = System.getenv("HASHTAG_EVAL_BACKUP");
        return new File(path == null || path.isEmpty() ? DEFAULT_PATH : path);
    }

    private static void write(File file, String text) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
