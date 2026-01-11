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

import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

public interface Q extends Rec {

    fURI Q_TID = f("/sys/type/space/q");
    fURI ON_WRITE_TID = Q_TID.extend("on_write");
    fURI ON_READ_TID = Q_TID.extend("on_read");

    fURI ON_WRITE = f("on_write");
    fURI PRE_WRITE = f("pre_write");
    fURI POST_WRITE = f("post_write");
    fURI QLESS_WRITE = f("qless_write");
    fURI ON_READ = f("on_read");
    fURI PRE_READ = f("pre_read");
    fURI POST_READ = f("post_read");
    Type Q_TYPE = T(f("/sys/space/q"));/*, null, instC(mtronInstSet.INST_TID.dom(ALL.maybe()).rng(f("/sys/space/q")),
            lst(T(REC_TID, isa_(rec(uri(PATTERN), T(URI_TID),
                    uri(ON_WRITE), rec(uri(PRE_WRITE), T(INST_TID).c(cInt::maybe), uri(POST_WRITE), T(INST_TID).c(cInt::maybe), uri(QLESS_WRITE), T(INST_TID).c(cInt::maybe)),
                    uri(ON_READ), rec(uri(PRE_READ), T(INST_TID).c(cInt::maybe), uri(POST_READ).c(cInt::maybe)))))), (lhs, inst) -> {
                return lhs;
            }));*/


    fURI pattern();

    Optional<OnWrite> onWrite();

    Optional<OnRead> onRead();

    interface OnWrite extends Rec {
        default Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }

        default Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj oldObj, final Obj newObj) {
            return Optional.empty();
        }

        default Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
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
        default Rec put(final Obj key, final Obj value) {
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
        default Optional<Obj> preRead(final fURI source, final fURI vid) {
            return Optional.empty();
        }

        default Optional<Obj> postRead(final fURI source, final fURI vid, final Obj obj) {
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
        default Rec put(final Obj key, final Obj value) {
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

        public static Optional<Obj> processPreWrite(final Lst qs, final fURI source, final fURI vid, final Obj obj) {
            return vid.hasQuery() && !qs.isEmpty() ? qs.<Q>elements()
                    .filter(q -> vid.hasQuery(q.pattern()))
                    .map(Q::onWrite)
                    .filter(Optional::isPresent)
                    // .peek(q -> LOG.info("handling {{y}}pre write{{X}} of %s for %s %s", source, vid, obj))
                    .map(Optional::get)
                    .map(q -> q.preWrite(source, vid, obj))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append) : Optional.empty();
        }
        
        public static Optional<Obj> processPreRead(final Lst qs, final fURI source, final fURI vid) {
            return vid.hasQuery() && !qs.isEmpty() ?
                    qs.<Q>elements()
                            .filter(q -> vid.hasQuery(q.pattern()))
                            .map(Q::onRead)
                            .filter(Optional::isPresent)
                            //.peek(q -> LOG.debug("handling {{m}}pre read{{X}} of %s for %s", source, vid))
                            .map(Optional::get)
                            .map(q -> q.preRead(source, vid))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .reduce(Obj::append) :
                    Optional.empty();
        }

        public static Optional<Obj> processPostRead(final Lst qs, final fURI source, final fURI vid, final Obj current) {
            return vid.hasQuery() && !qs.isEmpty() ? qs.<Q>elements()
                    .filter(q -> vid.hasQuery(q.pattern()))
                    .map(Q::onRead)
                    .filter(Optional::isPresent)
                    // .peek(q -> LOG.debug("handling {{c}}post read{{X}} of %s for %s", source, vid))
                    .map(Optional::get)
                    .map(q -> q.postRead(source, vid, current))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append)
                    .filter(q -> !q.isNoObj()) : Optional.empty();
        }

        public static Optional<Obj> processQlessWrite(final Lst qs, final fURI source, final fURI vid, final Obj obj) {
            return qs.<Q>elements()
                    .map(Q::onWrite)
                    .filter(Optional::isPresent)
                    // .peek(q -> LOG.debug("handling {{g}}qless write{{X}} of %s for %s", source, vid))
                    .map(Optional::get)
                    .map(q -> q.qlessWrite(source, vid, obj))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append)
                    .filter(q -> !q.isNoObj());
        }

        public static Optional<Obj> processPostWrite(final Lst qs, final fURI source, final fURI vid, final Obj obj) {
            return vid.hasQuery() && !qs.isEmpty() ? qs.<Q>elements()
                    .filter(q -> vid.hasQuery(q.pattern()))
                    .map(Q::onWrite)
                    .filter(Optional::isPresent)
                    //.peek(q -> LOG.trace("handling {{b}}post write{{X}} of %s for %s %s", source, vid, obj))
                    .map(Optional::get)
                    .map(q -> q.postWrite(source, vid, obj, obj))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .reduce(Obj::append)
                    .filter(q -> !q.isNoObj()) : Optional.empty();
        }

        private Helper() {
            // do nothing
        }


    }

}
