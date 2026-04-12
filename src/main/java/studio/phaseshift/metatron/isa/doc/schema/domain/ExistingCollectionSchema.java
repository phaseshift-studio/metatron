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

package studio.phaseshift.metatron.isa.doc.schema.domain;

import com.mongodb.client.MongoDatabase;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.Document;
import studio.phaseshift.metatron.isa.doc.docdbSpace;

import studio.phaseshift.metatron.furi.fURI;

import java.util.*;

/**
 * Schema for discovering existing MongoDB collections and their document structures.
 * Samples documents from each collection to infer field types and detect references.
 * <p>
 * This is analogous to {@link studio.phaseshift.metatron.isa.tble.schema.domain.ExistingTableSchema}
 * for SQL databases, but uses document sampling since MongoDB is schema-less.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExistingCollectionSchema {

    private final docdbSpace space;
    private final Map<String, CollectionMetadata> collectionSchemas = new LinkedHashMap<>();
    private final int sampleSize;

    /**
     * Metadata about a MongoDB collection
     */
    public record CollectionMetadata(String dbName, String collectionName,
                                     List<FieldMetadata> fields,
                                     List<ReferenceMetadata> references) {
    }

    /**
     * Metadata about a document field (inferred from sampling)
     */
    public record FieldMetadata(String path, BsonType bsonType, double probability) {
    }

    /**
     * Metadata about a detected reference between collections
     */
    public record ReferenceMetadata(String fromCollection, String fromField,
                                    String toCollection, ReferenceType type) {
    }

    /**
     * Types of references detected in documents
     */
    public enum ReferenceType {
        DBREF,              // MongoDB DBRef format: {$ref: "collection", $id: ObjectId(...)}
        OBJECT_ID_FIELD     // Field ending in "Id" containing an ObjectId
    }

    public ExistingCollectionSchema(final docdbSpace space, final int sampleSize) {
        this.space = space;
        this.sampleSize = sampleSize;
    }

    public ExistingCollectionSchema(final docdbSpace space) {
        this(space, 100);
    }

    /**
     * Discover all collections and infer their schemas by sampling documents
     */
    public void initialize(final MongoDatabase database) {
        this.collectionSchemas.clear();
        for (final String collectionName : database.listCollectionNames()) {
            final List<FieldMetadata> fields = inferFieldTypes(database, collectionName);
            final List<ReferenceMetadata> refs = detectReferences(collectionName, fields);

            this.collectionSchemas.put(collectionName.toLowerCase(),
                    new CollectionMetadata(database.getName(), collectionName, fields, refs));

            this.space.logger().debug("discovered collection: %s with %d fields, %d references",
                    collectionName, fields.size(), refs.size());
        }
        this.space.logger().info("discovered {{b}}%d{{X}} collections: %s",
                collectionSchemas.size(), collectionSchemas.keySet());
    }
    
    private List<FieldMetadata> inferFieldTypes(final MongoDatabase database, final String collectionName) {
        final Map<String, Map<BsonType, Integer>> fieldTypeCounts = new LinkedHashMap<>();
        int docCount = 0;

        for (final Document doc : database.getCollection(collectionName).find().limit(this.sampleSize)) {
            analyzeDocument("", doc.toBsonDocument(), fieldTypeCounts);
            docCount++;
        }

        return buildFieldMetadata(fieldTypeCounts, docCount);
    }
    
    private void analyzeDocument(final String prefix, final org.bson.BsonDocument doc,
                                 final Map<String, Map<BsonType, Integer>> counts) {
        for (final String key : doc.keySet()) {
            final String path = prefix.isEmpty() ? key : prefix + "." + key;
            final BsonValue value = doc.get(key);
            final BsonType type = value.getBsonType();

            counts.computeIfAbsent(path, k -> new LinkedHashMap<>())
                    .merge(type, 1, Integer::sum);

            // Recurse into nested documents (but not DBRefs)
            if (type == BsonType.DOCUMENT && !isDBRef(value.asDocument())) {
                analyzeDocument(path, value.asDocument(), counts);
            }
        }
    }
    
    private boolean isDBRef(final org.bson.BsonDocument doc) {
        return doc.containsKey("$ref") && doc.containsKey("$id");
    }
    
    private List<FieldMetadata> buildFieldMetadata(final Map<String, Map<BsonType, Integer>> counts,
                                                   final int docCount) {
        final List<FieldMetadata> fields = new ArrayList<>();

        for (final Map.Entry<String, Map<BsonType, Integer>> entry : counts.entrySet()) {
            final String path = entry.getKey();
            final Map<BsonType, Integer> typeCounts = entry.getValue();

            // Find the most common type for this field
            BsonType dominantType = BsonType.NULL;
            int maxCount = 0;
            int totalCount = 0;

            for (final Map.Entry<BsonType, Integer> tc : typeCounts.entrySet()) {
                totalCount += tc.getValue();
                if (tc.getValue() > maxCount) {
                    maxCount = tc.getValue();
                    dominantType = tc.getKey();
                }
            }

            // probabilities based on sample size (higher means more confident that the schema is consistent for all documents in the collection)
            final double probability = docCount > 0 ? (double) totalCount / docCount : 0.0;
            fields.add(new FieldMetadata(path, dominantType, probability));
        }

        return fields;
    }

    /**
     * Detect references from field metadata (DBRefs and *Id fields with ObjectIds)
     */
    private List<ReferenceMetadata> detectReferences(final String collectionName,
                                                     final List<FieldMetadata> fields) {
        final List<ReferenceMetadata> refs = new ArrayList<>();

        for (final FieldMetadata field : fields) {
            // Detect ObjectId fields ending in "Id" (e.g., "userId" -> "users")
            if (field.bsonType() == BsonType.OBJECT_ID &&
                    field.path().endsWith("Id") &&
                    !field.path().equals("_id")) {

                final String fieldName = field.path().substring(0, field.path().length() - 2);
                final String targetCollection = fieldName + "s"; // Simple pluralization
                refs.add(new ReferenceMetadata(collectionName, field.path(),
                        targetCollection, ReferenceType.OBJECT_ID_FIELD));
            }

            // Detect DBRef fields (they appear as DOCUMENT type with $ref path)
            if (field.path().endsWith(".$ref") && field.bsonType() == BsonType.STRING) {
                final String refField = field.path().substring(0, field.path().length() - 5);
                // We'd need to sample to get the actual target collection name
                refs.add(new ReferenceMetadata(collectionName, refField,
                        "?", ReferenceType.DBREF));
            }
        }

        return refs;
    }
    
    public Set<String> getCollectionNames() {
        return collectionSchemas.keySet();
    }
    
    public List<CollectionMetadata> getCollectionMetadata() {
        return new ArrayList<>(collectionSchemas.values());
    }
    
    public CollectionMetadata getCollectionMetadata(final String collectionName) {
        return collectionSchemas.get(collectionName.toLowerCase());
    }

    /**
     * Parse a fURI to extract collection name and document identifier.
     * Format: /collection_name/doc_id or /collection_name/+ for all documents
     * Returns null if not a collection path (i.e., first segment is not a known collection)
     */
    private List<String> parseCollectionPath(final fURI furi) {
        final List<String> segments = furi.segments();
        if (segments.isEmpty())
            return null;
        // First segment should be the collection name
        final String collectionName = segments.getFirst();
        if (!this.collectionSchemas.containsKey(collectionName.toLowerCase()))
            return null;
        final List<String> collectionPath = new ArrayList<>(segments);
        // Default to wildcard if only collection name is provided
        //if (segments.size() == 1)
        //    collectionPath.add("+");
        return collectionPath;
    }
    
    public boolean isCollectionPath(final fURI furi) {
        return parseCollectionPath(furi.asNode()) != null;
    }


    public String getCollectionName(final fURI furi) {
        final List<String> parsed = parseCollectionPath(furi.asNode());
        return parsed != null ? parsed.getFirst() : null;
    }
    
    public String getDocumentId(final fURI furi) {
        final List<String> parsed = parseCollectionPath(furi.asNode());
        return parsed != null && parsed.size() > 1 ? parsed.get(1) : null;
    }

    /**
     * Get the remaining path segments after collection/document.
     * Used for field-level access like /collection/doc/field/subfield
     * Returns null if no additional segments.
     */
    public List<String> getFieldPath(final fURI furi) {
        final List<String> parsed = parseCollectionPath(furi.asNode());
        if (parsed == null || parsed.size() <= 2)
            return null;
        return parsed.subList(2, parsed.size());
    }
}
