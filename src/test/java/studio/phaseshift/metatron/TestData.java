/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Memory;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.GraphittyLogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

/**
 * Prepopulate the machine with test data before the test body runs.
 * <p>
 * <b>ORDER — WHY THIS IS NOT A {@code BeforeEachCallback}.</b> The data is evaluated AFTER {@code @BeforeEach} and
 * BEFORE the test method. That is the point of the hook, not an accident: a test's {@code @BeforeEach} is where its
 * spaces get mounted (a tbleSpace, a ws client, an imported ISA), and data written before those exist would land
 * nowhere. The price is that a {@code @BeforeEach} sees the machine as it was BEFORE the seeds — a dial or a prefix
 * it registers attaches to the pre-seed frame, and a seed that then {@code move}s leaves it behind. If a fixture
 * needs both, declare the reach after the seeds (e.g. in the test method, with
 * {@link Machine.Helper#evalAt(studio.phaseshift.metatron.furi.fURI, java.util.function.Supplier)}).
 * <p>
 * <b>WHAT IT DOES.</b> Each entry is parsed and evaluated in order as a top-level program — {@code push(a)},
 * {@code move(</.>)}, {@code "/network/prefix -> [//self=>loop://self]"} — and the thread's PERSPECTIVE CARRIES
 * ACROSS entries, so a {@code push} in one entry is still standing in that frame for the next, and a
 * {@code move(</.>)} at the end is what puts the test method back at the root. A failing entry fails the method
 * ({@link MTronTestException}); entries in a {@code source()} file are evaluated line by line and a failing line is
 * logged and skipped rather than failing the run.
 * <p>
 * {@code source()} runs first, then {@code value()}, so a file can stage the machine and the inline values specialise
 * it.
 * <p>
 * <b>ONE-TIME vs PER-ROW.</b> The default re-evaluates the data before every test invocation, which is what a
 * {@code @ParameterizedTest} needs when a row must not see its predecessor's writes. {@code oneTime = true}
 * evaluates them once for the METHOD's rows instead (a parameterized method's rows share one method context) and
 * clears the argument stack afterwards. Because the "loaded" flag lives in the method's context, a test method
 * inherited by another class seeds that class's VM again.
 * <p>
 * Example usage:
 * <pre>{@code
 * @TestData({"data1", "data2"})
 * @ExtendWith(TestData.TestDataExtension.class)
 * @Test
 * public void testWithData() {
 *     // Test runs with prepopulated data
 * }
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestData {
    GraphittyLogger LOG = Graphitty.log(TestData.class);

    /**
     * Whether to load test data only once for the entire test class.
     *
     * @return true if test data should be loaded only once for the entire test class, false otherwise.
     */
    boolean oneTime() default false;

    /**
     * Path to a .mtron file in resources to load as test data.
     * If specified, the file will be loaded and evaluated before the inline values.
     *
     * @return path to .mtron file in resources, or empty string if not used
     */
    String source() default "";

    /**
     * The string values to parse and evaluate before test execution.
     *
     * @return an array of string values to be evaluated as test data
     */
    String[] value();

    /**
     * JUnit 5 extension that parses and evaluates test data before test execution.
     * Register with {@code @ExtendWith(TestData.TestDataExtension.class)}.
     */
    class TestDataExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

        private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestData.class);
        private static final String LOADED = "loaded";

        /**
         * Whether a test method's data is loaded is the method's own state: JUnit shares one extension instance across
         * every method of a class, so the flag lives in the method's store. A {@code @ParameterizedTest} row runs in
         * an invocation context whose parent is the method's context; a plain {@code @Test} is its own method context.
         * JUnit discards the store when the method's rows are done, so a method inherited by another class seeds that
         * class's VM again.
         */
        private static ExtensionContext.Store methodStore(final ExtensionContext context) {
            return context.getParent()
                    .filter(parent -> parent.getTestMethod().equals(context.getTestMethod()))
                    .orElse(context)
                    .getStore(NAMESPACE);
        }

        /**
         * Parses and evaluates the test data strings before test execution.
         *
         * @param context the current extension context
         * @throws MTronTestException if parsing or evaluation fails
         */
        @Override
        public void beforeTestExecution(final @NonNull ExtensionContext context) {
            try {
                final TestData annotation = context.getRequiredTestMethod().getAnnotation(TestData.class);
                final ExtensionContext.Store store = methodStore(context);
                if (annotation != null && (null == store.get(LOADED) || !annotation.oneTime())) {
                    // Load from file if source is specified
                    if (!annotation.source().isEmpty()) {
                        System.out.println("==> TestData: loading test data from file: " + annotation.source());
                        LOG.debug("loading test data from file: %s", annotation.source());
                        final java.io.InputStream stream = context.getRequiredTestClass()
                                .getResourceAsStream("/" + annotation.source());
                        if (stream == null) {
                            throw new IllegalArgumentException("Test data file not found: " + annotation.source());
                        }

                        // Get test instance to access make() method for $$ replacement
                        final Object testInstance = context.getRequiredTestInstance();
                        if (testInstance instanceof studio.phaseshift.metatron.isa.AbstractSpaceTest) {
                            final studio.phaseshift.metatron.isa.AbstractSpaceTest spaceTest =
                                    (studio.phaseshift.metatron.isa.AbstractSpaceTest) testInstance;

                            // Read file line by line and eval each statement separately
                            int recordCount = 0;
                            int lineNumber = 0;
                            boolean inBlockComment = false;
                            try (final java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(stream))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    lineNumber++;
                                    final String processedLine = spaceTest.make(line, context.getRequiredTestMethod()).trim();

                                    // Skip empty lines
                                    if (processedLine.isEmpty()) {
                                        continue;
                                    }

                                    // Handle single-line comments [-- comment --] or [== comment ==]
                                    if ((processedLine.startsWith("[--") && processedLine.endsWith("--]")) ||
                                            (processedLine.startsWith("[==") && processedLine.endsWith("==];"))) {
                                        continue;
                                    }

                                    // Track multi-line block comment state
                                    if (processedLine.startsWith("[==") || processedLine.startsWith("[--")) {
                                        inBlockComment = true;
                                        continue;
                                    }
                                    if (processedLine.equals("==];") || processedLine.equals("--]")) {
                                        inBlockComment = false;
                                        continue;
                                    }

                                    // Skip lines inside block comments
                                    if (inBlockComment) {
                                        continue;
                                    }

                                    System.out.println("==> TestData line " + lineNumber + ": evaluating: " + processedLine);
                                    LOG.debug("line %d: evaluating: %s", lineNumber, processedLine);
                                    try {
                                        // Execute each write statement individually and consume the stream
                                        ObjmtronSerializer.parse(processedLine).apply().forEach(obj -> {
                                        });
                                        System.out.println("==> TestData line " + lineNumber + ": completed");
                                        LOG.debug("line %d: completed", lineNumber);
                                        recordCount++;
                                    } catch (Exception e) {
                                        LOG.warn("line %d: failed to eval: %s - %s", lineNumber, processedLine, e.getMessage());
                                    }
                                }
                            }
                            System.out.println("==> TestData: successfully loaded " + recordCount + " records from " + lineNumber + " total lines");
                            LOG.debug("successfully loaded %d records from %d total lines", recordCount, lineNumber);
                        } else {
                            // Fallback for non-AbstractSpaceTest instances
                            final java.io.File tempFile = java.io.File.createTempFile("test-data-", ".mtron");
                            try {
                                java.nio.file.Files.copy(stream, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                Machine.Helper.evaluate(tempFile).asCall().forEach(o -> o.apply());
                            } finally {
                                tempFile.delete();
                            }
                        }
                        store.put(LOADED, true);
                    }

                    // Also load inline values (with $$ substitution for AbstractSpaceTest)
                    final java.lang.reflect.Method testMethod = context.getRequiredTestMethod();
                    final Object testInstance = context.getRequiredTestInstance();
                    Arrays.stream(annotation.value())
                            .filter(value -> !value.trim().isEmpty())
                            .peek(v -> store.put(LOADED, true))
                            .forEach(v -> {
                                final String made = testInstance instanceof studio.phaseshift.metatron.isa.AbstractSpaceTest
                                        ? ((studio.phaseshift.metatron.isa.AbstractSpaceTest) testInstance).make(v, testMethod)
                                        : v;
                                final Obj result = ObjmtronSerializer.parse(made).apply();
                                LOG.debug("loaded test data: %s -> %s", made, result);
                            });
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw MTronTestException.of(e);
            }
        }

        /**
         * Clears the test data from the stack after test execution if this method's data was loaded and the annotation specifies one-time loading.
         *
         * @param context the current extension context; never {@code null}
         */
        @Override
        public void afterTestExecution(final ExtensionContext context) {
            final TestData annotation = context.getRequiredTestMethod().getAnnotation(TestData.class);
            if (null != annotation && annotation.oneTime() && null != methodStore(context).get(LOADED)) {
                Memory.argStack().clear();
                LOG.debug("clearing %s test data from the stack", context.getRequiredTestMethod().getName());
            }
        }
    }
}
