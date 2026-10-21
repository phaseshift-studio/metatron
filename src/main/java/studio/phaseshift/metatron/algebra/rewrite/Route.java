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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Route — a target plus the {@link Span spans} assembled onto it. Each route contributes one chain to the
 * emitted layout and a {@code to(...)} that routes its result: {@code [chain, chain...] == [to(), to(a), ...]}.
 * The target is where the interesting variation lives, not a {@code master}/{@code slave} flag:
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
 * <p>A Route is a {@code rec} — {@code [target => uri(target), span => lst(spanRecs)]}, with {@code url}
 * carrying the peer/space address and {@code hops} the tree budget when present. Every accessor reads
 * {@code this.at(key)}; there are no Java fields.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Route extends MRec {

    public enum Target {
        LOCAL,
        PEER,
        PEERS,
        SPACE,
        TREE,
        SHUFFLE,
        NONE
    }

    public Route(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Route local(final Span... spans) {
        return of(Target.LOCAL, null, 0, spans);
    }

    public static Route peer(final fURI address, final Span... spans) {
        return of(Target.PEER, address, 0, spans);
    }

    public static Route peers(final Span... spans) {
        return of(Target.PEERS, null, 0, spans);
    }

    public static Route shuffle(final Span... spans) {
        return of(Target.SHUFFLE, null, 0, spans);
    }

    public static Route space(final fURI address, final Span... spans) {
        return of(Target.SPACE, address, 0, spans);
    }

    public static Route tree(final int hops, final Span... spans) {
        return of(Target.TREE, null, hops, spans);
    }

    /**
     * Nowhere — the isolated span is deleted (the unit law's transform: cut the identity, route to nothing).
     */
    public static Route none(final Span... spans) {
        return of(Target.NONE, null, 0, spans);
    }

    private static Route of(final Target target, final fURI address, final int hops, final Span... spans) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(TARGET), uri(target.name().toLowerCase()));
        if (null != address)
            jvm.put(uri(URL), uri(address));
        if (0 != hops)
            jvm.put(uri(HOPS), jnt(hops));
        jvm.put(uri(SPAN), lst(Arrays.stream(spans).map(s -> (Obj) s).toList()));
        return new Route(jvm, REC_TID, null);
    }

    public Target target() {
        return Target.valueOf(this.at(TARGET).uriValue().name().toUpperCase());
    }

    /**
     * The concrete peer/space address, or null for the addressless targets ({@code local/peers/tree}).
     */
    public fURI address() {
        final Obj address = this.at(URL);
        return address.isNoObj() ? null : address.uriValue();
    }

    /**
     * The tree-sharding hop budget, or 0 for the non-tree targets.
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
     * Reconstruct a Route from a raw {@code rec} — the read-side of the same {@code target/url/hops/span} encoding.
     */
    public static Route of(final Rec rec) {
        return new Route(new LinkedHashMap<>(rec.jvm()), rec.tid(), rec.vid());
    }
}
