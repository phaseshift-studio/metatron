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
 * Placement — a shape plus the {@link Span spans} assembled onto it. Each placement contributes one chain to the
 * emitted layout and a {@code to(...)} that routes its result: {@code [chain, chain...] == [to(), to(a), ...]}.
 * The shape is where the interesting variation lives, not a {@code master}/{@code slave} flag:
 *
 * <ul>
 *   <li>{@link #local()} — stay home; the master gathers the partials ({@code merge()}, the barrier near-ring
 *       {@code F_b}) and re-applies the {@code F_r} reducer through its combiner.</li>
 *   <li>{@link #peers()} — the live roster, one {@code to(peer)} per peer (the fan-out the seed bulk factors over).</li>
 *   <li>{@link #peer(fURI)} — ship to one peer ({@code to(ws://...)}).</li>
 *   <li>{@link #space(fURI)} — a durable result ({@code to(space://...)}).</li>
 *   <li>{@link #tree(int)} — recursive tree-sharding with a hop budget.</li>
 * </ul>
 *
 * <p>A Placement is a {@code rec} — {@code [shape => uri(shape), target => uri(address)?, hops => int?,
 * span => lst(spanRecs), inst => lst(uri)?]}. {@code shape} is the topology token, {@code target} the concrete
 * peer/space address, {@code hops} the tree budget when present, and {@code inst} the instructions the target must
 * serve (the key mirrors a client's {@code inst}). The shape carries its own {@link #obligations()} (§3) — the laws a
 * cut shipped to it and re-applied at home must declare — and its own {@link #inst()} — what its target must serve.
 * Every accessor reads {@code this.at(key)}; there are no Java fields.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Placement extends MRec {

    /**
     * The topology of a relocation decision — what {@code target} named before the shape coordinate landed. Each
     * shape declares the laws it demands of a cut shipped to it (§3); a shape with none demands nothing.
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

    public static Placement tree(final int hops, final Span... spans) {
        return of(Shape.TREE, null, hops, spans);
    }

    /**
     * Nowhere — the isolated span is deleted (the unit law's transform: cut the identity, route to nothing).
     */
    public static Placement none(final Span... spans) {
        return of(Shape.NONE, null, 0, spans);
    }

    private static Placement of(final Shape shape, final fURI address, final int hops, final Span... spans) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(SHAPE), uri(shape.name().toLowerCase()));
        if (null != address)
            jvm.put(uri(TARGET), uri(address));
        if (0 != hops)
            jvm.put(uri(HOPS), jnt(hops));
        jvm.put(uri(SPAN), lst(Arrays.stream(spans).map(s -> (Obj) s).toList()));
        // what the target must serve, written into the rec so the plan says it: the shape's own requirement
        if (!shape.inst().isEmpty())
            jvm.put(uri(INST), lst(shape.inst().stream().map(tid -> (Obj) uri(tid)).toList()));
        return new Placement(jvm, REC_TID, null);
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
     * The topology token — {@code local/peer/peers/space/tree/shuffle/none}.
     */
    public Shape shape() {
        return Shape.valueOf(this.at(SHAPE).uriValue().name().toUpperCase());
    }

    /**
     * The concrete peer/space address, or null for the addressless shapes ({@code local/peers/tree}).
     */
    public fURI target() {
        final Obj target = this.at(TARGET);
        return target.isNoObj() ? null : target.uriValue();
    }

    /**
     * The laws this placement's shape demands — the forward traversal (§3), read off the shape.
     */
    public Set<Law> obligations() {
        return this.shape().obligations();
    }

    /**
     * The tree-sharding hop budget, or 0 for the non-tree shapes.
     */
    public int hops() {
        final Obj hops = this.at(HOPS);
        return hops.isNoObj() ? 0 : hops.asInt().intValue().intValue();
    }

    public List<Span> spans() {
        final Obj spans = this.at(SPAN);
        return spans.isNoObj() ? List.of() : spans.asLst().elements().map(o -> Span.of(o.asRec())).toList();
    }

    /**
     * Reconstruct a Placement from a raw {@code rec} — the read-side of the same {@code shape/target/hops/span}
     * encoding, migrating the old {@code target/url} keys for one cycle.
     */
    public static Placement of(final Rec rec) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(rec.jvm());
        // compat: the pre-shape encoding held the topology under "target" and the address under "url"
        if (jvm.containsKey(uri(TARGET)) && !jvm.containsKey(uri(SHAPE)))
            jvm.put(uri(SHAPE), jvm.remove(uri(TARGET)));
        if (jvm.containsKey(uri(URL)) && !jvm.containsKey(uri(TARGET)))
            jvm.put(uri(TARGET), jvm.remove(uri(URL)));
        return new Placement(jvm, rec.tid(), rec.vid());
    }
}
