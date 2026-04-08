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

package studio.phaseshift.metatron.isa.m.type.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test suite for {@link FirstFindInstResolver}.
 * <p>
 * The FirstFindInstResolver uses the original findFirst() algorithm which
 * returns the first matching instruction without considering specificity.
 * This is preserved for backward compatibility and A/B testing.
 * <p>
 * Note: Some tests that work with ScoringInstResolver may fail here due to
 * the non-deterministic nature of findFirst() when multiple instructions match.
 * <p>
 * Run with: mvn test -Dtest=FirstFindInstResolverTest
 */
@DisplayName("FirstFindInstResolver Tests")
public class FirstFindInstResolverTest extends AbstractInstResolverTest {

    public FirstFindInstResolverTest() {
        super(FirstFindInstResolver::new);
    }

    // ========================================================================
    // FIRST-FIND SPECIFIC TESTS
    // These tests document the behavior (and limitations) of the original
    // findFirst() approach.
    // ========================================================================

    /**
     * Basic operations should work fine with FirstFind since there's typically
     * only one matching implementation for specific type+operation combinations.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)                                                              % 3",
            "10.mult(5)                                                             % 50",
            "7.minus(3)                                                             % 4",
            "20.0.mult(0.25)                                                          % 5.0",
            "1.5.plus(2.5)                                                          % 4.0",
            "3.0.mult(2.0)                                                          % 6.0",
    }, delimiter = '%')
    @DisplayName("Basic operations: should work with findFirst")
    public void testBasicOperationsFirstFind(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Comparison operations typically have clear type-specific implementations.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.gt(3)                                                                % true",
            "3.gt(5)                                                                % false",
            "5.eq(5)                                                                % true",
            "5.eq(3)                                                                % false",
    }, delimiter = '%')
    @DisplayName("Comparisons: should work with findFirst")
    public void testComparisonsFirstFind(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * List operations are typically unambiguous.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]>-.count()                                                        % 3",
            "[1,2,3]>-.sum()                                                          % 6",
    }, delimiter = '%')
    @DisplayName("List operations: should work with findFirst")
    public void testListOperationsFirstFind(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * String operations are typically unambiguous.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "'hello'.plus(' world')                                                 % \"hello world\"",
            "'ABC'.lcase()                                                          % \"abc\"",
            "'abc'.ucase()                                                          % \"ABC\"",
    }, delimiter = '%', quoteCharacter = '~')
    @DisplayName("String operations: should work with findFirst")
    public void testStringOperationsFirstFind(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Note: as() operations may have inconsistent behavior with FirstFind
     * due to multiple matching implementations. These tests document
     * cases that should work regardless of order.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1.as(real::T)                                                          % 1.0",
            "1.5.as(int::T)                                                         % 1",
    }, delimiter = '%')
    @DisplayName("as() basic: some conversions may work with findFirst")
    public void testAsBasicFirstFind(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }
}
