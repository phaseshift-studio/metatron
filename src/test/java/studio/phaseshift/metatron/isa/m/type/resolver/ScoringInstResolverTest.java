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
 * Test suite for {@link ScoringInstResolver}.
 * <p>
 * The ScoringInstResolver scores candidate instructions by specificity and selects
 * the highest-scoring match. This should resolve the "as() resolution miss" problem
 * where generic instructions were being selected over more specific ones.
 * <p>
 * Run with: mvn test -Dtest=ScoringInstResolverTest
 */
@DisplayName("ScoringInstResolver Tests")
public class ScoringInstResolverTest extends AbstractInstResolverTest {

    public ScoringInstResolverTest() {
        super(ScoringInstResolver::new);
    }

    // ========================================================================
    // SCORING-SPECIFIC TESTS
    // These tests verify that the scoring resolver correctly prefers
    // more specific instructions over generic ones.
    // ========================================================================

    /**
     * Tests that specific type conversions are resolved correctly without
     * explicit domain/range specification. This was the primary motivation
     * for the ScoringInstResolver - previously these required explicit
     * specifications like as?bytes<=file(bytes::T).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1.as(str::T)                                                           % \"1\"",
            "1.as(real::T)                                                          % 1.0",
            "1.5.as(int::T)                                                         % 1",
            "1.5.as(str::T)                                                         % \"1.5\"",
            "'42'.as(int::T)                                                        % 42",
            "'3.14'.as(real::T)                                                     % 3.14",
            "'true'.as(bool::T)                                                     % true",
            "true.as(str::T)                                                        % \"true\"",
            "true.as(int::T)                                                        % 1",
            "false.as(int::T)                                                       % 0",
    }, delimiter = '%', quoteCharacter = '~')
    @DisplayName("as() specificity: should resolve without explicit dom/rng")
    public void testAsSpecificity(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Tests byte array conversions which require specific resolver behavior.
     * The scoring resolver should prefer bytes.as(str) over A.as(type).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "0x48656c6c6f.as(str::T)                                                % \"Hello\"",
            "0x776f726c64.as(str::T)                                                % \"world\"",
            "\"abc\".as(bytes::T)                                                    % 0x616263",
    }, delimiter = '%')
    @DisplayName("bytes/str conversion: should use specific converters")
    public void testBytesStrConversion(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Tests that chained as() conversions work correctly.
     * Each step should resolve to the most specific converter.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "42.as(real::T).as(str::T)                                              % \"42.0\"",
            "'10'.as(int::T).as(real::T)                                            % 10.0",
            "true.as(int::T).as(str::T)                                             % \"1\"",
            "1.0.as(int::T).as(real::T)                                             % 1.0",
            "1.0.as(int::T).as(real::T).as(str::T)                                  % \"1.0\"",
    }, delimiter = '%', quoteCharacter = '~')
    @DisplayName("chained as(): each step should use specific converter")
    public void testChainedAsConversions(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Tests that operations on different numeric types resolve correctly.
     * int.plus(int) should use int-specific impl, real.plus(real) should use real-specific.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "10.plus(5).plus(15)                                                    % 30",
            "100.minus(25).minus(60)                                                % 15",
            "10.0.plus(5.0).plus(15.0)                                              % 30.0",
            "100.0.minus(25.0).minus(60.0)                                          % 15.0",
    }, delimiter = '%')
    @DisplayName("numeric operations: should use type-specific implementations")
    public void testNumericSpecificity(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Tests count() on different types - each type should use its specific implementation.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3,4,5]>-.count()                                                    % 5",
            "[,]>-.count()                                                            % 0",
            "[a=>1,b=>2,c=>3]>-.count()                                               % 3",
            "[x=>1]>-.count()                                                         % 1",
    }, delimiter = '%')
    @DisplayName("count(): should use type-specific implementations")
    public void testCountSpecificity(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }
}
