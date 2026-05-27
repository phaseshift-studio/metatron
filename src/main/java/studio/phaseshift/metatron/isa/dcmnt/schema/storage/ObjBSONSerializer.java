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

package studio.phaseshift.metatron.isa.dcmnt.schema.storage;

import org.bson.*;
import org.bson.codecs.BsonValueCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.dcmnt.space.dcmntSpace;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjBSONSerializer extends AbstractObjSerializer<BsonValue> {

    private static final GraphittyLogger LOG = Graphitty.log(ObjBSONSerializer.class);

    public static final Byte BYTES_MAGIC_NUMBER = (byte) 0x00;
    public static final Byte URI_MAGIC_NUMBER = (byte) 0x01;
    public static final Byte FAIL_MAGIC_NUMBER = (byte) 0x02;

    public static final fURI OBJ_BSON_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("bson");

    /**
     * Hidden BSON field that stores the nominal TID of a document for round-trip fidelity.
     * Only written when the TID differs from the base {@code rec::T} (i.e., the document
     * carries a named type like {@code chicken::T} or {@code users::T}).
     * Stripped on read and applied via {@link Rec#selfTID(fURI)}.
     */
    public static final String MTRON_TID_FIELD = "__mtron_tid";


    public static final ObjBSONSerializer SINGLE = new ObjBSONSerializer();

    private static final Codec<BsonValue> BSON_VALUE_CODEC = new BsonValueCodec();

    // Optional: Function to build reference paths (set by dcmntSpace)
    private Function<ReferenceInfo, fURI> referencePathBuilder = null;

    // Space-local URI scheme, used to discriminate intra- vs cross-space auto_from refs.
    // When set, writeInst encodes cross-space refs as $ref: "scheme:collection".
    private String localScheme = null;
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

    /**
     * Sets the space-local URI scheme. When set, auto_from Insts with a different scheme
     * are serialized as cross-space DBRefs ({@code $ref: "scheme:collection"}) instead of
     * bare collection names.
     */
    public void setLocalScheme(final String scheme) {
        this.localScheme = scheme;
    }

    public static ObjBSONSerializer single() {
        return SINGLE;
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
            LOG.warn("unknown binary magic byte 0x%s — returning noobj: %s", Integer.toHexString(magic & 0xFF), bson);
            return noobj();
        }
        if (bson.isDocument())
            return this.readRec(bson);
        if (bson.isArray())
            return this.readLst(bson);
        LOG.warn("unknown bson type — returning noobj: %s", bson.getClass());
        return noobj();
    }

    @Override
    public Bytes readBytes(final BsonValue bson) {
        final byte[] b = bson.asBinary().getData();
        return bytes(ByteBuffer.wrap(b, 1, b.length - 1));
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
            // Custom binary encoding: [URI_MAGIC_NUMBER (1 byte), ...uri string bytes...]
            final byte[] b = bson.asBinary().getData();
            if (b.length < 1 || b[0] != URI_MAGIC_NUMBER)
                return uri(NOOBJ_TID).c(cInt.ZERO()).as();
            return uri(new String(b, 1, b.length - 1));
        }
    }

    @Override
    public Fail readFail(final BsonValue bson) {
        final byte[] b = bson.asBinary().getData();
        return fail(new String(b, 1, b.length - 1));
    }

    @Override
    public Lst readLst(final BsonValue bson) {
        return lst(bson.asArray().stream().map(this::read).toList());
    }

    @Override
    public Rec readRec(final BsonValue bson) {
        final BsonDocument doc = bson.asDocument();

        // Extract and strip the hidden TID field before building the Rec
        final String storedTid = doc.containsKey(MTRON_TID_FIELD)
                ? doc.getString(MTRON_TID_FIELD).getValue()
                : null;

        // Build the Rec from all fields except the hidden TID field
        final Rec result = doc.entrySet().stream()
                .filter(kv -> !kv.getKey().equals(MTRON_TID_FIELD))
                .map(kv -> {
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
                                : idValue.isString()
                                ? idValue.asString().getValue()
                                : idValue.toString();

                        final fURI referencedPath;
                        if (collection.indexOf(':') >= 0) {
                            // Cross-space DBRef: $ref: "grph:V" → grph:V/1
                            referencedPath = f(collection).extend(id);
                        } else {
                            // Intra-space DBRef: $ref: "users" → mongo:users/507f...
                            referencedPath = this.referencePathBuilder.apply(
                                    new ReferenceInfo(collection, id));
                        }
                        return rel(uri(key), auto_from_(referencedPath).tryToInst());
                    }

                    // Check if this field is a potential reference (ends with "Id" and is an ObjectId)
                    if (this.referencePathBuilder != null &&
                            key.endsWith("Id") &&
                            !key.equals("_id") &&
                            value.isObjectId()) {

                        final String fieldName = key.substring(0, key.length() - 2); // Remove "Id"
                        final String collectionName = fieldName + "s"; // Simple pluralization
                        final String id = value.asObjectId().getValue().toHexString();

                        final fURI referencedPath = this.referencePathBuilder.apply(new ReferenceInfo(collectionName, id));
                        return rel(uri(key), auto_from_(referencedPath).tryToInst());
                    }

                    // Regular field
                    return rel(uri(key), this.read(value));
                }).collect(new CommonUtil.RecCollector());

        // Restore nominal TID (e.g. chicken::T) without triggering type checking
        if (storedTid != null)
            result.selfTID(f(storedTid));

        return result;
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
        final List<BsonElement> elements = new ArrayList<>();
        // Preserve nominal TID for round-trip fidelity — skip for base types (rec::T, etc.)
        if (!rec.type().isBaseType())
            elements.add(new BsonElement(MTRON_TID_FIELD, new BsonString(rec.tid().toString())));
        rec.jvm().entrySet().stream()
                .map(kv -> new BsonElement(kv.getKey().jvm().toString(), this.write(kv.getValue())))
                .forEach(elements::add);
        return new BsonDocument(elements);
    }

    // -- structured type overrides (required to prevent default infinite recursion) --

    @Override
    public BsonDocument writeInst(final Inst inst) {
        final fURI baseTid = inst.tid().basePath();
        if (baseTid.equals(mInstSet.AUTO_FROM_INST_TID) ||
            baseTid.equals(mInstSet.AUTO_AT_INST_TID)) {
            // Serialize auto_from / auto_at as a MongoDB DBRef.
            // Intra-space:  $ref: "collection",     $id: value  (native MongoDB footprint)
            // Cross-space: $ref: "scheme:collection", $id: value  (scheme prefixed in $ref)
            final fURI refURI = inst.arg(0).uriValue();
            final List<String> segs = refURI.segments();
            final String id = segs.getLast();
            final String collection;
            if (this.localScheme != null && refURI.hasScheme() &&
                !refURI.scheme().equals(this.localScheme)) {
                // Cross-space: g:V/1 → $ref: "g:V"
                collection = refURI.scheme() + ":" + segs.getFirst();
            } else {
                // Intra-space: mongo:users/507f... → $ref: "users"
                collection = segs.getFirst();
            }
            // 24-char hex → BsonObjectId (native MongoDB); otherwise BsonString
            final BsonValue idValue = (id != null && dcmntSpace.OBJECT_ID_REGEX.matcher(id).matches())
                    ? new BsonObjectId(new org.bson.types.ObjectId(id))
                    : new BsonString(id != null ? id : "");
            return new BsonDocument(List.of(
                    new BsonElement("$ref", new BsonString(collection)),
                    new BsonElement("$id", idValue)
            ));
        }
        throw MTronException.of("unsupported Inst type in BSON serializer: %s", inst.tid());
    }

    @Override
    public BsonDocument writeRel(final Rel rel) {
        return new BsonDocument(List.of(
                new BsonElement("k", this.write(rel.jvm().get0())),
                new BsonElement("v", this.write(rel.jvm().get1()))
        ));
    }

    @Override
    public BsonString writeCode(final Code code) {
        return new BsonString(code.toString());
    }

    @Override
    public BsonArray writeObjs(final Objs objs) {
        final List<BsonValue> out = new ArrayList<>();
        objs.jvm().forEach(obj -> out.add(this.write(obj)));
        return new BsonArray(out);
    }

    @Override
    public BsonString writeType(final Type type) {
        return new BsonString(type.toString());
    }

    @Override
    public BsonDocument writeMonad(final PCMonad monad) {
        return new BsonDocument(List.of(
                new BsonElement("obj", this.write(monad.obj())),
                new BsonElement("inst", this.write(monad.inst()))
        ));
    }

    @Override
    public BsonDocument writeMachine(final Machine machine) {
        return new BsonDocument(List.of(
                new BsonElement("code", this.writeCode(machine.code()))
        ));
    }
}
