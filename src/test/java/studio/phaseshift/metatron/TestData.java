/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
                if (context.getRequiredTestMethod().getAnnotation(TestData.class) != null &&
                        (!this.testDataLoaded ||
                                !context.getRequiredTestMethod().getAnnotation(TestData.class).oneTime())) {
                    Arrays.stream(context.getRequiredTestMethod().getAnnotation(TestData.class).value())
                            .peek(value -> LOG.debug("loading test data: %s", value))
                            .peek(v -> this.testDataLoaded = true)
                            .forEach(mParser::eval);
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
