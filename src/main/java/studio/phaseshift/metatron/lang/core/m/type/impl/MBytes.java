package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Bytes;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.BYTES_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MBytes extends MObj implements Bytes {

    public MBytes(final ByteBuffer jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Bytes bytes(final ByteBuffer jvm) {
        return new MBytes(jvm, BYTES_TID, fURI.NULL);
    }

    @Override
    public Bytes clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public ByteBuffer jvm() {
        return (ByteBuffer) this.jvm;
    }

}