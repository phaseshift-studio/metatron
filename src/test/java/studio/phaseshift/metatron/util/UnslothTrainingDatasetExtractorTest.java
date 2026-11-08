package studio.phaseshift.metatron.util;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
}
