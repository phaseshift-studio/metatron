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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.*;
import java.util.stream.Collectors;

public class MInstSet extends MSpace implements InstSet {

    protected static final fURI ANY_TID = fURI.of("#");
    protected static final String[] EMPTY_STRING_ARRAY = new String[0];

    /// /////////////////////////////////////////////////////////////////////////////////////////

    protected final Map<fURI, Map<fURI, Set<Inst>>> INST_TABLE = new LinkedHashMap<>();
    protected final Map<fURI, Type> TYPE_TABLE = new LinkedHashMap<>();
    protected final Map<fURI, Inst> REWRITE_TABLE = new LinkedHashMap<>();

    @Override
    public Map<fURI, Map<fURI, Set<Inst>>> value() {
        return INST_TABLE;
    }


    @Override
    public Obj read(final fURI vid) {
        final fURI bigvid = vid.big();
        final Obj result = ObjUtil.oneNoneOrAll(INST_TABLE.entrySet()
                .stream()
                .filter(kv -> kv.getKey().matches(bigvid.basePath()))
                .flatMap(kv -> kv.getValue().entrySet().stream())
                .filter(kv2 -> !bigvid.hasDom() || kv2.getKey().bimatches(bigvid.dom()))
                .map(Map.Entry::getValue)
                .flatMap(Set::stream)
                .filter(i -> !bigvid.hasRng() || i.rng().tid().bimatches(bigvid.rng()))
                .map(i -> (Obj) i));
        return result.isNoObj() ?
                ObjUtil.oneNoneOrAll((Iterator) TYPE_TABLE.entrySet().stream().filter(kv -> kv.getKey().matches(bigvid)).map(Map.Entry::getValue).iterator()) :
                result;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isInst()) {
            final Inst inst = obj.as();
            if (inst.dom().isCode()) {
                REWRITE_TABLE.put(inst.tid(), inst);
            } else {
                Router.global().registerRewrite(fURI.of(vid.name()), vid);
                INST_TABLE
                        .computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(inst.tid().dom(), k -> new LinkedHashSet<>())
                        .add(inst);
            }
        } else if (obj.isType()) {
            TYPE_TABLE.put(vid, obj.as());
        } else {
            throw MTronException.of("inst set %s can only store insts, types, and rewrites: {{r}}!{{/r}} %s", this.simpeToString(), obj);
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
    public Set<Type> types() {
        return new HashSet<>(this.TYPE_TABLE.values());
    }

    @Override
    public Set<Inst> rewrites() {
        return new HashSet<>(this.REWRITE_TABLE.values());
    }

    @Override
    public Set<Inst> insts() {
        return this.INST_TABLE.values().stream().flatMap(s -> s.values().stream()).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
