/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Objs;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.OBJS_TID;

public class MObjs extends MObj implements Objs {

    private static final GraphittyLogger LOG = Graphitty.log(MObjs.class);

    public MObjs(final Iterable<Obj> value, final fURI tid, final fURI vid) {
        super(value, value.iterator().hasNext() ? value.iterator().next().tid().coefficient("*") : fURI.of("/mtron/int[*]"), vid);
        if(value instanceof Obj)
            LOG.error("objs can not directly nest: %s",value);
    }

    public MObjs(final Iterable<Obj> value) {
        this(value, OBJS_TID, fURI.NULL);
    }

    @Override
    public fURI tid() {
        Set<fURI> types = IteratorUtil.stream(this.value()).map(Obj::tid).map(fURI::basePath).collect(Collectors.toSet());
        final long count = IteratorUtil.count(this.value());
        if(types.isEmpty() || 0 == count) return super.tid.coefficient("0");
        if(types.size() == 1) return types.iterator().next().coefficient(Long.toString(count));
        final fURI temp = types.stream().reduce(fURI::commonRoot).get();
        return temp.coefficient(""+count);
    }

    @Override
    public boolean equals(final Object other) {
        return this.toString().equals(other.toString()); // TODO: VERY BAD -- something is weird about the tid string encoding (hidden characters??)
        /*this.getClass().isAssignableFrom(other.getClass()) &&
                Objects.equals(this.tid, ((Obj) other).tid()) &&
                Objects.equals(this.vid, ((Obj) other).vid()) &&
                Objects.equals(this.value, ((Obj) other).value());*/
    }


    @Override
    public Objs clone(final Object value, final fURI tid, final fURI vid) {
        return new MObjs((Iterable<Obj>) value, tid, vid);
    }

    @Override
    public Objs append(final Obj obj){
       return (Objs) Objs.super.append(obj);//.tid(obj.tid().coefficient("*"));
    }

    @Override
    public Iterable<Obj> value() {
        return (Iterable<Obj>) this.value;
    }

    public static Objs of(final Iterable<Obj> iterable) {
        return new MObjs(iterable);
    }


}