package studio.phaseshift.metatron;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to skip inherited test methods by name or by tag.
 * Apply this to a test class to exclude specific inherited test methods from execution.
 *
 * Examples:
 * <pre>
 * {@code
 * // Skip by test method names
 * @SkipInheritedTests(methods = {"testAt", "testGet", "testNestedEvaluation"})
 * public class MySpaceTest extends AbstractSpaceTest {
 *     // Inherited tests with matching names will be skipped
 * }
 *
 * // Skip by tags/categories using enum
 * @SkipInheritedTests(tags = {TestTag.CRUD, TestTag.BOUNDARY})
 * public class MySpaceTest extends AbstractSpaceTest {
 *     // All inherited tests tagged with CRUD or BOUNDARY will be skipped
 * }
 *
 * // Skip by both
 * @SkipInheritedTests(
 *     tags = {TestTag.CRUD, TestTag.READ_WRITE},
 *     methods = {"testSpecialCase"}
 * )
 * public class MySpaceTest extends AbstractSpaceTest {
 *     // Tests matching either criteria will be skipped
 * }
 *
 * // Skip by tag but include specific methods
 * @SkipInheritedTests(
 *     tags = {TestTag.CRUD},
 *     include = {"testCreate", "testDelete"}
 * )
 * public class MySpaceTest extends AbstractSpaceTest {
 *     // All CRUD tests will be skipped EXCEPT testCreate and testDelete
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SkipInheritedTests {
    /**
     * Array of test method names to skip.
     * These should match the exact method names from the parent class.
     */
    String[] methods() default {};

    /**
     * Array of tags/categories to skip using TestTag enum.
     * Any test method annotated with @Tag matching these values will be skipped.
     */
    TestTag[] tags() default {};

    /**
     * Array of test method names to include even if they would be filtered out by tags.
     * This allows specific tests to run even if they have a tag that's being skipped.
     * Note: This only applies to tag-based filtering, not method name filtering.
     */
    String[] include() default {};

    /**
     * Shorthand for methods when only specifying method names.
     * Equivalent to methods = {...}
     */
    String[] value() default {};
}
