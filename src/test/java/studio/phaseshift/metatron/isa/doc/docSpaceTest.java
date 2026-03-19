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

package studio.phaseshift.metatron.isa.doc;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.doc.docInstSet.DOC_ISA_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for docSpace with in-memory MongoDB
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class docSpaceTest extends AbstractSpaceTest {

    protected static MongoServer mongoServer;
    protected static String connectionString;
    protected static final String DB_NAME = "testdb";
    protected static final fURI SPACE_VID = f("/sys/space/doc/test");

    public docSpaceTest() {
        super(() -> docSpace.of(
                rec(
                        uri(PATTERN), uri("mongo:#"),
                        uri(HOST), uri(connectionString + "/" + DB_NAME),
                        uri(ROUTE), rec(uri("mongo:"), uri(""))
                ).jvm(),
                SPACE_VID
        ));
        BootLoader.loadInstSetProvider(DOC_ISA_TID);
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

            LOG.info("test data setup complete");
        }
    }

    @Test
    public void testReadSingleDocument() {
        LOG.warn("testing read single document");

        final docSpace space = (docSpace) this.spaceSupplier.get();
        LOG.warn("HERE: %s", Router.global().read(space.pattern()));
        try {
            // Read a specific user
            final Obj user1 = space.read(f("mongo:users/user1"));

            assertFalse(user1.isNoObj(), "User1 should exist");
            assertTrue(user1.isRec(), "User1 should be a record");

            final Rec user1Rec = user1.asRec();
            assertEquals(str("Alice"), user1Rec.at(uri("name")), "Name should be Alice");
            assertEquals(jnt(30), user1Rec.at(uri("age")), "Age should be 30");
            assertEquals(str("alice@example.com"), user1Rec.at(uri("email")), "Email should match");
            assertEquals(bool(true), user1Rec.at(uri("active")), "Should be active");

            LOG.info("Successfully read user1: {}", user1);
        } finally {
            space.close();
        }
    }

    @Test
    public void testReadNonExistentDocument() {
        LOG.info("Testing read non-existent document");

        final docSpace space = (docSpace) this.spaceSupplier.get();
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

        final docSpace space = (docSpace) this.spaceSupplier.get();
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
            LOG.info("Successfully read {} users", count);
        } finally {
            space.close();
        }
    }

    @Test
    public void testWriteNewDocument() {
        LOG.info("Testing write new document");

        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Write a new user
            final Rec newUser = rec(
                    uri("name"), str("Diana"),
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
            assertEquals(str("Diana"), readBackRec.at(uri("name")), "Name should be Diana");
            assertEquals(jnt(28), readBackRec.at(uri("age")), "Age should be 28");

            LOG.info("Successfully wrote and read back new user");
        } finally {
            space.close();
        }
    }

    @Test
    public void testUpdateExistingDocument() {
        LOG.info("Testing update existing document");

        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Update user1
            final Rec updatedUser = rec(
                    uri("name"), str("Alice Updated"),
                    uri("age"), jnt(31),
                    uri("email"), str("alice.new@example.com"),
                    uri("active"), bool(true)
            );

            space.write(f("mongo:users/user1"), updatedUser);

            // Read it back
            final Obj readBack = space.read(f("mongo:users/user1"));
            final Rec readBackRec = readBack.asRec();

            assertEquals(str("Alice Updated"), readBackRec.at(uri("name")), "Name should be updated");
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

        final docSpace space = (docSpace) this.spaceSupplier.get();
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

        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Write a document with nested structure
            final Rec nestedDoc = rec(
                    uri("name"), str("Eve"),
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
            assertEquals(str("Eve"), readBackRec.at(uri("name")), "Name should be Eve");

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

        final docSpace space = (docSpace) this.spaceSupplier.get();
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

        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Read from users collection
            final Obj user = space.read(f("mongo:users/user1"));
            assertFalse(user.isNoObj(), "User should exist");
            assertEquals(str("Alice"), user.asRec().at(uri("name")), "User name should be Alice");

            // Read from products collection
            final Obj product = space.read(f("mongo:products/prod1"));
            assertFalse(product.isNoObj(), "Product should exist");
            assertEquals(str("Laptop"), product.asRec().at(uri("name")), "Product name should be Laptop");

            LOG.info("Successfully read from multiple collections");
        } finally {
            space.close();
        }
    }

    @Test
    public void testEmptyList() {
        LOG.info("Testing empty list handling");

        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            final Rec docWithEmptyList = rec(
                    uri("name"), str("Test"),
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

        final docSpace space = (docSpace) this.spaceSupplier.get();
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
            assertEquals(51, readBackRec.jvm().size(), "Should have 51 fields"); // 51 cause of _id

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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            final Obj user = space.read(f("mongo:users/" + userId));

            if (shouldBeNoObj) {
                assertTrue(user.isNoObj(), "User " + userId + " should not exist");
            } else {
                assertFalse(user.isNoObj(), "User " + userId + " should exist");
                assertTrue(user.isRec(), "User should be a record");

                final Rec userRec = user.asRec();
                assertEquals(str(expectedName), userRec.at(uri("name")), "Name should match");
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            final Obj product = space.read(f("mongo:products/" + productId));
            assertFalse(product.isNoObj(), "Product " + productId + " should exist");
            assertTrue(product.isRec(), "Product should be a record");

            final Rec productRec = product.asRec();
            assertEquals(str(expectedName), productRec.at(uri("name")), "Name should match");
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Write user
            final Rec newUser = rec(
                    uri("name"), str(name),
                    uri("age"), jnt(age),
                    uri("email"), str(email),
                    uri("active"), bool(true)
            );
            space.write(f("mongo:users/" + userId), newUser);

            // Read back and verify
            final Obj readBack = space.read(f("mongo:users/" + userId));
            assertFalse(readBack.isNoObj(), "User " + userId + " should exist");

            final Rec readBackRec = readBack.asRec();
            assertEquals(str(name), readBackRec.at(uri("name")), "Name should match");
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Write original
            space.write(f("mongo:users/" + userId), rec(
                    uri("name"), str(originalName),
                    uri("age"), jnt(originalAge)
            ));

            // Update
            space.write(f("mongo:users/" + userId), rec(
                    uri("name"), str(updatedName),
                    uri("age"), jnt(updatedAge)
            ));

            // Verify update
            final Obj readBack = space.read(f("mongo:users/" + userId));
            final Rec readBackRec = readBack.asRec();
            assertEquals(str(updatedName), readBackRec.at(uri("name")), "Name should be updated");
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Create user
            space.write(f("mongo:users/" + userId), rec(uri("name"), str(name)));

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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            final Rec nestedDoc = rec(
                    uri("name"), str(name),
                    uri("address"), rec(
                            uri("street"), str(street),
                            uri("city"), str(city),
                            uri("zip"), str(zip)
                    )
            );

            space.write(f("mongo:users/" + userId), nestedDoc);

            final Obj readBack = space.read(f("mongo:users/" + userId));
            final Rec readBackRec = readBack.asRec();

            assertEquals(str(name), readBackRec.at(uri("name")), "Name should match");

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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            final String[] tagArray = tagsStr.equals("<EMPTY>") ? new String[0] : tagsStr.split(",");
            final Rec docWithList = rec(
                    uri("name"), str(name),
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
        try {
            // Write empty record
            final Rec emptyRec = rec();
            space.write(f("mongo:users/" + docId), emptyRec);

            // Read back and verify
            final Obj readBack = space.read(f("mongo:users/" + docId));
            assertFalse(readBack.isNoObj(), "Empty record should exist");
            assertTrue(readBack.isRec(), "Should be a record");

            // Should only have _id field
            final Rec readBackRec = readBack.asRec();
            assertEquals(1, readBackRec.jvm().size(), "Should only have _id field");
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
    // Reference Resolution Tests (auto_from_)
    // ========================================

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

            LOG.info("[docSpaceTest] test data with references setup complete");
        }
    }

    @Test
    public void testObjectIdReferenceDetection() {
        setupTestDataWithReferences();
        final docSpace space = (docSpace) this.spaceSupplier.get();
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

            LOG.info("Post with reference: {}", post);
        } finally {
            space.close();
        }
    }

    @Test
    public void testLazyReferenceResolution() {
        setupTestDataWithReferences();
        final docSpace space = (docSpace) this.spaceSupplier.get();
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
            assertEquals(str("John Doe"), authorRec.at(uri("name")), "Author name should match");
            assertEquals(str("john@example.com"), authorRec.at(uri("email")), "Author email should match");

            LOG.info("Resolved author: {}", author);
        } finally {
            space.close();
        }
    }

    @Test
    public void testObjectIdReferenceInComments() {
        setupTestDataWithReferences();
        final docSpace space = (docSpace) this.spaceSupplier.get();
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

            LOG.info("Comment with reference: {}", comment);
        } finally {
            space.close();
        }
    }

    @Test
    public void testCommentPostReferenceLazyResolution() {
        setupTestDataWithReferences();
        final docSpace space = (docSpace) this.spaceSupplier.get();
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

            LOG.info("Resolved post from comment reference: {}", post);
        } finally {
            space.close();
        }
    }

    @Test
    public void testNoInfiniteRecursionWithReferences() {
        setupTestDataWithReferences();
        final docSpace space = (docSpace) this.spaceSupplier.get();
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

            LOG.info("Post without infinite recursion: {}", postStr);
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
        final docSpace space = (docSpace) this.spaceSupplier.get();
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

            assertEquals(str(expectedAuthorName), authorRec.at(uri("name")), "Author name should match");
        } finally {
            space.close();
        }
    }
}
