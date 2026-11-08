package studio.phaseshift.metatron.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the Unsloth training corpus, and separately verifies one.
 * <p>
 * The generator reflects the test suite, but a test's intention is not always {@code a == b}
 * (a serialization round trip, a rejected write, a peer layout). A corpus row therefore claims
 * only one thing — "evaluating {@code input} yields {@code output}" — and that claim is checked
 * here by evaluating it in the VM, independently of how the row was produced.
 * <p>
 * Each row carries its {@code source} ({@code Class.method}), so failures group into a worklist:
 * a method whose rows all fail needs an explicit {@code @Training} template (or
 * {@code @Training.SkipTraining}); a method whose rows all pass needs nothing.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class UnslothTrainingDatasetExtractorTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = new GraphittyLogger(UnslothTrainingDatasetExtractorTest.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NO_SOURCE = "(no source)";

    private static Path datasetPath() {
        return Paths.get(System.getProperty("mtron.training.dataset",
                Paths.get(System.getProperty("user.dir"), ".metatron/skills/mtron/assets", "mtron_training_dataset.jsonl").toString()));
    }

    /**
     * Rewrites the corpus. Deliberately on demand: the docs build owns this artifact and it is
     * regenerated before a training run, not on every {@code mvn test}.
     */
    @Disabled("regeneration is on demand; see docs/skills/mtron/references/unsloth-training-mtron.md")
    @Test
    public void runExtractor() {
        UnslothTrainingDatasetExtractor.main(new String[]{});
    }

    /**
     * Re-derives every row's answer from its own prompt. Rows that do not verify are reported,
     * never silently kept: a row that teaches a falsehood is worse than a missing row.
     */
    @Test
    public void verifyExtractedCorpus() throws IOException {
        final Path dataset = datasetPath();
        // -Dmtron.training.regenerate=true rewrites the corpus first, so a run always verifies
        // the artifact this commit produces rather than a stale one.
        if (Boolean.getBoolean("mtron.training.regenerate"))
            UnslothTrainingDatasetExtractor.main(new String[]{});
        assumeTrue(Files.exists(dataset), "no corpus to verify: " + dataset);

        int ok = 0;
        int mismatch = 0;
        int error = 0;
        int notExpression = 0;
        int knowledge = 0;
        int writes = 0;
        final List<String> failures = new ArrayList<>();
        final List<String> verifiedLines = new ArrayList<>();
        final List<String> verifiedSources = new ArrayList<>();
        // source -> [verified, failed], insertion ordered so the report is stable
        final Map<String, int[]> bySource = new LinkedHashMap<>();

        int index = 0;
        for (final String line : Files.readAllLines(dataset, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            index++;

            final JsonObject json;
            try {
                json = JsonParser.parseString(line).getAsJsonObject();
            } catch (final RuntimeException e) {
                error++;
                record(bySource, NO_SOURCE, false);
                failures.add(String.format("#%d [%s] malformed json: %s", index, NO_SOURCE, e.getMessage()));
                continue;
            }

            final String input = json.get("input").getAsString();
            final String expected = json.get("output").getAsString();
            final String source = json.has("source") ? json.get("source").getAsString() : NO_SOURCE;
            // Built-in meta/sugar/reference rows are prose answers, not expression evaluations;
            // evaluating their prompt as mtron would be a category error, not a failure.
            if (NO_SOURCE.equals(source)) {
                knowledge++;
                continue;
            }
            if (input.contains("->")) writes++;

            final Verdict verdict = verify(input, expected);
            record(bySource, source, verdict.kind() == Kind.OK);
            switch (verdict.kind()) {
                case OK -> {
                    ok++;
                    verifiedLines.add(line);
                    verifiedSources.add(source);
                }
                case NOT_AN_EXPRESSION -> {
                    notExpression++;
                    failures.add(String.format("#%d [%s] not-an-expression  %s  => expected %s", index, source, input, expected));
                }
                case MISMATCH -> {
                    mismatch++;
                    failures.add(String.format("#%d [%s] mismatch  %s  => expected %s, got %s", index, source, input, expected, verdict.detail()));
                }
                case ERROR -> {
                    error++;
                    failures.add(String.format("#%d [%s] error     %s  => expected %s, threw %s", index, source, input, expected, verdict.detail()));
                }
            }
        }

        final int total = ok + mismatch + error + notExpression + knowledge;
        LOG.none("""
                
                ==============================================
                          CORPUS VERIFICATION REPORT
                ==============================================
                  Entries checked:        %4d
                  Verified:               %4d
                  Mismatched:             %4d
                  Evaluation error:       %4d
                  Not an mtron expression:%4d
                  Knowledge (not expr):   %4d
                  (of which write rows):  %4d
                ----------------------------------------------
                """, total, ok, mismatch, error, notExpression, knowledge, writes);

        final List<Map.Entry<String, int[]>> worst = bySource.entrySet().stream()
                .filter(entry -> entry.getValue()[1] > 0)
                .sorted(Comparator.comparingInt((Map.Entry<String, int[]> entry) -> entry.getValue()[1]).reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();

        LOG.none("  Failing sources: %d\n", worst.size());
        for (int i = 0; i < Math.min(worst.size(), 200); i++) {
            final Map.Entry<String, int[]> entry = worst.get(i);
            final int[] tally = entry.getValue();
            LOG.none("    %-44s %4d / %4d\n", entry.getKey(), tally[1], tally[0] + tally[1]);
        }
        if (worst.size() > 200)
            LOG.none("    ... %d more, see the report file\n", worst.size() - 200);

        final Path report = dataset.resolveSibling("mtron_training_dataset.verification.txt");
        Files.writeString(report, renderReport(total, ok, mismatch, error, notExpression, knowledge, bySource, failures),
                StandardCharsets.UTF_8);
        LOG.none("  Report: %s\n", report);

        // The corpus worth training on: only rows whose own prompt reproduces their answer.
        final Path verifiedCorpus = dataset.resolveSibling("mtron_training_dataset.verified.jsonl");
        final String content = verifiedLines.isEmpty() ? "" : String.join("\n", verifiedLines) + "\n";
        final byte[] verifiedBytes = content.getBytes(StandardCharsets.UTF_8);
        UnslothTrainingDatasetExtractor.writeAtomically(verifiedCorpus, verifiedBytes);
        final Path verifiedManifest = UnslothTrainingDatasetExtractor.writeManifest(verifiedCorpus, verifiedBytes, verifiedLines.size());
        LOG.none("  Verified corpus: %s (%d rows)\n", verifiedCorpus, verifiedLines.size());
        LOG.none("  Verified manifest: %s\n", verifiedManifest);

        // Split by source method, never by row: rows of one method are near-duplicates and a
        // row-level split would put `1.plus(2)=>3` in train and `1.plus(3)=>4` in dev.
        final int devPercent = Integer.getInteger("mtron.training.devPercent", 10);
        if (devPercent > 0 && verifiedLines.size() > 1)
            writeSplit(dataset, verifiedLines, verifiedSources, devPercent);

        // The verifier itself must be doing work, or it proves nothing.
        assertTrue(ok > 0, "no entry verified — the verifier is not exercising the VM");
        if (Boolean.getBoolean("mtron.training.verify.strict"))
            assertEquals(0, mismatch + error + notExpression,
                    "corpus does not verify; see " + report);
    }

    private static void record(final Map<String, int[]> bySource, final String source, final boolean verified) {
        final int[] tally = bySource.computeIfAbsent(source, key -> new int[2]);
        tally[verified ? 0 : 1]++;
    }

    /** The report is tab-separated so it can be grepped, sorted and diffed across runs. */
    private static String renderReport(final int total, final int ok, final int mismatch, final int error,
                                       final int notExpression, final int knowledge,
                                       final Map<String, int[]> bySource, final List<String> failures) {
        final StringBuilder report = new StringBuilder();
        report.append(String.format("entries\t%d%nverified\t%d%nmismatch\t%d%nerror\t%d%nnot_expression\t%d%nknowledge\t%d%n",
                total, ok, mismatch, error, notExpression, knowledge));
        report.append("\nfailures_by_source (source, failed, total)\n");
        bySource.entrySet().stream()
                .filter(entry -> entry.getValue()[1] > 0)
                .sorted(Comparator.comparingInt((Map.Entry<String, int[]> entry) -> entry.getValue()[1]).reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> report.append(String.format("%s\t%d\t%d%n",
                        entry.getKey(), entry.getValue()[1], entry.getValue()[0] + entry.getValue()[1])));
        report.append("\nfailures\n");
        failures.forEach(failure -> report.append(failure).append('\n'));
        return report.toString();
    }

    private record Verdict(Kind kind, String detail) {
        static Verdict ok() {
            return new Verdict(Kind.OK, "");
        }

        static Verdict mismatch(final Object actual) {
            return new Verdict(Kind.MISMATCH, String.valueOf(actual));
        }

        static Verdict error(final Object cause) {
            return new Verdict(Kind.ERROR, String.valueOf(cause));
        }

        static Verdict notExpression(final Object cause) {
            return new Verdict(Kind.NOT_AN_EXPRESSION, String.valueOf(cause));
        }
    }

    private enum Kind {OK, MISMATCH, ERROR, NOT_AN_EXPRESSION}

    /**
     * Whether {@code input} really evaluates to {@code output}. The comparison mirrors
     * {@code AbstractMetatronTest.checkCodeParseApplyRaw}: same {@code <ERROR>} sentinel, same
     * real tolerance, same operation-tid rule for addresses.
     */
    private static Verdict verify(final String input, final String expected) {
        final Obj code;
        try {
            code = Machine.parse(input);
        } catch (final RuntimeException e) {
            return Verdict.notExpression(e.getMessage());
        }

        if (expected.trim().equals("<ERROR>")) {
            try {
                return code.apply().isFail() ? Verdict.ok() : Verdict.mismatch("a value");
            } catch (final Exception e) {
                return Verdict.ok();
            }
        }

        try {
            final Obj expectedObj = Machine.parse(expected);
            final Obj ex = expectedObj.isCall() ? expectedObj.apply() : expectedObj;
            final Obj actual = code.apply();
            return matches(ex, actual) ? Verdict.ok() : Verdict.mismatch(actual);
        } catch (final Exception e) {
            return Verdict.error(e.getMessage());
        }
    }

    private static boolean matches(final Obj ex, final Obj actual) {
        if (actual.isReal() && ex.isReal())
            return Math.abs(actual.realValue() - ex.realValue()) < 0.00001d
                    && actual.c().equals(ex.c())
                    && actual.tid().equals(ex.tid());
        if (ex.isUri() && actual.isUri())
            return Inst.Helper.operationTid(ex.uriValue()).equals(Inst.Helper.operationTid(actual.uriValue()));
        if (ex.isLst() && actual.isLst() && ex.lstValue().size() == actual.lstValue().size()) {
            for (int i = 0; i < ex.lstValue().size(); i++) {
                final Obj e = ex.lstValue().get(i);
                final Obj a = actual.lstValue().get(i);
                final boolean same = e.isUri() && a.isUri()
                        ? Inst.Helper.operationTid(e.uriValue()).equals(Inst.Helper.operationTid(a.uriValue()))
                        : e.equals(a);
                if (!same) return false;
            }
            return true;
        }
        if (ex.tid().hasPoly())
            return actual.test(ex);
        return ex.equals(actual);
    }

    /**
     * Assigns whole source methods to dev, never individual rows: rows of one method share an
     * instruction template and differ by a literal, so a row-level split would measure
     * memorization. Groups are ordered by a hash of the source name, so the assignment is a pure
     * function of (sources, devPercent) and does not reshuffle when the corpus is regenerated.
     */
    private static Split split(final List<String> sources, final int devPercent) {
        final Map<String, List<Integer>> bySource = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++)
            bySource.computeIfAbsent(sources.get(i), key -> new ArrayList<>()).add(i);

        final int devTarget = Math.max(1, sources.size() * devPercent / 100);
        // A method larger than this would dominate dev by itself; it stays in train.
        final int cap = Math.max(4, devTarget / 10);

        // Stratify by class so every language area is represented in dev.
        final Map<String, List<String>> byClass = new LinkedHashMap<>();
        for (final String source : bySource.keySet())
            byClass.computeIfAbsent(source.substring(0, source.indexOf('.')), key -> new ArrayList<>()).add(source);

        final boolean[] dev = new boolean[sources.size()];
        final List<String> devSources = new ArrayList<>();
        for (final List<String> group : byClass.values()) {
            final List<String> candidates = new ArrayList<>(group);
            candidates.sort(Comparator.comparing((String source) ->
                    UnslothTrainingDatasetExtractor.sha256(source.getBytes(StandardCharsets.UTF_8))));
            final int classRows = candidates.stream().mapToInt(source -> bySource.get(source).size()).sum();
            // Floor of one method per class: a class with fewer than 10 rows would otherwise
            // contribute nothing to dev, leaving a language area unmeasured.
            final int classTarget = Math.max(1, classRows * devPercent / 100);
            int taken = 0;
            for (final String source : candidates) {
                if (taken >= classTarget) break;
                final int size = bySource.get(source).size();
                if (size > cap) continue;
                taken += size;
                devSources.add(source);
                bySource.get(source).forEach(index -> dev[index] = true);
            }
        }
        final int devRows = devSources.stream().mapToInt(source -> bySource.get(source).size()).sum();
        return new Split(dev, devSources, sources.size() - devRows, devRows);
    }

    private static void writeSplit(final Path dataset, final List<String> lines,
                                   final List<String> sources, final int devPercent) {
        final Split split = split(sources, devPercent);
        final StringBuilder train = new StringBuilder();
        final StringBuilder dev = new StringBuilder();
        for (int i = 0; i < lines.size(); i++)
            (split.dev()[i] ? dev : train).append(lines.get(i)).append('\n');

        // The invariant the split exists to provide: a source is wholly in one file or the other.
        final Map<String, Boolean> seen = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            final Boolean prior = seen.putIfAbsent(sources.get(i), split.dev()[i]);
            assertTrue(prior == null || prior == split.dev()[i], "source split across files: " + sources.get(i));
        }

        final byte[] trainBytes = train.toString().getBytes(StandardCharsets.UTF_8);
        final byte[] devBytes = dev.toString().getBytes(StandardCharsets.UTF_8);
        final Path trainPath = dataset.resolveSibling("mtron_training_dataset.verified.train.jsonl");
        final Path devPath = dataset.resolveSibling("mtron_training_dataset.verified.dev.jsonl");
        UnslothTrainingDatasetExtractor.writeAtomically(trainPath, trainBytes);
        UnslothTrainingDatasetExtractor.writeAtomically(devPath, devBytes);

        final Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("devPercent", devPercent);
        manifest.put("trainRows", split.trainRows());
        manifest.put("devRows", split.devRows());
        manifest.put("trainSha256", UnslothTrainingDatasetExtractor.sha256(trainBytes));
        manifest.put("devSha256", UnslothTrainingDatasetExtractor.sha256(devBytes));
        manifest.put("devSources", split.devSources());
        final Path manifestPath = dataset.resolveSibling("mtron_training_dataset.split.manifest.json");
        UnslothTrainingDatasetExtractor.writeAtomically(manifestPath, GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));

        LOG.none("  Train: %s (%d rows)\n", trainPath, split.trainRows());
        LOG.none("  Dev:   %s (%d rows, %d methods)\n", devPath, split.devRows(), split.devSources().size());
        LOG.none("  Split manifest: %s\n", manifestPath);
    }

    private record Split(boolean[] dev, List<String> devSources, int trainRows, int devRows) {
    }
}
