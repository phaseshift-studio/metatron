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

package studio.phaseshift.metatron.algebra.rewrite;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Placement — <em>the distribution decision</em>: where a cut's shares go, how deep the application tree may run, and
 * what counts as one share. Three axes, one decision:
 *
 * <ul>
 *   <li>{@link #place()} — a {@link PlaceSet}: the designators named and the demand each carries (§2);</li>
 *   <li>{@link #hopAxis()} — a {@link Hop}: the tree's height and its arity (§3);</li>
 *   <li>{@link #split()} — a {@link Partition}: the partition function and the part count (§4).</li>
 * </ul>
 *
 * <p>Each placement contributes one chain to the emitted layout and a {@code to(...)} that routes its result:
 * {@code [chain, chain...] == [to(), to(a), ...]}. The rec is
 * {@code [place => (designator | lst | rec), hop => {arity}height, split => {parts}kind, span => lst(spanRecs),
 * inst => lst(uri)?]} — a hop whose arity has no coefficient spelling ({@code 0}, {@code 1}) is the pair
 * {@code [height, arity]}, and a split at its default count is the bare kind.
 *
 * <p><b>The topology enum is a read-compat projection.</b> {@link Shape} was three decisions in one enum; it survives
 * for one cycle as the <em>recorded</em> spelling of the same decision — {@link #shape()} projects it from the axes for
 * every member the axes can reproduce, and {@link #target()} and {@link #hop()} are read off the place set and the hop
 * axis. A rec that still states the enum (every plan in the tree today) is migrated into the axes on the way in, by
 * {@link #of(Rec)} and the <em>one</em> table {@link #legacy}, so a reader never branches on which spelling it got.
 *
 * <p><b>The obligations are derived, not declared</b> ({@link #obligations()}): a decision whose shares are
 * <em>regrouped out of order</em> must declare {@code monoidic} (partials regroup) and — unless the merge is
 * sequential — {@code commutative} (order-free). A keyed partition folds per bucket; a one-child hop is sequential; a
 * single home place regroups nothing. The clause is {@link PlaceSet#regroups()}, not "does the set leave home": one
 * home lane is the local fold, but several home lanes ({@code [local{4}]}) are concurrent partials gathered after the
 * fact. The enum's declared sets are the refactor's witness ({@code ObligationsTest}), with one recorded exception:
 * {@link Shape#SPACE}, a <em>landing</em> rather than a fan-out, which the axes do not encode yet.
 *
 * <p>{@code local} means two things and they stay apart: the origin's <em>tail</em> decision ({@code local} as the
 * whole place set, spans AT/POST — {@code merge().<combiner>.<tail>} runs at home) regroups nothing, so it demands
 * nothing; {@code local} as <em>one designator of a fan-out</em> ({@code [local, peer]}) is the master taking a share,
 * and the decision still ships. {@link PlaceSet#regroups()} is the predicate that separates them, and
 * {@link PlanBuilder#where} refuses to bind a regrouping place set to a placement with no seed span — the tail can
 * never silently become a lane.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Placement extends MRec {

    /**
     * The recorded topology of a relocation decision — the enum the axes replace, kept as the read-compat spelling for
     * one cycle. Each member's declared laws are the obligations the <em>derivation</em> must reproduce
     * ({@link #obligations()} derives them; {@code ObligationsTest} is the witness).
     */
    public enum Shape {
        /** stay home — no regrouping, no reordering, no duplication */
        LOCAL,
        /** ship to one peer — a one-slave shard whose cut is recombined */
        PEER(Law.monoidic, Law.commutative),
        /** fan out over the roster — order-free merge across a set */
        PEERS(Law.monoidic, Law.commutative),
        /** a durable result — write to a space, no combiner */
        SPACE,
        /** recursive tree-sharding with a hop budget */
        TREE(Law.monoidic, Law.commutative),
        /** key-routed fan-out — monoidic only; the keyed law is the fold's derivation */
        SHUFFLE(Law.monoidic),
        /** sequential forwards — ship the whole code to one place (a {@code move} hop) */
        CHAIN(Law.monoidic),
        /** fan-out over the roster's places — each hop bound by the origin */
        STAR(Law.monoidic, Law.commutative),
        /** nowhere — the isolated span is deleted */
        NONE;

        private final Set<Law> obligations;

        Shape(final Law... obligations) {
            this.obligations = Set.of(obligations);
        }

        /**
         * The laws this shape demands — the forward traversal (§3): {@code obligations(shape)}. A cut shipped to this
         * shape and re-applied at home must declare every law here.
         */
        public Set<Law> obligations() {
            return this.obligations;
        }

        /**
         * The shapes a law set admits — the reverse traversal (§3): {@code admissible(laws) = { shape :
         * obligations(shape) ⊆ laws }}.
         */
        public static List<Shape> admissible(final Set<Law> laws) {
            return Arrays.stream(values())
                    .filter(shape -> laws.containsAll(shape.obligations))
                    .toList();
        }

        /**
         * Whether this shape ships code to a peer — the distributed shapes. {@code local}/{@code none}/{@code space}
         * stay on the machine that holds them.
         */
        public boolean distributes() {
            return PEER == this || PEERS == this || TREE == this || SHUFFLE == this || CHAIN == this || STAR == this;
        }

        /**
         * The instructions a target of this shape must SERVE — the second axis beside {@link #obligations()}: what the
         * shape demands of the place it ships to, not of the cut. Every distributing shape ships its chain through
         * {@code eval(<target>)} (each route contributes one chain and an {@code eval()}), so its target's client must
         * declare {@code eval} in its {@code inst} — the route carries a PROGRAM the target must apply, and {@code eval}
         * is exactly "apply this program and answer its value". An http POST that only writes (no eval) runs nothing,
         * so a peer that cannot evaluate is no place to ship code. A shape that stays home demands nothing.
         */
        public Set<fURI> inst() {
            // the route is what applies its cargo, so the plan prints exactly what the target must serve
            return this.distributes() ? Collections.unmodifiableSet(new LinkedHashSet<>(List.of(EVAL_INST_TID))) : Set.of();
        }
    }

    /**
     * The three axes of one decision — what the shape→axes table answers, and what the builder writes.
     */
    private record Axes(PlaceSet place, Hop hop, Partition split) {
    }

    public Placement(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Placement local(final Span... spans) {
        return of(Shape.LOCAL, null, 0, spans);
    }

    public static Placement peer(final fURI address, final Span... spans) {
        return of(Shape.PEER, address, 0, spans);
    }

    public static Placement peers(final Span... spans) {
        return of(Shape.PEERS, null, 0, spans);
    }

    public static Placement shuffle(final Span... spans) {
        return of(Shape.SHUFFLE, null, 0, spans);
    }

    /**
     * A {@code move} hop — ship the whole code to one place, sequentially.
     */
    public static Placement chain(final fURI address, final Span... spans) {
        return of(Shape.CHAIN, address, 0, spans);
    }

    /**
     * A {@code move} fan-out — ship the whole code to every place the origin reaches.
     */
    public static Placement star(final fURI address, final Span... spans) {
        return of(Shape.STAR, address, 0, spans);
    }

    public static Placement space(final fURI address, final Span... spans) {
        return of(Shape.SPACE, address, 0, spans);
    }

    public static Placement tree(final int hop, final Span... spans) {
        return of(Shape.TREE, null, hop, spans);
    }

    /**
     * Nowhere — the isolated span is deleted (the unit law's transform: cut the identity, route to nothing).
     */
    public static Placement none(final Span... spans) {
        return of(Shape.NONE, null, 0, spans);
    }

    private static Placement of(final Shape shape, final fURI address, final int hop, final Span... spans) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(SHAPE), uri(shape.name().toLowerCase()));
        if (null != address)
            jvm.put(uri(TARGET), uri(address));
        if (0 != hop)
            jvm.put(uri(HOP), jnt(hop));
        jvm.put(uri(SPAN), lst(Arrays.stream(spans).map(s -> (Obj) s).toList()));
        // what the target must serve, written into the rec so the plan says it: the shape's own requirement
        if (!shape.inst().isEmpty())
            jvm.put(uri(INST), lst(shape.inst().stream().map(tid -> (Obj) uri(tid)).toList()));
        return new Placement(jvm, REC_TID, null);
    }

    /**
     * A decision stated in the axes — what {@link PlanBuilder#place}, {@link PlanBuilder#hop} and
     * {@link PlanBuilder#split} bind to a placement's spans, and the ONLY encoding the plans write now (C9). The
     * derived enum token and the address are NOT written: the shape is a consequence of the axes
     * ({@link #shapeOf}) and the target is the place set's sole address ({@link #target}), so a rec that stated
     * them too could contradict itself. The read side still accepts the legacy encoding for one cycle
     * ({@link #of(Rec)}), and {@link #shape()} reads a recorded token only where the projection coarsens.
     */
    public static Placement of(final PlaceSet place, final Hop hop, final Partition split, final Span... spans) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(PLACE), place.toObj());
        jvm.put(uri(HOP), hop.toObj());
        jvm.put(uri(SPLIT), split.toObj());
        jvm.put(uri(SPAN), lst(Arrays.stream(spans).map(s -> (Obj) s).toList()));
        // what the target must serve is still read off the projected shape, and written, so the plan says it
        final Shape shape = shapeOf(place, hop, split);
        if (!shape.inst().isEmpty())
            jvm.put(uri(INST), lst(shape.inst().stream().map(tid -> (Obj) uri(tid)).toList()));
        return new Placement(jvm, REC_TID, null);
    }

    /**
     * This placement's decision replaced by {@code place}/{@code hop}/{@code split}, keeping its spans and whatever
     * {@code inst} it declared — what a builder binds pending axes to.
     */
    public Placement axes(final PlaceSet place, final Hop hop, final Partition split) {
        final Placement rebuilt = of(place, hop, split, this.spans().toArray(Span[]::new));
        final Obj declared = this.at(INST);
        return declared.isLst()
                ? rebuilt.inst(declared.asLst().elements().filter(Obj::isUri).map(Obj::uriValue).toArray(fURI[]::new))
                : rebuilt;
    }

    /**
     * The instructions this placement's target must serve — the shape's own requirement ({@link Shape#inst()}) plus
     * whatever the placement adds for what its shipped chunk needs. Read from the rec, so a placement written in mtron
     * without an {@code inst} still requires what its shape ships through. A target serves them when its client's
     * {@code inst} — the mini-instset the key mirrors — declares an override for each.
     */
    public Set<fURI> inst() {
        final Set<fURI> required = new LinkedHashSet<>(this.shape().inst());
        final Obj declared = this.at(INST);
        if (declared.isLst())
            declared.asLst().elements().filter(Obj::isUri).map(Obj::uriValue).forEach(required::add);
        return required;
    }

    /**
     * This placement, requiring {@code instTIDs} of its target as well — for a plan whose shipped chunk calls a peer
     * instruction beyond the one its shape ships through.
     */
    public Placement inst(final fURI... instTIDs) {
        final Set<fURI> required = this.inst();
        required.addAll(Arrays.asList(instTIDs));
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(this.jvm());
        jvm.put(uri(INST), lst(required.stream().map(tid -> (Obj) uri(tid)).toList()));
        return new Placement(jvm, this.tid(), this.vid());
    }

    /**
     * THE ONE TABLE — the recorded shape to the axes it stands for (§1 of the design: the enum <em>is</em> three
     * decisions). This is the whole migration, read in both directions: {@link #of(Rec)} writes these axes over a rec
     * that stated only the enum, and {@link #shape()} inverts them for a rec that states only the axes. Read it as the
     * design's own table:
     *
     * <ul>
     *   <li>{@code local} — the home place, no depth;</li>
     *   <li>{@code none} — the empty place set (nowhere), no depth;</li>
     *   <li>{@code peer}/{@code star}/{@code space} — one named address at no stated depth; the peer and the star are
     *       the same decision here (what separates them is the cut, which a placement does not hold), so the axes
     *       answer one of them and a recorded token keeps the other — see {@link #shape()};</li>
     *   <li>{@code chain} — one named address on a one-child hop, which is the design's {@code ?hop=n{1}}: a
     *       sequential forward, so the merge preserves order and the decision demands {@code monoidic} alone;</li>
     *   <li>{@code peers} — the dialed set at no stated depth, the depth-1 fan-out being the tree's radius one;</li>
     *   <li>{@code tree} — the dialed set at the budget the rec stated;</li>
     *   <li>{@code shuffle} — the dialed set, one level, keyed.</li>
     * </ul>
     *
     * <p>Every row is exact for the <em>obligations</em> except {@code space}, whose declared set stands because the
     * axes cannot say "landing" (the recorded exception, {@link #obligations()}). The <em>shape</em> projection is
     * exact for every row but the peer/star collapse ({@link #shapeOf}), which is why a rec that records the enum keeps
     * answering it.
     */
    private static Axes legacy(final Shape shape, final fURI address, final int hop) {
        final PlaceSet places = null == address ? PlaceSet.peer() : PlaceSet.of(address);
        return switch (shape) {
            case LOCAL -> new Axes(PlaceSet.local(), Hop.point(), Partition.stream());
            case NONE -> new Axes(PlaceSet.none(), Hop.point(), Partition.stream());
            case PEER -> new Axes(places, Hop.point(), Partition.stream());
            case PEERS -> new Axes(PlaceSet.peer(), Hop.point(), Partition.stream());
            case SPACE -> new Axes(places, Hop.point(), Partition.stream());
            case TREE -> new Axes(PlaceSet.peer(), Hop.tree(hop), Partition.stream());
            case SHUFFLE -> new Axes(PlaceSet.peer(), Hop.tree(1), Partition.key());
            case CHAIN -> new Axes(places, Hop.chain(hop), Partition.stream());
            case STAR -> new Axes(places, Hop.point(), Partition.stream());
        };
    }

    /**
     * The inverse of {@link #legacy} — the recorded enum a decision's axes stand for, which is what {@link #shape()}
     * projects for a rec written in the axes alone. Exact for every member the table inverts, with two coarsenings
     * that are the axes' own richness and are named rather than forced:
     *
     * <ul>
     *   <li>a single named address at no depth is both the {@code peer} (a one-slave shard) and the {@code star} (a
     *       move fan-out) — what separates them is the cut, which a placement does not hold — so the projection answers
     *       {@code peer};</li>
     *   <li>a landing ({@code space}) is not a fan-out at all and has no axis spelling yet (the design defers it), so
     *       it projects as the address it names; the recorded token keeps it.</li>
     * </ul>
     *
     * <p>A named set of several addresses has no enum member at all — the axes are strictly richer there — so it is
     * projected as the fan-out of the same place set.
     */
    private static Shape shapeOf(final PlaceSet place, final Hop hop, final Partition split) {
        if (place.isEmpty())
            return Shape.NONE;
        if (!place.regroups())
            return Shape.LOCAL;
        if (Partition.Kind.KEY == split.kind())
            return Shape.SHUFFLE;
        if (hop.sequential())
            return Shape.CHAIN;
        final boolean dialed = place.designators().stream()
                .anyMatch(designator -> PlaceSet.Form.PEER == designator.form() || PlaceSet.Form.ANY == designator.form());
        if (dialed)
            return hop.isPoint() ? Shape.PEERS : Shape.TREE;
        final boolean named = null != place.soleAddress();
        if (named)
            return hop.isPoint() ? Shape.PEER : Shape.STAR;
        return hop.isPoint() ? Shape.PEERS : Shape.TREE;
    }

    /**
     * The place axis — where the decision's shares may go. Read from the {@code place} key when the rec states one;
     * otherwise from the recorded enum, through the one table ({@link #legacy}).
     */
    public PlaceSet place() {
        final Obj place = this.at(PLACE);
        return place.isNoObj()
                ? legacy(this.recordedShape(), this.recordedTarget(), this.recordedHop()).place()
                : PlaceSet.of(place);
    }

    /**
     * The depth axis — the application tree's height and arity. Read from the {@code hop} key when the rec states one
     * (a counted int: the value is the height, the coefficient the arity); otherwise from the recorded enum.
     */
    public Hop hopAxis() {
        final Obj hop = this.at(HOP);
        return this.speaksAxes() && !hop.isNoObj()
                ? Hop.of(hop)
                : legacy(this.recordedShape(), this.recordedTarget(), this.recordedHop()).hop();
    }

    /**
     * The partition axis — what counts as one share. Read from the {@code split} key when the rec states one (a
     * counted uri: the name is the kind, the coefficient the part count); otherwise from the recorded enum.
     */
    public Partition split() {
        final Obj split = this.at(SPLIT);
        return this.speaksAxes() && !split.isNoObj()
                ? Partition.of(split)
                : legacy(this.recordedShape(), this.recordedTarget(), this.recordedHop()).split();
    }

    /**
     * The members the axes COARSEN — where a placement cannot name what a rec recorded: one named address at no depth
     * is the {@code peer} (a one-slave shard), the {@code star} (a move fan-out) or a {@code space} (a landing), and
     * what separates them is the cut, which a placement does not hold.
     */
    private static final Set<Shape> COARSENED = Set.of(Shape.PEER, Shape.STAR, Shape.SPACE);

    /**
     * The recorded topology token — the read-compat spelling of the decision, and all that is left of the enum.
     * <b>The axes have precedence:</b> the decision is projected through {@link #shapeOf}, and the recorded token
     * answers only where the projection coarsens to {@code peer} and the token is one of the members the axes cannot
     * tell apart ({@link #COARSENED}). So a rec stating both can never contradict itself — the case the old
     * token-first order allowed, {@code shape()==TREE} with {@code obligations()=={}}, is unreachable — while every
     * legacy member still answers its recorded token. {@code plan::T} keeps requiring that token for one cycle (C9
     * drops it).
     */
    public Shape shape() {
        final PlaceSet place = this.place();
        final Hop hop = this.hopAxis();
        final Shape projected = shapeOf(place, hop, this.split());
        final Obj recorded = this.at(SHAPE);
        if (recorded.isNoObj())
            return projected;
        final Shape token = Shape.valueOf(recorded.uriValue().name().toUpperCase());
        return Shape.PEER == projected && COARSENED.contains(token) ? token : projected;
    }

    /**
     * The concrete peer/space address, or null when the decision names no single place (the dialed set, the wildcard,
     * the home place, several places). Read off the place set: one explicit address is the target.
     */
    public fURI target() {
        return this.place().soleAddress();
    }

    /**
     * The laws this placement's decision demands — the forward traversal (§3), DERIVED from the axes (§1 of the
     * plan): a cut re-applied behind an off-machine recombine demands {@code monoidic} (partials regroup) and, unless
     * the merge is sequential, {@code commutative} (order-free); a keyed partition folds per bucket, so
     * {@code monoidic} alone; a decision whose shares are never regrouped (one home place, the empty set) demands
     * nothing.
     */
    public Set<Law> obligations() {
        // THE ONE RECORDED EXCEPTION: a landing is not a fan-out. A space write ships a result to a durable store and
        // nothing comes back to recombine, so the cut is never re-applied off-machine — but the axes cannot say
        // "landing" (the design defers SPACE past them), so its recorded declared set stands. It is inert: no plan in
        // the tree carries a space placement.
        if (Shape.SPACE == this.shape())
            return Shape.SPACE.obligations();
        return obligations(this.place(), this.hopAxis(), this.split());
    }

    /**
     * The obligations rule itself, read straight off the axes — the derivation the enum is proved against.
     */
    public static Set<Law> obligations(final PlaceSet place, final Hop hop, final Partition split) {
        // THE CLAUSE IS "IS ANYTHING REGROUPED OUT OF ORDER?", not "does the set leave home": one home lane is the
        // local fold, but four home lanes are four concurrent partials gathered after the fact, and a stream split
        // permutes them — so k > 1 demands the off-machine merge's laws exactly as a fan-out does (`[local{4}]`, the
        // design's peerless-lanes case). Only a set that neither leaves home nor asks for a second lane demands nothing.
        if (!place.regroups())
            return Set.of();
        if (Partition.Kind.KEY == split.kind())
            return Set.of(Law.monoidic);
        if (hop.sequential())
            return Set.of(Law.monoidic);
        return Set.of(Law.monoidic, Law.commutative);
    }

    /**
     * The pre-singular spelling of the {@link studio.phaseshift.metatron.Tokens#HOP hop} key — a read-only alias for
     * one cycle: this class migrates it on the way in ({@link #of(Rec)}) and never writes it, and
     * {@link PlanInterpreter#hop} reads it off a chain.
     */
    static final String HOP_ALIAS = "hops";

    /**
     * The tree-sharding hop budget, or 0 for a decision that states no depth — the pre-axes int accessor, now the
     * height of the hop axis. The emitter reads the whole axis ({@link PlanInterpreter#hopAxis}) and this accessor
     * answers where only the height is needed (whether a fold is planned at all).
     */
    public int hop() {
        return this.hopAxis().height();
    }

    public List<Span> spans() {
        final Obj spans = this.at(SPAN);
        return spans.isNoObj() ? List.of() : spans.asLst().elements().map(o -> Span.of(o.asRec())).toList();
    }

    /**
     * Whether the rec SPEAKS THE AXES — it states a {@code place}, which is exactly what {@link #migrate} writes, and
     * what a plan written in the axes states. Until it does, a {@code hop} key is the pre-axes budget of the recorded
     * enum's row (a bare int whose arity is unstated, not one child), so the table — not the key — is the answer.
     */
    private boolean speaksAxes() {
        return !this.at(PLACE).isNoObj();
    }

    private Shape recordedShape() {
        return Shape.valueOf(this.at(SHAPE).uriValue().name().toUpperCase());
    }

    private fURI recordedTarget() {
        final Obj target = this.at(TARGET);
        return target.isNoObj() ? null : target.uriValue();
    }

    private int recordedHop() {
        final Obj hop = this.at(HOP);
        return hop.isNoObj() ? 0 : hop.asInt().intValue().intValue();
    }

    /**
     * Reconstruct a Placement from a raw {@code rec} — the read-side of the same encoding, and THE migration point:
     * the old {@code target/url} keys and the pre-singular {@code hops} key are folded in, and then a rec that states
     * only the recorded enum is rewritten into the axes by the one table ({@link #legacy}). The legacy keys are kept —
     * for one cycle they are the read-compat spelling a plan may still be written in.
     */
    public static Placement of(final Rec rec) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(rec.jvm());
        // compat: the pre-shape encoding held the topology under "target" and the address under "url"
        if (jvm.containsKey(uri(TARGET)) && !jvm.containsKey(uri(SHAPE)))
            jvm.put(uri(SHAPE), jvm.remove(uri(TARGET)));
        if (jvm.containsKey(uri(URL)) && !jvm.containsKey(uri(TARGET)))
            jvm.put(uri(TARGET), jvm.remove(uri(URL)));
        // compat: the pre-singular hop budget
        if (jvm.containsKey(uri(HOP_ALIAS)) && !jvm.containsKey(uri(HOP)))
            jvm.put(uri(HOP), jvm.remove(uri(HOP_ALIAS)));
        migrate(jvm);
        return new Placement(jvm, rec.tid(), rec.vid());
    }

    /**
     * THE MIGRATION — a rec that states the recorded enum and no axes is rewritten into the axes by the one table
     * ({@link #legacy}). A rec that already states a {@code place} speaks the axes and is left alone; so is a rec with
     * no recorded decision (the spans alone).
     */
    private static void migrate(final Map<Obj, Obj> jvm) {
        if (jvm.containsKey(uri(PLACE)))
            return;
        final Obj recorded = jvm.get(uri(SHAPE));
        if (null == recorded || !recorded.isUri())
            return;
        final Shape shape = Shape.valueOf(recorded.uriValue().name().toUpperCase());
        final Obj address = jvm.get(uri(TARGET));
        final Obj hop = jvm.get(uri(HOP));
        final Axes axes = legacy(shape,
                null == address || !address.isUri() ? null : address.uriValue(),
                null == hop || !hop.isInt() ? 0 : hop.asInt().intValue().intValue());
        jvm.put(uri(PLACE), axes.place().toObj());
        jvm.put(uri(HOP), axes.hop().toObj());
        jvm.put(uri(SPLIT), axes.split().toObj());
    }
}
