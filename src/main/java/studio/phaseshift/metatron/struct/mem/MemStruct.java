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

package studio.phaseshift.metatron.struct.mem;

import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.struct.Struct;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.*;

import static studio.phaseshift.metatron.lang.obj.BObj.*;

public class MemStruct extends SObj.Obj implements Struct {
    private static final Logger LOG = LoggerFactory.getLogger(MemStruct.class);
    public static final fURI MEMSTRUCT_TID = fURI.of("struct:/mtron/mem");
    private final fURI pattern;

    final Map<fURI, Obj> store = new HashMap<>();

    public MemStruct(final fURI pattern, final fURI vid) {
        super(Map.of(), MEMSTRUCT_TID, vid);
        this.pattern = pattern;
        // this.config = config;
        LOG.info(Graphitty.string("%s loaded at %s !g[!yaddr!g=>!!%s!g]!!\n".formatted(tid().toUri(true), SObj.Uri.of(this.vid), SObj.Uri.of(this.pattern))));
    }

    @Override
    public String toString() {
        return this.toString(Palette.GLOBAL);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.vid, this.pattern, this.tid());
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MemStruct && this.hashCode() == other.hashCode();
    }

    @Override
    public Map<Obj, Obj> value() {
        return Map.of();
    }

    @Override
    public fURI pattern() {
        return this.pattern;
    }


    @Override
    public Obj read(final fURI addr) {
        if (addr.isBranch()) {
            final List<BObj.Obj> map = new ArrayList<>();
            if (addr.hasPattern()) {
                this.store.entrySet()
                        .stream()
                        .filter(kv -> kv.getKey().matches(addr))
                        .forEach(kv -> map.add(SObj.Rel.of(SObj.Uri.of(kv.getKey()), kv.getValue())));
                return new SObj.Objs(map, OBJS_URI, fURI.NONE);
            } else {
                return SObj.Objs.of(SObj.Rel.of(SObj.Uri.of(addr), this.store.getOrDefault(addr, NoObj.of())));
            }
        } else {
            BObj.Obj result = addr.hasPattern() ?
                    ObjUtil.oneNoneOrAll(
                            this.store.entrySet()
                                    .stream()
                                    .filter(kv -> kv.getKey().matches(addr))
                                    .map(Map.Entry::getValue)
                                    .flatMap(o -> IteratorUtil.stream(o.iterator()))
                                    .toList()) :
                    this.store.getOrDefault(addr, NoObj.of());
            if (result.isNoObj()) {
                final Optional<Pair<fURI, Poly>> pair = this.locateBasePoly(addr.retract(), null);
                if (pair.isPresent()) {
            /*LOG_WRITE(TRACE, this, L("base poly found at {}: {}\n",
                                     pair->first.toString(),
                                     pair->second->toString())                  );*/
                    final fURI furiSubpath = addr.removeSubpath(pair.get().getValue0().asBranch());
                    final Poly poly = pair.get().getValue1();
                    final BObj.Obj readObj = poly.get(furiSubpath);
                    result = SObj.Objs.of(result).append(readObj);
                } /*(else if (result.isNoObj()) {
                    result. (NoObj.of());
                }*/
            }
            return result;
        }
    }

    @Override
    public Obj write(final fURI addr, Obj obj) {
        final Obj current = this.store.get(addr);
        if (null == current) {
            this.store.put(addr, obj);
            return obj;
        } else {
            final Obj next = current.apply(obj);
            this.store.put(addr, next);
            return next;
        }
    }

    @Override
    public void append(final fURI addr, final Obj... obj) {
        Obj poly = this.store.get(addr);
        if (null == poly || poly.isMono())
            this.store.put(addr, new SObj.Objs(Arrays.asList(obj), OBJS_URI, null));
        else {
            Poly ppoly = (Poly) poly;
            ppoly.append(obj);
        }
    }

    @Override
    public Obj get(int index) {
        return NoObj.of();
    }

    @Override
    public Obj get(final fURI key) {
        return this.read(key);
    }


    @Override
    public long length() {
        return 0;
    }

    @Override
    public Iterator<Obj> iterator() {
        return null;
    }
}
