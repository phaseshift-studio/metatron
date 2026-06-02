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

package studio.phaseshift.metatron.isa.dcmnt.schema;

import org.bson.BsonType;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;

/**
 * Bidirectional mapper between BSON types and mtron types.
 * <p>
 * Provides utilities for converting between MongoDB's BSON type system
 * and Metatron's type system, useful for schema inference, serialization,
 * and type checking.
 *
 * <h2>Type Mappings</h2>
 * <pre>
 * BSON Type        → mtron Type
 * ─────────────────────────────
 * STRING           → str
 * INT32, INT64     → int
 * DOUBLE, DECIMAL  → real
 * BOOLEAN          → bool
 * OBJECT_ID        → uri
 * DOCUMENT         → rec
 * ARRAY            → lst
 * DATE_TIME        → int (epoch millis)
 * BINARY           → str (base64)
 * NULL             → noobj
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class BsonTypeMapper {

    private BsonTypeMapper() {
        // Utility class - no instantiation
    }

    /**
     * Convert a BSON type to the corresponding mtron type.
     *
     * @param bsonType the BSON type
     * @return the corresponding mtron Type
     */
    public static Type toMtronType(final BsonType bsonType) {
        return switch (bsonType) {
            // String types
            case STRING, SYMBOL, JAVASCRIPT, JAVASCRIPT_WITH_SCOPE -> STR_TYPE;

            // Integer types
            case INT32, INT64, TIMESTAMP -> INT_TYPE;

            // Floating point types
            case DOUBLE, DECIMAL128 -> REAL_TYPE;

            // Boolean
            case BOOLEAN -> BOOL_TYPE;

            // ObjectId → uri (enables cross-database references)
            case OBJECT_ID -> URI_TYPE;

            // Document/Object → rec
            case DOCUMENT -> REC_TYPE;

            // Array → lst
            case ARRAY -> LST_TYPE;

            // DateTime → int (milliseconds since epoch)
            case DATE_TIME -> INT_TYPE;

            // Binary → str (will be base64 encoded)
            case BINARY -> STR_TYPE;

            // Regex → str
            case REGULAR_EXPRESSION -> STR_TYPE;

            // Null, Undefined → defaults to str for schema purposes
            case NULL, UNDEFINED -> STR_TYPE;

            // DB Pointer (deprecated) → uri
            case DB_POINTER -> URI_TYPE;

            // Min/Max keys → str
            case MIN_KEY, MAX_KEY -> STR_TYPE;

            // Fallback
            default -> STR_TYPE;
        };
    }

    /**
     * Convert a mtron type to the most appropriate BSON type.
     *
     * @param mtronType the mtron type
     * @return the corresponding BsonType
     */
    public static BsonType toBsonType(final Type mtronType) {
        final String tid = mtronType.tid().toString();

        if (tid.contains("str")) return BsonType.STRING;
        if (tid.contains("int")) return BsonType.INT64;
        if (tid.contains("real")) return BsonType.DOUBLE;
        if (tid.contains("bool")) return BsonType.BOOLEAN;
        if (tid.contains("uri")) return BsonType.STRING;  // URIs stored as strings
        if (tid.contains("rec")) return BsonType.DOCUMENT;
        if (tid.contains("lst")) return BsonType.ARRAY;
        if (tid.contains("bytes")) return BsonType.BINARY;

        // Default to string for unknown types
        return BsonType.STRING;
    }

    /**
     * Get the mtron type name for a BSON type (e.g., "int", "str", "rec").
     *
     * @param bsonType the BSON type
     * @return the mtron type name as a string
     */
    public static String toMtronTypeName(final BsonType bsonType) {
        return switch (bsonType) {
            case STRING, SYMBOL, JAVASCRIPT, JAVASCRIPT_WITH_SCOPE,
                 REGULAR_EXPRESSION, BINARY -> "str";
            case INT32, INT64, TIMESTAMP, DATE_TIME -> "int";
            case DOUBLE, DECIMAL128 -> "real";
            case BOOLEAN -> "bool";
            case OBJECT_ID, DB_POINTER -> "uri";
            case DOCUMENT -> "rec";
            case ARRAY -> "lst";
            case NULL, UNDEFINED -> "noobj";
            default -> "str";
        };
    }

    /**
     * Get the BSON type name from a mtron type name.
     *
     * @param mtronTypeName the mtron type name (e.g., "int", "str")
     * @return the BSON type name
     */
    public static String toBsonTypeName(final String mtronTypeName) {
        return switch (mtronTypeName.toLowerCase()) {
            case "str" -> "STRING";
            case "int" -> "INT64";
            case "real" -> "DOUBLE";
            case "bool" -> "BOOLEAN";
            case "uri" -> "OBJECT_ID";
            case "rec" -> "DOCUMENT";
            case "lst" -> "ARRAY";
            case "bytes" -> "BINARY";
            case "noobj" -> "NULL";
            default -> "STRING";
        };
    }
}
