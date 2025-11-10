package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MBytes.bytes;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Bytes extends Mono, PlusMonoid<Bytes> {
    @Override
    Bytes clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    ByteBuffer jvm();

    default Bytes jvm(final Long jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Bytes tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Bytes vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    default Bytes c(cInt c) {
        return (Bytes) Mono.super.c(c);
    }

    @Override
    default Bytes zero() {
        return bytes(ByteBuffer.wrap(new byte[0]));
    }

    @Override
    default Bytes plus(final Bytes rhs) {
        final ByteBuffer buffer = ByteBuffer.allocate(this.jvm().remaining() + rhs.jvm().remaining());
        buffer.put(this.jvm().duplicate());
        buffer.put(rhs.jvm().duplicate());
        buffer.flip();
        return this.jvm(buffer);
    }


}
