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

package studio.phaseshift.metatron.lang.db.kv;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.mutableMap;


public class kvSpace extends MSpace<Map<fURI, Obj>> {

    public static final fURI KV_TID = f("/kv/space/kv");
    public static final Type KV_TYPE = T(KV_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(KV_TID), lst(isa_(rec(uri(Tokens.PATTERN), T(URI_TID)/*, uri(Tokens.Q).c(cInt::maybe), T(LST_TID.maybe())*/)).tryToInst()), (lhs, inst) -> {
        // final fURI pattern = inst.arg(0).<Rec>as().at(Tokens.PATTERN).uriValue();
        final Space space = new kvSpace(inst.arg(0).<Rec>as().jvm(), inst.arg(0).vid());
        Router.global().addSpace(space);
        return space;
    }));


    public kvSpace(final Map<Obj, Obj> config, final fURI vid) {
        super(new ConcurrentHashMap<>(), config, config.get(uri(Tokens.PATTERN)).uriValue(), KV_TID, vid);
    }


    public kvSpace(final fURI pattern, final fURI vid) {
        super(new ConcurrentHashMap<>(), mutableMap(uri(Tokens.PATTERN), uri(pattern)), pattern, KV_TID, vid);
    }

    public static kvSpace of(final fURI pattern, final fURI vid) {
        return new kvSpace(pattern, vid);
    }

    @Override
    public void close() {
        this.sjvm().values().stream().filter(o -> o != Router.global()).filter(o -> o != this).forEach(Common::close);
        Router.global().removeSpace(this.vid());
        super.close();
    }

    @Override
    public Obj read(final fURI vid) {
        return Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
            //return obj;
            return Q.Helper.processPostWrite(this.qs(), vid, vid, obj).orElse(Q.Helper.processQlessWrite(this.qs(), vid, vid, obj).orElse(obj));
        });
    }

    @Override
    public Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader() {
        return (pattern) -> {
            if (pattern.equals(fURI.ALL))
                return this.sjvm().entrySet().stream().map(kv -> Tuple.Pair.with(kv.getKey(), kv.getValue())).iterator();
            else {
                if (pattern.hasPattern()) {
                    final List<Tuple.Pair<fURI, Obj>> partial = new ArrayList<>();
                    this.sjvm().forEach((key, value) -> {
                        if (key.matches(pattern.asNode()))
                            partial.add(Tuple.Pair.with(key, value));
                        if (value.isPoly())
                            partial.addAll(Space.Helper.unrollPoly(key, value.as(), pattern.asNode()));
                    });
                    return partial.iterator();
                } else {
                    final Obj value = this.sjvm().get(pattern);
                    return null == value ? IteratorUtil.of() : IteratorUtil.of(Tuple.Pair.with(pattern, value));
                }
            }
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.get0(), obj));
            } else {
                final Obj current = this.sjvm().get(pattern);
                if (obj.isNoObj()) {
                    this.sjvm().remove(pattern);
                    Common.close(current);
                } else
                    this.sjvm().put(pattern, (null != current && (obj.isObjs() || current.isObjs())) ? current.append(obj) : obj);
            }
            return obj;
        };
    }
}
