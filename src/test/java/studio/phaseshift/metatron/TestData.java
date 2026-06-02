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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

/**
 * Annotation for prepopulating a machine with test data before test execution.
 * <p>
 * The provided string values are parsed and evaluated using {@link mParser#eval(String)}
 * prior to test execution.
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

        /**
         * Tracks whether test data was loaded for the current test.
         */
        protected boolean testDataLoaded = false;

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
                if (annotation != null && (!this.testDataLoaded || !annotation.oneTime())) {
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
                                        ObjmtronSerializer.parse(processedLine).apply().forEach(obj -> {});
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
                                mParser.eval(tempFile).asCall().forEach(o -> o.apply());
                            } finally {
                                tempFile.delete();
                            }
                        }
                        this.testDataLoaded = true;
                    }

                    // Also load inline values (with $$ substitution for AbstractSpaceTest)
                    final java.lang.reflect.Method testMethod = context.getRequiredTestMethod();
                    final Object testInstance = context.getRequiredTestInstance();
                    Arrays.stream(annotation.value())
                            .filter(value -> !value.trim().isEmpty())
                            .peek(v -> this.testDataLoaded = true)
                            .forEach(v -> {
                                final String resolved = testInstance instanceof studio.phaseshift.metatron.isa.AbstractSpaceTest
                                        ? ((studio.phaseshift.metatron.isa.AbstractSpaceTest) testInstance).make(v, testMethod)
                                        : v;
                                final Obj result = ObjmtronSerializer.parse(resolved).apply();
                                LOG.debug("loaded test data: %s -> %s", resolved, result);
                            });
                }
            } catch (Exception e) {
                throw MTronTestException.of(e);
            }
        }

        /**
         * Clears the test data from the stack after test execution if test data was loaded and the annotation specifies one-time loading.
         *
         * @param context the current extension context; never {@code null}
         */
        @Override
        public void afterTestExecution(final ExtensionContext context) {
            if (context.getRequiredTestMethod().getAnnotation(TestData.class) != null &&
                    context.getRequiredTestMethod().getAnnotation(TestData.class).oneTime() && this.testDataLoaded) {
                Router.stack().clear();
                LOG.debug("clearing %s test data from the stack", context.getRequiredTestMethod().getName());
            }
        }
    }
}
