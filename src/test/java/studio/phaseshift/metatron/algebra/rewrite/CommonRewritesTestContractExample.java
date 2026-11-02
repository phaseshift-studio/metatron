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

package studio.phaseshift.metatron.algebra.rewrite;

import studio.phaseshift.metatron.furi.fURI;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Example implementations showing how different database types can implement
 * the CommonRewritesTestContract.
 *
 * <p>The contract now uses Metatron syntax for dataset creation, which works
 * across all database types automatically! You only need to implement one method.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CommonRewritesTestContractExample {

    /**
     * Example for SQL/tbleSpace implementation.
     *
     * <p>Minimal implementation - just specify the base URI and add test method wrappers!
     * The dataset is created automatically using Metatron syntax.
     *
     * <pre>{@code
     * public class SqliteTbleSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {
     *
     *     @Override
     *     public fURI getRewriteTestDatasetBaseUri() {
     *         return f("/tble/rewrite_test");
     *     }
     *
     *     // Add parameterized test with static provider
     *     @ParameterizedTest(name = "[{index}] {0}")
     *     @MethodSource("provideCountRewriteTestCases")
     *     public void testCountRewrite(String description, String expression, Obj expectedValue) throws Exception {
     *         CommonRewritesTestContract.super.testCountRewrite(description, expression, expectedValue);
     *     }
     *
     *     static Stream<Arguments> provideCountRewriteTestCases() {
     *         return new SqliteTbleSpaceTest().generateCountRewriteTestCases();
     *     }
     *
     *     // Repeat for sum, mean, etc. (or copy-paste the pattern)
     *
     *     // Optional: Override cleanup if you need to DROP TABLE
     *     @Override
     *     public void cleanupRewriteTestDataset() throws Exception {
     *         // Default implementation deletes via Metatron (writes noobj)
     *         // But for SQL you might want to drop the table:
     *         try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
     *              Statement stmt = conn.createStatement()) {
     *             stmt.executeUpdate("DROP TABLE IF EXISTS rewrite_test");
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p><b>Dataset is automatically created via Metatron:</b>
     * <pre>
     * /tble/rewrite_test/1 -> [id:1, value:1, name:'item1', weight:1.5]
     * /tble/rewrite_test/2 -> [id:2, value:2, name:'item2', weight:3.0]
     * ...
     * /tble/rewrite_test/10 -> [id:10, value:10, name:'item10', weight:15.0]
     * </pre>
     *
     * <p>The tbleSpace write() method automatically converts these to SQL INSERTs.
     */
    public static class SQLExample {
        private static final String DB_PATH = "target/test-db.db";

        public fURI getTestDataUriPrefix() {
            return f("/tble/rewrite_test");
        }

        // Optional: database-specific cleanup
        public void cleanupRewriteTestDataset() throws Exception {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS rewrite_test");
            }
        }
    }

    /**
     * Example for MongoDB/dcmntSpace implementation.
     *
     * <p>Same pattern as SQL - just specify the base URI and add test wrappers!
     *
     * <pre>{@code
     * public class dcmntSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {
     *
     *     @Override
     *     public fURI getRewriteTestDatasetBaseUri() {
     *         return f("mongo:rewrite_test");
     *     }
     *
     *     @ParameterizedTest(name = "[{index}] {0}")
     *     @MethodSource("provideCountRewriteTestCases")
     *     public void testCountRewrite(String description, String expression, Obj expectedValue) throws Exception {
     *         CommonRewritesTestContract.super.testCountRewrite(description, expression, expectedValue);
     *     }
     *
     *     static Stream<Arguments> provideCountRewriteTestCases() {
     *         return new dcmntSpaceTest().generateCountRewriteTestCases();
     *     }
     *
     *     // Repeat for sum, mean tests...
     *
     *     // No cleanup override needed! Default implementation deletes via Metatron.
     * }
     * }</pre>
     *
     * <p><b>Dataset is automatically created via Metatron:</b>
     * <pre>
     * mongo:rewrite_test/1 -> [id:1, value:1, name:'item1', weight:1.5]
     * mongo:rewrite_test/2 -> [id:2, value:2, name:'item2', weight:3.0]
     * ...
     * mongo:rewrite_test/10 -> [id:10, value:10, name:'item10', weight:15.0]
     * </pre>
     *
     * <p>The dcmntSpace write() method automatically converts these to MongoDB Documents.
     */
    public static class MongoDBExample {
        public fURI getTestDataUriPrefix() {
            return f("mongo:rewrite_test");
        }

        // No cleanup override needed - default implementation works perfectly
    }

    /**
     * Example with custom dataset values (different count/sum/mean).
     *
     * <p>If you want to use a different dataset, override the setup method:
     * <pre>{@code
     * public class customSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {
     *
     *     @Override
     *     public fURI getRewriteTestDatasetBaseUri() {
     *         return f("/custom/dataset");
     *     }
     *
     *     @Override
     *     public void setupRewriteTestDataset() throws Exception {
     *         final fURI baseUri = getRewriteTestDatasetBaseUri();
     *
     *         // Create custom dataset with values: 10, 20, 30, 40, 50
     *         // Count: 5, Sum: 150, Mean: 30
     *         for (int i = 1; i <= 5; i++) {
     *             final String mtronCode = String.format(
     *                 "%s/%d -> [id:%d, value:%d]",
     *                 baseUri, i, i, i * 10
     *             );
     *             mParser.eval(mtronCode);
     *         }
     *     }
     *
     *     @Override
     *     public long getExpectedCount() {
     *         return 5L;
     *     }
     *
     *     @Override
     *     public double getExpectedSum() {
     *         return 150.0;
     *     }
     *
     *     @Override
     *     public double getExpectedMean() {
     *         return 30.0;
     *     }
     * }
     * }</pre>
     */
    public static class CustomValuesExample {
        public fURI getTestDataUriPrefix() {
            return f("/custom/dataset");
        }

        // Override the expected values for custom test data
        public long getExpectedCount() {
            return 5L;
        }

        public double getExpectedSum() {
            return 150.0;
        }

        public double getExpectedMean() {
            return 30.0;
        }
    }
}
