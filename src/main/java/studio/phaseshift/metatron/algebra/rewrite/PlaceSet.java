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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static studio.phaseshift.metatron.Tokens.HASH_FURI;
import static studio.phaseshift.metatron.Tokens.LOCAL;
import static studio.phaseshift.metatron.Tokens.PEER;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PlaceSet — the {@code place} axis of a distribution decision: <em>where</em> a cut's shares may be applied, as the
 * designators named plus the demand each one carries (§2 of {@code distributed-computing-qparams.md}).
 *
 * <p>The designators, and what each resolves through once a place set is resolved against an {@code Env} (C4, not
 * this chunk):
 *
 * <ul>
 *   <li>{@link Form#LOCAL} — {@code local}: this frame's own place. The home place of a fan-out is one of these.</li>
 *   <li>{@link Form#PEER} — {@code peer}: the <em>dialed</em> set, host-bearing only.</li>
 *   <li>{@link Form#ANY} — {@code #}: every remaining reachable place.</li>
 *   <li>{@link Form#ADDRESS} — {@code //host[:port]}: the addressing redirect (roster ∪ dials).</li>
 *   <li>{@link Form#AUTHORITY} — {@code scheme://host:port}: one authority, as written.</li>
 * </ul>
 *
 * <p>The demand is the coefficient the designator carries — the share it asks for ({@code {2}//abc:45} asks two of
 * abc) — and it is a {@link cInt} so a band ({@code {2,5}}) is a value, not a syntax. Five forms, one coefficient
 * reading (§5).
 *
 * <p>The value's <em>type</em> decides how it is written (§2): a {@code uri} is one designator, a {@code lst} is a
 * set of uniform shares, a {@code rec} is the weighted form ({@code designator => count}). {@link #toObj()} writes the
 * canonical spelling for a set: the uniform set as a {@code lst}, the weighted one as a {@code rec} (the only form
 * that can carry a zero demand — a zero coefficient collapses to {@code noobj} in mtron, so
 * {@code {0}//abc:45} is no object at all).
 *
 * <p>This is a Java value, not a space object: it is what a {@link Placement} carries in its {@code place} key. Two
 * predicates earn it: {@link #distributes()} (does the decision leave the machine) and {@link #regroups()} (are the
 * shares gathered out of order — anything off-machine, or more than one home lane), which is the clause the
 * obligations derivation reads.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PlaceSet {

    /**
     * The designator forms (§2). The form, not the string, is what resolution reads: an {@code //host} is a redirect
     * through the roster while a {@code scheme://host:port} is an authority as written.
     */
    public enum Form {
        /** {@code local} — this frame's place; a place set holding only these ships nothing */
        LOCAL,
        /** {@code peer} — the dialed set */
        PEER,
        /** {@code #} — every remaining reachable place */
        ANY,
        /** {@code //host[:port]} — the addressing redirect */
        ADDRESS,
        /** {@code scheme://host:port} — one authority as written */
        AUTHORITY
    }

    /**
     * One designator and the share it demands. The uri is the designator <em>as written</em> (its coefficient is the
     * demand, read back by {@link #demand()}); equality is over both.
     */
    public record Designator(Form form, fURI uri, cInt demand) {

        public Designator {
            Objects.requireNonNull(form, "a designator has a form");
            Objects.requireNonNull(uri, "a designator has an address");
            Objects.requireNonNull(demand, "a designator states a demand");
        }

        /** Whether this designator is the place the code already stands on. */
        public boolean isLocal() {
            return Form.LOCAL == this.form;
        }

        /** The designator as written — the view an error message or a test reads. */
        public String name() {
            return this.uri.toString();
        }
    }

    /** The empty place set — nothing ships (the {@code none} shape's decision). */
    private static final PlaceSet NONE = new PlaceSet(List.of());

    private final List<Designator> designators;

    private PlaceSet(final List<Designator> designators) {
        this.designators = List.copyOf(designators);
    }

    /** Nowhere — the empty set, the {@code none} shape's decision. */
    public static PlaceSet none() {
        return NONE;
    }

    /** {@code [local]} — this frame's own place. */
    public static PlaceSet local() {
        return of(uri(LOCAL));
    }

    /** {@code [peer]} — the dialed set, one uniform share each. */
    public static PlaceSet peer() {
        return of(uri(PEER));
    }

    /** {@code [#]} — every remaining reachable place. */
    public static PlaceSet any() {
        return of(uri(HASH_FURI));
    }

    /**
     * A set of addresses, each demanding one share — the {@code [//a, //b:45]} form.
     */
    public static PlaceSet of(final fURI... addresses) {
        final List<Designator> designators = new ArrayList<>(addresses.length);
        for (final fURI address : addresses)
            designators.add(designator(address, cInt.ONE()));
        return designators.isEmpty() ? NONE : new PlaceSet(designators);
    }

    /**
     * One address demanding {@code demand} shares.
     */
    public static PlaceSet of(final fURI address, final cInt demand) {
        return new PlaceSet(List.of(designator(address, demand)));
    }

    /**
     * Read a place set from the value a {@code place} key holds: a {@code uri} is one designator, a {@code lst} a set
     * of uniform (or coefficient-stated) shares, a {@code rec} the weighted form. A {@code noobj} — the key absent —
     * is the empty set, never the default (the caller decides what an unstated place means).
     */
    public static PlaceSet of(final Obj value) {
        if (value.isNoObj())
            return NONE;
        if (value.isUri())
            return of(value.uriValue(), value.c());
        if (value.isLst()) {
            final List<Designator> designators = value.asLst().elements()
                    .map(element -> {
                        if (!element.isUri())
                            throw MTronException.of("a place set's members are designators, not %s", element);
                        return designator(element.uriValue(), element.c());
                    })
                    .toList();
            return designators.isEmpty() ? NONE : new PlaceSet(designators);
        }
        if (value.isRec()) {
            final List<Designator> designators = new ArrayList<>();
            for (final Map.Entry<Obj, Obj> entry : value.asRec().jvm().entrySet()) {
                if (!entry.getKey().isUri() || !entry.getValue().isInt())
                    throw MTronException.of("a weighted place set is designator => count, not %s => %s",
                            entry.getKey(), entry.getValue());
                designators.add(designator(entry.getKey().uriValue(), cInt.of(entry.getValue().asInt().intValue())));
            }
            return designators.isEmpty() ? NONE : new PlaceSet(designators);
        }
        throw MTronException.of("a place set is a uri, a lst of designators, or a rec of designator => count: %s", value);
    }

    /**
     * Classify a designator by the form it is written in: the two value tokens ({@code local}, {@code peer}), the
     * wildcard ({@code #}), an addressed redirect ({@code //host[:port]}), or a spelled authority
     * ({@code scheme://host:port}). A designator that is none of these is refused, never silently read as local.
     */
    private static Designator designator(final fURI address, final cInt demand) {
        final String name = address.name();
        final Form form;
        if (LOCAL.equals(name))
            form = Form.LOCAL;
        else if (PEER.equals(name))
            form = Form.PEER;
        else if (HASH_FURI.name().equals(name))
            form = Form.ANY;
        else if (address.hasHost())
            form = address.hasScheme() ? Form.AUTHORITY : Form.ADDRESS;
        else
            throw MTronException.of("a place designator is local, peer, #, //host[:port] or scheme://host:port: %s", address);
        return new Designator(form, address, demand);
    }

    /** The designators, in the order they were written, each with its demand. */
    public List<Designator> designators() {
        return this.designators;
    }

    /** Whether this set names no place at all. */
    public boolean isEmpty() {
        return this.designators.isEmpty();
    }

    /** The designators that leave home — every designator that is not {@code local}. */
    public List<Designator> nonLocal() {
        return this.designators.stream().filter(designator -> !designator.isLocal()).toList();
    }

    /**
     * Whether this set ships code away from home — any non-{@code local} designator. This is the {@code place} axis'
     * own question: whether the decision leaves the machine it stands on.
     */
    public boolean distributes() {
        return !this.nonLocal().isEmpty();
    }

    /**
     * Whether this set's shares are gathered OUT OF ORDER — the predicate the obligations derivation reads first, and
     * a different question from {@link #distributes()}: anything that leaves home regroups, and so does more than one
     * <em>home lane</em>. {@code [local]} is the local fold (nothing is regrouped, the cut is re-applied in place, no
     * law is demanded), but {@code [local{4}]} is four concurrent home lanes gathered after the fact — and a
     * {@code stream} split permutes the shares anyway — so k > 1 needs the off-machine merge's laws
     * ({@code monoidic} and {@code commutative}), exactly as a fan-out does.
     */
    public boolean regroups() {
        if (!this.nonLocal().isEmpty())
            return true;
        final cInt demand = this.demand();
        // a band or an open demand could realize more than one lane, so it is a regroup until it is clamped
        return null == demand.max() || 1 < demand.max();
    }

    /**
     * The total demand this set states — the sum of its designators' demands (§5's one coefficient reading, summed).
     * {@code [local]} asks for one lane, {@code [local{4}]} for four, an empty set for none.
     */
    public cInt demand() {
        Long min = 0L;
        Long max = 0L;
        for (final Designator designator : this.designators) {
            min = (null == min || null == designator.demand().min()) ? null : min + designator.demand().min();
            max = (null == max || null == designator.demand().max()) ? null : max + designator.demand().max();
        }
        return cInt.of(min, max);
    }

    /** The designators as written — the resolved-name view a test, a log line or a refusal reads. */
    public List<String> names() {
        return this.designators.stream().map(Designator::name).toList();
    }

    /**
     * The one explicit address this set names, or null — what a {@link Placement#target()} is read off. A set of
     * several places, an empty set, and the enum/wildcard designators name no single target.
     */
    public fURI soleAddress() {
        if (1 != this.designators.size())
            return null;
        final Designator designator = this.designators.getFirst();
        return (Form.ADDRESS == designator.form() || Form.AUTHORITY == designator.form()) ? designator.uri() : null;
    }

    /**
     * The value a {@code place} key holds: the uniform set as a {@code lst} of designators, the weighted set as a
     * {@code rec} ({@code designator => count}) — the only form that can carry a zero demand, since a zero coefficient
     * collapses to {@code noobj} in mtron. A band demand has no {@code rec} spelling (a rec's count is an exact int),
     * so it is written on the designator itself, which is the form a band is read back from.
     */
    public Obj toObj() {
        if (this.designators.isEmpty())
            return lst(List.of());
        if (this.designators.stream().allMatch(designator -> designator.demand().isOne()))
            return lst(this.designators.stream().map(designator -> (Obj) uri(designator.uri())).toList());
        if (this.designators.stream().allMatch(designator -> exact(designator.demand()))) {
            final Map<Obj, Obj> weights = new LinkedHashMap<>();
            this.designators.forEach(designator -> weights.put(uri(designator.uri()), jnt(designator.demand().min())));
            return rec(weights);
        }
        return lst(this.designators.stream().map(designator -> (Obj) uri(designator.uri()).c(designator.demand())).toList());
    }

    /**
     * A set is order-free: two place sets are equal when they name the same designators with the same demands.
     */
    /**
     * Whether a demand is one number rather than a band — the {@code rec} spelling's count is an exact int.
     */
    private static boolean exact(final cInt demand) {
        return null != demand.min() && demand.min().equals(demand.max());
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof PlaceSet that && this.canonical().equals(that.canonical());
    }

    @Override
    public int hashCode() {
        return this.canonical().hashCode();
    }

    private List<String> canonical() {
        return this.designators.stream()
                .map(designator -> designator.form() + ":" + designator.uri() + ":" + designator.demand())
                .sorted()
                .toList();
    }

    @Override
    public String toString() {
        return this.designators.isEmpty() ? "[]" : "[" + String.join(",", this.names()) + "]";
    }
}
