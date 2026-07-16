package studio.phaseshift.metatron.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoaderCLITest;
import studio.phaseshift.metatron.TokenMapperTest;
import studio.phaseshift.metatron.furi.DataPathTest;
import studio.phaseshift.metatron.furi.c.cIntTest;
import studio.phaseshift.metatron.furi.fURITest;
import studio.phaseshift.metatron.furi.q.QProcIntegrationTest;
import studio.phaseshift.metatron.furi.q.TypeQTest;
import studio.phaseshift.metatron.isa.dcmnt.dcmntSpaceTest;
import studio.phaseshift.metatron.isa.grph.TinkerGrphSpaceTest;
import studio.phaseshift.metatron.isa.llm.llmInstSetTest;
import studio.phaseshift.metatron.isa.m.mInstSetTest;
import studio.phaseshift.metatron.isa.m.mParserTest;
import studio.phaseshift.metatron.isa.m.parse.CodeParseTest;
import studio.phaseshift.metatron.isa.m.parse.InstParseTest;
import studio.phaseshift.metatron.isa.m.type.BoolTest;
import studio.phaseshift.metatron.isa.m.type.CodeTest;
import studio.phaseshift.metatron.isa.m.type.FailTest;
import studio.phaseshift.metatron.isa.m.type.InstTest;
import studio.phaseshift.metatron.isa.m.type.IntTest;
import studio.phaseshift.metatron.isa.m.type.LstTest;
import studio.phaseshift.metatron.isa.m.type.NoObjTest;
import studio.phaseshift.metatron.isa.m.type.ObjsTest;
import studio.phaseshift.metatron.isa.m.type.RealTest;
import studio.phaseshift.metatron.isa.m.type.RecTest;
import studio.phaseshift.metatron.isa.m.type.RelTest;
import studio.phaseshift.metatron.isa.m.type.StrTest;
import studio.phaseshift.metatron.isa.m.type.TypeTest;
import studio.phaseshift.metatron.isa.m.type.UriTest;
import studio.phaseshift.metatron.isa.m.type.impl.LazyObjsTest;
import studio.phaseshift.metatron.isa.m.type.resolver.FirstFindInstResolverTest;
import studio.phaseshift.metatron.isa.m.type.resolver.ScoringInstResolverTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjJavaSerializerTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSQLSerializerTest;
import studio.phaseshift.metatron.isa.mach.machInstSetTest;
import studio.phaseshift.metatron.isa.mach.space.fsSpaceTest;
import studio.phaseshift.metatron.isa.math.mathInstSetTest;
import studio.phaseshift.metatron.isa.sys.type_.ThreadExecutorTest;
import studio.phaseshift.metatron.isa.tble.schema.fURIAwareIndexedSchemaTest;
import studio.phaseshift.metatron.isa.tble.tbleInstSetTest;
import studio.phaseshift.metatron.isa.vec.vecInstSetTest;
import studio.phaseshift.metatron.isa.web.parser.ObjHTMLSerializerTest;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializerTest;
import studio.phaseshift.metatron.isa.web.parser.ObjMarkdownSerializerTest;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

/**
 * Extracts training data from @ParameterizedTest/@CsvSource annotated methods
 * and converts them into a JSONL format suitable for Unsloth fine-tuning.
 */
public class UnslothTrainingDatasetExtractor {

    private static final GraphittyLogger LOG = new GraphittyLogger(UnslothTrainingDatasetExtractor.class);

    // Lowercased raw class name → display-friendly name for instruction derivation
    private static final Map<String, String> RENAME = Map.of(
            "threadexecutor", "virtual",
            "tinkergrphspace", "grphspace",
            "furiawareindexedschema", "tblespace",
            "scoringinstresolver", "inst",
            "firstfindinstresolver", "inst"
    );

    // Tracks how many times each rename key was applied during extraction
    private static final Map<String, Integer> RENAME_COUNT = new java.util.concurrent.ConcurrentHashMap<>();

    public static void main(String[] args) {
        // The target test classes to extract from
        final String[] targetClasses = {
                BootLoaderCLITest.class.getCanonicalName(),
                TokenMapperTest.class.getCanonicalName(),
                DataPathTest.class.getCanonicalName(),
                cIntTest.class.getCanonicalName(),
                fURITest.class.getCanonicalName(),
                QProcIntegrationTest.class.getCanonicalName(),
                TypeQTest.class.getCanonicalName(),
                dcmntSpaceTest.class.getCanonicalName(),
                TinkerGrphSpaceTest.class.getCanonicalName(),
                llmInstSetTest.class.getCanonicalName(),
                mInstSetTest.class.getCanonicalName(),
                mParserTest.class.getCanonicalName(),
                CodeParseTest.class.getCanonicalName(),
                InstParseTest.class.getCanonicalName(),
                BoolTest.class.getCanonicalName(),
                CodeTest.class.getCanonicalName(),
                FailTest.class.getCanonicalName(),
                InstTest.class.getCanonicalName(),
                IntTest.class.getCanonicalName(),
                LstTest.class.getCanonicalName(),
                NoObjTest.class.getCanonicalName(),
                ObjsTest.class.getCanonicalName(),
                RealTest.class.getCanonicalName(),
                RecTest.class.getCanonicalName(),
                RelTest.class.getCanonicalName(),
                StrTest.class.getCanonicalName(),
                TypeTest.class.getCanonicalName(),
                UriTest.class.getCanonicalName(),
                LazyObjsTest.class.getCanonicalName(),
                FirstFindInstResolverTest.class.getCanonicalName(),
                ScoringInstResolverTest.class.getCanonicalName(),
                ObjJavaSerializerTest.class.getCanonicalName(),
                ObjSQLSerializerTest.class.getCanonicalName(),
                machInstSetTest.class.getCanonicalName(),
                fsSpaceTest.class.getCanonicalName(),
                mathInstSetTest.class.getCanonicalName(),
                ThreadExecutorTest.class.getCanonicalName(),
                fURIAwareIndexedSchemaTest.class.getCanonicalName(),
                tbleInstSetTest.class.getCanonicalName(),
                vecInstSetTest.class.getCanonicalName(),
                ObjHTMLSerializerTest.class.getCanonicalName(),
                ObjJSONSerializerTest.class.getCanonicalName(),
                ObjMarkdownSerializerTest.class.getCanonicalName(),
                CommonUtilTest.class.getCanonicalName(),
                MTronExceptionTest.class.getCanonicalName(),
        };

        // Methods to skip during extraction: "SimpleClassName.methodName"
        final Set<String> ignoreTestMethods = Set.of(
                // "BootLoaderCLITest.someFlakyTest",
                // "mInstSetTest.someSlowTest"
        );

        List<DatasetEntry> dataset = new ArrayList<>();
        List<ClassStats> allStats = new ArrayList<>();
        int failedClasses = 0;

        LOG.info("=== Starting extraction from %d test classes ===%n", targetClasses.length);

        for (String className : targetClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                ClassStats stats = extractFromClass(clazz, dataset, ignoreTestMethods, deriveInstruction(clazz));
                allStats.add(stats);
                // Track rename row counts
                String renameKey = renameKey(clazz);
                if (renameKey != null && RENAME.containsKey(renameKey)) {
                    RENAME_COUNT.merge(renameKey, stats.rowsExtracted, Integer::sum);
                }
                LOG.info("  %s: %d methods, %d @PT, %d rows",
                        clazz.getSimpleName(),
                        stats.totalMethods,
                        stats.parameterizedTestCount,
                        stats.rowsExtracted);
            } catch (ClassNotFoundException e) {
                failedClasses++;
                LOG.info("  NOT FOUND: %s", className);
            }
        }

        String outputPath = Paths.get(System.getProperty("user.dir") + "/.metatron/skills/mtron/assets", "mtron_training_dataset.jsonl").toString();
        writeJsonl(dataset, outputPath);

        // ── Summary ──
        int totalMethods = allStats.stream().mapToInt(s -> s.totalMethods).sum();
        int totalPT = allStats.stream().mapToInt(s -> s.parameterizedTestCount).sum();
        int totalRows = allStats.stream().mapToInt(s -> s.rowsExtracted).sum();
        int totalIgnored = allStats.stream().mapToInt(s -> s.methodsIgnored).sum();
        int totalSkipped = allStats.stream().mapToInt(s -> s.rowsSkipped).sum();
        int classesWithData = (int) allStats.stream().filter(s -> s.rowsExtracted > 0).count();
        int classesEmpty = (int) allStats.stream().filter(s -> s.rowsExtracted == 0).count();

        LOG.info("==============================================");
        LOG.info("          EXTRACTION SUMMARY REPORT           ");
        LOG.info("==============================================");
        LOG.info("  Classes targeted:         %4d", targetClasses.length);
        LOG.info("  Classes loaded:           %4d", allStats.size());
        LOG.info("  Classes failed:           %4d", failedClasses);
        LOG.info("  Classes with data:        %4d", classesWithData);
        LOG.info("  Classes empty:            %4d", classesEmpty);
        LOG.info("  Total methods scanned:    %4d", totalMethods);
        LOG.info("  @ParameterizedTest meth:  %4d", totalPT);
        LOG.info("  Methods ignored:          %4d", totalIgnored);
        LOG.info("  Rows extracted:           %4d", totalRows);
        LOG.info("  Rows skipped (<2 cols):   %4d", totalSkipped);
        LOG.info("  Dataset entries written:  %4d", dataset.size());
        if (!RENAME.isEmpty()) {
            LOG.info("----------------------------------------------");
            LOG.info("  Name remappings:");
            RENAME.forEach((from, to) -> {
                int count = RENAME_COUNT.getOrDefault(from, 0);
                LOG.info("    %s -- %d renamed --> %s", from, count, to);
            });
        }
        LOG.info("----------------------------------------------");
        LOG.info("  Output: %s", outputPath);
        LOG.info("==============================================");
    }

    /**
     * Derives a domain-specific instruction from the test class name.
     * <p>
     * InstSet tests (e.g. mathInstSetTest) → "using the /math instruction set"
     * Type tests (e.g. StrTest, IntTest) → "involving str type operations"
     * Fallback → generic mtron evaluation prompt.
     */
    /**
     * Extracts the rename-map key from a test class: strips {@code Test} or {@code InstSetTest}
     * suffix and lowercases. Returns {@code null} if the class doesn't match expected naming.
     */
    private static String renameKey(Class<?> clazz) {
        String name = clazz.getSimpleName();
        if (name.endsWith("InstSetTest"))
            return name.substring(0, name.length() - "InstSetTest".length()).toLowerCase();
        if (name.endsWith("Test"))
            return name.substring(0, name.length() - "Test".length()).toLowerCase();
        return null;
    }

    private static String deriveInstruction(Class<?> clazz) {
        String key = renameKey(clazz);
        if (key == null) return "Evaluate this mtron expression";

        String display = RENAME.getOrDefault(key, key);

        if (clazz.getSimpleName().endsWith("InstSetTest")) {
            String isaPath = display.equals("m") ? "/m" : "/m/" + display;
            return "Evaluate this mtron expression using the " + isaPath + " instruction set";
        }
        return "Evaluate this mtron expression involving " + display + " operations";
    }

    private static ClassStats extractFromClass(Class<?> clazz, List<DatasetEntry> dataset, Set<String> ignoreTestMethods, String instruction) {
        int totalMethods = 0;
        int ptCount = 0;
        int methodsIgnored = 0;
        int rowsExtracted = 0;
        int rowsSkipped = 0;
        String simpleName = clazz.getSimpleName();

        for (Method method : clazz.getDeclaredMethods()) {
            totalMethods++;
            if (method.isAnnotationPresent(ParameterizedTest.class)) {
                ptCount++;
                String methodKey = simpleName + "." + method.getName();
                if (ignoreTestMethods.contains(methodKey)) {
                    methodsIgnored++;
                    LOG.info("  SKIPPED (ignore list): %s", methodKey);
                    continue;
                }
                CsvSource csv = method.getAnnotation(CsvSource.class);
                if (csv != null) {
                    String delimiter = String.valueOf(csv.delimiter());

                    for (String row : csv.value()) {
                        // Split the CSV row by the specified delimiter - handles both char and string delimiters
                        String regexDelimiter = java.util.regex.Pattern.quote(delimiter);
                        String[] parts = row.split(regexDelimiter);

                        if (parts.length < 2) {
                            rowsSkipped++;
                            continue;
                        }

                        // Convention: First part is usually the code/input, last part is often the expected result.
                        // In mInstSetTest, they are typically "code % expected".
                        String input = parts[0].trim();
                        String output = parts[parts.length - 1].trim();

                        // Handle Java string escaping for the LLM (e.g., \" -> ")
                        input = cleanJavaString(input);
                        output = cleanJavaString(output);

                        dataset.add(new DatasetEntry(
                                instruction,
                                input,
                                output,
                                methodKey
                        ));
                        rowsExtracted++;
                    }
                }
            }
        }

        return new ClassStats(totalMethods, ptCount, rowsExtracted, rowsSkipped, methodsIgnored);
    }

    private static String cleanJavaString(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim();
    }

    private static void writeJsonl(List<DatasetEntry> entries, String path) {
        try (FileWriter writer = new FileWriter(path)) {
            String lastSourceMethod = null;
            for (DatasetEntry entry : entries) {
                if (entry.sourceMethod != null && !entry.sourceMethod.equals(lastSourceMethod)) {
                    writer.write("# " + entry.sourceMethod + "\n");
                    lastSourceMethod = entry.sourceMethod;
                }
                writer.write(entry.toJson() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClassStats {
        final int totalMethods;
        final int parameterizedTestCount;
        final int rowsExtracted;
        final int rowsSkipped;
        final int methodsIgnored;

        ClassStats(int totalMethods, int parameterizedTestCount, int rowsExtracted, int rowsSkipped, int methodsIgnored) {
            this.totalMethods = totalMethods;
            this.parameterizedTestCount = parameterizedTestCount;
            this.rowsExtracted = rowsExtracted;
            this.rowsSkipped = rowsSkipped;
            this.methodsIgnored = methodsIgnored;
        }
    }

    static class DatasetEntry {
        String instruction;
        String input;
        String output;
        String sourceMethod; // "SimpleClassName.methodName" — null for no comment

        DatasetEntry(String instruction, String input, String output, String sourceMethod) {
            this.instruction = instruction;
            this.input = input;
            this.output = output;
            this.sourceMethod = sourceMethod;
        }

        String toJson() {
            return String.format(
                    "{\"instruction\": %s, \"input\": %s, \"output\": %s}",
                    escapeJson(instruction),
                    escapeJson(input),
                    escapeJson(output)
            );
        }

        private String escapeJson(String s) {
            return "\"" + s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r") + "\"";
        }
    }
}
