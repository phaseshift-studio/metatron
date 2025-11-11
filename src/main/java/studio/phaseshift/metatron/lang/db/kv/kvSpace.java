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

package studio.phaseshift.metatron.lang.db.kv;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.util.Common;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;


public class kvSpace extends MSpace<Map<fURI, Obj>> {

    public static final fURI KV_TID = f("/kv/space/kv");
    public static final Type KV_TYPE = T(KV_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(KV_TID), lst(T(REC_TID, isa_(rec(uri(PATTERN), T(URI_TID))))), (lhs, inst) -> {
        final fURI pattern = inst.arg(0).<Rec>as().at(PATTERN).uriValue();
        final Space space = new kvSpace(pattern, inst.arg(0).vid());
        //space.qs().add(new PubSubQ(space));
        Router.global().addSpace(space);
        return space;
    }));

    public kvSpace(final fURI pattern, final fURI vid) {
        super(new HashMap<>(), Map.of(uri(PATTERN), uri(pattern)), pattern, KV_TID, vid);
    }

    public static kvSpace of(final fURI pattern, final fURI vid) {
        return new kvSpace(pattern, vid);
    }

    @Override
    public void close() {
        this.sjvm().values().forEach(Common::close);
        super.close();
    }

    @Override
    public Obj read(final fURI vid) {
        return this.qs().processPreRead(vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return this.qs().processPostRead(vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.qs().processPreWrite(vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
            //return obj;
            return this.qs().processPostWrite(vid, vid, obj).orElse(this.qs().processQlessWrite(vid, vid, obj).orElse(obj));
        });
    }

    @Override
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return (pattern) -> {
            if (pattern.equals(fURI.ALL))
                return this.sjvm();
            else {
                if (pattern.hasPattern()) {
                    final Map<fURI, Obj> partial = new LinkedHashMap<>();
                    this.sjvm().forEach((key, value) -> {
                        if (key.matches(pattern.asNode()))
                            partial.put(key, value);
                        if (value.isPoly())
                            Space.Helper.unrollPoly(partial, key, value.as(), pattern.asNode());
                    });
                    return partial;
                } else {
                    final Obj value = this.sjvm().get(pattern);
                    return null == value ? Map.of() : Map.of(pattern, value);
                }
            }
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                this.directReader().apply(pattern).forEach((key, value) -> this.write(key, obj));
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
