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
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

/*
 * The user provided string values are parsed and evaluated prior to test evaluation.
 * Useful for prepopulating a machine with data.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestData {
    GraphittyLogger LOG = Graphitty.log(TestData.class);

    String[] values();

    class TestDataExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

        protected boolean hasTestData = false;

        @Override
        public void beforeTestExecution(final ExtensionContext context) {
            try {
                if (context.getRequiredTestMethod().getAnnotation(TestData.class) != null) {
                    Arrays.stream(context.getRequiredTestMethod().getAnnotation(TestData.class).values())
                            .peek(value -> LOG.debug("loading test data: %s", value))
                            .peek(v -> this.hasTestData = true)
                            .forEach(mParser::eval);
                }
            } catch (Exception e) {
                throw MTronTestException.of(e);
            }
        }


        @Override
        public void afterTestExecution(final ExtensionContext context) {
            if(this.hasTestData)
            LOG.warn("testing state still remains from  %s", context.getRequiredTestMethod().getName());
        }
    }
}
