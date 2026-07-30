package studio.phaseshift.metatron.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.furi.q.QProcIntegrationTest;
import studio.phaseshift.metatron.furi.q.TypeQTest;
import studio.phaseshift.metatron.isa.grph.TinkerGrphSpaceTest;
import studio.phaseshift.metatron.isa.llm.llmInstSetTest;
import studio.phaseshift.metatron.isa.m.mInstSetTest;
import studio.phaseshift.metatron.isa.m.mParserTest;
import studio.phaseshift.metatron.isa.m.parse.CodeParseTest;
import studio.phaseshift.metatron.isa.m.parse.InstParseTest;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.machInstSetTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.math.mathInstSetTest;
import studio.phaseshift.metatron.isa.vec.vecInstSetTest;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                //BootLoaderCLITest.class.getCanonicalName(),
                //TokenMapperTest.class.getCanonicalName(),
                //DataPathTest.class.getCanonicalName(),
                //cIntTest.class.getCanonicalName(),
                //fURITest.class.getCanonicalName(),
                QProcIntegrationTest.class.getCanonicalName(),
                TypeQTest.class.getCanonicalName(),
                //dcmntSpaceTest.class.getCanonicalName(),
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
                //LazyObjsTest.class.getCanonicalName(),
                //FirstFindInstResolverTest.class.getCanonicalName(),
                //ScoringInstResolverTest.class.getCanonicalName(),
                //ObjJavaSerializerTest.class.getCanonicalName(),
                //ObjSQLSerializerTest.class.getCanonicalName(),
                machInstSetTest.class.getCanonicalName(),
                //fsSpaceTest.class.getCanonicalName(),
                mathInstSetTest.class.getCanonicalName(),
                //ThreadExecutorTest.class.getCanonicalName(),
                //fURIAwareIndexedSchemaTest.class.getCanonicalName(),
                //tbleInstSetTest.class.getCanonicalName(),
                vecInstSetTest.class.getCanonicalName(),
                //ObjHTMLSerializerTest.class.getCanonicalName(),
                //ObjJSONSerializerTest.class.getCanonicalName(),
                //ObjMarkdownSerializerTest.class.getCanonicalName(),
                //CommonUtilTest.class.getCanonicalName(),
                //MTronExceptionTest.class.getCanonicalName(),
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

        // ── Meta-knowledge entries about mtron ──
        addMetaKnowledge(dataset);

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

        LOG.none("==============================================\n");
        LOG.none("          EXTRACTION SUMMARY REPORT           \n");
        LOG.none("==============================================\n");
        LOG.none("  Classes targeted:         %4d\n", targetClasses.length);
        LOG.none("  Classes loaded:           %4d\n", allStats.size());
        LOG.none("  Classes failed:           %4d\n", failedClasses);
        LOG.none("  Classes with data:        %4d\n", classesWithData);
        LOG.none("  Classes empty:            %4d\n", classesEmpty);
        LOG.none("  Total methods scanned:    %4d\n", totalMethods);
        LOG.none("  @ParameterizedTest meth:  %4d\n", totalPT);
        LOG.none("  Methods ignored:          %4d\n", totalIgnored);
        LOG.none("  Rows extracted:           %4d\n", totalRows);
        LOG.none("  Rows skipped (<2 cols):   %4d\n", totalSkipped);
        LOG.none("  Dataset entries written:  %4d\n", dataset.size());
        if (!RENAME.isEmpty()) {
            LOG.none("----------------------------------------------\n");
            LOG.none("  Name remappings:\n");
            RENAME.forEach((from, to) -> {
                int count = RENAME_COUNT.getOrDefault(from, 0);
                LOG.info("    %s -- %d renamed --> %s\n", from, count, to);
            });
        }
        LOG.none("----------------------------------------------\n");
        LOG.none("  Output: %s\n", outputPath);
        LOG.none("==============================================\n");
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

    private static void addMetaKnowledge(List<DatasetEntry> dataset) {
        final String INSTR = "Answer this question about the mtron language and metatron virtual machine accurately and concisely.";
        // ── Core identity ──
        dataset.add(new DatasetEntry(INSTR,
                "what is mtron?",
                "mtron is the functional programming language of the metatron vm, a distributed data-oriented computing system built in Java. mtron expressions navigate typed spaces via URI-based paths. It supports mono types (int, real, str, bool, uri, bytes), poly types (rec, lst), and call types (inst, code). Operations include .select(), .where(), .count(), .plus(), .as(), and path traversal with wildcards (+ for single segment, # for multi-segment).",
                null
        ));
        dataset.add(new DatasetEntry(INSTR,
                "What is the Metatron VM?",
                "Metatron is a distributed data-oriented computing language and virtual machine. It uses Spaces as fundamental data containers, Instruction Sets (InstSets) for domain-specific operations, and a universal type system called Obj. The VM is booted via BootLoader and instruction sets are discovered through SPI. Key instruction domains include /m (core language), /m/mach (machine/IO), /m/web (HTTP/WebSocket), /m/grph (graph DB), /m/tble (SQL), /m/math (math), and /m/vec (vectors).",
                null
        ));
        // ── Type system ──
        dataset.add(new DatasetEntry(INSTR,
                "What types exist in mtron?",
                "mtron has three categories of types. Mono types: bool (true/false), int (integers), real (floating point), str (strings), uri (uniform resource identifiers), bytes (binary data). Poly types: rec (records/maps with key-value pairs), lst (lists/sequences). Call types: inst (instructions with domain/range), code (executable code blocks). Types are structural and Turing Complete using [?predicate] refinement. Base types have tid == vid (e.g. int::T@int).",
                null
        ));
        dataset.add(new DatasetEntry(INSTR,
                "What is the difference between tid and vid in mtron?",
                "In mtron, every Obj has a tid (Type ID) and vid (Value ID). For a type: vid is the type's name (e.g. 'nat') and tid is its refinement (e.g. 'int' giving int::T[?>0]@nat). For a value: vid is the value's location in space (e.g. nat::29@/usr/marko/age) and tid is the type constraining it. tid == vid for base types, creating loops in the identifier chain.",
                null
        ));
        // ── Expressions and evaluation ──
        dataset.add(new DatasetEntry(INSTR,
                "How do you evaluate a mtron expression?",
                "mtron expressions are evaluated by navigating typed spaces. Use *<uri> to dereference (read from a space) and <uri> -> <obj> to reference (write). Instructions follow the pattern inst?rng<=dom(args){code}. Expressions support method chaining: {int{10}::1}.plus(_) evaluates to int{10}::2. The la-palette uses .== for selection/mutation and =?= for verification across Rec, Str, Uri, and Lst types.",
                null
        ));
        // ── Common operations ──
        dataset.add(new DatasetEntry(INSTR,
                "What are common mtron operators and operations?",
                "Common mtron operations include: .select(predicate) to filter records, .where(predicate) for conditional filtering, .count() to count elements, .plus(x) for addition, .as(type) for type casting, outE()/inE() for graph edge traversal, /+/ for wildcard single-segment navigation, /#/ for multi-segment wildcards, >> for projection, -> for assignment, and * for dereference. Records use {key=>value, ...} syntax and lists use [a,b,c] syntax.",
                null
        ));
        // ── Spaces ──
        dataset.add(new DatasetEntry(INSTR,
                "What are Spaces in mtron?",
                "Spaces are the fundamental data containers in Metatron. They implement rec::T and are registered in the Router upon construction (automatic when extending AbstractSpace). Spaces are read/written via URI paths: *<uri> dereferences and <uri> -> <obj> references. Key space types include memSpace (in-memory), fsSpace (filesystem), tble spaces (SQL databases), grph spaces (TinkerPop graph), dcmnt spaces (document stores), and vec spaces (vectors). Spaces can be linked via rel::T relations.",
                null
        ));
        // ── Capability ──
        dataset.add(new DatasetEntry(INSTR,
                "Can you help me write mtron code?",
                "Yes. I can evaluate mtron expressions, explain mtron syntax and type semantics, help write mtron queries for graph traversal (TinkerPop/Gremlin-style), SQL queries via tble spaces, vector operations, and general Metatron VM programming. I understand mtron's URI-based navigation, type system with mono/poly/call types, instruction sets, space architecture, and the la-palette symmetry pattern for structural selection and verification.",
                null
        ));
        // ── Sugar reference + training pairs (generated from mInstSet.sugars()) ──
        final String SUGAR_TO = "Convert this mtron sugar expression to its desugared form";
        final String SUGAR_FROM = "Convert this mtron desugared call to its sugar form";
        try {
            mInstSet isa = new mInstSet();
            for (final Sugar s : isa.sugars()) {
                String token = s.getStartToken().trim();
                String instName = s.getInstChain().getFirst().name();

                // Build sugar form and desugared examples from Sugar metadata
                String sugarEx, desugEx;
                switch (s.getPosition()) {
                    case WRAP -> {
                        // _/ a \_  ↔  a.within()   OR   a._/ b \_  ↔  a.within(b)
                        sugarEx = "a._/ b \\_".replace("_/", s.getStartToken().trim())
                                .replace("\\_", s.getEndToken().trim());
                        desugEx = "a." + instName + "(b)";
                    }
                    case INFIX -> {
                        // a & b  ↔  a.and(b)
                        sugarEx = "a" + s.getStartToken() + "b";
                        desugEx = "a." + instName + "(b)";
                    }
                    case PREFIX -> {
                        if (s.getArgCount() == 0) {
                            // Standalone tokens like _, ^* vs postfix like ;, >-, >>
                            boolean standalone = token.equals("_") || token.equals("^*");
                            sugarEx = standalone ? token : "a " + token;
                            desugEx = standalone ? instName + "()" : "a." + instName + "()";
                        } else {
                            // Check if token is a true prefix (binds left, e.g. * a → mult(a))
                            boolean isPrefix = token.startsWith("*") || token.equals("|");
                            if (isPrefix) {
                                sugarEx = token + " a";
                                desugEx = instName + "(a)";
                            } else {
                                // a + b  ↔  a.plus(b)
                                sugarEx = "a " + token + " b";
                                desugEx = "a." + instName + "(b)";
                            }
                        }
                    }
                    default -> { sugarEx = token; desugEx = instName + "()"; }
                }

                // Reference: what is this sugar?
                dataset.add(new DatasetEntry(INSTR,
                        "What is the mtron sugar operator '" + token + "'?",
                        "'" + token + "' is sugar for " + instName + "(). Example: " + sugarEx + " → " + desugEx,
                        null));
                // Training pair: sugar → desugar
                dataset.add(new DatasetEntry(SUGAR_TO, sugarEx, desugEx, null));
                // Training pair: desugar → sugar (skip for multi-inst chains — too ambiguous)
                if (s.getInstChain().size() == 1) {
                    dataset.add(new DatasetEntry(SUGAR_FROM, desugEx, sugarEx, null));
                }
            }
            LOG.info("  Sugar entries generated: %d sugars", isa.sugars().size());
        } catch (Exception e) {
            LOG.warn("  Could not extract sugars: %s", e.getMessage());
        }
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
            if (method.isAnnotationPresent(ParameterizedTest.class) && !method.isAnnotationPresent(TestData.class)) {
                ptCount++;
                final String methodKey = simpleName + "." + method.getName();
                if (ignoreTestMethods.contains(methodKey)) {
                    methodsIgnored++;
                    LOG.warn("  SKIPPED (ignore list): %s", methodKey);
                    continue;
                }

                if (method.isAnnotationPresent(Training.class)) {
                    for (final Training.Run run : Training.Run.runs(method.getAnnotation(Training.class))) {
                        CsvSource csv = method.getAnnotation(CsvSource.class);
                        if (csv != null) {
                            for (final String row : csv.value()) {
                                final String[] parts = row.split(java.util.regex.Pattern.quote(String.valueOf(csv.delimiter())));
                                dataset.add(new DatasetEntry(
                                        run.desc() + ": " + run.mapDesc(),
                                        cleanJavaString(parts[run.lhs()].trim()),
                                        cleanJavaString(parts[run.rhs()].trim()),
                                        methodKey
                                ));
                                rowsExtracted++;
                            }
                        }
                    }
                } else {
                    CsvSource csv = method.getAnnotation(CsvSource.class);
                    if (csv != null) {
                        String delimiter = String.valueOf(csv.delimiter());
                        for (String row : csv.value()) {
                            // Split the CSV row by the specified delimiter - handles both char and string delimiters
                            String regexDelimiter = java.util.regex.Pattern.quote(delimiter);
                            String[] parts = row.split(regexDelimiter);

                            if (parts.length != 2) {
                                rowsSkipped++;
                                continue;
                            }

                            // Convention: First part is usually the code/input, last part is often the expected result.
                            // In mInstSetTest, they are typically "code % expected".
                            String input = parts[0].trim();
                            String function = "lhs evaluates to rhs";
                            String output = parts[1].trim();

                            // Handle Java string escaping for the LLM (e.g., \" -> ")
                            input = cleanJavaString(input);
                            function = cleanJavaString(function);
                            output = cleanJavaString(output);

                            dataset.add(new DatasetEntry(
                                    instruction + ": " + function,
                                    input,
                                    output,
                                    methodKey
                            ));
                            rowsExtracted++;
                        }
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
                    // writer.write("# " + entry.sourceMethod + "\n");
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
                    "{\"instruction\": %s,\"input\": %s,\"output\": %s}",
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
