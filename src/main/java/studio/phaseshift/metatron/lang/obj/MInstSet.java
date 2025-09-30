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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;

public class MInstSet extends MSpace implements InstSet {


    protected static final Map<fURI, Map<fURI, Set<Inst>>> SYMBOL_TABLE = new LinkedHashMap<>();
    protected final Map<fURI, Obj> OBJ_TABLE = new LinkedHashMap<>();

    @Override
    public Map<fURI, Map<fURI, Set<Inst>>> value() {
        return SYMBOL_TABLE;
    }


    @Override
    public Obj read(final fURI vid) {
        final fURI bigvid = vid.big();
        final Obj result = ObjUtil.oneNoneOrAll(SYMBOL_TABLE.entrySet()
                .stream()
                .filter(kv -> kv.getKey().matches(bigvid.basePath()))
                .flatMap(kv -> kv.getValue().entrySet().stream())
                .filter(kv2 -> !bigvid.hasDom() || kv2.getKey().bimatches(bigvid.dom()))
                .map(Map.Entry::getValue)
                .flatMap(Set::stream)
                .filter(i -> !bigvid.hasRng() || i.rng().tid().bimatches(bigvid.rng()))
                .map(i -> (Obj) i));
        return result.isNoObj() ?
                ObjUtil.oneNoneOrAll(OBJ_TABLE.entrySet().stream().filter(kv -> kv.getKey().matches(bigvid)).map(Map.Entry::getValue).iterator()) :
                result;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isInst()) {
            Router.global().registerRewrite(fURI.of(vid.name()), vid);
            final Inst inst = obj.as();
            SYMBOL_TABLE
                    .computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(inst.tid().dom(), k -> new LinkedHashSet<>()).add(inst);
        } else {
            OBJ_TABLE.put(vid, obj);
        }
        return obj;
    }

    public MInstSet(final fURI tid, final fURI vid) {
        super(tid.extend("#"), tid, vid);
    }

    @Override
    public MInstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Objs types() {
        return objs(this.OBJ_TABLE.values());
    }
}
