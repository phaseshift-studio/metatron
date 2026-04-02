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

package studio.phaseshift.metatron.algebra.rewrite;

import studio.phaseshift.metatron.furi.fURI;

/**
 * Marker interface for tests that use common rewrite test datasets.
 * <p>
 * This interface indicates that a test class uses the standard rewrite test dataset
 * loaded from resources/rewrite_test_dataset.mtron. The dataset contains 10 records
 * with predictable values for testing count, sum, and mean operations.
 * <p>
 * To use this contract:
 * <ol>
 *   <li>Implement {@link #getTestDataUriPrefix()} to return the base URI for your space</li>
 *   <li>Add {@code @TestData(source = "rewrite_test_dataset.mtron")} to your test methods</li>
 *   <li>Use {@code $$} in your {@code @CsvSource} test data - it will be replaced via {@code make()}</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * @TestCategory.Rewrite
 * @ParameterizedTest
 * @TestData(source = "rewrite_test_dataset.mtron")
 * @CsvSource(value = {
 *     "$$/+>>value.sum()    % 55",
 *     "$$/+.count()         % 10",
 *     "$$/+>>value.mean()   % 5.5",
 * }, delimiter = '%')
 * public void testCommonRewrites(String code, String expected) throws Exception {
 *     String result = mParser.eval(make(code)).toString();
 *     assertEquals(expected, result);
 * }
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface CommonRewritesTestContract {

    /**
     * Returns the base URI prefix for test data.
     * This is used by the test dataset loader (via $$ replacement) to determine where to store test records.
     * <p>
     * Examples:
     * <ul>
     *   <li>SQL: {@code f("/tble/test/")}</li>
     *   <li>MongoDB: {@code f("mongo:test_collection/")}</li>
     * </ul>
     *
     * @return the base URI prefix for test data
     */
    fURI getTestDataUriPrefix();
}
