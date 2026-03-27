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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.TriFunction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Q extends Rec {

    fURI Q_TID = MTRON_TID.extend("space/q");
    fURI ON_WRITE_TID = Q_TID.extend("on_write");
    fURI ON_READ_TID = Q_TID.extend("on_read");

    fURI ON_WRITE = f("on_write");
    fURI PRE_WRITE = f("pre_write");
    fURI POST_WRITE = f("post_write");
    fURI QLESS_WRITE = f("qless_write");
    fURI ON_READ = f("on_read");
    fURI PRE_READ = f("pre_read");
    fURI POST_READ = f("post_read");
    Type Q_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(Q_TID)
            .constructor(instC(INST_TID.dom(ALL.maybe()).rng(Q_TID),
                    lst(rec(uri(PATTERN), T(URI_TID),
                            uri(PRE_WRITE.maybe()), T(INST_TID),
                            uri(POST_WRITE.maybe()), T(INST_TID),
                            uri(QLESS_WRITE.maybe()), T(INST_TID),
                            uri(PRE_READ.maybe()), T(INST_TID),
                            uri(POST_READ.maybe()), T(INST_TID))),
                    (lhs, inst) -> new BaseQ(inst.arg(0).asRec().jvm(), inst.arg(0).asRec().at(PATTERN).uriValue(), inst.arg(0).tid()))).create();


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

        @Override
        default Rec clone(final Object jvm, final fURI tid, final fURI vid) {
            return this;
        }

        @Override
        default Map<Obj, Obj> jvm() {
            return Map.of();
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
            return f("/sys/space/q/on_write");
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
            return Map.of();
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
            return f("/sys/space/q/on_read");
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

        public static String qToString(final Q q) {
            return Obj.Helper.objToString(q);
            //return Graphitty.string("{{b}}" + space.tid() + "{{g}}::[{{c}}pattern:{{b}}" + space.pattern() + "{{g}}]{{X}}");
        }

        public static int qHashCode(final Q q) {
            return Objects.hash(q.tid(), q.vid(), q.pattern());
        }

        public static boolean qEquals(final Q q, final Object other) {
            return other instanceof Space &&
                    ((Q) other).tid().equals(q.tid()) &&
                    (q.vid() != null && ((Q) other).vid() != null && ((Q) other).vid().equals(q.vid()));
        }

        public static Optional<Obj> processPreWrite(final Lst qs,final fURI vid, final Obj obj) {
            return vid.hasQ() && !qs.isEmpty() ? qs.<Q>elements()
                    .filter(q -> vid.hasQ(q.pattern().toString()))
                    .map(Q::onWrite)
                    .filter(Optional::isPresent)
                    // .peek(q -> LOG.info("handling {{y}}pre write{{X}} of %s for %s %s", source, vid, obj))
                    .map(Optional::get)
                    .map(q -> q.preWrite( vid, obj))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append) : Optional.empty();
        }

        public static Optional<Obj> processPreRead(final Lst qs,final fURI vid) {
            return vid.hasQ() && !qs.isEmpty() ?
                    qs.<Q>elements()
                            .filter(q -> vid.hasQ(q.pattern().toString()))
                            .map(Q::onRead)
                            .filter(Optional::isPresent)
                            //.peek(q -> LOG.debug("handling {{m}}pre read{{X}} of %s for %s", source, vid))
                            .map(Optional::get)
                            .map(q -> q.preRead(vid))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .reduce(Obj::append) :
                    Optional.empty();
        }

        public static Optional<Obj> processPostRead(final Lst qs, final fURI vid, final Obj current) {
            return vid.hasQ() && !qs.isEmpty() ? qs.<Q>elements()
                    .filter(q -> vid.hasQ(q.pattern().toString()))
                    .map(Q::onRead)
                    .filter(Optional::isPresent)
                    // .peek(q -> LOG.debug("handling {{c}}post read{{X}} of %s for %s", source, vid))
                    .map(Optional::get)
                    .map(q -> q.postRead(vid, current))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append)
                    .filter(q -> !q.isNoObj()) : Optional.empty();
        }

        public static Optional<Obj> processQlessWrite(final Lst qs,final fURI vid, final Obj obj) {
            return qs.<Q>elements()
                    .map(Q::onWrite)
                    .filter(Optional::isPresent)
                    // .peek(q -> LOG.debug("handling {{g}}qless write{{X}} of %s for %s", source, vid))
                    .map(Optional::get)
                    .map(q -> q.qlessWrite(vid, obj))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append)
                    .filter(q -> !q.isNoObj());
        }

        public static Optional<Obj> processPostWrite(final Lst qs, final fURI vid, final Obj obj) {
            return vid.hasQ() && !qs.isEmpty() ? qs.<Q>elements()
                    .filter(q -> vid.hasQ(q.pattern().toString()))
                    .map(Q::onWrite)
                    .filter(Optional::isPresent)
                    //.peek(q -> LOG.trace("handling {{b}}post write{{X}} of %s for %s %s", source, vid, obj))
                    .map(Optional::get)
                    .map(q -> q.postWrite(vid, obj, obj))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append)
                    .filter(q -> !q.isNoObj()) : Optional.empty();
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

            public Builder qlessWrite(final BiFunction<fURI, Obj, Obj> qlessWrite) {
                this.jvm.put(QLESS_WRITE, qlessWrite);
                return this;
            }

            public Q create() {
                return BaseQ.create(this.tid, this.pattern,
                        (Function<fURI, Obj>) this.jvm.get(PRE_READ),
                        (BiFunction<fURI, Obj, Obj>) this.jvm.get(POST_READ),
                        (BiFunction<fURI, Obj, Obj>) this.jvm.get(PRE_WRITE),
                        (TriFunction<fURI, Obj, Obj, Obj>) this.jvm.get(POST_WRITE),
                        (BiFunction<fURI, Obj, Obj>) this.jvm.get(QLESS_WRITE));
            }
        }
    }

}
