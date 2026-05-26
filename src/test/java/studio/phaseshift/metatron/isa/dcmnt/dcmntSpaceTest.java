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

package studio.phaseshift.metatron.isa.dcmnt;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewritesTestContract;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.dcmnt.space.dcmntSpace;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet.DCMNT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for dcmntSpace with in-memory MongoDB.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class dcmntSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {//, SubQTest {

    protected static MongoServer mongoServer;
    protected static String connectionString;
    protected static final String DB_NAME = "testdb";
    protected static final fURI SPACE_VID = f("/sys/space/doc/test");

    public dcmntSpaceTest() {
        super(f("mongo:test_collection/rewrite_test"), () -> dcmntSpace.of(
                rec(
                        uri(PATTERN), uri("mongo:#"),
                        uri(HOST), uri(connectionString + "/" + DB_NAME),
                        uri(ROUTE), rec(uri("mongo:"), uri("")),
                        uri(COLLECTION), lst()
                ).jvm(),
                SPACE_VID
        ));

    }

    @Override
    public String make(final String expression, final Method testMethod) {
        // For testMonoUpdate, $$ → mongo: so seed data writes to mongo:<collection>/<docId>
        // and update/read expressions resolve to the same two-segment document paths.
        if (testMethod != null && ("testMonoUpdate".equals(testMethod.getName()) || "testMonoDepth".equals(testMethod.getName()))) {
            return expression.contains("$$") ? expression.replace("$$/", "mongo:") : expression;
        }
        return super.make(expression, testMethod);
    }

    @BeforeAll
    public static void setupInstSet() {
        InstSet.importInstSet(DCMNT_ISA_TID);
    }

    // Disable all abstract tests - dcmntSpace has its own comprehensive MongoDB-specific tests
    @Override
    @Disabled
    public void testStringCornerCases(String description, String value) {
    }

    @Override
    @Disabled
    public void testIntegerBoundaries(String description, long value) {
    }

    @Override
    @Disabled
    public void testRealBoundaries(String description, double value) {
    }

    @Override
    @Disabled
    public void testBooleanValues(String description, boolean value) {
    }

    @Override
    @Disabled
    public void testSequentialUpdates(int iterations) {
    }

    @Override
    @Disabled
    public void testBasicCRUD(String description, String key, String valueStr) {
    }

    @Override
    @Disabled
    public void testTypePreservation(String description, Obj value) {
    }

    @Override
    @Disabled
    public void testNestedRecords(int depth) {
    }

    @Override
    @Disabled
    public void testListHandling(String description, studio.phaseshift.metatron.isa.m.type.Lst listValue, int expectedCount) {
    }

    @Override
    @Disabled
    public void testTypeChanges(String description, Obj initialValue, Obj updatedValue) {
    }

    @Override
    @Disabled
    public void testMultiFieldUpdates(int fieldCount) {
    }

    @Override
    @Disabled
    public void testSpecialStringValues(String description, String value) {
    }

    /**
     * dcmntSpace enforces that only Recs (documents) can be written at the collection/docId root.
     * When the space rejects a non-Rec write (List, Int, Str, etc.) at the document root,
     * skip the inherited test case rather than fail it — the rejection IS the correct behavior.
     */
    @Override
    protected boolean expectWriteRejection(final Obj writeFailObj) {
        // Any root type constraint rejection from this space is expected and should cause the test to be skipped
        if (!writeFailObj.isFail() && !writeFailObj.isNoObj()) return false;
        final String msg = writeFailObj.asFail().message().getMessage();
        return msg != null && msg.contains("requires") && msg.contains("at root");
    }

    @BeforeAll
    public static void setupAll() {
        AbstractMetatronTest.begin();
        // Start in-memory MongoDB server
        mongoServer = new MongoServer(new MemoryBackend());
        final InetSocketAddress bindAddress = mongoServer.bind();
        connectionString = "mongodb://" + bindAddress.getHostString() + ":" + bindAddress.getPort();
        STATIC_LOG.info("started in-memory mongodb at " + connectionString);
    }

    @AfterAll
    public static void stopAll() {
        AbstractMetatronTest.end();
        if (mongoServer != null) {
            mongoServer.shutdown();
            STATIC_LOG.info("shutdown in-memory mongodb");
        }
    }

    @BeforeEach
    public void setupTestData() {
        // Create test collections and documents
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            // Create users collection
            final MongoCollection<Document> users = db.getCollection("users");
            users.drop(); // Clean slate

            users.insertOne(new Document()
                    .append("_id", "user1")
                    .append("name", "Alice")
                    .append("age", 30)
                    .append("email", "alice@example.com")
                    .append("active", true));

            users.insertOne(new Document()
                    .append("_id", "user2")
                    .append("name", "Bob")
                    .append("age", 25)
                    .append("email", "bob@example.com")
                    .append("active", true));

            users.insertOne(new Document()
                    .append("_id", "user3")
                    .append("name", "Charlie")
                    .append("age", 35)
                    .append("email", "charlie@example.com")
                    .append("active", false));

            // create products collection
            MongoCollection<Document> products = db.getCollection("products");
            products.drop();

            products.insertOne(new Document()
                    .append("_id", "prod1")
                    .append("name", "Laptop")
                    .append("price", 1299.99)
                    .append("inStock", true)
                    .append("quantity", 15));

            products.insertOne(new Document()
                    .append("_id", "prod2")
                    .append("name", "Mouse")
                    .append("price", 29.99)
                    .append("inStock", true)
                    .append("quantity", 50));

            // Create rewrite_test collection for CommonRewritesTestContract
            // 10 docs: id=1-10, value=1-10, name='item1'-'item10', active=alternating true/false
            final MongoCollection<Document> rewriteTest = db.getCollection("rewrite_test");
            rewriteTest.drop();

            for (int i = 1; i <= 10; i++) {
                final boolean active = (i % 2 == 1); // odd = true, even = false
                rewriteTest.insertOne(new Document()
                        .append("_id", String.valueOf(i))
                        .append("id", i)
                        .append("value", i)
                        .append("name", "item" + i)
                        .append("active", active));
            }

            LOG.info("test data setup complete");
        }
    }

    @Test
    public void testReadSingleDocument() {
        LOG.warn("testing read single document");
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read a specific user
            final Obj user1 = space.read(f("mongo:users/user1"));

            assertFalse(user1.isNoObj(), "User1 should exist");
            assertTrue(user1.isRec(), "User1 should be a record");

            final Rec user1Rec = user1.asRec();
            assertEquals(str("Alice"), user1Rec.at(uri(NAME)), "Name should be Alice");
            assertEquals(jnt(30), user1Rec.at(uri("age")), "Age should be 30");
            assertEquals(str("alice@example.com"), user1Rec.at(uri("email")), "Email should match");
            assertEquals(bool(true), user1Rec.at(uri("active")), "Should be active");

            LOG.info("Successfully read user1: %s", user1);
        } finally {
            space.close();
        }
    }

    @Test
    public void testReadNonExistentDocument() {
        LOG.info("Testing read non-existent document");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj result = space.read(f("mongo:users/nonexistent"));
            assertTrue(result.isNoObj(), "Non-existent document should return noobj");
        } finally {
            space.close();
        }
    }

    @Test
    public void testReadAllDocumentsInCollection() {
        LOG.info("Testing read all documents in collection");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read all users using + pattern
            final Obj allUsers = space.read(f("mongo:users/+"));

            assertFalse(allUsers.isNoObj(), "Should return results");
            assertTrue(allUsers.isObjs(), "Should return multiple objects");

            // Count the results
            int count = 0;
            for (Obj user : allUsers.asObjs()) {
                count++;
                assertTrue(user.isRec(), "Each user should be a record");
            }

            assertEquals(3, count, "Should have 3 users");
            LOG.info("Successfully read %s users", count);
        } finally {
            space.close();
        }
    }

    @Test
    public void testWriteNewDocument() {
        LOG.info("Testing write new document");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write a new user
            final Rec newUser = rec(
                    uri(NAME), str("Diana"),
                    uri("age"), jnt(28),
                    uri("email"), str("diana@example.com"),
                    uri("active"), bool(true)
            );

            space.write(f("mongo:users/user4"), newUser);

            // Read it back
            final Obj readBack = space.read(f("mongo:users/user4"));
            assertFalse(readBack.isNoObj(), "New user should exist");
            assertTrue(readBack.isRec(), "New user should be a record");

            final Rec readBackRec = readBack.asRec();
            assertEquals(str("Diana"), readBackRec.at(uri(NAME)), "Name should be Diana");
            assertEquals(jnt(28), readBackRec.at(uri("age")), "Age should be 28");

            LOG.info("Successfully wrote and read back new user");
        } finally {
            space.close();
        }
    }

    @Test
    public void testUpdateExistingDocument() {
        LOG.info("Testing update existing document");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Update user1
            final Rec updatedUser = rec(
                    uri(NAME), str("Alice Updated"),
                    uri("age"), jnt(31),
                    uri("email"), str("alice.new@example.com"),
                    uri("active"), bool(true)
            );

            space.write(f("mongo:users/user1"), updatedUser);

            // Read it back
            final Obj readBack = space.read(f("mongo:users/user1"));
            final Rec readBackRec = readBack.asRec();

            assertEquals(str("Alice Updated"), readBackRec.at(uri(NAME)), "Name should be updated");
            assertEquals(jnt(31), readBackRec.at(uri("age")), "Age should be updated");
            assertEquals(str("alice.new@example.com"), readBackRec.at(uri("email")), "Email should be updated");

            LOG.info("Successfully updated user1");
        } finally {
            space.close();
        }
    }

    @Test
    public void testDeleteDocument() {
        LOG.info("Testing delete document");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Verify user2 exists
            Obj user2 = space.read(f("mongo:users/user2"));
            assertFalse(user2.isNoObj(), "User2 should exist before deletion");

            // Delete user2
            space.write(f("mongo:users/user2"), noobj());

            // Verify it's gone
            user2 = space.read(f("mongo:users/user2"));
            assertTrue(user2.isNoObj(), "User2 should not exist after deletion");

            LOG.info("Successfully deleted user2");
        } finally {
            space.close();
        }
    }

    @Test
    public void testNestedDocuments() {
        LOG.info("Testing nested documents");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write a document with nested structure
            final Rec nestedDoc = rec(
                    uri(NAME), str("Eve"),
                    uri("age"), jnt(40),
                    uri("address"), rec(
                            uri("street"), str("123 Main St"),
                            uri("city"), str("Springfield"),
                            uri("zip"), str("12345")
                    ),
                    uri("tags"), lst(str("admin"), str("developer"), str("manager"))
            );

            space.write(f("mongo:users/user5"), nestedDoc);

            // Read it back
            final Obj readBack = space.read(f("mongo:users/user5"));
            assertFalse(readBack.isNoObj(), "Nested document should exist");

            final Rec readBackRec = readBack.asRec();
            assertEquals(str("Eve"), readBackRec.at(uri(NAME)), "Name should be Eve");

            // Check nested address
            final Obj address = readBackRec.at(uri("address"));
            assertTrue(address.isRec(), "Address should be a record");
            assertEquals(str("Springfield"), address.asRec().at(uri("city")), "City should be Springfield");

            // Check tags list
            final Obj tags = readBackRec.at(uri("tags"));
            assertTrue(tags.isLst(), "Tags should be a list");
            assertEquals(3, tags.asLst().count(), "Should have 3 tags");

            LOG.info("Successfully handled nested document");
        } finally {
            space.close();
        }
    }

    @Test
    public void testMultipleDataTypes() {
        LOG.info("Testing multiple data types");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write a document with various data types
            final Rec multiTypeDoc = rec(
                    uri("stringField"), str("test string"),
                    uri("intField"), jnt(42),
                    uri("realField"), real(3.14159),
                    uri("boolField"), bool(true),
                    uri("listField"), lst(jnt(1), jnt(2), jnt(3)),
                    uri("nestedField"), rec(uri("inner"), str("value"))
            );

            space.write(f("mongo:products/prod3"), multiTypeDoc);

            // Read it back
            final Obj readBack = space.read(f("mongo:products/prod3"));
            final Rec readBackRec = readBack.asRec();

            assertEquals(str("test string"), readBackRec.at(uri("stringField")), "String should match");
            assertEquals(jnt(42), readBackRec.at(uri("intField")), "Int should match");
            assertEquals(real(3.14159), readBackRec.at(uri("realField")), "Real should match");
            assertEquals(bool(true), readBackRec.at(uri("boolField")), "Bool should match");

            final Obj listField = readBackRec.at(uri("listField"));
            assertTrue(listField.isLst(), "List field should be a list");
            assertEquals(3, listField.asLst().count(), "List should have 3 elements");

            final Obj nestedField = readBackRec.at(uri("nestedField"));
            assertTrue(nestedField.isRec(), "Nested field should be a record");

            LOG.info("Successfully handled multiple data types");
        } finally {
            space.close();
        }
    }

    @Test
    public void testReadMultipleCollections() {
        LOG.info("Testing read from multiple collections");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read from users collection
            final Obj user = space.read(f("mongo:users/user1"));
            assertFalse(user.isNoObj(), "User should exist");
            assertEquals(str("Alice"), user.asRec().at(uri(NAME)), "User name should be Alice");

            // Read from products collection
            final Obj product = space.read(f("mongo:products/prod1"));
            assertFalse(product.isNoObj(), "Product should exist");
            assertEquals(str("Laptop"), product.asRec().at(uri(NAME)), "Product name should be Laptop");

            LOG.info("Successfully read from multiple collections");
        } finally {
            space.close();
        }
    }

    @Test
    public void testEmptyList() {
        LOG.info("Testing empty list handling");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Rec docWithEmptyList = rec(
                    uri(NAME), str("Test"),
                    uri("emptyList"), lst()
            );

            space.write(f("mongo:users/user6"), docWithEmptyList);

            final Obj readBack = space.read(f("mongo:users/user6"));
            final Obj emptyList = readBack.asRec().at(uri("emptyList"));

            assertTrue(emptyList.isLst(), "Should be a list");
            assertEquals(0, emptyList.asLst().count(), "List should be empty");

            LOG.info("Successfully handled empty list");
        } finally {
            space.close();
        }
    }

    @Test
    public void testLargeDocument() {
        LOG.info("Testing large document with many fields");

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Create a document with many fields
            final Map<Obj, Obj> fields = new LinkedHashMap<>();
            for (int i = 0; i < 50; i++) {
                fields.put(uri("field" + i), str("value" + i));
            }

            final Rec largeDoc = rec(fields);
            space.write(f("mongo:users/largeDoc"), largeDoc);

            // Read it back
            final Obj readBack = space.read(f("mongo:users/largeDoc"));
            assertFalse(readBack.isNoObj(), "Large document should exist");

            final Rec readBackRec = readBack.asRec();
            assertEquals(50, readBackRec.jvm().size(), "Should have 50 fields");

            // Verify a few fields
            assertEquals(str("value0"), readBackRec.at(uri("field0")), "Field0 should match");
            assertEquals(str("value25"), readBackRec.at(uri("field25")), "Field25 should match");
            assertEquals(str("value49"), readBackRec.at(uri("field49")), "Field49 should match");

            LOG.info("Successfully handled large document with 50 fields");
        } finally {
            space.close();
        }
    }

    // ========================================
    // Parameterized Tests
    // ========================================

    @ParameterizedTest
    @CsvSource(value = {
            "user1     | Alice         | 30       | false",
            "user2     | Bob           | 25       | false",
            "user3     | Charlie       | 35       | false",
            "emptyId   | <NONE>        | 0        | true",   // empty string ID test
            "noSuchId  | <NONE>        | 0        | true"    // non-existent ID
    }, delimiter = '|')
    public void testReadUserByIdParameterized(final String userId, final String expectedName,
                                              final int expectedAge, final boolean shouldBeNoObj) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj user = space.read(f("mongo:users/" + userId));

            if (shouldBeNoObj) {
                assertTrue(user.isNoObj(), "User " + userId + " should not exist");
            } else {
                assertFalse(user.isNoObj(), "User " + userId + " should exist");
                assertTrue(user.isRec(), "User should be a record");

                final Rec userRec = user.asRec();
                assertEquals(str(expectedName), userRec.at(uri(NAME)), "Name should match");
                assertEquals(jnt(expectedAge), userRec.at(uri("age")), "Age should match");
            }
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "prod1     | Laptop    | 1299.99   | 15",
            "prod2     | Mouse     | 29.99     | 50"
    }, delimiter = '|')
    public void testReadProductByIdParameterized(final String productId, final String expectedName,
                                                 final double expectedPrice, final int expectedQuantity) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj product = space.read(f("mongo:products/" + productId));
            assertFalse(product.isNoObj(), "Product " + productId + " should exist");
            assertTrue(product.isRec(), "Product should be a record");

            final Rec productRec = product.asRec();
            assertEquals(str(expectedName), productRec.at(uri(NAME)), "Name should match");
            assertEquals(real(expectedPrice), productRec.at(uri("price")), "Price should match");
            assertEquals(jnt(expectedQuantity), productRec.at(uri("quantity")), "Quantity should match");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "testUser1 | Alice Smith   | 28  | alice.smith@test.com",
            "testUser2 | Bob Jones     | 35  | bob.jones@test.com",
            "testUser3 | Carol White   | 42  | carol.white@test.com",
            "testUser4 | David Brown   | 31  | david.brown@test.com",
            "testUser5 | Eve Davis     | 27  | eve.davis@test.com",
            "testUser6 | x             | 0   | x",                     // minimal strings
            "testUser7 | Name Only     | 0   | none@test.com",         // zero age
            "testUser8 | Empty Email   | 100 | email@test.com",        // large age
            "testUser9 | Negative Age  | -5  | negative@test.com",     // negative age
            "testUser10| Zero Age      | 0   | zero@test.com"          // zero age
    }, delimiter = '|')
    public void testWriteAndReadParameterized(final String userId, final String name,
                                              final int age, final String email) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write user
            final Rec newUser = rec(
                    uri(NAME), str(name),
                    uri("age"), jnt(age),
                    uri("email"), str(email),
                    uri("active"), bool(true)
            );
            space.write(f("mongo:users/" + userId), newUser);

            // Read back and verify
            final Obj readBack = space.read(f("mongo:users/" + userId));
            assertFalse(readBack.isNoObj(), "User " + userId + " should exist");

            final Rec readBackRec = readBack.asRec();
            assertEquals(str(name), readBackRec.at(uri(NAME)), "Name should match");
            assertEquals(jnt(age), readBackRec.at(uri("age")), "Age should match");
            assertEquals(str(email), readBackRec.at(uri("email")), "Email should match");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "updateUser1 | Original Name | 25  | Updated Name  | 26",
            "updateUser2 | John Doe      | 30  | Jane Doe      | 31",
            "updateUser3 | Test User     | 40  | Test User 2   | 41",
            "updateUser4 | Has Name      | 50  | x             | 0",    // update to minimal string
            "updateUser5 | Positive Age  | 100 | Negative Age  | -10",  // update to negative
            "updateUser6 | Old           | 1   | Old           | 1",    // no change update
            "updateUser7 | x             | 0   | New Name      | 99"    // update from minimal
    }, delimiter = '|')
    public void testUpdateParameterized(final String userId, final String originalName, final int originalAge,
                                        final String updatedName, final int updatedAge) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write original
            space.write(f("mongo:users/" + userId), rec(
                    uri(NAME), str(originalName),
                    uri("age"), jnt(originalAge)
            ));

            // Update
            space.write(f("mongo:users/" + userId), rec(
                    uri(NAME), str(updatedName),
                    uri("age"), jnt(updatedAge)
            ));

            // Verify update
            final Obj readBack = space.read(f("mongo:users/" + userId));
            final Rec readBackRec = readBack.asRec();
            assertEquals(str(updatedName), readBackRec.at(uri(NAME)), "Name should be updated");
            assertEquals(jnt(updatedAge), readBackRec.at(uri("age")), "Age should be updated");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "deleteUser1 | Test User 1",
            "deleteUser2 | Test User 2",
            "deleteUser3 | Test User 3"
    }, delimiter = '|')
    public void testDeleteParameterized(final String userId, final String name) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Create user
            space.write(f("mongo:users/" + userId), rec(uri(NAME), str(name)));

            // Verify exists
            assertFalse(space.read(f("mongo:users/" + userId)).isNoObj(),
                    "User should exist before deletion");

            // Delete
            space.write(f("mongo:users/" + userId), noobj());

            // Verify deleted
            assertTrue(space.read(f("mongo:users/" + userId)).isNoObj(),
                    "User should not exist after deletion");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "nestedUser1 | Alice   | 123 Main St   | Springfield | 12345",
            "nestedUser2 | Bob     | 456 Oak Ave   | Portland    | 67890",
            "nestedUser3 | Charlie | 789 Pine Rd   | Seattle     | 54321",
            "nestedUser4 | x       | x             | x           | x",      // minimal nested fields
            "nestedUser5 | Dave    | none          | CityOnly    | none",   // partial data
            "nestedUser6 | Eve     | Street Only   | none        | 00000"   // different partial
    }, delimiter = '|')
    public void testNestedDocumentsParameterized(final String userId, final String name,
                                                 final String street, final String city, final String zip) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Rec nestedDoc = rec(
                    uri(NAME), str(name),
                    uri("address"), rec(
                            uri("street"), str(street),
                            uri("city"), str(city),
                            uri("zip"), str(zip)
                    )
            );

            space.write(f("mongo:users/" + userId), nestedDoc);

            final Obj readBack = space.read(f("mongo:users/" + userId));
            final Rec readBackRec = readBack.asRec();

            assertEquals(str(name), readBackRec.at(uri(NAME)), "Name should match");

            final Obj address = readBackRec.at(uri("address"));
            assertTrue(address.isRec(), "Address should be a record");
            assertEquals(str(city), address.asRec().at(uri("city")), "City should match");
            assertEquals(str(street), address.asRec().at(uri("street")), "Street should match");
            assertEquals(str(zip), address.asRec().at(uri("zip")), "Zip should match");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "listUser1 | Alice   | admin,developer,manager",
            "listUser2 | Bob     | user,viewer",
            "listUser3 | Charlie | admin,superuser,auditor,developer",
            "listUser4 | Dave    | single",                              // single item list
            "listUser5 | Eve     | <EMPTY>",                             // empty list marker
            "listUser6 | Frank   | x,x,x",                               // list with minimal strings
            "listUser7 | Grace   | a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z"  // large list
    }, delimiter = '|')
    public void testListFieldsParameterized(final String userId, final String name, final String tagsStr) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final String[] tagArray = tagsStr.equals("<EMPTY>") ? new String[0] : tagsStr.split(",");
            final Rec docWithList = rec(
                    uri(NAME), str(name),
                    uri("tags"), lst(java.util.Arrays.stream(tagArray).<Obj>map(MStr::str))
            );

            space.write(f("mongo:users/" + userId), docWithList);

            final Obj readBack = space.read(f("mongo:users/" + userId));
            final Obj tags = readBack.asRec().at(uri("tags"));

            assertTrue(tags.isLst(), "Tags should be a list");
            assertEquals(tagArray.length, tags.asLst().count(), "Should have correct number of tags");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "typeTest1 | test string 1 | 42         | 3.14159   | true",
            "typeTest2 | test string 2 | 100        | 2.71828   | false",
            "typeTest3 | test string 3 | -50        | 1.41421   | true",
            "typeTest4 | x             | 0          | 0.0       | false",  // minimal string, zeros
            "typeTest5 | special !@#$  | -2147483648| -999.999  | true",   // special chars, min int, negative real
            "typeTest6 | unicode 你好   | 2147483647 | 999999.99 | false",  // unicode, max int, large real
            "typeTest7 | negative      | -1         | -0.0      | true"    // negative values
    }, delimiter = '|')
    public void testMultipleDataTypesParameterized(final String docId, final String strVal,
                                                   final int intVal, final double realVal, final boolean boolVal) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Rec multiTypeDoc = rec(
                    uri("stringField"), str(strVal),
                    uri("intField"), jnt(intVal),
                    uri("realField"), real(realVal),
                    uri("boolField"), bool(boolVal)
            );

            space.write(f("mongo:products/" + docId), multiTypeDoc);

            final Obj readBack = space.read(f("mongo:products/" + docId));
            final Rec readBackRec = readBack.asRec();

            assertEquals(str(strVal), readBackRec.at(uri("stringField")), "String should match");
            assertEquals(jnt(intVal), readBackRec.at(uri("intField")), "Int should match");
            assertEquals(real(realVal), readBackRec.at(uri("realField")), "Real should match");
            assertEquals(bool(boolVal), readBackRec.at(uri("boolField")), "Bool should match");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "nonExistent1",
            "nonExistent2",
            "nonExistent3",
            "fakeUser123",
            "missingDoc",
            "user_with_underscores",   // ID with underscores
            "user-with-dashes",        // ID with dashes
            "user.with.dots",          // ID with dots
            "user@special#chars",      // ID with special characters
            "verylongidthatgoesonyesverylongidthatgoesonyesverylongidthatgoesonyesverylongidthatgoeson"  // very long ID
    })
    public void testReadNonExistentDocumentParameterized(final String docId) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj result = space.read(f("mongo:users/" + docId));
            assertTrue(result.isNoObj(), "Non-existent document " + docId + " should return noobj");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "users     | 3",  // 3 users from setup
            "products  | 2"   // 2 products from setup
    }, delimiter = '|')
    public void testCollectionCountParameterized(final String collectionName, final int expectedMinCount) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj allDocs = space.read(f("mongo:" + collectionName + "/+"));
            assertFalse(allDocs.isNoObj(), "Should return results for " + collectionName);
            assertTrue(allDocs.isObjs(), "Should return multiple objects");

            int count = 0;
            for (Obj doc : allDocs.asObjs()) {
                count++;
                assertTrue(doc.isRec(), "Each document should be a record");
            }

            assertTrue(count >= expectedMinCount,
                    "Should have at least " + expectedMinCount + " documents in " + collectionName);
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "emptyRec1  | <EMPTY_REC>",
            "emptyRec2  | <EMPTY_REC>",
            "emptyRec3  | <EMPTY_REC>"
    }, delimiter = '|')
    public void testWriteEmptyRecordParameterized(final String docId, final String marker) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write empty record
            final Rec emptyRec = rec();
            space.write(f("mongo:users/" + docId), emptyRec);

            // Read back and verify
            final Obj readBack = space.read(f("mongo:users/" + docId));
            assertFalse(readBack.isNoObj(), "Empty record should exist");
            assertTrue(readBack.isRec(), "Should be a record");

            // _id is stripped from returned records (encoded in URI path); empty rec has 0 fields
            final Rec readBackRec = readBack.asRec();
            assertEquals(0, readBackRec.jvm().size(), "Empty record should have no fields");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "boundaryTest1 | 2147483647  | 9223372036854775807",   // max int, max long
            "boundaryTest2 | -2147483648 | -9223372036854775808",  // min int, min long
            "boundaryTest3 | 0           | 0",                     // zeros
            "boundaryTest4 | 1           | 1",                     // ones
            "boundaryTest5 | -1          | -1"                     // negative ones
    }, delimiter = '|')
    public void testBoundaryValuesParameterized(final String docId, final int intVal, final long longVal) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Rec boundaryDoc = rec(
                    uri("intField"), jnt(intVal),
                    uri("longField"), jnt(longVal)
            );

            space.write(f("mongo:products/" + docId), boundaryDoc);

            final Obj readBack = space.read(f("mongo:products/" + docId));
            final Rec readBackRec = readBack.asRec();

            assertEquals(jnt(intVal), readBackRec.at(uri("intField")), "Int should match");
            assertEquals(jnt(longVal), readBackRec.at(uri("longField")), "Long should match");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "specialStr1 | x",
            "specialStr2 | test string with spaces",
            "specialStr3 | special!@#$%^&*()",
            "specialStr4 | unicode_你好世界",
            "specialStr5 | a_very_long_string_that_goes_on_and_on_and_on_and_on_and_on",
            "specialStr6 | numbers123456789",
            "specialStr7 | MixedCaseString"
    }, delimiter = '|')
    public void testSpecialStringValuesParameterized(final String docId, final String specialStr) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Rec doc = rec(uri("specialField"), str(specialStr));
            space.write(f("mongo:users/" + docId), doc);

            final Obj readBack = space.read(f("mongo:users/" + docId));
            final Rec readBackRec = readBack.asRec();

            assertEquals(str(specialStr), readBackRec.at(uri("specialField")), "Special string should match");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "deepNest1 | 1",
            "deepNest2 | 2",
            "deepNest3 | 3",
            "deepNest4 | 5",
            "deepNest5 | 10"
    }, delimiter = '|')
    public void testDeeplyNestedDocumentsParameterized(final String docId, final int depth) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Build deeply nested structure
            Obj nested = str("deepest value");
            for (int i = 0; i < depth; i++) {
                nested = rec(uri("level" + i), nested);
            }

            space.write(f("mongo:users/" + docId), (Rec) nested);

            // Read back and verify depth
            final Obj readBack = space.read(f("mongo:users/" + docId));
            assertFalse(readBack.isNoObj(), "Nested document should exist");

            // Navigate down the nesting
            Obj current = readBack;
            for (int i = depth - 1; i >= 0; i--) {
                assertTrue(current.isRec(), "Level " + i + " should be a record");
                current = current.asRec().at(uri("level" + i));
            }

            assertEquals(str("deepest value"), current, "Should reach deepest value");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "mixedList1 | 1     | hello | 3.14  | true",
            "mixedList2 | 0     | x     | 0.0   | false",
            "mixedList3 | -1    | world | -2.5  | false",
            "mixedList4 | 100   | test  | 99.99 | true",
            "mixedList5 | -999  | neg   | -1.0  | false"
    }, delimiter = '|')
    public void testMixedTypeListsParameterized(final String docId, final int intVal,
                                                final String strVal, final double realVal, final boolean boolVal) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Rec doc = rec(
                    uri("mixedList"), lst(
                            jnt(intVal),
                            str(strVal),
                            real(realVal),
                            bool(boolVal)
                    )
            );

            space.write(f("mongo:users/" + docId), doc);

            final Obj readBack = space.read(f("mongo:users/" + docId));
            final Obj mixedList = readBack.asRec().at(uri("mixedList"));

            assertTrue(mixedList.isLst(), "Should be a list");
            assertEquals(4, mixedList.asLst().count(), "Should have 4 elements");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "deleteNonExist1 | <ERROR>",
            "deleteNonExist2 | <ERROR>",
            "deleteNonExist3 | <ERROR>"
    }, delimiter = '|')
    public void testDeleteNonExistentDocumentParameterized(final String docId, final String marker) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Verify doesn't exist
            assertTrue(space.read(f("mongo:users/" + docId)).isNoObj(),
                    "Document should not exist initially");

            // Delete non-existent document (should not throw error, just no-op)
            space.write(f("mongo:users/" + docId), noobj());

            // Verify still doesn't exist
            assertTrue(space.read(f("mongo:users/" + docId)).isNoObj(),
                    "Document should still not exist after delete");
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "multiWrite1 | 5",
            "multiWrite2 | 10",
            "multiWrite3 | 20"
    }, delimiter = '|')
    public void testMultipleWritesSameDocumentParameterized(final String docId, final int iterations) {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write same document multiple times with different values
            for (int i = 0; i < iterations; i++) {
                final Rec doc = rec(
                        uri("iteration"), jnt(i),
                        uri("value"), str("value" + i)
                );
                space.write(f("mongo:users/" + docId), doc);
            }

            // Read back and verify last write wins
            final Obj readBack = space.read(f("mongo:users/" + docId));
            final Rec readBackRec = readBack.asRec();

            assertEquals(jnt(iterations - 1), readBackRec.at(uri("iteration")),
                    "Should have last iteration value");
            assertEquals(str("value" + (iterations - 1)), readBackRec.at(uri("value")),
                    "Should have last value");
        } finally {
            space.close();
        }
    }

    // ========================================
    // Common Rewrite Tests
    // ========================================

    /**
     * Override to provide the rewrite test data URI prefix.
     * Uses the mongo: scheme for MongoDB collections.
     * Test data is created in @BeforeEach setupTestData().
     */
    @Override
    public fURI getTestDataUriPrefix() {
        return f("mongo:rewrite_test");
    }

    /**
     * Parameterized test for all rewrite optimizations.
     * Tests count, limit, has, where, where+count, aggregations, and compositions.
     * Test data (rewrite_test collection with 10 docs) is created in @BeforeEach.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideAllRewriteTestCases")
    public void testRewriteOptimizations(String description, String code, Obj expected) throws Exception {
        runRewriteTest(description, code, expected);
    }

    /**
     * Provides all rewrite test cases from the contract.
     */
    static Stream<Arguments> provideAllRewriteTestCases() {
        return new dcmntSpaceTest().generateAllRewriteTestCases();
    }
    
    // ========================================
    // DateTime Tests
    // ========================================

    @Test
    public void testDateTimeFieldHandling() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("events");
            events.drop();

            // Insert document with DateTime field
            final long timestamp = System.currentTimeMillis();
            events.insertOne(new Document()
                    .append("_id", "event1")
                    .append("name", "Test Event")
                    .append("createdAt", new java.util.Date(timestamp)));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj event = space.read(f("mongo:events/event1"));
            assertFalse(event.isNoObj(), "Event should exist");
            assertTrue(event.isRec(), "Event should be a record");

            final Rec eventRec = event.asRec();
            assertEquals(str("Test Event"), eventRec.at(uri(NAME)), "Name should match");

            // DateTime should be read as int (milliseconds since epoch)
            final Obj createdAt = eventRec.at(uri("createdAt"));
            assertTrue(createdAt.isInt(), "createdAt should be an int");

            LOG.info("Event with DateTime: %s", event);
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "event1 | 2024-01-01 | 1704067200000",  // Jan 1, 2024 00:00:00 UTC
            "event2 | 2024-06-15 | 1718409600000",  // Jun 15, 2024 00:00:00 UTC
            "event3 | 2024-12-31 | 1735603200000",  // Dec 31, 2024 00:00:00 UTC
            "event4 | 1970-01-01 | 0",              // Unix epoch
            "event5 | 2025-01-01 | 1735689600000"   // Jan 1, 2025 00:00:00 UTC
    }, delimiter = '|')
    public void testDateTimeValuesParameterized(final String eventId, final String dateStr, final long expectedMillis) {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("events");
            events.drop();

            // Insert document with specific DateTime
            events.insertOne(new Document()
                    .append("_id", eventId)
                    .append("description", "Event on " + dateStr)
                    .append("timestamp", new java.util.Date(expectedMillis)));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj event = space.read(f("mongo:events/" + eventId));
            final Rec eventRec = event.asRec();

            final Obj timestamp = eventRec.at(uri("timestamp"));
            assertTrue(timestamp.isInt(), "timestamp should be an int");
            assertEquals(jnt(expectedMillis), timestamp, "Timestamp should match expected milliseconds");
        } finally {
            space.close();
        }
    }

    @Test
    public void testMultipleDateTimeFields() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> tasks = db.getCollection("tasks");
            tasks.drop();

            final long now = System.currentTimeMillis();
            final long tomorrow = now + (24 * 60 * 60 * 1000);

            tasks.insertOne(new Document()
                    .append("_id", "task1")
                    .append("title", "Important Task")
                    .append("createdAt", new java.util.Date(now))
                    .append("dueDate", new java.util.Date(tomorrow))
                    .append("completedAt", null));  // null date
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj task = space.read(f("mongo:tasks/task1"));
            final Rec taskRec = task.asRec();

            assertEquals(str("Important Task"), taskRec.at(uri("title")), "Title should match");

            final Obj createdAt = taskRec.at(uri("createdAt"));
            assertTrue(createdAt.isInt(), "createdAt should be an int");

            final Obj dueDate = taskRec.at(uri("dueDate"));
            assertTrue(dueDate.isInt(), "dueDate should be an int");

            // Verify dueDate is after createdAt
            assertTrue(dueDate.asInt().jvm() > createdAt.asInt().jvm(),
                    "dueDate should be after createdAt");

            LOG.info("Task with multiple DateTimes: %s", task);
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "dtCorner1 | 0",                          // Unix epoch (Jan 1, 1970 00:00:00 UTC)
            "dtCorner2 | 1",                          // 1 millisecond after epoch
            "dtCorner3 | -1",                         // 1 millisecond before epoch
            "dtCorner4 | 9223372036854775807",        // Max long value (far future)
            "dtCorner5 | -9223372036854775808",       // Min long value (far past)
            "dtCorner6 | 1000000000000",              // Sep 9, 2001 01:46:40 UTC
            "dtCorner7 | 2000000000000",              // May 18, 2033 03:33:20 UTC
            "dtCorner8 | 253402300799999"             // Dec 31, 9999 23:59:59.999 UTC
    }, delimiter = '|')
    public void testDateTimeCornerCasesParameterized(final String eventId, final long milliseconds) {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("events");
            events.drop();

            events.insertOne(new Document()
                    .append("_id", eventId)
                    .append("timestamp", new java.util.Date(milliseconds))
                    .append("description", "Event at " + milliseconds + " ms"));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj event = space.read(f("mongo:events/" + eventId));
            final Rec eventRec = event.asRec();

            final Obj timestamp = eventRec.at(uri("timestamp"));
            assertTrue(timestamp.isInt(), "timestamp should be an int");
            assertEquals(jnt(milliseconds), timestamp, "Timestamp should match expected milliseconds");

            LOG.info("DateTime corner case %s: %s ms", eventId, milliseconds);
        } finally {
            space.close();
        }
    }

    @Test
    public void testDateTimeWithNestedDocuments() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> orders = db.getCollection("orders");
            orders.drop();

            final long orderTime = System.currentTimeMillis();
            final long shipTime = orderTime + (2 * 24 * 60 * 60 * 1000); // 2 days later

            orders.insertOne(new Document()
                    .append("_id", "order1")
                    .append("orderId", "ORD-12345")
                    .append("orderDate", new java.util.Date(orderTime))
                    .append("shipping", new Document()
                            .append("estimatedDelivery", new java.util.Date(shipTime))
                            .append("carrier", "FedEx")));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj order = space.read(f("mongo:orders/order1"));
            final Rec orderRec = order.asRec();

            // Check top-level datetime
            final Obj orderDate = orderRec.at(uri("orderDate"));
            assertTrue(orderDate.isInt(), "orderDate should be an int");

            // Check nested datetime in shipping
            final Obj shipping = orderRec.at(uri("shipping"));
            assertTrue(shipping.isRec(), "shipping should be a record");
            final Obj estimatedDelivery = shipping.asRec().at(uri("estimatedDelivery"));
            assertTrue(estimatedDelivery.isInt(), "estimatedDelivery should be an int");

            LOG.info("Order with nested DateTimes: %s", order);
        } finally {
            space.close();
        }
    }

    @Test
    public void testDateTimeArrays() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> logs = db.getCollection("logs");
            logs.drop();

            final long now = System.currentTimeMillis();

            logs.insertOne(new Document()
                    .append("_id", "log1")
                    .append("message", "System log")
                    .append("timestamp1", new java.util.Date(now))
                    .append("timestamp2", new java.util.Date(now + 1000))
                    .append("timestamp3", new java.util.Date(now + 2000))
                    .append("timestamp4", new java.util.Date(now + 3000)));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj log = space.read(f("mongo:logs/log1"));
            final Rec logRec = log.asRec();

            // Verify all timestamp fields are ints
            final Obj ts1 = logRec.at(uri("timestamp1"));
            final Obj ts2 = logRec.at(uri("timestamp2"));
            final Obj ts3 = logRec.at(uri("timestamp3"));
            final Obj ts4 = logRec.at(uri("timestamp4"));

            assertTrue(ts1.isInt(), "timestamp1 should be an int");
            assertTrue(ts2.isInt(), "timestamp2 should be an int");
            assertTrue(ts3.isInt(), "timestamp3 should be an int");
            assertTrue(ts4.isInt(), "timestamp4 should be an int");

            // Verify they are in ascending order
            assertTrue(ts2.asInt().jvm() > ts1.asInt().jvm(), "Timestamps should be in ascending order");
            assertTrue(ts3.asInt().jvm() > ts2.asInt().jvm(), "Timestamps should be in ascending order");
            assertTrue(ts4.asInt().jvm() > ts3.asInt().jvm(), "Timestamps should be in ascending order");

            LOG.info("Log with multiple DateTime fields: %s", log);
        } finally {
            space.close();
        }
    }

    @Test
    public void testWriteAndReadBackDateTime() {
        // Create the collection first
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("events");
            events.drop();
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Note: We write as int (milliseconds), MongoDB will store as int64
            final long timestamp = System.currentTimeMillis();
            final Rec eventDoc = rec(
                    uri(NAME), str("Test Event"),
                    uri("timestamp"), jnt(timestamp)
            );

            space.write(f("mongo:events/writeTest1"), eventDoc);

            // Read back
            final Obj readBack = space.read(f("mongo:events/writeTest1"));
            assertFalse(readBack.isNoObj(), "Document should exist after write");
            assertTrue(readBack.isRec(), "Document should be a record");

            final Rec readBackRec = readBack.asRec();

            assertEquals(str("Test Event"), readBackRec.at(uri(NAME)), "Name should match");
            assertEquals(jnt(timestamp), readBackRec.at(uri("timestamp")), "Timestamp should match");

            LOG.info("Write and read back timestamp: %s", timestamp);
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "dtMixed1 | Event 1 | 1704067200000 | 1704153600000",  // Jan 1-2, 2024
            "dtMixed2 | Event 2 | 0              | 1000000000000",  // Epoch to 2001
            "dtMixed3 | Event 3 | -86400000      | 86400000",       // Day before/after epoch
            "dtMixed4 | Event 4 | 1000           | 2000",           // Small values
            "dtMixed5 | Event 5 | 253402300799999| 253402300799999" // Same start/end (far future)
    }, delimiter = '|')
    public void testDateTimeRangesParameterized(final String eventId, final String name,
                                                final long startTime, final long endTime) {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("events");
            events.drop();

            events.insertOne(new Document()
                    .append("_id", eventId)
                    .append("name", name)
                    .append("startTime", new java.util.Date(startTime))
                    .append("endTime", new java.util.Date(endTime)));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj event = space.read(f("mongo:events/" + eventId));
            final Rec eventRec = event.asRec();

            assertEquals(str(name), eventRec.at(uri(NAME)), "Name should match");

            final Obj start = eventRec.at(uri("startTime"));
            final Obj end = eventRec.at(uri("endTime"));

            assertTrue(start.isInt(), "startTime should be an int");
            assertTrue(end.isInt(), "endTime should be an int");

            assertEquals(jnt(startTime), start, "Start time should match");
            assertEquals(jnt(endTime), end, "End time should match");

            assertTrue(end.asInt().jvm() >= start.asInt().jvm(),
                    "End time should be >= start time");

            LOG.info("DateTime range %s: %s to %s", eventId, startTime, endTime);
        } finally {
            space.close();
        }
    }

    @Test
    public void testMixedDateTimeAndOtherTypes() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> mixed = db.getCollection("mixed");
            mixed.drop();

            final long timestamp = System.currentTimeMillis();

            mixed.insertOne(new Document()
                    .append("_id", "mixed1")
                    .append("stringField", "test")
                    .append("intField", 42)
                    .append("doubleField", 3.14)
                    .append("boolField", true)
                    .append("dateField", new java.util.Date(timestamp))
                    .append("nestedDoc", new Document()
                            .append("nestedDate", new java.util.Date(timestamp + 1000))
                            .append("nestedString", "nested")));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj doc = space.read(f("mongo:mixed/mixed1"));
            final Rec docRec = doc.asRec();

            // Verify all types
            assertTrue(docRec.at(uri("stringField")).isStr(), "stringField should be str");
            assertTrue(docRec.at(uri("intField")).isInt(), "intField should be int");
            assertTrue(docRec.at(uri("doubleField")).isReal(), "doubleField should be real");
            assertTrue(docRec.at(uri("boolField")).isBool(), "boolField should be bool");
            assertTrue(docRec.at(uri("dateField")).isInt(), "dateField should be int");

            // Verify nested date
            final Obj nestedDoc = docRec.at(uri("nestedDoc"));
            assertTrue(nestedDoc.isRec(), "nestedDoc should be rec");
            assertTrue(nestedDoc.asRec().at(uri("nestedDate")).isInt(),
                    "nestedDate should be int");

            LOG.info("Mixed types with DateTime: %s", doc);
        } finally {
            space.close();
        }
    }

    @Test
    public void testEmptyDateTimeCollection() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("emptyEvents");
            events.drop();
            // Don't insert anything
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj result = space.read(f("mongo:emptyEvents/+"));
            // Should return noobj or empty results
            assertTrue(result.isNoObj() || (result.isObjs() && !result.asObjs().iterator().hasNext()),
                    "Empty collection should return noobj or empty results");

            LOG.info("Empty DateTime collection result: %s", result);
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "dtUpdate1 | 1000000000000 | 2000000000000",  // Update to later time
            "dtUpdate2 | 2000000000000 | 1000000000000",  // Update to earlier time
            "dtUpdate3 | 0             | 1",              // Update from epoch
            "dtUpdate4 | 1704067200000 | 1704067200000",  // Update to same time
            "dtUpdate5 | -1000         | 1000"            // Update from negative to positive
    }, delimiter = '|')
    public void testUpdateDateTimeFieldsParameterized(final String eventId,
                                                      final long originalTime, final long updatedTime) {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("events");
            events.drop();

            // Insert with original time
            events.insertOne(new Document()
                    .append("_id", eventId)
                    .append("timestamp", new java.util.Date(originalTime)));
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Verify original
            Obj event = space.read(f("mongo:events/" + eventId));
            assertEquals(jnt(originalTime), event.asRec().at(uri("timestamp")),
                    "Original timestamp should match");

            // Update via MongoDB client
            try (final MongoClient client = MongoClients.create(connectionString)) {
                final MongoDatabase db = client.getDatabase(DB_NAME);
                final MongoCollection<Document> events = db.getCollection("events");
                events.updateOne(
                        new Document("_id", eventId),
                        new Document("$set", new Document("timestamp", new java.util.Date(updatedTime)))
                );
            }

            // Read back and verify update
            event = space.read(f("mongo:events/" + eventId));
            assertEquals(jnt(updatedTime), event.asRec().at(uri("timestamp")),
                    "Updated timestamp should match");

            LOG.info("Updated DateTime from %s to %s", originalTime, updatedTime);
        } finally {
            space.close();
        }
    }

    @Test
    public void testDateTimeWithAllDocumentsQuery() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            final MongoCollection<Document> events = db.getCollection("allEvents");
            events.drop();

            final long baseTime = System.currentTimeMillis();
            for (int i = 0; i < 5; i++) {
                events.insertOne(new Document()
                        .append("_id", "event" + i)
                        .append("sequence", i)
                        .append("timestamp", new java.util.Date(baseTime + (i * 1000))));
            }
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            final Obj allEvents = space.read(f("mongo:allEvents/+"));
            assertFalse(allEvents.isNoObj(), "Should return results");
            assertTrue(allEvents.isObjs(), "Should return multiple objects");

            int count = 0;
            for (Obj event : allEvents.asObjs()) {
                assertTrue(event.isRec(), "Each event should be a record");
                final Obj timestamp = event.asRec().at(uri("timestamp"));
                assertTrue(timestamp.isInt(), "Each timestamp should be an int");
                count++;
            }

            assertEquals(5, count, "Should have 5 events");
            LOG.info("Retrieved %s events with DateTimes using + query", count);
        } finally {
            space.close();
        }
    }

    // ========================================
    // Reference Resolution Tests (auto_from_)
    // ========================================

    /**
     * Intra-space auto_from: write a record with {@code !*mongo:collection/id} through
     * the metatron write path, verifying it's serialized as a standard MongoDB DBRef
     * ({@code $ref: "collection"}) and reconstructions correctly on read.
     */
    @Test
    public void testIntraSpaceAutoFromRoundTrip() {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write target record
            Router.writeToSpace(f("mongo:locations/1"),
                    rec(uri(NAME), str("downtown"),
                            uri("capacity"), jnt(5000)));

            // Write record with intra-space auto_from → mongo:locations/1
            Router.writeToSpace(f("mongo:arenas/1"), rec(
                    uri(NAME), str("main_stage"),
                    uri("venue"), auto_from_(f("mongo:locations/1")).tryToInst()));

            // Read back: auto_from reconstructs from DBRef with bare collection name
            final Obj arena = Router.readFromSpace(f("mongo:arenas/1"));
            final Obj venueInst = arena.recValue().get(uri("venue"));
            assertTrue(venueInst.isInst(), "venue should be a lazy auto_from inst");
            assertEquals(f("mongo:locations/1"), venueInst.asInst().arg(0).uriValue(),
                    "intra-space auto_from should use space pattern (bare $ref)");

            // Resolution via rec.at() fetches the target record
            final Obj resolved = arena.asRec().at(uri("venue"));
            assertTrue(resolved.isRec(), "resolved value should be a record");
            assertEquals(str("downtown"), resolved.asRec().at(uri(NAME)));
            assertEquals(jnt(5000), resolved.asRec().at(uri("capacity")));

            LOG.info("intra-space auto_from round-trip test passed");
        } finally {
            space.close();
            // Clean up collections so they don't interfere with schema discovery
            // in subsequent tests (DBRef documents can't be decoded by in-memory MongoDB codecs)
            try (final MongoClient client = MongoClients.create(connectionString)) {
                client.getDatabase(DB_NAME).getCollection("arenas").drop();
                client.getDatabase(DB_NAME).getCollection("locations").drop();
            }
        }
    }

    /**
     * Cross-space auto_from: write a record with {@code !*grph:V/1} through the
     * metatron write path, verifying it's serialized as {@code $ref: "grph:V"}
     * (scheme-prefixed DBRef) and resolves through the router to a memSpace target.
     */
    @Test
    public void testCrossSpaceAutoFromRoundTrip() {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        final fURI memSpaceVid = f("/sys/space/mem/dcmnt_xspace_target");
        final memSpace targetSpace = memSpace.of(f("grph:#"), memSpaceVid);
        Router.global().addSpace(targetSpace);
        try {
            // Write the cross-space target into memSpace
            Router.writeToSpace(f("grph:vertices/42"),
                    rec(uri("label"), str("plaza"),
                            uri("zone"), str("A")));

            // Write dcmntSpace record with cross-space auto_from → grph:vertices/42
            Router.writeToSpace(f("mongo:stages/1"), rec(
                    uri(NAME), str("open_air"),
                    uri("spot"), auto_from_(f("grph:vertices/42")).tryToInst()));

            // Read back: auto_from reconstructs from $ref: "grph:vertices" → grph:vertices/42
            final Obj stage = Router.readFromSpace(f("mongo:stages/1"));
            final Obj spotInst = stage.recValue().get(uri("spot"));
            assertTrue(spotInst.isInst(), "spot should be a lazy auto_from inst");
            assertEquals(f("grph:vertices/42"), spotInst.asInst().arg(0).uriValue(),
                    "cross-space auto_from should preserve original scheme");

            // Resolution through router: rec.at() fetches the memSpace target
            final Obj resolved = stage.asRec().at(uri("spot"));
            assertTrue(resolved.isRec(),
                    "cross-space auto_from should resolve to memSpace record");
            assertEquals(str("plaza"), resolved.asRec().at(uri("label")));
            assertEquals(str("A"), resolved.asRec().at(uri("zone")));

            LOG.info("cross-space auto_from round-trip test passed");
        } finally {
            space.close();
            Router.global().removeSpace(targetSpace.vid());
            targetSpace.close();
            // Clean up collection so DBRef schema discovery doesn't break subsequent tests
            try (final MongoClient client = MongoClients.create(connectionString)) {
                client.getDatabase(DB_NAME).getCollection("stages").drop();
            }
        }
    }

    /**
     * Verifies that deeply nested DBRef objects (from auto_from references inside
     * sub-documents) survive the round-trip without crashing {@code toBsonDocument()}.
     * The in-memory MongoDB driver (bwaldvogel) converts {@code {$ref,$id}} to
     * {@code com.mongodb.DBRef} objects, which the BSON codec cannot serialize.
     * {@code normalizeDBRefs()} converts them back recursively before BSON conversion.
     */
    @Test
    public void testNestedDBRefInSubDocument() {
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write a target record
            Router.writeToSpace(f("mongo:cities/1"),
                    rec(uri(NAME), str("santa_fe")));

            // Write a document with a sub-document containing a DBRef
            Router.writeToSpace(f("mongo:events/1"), rec(
                    uri(NAME), str("fiesta"),
                    uri("details"), rec(
                            uri("venue"), str("plaza"),
                            uri("city"), auto_from_(f("mongo:cities/1")).tryToInst()
                    )));

            // Read back — the nested DBRef must not crash processDocument
            final Obj event = Router.readFromSpace(f("mongo:events/1"));
            assertTrue(event.isRec(), "event should be a record");

            // Navigate into the sub-document and resolve the nested reference
            final Obj details = event.asRec().at(uri("details"));
            assertTrue(details.isRec(), "details should be a sub-record");
            final Obj city = details.asRec().at(uri("city"));
            assertTrue(city.isRec(), "nested DBRef should resolve to a record");
            assertEquals(str("santa_fe"), city.asRec().at(uri(NAME)));

            LOG.info("nested DBRef in sub-document test passed");
        } finally {
            space.close();
            try (final MongoClient client = MongoClients.create(connectionString)) {
                client.getDatabase(DB_NAME).getCollection("events").drop();
                client.getDatabase(DB_NAME).getCollection("cities").drop();
            }
        }
    }

    /**
     * Verifies that writing noobj to a specific field path issues an $unset
     * (field deletion) rather than a $set of null.
     */
    @Test
    public void testFieldWriteNoobjDeletesField() {
        // Verify $unset works after replaceOne+upsert (metatron's write path)
        // — some in-memory MongoDB implementations have gaps here.
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);
            // Mimic metatron's write path: replaceOne with upsert, then updateOne with $unset
            db.getCollection("items_raw").replaceOne(
                    new Document("_id", "1"),
                    new Document("_id", "1").append("name", "widget").append("color", "red"),
                    new com.mongodb.client.model.ReplaceOptions().upsert(true));
            db.getCollection("items_raw").updateOne(
                    new Document("_id", "1"),
                    new Document("$unset", new Document("color", "")));
            final Document after = db.getCollection("items_raw")
                    .find(new Document("_id", "1")).first();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    after != null && !after.containsKey("color"),
                    "$unset after replaceOne+upsert is supported by this MongoDB driver");
        }

        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Write the full document first
            Router.writeToSpace(f("mongo:items/1"), rec(
                    uri(NAME), str("widget"),
                    uri("color"), str("red")));

            // Verify the full document exists
            final Obj before = Router.readFromSpace(f("mongo:items/1"));
            assertTrue(before.isRec(), "doc should exist before field delete");
            assertEquals(str("red"), before.asRec().at(uri("color")));

            // Delete the 'color' field by writing noobj to the field path
            Router.writeToSpace(f("mongo:items/1/color"), noobj());

            // Read back — color should be gone
            final Obj after = Router.readFromSpace(f("mongo:items/1"));
            assertTrue(after.isRec(), "doc should still exist after field delete");
            assertFalse(after.recValue().containsKey(uri("color")),
                    "color field should be unset");
            assertEquals(str("widget"), after.asRec().at(uri(NAME)));

            LOG.info("field-write noobj delete test passed");
        } finally {
            space.close();
            try (final MongoClient client = MongoClients.create(connectionString)) {
                client.getDatabase(DB_NAME).getCollection("items").drop();
            }
        }
    }

    /**
     * Setup test data with references for testing lazy resolution
     */
    private void setupTestDataWithReferences() {
        try (final MongoClient client = MongoClients.create(connectionString)) {
            final MongoDatabase db = client.getDatabase(DB_NAME);

            // Create authors collection
            final MongoCollection<Document> authors = db.getCollection("authors");
            authors.drop();
            authors.insertOne(new Document()
                    .append("_id", new org.bson.types.ObjectId("507f1f77bcf86cd799439011"))
                    .append("name", "John Doe")
                    .append("email", "john@example.com"));
            authors.insertOne(new Document()
                    .append("_id", new org.bson.types.ObjectId("507f1f77bcf86cd799439012"))
                    .append("name", "Jane Smith")
                    .append("email", "jane@example.com"));

            // Create posts collection with authorId references
            final MongoCollection<Document> posts = db.getCollection("posts");
            posts.drop();
            posts.insertOne(new Document()
                    .append("_id", new org.bson.types.ObjectId("507f1f77bcf86cd799439021"))
                    .append("title", "First Post")
                    .append("content", "This is the first post")
                    .append("authorId", new org.bson.types.ObjectId("507f1f77bcf86cd799439011")));
            posts.insertOne(new Document()
                    .append("_id", new org.bson.types.ObjectId("507f1f77bcf86cd799439022"))
                    .append("title", "Second Post")
                    .append("content", "This is the second post")
                    .append("authorId", new org.bson.types.ObjectId("507f1f77bcf86cd799439012")));

            // Create comments collection with custom reference pattern (not using $ref/$id to avoid DBRef codec issues)
            final MongoCollection<Document> comments = db.getCollection("comments");
            comments.drop();
            comments.insertOne(new Document()
                    .append("_id", new org.bson.types.ObjectId("507f1f77bcf86cd799439031"))
                    .append("text", "Great post!")
                    .append("postId", new org.bson.types.ObjectId("507f1f77bcf86cd799439021")));

            LOG.info("[dcmntSpaceTest] test data with references setup complete");
        }
    }

    @Test
    public void testObjectIdReferenceDetection() {
        setupTestDataWithReferences();
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read a post with authorId reference
            final Obj post = space.read(f("mongo:posts/507f1f77bcf86cd799439021"));
            assertFalse(post.isNoObj(), "Post should exist");
            assertTrue(post.isRec(), "Post should be a record");

            final Rec postRec = post.asRec();
            assertEquals(str("First Post"), postRec.at(uri("title")), "Title should match");

            // Check that authorId is an instruction (auto_from)
            // Use jvm() to get the raw value without auto-resolution
            final Obj authorIdField = postRec.jvm().get(uri("authorId"));
            assertTrue(authorIdField.isInst(), "authorId should be an auto_from instruction");

            LOG.info("Post with reference: %s", post);
        } finally {
            space.close();
        }
    }

    @Test
    public void testLazyReferenceResolution() {
        setupTestDataWithReferences();
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read a post with authorId reference
            final Obj post = space.read(f("mongo:posts/507f1f77bcf86cd799439021"));
            final Rec postRec = post.asRec();

            // Access the authorId field without auto-resolution
            final Obj authorIdField = postRec.jvm().get(uri("authorId"));
            assertTrue(authorIdField.isInst(), "authorId should be an instruction");

            // Execute the instruction to resolve the reference
            final Obj author = authorIdField.asInst().apply();
            assertFalse(author.isNoObj(), "Author should be resolved");
            assertTrue(author.isRec(), "Author should be a record");

            final Rec authorRec = author.asRec();
            assertEquals(str("John Doe"), authorRec.at(uri(NAME)), "Author name should match");
            assertEquals(str("john@example.com"), authorRec.at(uri("email")), "Author email should match");

            LOG.info("Resolved author: %s", author);
        } finally {
            space.close();
        }
    }

    @Test
    public void testObjectIdReferenceInComments() {
        setupTestDataWithReferences();
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read a comment with postId reference
            final Obj comment = space.read(f("mongo:comments/507f1f77bcf86cd799439031"));
            assertFalse(comment.isNoObj(), "Comment should exist");
            assertTrue(comment.isRec(), "Comment should be a record");

            final Rec commentRec = comment.asRec();
            assertEquals(str("Great post!"), commentRec.at(uri("text")), "Comment text should match");

            // Check that postId is an instruction (auto_from)
            final Obj postIdField = commentRec.jvm().get(uri("postId"));
            assertTrue(postIdField.isInst(), "postId should be an auto_from instruction");

            LOG.info("Comment with reference: %s", comment);
        } finally {
            space.close();
        }
    }

    @Test
    public void testCommentPostReferenceLazyResolution() {
        setupTestDataWithReferences();
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read a comment with postId reference
            final Obj comment = space.read(f("mongo:comments/507f1f77bcf86cd799439031"));
            final Rec commentRec = comment.asRec();

            // Access the postId field without auto-resolution
            final Obj postIdField = commentRec.jvm().get(uri("postId"));
            assertTrue(postIdField.isInst(), "postId should be an instruction");

            // Execute the instruction to resolve the reference
            final Obj post = postIdField.asInst().apply();
            assertFalse(post.isNoObj(), "Post should be resolved");
            assertTrue(post.isRec(), "Post should be a record");

            final Rec postRec = post.asRec();
            assertEquals(str("First Post"), postRec.at(uri("title")), "Post title should match");
            assertEquals(str("This is the first post"), postRec.at(uri("content")), "Post content should match");

            LOG.info("Resolved post from comment reference: %s", post);
        } finally {
            space.close();
        }
    }

    @Test
    public void testNoInfiniteRecursionWithReferences() {
        setupTestDataWithReferences();
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read a post - should not cause infinite recursion
            final Obj post = space.read(f("mongo:posts/507f1f77bcf86cd799439021"));
            assertFalse(post.isNoObj(), "Post should exist");

            // The authorId should be an instruction, not eagerly resolved
            final Rec postRec = post.asRec();
            final Obj authorIdField = postRec.jvm().get(uri("authorId"));
            assertTrue(authorIdField.isInst(), "authorId should be lazy (instruction)");

            // We can safely convert to string without triggering resolution
            final String postStr = post.toString();
            assertNotNull(postStr, "Should be able to stringify without infinite recursion");

            LOG.info("Post without infinite recursion: %s", postStr);
        } finally {
            space.close();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "507f1f77bcf86cd799439021 | First Post  | John Doe",
            "507f1f77bcf86cd799439022 | Second Post | Jane Smith"
    }, delimiter = '|')
    public void testMultipleReferencesParameterized(final String postId, final String expectedTitle,
                                                    final String expectedAuthorName) {
        setupTestDataWithReferences();
        final dcmntSpace space = (dcmntSpace) this.spaceSupplier.get();
        try {
            // Read post
            final Obj post = space.read(f("mongo:posts/" + postId));
            final Rec postRec = post.asRec();

            assertEquals(str(expectedTitle), postRec.at(uri("title")), "Title should match");

            // Resolve author reference
            final Obj authorInst = postRec.jvm().get(uri("authorId"));
            assertTrue(authorInst.isInst(), "authorId should be an instruction");

            final Obj author = authorInst.asInst().apply();
            final Rec authorRec = author.asRec();

            assertEquals(str(expectedAuthorName), authorRec.at(uri(NAME)), "Author name should match");
        } finally {
            space.close();
        }
    }
}
