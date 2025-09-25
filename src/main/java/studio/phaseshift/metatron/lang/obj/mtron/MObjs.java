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
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Objs;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.OBJS_TID;

public class MObjs extends MObj implements Objs {

    private static final GraphittyLogger LOG = Graphitty.log(MObjs.class);

    private static fURI computeTID(final Iterable<Obj> value) {
        Set<fURI> types = IteratorUtil.stream(value).map(Obj::tid).map(fURI::basePath).collect(Collectors.toSet());
        // TODO: make efficient
        final long count = IteratorUtil.stream(value).map(Obj::tid).map(f -> (f.coefficientValue().max() != null) ? f.coefficientValue().max() : 1).reduce(0L, Long::sum);
        //final long count = IteratorUtil.count(this.value());
        if (types.isEmpty() || 0 == count) return fURI.NONE.zero();
        if (types.size() == 1) return types.iterator().next().coefficient(Long.toString(count));
        final fURI temp = types.stream().reduce(fURI::commonRoot).get();
        return temp.coefficient("" + count);
    }


    public static Objs objs(final Iterable<Obj> os) {
        return MObjs.of(os);
    }


    public MObjs(final Iterable<Obj> value, final fURI tid, final fURI vid) {
        super(value, computeTID(value), vid);
        if (value instanceof Obj)
            LOG.error("objs can not directly nest: %s", value);
    }

    /*
       public MObjs(final Iterable<Obj> value, final fURI tid, final fURI vid) {
        super(value, tid,vid);
        if(value instanceof Obj)
            LOG.error("objs can not directly nest: %s",value);
        IteratorUtil.stream(value).map(v -> v.tid());
    }

     */

    public MObjs(final Iterable<Obj> value) {
        this(value, OBJS_TID, fURI.NULL);
    }

    @Override
    public fURI tid() {
        return computeTID(this.objsValue());
    }

    @Override
    public Objs tid(final fURI newtid) {
        return (Objs) super.tid(computeTID(this.objsValue()));
    }

    @Override
    public Type type() {
        return MType.of(this, this.tid());
    }

    @Override
    public boolean equals(final Object other) {
        return this.toString().equals(other.toString()); // TODO: VERY BAD -- something is weird about the tid string encoding (hidden characters??)
      /*  return other instanceof Obj && ((Obj) other).isObjs() &&
                Objects.equals(this.tid(), ((Obj) other).tid()) &&
                Objects.equals(this.vid, ((Obj) other).vid()) &&
                Objects.equals(this.value, ((Obj) other).value());*/
    }


    @Override
    public Objs clone(final Object value, final fURI tid, final fURI vid) {
        return new MObjs((Iterable<Obj>) value, tid, vid);
    }

    @Override
    public Objs append(final Obj obj) {
        return (Objs) Objs.super.append(obj);//.tid(obj.tid().coefficient("*"));
    }

    @Override
    public Iterable<Obj> value() {
        return (Iterable<Obj>) this.value;
    }

    public static Objs of(final Iterable<Obj> iterable) {
        return new MObjs(iterable);
    }

    public static Obj ofUsage(final Object object) {
        if (null == object)
            return NoObj.single();
        if (object instanceof Stream)
            return ofUsage(((Stream) object).toList()); // TODO: strange....
        if (object instanceof List)
            return ObjUtil.oneNoneOrAll((List) object);
        if (object instanceof Obj)
            return (Obj) object;
        throw MTronException.of("unknown object type: %s", object);

    }


}