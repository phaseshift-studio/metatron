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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable.Style;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opt-in per-class test report. When applied to a test class, a colorized
 * {@link TableWidget} summary is printed after the class finishes, showing
 * execution totals and the {@link SkipRegexTest} skip breakdown.
 *
 * <pre>{@code
 * @TestReport("fsSpace")
 * public class fsSpaceTest extends AbstractSpaceTest { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestReport.TestReportExtension.class)
public @interface TestReport {

    /**
     * Optional title; defaults to the test class simple name.
     */
    String value() default "";

    class TestReportExtension implements ExecutionCondition, AfterTestExecutionCallback, AfterAllCallback {

        private static final Map<Class<?>, Stats> REGISTRY = new ConcurrentHashMap<>();

        public static Stats stats(final Class<?> testClass) {
            return REGISTRY.computeIfAbsent(testClass, k -> new Stats());
        }

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
            final Method method = context.getTestMethod().orElse(null);
            if (null != method && method.isAnnotationPresent(Disabled.class))
                stats(context.getRequiredTestClass()).disabled++;
            return ConditionEvaluationResult.enabled("counting @Disabled only");
        }

        @Override
        public void afterTestExecution(final ExtensionContext context) {
            final Stats s = stats(context.getRequiredTestClass());
            if (context.getExecutionException().isEmpty())
                s.passed++;
            else if (context.getExecutionException().get() instanceof TestAbortedException)
                s.aborted++;
            else
                s.failed++;
        }

        @Override
        public void afterAll(final ExtensionContext context) {
            final Class<?> testClass = context.getRequiredTestClass();
            final Stats s = REGISTRY.remove(testClass);
            if (null != s)
                render(testClass, s);
        }

        private static void render(final Class<?> testClass, final Stats s) {
            final TestReport ann = testClass.getAnnotation(TestReport.class);
            final String title = (null == ann || ann.value().isEmpty()) ? testClass.getSimpleName() : ann.value();
            // skipped = disabled (never ran) + aborted (assumeTrue). rowRegex aborts are part of `aborted`.
            final int skipped = s.methodRegex + s.tag + s.errorCase + s.disabled + s.aborted;
            final int total = s.passed + s.failed + skipped;
            final double elapsed = (System.currentTimeMillis() - s.startMillis) / 1000.0;

            final TableWidget table = new TableWidget(List.of("Metric", "Count"));
            table.style(Style.empty());
            table.getStyle().border(Border.continuous).divider("│").headerDivider("│");
            table.addRow(row("{{W}}Total{{X}}", "{{W}}" + total + "{{X}}"));
            table.addRow(row("Passed", "{{G}}" + s.passed + "{{X}}"));
            table.addRow(row("Failed", s.failed == 0 ? "0" : "{{R}}" + s.failed + "{{X}}"));
            table.addRow(row("Skipped", skipped == 0 ? "0" : "{{Y}}" + skipped + "{{X}}"));
            table.addRow(row("  method-rx", Integer.toString(s.methodRegex)));
            table.addRow(row("  row-rx", Integer.toString(s.rowRegex)));
            table.addRow(row("  tag", Integer.toString(s.tag)));
            table.addRow(row("  error", Integer.toString(s.errorCase)));
            table.addRow(row("  disabled", Integer.toString(s.disabled)));
            if (s.aborted > s.rowRegex)
                table.addRow(row("  runtime", Integer.toString(s.aborted - s.rowRegex)));
            table.addRow(row("{{C}}Elapsed{{X}}", String.format("%.1fs", elapsed)));

            System.out.print(Graphitty.string("\n{{B}}%s{{X}}\n%s\n", title, table.format()));
        }

        private static List<Object> row(final Object... cells) {
            return List.of(cells);
        }
    }

    /**
     * Per-test-class accumulation of execution totals and {@link SkipRegexTest} skip reasons.
     */
    public static class Stats {
        int passed, failed, aborted;
        int methodRegex, rowRegex, tag, errorCase, disabled;
        final long startMillis = System.currentTimeMillis();
    }
}
