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

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Annotation for selectively skipping inherited test methods from a parent test class.
 * <p>
 * Example usage:
 * <pre>{@code
 * @TestSkip(testClass = BaseTestClass.class, testMethods = {"testMethodToSkip"})
 * @ExtendWith(TestSkip.TestSkipExtension.class)
 * public class MyTestClass extends BaseTestClass {
 *     // Skips specified methods from BaseTestClass
 * }
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestSkip {

    /**
     * The test class containing the methods to skip (typically a parent class).
     *
     * @return the class containing the test methods to skip
     */
    Class testClass();

    /**
     * The names of the test methods to skip.
     *
     * @return an array of test method names to skip
     */
    String[] testMethods();

    /**
     * JUnit 5 extension that implements the test skipping logic using JUnit's assumption mechanism.
     * Register with {@code @ExtendWith(TestSkip.TestSkipExtension.class)}.
     */
    class TestSkipExtension implements BeforeTestExecutionCallback {

        /**
         * Callback invoked before each test execution. Traverses the class hierarchy
         * to determine if the current test method should be skipped.
         *
         * @param context the current extension context
         */
        @Override
        public void beforeTestExecution(final ExtensionContext context) {
            final TestSkip skip = context.getRequiredTestClass().getAnnotation(TestSkip.class);
            Class parentClass = context.getRequiredTestClass().getSuperclass();
            while (parentClass != Object.class) {
                assumeTrue(skip == null || !skip.testClass().getName().equals(parentClass.getName()) ||
                        !Arrays.asList(skip.testMethods()).contains(context.getRequiredTestMethod().getName()), "%s set to skip test data".formatted(skip));
                parentClass = parentClass.getSuperclass();
            }
        }

    }

}
