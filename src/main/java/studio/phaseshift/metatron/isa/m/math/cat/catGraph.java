/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.CommonUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.OBJECT_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;


/**
 * The category's type graph — the directed inst graph (dom basePath → rng basePath), its reversible
 * core, and the memoized computes each morphism block serves lazily: position, contested, orbit,
 * family, the set-theoretic class, and the materialized block.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catGraph {

    /// //////////////////////////////////////////////////////////////
    // the graph — derived from every registered inst, memoized, invalidated on registration writes

    private static volatile List<Inst> CACHED_INSTS;
    private static final Map<String, Lst> POSITION_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Lst> CONTESTED_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Obj> ORBIT_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Lst> FAMILY_MEMO = new ConcurrentHashMap<>();

    /**
     * drop every derived map — call on any inst-registration write
     */
    public static void invalidate() {
        CACHED_INSTS = null;
        POSITION_MEMO.clear();
        CONTESTED_MEMO.clear();
        ORBIT_MEMO.clear();
        FAMILY_MEMO.clear();
    }

    /**
     * every registered instruction across every inst set
     */
    public static List<Inst> instructions() {
        final List<Inst> cached = CACHED_INSTS;
        if (null != cached)
            return cached;
        // INSTRUCTION MEMORY IS THE SOURCE, not the data index: the machine's own instset is the union of every
        // ISA this frame uses, and insts() already merges the members' tables by tid identity, nearest level
        // first. (The scan over memory().spaces() for instanceof InstSet was the old Router-era route, and it is
        // exactly the binding the instruction-memory/data-memory split removes.)
        final InstSet union = Machine.current().instset();
        final List<Inst> all = new ArrayList<>(union.insts());
        for (final InstSet instSet : union.members()) {
            // old-style instsets (e.g. mInstSet) hold their insts in the jvm map under /inst — their insts()
            // answers empty, so read the map directly, as the table-backed merge cannot see them
            final Obj live = instSet.jvm().get(uri(INST));
            if (null != live && live.isLst())
                live.lstValue().stream().filter(Obj::isObjInst).map(Obj::<Inst>as).forEach(all::add);
        }
        final List<Inst> distinct = all.stream().distinct().toList();
        CACHED_INSTS = distinct;
        return distinct;
    }

    /**
     * the directed type graph G: dom basePath -> rng basePath — degenerate endpoints are not vertices
     */
    public static Map<fURI, Set<fURI>> graph(final List<Inst> all) {
        final Map<fURI, Set<fURI>> graph = new HashMap<>();
        for (final Inst inst : all) {
            if (degenerate(inst.dom()) || degenerate(inst.rng()))
                continue;
            final fURI dom = inst.tid().dom().basePath();
            final fURI rng = inst.tid().rng().basePath();
            graph.computeIfAbsent(dom, k -> new HashSet<>()).add(rng);
        }
        return graph;
    }

    /**
     * generics, the root, and noobj are not objects of the category — only named types are
     */
    private static boolean degenerate(final Type type) {
        return type.isGeneric() || type.isRootType();
    }

    /**
     * the reversible core G ∩ G⁻¹ — edges that exist in both directions
     */
    public static Map<fURI, Set<fURI>> reversible(final Map<fURI, Set<fURI>> graph) {
        final Map<fURI, Set<fURI>> reversible = new HashMap<>();
        for (final Map.Entry<fURI, Set<fURI>> edge : graph.entrySet())
            for (final fURI to : edge.getValue())
                if (graph.getOrDefault(to, Set.of()).contains(edge.getKey())) {
                    reversible.computeIfAbsent(edge.getKey(), k -> new HashSet<>()).add(to);
                    reversible.computeIfAbsent(to, k -> new HashSet<>()).add(edge.getKey());
                }
        return reversible;
    }

    public static boolean reaches(final Map<fURI, Set<fURI>> graph, final fURI a, final fURI b) {
        final Set<fURI> seen = new HashSet<>();
        final ArrayDeque<fURI> stack = new ArrayDeque<>();
        stack.push(a);
        while (!stack.isEmpty()) {
            final fURI node = stack.pop();
            if (node.equals(b))
                return true;
            if (!seen.add(node))
                continue;
            graph.getOrDefault(node, Set.of()).forEach(stack::push);
        }
        return false;
    }

    /**
     * a vertex's address in the graph: the type's name, or its tid when it has no name of its own
     */
    public static fURI object(final Type type) {
        return (null != type.vid() ? type.vid() : type.tid()).basePath();
    }

    private static boolean refines(final Type a, final Type b) {
        return a.testNominally(b) && a.c().within(b.c());
    }

    static boolean incomparable(final Type a, final Type b) {
        if (a.isRootType() || a.isGeneric() || b.isRootType() || b.isGeneric())
            return false;
        return !refines(a, b) && !refines(b, a);
    }

    /// //////////////////////////////////////////////////////////////
    // the four computes

    /**
     * the position labels of the inst — where the edge sits among the others
     */
    public static Lst position(final Inst inst) {
        final String key = inst.tid().toString();
        final Lst cached = POSITION_MEMO.get(key);
        if (null != cached)
            return cached;
        final List<Inst> all = instructions();
        final Map<fURI, Set<fURI>> graph = graph(all);
        final fURI out = object(inst.dom());
        final fURI in = object(inst.rng());
        final fURI op = inst.tid().basePath();
        final EnumSet<Position> kinds = EnumSet.noneOf(Position.class);
        // a redundant mapping: another inst of the same family casts the same endpoints
        final long same = all.stream()
                .filter(i -> i.tid().basePath().equals(op) && object(i.dom()).equals(out) && object(i.rng()).equals(in))
                .count();
        if (same > 1)
            kinds.add(Position.duplicate);
        // contested endpoints: siblings sharing this edge's rng with an incomparable dom, or sharing its
        // dom with an incomparable rng — overlapping (same base, cross-over reachable) vs disjoint
        for (final Inst other : all) {
            if (!object(other.rng()).equals(in) || object(other.dom()).equals(out))
                continue;
            if (incomparable(inst.dom(), other.dom()))
                kinds.add(overlap(object(inst.dom()), object(other.dom()), graph) ? Position.ambiguous : Position.incomparable);
        }
        for (final Inst other : all) {
            if (!object(other.dom()).equals(out) || object(other.rng()).equals(in))
                continue;
            if (incomparable(inst.rng(), other.rng()))
                kinds.add(overlap(object(inst.rng()), object(other.rng()), graph) ? Position.ambiguous : Position.incomparable);
        }
        // the relation between the edge's own endpoints
        if (out.equals(in))
            kinds.add(Position.retract);
        else if (graph.getOrDefault(out, Set.of()).contains(in) && graph.getOrDefault(in, Set.of()).contains(out))
            kinds.add(Position.coupling);
        else {
            final Map<fURI, Set<fURI>> reversible = reversible(graph);
            if (reaches(reversible, out, in))
                kinds.add(Position.isochain);
            else if (reaches(graph, out, in) && reaches(graph, in, out))
                kinds.add(Position.retract);
        }
        final Lst result = kinds.stream().map(k -> uri(k.name())).collect(new CommonUtil.LstCollector());
        POSITION_MEMO.put(key, result);
        return result;
    }

    private static boolean overlap(final fURI mine, final fURI theirs, final Map<fURI, Set<fURI>> graph) {
        return mine.equals(theirs) && reaches(graph, mine, theirs);
    }

    /**
     * the witness edges behind the ambiguous/incomparable labels — the contesting siblings
     */
    public static Lst contested(final Inst inst) {
        final String key = inst.tid().toString();
        final Lst cached = CONTESTED_MEMO.get(key);
        if (null != cached)
            return cached;
        final List<Inst> all = instructions();
        final Map<fURI, Set<fURI>> graph = graph(all);
        final fURI out = object(inst.dom());
        final fURI in = object(inst.rng());
        final Set<fURI> witnesses = new LinkedHashSet<>();
        for (final Inst other : all) {
            if (!object(other.rng()).equals(in) || object(other.dom()).equals(out))
                continue;
            if (incomparable(inst.dom(), other.dom()))
                witnesses.add(other.tid());
        }
        for (final Inst other : all) {
            if (!object(other.dom()).equals(out) || object(other.rng()).equals(in))
                continue;
            if (incomparable(inst.rng(), other.rng()))
                witnesses.add(other.tid());
        }
        final Lst result = witnesses.stream().map(MUri::uri).collect(new CommonUtil.LstCollector());
        CONTESTED_MEMO.put(key, result);
        return result;
    }

    /**
     * the reversible-core component of the inst's dom — everything the type can invert to and back
     */
    public static Obj orbit(final Inst inst) {
        if (inst.isNoObj())
            return inst;
        final String key = inst.tid().toString();
        final Obj cached = ORBIT_MEMO.get(key);
        if (null != cached && !cached.isNoObj())
            return cached;
        final Map<fURI, Set<fURI>> reversible = reversible(graph(instructions()));
        final Set<fURI> seen = new LinkedHashSet<>();
        final ArrayDeque<fURI> stack = new ArrayDeque<>();
        stack.push(object(inst.dom()));
        while (!stack.isEmpty()) {
            final fURI node = stack.pop();
            if (!seen.add(node))
                continue;
            reversible.getOrDefault(node, Set.of()).forEach(stack::push);
        }
        final Obj result = objs(seen.stream().sorted().map(f -> rec(mutableMap(uri(OBJ), T(f)), OBJECT_TID, null)));
        ORBIT_MEMO.put(key, result);
        return result;
    }

    /**
     * the same-op sibling registrations — the inst's own family
     */
    public static Lst family(final Inst inst) {
        final String key = inst.tid().toString();
        final Lst cached = FAMILY_MEMO.get(key);
        if (null != cached)
            return cached;
        final Lst result = instructions().stream()
                .filter(i -> i.tid().basePath().equals(inst.tid().basePath()))
                .map(Obj::<Obj>as)
                .collect(new CommonUtil.LstCollector());
        FAMILY_MEMO.put(key, result);
        return result;
    }

    /**
     * The set-theoretic class of the inst — a lst of class-label uris, computed conservatively (sound: no
     * label is emitted unless it is mathematically forced). The invertible case is the declared involution
     * law ({@code f·f = 1} makes {@code f} its own inverse, hence iso; an iso is both a section and a
     * retraction, hence both mono and epi). A proper (non-split) mono/epi is not yet detected.
     */
    public static Lst clazz(final Inst inst) {
        if (inst.isNoObj())
            return lst();
        final boolean endo = object(inst.dom()).equals(object(inst.rng()));
        final LawTable.Entry entry = mInstSetLawTable.INSTANCE.lookup(inst.tid());
        final boolean iso = null != entry && entry.laws().lstValue().stream()
                .anyMatch(l -> l.uriValue().name().equals(Law.involution.name()));
        final List<Obj> labels = new ArrayList<>();
        if (endo)
            labels.add(uri(MorphClass.endo.name()));
        if (iso) {
            labels.add(uri(MorphClass.iso.name()));
            if (endo)
                labels.add(uri(MorphClass.auto.name()));
            labels.add(uri(MorphClass.section.name()));
            labels.add(uri(MorphClass.retraction.name()));
            labels.add(uri(MorphClass.mono.name()));
            labels.add(uri(MorphClass.epi.name()));
        }
        return lst(labels);
    }

    /**
     * Materialize a category block's lazy {@code !compute_*} auto_calls: copy the block and resolve every
     * auto field to its computed value, leaving the declared fields untouched. The stored block stays a
     * declaration — this returns the fully-populated view {@code ?catq} serves.
     *
     * @param block the stored object/morphism block (declared fields plus lazy compute pointers)
     * @return a fully-populated copy
     */
    public static Rec materialize(final Rec block) {
        final Map<Obj, Obj> filled = new LinkedHashMap<>();
        block.jvm().forEach((key, value) -> filled.put(key, value.dereference()));
        return rec(filled, block.tid(), block.vid());
    }
}
