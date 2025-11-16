package studio.phaseshift.metatron.lang.util.serial;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.util.Common;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Serializers {

    final Map<fURI, ObjSerializer<?>> serializers;

    public Serializers() {
        this.serializers = new HashMap<>();
        this.add(new mParserObjSerializer());
        this.add(new ObjByteBufferSerializer());
        this.add(ObjStringSerializer.build().simpleColon(false).prettyPrint(false).create());
    }

    public ObjSerializer<?> get(final fURI tid) {
        return this.serializers.get(tid);
    }

    public void add(final ObjSerializer<?> serializer) {
        this.serializers.put(serializer.tid(), serializer);
    }

    @Override
    public String toString() {
        return this.serializers.toString();
    }

    public Rec getSerializers() {
        return this.serializers.entrySet().stream().map(kv -> rel(kv.getKey().toUri(), uri(kv.getValue().getClass().getSimpleName()))).collect(new Common.RecCollector());
    }

}
