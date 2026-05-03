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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Objs;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_STAR;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

public class MObjs implements Objs {

    public static final int BULK_TRIGGER = 10000;

    private fURI vid;
    private fURI tid;
    private cInt internalC = null;
    private List<Obj> jvm;

    protected MObjs(final List<Obj> jvm) {
        this(jvm, ALL_STAR, null);
    }

    protected MObjs(final List<Obj> jvm, final fURI tid, final fURI vid) {
        this.jvm = flatten(jvm);
        this.vid = vid;
        this.tid = tid;
        if (null != vid)
            Router.writeToSpace(vid, this);
    }

    @Override
    public boolean isNoObj() {
        return this.jvm.isEmpty();
    }

    private MObjs computeC() {
        if (null == internalC) {
            this.internalC = cInt.ZERO();
            for (final Obj o : this.jvm) {
                this.internalC = this.internalC.plus(o.c());
            }
        }
        return this;
    }

    private List<Obj> flatten(final List<Obj> list) {
        final List<Obj> flat = new ArrayList<>();
        internalC = cInt.ZERO();
        for (final Obj o : list) {
            if (!o.isNoObj()) {
                internalC = internalC.plus(o.c());
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
            this.jvm.forEach(o -> map.merge(o.c(studio.phaseshift.metatron.furi.C::one), o.c(), cInt::plus));
            this.jvm.clear();
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

    public static Objs objs0() {
        return new MObjs(new ArrayList<>(), ALL_STAR, null); // a noobj that can be appended
    }

    public static Obj objs(final Obj... objs) {
        return objs(new ArrayList<Obj>(List.of(objs)));
    }


    public static Obj objs(final Iterable<Obj> objs) {
        return objs(objs.iterator());
    }

    public static Obj objs(final Iterator<Obj> objs) {
        if (!objs.hasNext())
            return noobj();
        final Obj o = objs.next();
        if (!objs.hasNext())
            return o;
        else {
            final List<Obj> temp = new ArrayList<>();
            temp.add(o);
            IteratorUtil.fill(objs, temp);
            return new MObjs(temp, ALL_STAR, null).attemptBulk(true).tryToShrink();
        }
    }

    public static Obj objs(final List<Obj> objs) {
        return objs(objs, ALL_STAR, null);
    }

    public static Obj objs(final List<Obj> objs, final fURI tid, final fURI vid) {
        if (objs.isEmpty()) return noobj();
        if (objs.size() == 1) return objs.getFirst();
        final fURI bigTID = tid.big();
        final fURI newTID = objs.stream().map(Obj::tid).reduce(fURI::plus).orElse(NOOBJ_TID);
        if (!newTID.test(bigTID))
            throw MTronException.of("tid does not match objs tid: %s != %s", bigTID, newTID);
        return new MObjs(objs, bigTID, vid).attemptBulk(true).tryToShrink();
    }

    public static Obj objs(final Stream<Obj> objs) {
        return objs(objs.iterator());
    }

    @Override
    public Obj resolve(final Obj obj) {
        return this.clone(this.jvm.stream().map(o -> o.resolve(obj)), ALL_STAR, this.vid);
    }

    @Override
    public Obj append(final Obj obj) {
        if (obj.isNoObj()) return this;

        this.internalC = this.computeC().internalC.plus(obj.c());
        if (obj instanceof Objs) {
            if (this != obj)
                IteratorUtil.fill(((Iterable<Obj>) obj.jvm()).iterator(), this.jvm);
        } else {
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
        attemptBulk(true);
        return cInt.of(this.jvm.size());
    }

    @Override
    public Iterable<Obj> jvm() {
        return this.jvm;
    }

    @Override
    public cInt c() {
        return this.computeC().internalC;
        //   return this.jvm.stream().map(Obj::c).reduce(cInt.ZERO(), cInt::plus);
    }

    @Override
    public Obj c(final Function<cInt, cInt> func) {
        this.jvm = this.jvm.stream().map(obj -> obj.c(func)).collect(Collectors.toCollection(ArrayList::new));
        return tryToShrink();
    }

    @Override
    public Obj take() {
        final Obj temp = this.jvm.isEmpty() ? null : this.jvm.removeFirst();
        this.internalC = temp == null ? cInt.ZERO() : this.computeC().internalC.minus(temp.c());
        return temp;
    }

    @Override
    public Tuple.Pair<Obj, Obj> take(final cInt c) {
        boolean isNegative = c.lt(cInt.ZERO());
        final cInt abs = c.abs();
        if (abs.isZero())
            return Tuple.Pair.with(noobj(), this);
        if (abs.isMaybeSome() || Objects.equals(abs.max(), this.c().max())) {
            this.internalC = cInt.ZERO();
            return Tuple.Pair.with(this, noobj());
        }
        final List<Obj> retrieved = new ArrayList<>();
        final List<Obj> remaining = new ArrayList<>();
        cInt total = cInt.ZERO();
        this.internalC = cInt.ZERO();
        for (Obj entry : (isNegative ? this.jvm.reversed() : this.jvm)) {
            final cInt toTake = abs.minus(total);
            final cInt entryC = entry.c();
            if (toTake.gt(abs.zero())) {
                if (entryC.lte(toTake)) {
                    retrieved.add(entry);
                    total = total.plus(entryC);
                } else {
                    final cInt t = entryC.minus(toTake);
                    this.internalC = this.internalC.plus(t);
                    remaining.add(entry.c(t));
                    retrieved.add(entry.c(toTake));
                    total = total.plus(toTake);
                }
            } else {
                this.internalC = this.internalC.plus(entry.c());
                remaining.add(entry);
            }
        }
        this.jvm = isNegative ? remaining.reversed() : remaining;
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
       /* if (null == this.tid || ALL_STAR == this.tid) {
            List<Type> types = this.jvm.stream().map(o -> o.isType() ? o.asType() : o.type()).toList();
            final Type lcd = Type.Helper.findLCD(types);
            this.tid = lcd.vid();
        }*/ // TODO: messes up at reduction instructions
       try {
            return this.jvm
                    .stream()
                    .map(Obj::tid)
                    .reduce(fURI::plus)
                    .orElse(fURI.Singleton.NOOBJ);
        } catch (final Exception e) {
            return this.jvm
                    .stream()
                    .map(Obj::tid).reduce(fURI::plus)
                    .orElse(fURI.Singleton.NOOBJ);
        }
        //  return this.tid;
    }

    @Override
    public Obj vid(final fURI vid) {
        final MObjs temp = new MObjs(new ArrayList<Obj>(this.jvm), this.tid, vid);
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
        if (a instanceof Objs && b instanceof Objs) {
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
        return (Objs) this.clone(this.jvm, this.tid, this.vid);
    }

    @Override
    public Objs self(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = (List<Obj>) jvm;
        this.tid = tid;
        this.vid = vid;
        return this;
    }
}
