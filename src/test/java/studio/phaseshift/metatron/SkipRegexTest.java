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

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.params.ParameterizedTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regex-driven mechanism for opting out of inherited test methods and, at finer granularity,
 * individual {@link ParameterizedTest}/{@code @CsvSource} invocations.
 * <p>
 * Three independent axes:
 * <ul>
 *     <li>{@link #value()} — {@link Skip} entries: a {@link Skip#method()} regex (full-match) plus an
 *         optional {@link Skip#params()} bag of regexes (substring) matched against the invocation's
 *         arguments. No params → skip the whole method; params → skip only matching rows.</li>
 *     <li>{@link #tags()} — regexes (full-match) against the method's {@code @Tag} values (e.g.
 *         {@link TestCategory.Read}); a match skips the whole method.</li>
 *     <li>{@link #include()} — method-name regexes exempt from {@link #tags()} skipping only.</li>
 * </ul>
 *
 * The annotation composes across the class hierarchy: an abstract base may declare family-level
 * skips, and a concrete leaf may declare its own additions; both are unioned.
 *
 * <pre>{@code
 * @SkipRegexTest(
 *     value = {
 *         @SkipRegexTest.Skip(method = "testMonoUpdate"),
 *         @SkipRegexTest.Skip(method = "testAnotherTest", params = {"people/[0-9]+/(name|age)", ">>{2}"})
 *     },
 *     tags = {"crud", "boundary"},
 *     include = {"testMonoReadWrite"}
 * )
 * public class httpSpaceTest extends AbstractSpaceTest { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SkipRegexTest.SkipRegexTestExtension.class)
public @interface SkipRegexTest {

    Skip[] value() default {};

    String[] tags() default {};

    String[] include() default {};

    @Retention(RetentionPolicy.RUNTIME)
    @interface Skip {
        String method();

        String[] params() default {};
    }

    class SkipRegexTestExtension implements ExecutionCondition, InvocationInterceptor {

        private static final GraphittyLogger LOG = Graphitty.log(SkipRegexTest.class);

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
            final Method method = context.getTestMethod().orElse(null);
            if (null == method)
                return ConditionEvaluationResult.enabled("not a test method");
            final List<SkipRegexTest> annotations = collect(context.getRequiredTestClass());
            if (annotations.isEmpty())
                return ConditionEvaluationResult.enabled("no @SkipRegexTest present");

            final Class<?> testClass = context.getRequiredTestClass();
            final String methodName = method.getName();
            final boolean parameterized = method.isAnnotationPresent(ParameterizedTest.class);
            for (final SkipRegexTest annotation : annotations) {
                for (final Skip skip : annotation.value()) {
                    if (!Pattern.matches(skip.method(), methodName))
                        continue;
                    if (skip.params().length == 0) {
                        TestReport.TestReportExtension.stats(testClass).methodRegex++;
                        LOG.warn("%s is skipping %s [test method]", testClass.getSimpleName(), methodName);
                        return ConditionEvaluationResult.disabled("skipped by @SkipRegexTest");
                    }
                    if (!parameterized) {
                        TestReport.TestReportExtension.stats(testClass).errorCase++;
                        LOG.error("%s is skipping %s but %s does not contain parameterized tests -- skipping entire test regardless",
                                testClass.getSimpleName(), methodName, methodName);
                        return ConditionEvaluationResult.disabled("skipped by @SkipRegexTest");
                    }
                }
            }
            if (matchesAnyTag(allTags(annotations), context.getTags()) && !isIncluded(allIncludes(annotations), methodName)) {
                TestReport.TestReportExtension.stats(testClass).tag++;
                LOG.warn("%s is skipping %s [test method]", testClass.getSimpleName(), methodName);
                return ConditionEvaluationResult.disabled("skipped by @SkipRegexTest");
            }
            return ConditionEvaluationResult.enabled("no whole-method skip matched");
        }

        @Override
        public void interceptTestTemplateMethod(final Invocation<Void> invocation,
                                                final ReflectiveInvocationContext<Method> invocationContext,
                                                final ExtensionContext extensionContext) throws Throwable {
            final List<SkipRegexTest> annotations = collect(extensionContext.getRequiredTestClass());
            if (!annotations.isEmpty()) {
                final String methodName = invocationContext.getExecutable().getName();
                final List<Object> arguments = invocationContext.getArguments();
                for (final SkipRegexTest annotation : annotations) {
                    for (final Skip skip : annotation.value()) {
                        if (skip.params().length == 0 || !Pattern.matches(skip.method(), methodName))
                            continue;
                        if (matchesRow(arguments, skip.params())) {
                            TestReport.TestReportExtension.stats(extensionContext.getRequiredTestClass()).rowRegex++;
                            LOG.warn("%s is skipping %s [%s]", extensionContext.getRequiredTestClass().getSimpleName(), methodName, concatenate(arguments));
                            assumeTrue(false, "skipped by @SkipRegexTest");
                        }
                    }
                }
            }
            invocation.proceed();
        }

        private static List<SkipRegexTest> collect(final Class<?> testClass) {
            final List<SkipRegexTest> annotations = new ArrayList<>();
            for (Class<?> c = testClass; c != null && c != Object.class; c = c.getSuperclass()) {
                final SkipRegexTest annotation = c.getAnnotation(SkipRegexTest.class);
                if (null != annotation)
                    annotations.add(annotation);
            }
            return annotations;
        }

        private static Set<String> allTags(final List<SkipRegexTest> annotations) {
            final Set<String> tags = new HashSet<>();
            for (final SkipRegexTest annotation : annotations)
                tags.addAll(Arrays.asList(annotation.tags()));
            return tags;
        }

        private static Set<String> allIncludes(final List<SkipRegexTest> annotations) {
            final Set<String> includes = new HashSet<>();
            for (final SkipRegexTest annotation : annotations)
                includes.addAll(Arrays.asList(annotation.include()));
            return includes;
        }

        private static boolean matchesRow(final List<Object> arguments, final String[] params) {
            for (final String regex : params) {
                final Pattern pattern = Pattern.compile(regex);
                for (final Object argument : arguments) {
                    if (pattern.matcher(String.valueOf(argument)).find())
                        return true;
                }
            }
            return false;
        }

        private static boolean matchesAnyTag(final Set<String> tags, final Set<String> methodTags) {
            for (final String tagRegex : tags) {
                final Pattern pattern = Pattern.compile(tagRegex);
                for (final String methodTag : methodTags) {
                    if (pattern.matcher(methodTag).matches())
                        return true;
                }
            }
            return false;
        }

        private static boolean isIncluded(final Set<String> include, final String methodName) {
            for (final String regex : include) {
                if (Pattern.matches(regex, methodName))
                    return true;
            }
            return false;
        }

        private static String concatenate(final List<Object> arguments) {
            return arguments.stream().map(String::valueOf).collect(Collectors.joining(" | "));
        }
    }
}
