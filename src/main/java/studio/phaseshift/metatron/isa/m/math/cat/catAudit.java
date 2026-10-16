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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import java.util.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;


/**
 * The as-graph audit — the cast subgraph of the category: the registered {@code as}-casts minus the
 * identities, the {@link Position} findings over them, and the implicit casts the subtype hierarchy
 * manifests. Each finding is a {@link Finding} — the witness insts, the tightest position kind, and
 * a reason.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catAudit {

    public record Finding(List<Inst> insts, Position kind, String reason) {
    }


    /**
     * The cast family, minus the identity casts ({@code as?X<=X} are trivially true, not graph edges).
     */
    private static List<Inst> asInsts() {
        return Machine.read(AS_INST_TID).stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(inst -> !inst.tid().dom().equals(inst.tid().rng()))
                .sorted(Comparator.comparing(inst -> inst.tid().toString()))
                .toList();
    }

    /**
     * Audit the cast subgraph for the requested {@link Position kinds}: {@code duplicate} casts, {@code ambiguous}/{@code incomparable} contesting
     * doms, and {@code coupling}/{@code isochain}/{@code retract} reversibility — each at its tightest kind,
     * with a reason.
     *
     * @param kinds the position kinds to check (empty means all)
     * @return the findings found, each as a {@link Finding}
     */
    public static Set<Finding> check(final Position... kinds) {
        final Set<Finding> findings = new HashSet<>();
        if (!Machine.loaded())
            return findings;
        final Set<Position> requested = 0 == kinds.length
                ? EnumSet.allOf(Position.class)
                : EnumSet.copyOf(Arrays.asList(kinds));
        final List<Inst> asInsts = asInsts();
        if (asInsts.isEmpty())
            return findings;
        if (requested.contains(Position.duplicate) || requested.contains(Position.ambiguous) || requested.contains(Position.incomparable)) {
            // undirected cast graph (dom <-> rng), keyed by basePath — used to decide whether two same-base
            // sibling doms capture the same values (reachable via a cross-over path)
            final Map<fURI, Set<fURI>> graph = new HashMap<>();
            for (final Inst inst : asInsts) {
                final fURI dom = inst.tid().dom().basePath();
                final fURI rng = inst.tid().rng().basePath();
                graph.computeIfAbsent(dom, k -> new HashSet<>()).add(rng);
                graph.computeIfAbsent(rng, k -> new HashSet<>()).add(dom);
            }
            final Map<fURI, List<Inst>> byRng = new LinkedHashMap<>();
            for (final Inst inst : asInsts)
                byRng.computeIfAbsent(inst.tid().rng(), k -> new ArrayList<>()).add(inst);
            for (final List<Inst> group : byRng.values()) {
                for (int i = 0; i < group.size(); i++) {
                    for (int j = i + 1; j < group.size(); j++) {
                        final Type domA = group.get(i).dom();
                        final Type domB = group.get(j).dom();
                        final Type rngType = group.get(i).rng();
                        if (group.get(i).tid().dom().equals(group.get(j).tid().dom())) {
                            if (requested.contains(Position.duplicate))
                                findings.add(new Finding(group, Position.duplicate, "duplicate as: dom " + group.get(i).dom() + " -> rng " + rngType.namedType() + " appears twice"));
                        } else if (catGraph.incomparable(domA, domB)) {
                            final boolean sameBase = domA.tid().basePath().equals(domB.tid().basePath());
                            final boolean overlapping = sameBase && catGraph.reaches(graph, group.get(i).tid().dom().basePath(), group.get(j).tid().dom().basePath());
                            final Position kind = overlapping ? Position.ambiguous : Position.incomparable;
                            if (requested.contains(kind))
                                findings.add(new Finding(group, kind,
                                        (overlapping ? "ambiguous" : "incomparable") + " as: dom " + group.get(i).dom() + " vs dom " + group.get(j).dom() + " for rng " + rngType.namedType()));
                        }
                    }
                }
            }
        }
        if (requested.contains(Position.coupling) || requested.contains(Position.isochain) || requested.contains(Position.retract))
            findings.addAll(retracts(asInsts, requested));
        return findings;
    }

    /**
     * the coupling/isochain/retract pair relations over the cast subgraph — one finding per type pair
     */
    private static Set<Finding> retracts(final List<Inst> asInsts, final Set<Position> requested) {
        final Set<Finding> retracts = new HashSet<>();
        final Map<fURI, Set<fURI>> graph = new HashMap<>();
        for (final Inst inst : asInsts)
            graph.computeIfAbsent(inst.tid().dom().basePath(), k -> new HashSet<>()).add(inst.tid().rng().basePath());
        final Map<fURI, Set<fURI>> reversible = catGraph.reversible(graph);
        final List<fURI> nodes = new ArrayList<>(graph.keySet());
        nodes.sort(Comparator.comparing(fURI::toString));
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final fURI a = nodes.get(i);
                final fURI b = nodes.get(j);
                final Position kind;
                if (graph.getOrDefault(a, Set.of()).contains(b) && graph.getOrDefault(b, Set.of()).contains(a))
                    kind = Position.coupling;
                else if (catGraph.reaches(reversible, a, b))
                    kind = Position.isochain;
                else if (catGraph.reaches(graph, a, b) && catGraph.reaches(graph, b, a))
                    kind = Position.retract;
                else
                    continue;
                if (requested.contains(kind))
                    retracts.add(new Finding(List.of(), kind, typeName(a) + " ⇄ " + typeName(b)));
            }
        }
        return retracts;
    }

    private static String typeName(final fURI f) {
        final cInt c = f.c();
        return f.name() + (null == c || c.isOne() ? "" : "{" + c + "}");
    }

    /**
     * Manifest the implicit casts that arise from the subtype hierarchy of the types already present in the
     * explicit cast subgraph: a type {@code T} refining {@code A} ({@code T.test(A)}) needs no explicit
     * {@code as?A<=T} — the cast is just the removal of the constraint, realized by re-tagging to {@code A}'s
     * vid. Only direct refinements are emitted; generics, the root, and {@code noobj} are excluded.
     *
     * @return the manifested implicit {@code as} instructions (empty when nothing is registered)
     */
    public static List<Inst> implicit() {
        final List<Inst> implicit = new ArrayList<>();
        if (!Machine.loaded())
            return implicit;
        final List<Type> types = Machine.read(AS_INST_TID).stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .flatMap(inst -> List.of(inst.dom().vid(), inst.rng().vid()).stream())
                .map(fURI::basePath)
                .distinct()
                .sorted(Comparator.comparing(fURI::toString))
                .map(vid -> T(vid))
                .filter(t -> !t.isGeneric() && !t.isRootType() && !t.vid().basePath().equals(NOOBJ_TID))
                .toList();
        for (final Type src : types) {
            for (final Type dst : types) {
                if (src.vid().equals(dst.vid()))
                    continue;
                if (src.test(dst))
                    implicit.add(instC(AS_INST_TID.dom(src.vid()).rng(dst.vid()), lst(dst), (lhs, inst) -> lhs.vid(inst.arg(0).vid())));
            }
        }
        return implicit;
    }
}
