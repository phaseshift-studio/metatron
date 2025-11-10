/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MLst;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.ArrayList;
import java.util.Optional;

public class Qs extends MLst {

    public static final fURI QS_TID = sysInstSet.SPACE_TID.extend("q");
    private final GraphittyLogger LOG;

    public Qs() {
        super(new ArrayList<>(), QS_TID, fURI.NULL);
        LOG = Graphitty.log(this);
    }

    public Qs register(final Q q) {
        LOG.debug("registered q %s", q);
        this.lstValue().add(q);
        return this;
    }

    public Qs clear() {
        this.lstValue().clear();
        return this;
    }

    @Override
    public Qs clone() {
        return this;
    }

    public Optional<Obj> processPreWrite(final fURI source, final fURI vid, final Obj obj) {
        return this.<Q>elements()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onWrite)
                .filter(Optional::isPresent)
                .peek(q -> Router.global().logger().trace("handling {{y}}pre write{{X}} of %s for %s %s", source, vid, obj))
                .map(Optional::get)
                .map(q -> q.preWrite(source, vid, obj))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processPreRead(final fURI source, final fURI vid) {
        return this.<Q>elements()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onRead)
                .filter(Optional::isPresent)
                .peek(q -> Router.global().logger().trace("handling {{m}}pre read{{X}} of %s for %s", source, vid))
                .map(Optional::get)
                .map(q -> q.preRead(source, vid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processPostRead(final fURI source, final fURI vid, final Obj current) {
        return this.<Q>elements()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onRead)
                .filter(Optional::isPresent)
                .peek(q -> Router.global().logger().trace("handling {{c}}post read{{X}} of %s for %s", source, vid))
                .map(Optional::get)
                .map(q -> q.postRead(source, vid, current))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processQlessWrite(final fURI source, final fURI vid, final Obj obj) {
        return this.<Q>elements()
                .map(Q::onWrite)
                .filter(Optional::isPresent)
                .peek(q -> LOG.trace("handling {{g}}qless write{{X}} of %s for %s", source, vid))
                .map(Optional::get)
                .map(q -> q.qlessWrite(source, vid, obj))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processPostWrite(final fURI source, final fURI vid, final Obj obj) {
        return this.<Q>elements()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onWrite)
                .filter(Optional::isPresent)
                .peek(q -> Router.global().logger().trace("handling {{b}}post write{{X}} of %s for %s %s", source, vid, obj))
                .map(Optional::get)
                .map(q -> q.postWrite(source, vid, obj, obj))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

}
