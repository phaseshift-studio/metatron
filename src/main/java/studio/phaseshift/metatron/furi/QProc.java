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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.Addressable;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.TriFunction;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public interface QProc extends Rec {

    fURI QPROC_TID = M_ISA_TID.extend("qproc");
    fURI ON_WRITE_TID = QPROC_TID.extend("on_write");
    fURI ON_READ_TID = QPROC_TID.extend("on_read");

    fURI ON_WRITE = f("on_write");
    fURI PRE_WRITE = f("pre_write");
    fURI POST_WRITE = f("post_write");
    fURI QLESS_WRITE = f("qless_write");
    fURI ON_READ = f("on_read");
    fURI PRE_READ = f("pre_read");
    fURI POST_READ = f("post_read");
    Type QPROC_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(QPROC_TID)
            .isaPredicate(rec(uri(PATTERN), URI_TYPE,
                    uri(PRE_WRITE).maybe(), INST_TYPE,
                    uri(POST_WRITE).maybe(), INST_TYPE,
                    uri(QLESS_WRITE).maybe(), INST_TYPE,
                    uri(PRE_READ).maybe(), INST_TYPE,
                    uri(POST_READ).maybe(), INST_TYPE))
            .constructor(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(QPROC_TID),
                    lst(T(QPROC_TID)),
                    (lhs, inst) -> new BaseQ(inst.arg(0).asRec().jvm(),
                            inst.arg(0).asRec().at(PATTERN).uriValue(),
                            inst.arg(0).tid()))).create();


    fURI pattern();

    Optional<OnWrite> onWrite();

    Optional<OnRead> onRead();

    interface OnWrite extends Rec {
        default Optional<Obj> preWrite(final fURI vid, final Obj obj) {
            return Optional.empty();
        }

        default Optional<Obj> postWrite(final fURI vid, final Obj oldObj, final Obj newObj) {
            return Optional.empty();
        }

        default Optional<Obj> qlessWrite(final fURI vid, final Obj obj) {
            return Optional.empty();
        }

        default boolean hasQlessHandler() {
            return false;
        }

        /**
         * WHAT the qless handler is, which decides WHEN it runs ({@link Layer#write}). A GUARD (lockq, constq, typeq)
         * judges the write, may answer for it, and so runs before the access. A BROADCAST (subq) only tells others
         * that a write happened, so it runs once the write has landed: a subscriber that registers and then reads
         * must find the write in one place or the other.
         *
         * @return true when the qless handler is a broadcast rather than a guard
         */
        default boolean broadcasts() {
            return false;
        }

        @Override
        default Rec clone(final Object jvm, final fURI tid, final fURI vid) {
            return this;
        }

        @Override
        default Map<Obj, Obj> jvm() {
            return mutableMap();
        }

        @Override
        default Rec at(final Obj key, final Obj value) {
            return this;
        }

        @Override
        default Rec plus(final Rec objs) {
            return this;
        }

        @Override
        default fURI tid() {
            return ON_WRITE_TID;
        }

        @Override
        default fURI vid() {
            return null;
        }

        @Override
        default Rec self(final Object jvm, final fURI tid, final fURI vid) {
            return this;
        }

        @Override
        default Obj clone() {
            return this;
        }
    }

    interface OnRead extends Rec {
        default Optional<Obj> preRead(final fURI vid) {
            return Optional.empty();
        }

        default Optional<Obj> postRead(final fURI vid, final Obj obj) {
            return Optional.empty();
        }

        @Override
        default Rec clone(final Object jvm, final fURI tid, final fURI vid) {
            return this;
        }

        @Override
        default Map<Obj, Obj> jvm() {
            return mutableMap();
        }

        @Override
        default Rec at(final Obj key, final Obj value) {
            return this;
        }

        @Override
        default Rec plus(final Rec objs) {
            return this;
        }

        @Override
        default fURI tid() {
            return ON_READ_TID;
        }

        @Override
        default fURI vid() {
            return null;
        }

        @Override
        default Obj clone() {
            return this;
        }
    }

    final class Helper {

        public static String qToString(final QProc qProc) {
            return Obj.Helper.objToString(qProc);
            //return Graphitty.string("{{b}}" + space.tid() + "{{g}}::[{{c}}pattern:{{b}}" + space.pattern() + "{{g}}]{{X}}");
        }

        public static int qHashCode(final QProc qProc) {
            return Objects.hash(qProc.tid(), qProc.vid(), qProc.pattern());
        }

        public static boolean qEquals(final QProc qProc, final Object other) {
            return other instanceof Space &&
                    ((Obj) other).tid().equals(qProc.tid()) &&
                    (qProc.vid() != null && ((Obj) other).vid() != null && ((Obj) other).vid().equals(qProc.vid()));
        }

        /**
         * A query on a vid is answered only by a processor the SOURCE declares — {@code ?subq} on a source
         * that never declared subq is a mistake worth naming, not a silent no-op.
         *
         * @param source the addressable the vid is handed to
         * @param vid    the addressed vid
         * @return true (a declaration mismatch throws instead of answering)
         */
        public static boolean checkQProcs(final Addressable source, final fURI vid) {
            if (vid.hasQ()) {
                final List<String> qSpace = source.qs().lstValue().stream().map(q -> q.asRec().at(PATTERN).uriValue()).map(fURI::toString).toList();
                vid.qMap().keySet().stream().filter(k -> k.endsWith("q")).filter(k -> !k.equals(DOCQ)).forEach(k -> {
                    if (!qSpace.contains(k))
                        throw MTronException.of("no %s query processor attached to %s", k, source.pattern());
                });
            }
            return true;
        }

        public static Optional<Obj> processPreWrite(final Lst qs, final fURI vid, final Obj obj) {
            if (!vid.hasQ() || qs.isEmpty()) return Optional.empty();
            Obj acc = null;
            for (final QProc q : qs.<QProc>elements().toList()) {
                if (vid.hasQ(q.pattern()) && q.onWrite().isPresent()) {
                    final Optional<Obj> r = q.onWrite().get().preWrite(vid, obj);
                    if (r.isPresent())
                        acc = acc == null ? r.get() : acc.append(r.get());
                }
            }
            return Optional.ofNullable(acc);
        }

        public static Optional<Obj> processPreRead(final Lst qs, final fURI vid) {
            if (!vid.hasQ() || qs.isEmpty()) return Optional.empty();
            Obj acc = null;
            for (final QProc q : qs.<QProc>elements().toList()) {
                if (vid.hasQ(q.pattern()) && q.onRead().isPresent()) {
                    final Optional<Obj> r = q.onRead().get().preRead(vid);
                    if (r.isPresent())
                        acc = acc == null ? r.get() : acc.append(r.get());
                }
            }
            return Optional.ofNullable(acc);
        }

        public static Obj processPostRead(final Lst qs, final fURI id, final Obj obj) {
            return obj.isObjs() ? objs(obj.stream().map(current -> processPostReadIndividual(qs, id, current).orElse(current))) : processPostReadIndividual(qs, id, obj).orElse(obj);
        }

        private static Optional<Obj> processPostReadIndividual(final Lst qs, final fURI vid, final Obj current) {
            if (!vid.hasQ() || qs.isEmpty()) return Optional.empty();
            Obj acc = current;
            boolean found = false;
            for (final QProc q : qs.<QProc>elements().toList()) {
                if (vid.hasQ(q.pattern()) && q.onRead().isPresent()) {
                    final Optional<Obj> r = q.onRead().get().postRead(vid, current);
                    if (r.isPresent()) {
                        found = true;
                        acc = r.get();
                    }
                }
            }
            // noobj is a result, not an absence: a qproc that finds nothing for this read suppresses it rather
            // than handing the caller the result the read would have had without the qproc.
            return Optional.ofNullable(found ? acc : null);
        }

        /**
         * Every qless handler, guard and broadcast alike, in declaration order: for a source that runs its own write
         * sequence instead of the layer's ({@code mqttSpace}).
         */
        public static Optional<Obj> processQlessWrite(final Lst qs, final fURI vid, final Obj obj) {
            return processQless(qs, vid, obj, w -> true);
        }

        /**
         * The qless GUARDS, before the access: an answer stands in for the write, which then never happens.
         */
        public static Optional<Obj> processQlessGuards(final Lst qs, final fURI vid, final Obj obj) {
            return processQless(qs, vid, obj, w -> !w.broadcasts());
        }

        /**
         * The qless BROADCASTS, once the access has landed. They have no answer to give: the write already happened.
         */
        public static void processQlessBroadcasts(final Lst qs, final fURI vid, final Obj obj) {
            processQless(qs, vid, obj, QProc.OnWrite::broadcasts);
        }

        private static Optional<Obj> processQless(final Lst qs, final fURI vid, final Obj obj,
                                                  final Predicate<QProc.OnWrite> kind) {
            Obj acc = null;
            for (final QProc q : qs.<QProc>elements().toList()) {
                if (q.onWrite().filter(QProc.OnWrite::hasQlessHandler).filter(kind).isPresent()) {
                    final Optional<Obj> r = q.onWrite().get().qlessWrite(vid, obj);
                    if (r.isPresent())
                        acc = acc == null ? r.get() : acc.append(r.get());
                }
            }
            return Optional.ofNullable(acc).filter(a -> !a.isNoObj());
        }

        public static Optional<Obj> processPostWrite(final Lst qs, final fURI vid, final Obj obj) {
            if (!vid.hasQ() || qs.isEmpty()) return Optional.empty();
            Obj acc = null;
            for (final QProc q : qs.<QProc>elements().toList()) {
                if (vid.hasQ(q.pattern()) && q.onWrite().isPresent()) {
                    final Optional<Obj> r = q.onWrite().get().postWrite(vid, obj, obj);
                    if (r.isPresent())
                        acc = acc == null ? r.get() : acc.append(r.get());
                }
            }
            return Optional.ofNullable(acc).filter(a -> !a.isNoObj());
        }

        private Helper() {
            // do nothing
        }

        public static Builder build(final fURI tid, final fURI pattern) {
            return new Builder(tid, pattern);
        }


        public static class Builder {

            protected Map<fURI, Object> jvm = new LinkedHashMap<>();

            protected final fURI pattern;
            protected final fURI tid;
            protected boolean broadcasts = false;

            protected Builder(final fURI tid, final fURI pattern) {
                this.pattern = pattern;
                this.tid = tid;
            }

            public Builder preRead(final Function<fURI, Obj> preRead) {
                this.jvm.put(PRE_READ, preRead);
                return this;
            }

            public Builder postRead(final BiFunction<fURI, Obj, Obj> postRead) {
                this.jvm.put(POST_READ, postRead);
                return this;
            }

            public Builder preWrite(final BiFunction<fURI, Obj, Obj> preWrite) {
                this.jvm.put(PRE_WRITE, preWrite);
                return this;
            }

            public Builder postWrite(final TriFunction<fURI, Obj, Obj, Obj> postWrite) {
                this.jvm.put(POST_WRITE, postWrite);
                return this;
            }

            /**
             * The qless handler as a GUARD: it runs before the access, and an answer stands in for the write.
             */
            public Builder qlessWrite(final BiFunction<fURI, Obj, Obj> qlessWrite) {
                this.jvm.put(QLESS_WRITE, qlessWrite);
                return this;
            }

            /**
             * The qless handler as a BROADCAST: it runs once the access has landed, and its answer is ignored (see
             * {@link OnWrite#broadcasts()}).
             */
            public Builder broadcast(final BiFunction<fURI, Obj, Obj> broadcast) {
                this.broadcasts = true;
                return this.qlessWrite(broadcast);
            }

            public Builder obj(final fURI key, final Obj value) {
                this.jvm.put(key, value);
                return this;
            }

            public QProc create() {
                final QProc qProc = BaseQ.create(this.tid, this.pattern,
                        (Function<fURI, Obj>) this.jvm.get(PRE_READ),
                        (BiFunction<fURI, Obj, Obj>) this.jvm.get(POST_READ),
                        (BiFunction<fURI, Obj, Obj>) this.jvm.get(PRE_WRITE),
                        (TriFunction<fURI, Obj, Obj, Obj>) this.jvm.get(POST_WRITE),
                        (BiFunction<fURI, Obj, Obj>) this.jvm.get(QLESS_WRITE),
                        this.broadcasts);
                if (jvm.containsKey(f(OBJ)))
                    qProc.jvm().put(uri(OBJ), (Obj) jvm.get(f(OBJ)));
                if (jvm.containsKey(f(INST)))
                    qProc.jvm().put(uri(INST), (Obj) jvm.get(f(INST)));
                return qProc;
            }
        }
    }

    /**
     * THE Q-PROC PIPELINE — one implementation of the read and write sequences, so no source re-derives the
     * order and no source has to know what a q-proc does.
     * <p>
     * A source hands the layer the access it would have performed on its own (the {@link Supplier}) and the
     * layer brackets it: {@code pre_read} before and {@code post_read} after; {@code pre_write} and the
     * {@code qless_write} guards before, the {@code qless_write} broadcasts and {@code post_write} after (see
     * {@link OnWrite#broadcasts()}). A q-proc that answers from its own hook
     * — an {@code ?incrq} that assigns and stores, a {@code ?subq} that only registers — replaces the access
     * rather than decorating it, which is what the pre hooks' answer already means.
     * <p>
     * The write's ANSWER is whatever the supplier returns: a space returns the obj the caller handed in (a
     * store's own return value is bookkeeping, and a caller that wrote {@code x} expects {@code x} back), while a
     * transport returns the peer's reply, which is the value its caller is actually waiting for.
     */
    final class Layer {

        private Layer() {
            // static only
        }

        /**
         * the read pipeline: {@code pre_read} → the source's own read → {@code post_read}.
         * <p>
         * NO DECLARATION CHECK HERE. Which queries a source speaks is the source's own verdict, given where it
         * finally handles the address ({@code AbstractSpace} calls {@link Helper#checkQProcs}); a source the
         * dispatch merely OFFERS an address to — the network, which declines everything without a host — must not
         * refuse a {@code ?incrq} that belongs to the space the write is on its way to.
         *
         * @param source the source the q-procs are declared on
         * @param vid    the address read
         * @param read   the access to run when no q-proc answers first
         * @return the q-proc's answer, or the source's post-processed read
         */
        public static Obj read(final Addressable source, final fURI vid, final Supplier<Obj> read) {
            if (!source.hasQProcs())
                return read.get();             // the hot path: one map lookup, no list, straight to the source
            final Lst qs = source.qs();
            return Helper.processPreRead(qs, vid).orElseGet(() -> Helper.processPostRead(qs, vid, read.get()));
        }

        /**
         * the write pipeline: {@code pre_write} → the {@code qless_write} guards → the source's own write → the
         * {@code qless_write} broadcasts → {@code post_write}.
         * <p>
         * A GUARD runs BEFORE the access, because it may refuse it. A BROADCAST runs AFTER it, because only a
         * landed write is news. Broadcasting first lost writes: a subscriber that registered and then read between
         * a writer's broadcast and its store found the write in neither place, so a barrier's subscribe-then-read
         * could wait forever on a report sitting in its mailbox (MachineSwarmMigrationTest). It also published
         * writes a guard went on to refuse. A source that orders its write differently ({@code mqttSpace} runs
         * every qless handler after its own write) is not routed through this layer yet and keeps its own sequence.
         *
         * @param source the source the q-procs are declared on
         * @param vid    the address written
         * @param obj    the value written
         * @param write  the access to run when no q-proc answers first
         * @return the q-proc's answer, or the source's post-processed write
         */
        public static Obj write(final Addressable source, final fURI vid, final Obj obj, final Supplier<Obj> write) {
            if (!source.hasQProcs())
                return write.get();            // the hot path: one map lookup, no list, straight to the source
            final Lst qs = source.qs();
            return Helper.processPreWrite(qs, vid, obj)
                    .orElseGet(() -> Helper.processQlessGuards(qs, vid, obj).orElseGet(() -> {
                        final Obj result = write.get();
                        Helper.processQlessBroadcasts(qs, vid, obj);
                        return Helper.processPostWrite(qs, vid, result).orElse(result);
                    }));
        }
    }

}
