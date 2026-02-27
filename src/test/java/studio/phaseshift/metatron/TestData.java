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

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import studio.phaseshift.metatron.isa.m.parser.mParser;
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

        /** Tracks whether test data was loaded for the current test. */
        protected boolean hasTestData = false;

        /**
         * Parses and evaluates the test data strings before test execution.
         *
         * @param context the current extension context
         * @throws MTronTestException if parsing or evaluation fails
         */
        @Override
        public void beforeTestExecution(final ExtensionContext context) {
            try {
                if (context.getRequiredTestMethod().getAnnotation(TestData.class) != null) {
                    Arrays.stream(context.getRequiredTestMethod().getAnnotation(TestData.class).value())
                            .peek(value -> LOG.debug("loading test data: %s", value))
                            .peek(v -> this.hasTestData = true)
                            .forEach(mParser::eval);
                }
            } catch (Exception e) {
                throw MTronTestException.of(e);
            }
        }

        /**
         * Logs a debug message after test execution if test data was loaded.
         *
         * @param context the current extension context
         */
        @Override
        public void afterTestExecution(final ExtensionContext context) {
            if (this.hasTestData)
                LOG.debug("testing state still remains from  %s", context.getRequiredTestMethod().getName());
        }
    }
}
