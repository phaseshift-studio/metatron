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

package studio.phaseshift.metatron.space.mem;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.obj.BObj.*;

public class MemSpace extends SObj.Obj implements Space {
    private static final GraphittyLogger LOG = Graphitty.log(MemSpace.class);
    public static final fURI MEMSTRUCT_TID = fURI.of("struct:/mtron/mem");
    private final fURI pattern;

    final Map<fURI, Obj> store = new HashMap<>();

    public MemSpace(final fURI pattern, final fURI vid) {
        super(Map.of(), MEMSTRUCT_TID, vid);
        this.pattern = pattern;
        // this.config = config;
        LOG.info("%s loaded at %s {{g}}[{{y}}addr{{g}}=>{{X}}%s{{g}}]{{X}}", tid().toUri(true), SObj.Uri.of(this.vid), SObj.Uri.of(this.pattern));
    }

    @Override
    public String toString() {
        return "memstruct";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.vid, this.pattern, this.tid());
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MemSpace && this.hashCode() == other.hashCode();
    }

    @Override
    public Map<Obj, Obj> value() {
        return Map.of();
    }

    @Override
    public fURI pattern() {
        return null == this.pattern ? fURI.of("#") : this.pattern;
    }


    @Override
    public Obj read(final fURI addr) {
        if (addr.isBranch()) {
            // pattern/branch
            if (addr.hasPattern()) {
                Graphitty.log(this).info("processing pattern %s", addr.toUri());
                return SObj.Objs.of(this.store.entrySet()
                        .stream()
                        .flatMap(kv -> kv.getValue().isRec() ? kv
                                .getValue()
                                .recValue()
                                .entrySet()
                                .stream()
                                .filter(kv2 -> !kv2.getValue().isNoObj())
                                .flatMap(kv2 -> Map.of(kv.getKey().extend(kv2.getKey().uriValue()), kv2.getValue()).entrySet().stream()) : Stream.of(kv))
                        .flatMap(kv -> Map.of(kv.getKey().toUri(), kv.getValue()).entrySet().stream())
                        .filter(kv -> {
                            final boolean check = kv.getKey().matches(addr.toUri()) || kv.getKey().matches(addr.retractPattern().asNode().toUri());
                            Graphitty.log(this).info("checking %s against %s at %s [%s]", addr.asNode(), kv.getValue(), kv.getKey(), check ? "{{g}}OK{{X}}" : "{{r}}X{{X}}");
                            return check;
                        })
                        .map(kv -> new SObj.Rel(Pair.with(kv.getKey(), kv.getValue()), REL_URI, fURI.NONE)).toList());
            } else {
                // resolved/branch
                Graphitty.log(this).info("searching %s", addr.extend("+").toUri());
                return this.read(addr.extend("+").asBranch());// new SObj.Objs(List.of(new SObj.Rel(Pair.with(SObj.Uri.of(addr), this.store.getOrDefault(addr, NoObj.of())), REL_URI, fURI.NONE)), OBJS_URI, fURI.NONE);
            }
        } else {
            Map<BObj.Uri, BObj.Obj> map = new LinkedHashMap<>();
            if (addr.hasPattern()) {
                this.store.entrySet()
                        .stream()
                        .filter(kv -> kv.getKey().matches(addr))
                        .forEach(kv ->
                                map.put(kv.getKey().toUri(), kv.getValue()));
            } else if (this.store.containsKey(addr))
                map.put(addr.toUri(), this.store.get(addr));
            if (map.isEmpty()) {
                final Optional<Pair<fURI, Poly>> pair = this.locateBasePoly(addr.retract(), null);
                if (pair.isPresent()) {
                    final Poly poly = pair.get().getValue1();
                    Graphitty.stdout().print("base poly found at %s: %s\n".formatted(pair.get().getValue0(), poly));
                    final fURI furiSubpath = addr.removeSubpath(pair.get().getValue0()).asNode();
                    Graphitty.stdout().print("searching base poly %s for %s\n".formatted(poly, furiSubpath.toUri()));
                    final BObj.Obj readObj = poly.get(furiSubpath);
                    Graphitty.stdout().print("located poly obj %s in %s\n".formatted(readObj, poly));
                    if (!readObj.isNoObj())
                        map.put(addr.retractPattern().toUri(), readObj);
                } /*(else if (result.isNoObj()) {
                    result. (NoObj.of());
                }*/
            }
            if (map.isEmpty())
                return NoObj.of();
            else {
                return SObj.Rec.of(map);
                //   new SObj.Rec((Map) map, REC_URI, fURI.NONE);
            }
        }

        /*

        SObj.Objs.of(map.entrySet().stream()
                        .flatMap(kv ->
                                IteratorUtil.asList(nodeBranchProcess(addr.retractPattern().asBranch(), new SObj.Rec((Map) map, REC_URI, fURI.NONE)))
                                        .stream()
                                        .map(z -> new SObj.Rel(Pair.with(((Pair<fURI, BObj.Obj>) z).getValue0().toUri(), ((Pair<fURI, BObj.Obj>) z).getValue1()), REL_URI, fURI.NONE))
                                        .toList()
                                        .stream()).toList());
         */
    }

    @Override
    public Obj write(final fURI addr, BObj.Obj obj) {
        this.resolveWrite(addr, addr.retractPattern(), obj, (resolvedAddr, resolvedObj) -> {
            if (resolvedObj.isNoObj())
                this.store.remove(resolvedAddr);
            else
                this.store.put(resolvedAddr, resolvedObj);
        });
        return obj;
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
        return Collections.emptyIterator();
    }
}
