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

package studio.phaseshift.metatron.algebra;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.union_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The category of the object graph: every inst is a morphism — an edge whose outV is its dom and inV its
 * rng — and every type is an object (a vertex). The classification space has three axes —
 * {@link Inst.Form form} (the n-tid's coefficient shape), {@link Position position} (where the edge sits
 * among the others), and {@link Law law} (the algebraic laws it obeys). The instructions under
 * {@code /m/algebra} reify the position analysis as ordinary instructions, and the block types type the
 * category's objects and morphisms: {@code category::T} (the shared obj-and-orbit base),
 * {@code object::T} (a vertex), {@code morphism::T} (an edge), {@code algebraic_theory::T} (the nominal super
 * of the algebraic theories), and {@code ring_theory::T}/{@code group_theory::T}/{@code monoid_theory::T}
 * (role-parameterized theory structures an object models).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Category {

    private Category() {
    }

    /// //////////////////////////////////////////////////////////////
    // the axes

    /**
     * The position axis: the six relations an edge takes part in, derived from its endpoints' place in
     * the graph. Enum constants are lowercase so {@code name()} is the mtron label.
     */
    public enum Position {
        /**
         * another inst of the same family maps the same dom to the same rng — a redundant registration
         */
        duplicate,
        /**
         * contested by an incomparable sibling that overlaps — dispatch has no most-specific winner
         */
        ambiguous,
        /**
         * contested by an incomparable, disjoint sibling — dispatch stays total
         */
        incomparable,
        /**
         * an opposing edge exists — a candidate inverse pair
         */
        coupling,
        /**
         * endpoints linked by a chain of couplings — one orbit of the reversible core
         */
        isochain,
        /**
         * mutually reachable and round trip idempotent — a subobject retraction
         */
        retract;
    }

    /**
     * A category-graph finding: the offending instructions, the position kind, and a human-readable reason.
     */
    public record Finding(List<Inst> insts, Position kind, String reason) {
    }

    /**
     * The law axis: the algebraic laws a morphism obeys. {@code boolean_} is the one keyword exception —
     * its mtron label is {@code boolean}.
     */
    public enum Law {
        // syntactic — derived from the n-tid
        /**
         * an annihilator: {@code a·x = a} for every {@code x} (absorbing element of the operation)
         */
        absorbing(Tier.syntactic),
        /**
         * the reflexive-transitive iterate — {@code aⁿ} over the operation (a Kleene closure)
         */
        kleene(Tier.syntactic),
        /**
         * pass-through-or-drop ({0,1}); the optional/zeroable lift of a value
         */
        partial(Tier.syntactic),
        /**
         * commutes through the stream ring's parallel/serial structure — the coefficient floats off F\Fr
         */
        floatable(Tier.syntactic),
        /**
         * cannot float past a barrier — a reduce anchors the coefficient in place
         */
        blocked(Tier.syntactic),
        // declared — proved once, registered per family
        /**
         * an associative binary op with an identity element
         */
        monoidic(Tier.declared),
        /**
         * an associative, commutative, idempotent join/meet
         */
        semilattice(Tier.declared),
        /**
         * the Boolean-algebra structure of the filter family ({@code ∧=·}, {@code ∨=a+b−ab}, {@code ¬=ā})
         */
        boolean_(Tier.declared),
        /**
         * a ring where only one distributivity side holds
         */
        near_ring(Tier.declared),
        /**
         * two binary ops — a commutative group under {@code +} and a monoid under {@code ·} that distributes
         */
        ring(Tier.declared),
        /**
         * a monoid where every element has an inverse
         */
        group(Tier.declared),
        /**
         * a monoid/group acting on a carrier — {@code f(gh)x = f(g)f(h)x}
         */
        action(Tier.declared),
        /**
         * an atomic/indecomposable element — no nontrivial factorization
         */
        prime(Tier.declared),
        /**
         * distributes over {@code +} on the left — {@code a(b+c) = ab+ac}
         */
        left_distributive(Tier.declared),
        /**
         * distributes over {@code +} on the right — {@code (a+b)c = ac+bc}
         */
        right_distributive(Tier.declared),
        /**
         * {@code a·b = b·a}
         */
        commutative(Tier.declared),
        /**
         * the carrier is partially ordered, and these insts are its comparisons
         */
        poset(Tier.declared),
        // semantic — apply-and-test on canonical objs
        /**
         * the identity element — {@code e·x = x·e = x}
         */
        unit(Tier.semantic),
        /**
         * self-inverse — {@code f(f(x)) = x} (period two)
         */
        involution(Tier.semantic),
        /**
         * {@code f(f(x)) = f(x)}
         */
        idempotent(Tier.semantic),
        /**
         * some power is zero — {@code fⁿ = 0}
         */
        nilpotent(Tier.semantic),
        /**
         * nonzero {@code a} with {@code a·b = 0} for some nonzero {@code b}
         */
        zero_divisor(Tier.semantic),
        /**
         * generated by one element — every value is a power of a single generator
         */
        cyclic(Tier.semantic);

        public enum Tier {
            syntactic, declared, semantic;
        }

        private final Tier tier;

        Law(final Tier tier) {
            this.tier = tier;
        }

        public Tier tier() {
            return this.tier;
        }

        /**
         * the mtron label — {@code name()} save the {@code boolean} keyword collision
         */
        public String label() {
            return this.name().equals("boolean_") ? "boolean" : this.name();
        }
    }

    /**
     * The authoring helper: a lst of law-label uris for a {@link CatQ#catWrap} declaration —
     * {@code Category.laws(Law.commutative, Law.right_distributive)}.
     */
    public static Lst laws(final Law... laws) {
        return lst(Arrays.stream(laws).map(l -> (Obj) uri(l.label())).toList());
    }

    /// //////////////////////////////////////////////////////////////
    // tids

    public static final fURI ALGEBRA_TID = M_ISA_TID.extend("algebra");
    public static final fURI COMPUTE_POSITION_TID = ALGEBRA_TID.extend("compute_position");
    public static final fURI COMPUTE_CONTESTED_TID = ALGEBRA_TID.extend("compute_contested");
    public static final fURI COMPUTE_ORBIT_TID = ALGEBRA_TID.extend("compute_orbit");
    public static final fURI COMPUTE_FAMILY_TID = ALGEBRA_TID.extend("compute_family");
    public static final fURI CATEGORY_TID = ALGEBRA_TID.extend("category");
    public static final fURI MORPHISM_TID = CATEGORY_TID.extend("morphism");
    public static final fURI OBJECT_TID = CATEGORY_TID.extend("object");
    public static final fURI ALGEBRAIC_THEORY_TID = ALGEBRA_TID.extend("algebraic_theory");
    public static final fURI RING_THEORY_TID = ALGEBRA_TID.extend("ring_theory");
    public static final fURI GROUP_THEORY_TID = ALGEBRA_TID.extend("group_theory");
    public static final fURI MONOID_THEORY_TID = ALGEBRA_TID.extend("monoid_theory");

    /// //////////////////////////////////////////////////////////////
    // the category block types — an object or a morphism is an obj (the elevator back to the obj graph)
    // plus its reversible orbit; a morphism is the edge's algebra (form/position/law/…), an object is the
    // algebraic theories it models

    /**
     * {@code category::T} — the base category block: the obj elevator and its reversible orbit
     */
    public static final Type CATEGORY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CATEGORY_TID)
            .isaPredicate(rec(
                    uri(OBJ), ALL_TYPE,
                    uri(ORBIT).maybe(), lst(ALL_TYPE)))
            .create();

    /**
     * {@code morphism::T} — the edge block: the morphism's own algebra
     */
    public static final Type MORPHISM_TYPE = Type.Builder.build()
            .tid(CATEGORY_TID)
            .vid(MORPHISM_TID)
            .isaPredicate(rec(
                    // uri(OBJ), INST_TYPE,
                    uri(FORM), union_(Arrays.stream(Inst.Form.values()).map(e -> (Obj) uri(e.name())).toList()).tryToInst(),
                    uri(POSITION).maybe(), lst(union_(Arrays.stream(Position.values()).map(e -> (Obj) uri(e.name())).toList()).tryToInst().c(cInt.SOME())),
                    uri(CONTESTED).maybe(), lst(INST_TYPE),
                    //uri(ORBIT).maybe(), lst(ALL_TYPE),
                    uri(FAMILY).maybe(), lst(INST_TYPE),
                    uri(LAW).maybe(), lst(union_(Arrays.stream(Law.values()).map(e -> (Obj) uri(e.label())).toList()).tryToInst().c(cInt.SOME())),
                    uri(INVERSE).maybe(), URI_TYPE))
            .create();

    /// //////////////////////////////////////////////////////////////
    // the algebraic theories — a theory is a named tuple of op-edges (role => op-tid); algebraic_theory::T
    // is the nominal (structure-blind) super-type grouping them for bookkeeping and inference

    /**
     * {@code algebraic_theory::T} — nominal super-type of the algebraic theories
     */
    public static final Type ALGEBRAIC_THEORY = Type.Builder.build()
            .tid(REC_TID)
            .vid(ALGEBRAIC_THEORY_TID)
            .create();

    /**
     * {@code ring_theory::T} — (add, mul, zero, one): additive and multiplicative ops with their identities
     */
    public static final Type RING_THEORY_TYPE = Type.Builder.build()
            .tid(ALGEBRAIC_THEORY_TID)
            .vid(RING_THEORY_TID)
            .isaPredicate(rec(
                    uri(ADD), INST_TYPE,
                    uri(MUL), INST_TYPE,
                    uri(ZERO), INST_TYPE,
                    uri(ONE), INST_TYPE))
            .create();

    /**
     * {@code group_theory::T} — (op, id, inv): an operation with its identity and inverse
     */
    public static final Type GROUP_THEORY_TYPE = Type.Builder.build()
            .tid(ALGEBRAIC_THEORY_TID)
            .vid(GROUP_THEORY_TID)
            .isaPredicate(rec(
                    uri(OP), INST_TYPE,
                    uri(ID), INST_TYPE,
                    uri(INV), INST_TYPE))
            .create();

    /**
     * {@code monoid_theory::T} — (op, id): an associative operation with its identity
     */
    public static final Type MONOID_THEORY_TYPE = Type.Builder.build()
            .tid(ALGEBRAIC_THEORY_TID)
            .vid(MONOID_THEORY_TID)
            .isaPredicate(rec(
                    uri(OP), URI_TYPE,
                    uri(ID), URI_TYPE))
            .create();

    /**
     * {@code object::T} — the vertex block: the algebraic theories the object models, keyed by the
     * user's name for each theory instance (an object can model several — even several of one theory)
     */
    public static final Type OBJECT_TYPE = Type.Builder.build()
            .tid(CATEGORY_TID)
            .vid(OBJECT_TID)
            .isaPredicate(rec(
                    uri(LAW).maybe().asUri(), rec(URI_TYPE, ALGEBRAIC_THEORY)))
            .create();

    /// //////////////////////////////////////////////////////////////
    // the graph — derived from every registered inst, memoized, invalidated on registration writes

    private static volatile List<Inst> CACHED_INSTS;
    private static final Map<String, Lst> POSITION_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Lst> CONTESTED_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Lst> ORBIT_MEMO = new ConcurrentHashMap<>();
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
    public static List<Inst> insts() {
        final List<Inst> cached = CACHED_INSTS;
        if (null != cached)
            return cached;
        final List<Inst> all = new ArrayList<>();
        for (final Obj obj : Router.global().spaces().values().toList()) {
            final Space space = obj.as();
            if (!(space instanceof InstSet instSet))
                continue;
            // new-style instsets serve their registrations from the table
            all.addAll(instSet.insts());
            // old-style instsets (e.g. mInstSet) hold their insts in the jvm map under /inst —
            // read the map directly, since space.at() routes through the table-backed read
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
        if (type.isGeneric() || type.isRootType())
            return true;
        final String name = object(type).name();
        return "noobj".equals(name) || "#".equals(name);
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

    private static boolean incomparable(final Type a, final Type b) {
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
        final List<Inst> all = insts();
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
        final List<Inst> all = insts();
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
        final Lst result = witnesses.stream().map(Category::toUri).collect(new CommonUtil.LstCollector());
        CONTESTED_MEMO.put(key, result);
        return result;
    }

    /**
     * the reversible-core component of the inst's dom — everything the type can invert to and back
     */
    public static Lst orbit(final Inst inst) {
        final String key = inst.tid().toString();
        final Lst cached = ORBIT_MEMO.get(key);
        if (null != cached)
            return cached;
        final Map<fURI, Set<fURI>> reversible = reversible(graph(insts()));
        final Set<fURI> seen = new LinkedHashSet<>();
        final ArrayDeque<fURI> stack = new ArrayDeque<>();
        stack.push(object(inst.dom()));
        while (!stack.isEmpty()) {
            final fURI node = stack.pop();
            if (!seen.add(node))
                continue;
            reversible.getOrDefault(node, Set.of()).forEach(stack::push);
        }
        final Lst result = seen.stream().sorted().map(Category::toUri).collect(new CommonUtil.LstCollector());
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
        final Lst result = insts().stream()
                .filter(i -> i.tid().basePath().equals(inst.tid().basePath()))
                .map(Obj::<Obj>as)
                .collect(new CommonUtil.LstCollector());
        FAMILY_MEMO.put(key, result);
        return result;
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

    /// //////////////////////////////////////////////////////////////
    // the as-graph audit — the cast subgraph of the category

    /**
     * The cast family, minus the identity casts ({@code as?X<=X} are trivially true, not graph edges).
     */
    private static List<Inst> asInsts() {
        return Router.readFromSpace(AS_INST_TID).stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(inst -> !inst.tid().dom().equals(inst.tid().rng()))
                .sorted(Comparator.comparing(inst -> inst.tid().toString()))
                .toList();
    }

    /**
     * Audit the cast subgraph for the requested {@link Position kinds}. The former {@code ?asq} checker,
     * generalized to the category: {@code duplicate} casts, {@code ambiguous}/{@code incomparable} contesting
     * doms, and {@code coupling}/{@code isochain}/{@code retract} reversibility — each at its tightest kind,
     * with a reason.
     *
     * @param kinds the position kinds to check (empty means all)
     * @return the findings found, each as a {@link Finding}
     */
    public static Set<Finding> check(final Position... kinds) {
        final Set<Finding> findings = new HashSet<>();
        if (!Router.loaded())
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
                        } else if (incomparable(domA, domB)) {
                            final boolean sameBase = domA.tid().basePath().equals(domB.tid().basePath());
                            final boolean overlapping = sameBase && reaches(graph, group.get(i).tid().dom().basePath(), group.get(j).tid().dom().basePath());
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
        final Map<fURI, Set<fURI>> reversible = reversible(graph);
        final List<fURI> nodes = new ArrayList<>(graph.keySet());
        nodes.sort(Comparator.comparing(fURI::toString));
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final fURI a = nodes.get(i);
                final fURI b = nodes.get(j);
                final Position kind;
                if (graph.getOrDefault(a, Set.of()).contains(b) && graph.getOrDefault(b, Set.of()).contains(a))
                    kind = Position.coupling;
                else if (reaches(reversible, a, b))
                    kind = Position.isochain;
                else if (reaches(graph, a, b) && reaches(graph, b, a))
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

    private static Obj toUri(final fURI f) {
        return uri(f);
    }

    /// //////////////////////////////////////////////////////////////
    // the compute instructions — registered in mInstSet under /m/algebra
    // each takes the target inst's tid as a uri arg (a value — never applied by the resolver)
    private static Inst computeTarget(final Inst inst) {
        final Obj arg = inst.arg(0);
        if (!arg.isUri())
            throw MTronException.of("compute arg is not a uri: %s", arg);
        final fURI tid = arg.uriValue();
        final Obj target = Router.readFromSpace(tid);
        if (target.isNoObj())
            throw MTronException.of("unable to resolve compute target: %s", tid);
        return target.asInst();
    }

    public final class CategoryType {

        public static Set<Inst> insts() {
            return Set.of(
                    docWrap(instC(COMPUTE_POSITION_TID.dom(ALL.maybe()).rng(LST_TID), lst(T(URI_TID)),
                                    (lhs, inst) -> {
                                        final Inst target = computeTarget(inst);
                                        return null == target ? noobj() : Category.position(target);
                                    }),
                            "the position labels of the inst in the category",
                            "compute_position(<plus?int<=int>)  [-- [retract, incomparable] --]"),
                    docWrap(instC(COMPUTE_CONTESTED_TID.dom(ALL.maybe()).rng(LST_TID), lst(T(URI_TID)),
                                    (lhs, inst) -> {
                                        final Inst target = computeTarget(inst);
                                        return null == target ? noobj() : Category.contested(target);
                                    }),
                            "the contesting siblings behind the ambiguous/incomparable labels",
                            "compute_contested(plus?int<=int)  [-- the edges whose doms contend with int --]"),
                    docWrap(instC(COMPUTE_ORBIT_TID.dom(ALL.maybe()).rng(LST_TID), lst(T(URI_TID)),
                                    (lhs, inst) -> {
                                        final Inst target = computeTarget(inst);
                                        return null == target ? noobj() : Category.orbit(target);
                                    }),
                            "the reversible-core component of the inst's dom",
                            "compute_orbit(plus?int<=int)  [-- the types int can invert to and back --]"),
                    docWrap(instC(COMPUTE_FAMILY_TID.dom(ALL.maybe()).rng(LST_TID), lst(T(URI_TID)),
                                    (lhs, inst) -> {
                                        final Inst target = computeTarget(inst);
                                        return null == target ? noobj() : Category.family(target);
                                    }),
                            "the same-op sibling registrations of the inst",
                            "compute_family(plus?int<=int)  [-- every plus registration --]"));
        }
    }
}
