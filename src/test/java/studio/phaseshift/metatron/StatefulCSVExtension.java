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

import java.util.List;
import java.util.stream.Stream;

/**
 * Drives {@link StatefulParametrizedTest}: reads the method's {@link StatefulCSVSource}, and provides ONE invocation
 * that runs the whole script (in {@code beforeTestExecution}) and then resets the machine to root (in
 * {@code afterTestExecution}). The method body is empty — the script IS the test.
 */
public class StatefulCSVExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(final ExtensionContext context) {
        return context.getTestMethod().map(m -> m.isAnnotationPresent(StatefulCSVSource.class)).orElse(false);
    }

    @Override
    public @NonNull Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(final ExtensionContext context) {
        final String[] lines = context.getTestMethod()
                .stream()
                .map(m -> m.getAnnotation(StatefulCSVSource.class))
                .map(StatefulCSVSource::value)
                .findFirst()
                .orElseGet(() -> new String[0]);
        return Stream.of(new StatefulInvocationContext(lines));
    }

    private record StatefulInvocationContext(String[] lines) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(final int invocationIndex) {
            return "stateful script [" + this.lines.length + " lines]";
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(
                    (BeforeTestExecutionCallback) ctx ->
                            ((AbstractMetatronTest) ctx.getRequiredTestInstance()).script(this.lines),
                    (AfterTestExecutionCallback) ctx ->
                            ((AbstractMetatronTest) ctx.getRequiredTestInstance()).resetToRoot());
        }
    }
}
