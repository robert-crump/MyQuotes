package com.example.myquotes;

import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

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
