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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JUnit 5 extension that implements the @SkipInheritedTests annotation.
 * This extension checks if a test method should be skipped based on the
 * annotation on the test class, either by method name or by tag.
 */
public class SkipInheritedTestsExtension implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        // Only evaluate for test methods, not classes
        if (!context.getTestMethod().isPresent()) {
            return ConditionEvaluationResult.enabled("Not a test method");
        }

        Method testMethod = context.getTestMethod().get();
        Class<?> testClass = context.getRequiredTestClass();

        // Check if the test class has the @SkipInheritedTests annotation
        SkipInheritedTests annotation = testClass.getAnnotation(SkipInheritedTests.class);
        if (annotation == null) {
            return ConditionEvaluationResult.enabled("No @SkipInheritedTests annotation present");
        }

        String methodName = testMethod.getName();

        // Collect all methods to skip (from both 'value' and 'methods' attributes)
        Set<String> methodsToSkip = new HashSet<>();
        methodsToSkip.addAll(Arrays.asList(annotation.value()));
        methodsToSkip.addAll(Arrays.asList(annotation.methods()));

        // Check if this method should be skipped by name
        if (methodsToSkip.contains(methodName)) {
            return ConditionEvaluationResult.disabled(
                "Test method '" + methodName + "' is excluded by @SkipInheritedTests(methods)"
            );
        }

        // Check if this method should be skipped by tag
        TestTag[] tagsToSkip = annotation.tags();
        if (tagsToSkip.length > 0) {
            // Convert TestTag enums to their string representations
            Set<String> tagNamesToSkip = Arrays.stream(tagsToSkip)
                    .map(TestTag::getTagName)
                    .collect(Collectors.toSet());

            // Get all tags on the test method (including meta-annotations)
            Set<String> methodTags = getTagsFromMethod(testMethod);

            // Check if any of the method's tags match the tags to skip
            for (String tag : methodTags) {
                if (tagNamesToSkip.contains(tag)) {
                    // Check if this method is in the include list
                    Set<String> methodsToInclude = new HashSet<>(Arrays.asList(annotation.include()));
                    if (methodsToInclude.contains(methodName)) {
                        return ConditionEvaluationResult.enabled(
                            "Test method '" + methodName + "' has tag '" + tag + "' but is explicitly included by @SkipInheritedTests(include)"
                        );
                    }

                    return ConditionEvaluationResult.disabled(
                        "Test method '" + methodName + "' has tag '" + tag + "' which is excluded by @SkipInheritedTests(tags)"
                    );
                }
            }
        }

        return ConditionEvaluationResult.enabled("Test method not in exclusion list");
    }

    /**
     * Extract all @Tag values from a method, including tags in meta-annotations.
     * This recursively searches through all annotations on the method to find @Tag annotations.
     */
    private Set<String> getTagsFromMethod(Method method) {
        Set<String> tags = new HashSet<>();

        // Get all annotations on the method
        for (Annotation annotation : method.getAnnotations()) {
            // Add tags from this annotation
            tags.addAll(getTagsFromAnnotation(annotation));
        }

        return tags;
    }

    /**
     * Recursively extract @Tag values from an annotation and its meta-annotations.
     */
    private Set<String> getTagsFromAnnotation(Annotation annotation) {
        Set<String> tags = new HashSet<>();

        // Check if this annotation is itself a @Tag
        if (annotation instanceof Tag) {
            tags.add(((Tag) annotation).value());
            return tags;
        }

        // Check if this annotation's type has @Tag as a meta-annotation
        Class<? extends Annotation> annotationType = annotation.annotationType();
        Tag[] metaTags = annotationType.getAnnotationsByType(Tag.class);
        for (Tag metaTag : metaTags) {
            tags.add(metaTag.value());
        }

        return tags;
    }
}
