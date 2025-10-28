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

import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MLst;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_SPACE_TID;

public class Qs extends MLst {

    public static final fURI QS_TID = MTRON_SPACE_TID.extend("qs");

    final List<Q> qs = new ArrayList<>();

    public Qs(final fURI spacevid) {
        super(new ArrayList<>(), QS_TID, spacevid.extend("q"));
    }

    public Qs register(final Q q) {
        this.qs.add(q);
        return this;
    }

    public Qs clear() {
        this.qs.clear();
        return this;
    }

    public Optional<Obj> processPreWrite(final fURI source, final fURI vid, final Obj obj) {
        return this.qs.stream()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onWrite)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(q -> q.preWrite(source, vid, obj))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processPreRead(final fURI source, final fURI vid) {
        return this.qs.stream()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onRead)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(q -> q.preRead(source, vid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processPostRead(final fURI source, final fURI vid, final Obj current) {
        return this.qs.stream()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onRead)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(q -> q.postRead(source, vid, current))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processQlessWrite(final fURI source, final fURI vid, final Obj obj) {
        return this.qs.stream()
                .map(Q::onWrite)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(q -> q.qlessWrite(source, vid, obj))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

    public Optional<Obj> processPostWrite(final fURI source, final fURI vid, final Obj obj) {
        return this.qs.stream()
                .filter(q -> vid.hasQuery(q.jvm().toString()))
                .map(Q::onWrite)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(q -> q.postWrite(source, vid, obj, obj))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(Obj::append);
    }

}
