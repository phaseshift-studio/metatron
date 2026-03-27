package studio.phaseshift.metatron.isa.mach.io.jdbc;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import studio.phaseshift.metatron.furi.fURI;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Minimal JSON serializer for JDBC driver.
 *
 * This serializer has ZERO dependencies on the mtron type system (Obj, Rec, Lst, etc.)
 * to allow cold-start initialization when loaded by external JDBC tools.
 *
 * It uses raw JsonElement for serialization/deserialization and delegates to the
 * server-side for actual mtron object handling.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JDBCSerializer {

    private static final fURI VID = f("/m/mach/io/serializer/jdbc");

    public fURI vid() {
        return VID;
    }

    public fURI jvm() {
        return VID;
    }

    /**
     * Serialize a JSON element to bytes for transmission
     */
    public ByteBuffer outputBytes(final JsonElement json) {
        return ByteBuffer.wrap(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserialize bytes to a JSON element
     */
    public JsonElement inputBytes(final ByteBuffer bytes) {
        String json = new String(bytes.array(), StandardCharsets.UTF_8);
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setStrictness(Strictness.LENIENT);
        return JsonParser.parseReader(reader);
    }

    /**
     * Write a string as JSON
     */
    public JsonElement write(final String data) {
        return new JsonPrimitive(data);
    }

    /**
     * Read a JSON element as string
     */
    public String read(final JsonElement json) {
        if (json.isJsonPrimitive()) {
            return json.getAsString();
        } else if (json.isJsonObject()) {
            return json.toString();
        } else if (json.isJsonArray()) {
            return json.toString();
        } else {
            return "null";
        }
    }

    /**
     * Parse a JSON string
     */
    public static JsonElement parse(final String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setStrictness(Strictness.LENIENT);
        return JsonParser.parseReader(reader);
    }
}
