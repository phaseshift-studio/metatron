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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Objs;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;

public class MObjs implements Objs {

    public static final int BULK_TRIGGER = 10000;
    
    private fURI vid;
    private cInt count = null;
    private List<Obj> jvm;

    public MObjs(final List<Obj> jvm) {
        this(jvm, null);
    }

    public MObjs(final List<Obj> jvm, final fURI vid) {
        this.jvm = flatten(jvm);
        this.vid = vid;
    }

    @Override
    public boolean isNoObj() {
        return this.jvm.isEmpty();
    }

    private MObjs computeC() {
        if (null == count) {
            this.count = cInt.ZERO();
            for (final Obj o : this.jvm) {
                this.count = this.count.plus(o.c());
            }
        }
        return this;
    }

    private List<Obj> flatten(final List<Obj> list) {
        final List<Obj> flat = new ArrayList<>();
        count = cInt.ZERO();
        for (final Obj o : list) {
            if (!o.isNoObj()) {
                count = count.plus(o.c());
                if (o.isObjs()) {
                    flat.addAll(flatten((List<Obj>) o.objsValue()));
                } else {
                    flat.add(o);
                }
            }
        }
        return flat;
    }

   /* public Obj done() {
        return this.attemptBulk(true).tryToShrink();
    }*/

    private MObjs attemptBulk(final boolean force) {
        if (force || this.jvm.size() > BULK_TRIGGER) {
            final Map<Obj, cInt> map = new LinkedHashMap<>();
            this.jvm.forEach(o -> map.merge(o.c(C::one), o.c(), cInt::plus));
            this.jvm = new ArrayList<>();
            map.forEach((k, v) -> this.jvm.add(k.c(v)));
            assert this.jvm.size() == map.size();
            map.clear();
        }
        return this;

    }

    private Obj tryToShrink() {
        if (this.jvm.isEmpty())
            return noobj();
        if (1 == this.jvm.size())
            return this.jvm.getFirst();
        return this;
    }

    public static Objs empty() {
        return new MObjs(new ArrayList<>(), null); // a noobj that can be appended
    }

    public static Obj objs(final Obj... objs) {
        return objs(new ArrayList<Obj>(List.of(objs)));
    }

    public static Obj objs(final Iterable<Obj> objs) {
        final Iterator<Obj> itty = objs.iterator();
        if(!itty.hasNext())
            return noobj();
        final Obj o = itty.next();
        if(!itty.hasNext())
            return o;
        else {
            final List<Obj> temp = new ArrayList<>();
            temp.add(o);
            IteratorUtil.fill(itty, temp);
            return new MObjs(temp, null).attemptBulk(true).tryToShrink();
        }
    }

    public static Obj objs(final List<Obj> objs) {
        if (objs.isEmpty()) return noobj();
        if (objs.size() == 1) return objs.getFirst();
        return new MObjs(objs, null).attemptBulk(true).tryToShrink();
    }

    public static Obj objs(final Stream<Obj> objs) {
        return objs(new ArrayList<Obj>(objs.toList()));
    }

    @Override
    public Obj resolve(final Obj obj) {
        return this.clone(new ArrayList<>(this.jvm.stream().map(o -> o.resolve(obj)).toList()), null, this.vid);
    }

    @Override
    public Obj append(final Obj obj) {
        if (obj.isNoObj()) return this;

        this.count = this.computeC().count.plus(obj.c());
        if (obj instanceof Objs)
            IteratorUtil.fill(((Iterable<Obj>) obj.jvm()).iterator(), this.jvm);
        else {
            this.jvm.add(obj);
        }
        return tryToShrink();
    }

    @Override
    public cInt uniqueC() {
        /*Map<Obj, cInt> map = new LinkedHashMap<>();
        this.jvm.stream().map(x -> Tuple.Pair.with(x.c(cInt.ONE()), x.c())).filter(x -> !x.get1().isZero()).forEach(x -> map.compute(x.get0(), (k, v) -> v == null ? x.get1() : v.plus(x.get1())));
        this.jvm.clear();
        map.forEach((k, v) -> this.jvm.add(k.c(v)));*/
        return cInt.of(this.jvm.size());
    }

    @Override
    public Iterable<Obj> jvm() {
        return this.jvm;
    }

    @Override
    public cInt c() {
        return this.computeC().count;
        //   return this.jvm.stream().map(Obj::c).reduce(cInt.ZERO(), cInt::plus);
    }

    @Override
    public Obj c(final Function<cInt, cInt> func) {
        this.jvm = new ArrayList<Obj>(this.jvm.stream().map(obj -> obj.c(func)).toList());
        return tryToShrink();
    }

    @Override
    public Obj take() {
        final Obj temp = this.jvm.isEmpty() ? null : this.jvm.removeFirst();
        this.count = temp == null ? cInt.ZERO() : this.computeC().count.minus(temp.c());
        return temp;
    }

    @Override
    public Tuple.Pair<Obj, Obj> take(final cInt c) {
        if (c.isZero())
            return Tuple.Pair.with(noobj(), this);
        if (c.isMaybeSome() || Objects.equals(c.max(), this.c().max())) {
            this.count = cInt.ZERO();
            return Tuple.Pair.with(this, noobj());
        }
        final List<Obj> retrieved = new ArrayList<>();
        final List<Obj> remaining = new ArrayList<>();
        cInt total = cInt.ZERO();
        this.count = cInt.ZERO();
        for (Obj entry : this.jvm) {
            final cInt toTake = c.minus(total);
            final cInt entryC = entry.c();
            if (toTake.gt(c.zero())) {
                if (entryC.lte(toTake)) {
                    retrieved.add(entry);
                    total = total.plus(entryC);
                } else {
                    final cInt t = entryC.minus(toTake);
                    this.count = this.count.plus(t);
                    remaining.add(entry.c(t));
                    retrieved.add(entry.c(toTake));
                    total = total.plus(toTake);
                }
            } else {
                this.count = this.count.plus(entry.c());
                remaining.add(entry);
            }
        }
        this.jvm = remaining;
        return Tuple.Pair.with(objs(retrieved), objs(remaining));
    }

    @Override
    public Stream<Obj> stream() {
        return this.jvm.stream();
    }

    @Override
    public Iterator<Obj> iterator() {
        return this.jvm.iterator();
    }

    @Override
    public fURI tid() {
        try {
            return this.jvm
                    .stream()
                    .map(Obj::tid)
                    .reduce(fURI::plus)
                    .orElse(fURI.NOOBJ);
        } catch (final Exception e) {
            return this.jvm
                    .stream()
                    .map(Obj::tid).reduce(fURI::commonRoot)
                    .orElse(fURI.NOOBJ);
        }
    }

    @Override
    public Obj vid(final fURI vid) {
        final MObjs temp = new MObjs(new ArrayList<Obj>(this.jvm), vid);
        return temp.tryToShrink();
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Obj clone(final Object jvm, final fURI tid, final fURI vid) {
        return objs(this.jvm).vid(vid);
    }

    @Override
    public String toString() {
        return Helper.objToString(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.jvm.size(), this.vid);
    }

    @Override
    public boolean equals(final Object other) {
        final Obj a;
        final Obj b;
        a = this.attemptBulk(true).tryToShrink();
        if (other instanceof MObjs) {
            b = (((MObjs) other).attemptBulk(true).tryToShrink()); // TODO: might require attemptBulk(true)
        } else {
            b = (Obj) other;
        }
        if (a instanceof MObjs && b instanceof MObjs) {
            return new HashSet<>(a.jvm()).equals(new HashSet<>(b.jvm()));
        }/* else if (a instanceof MObjs) {
            return b.equals(((MObjs) a).attemptBulk(true).tryToShrink());
        } else if (b instanceof MObjs) {
            return a.equals(((MObjs) b).attemptBulk(true).tryToShrink());
        }*/ else {
            return b.equals(a);
        }
    }

    @Override
    public Objs clone() {
        return (Objs) this.clone(this.jvm, this.tid(), this.vid);
    }

    @Override
    public Objs self(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = (List<Obj>) jvm;
        this.vid = vid;
        return this;
    }
}
