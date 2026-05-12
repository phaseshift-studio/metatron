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
import org.junit.jupiter.api.extension.*;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestScope {
    class TestScopeExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

        @Override
        public void beforeTestExecution(final @NonNull ExtensionContext context) {
            if (context.getTestMethod().get().getAnnotation(TestScope.class) == null) {
                context.getTestClass().ifPresent(c -> {
                    Graphitty.log(c).debug("test out of scope");
                    if (AbstractSpaceTest.class.isAssignableFrom(context.getRequiredTestClass()))
                        ((AbstractSpaceTest) context.getRequiredTestInstance()).inScope.set(false);
                });
            } else {
                context.getTestClass().ifPresent(c -> {
                    Graphitty.log(c).debug("test in scope");
                    if (AbstractSpaceTest.class.isAssignableFrom(context.getRequiredTestClass()))
                        ((AbstractSpaceTest) context.getRequiredTestInstance()).inScope.set(true);
                });
            }
        }

        @Override
        public void afterTestExecution(final @NonNull ExtensionContext context) {
            context.getTestClass().ifPresent(c -> {
                //   Graphitty.log(context.getRequiredTestInstance().getClass()).warn("test out of scope");
                //  ((AbstractSpaceTest) context.getRequiredTestInstance()).inScope.set(false);
            });
        }
    }
}
