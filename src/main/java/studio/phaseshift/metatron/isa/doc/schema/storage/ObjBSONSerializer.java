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

package studio.phaseshift.metatron.isa.doc.schema.storage;

import org.bson.*;
import org.bson.codecs.*;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.io.BasicOutputBuffer;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Function;

import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjBSONSerializer extends AbstractObjSerializer<BsonValue> {

    public static final Byte BYTES_MAGIC_NUMBER = (byte) 0x00;
    public static final Byte URI_MAGIC_NUMBER = (byte) 0x01;
    public static final Byte FAIL_MAGIC_NUMBER = (byte) 0x02;

    public static final fURI OBJ_BSON_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("bson");

    private static final Codec<BsonValue> BSON_VALUE_CODEC = new BsonValueCodec();

    // Optional: Function to build reference paths (set by docSpace)
    private Function<ReferenceInfo, fURI> referencePathBuilder = null;
  //  private ObjFactory objFactory = MObjFactory.single();

    /**
     * Information about a detected reference
     */
    public record ReferenceInfo(String collection, String id) {
    }

    public ObjBSONSerializer() {
    }

    /**
     * Set the reference path builder for lazy reference resolution
     *
     * @param builder Function that takes collection name and ID and returns a full fURI
     */
    public void setReferencePathBuilder(final Function<ReferenceInfo, fURI> builder) {
        this.referencePathBuilder = builder;
    }

    public fURI vid() {
        return OBJ_BSON_SERIALIZER_VID;
    }

    public fURI jvm() {
        return OBJ_BSON_SERIALIZER_VID;
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
        BSON_VALUE_CODEC.encode(writer, this.write(obj), EncoderContext.builder().isEncodingCollectibleDocument(obj.isRec()).build());
        buffer.close();
        return ByteBuffer.wrap(buffer.toByteArray());
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        BsonBinaryReader reader = new BsonBinaryReader(bytes);
        BsonValue value = BSON_VALUE_CODEC.decode(reader, DecoderContext.builder().build());
        reader.close();
        return this.read(value);
    }

    @Override
    public Obj read(final BsonValue bson) {
        if (bson.isNull())
            return noobj();
        if (bson.isBoolean())
            return this.readBool(bson);
        if (bson.isInt32())
            return this.readInt(bson);
        if (bson.isInt64())
            return this.readInt(bson);
        if (bson.isDouble())
            return this.readReal(bson);
        if (bson.isString())
            return this.readStr(bson);
        if (bson.isObjectId())
            return this.readUri(bson);
        if (bson.isDateTime())
            return this.readInt(bson);  // datetime maps to int (milliseconds since epoch)
        if (bson.isBinary()) {
            final Byte magic = bson.asBinary().getData()[0];
            if (Objects.equals(magic, BYTES_MAGIC_NUMBER)) {
                return this.readBytes(bson);
            } else if (Objects.equals(magic, URI_MAGIC_NUMBER)) {
                return this.readUri(bson);
            } else if (Objects.equals(magic, FAIL_MAGIC_NUMBER)) {
                return this.readFail(bson);
            }
        }
        if (bson.isDocument())
            return this.readRec(bson);
        if (bson.isArray())
            return this.readLst(bson);
        throw MTronException.of("unknown bson type: %s", bson.getClass());
    }

    @Override
    public Bytes readBytes(final BsonValue bson) {
        final byte[] b = bson.asBinary().getData();
        return bytes(ByteBuffer.wrap(b, 2, b.length));
    }

    @Override
    public Bool readBool(final BsonValue bson) {
        return bool(bson.asBoolean().getValue());
    }

    @Override
    public Int readInt(final BsonValue bson) {
        if (bson.isInt32())
            return jnt(bson.asInt32().getValue());
        else if (bson.isInt64())
            return jnt(bson.asInt64().getValue());
        else if (bson.isDateTime())
            return jnt(bson.asDateTime().getValue());  // milliseconds since epoch
        else
            throw MTronException.of("Cannot convert %s to Int", bson.getClass());
    }

    @Override
    public Real readReal(final BsonValue bson) {
        return real(bson.asDouble().getValue());
    }

    @Override
    public Str readStr(final BsonValue bson) {
        return str(bson.asString().getValue());
    }

    @Override
    public Uri readUri(final BsonValue bson) {
        if (bson.isObjectId()) {
            // MongoDB ObjectId -> convert to hex string URI
            return uri(bson.asObjectId().getValue().toHexString());
        } else {
            // Custom binary encoding
            final byte[] b = bson.asBinary().getData();
            return uri(new String(ByteBuffer.wrap(b, 2, b.length).array()));
        }
    }

    @Override
    public Fail readFail(final BsonValue bson) {
        final byte[] b = bson.asBinary().getData();
        return fail(new String(ByteBuffer.wrap(b, 2, b.length).array()));
    }

    @Override
    public Lst readLst(final BsonValue bson) {
        return lst(bson.asArray().stream().map(this::read).toList());
    }

    @Override
    public Rec readRec(final BsonValue bson) {
        final BsonDocument doc = bson.asDocument();

        // Regular document - check each field for potential references
        return doc.entrySet().stream().map(kv -> {
            final String key = kv.getKey();
            final BsonValue value = kv.getValue();

            // Check if this field value is a DBRef pattern: { $ref: "collection", $id: ObjectId(...) }
            if (this.referencePathBuilder != null &&
                    value.isDocument() &&
                    value.asDocument().containsKey("$ref") &&
                    value.asDocument().containsKey("$id")) {

                final BsonDocument refDoc = value.asDocument();
                final String collection = refDoc.getString("$ref").getValue();
                final BsonValue idValue = refDoc.get("$id");
                final String id = idValue.isObjectId()
                        ? idValue.asObjectId().getValue().toHexString()
                        : idValue.toString();

                final fURI referencedPath = this.referencePathBuilder.apply(new ReferenceInfo(collection, id));
                return rel(uri(key), auto_from_(referencedPath).tryToInst());
            }

            // Check if this field is a potential reference (ends with "Id" and is an ObjectId)
            if (this.referencePathBuilder != null &&
                    key.endsWith("Id") &&
                    !key.equals("_id") &&
                    value.isObjectId()) {

                // Extract the collection name from the field name (e.g., "userId" -> "users")
                final String fieldName = key.substring(0, key.length() - 2); // Remove "Id"
                final String collectionName = fieldName + "s"; // Simple pluralization
                final String id = value.asObjectId().getValue().toHexString();

                final fURI referencedPath = this.referencePathBuilder.apply(new ReferenceInfo(collectionName, id));
                return rel(uri(key), auto_from_(referencedPath).tryToInst());
            }

            // Regular field
            return rel(uri(key), this.read(value));
        }).collect(new CommonUtil.RecCollector());
    }


    @Override
    public BsonBinary writeBytes(final Bytes bytes) {
        return new BsonBinary(bytes(new byte[]{BYTES_MAGIC_NUMBER}).plus(bytes).jvm().array());
    }

    @Override
    public BsonNull writeNoObj(final NoObj noobj) {
        return BsonNull.VALUE;
    }

    @Override
    public BsonBoolean writeBool(final Bool dool) {
        return BsonBoolean.valueOf(dool.jvm());
    }

    @Override
    public BsonBinary writeFail(final Fail fail) {
        return new BsonBinary(bytes(new byte[]{FAIL_MAGIC_NUMBER}).plus(bytes(fail.toString().getBytes())).jvm().array());
    }

    @Override
    public BsonString writeStr(final Str str) {
        return new BsonString(str.jvm());
    }

    @Override
    public BsonInt64 writeInt(final Int jnt) {
        return new BsonInt64(jnt.jvm());
    }

    @Override
    public BsonDouble writeReal(final Real real) {
        return new BsonDouble(real.jvm());
    }

    @Override
    public BsonBinary writeUri(final Uri uri) {
        return new BsonBinary(bytes(new byte[]{URI_MAGIC_NUMBER}).plus(bytes(uri.uriValue().toString().getBytes())).jvm().array());
    }

    @Override
    public BsonArray writeLst(final Lst lst) {
        return new BsonArray(lst.jvm().stream().map(this::write).toList());
    }

    @Override
    public BsonDocument writeRec(final Rec rec) {
        return new BsonDocument(rec.jvm().entrySet().stream().map(kv -> new BsonElement(kv.getKey().jvm().toString(), this.write(kv.getValue()))).toList());
    }
}
