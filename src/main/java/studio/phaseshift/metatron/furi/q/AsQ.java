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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.QProc.QPROC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The {@code as}-graph: every {@code as} instruction is an edge labelled {@code as} whose outV is its dom and
 * whose inV is its rng, and {@link #asEdgeKinds(Type, Type)} is that edge's property map — the kinds of
 * relation the edge participates in. {@code ?asq} reads an edge by its endpoints and hands back the edge
 * carrying its map, so the graph is introspectable from mtron without being materialised anywhere.
 * <p>
 * The map is derived, never authoritative: {@link #EDGE_PROPERTIES} memoizes the derivation and is dropped
 * when a new {@code as} instruction is written.
 */
public final class AsQ {

    private AsQ() {
    }

    public static final fURI ASQ_PATTERN = f("asq");
    public static final fURI ASQ_TID = QPROC_TID.extend(ASQ_PATTERN);
    public static final Type ASQ_TYPE = Type.Builder.build()
            .tid(QPROC_TID)
            .vid(ASQ_TID)
            .constructor(AsQ::asQ)
            .create();

    /**
     * the derived edge properties, keyed by edge identity (outV@inV). shared across the instances of the
     * q-proc because they live on different spaces: a q-param'd read is served by the pattern-less catch-all
     * space while an {@code as} instruction is written through the {@code /m} space, and only a shared store
     * lets the writing instance's invalidation be seen by the reading one.
     */
    private static final Map<String, Lst> EDGE_PROPERTIES = new ConcurrentHashMap<>();

    /**
     * The {@code as}-graph read: an edge named by its endpoints. A {@code ?asq=[kind,…]} narrows the property
     * map to the kinds asked for; when the edge has none of them there is no such edge property to read, so
     * the read yields nothing.
     */
    public static QProc asQ() {
        return QProc.Helper.build(ASQ_TID, ASQ_PATTERN)
                .postRead((furi, obj) -> {
                    if (!furi.hasDom() || !furi.hasRng())
                        return noobj();
                    final Type dom = T(furi.dom().big());
                    final Type rng = T(furi.rng().big());
                    final String askedQ = furi.q(ASQ_PATTERN.toString());
                    final Obj asked = null == askedQ || askedQ.isBlank() ? noobj() : ObjmtronSerializer.parse(askedQ);
                    final Set<String> wanted = asked.isLst() ? asked.asLst().elements().map(AsQ::asKindName).collect(Collectors.toSet())
                            : asked.isNoObj() ? null : Set.of(asKindName(asked));
                    final Lst kinds = EDGE_PROPERTIES.computeIfAbsent(asVertex(dom) + "@" + asVertex(rng),
                                    id -> asEdgeKinds(dom, rng)).elements()
                            .filter(k -> null == wanted || wanted.contains(asKindName(k)))
                            .collect(new CommonUtil.LstCollector());
                    if (kinds.isEmpty())
                        return noobj();
                    final fURI outVID = null != dom.vid() ? dom.vid() : dom.tid();
                    final fURI inVID = null != rng.vid() ? rng.vid() : rng.tid();
                    // a plain uri: the edge is a description, and an asq read must never write back
                    return uri(AS_INST_TID.dom(outVID).rng(inVID).q(ASQ_PATTERN.toString(), kinds));
                })
                // a new as instruction changes the graph under the map
                .postWrite((vid, oldObj, newObj) -> {
                    if (AS_INST_TID.test(vid.basePath()))
                        EDGE_PROPERTIES.clear();
                    return noobj();
                }).create();
    }

    /** a kind's name, whichever way it arrived: {@code ?asq=[retract]} may parse to {@code /m/retract} */
    private static String asKindName(final Obj kind) {
        final String name = kind.isUri() ? kind.asUri().uriValue().name() : kind.toCleanString();
        return name.toLowerCase();
    }

    /**
     * An {@code as}-graph finding: the offending instructions, the violation kind, and a human-readable reason.
     */
    public record AsEdge(List<Inst> insts, Kind kind, String reason) {

        public enum Kind {
            /**
             * two {@code as} instructions cast the same dom to the same rng — a redundant mapping
             */
            DUPLICATE("two `as` instructions cast the same dom to the same rng — a redundant mapping:  f, g : A → B,  f ≠ g"),
            /**
             * same-rng doms are incomparable yet overlap — a future input could match both with no most-specific winner
             */
            AMBIGUOUS("same-rng doms are incomparable yet overlap — a future input could match both with no most-specific winner:  ¬(A ≤ B) ∧ ¬(B ≤ A) ∧ ∃T. T ≤ A ∧ T ≤ B"),
            /**
             * same-rng doms are incomparable and disjoint — no input matches both, so dispatch stays total
             */
            INCOMPARABLE("same-rng doms are incomparable and disjoint — no input matches both, so dispatch stays total:  ¬(A ≤ B) ∧ ¬(B ≤ A) ∧ ∄T. T ≤ A ∧ T ≤ B"),
            /**
             * two types are directly mutually castable — a pair of opposing casts, a candidate isomorphism/retraction
             */
            COUPLING("two types are directly mutually castable (A ⇄ B) — a pair of opposing casts, a candidate isomorphism/retraction:  f : A → B,  g : B → A"),
            /**
             * two types are linked by a chain of couplings — connected in the reversible core of the as-graph
             */
            ISOCHAIN("two types are linked by a chain of couplings (A ⇄ … ⇄ B) — connected in the reversible core G∩G⁻¹, each hop an opposing pair:  a chain of candidate-isomorphisms"),
            /**
             * two types are mutually reachable — the round-trip is an idempotent, so each is a retract of the other
             */
            RETRACT("two types are mutually reachable (A ⇒ B ∧ B ⇒ A) — the round-trip A⇒B⇒A is an idempotent, so A ≅ im(e), a subobject of A, not necessarily A itself:  e = A⇒B⇒A,  e∘e = e,  A ≅ { a ∈ A : e(a) = a }");

            private final String description;

            Kind(final String description) {
                this.description = description;
            }

            public String description() {
                return this.description;
            }
        }
    }

    /**
     * Checks the {@code as}-graph for the requested {@link AsEdge.Kind}s.
     * <p>
     * Dispatch resolves {@code as} by matching the input against each candidate's dom and choosing the
     * most-specific (most-refined) dom that accepts it. The full check exposes four layers of potential
     * ambiguity, from cheap syntactic facts up to graph reachability:
     * <ol>
     *   <li>{@link AsEdge.Kind#DUPLICATE} — two {@code as} instructions sharing the same dom and rng;</li>
     *   <li>{@link AsEdge.Kind#AMBIGUOUS} / {@link AsEdge.Kind#INCOMPARABLE} — same-rng doms that are
     *   neither {@code A ≤ B} nor {@code B ≤ A}, split by whether they overlap;</li>
     *   <li>{@link AsEdge.Kind#COUPLING} / {@link AsEdge.Kind#ISOCHAIN} / {@link AsEdge.Kind#RETRACT}
     *   — mutually castable type pairs (reversibility at three strengths).</li>
     * </ol>
     * Refinement ({@code A ≤ B}) is {@code A.testNominally(B) && A.c().within(B.c())} — predicate-blind, so it
     * certifies dispatch, not type-checking.
     *
     * @param types the violation kinds to check (empty means all)
     * @return the violations found, each at its tightest {@link AsEdge.Kind}
     */
    public static Set<AsEdge> check(final AsEdge.Kind... types) {
        final Set<AsEdge> violations = new HashSet<>();
        if (!Router.loaded())
            return violations;
        final Set<AsEdge.Kind> requested = 0 == types.length
                ? EnumSet.allOf(AsEdge.Kind.class)
                : EnumSet.copyOf(Arrays.asList(types));
        final List<Inst> asInsts = asInsts();
        if (asInsts.isEmpty())
            return violations;
        // same-rng ambiguity (DUPLICATE / AMBIGUOUS / INCOMPARABLE)
        if (requested.contains(AsEdge.Kind.DUPLICATE) || requested.contains(AsEdge.Kind.AMBIGUOUS) || requested.contains(AsEdge.Kind.INCOMPARABLE)) {
            // undirected type graph of every non-identity as edge (dom <-> rng), keyed by basePath — used to
            // decide whether two same-base sibling doms "capture the same values" (reachable via a cross-over path)
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
                            if (requested.contains(AsEdge.Kind.DUPLICATE))
                                violations.add(new AsEdge(group, AsEdge.Kind.DUPLICATE, "duplicate as: dom " + group.get(i).dom() + " -> rng " + rngType.namedType() + " appears twice"));
                        } else if (incomparable(domA, domB)) {
                            final boolean sameBase = domA.tid().basePath().equals(domB.tid().basePath());
                            final boolean overlapping = sameBase && reaches(graph, group.get(i).tid().dom().basePath(), group.get(j).tid().dom().basePath());
                            final AsEdge.Kind type = overlapping ? AsEdge.Kind.AMBIGUOUS : AsEdge.Kind.INCOMPARABLE;
                            if (requested.contains(type))
                                violations.add(new AsEdge(group, type,
                                        (overlapping ? "ambiguous" : "incomparable") + " as: dom " + group.get(i).dom() + " vs dom " + group.get(j).dom() + " for rng " + rngType.namedType()));
                        }
                    }
                }
            }
        }
        // reversible/retract graph relations (COUPLING / ISOCHAIN / RETRACT)
        if (requested.contains(AsEdge.Kind.COUPLING) || requested.contains(AsEdge.Kind.ISOCHAIN) || requested.contains(AsEdge.Kind.RETRACT))
            violations.addAll(retracts(asInsts, requested));
        return violations;
    }

    private static List<Inst> asInsts() {
        return Router.readFromSpace(AS_INST_TID).stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(inst -> !inst.tid().dom().equals(inst.tid().rng())) // identity casts (as?X<=X) are trivially true — not part of the graph
                .sorted(Comparator.comparing(inst -> inst.tid().toString()))
                .toList();
    }

    /**
     * The {@code as}-graph is a property graph: an {@code as} instruction is an edge labeled {@code as},
     * its dom is the outV and its rng the inV. This returns the edge's property map — the kinds of
     * relation that <em>this edge</em> participates in, at its tightest kind per relation, mirroring how
     * {@link #retracts} picks one of COUPLING/ISOCHAIN/RETRACT for a pair:
     * <ul>
     *   <li>{@link AsEdge.Kind#DUPLICATE} — another {@code as} instruction casts this edge's outV to its inV;</li>
     *   <li>{@link AsEdge.Kind#AMBIGUOUS} / {@link AsEdge.Kind#INCOMPARABLE} — the edge's outV is one of
     *       several outVs reaching its inV (or its inV one of several inVs leaving its outV) that are
     *       incomparable, overlapping (AMBIGUOUS) or disjoint (INCOMPARABLE);</li>
     *   <li>{@link AsEdge.Kind#COUPLING} / {@link AsEdge.Kind#ISOCHAIN} / {@link AsEdge.Kind#RETRACT} —
     *       the relation between the edge's own two endpoints: an opposing edge (COUPLING), linked by a chain
     *       of opposing pairs (ISOCHAIN), or mutually reachable only (RETRACT).</li>
     * </ul>
     * A self-loop (outV == inV) is the identity, an idempotent, so it is a RETRACT of itself — not a coupling,
     * which needs two types.
     *
     * @param dom the edge's outV (the instruction's dom)
     * @param rng the edge's inV (the instruction's rng)
     * @return the kinds, in declaration order, each as a lowercase uri (empty when the edge participates in none)
     */
    public static Lst asEdgeKinds(final Type dom, final Type rng) {
        if (null == dom || null == rng || !Router.loaded())
            return lst();
        final List<Inst> asInsts = asInsts();
        final fURI out = asVertex(dom);
        final fURI in = asVertex(rng);
        final EnumSet<AsEdge.Kind> kinds = EnumSet.noneOf(AsEdge.Kind.class);
        // directed type graph G (outV -> inV) and its reversible core G ∩ G⁻¹, both keyed by basePath
        final Map<fURI, Set<fURI>> graph = new HashMap<>();
        for (final Inst inst : asInsts)
            graph.computeIfAbsent(inst.tid().dom().basePath(), k -> new HashSet<>()).add(inst.tid().rng().basePath());
        // a redundant mapping: another instruction casts this edge's outV to its inV
        if (asInsts.stream().filter(i -> i.tid().dom().basePath().equals(out) && i.tid().rng().basePath().equals(in)).count() > 1)
            kinds.add(AsEdge.Kind.DUPLICATE);
        // contested endpoints: compare this edge's outV against the siblings sharing its inV, and its inV
        // against the siblings sharing its outV. A sibling with the same outV AND inV is a duplicate, not an
        // ambiguity, so it is skipped here.
        for (final Inst other : asInsts) {
            if (!other.tid().rng().basePath().equals(in) || other.tid().dom().basePath().equals(out))
                continue;
            addContested(kinds, dom, other.dom(), out, other.tid().dom().basePath(), graph);
        }
        for (final Inst other : asInsts) {
            if (!other.tid().dom().basePath().equals(out) || other.tid().rng().basePath().equals(in))
                continue;
            addContested(kinds, rng, other.rng(), in, other.tid().rng().basePath(), graph);
        }
        // the relation between the edge's own endpoints
        if (out.equals(in))
            kinds.add(AsEdge.Kind.RETRACT);
        else if (graph.getOrDefault(out, Set.of()).contains(in) && graph.getOrDefault(in, Set.of()).contains(out))
            kinds.add(AsEdge.Kind.COUPLING);
        else {
            final Map<fURI, Set<fURI>> reversible = new HashMap<>();
            for (final Map.Entry<fURI, Set<fURI>> edge : graph.entrySet())
                for (final fURI to : edge.getValue())
                    if (graph.getOrDefault(to, Set.of()).contains(edge.getKey())) {
                        reversible.computeIfAbsent(edge.getKey(), k -> new HashSet<>()).add(to);
                        reversible.computeIfAbsent(to, k -> new HashSet<>()).add(edge.getKey());
                    }
            if (reaches(reversible, out, in))
                kinds.add(AsEdge.Kind.ISOCHAIN);
            else if (reaches(graph, out, in) && reaches(graph, in, out))
                kinds.add(AsEdge.Kind.RETRACT);
        }
        return kinds.stream().map(k -> uri(k.name().toLowerCase())).collect(new CommonUtil.LstCollector());
    }

    private static void addContested(final EnumSet<AsEdge.Kind> kinds, final Type mine, final Type theirs,
                                     final fURI minePath, final fURI theirPath, final Map<fURI, Set<fURI>> graph) {
        if (!incomparable(mine, theirs))
            return;
        final boolean overlapping = minePath.basePath().equals(theirPath.basePath()) && reaches(graph, minePath, theirPath);
        kinds.add(overlapping ? AsEdge.Kind.AMBIGUOUS : AsEdge.Kind.INCOMPARABLE);
    }

    /** a vertex's address in the as-graph: the type's name, or its tid when it has no name of its own */
    private static fURI asVertex(final Type type) {
        return (null != type.vid() ? type.vid() : type.tid()).basePath();
    }

    private static Set<AsEdge> retracts(final List<Inst> asInsts, final Set<AsEdge.Kind> requested) {
        final Set<AsEdge> retracts = new HashSet<>();
        // directed type graph G (dom -> rng), keyed by basePath
        final Map<fURI, Set<fURI>> graph = new HashMap<>();
        for (final Inst inst : asInsts)
            graph.computeIfAbsent(inst.tid().dom().basePath(), k -> new HashSet<>()).add(inst.tid().rng().basePath());
        // reversible core G ∩ G⁻¹ — the undirected edges that exist in both directions
        final Map<fURI, Set<fURI>> reversible = new HashMap<>();
        for (final Map.Entry<fURI, Set<fURI>> edge : graph.entrySet()) {
            final fURI dom = edge.getKey();
            for (final fURI rng : edge.getValue()) {
                if (graph.getOrDefault(rng, Set.of()).contains(dom)) {
                    reversible.computeIfAbsent(dom, k -> new HashSet<>()).add(rng);
                    reversible.computeIfAbsent(rng, k -> new HashSet<>()).add(dom);
                }
            }
        }
        final List<fURI> nodes = new ArrayList<>(graph.keySet());
        nodes.sort(Comparator.comparing(fURI::toString));
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final fURI a = nodes.get(i);
                final fURI b = nodes.get(j);
                final AsEdge.Kind type;
                if (graph.getOrDefault(a, Set.of()).contains(b) && graph.getOrDefault(b, Set.of()).contains(a))
                    type = AsEdge.Kind.COUPLING;                              // direct opposing pair
                else if (reaches(reversible, a, b))
                    type = AsEdge.Kind.ISOCHAIN;                              // chain of opposing pairs
                else if (reaches(graph, a, b) && reaches(graph, b, a))
                    type = AsEdge.Kind.RETRACT;                               // mutual reachability only
                else
                    continue;
                if (requested.contains(type))
                    retracts.add(new AsEdge(List.of(), type, typeName(a) + " ⇄ " + typeName(b)));
            }
        }
        return retracts;
    }

    /**
     * Manifests the implicit {@code as} instructions that arise from the subtype hierarchy of the
     * types already present in the explicit as-graph.
     * <p>
     * A type {@code T} that is a nominal or structural refinement of {@code A} ({@code T.test(A)} holds)
     * needs no explicit {@code as?A<=T}: the cast is just the removal of the constraint that
     * distinguishes {@code T} from {@code A}, realized by re-tagging the value with {@code A}'s vid. Only
     * direct refinements among the harvestable types are emitted — sibling subtypes (e.g. {@code json} vs
     * {@code yaml}) and unrelated base types never satisfy {@code test}. Generics ({@code A}, {@code B})
     * are per-instruction bindings and are excluded, as are the root and {@code noobj} types.
     *
     * @return the manifested implicit {@code as} instructions (empty when nothing is registered)
     */
    public static List<Inst> implicit() {
        final List<Inst> implicit = new ArrayList<>();
        if (!Router.loaded())
            return implicit;
        final List<Type> types = Router.readFromSpace(AS_INST_TID).stream()
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

    private static boolean incomparable(final Type a, final Type b) {
        if (a.isRootType() || a.isGeneric() || b.isRootType() || b.isGeneric())
            return false;
        return !refines(a, b) && !refines(b, a);
    }

    private static boolean refines(final Type a, final Type b) {
        return a.testNominally(b) && a.c().within(b.c());
    }

    private static boolean reaches(final Map<fURI, Set<fURI>> graph, final fURI a, final fURI b) {
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

    private static String typeName(final fURI f) {
        final cInt c = f.c();
        return f.name() + (null == c || c.isOne() ? "" : "{" + c + "}");
    }

}
