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
import studio.phaseshift.metatron.isa.m.math.cat.Places;
import studio.phaseshift.metatron.isa.m.math.cat.Signatures;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.LOCAL;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PlaceResolver — <em>the one place resolver</em>: a {@link PlaceSet} and an {@link Env}, answered as the ordered list
 * of places a distribution's shares may land on, each with the demand it carries. This is §7 of
 * {@code distributed-computing-qparams.md} as code, and it is the seam the emitter reads instead of
 * {@code Machine.current()} (C4 of {@code distributed-qparams-implementation.md}).
 *
 * <p><b>The seven steps, and where each one lives:</b>
 *
 * <ol>
 *   <li><b>Exclusions first</b> — {@link #resolve}: a designator whose demand is exactly zero ({@code local=>0}, the
 *       {@code rec} spelling, or a leading {@code {0}} read back as {@code cInt.ZERO()}) is expanded <em>before</em>
 *       anything else and its places are subtracted from every other designator's answer, so neither an enum nor the
 *       wildcard can resurrect a place an exclusion removed.</li>
 *   <li><b>Expand</b> — {@link #named}: the one place a designator names. {@code local} → this machine's own place
 *       ({@link #self}: {@code Places.self()}'s reading, taken from the snapshot's authorities); {@code peer} → the
 *       dialed set ({@link #dialed}); {@code #} → every remaining reachable place ({@link #reachable}); an address →
 *       the transports that host answers at ({@link #address}); a spelled authority → itself ({@link #authority}). A
 *       demand applies <em>per resolved place</em>, so {@code peer{2}} is each peer asking two shares.</li>
 *   <li><b>Specificity, not summation</b> — {@link #resolve}'s three passes and {@link #specificity}: an explicit
 *       address is resolved before an enum, and an enum before the pattern, and each pass <em>claims</em> the places it
 *       answered. So {@code [{2}//ginger:8555,#]} gives ginger exactly 2 and the pattern the rest — never 3. (The
 *       counted spelling is the <em>leading</em> one: a trailing coefficient has no projection onto the obj's
 *       {@code c}, measured.) <em>Within</em> a pass, two designators of the same form that name the same place
 *       <em>sum</em> their demands on it, which is the brace-free spelling of the same weight:
 *       {@code [//ginger,//ginger,//booger]} is ginger{2}, booger{1}. Specificity decides <em>across</em> forms;
 *       a duplicate of one form is a weight, not a second place.</li>
 *   <li><b>Reach, never knowledge</b> — {@link #reachable}: the wildcard's universe is the dialed set ∪ the local
 *       place, never {@link Env#machines() the roster}. The roster is what this machine <em>knows</em>; shipping there
 *       is the accepted-twin failure. Knowledge is reachable only when the author names it, which is exactly what an
 *       explicit {@code //host} address is.</li>
 *   <li><b>No frame paths</b> — {@link #address} selects on {@link fURI#host()} alone, and {@link #origin} strips the
 *       path and the query off a spelled authority. A place is a host and its authority; {@code //host/a/b} names the
 *       same place as {@code //host}, and the port is not a selector either ({@code //host:9999} resolves through the
 *       host's transports — {@code Places.place}'s own reading).</li>
 *   <li><b>Liveness</b> — {@link #dialed}: a place is a candidate only when {@link Places#serves} says so — the client
 *       is <em>online</em> ∧ declares the instructions the route requires (a shipped shard travels as
 *       {@code eval(<authority>)}, so {@code eval}). An offline peer is dropped (a shard shipped down it answers
 *       {@code not connected} and poisons the reduction) and so is a client that cannot {@code eval}. A {@code //host}
 *       row the machine only <em>knows</em> is admitted by {@link Signatures#resolves} instead — the declared half of
 *       the liveness test — because there is no dialed client to ask.</li>
 *   <li><b>Clamp</b> — the final loop of {@link #resolve}: zero demands are dropped, and when nothing would ship the
 *       answer is empty and the caller declines (as the emitter always did, by returning the code unchanged). The data
 *       clamp — {@code min(Σ demands, seed.size())} — is the emitter's, which is the only place that holds the
 *       seed.</li>
 * </ol>
 *
 * <p><b>The resolution is a function of the {@link Env}.</b> It reads the snapshot's peer map, roster and authority set
 * and nothing else off the machine; {@link Places} is consulted for its <em>predicates</em> ({@code serves}) and
 * {@link Signatures} for the declared liveness of a scheme, both of which are reads of an obj and the ISA rather than
 * of the network. So the same place set resolves over the same <em>membership and reach</em> twice off one
 * {@code Env} — but a member's liveness is read <em>when it is asked</em> ({@code Places.serves} reads the client's own
 * status), so a peer that goes offline between the snapshot and the resolution is dropped, exactly as it always was.
 * That is what makes a sweep over synthetic peers — no dial, no forked peer — the same question the emitter asks.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PlaceResolver {

    /**
     * What a shipped route requires of its target: the distributing shape's own {@code inst} — {@code eval}, because
     * the route carries a program the far side must apply ({@link Placement.Shape#inst()}). A caller with a richer
     * requirement (a plan that adds one) passes it to the three-argument {@link #resolve}.
     */
    private static final Set<fURI> SHIPPED_INST = Placement.Shape.TREE.inst();

    private PlaceResolver() {
        // static only
    }

    /**
     * ONE PLACE A DECISION MAY LAND ON: the wire {@code authority} the share travels to, and the {@code demand} the
     * designator stated for it. A {@code null} authority is the <em>local</em> place — the one that ships nothing,
     * because the code already stands there (the addressless {@code eval()} the layout reads as a lane at distance
     * zero, {@code DistributedSweep.Lane}).
     *
     * @param authority where the share travels, or {@code null} for the local place
     * @param demand    the shares this place is asked for
     */
    public record ResolvedPlace(fURI authority, cInt demand) {

        public ResolvedPlace {
            Objects.requireNonNull(demand, "a resolved place states a demand");
        }

        /**
         * Whether this is the place the code already stands on rather than a place it ships to.
         */
        public boolean isLocal() {
            return null == this.authority;
        }

        /**
         * The place as written for a lane: {@code local}, or the authority.
         */
        public String name() {
            return this.isLocal() ? LOCAL : this.authority.toString();
        }

        @Override
        public String toString() {
            return this.name() + "{" + this.demand + "}";
        }
    }

    /**
     * Resolve {@code places} against {@code env} for a shipped route — the demand being what a distributing shape
     * requires of its target ({@code eval}).
     */
    public static List<ResolvedPlace> resolve(final PlaceSet places, final Env env) {
        return resolve(places, env, SHIPPED_INST);
    }

    /**
     * The seven steps over one snapshot: the ordered places, each with the demand the designator gave it, empty when
     * nothing would ship (the caller's decline). The order is the order the designators were <em>written</em> — so
     * {@code [local,peer]} answers the local place first, the design's S order — with each designator's own places in
     * their source order (the peer map's, the roster's-and-dials' canonical sort, or the local place alone). A
     * duplicate designator of one form joins the place it duplicates, its demand added to that place's (see step 3),
     * so the answer keeps one entry per resolved place — and the emitter, which owns the lane order, is what repeats
     * it into one lane per share.
     *
     * @param places the place set a placement's {@code place} axis holds
     * @param env    the frozen environment ({@code Env.snapshot()} or {@link Env#of})
     * @param inst   what a target must serve to take a share ({@link Placement#inst()})
     */
    public static List<ResolvedPlace> resolve(final PlaceSet places, final Env env, final Set<fURI> inst) {
        if (null == places || places.isEmpty())
            return List.of();
        final Placement requires = requires(inst);
        final List<PlaceSet.Designator> designators = places.designators();
        // STEP 1 — EXCLUSIONS FIRST: a zero demand is expanded before anything else and subtracted from every other
        // designator's answer, so neither an enum nor the wildcard can resurrect the place it removed
        final Set<fURI> excluded = new LinkedHashSet<>();
        for (final PlaceSet.Designator designator : designators)
            if (designator.demand().isZero())
                excluded.addAll(named(designator, env, requires));
        // STEP 3 — SPECIFICITY, NOT SUMMATION: explicit address, then enum, then pattern, and each PASS claims what it
        // answered, so a less specific designator cannot count a place twice. Inside a pass the written order decides,
        // and two designators of the SAME form that name the same place SUM their demands on it (step 3's other half):
        // `[//ginger,//ginger,//booger]` is ginger{2}, booger{1} — the brace-free spelling of the weight a leading
        // coefficient states (`[{2}//ginger,//booger]`), which is the same 2:1 bias over three lanes.
        final Set<fURI> claimed = new LinkedHashSet<>();
        final List<List<ResolvedPlace>> resolved = new ArrayList<>(designators.size());
        for (int i = 0; i < designators.size(); i++)
            resolved.add(new ArrayList<>());
        for (int pass = 0; 3 > pass; pass++) {
            // what the MORE SPECIFIC passes claimed: a place a narrower designator named is not this pass's to count
            final Set<fURI> claimedEarlier = new LinkedHashSet<>(claimed);
            // where each authority landed IN THIS PASS, so a duplicate designator accumulates instead of being starved
            final Map<fURI, int[]> landed = new LinkedHashMap<>();
            final Set<fURI> answered = new LinkedHashSet<>();
            for (int i = 0; i < designators.size(); i++) {
                final PlaceSet.Designator designator = designators.get(i);
                if (specificity(designator.form()) != pass || designator.demand().isZero())
                    continue;
                final List<fURI> named = named(designator, env, requires);
                for (final fURI authority : named) {
                    if (excluded.contains(authority) || claimedEarlier.contains(authority))
                        continue;
                    final int[] at = landed.get(authority);
                    if (null == at) {
                        resolved.get(i).add(new ResolvedPlace(authority, designator.demand()));
                        landed.put(authority, new int[]{i, resolved.get(i).size() - 1});
                    } else if (at[0] == i) {
                        // a REPEATED PLACE INSIDE ONE DESIGNATOR is two places, not one: two dials of one service are
                        // two lanes, exactly as they always were
                        resolved.get(i).add(new ResolvedPlace(authority, designator.demand()));
                    } else {
                        // a DUPLICATE DESIGNATOR of the same form: one place, the demands summed on it
                        final List<ResolvedPlace> owner = resolved.get(at[0]);
                        final ResolvedPlace place = owner.get(at[1]);
                        owner.set(at[1], new ResolvedPlace(authority, place.demand().plus(designator.demand())));
                    }
                }
                answered.addAll(named);
            }
            claimed.addAll(answered);
        }
        // STEP 7 — CLAMP: an answer that names no place is the empty answer (the decline). Zero demands never reach
        // here: step 1 expanded them and step 3 skipped their designators, so this is the concatenation in written
        // order and nothing else. The data clamp (min of Σ demands and the seed) belongs to the emitter, which holds
        // the seed.
        final List<ResolvedPlace> answer = new ArrayList<>();
        for (final List<ResolvedPlace> designator : resolved)
            answer.addAll(designator);
        return List.copyOf(answer);
    }

    /**
     * The places one designator names, in its own order — step 1's input and step 3's answer. A designator with no
     * candidate (the dialed set with nothing dialed, an unknown host, a silent machine's local place) names nothing.
     */
    private static List<fURI> named(final PlaceSet.Designator designator, final Env env, final Placement requires) {
        return switch (designator.form()) {
            case LOCAL -> self(env);
            case PEER -> dialed(env, requires);
            case ANY -> reachable(env, requires);
            case ADDRESS -> address(designator.uri(), env, requires);
            case AUTHORITY -> authority(designator.uri());
        };
    }

    /**
     * The specificity class of a designator form: {@code 0} explicit (an address or a spelled authority), {@code 1}
     * enum ({@code local}, {@code peer}), {@code 2} pattern ({@code #}). The design's "explicit address beats an enum
     * beats a pattern".
     */
    private static int specificity(final PlaceSet.Form form) {
        return switch (form) {
            case ADDRESS, AUTHORITY -> 0;
            case LOCAL, PEER -> 1;
            case ANY -> 2;
        };
    }

    /**
     * {@code local} — this frame's own place, as {@link Places#self()} reads it: the transports the machine answers on,
     * and <b>no place on a silent machine</b> (a program there is not placeable, so there is nothing to be a place
     * <em>of</em>). Read off the snapshot: the empty authority set is exactly {@code Places.self()}'s {@code noobj}
     * test, so the resolution stays a function of the {@code Env}. The place itself is the {@code null} authority —
     * the lane at distance zero, never a wire address.
     */
    private static List<fURI> self(final Env env) {
        return env.authorities().isEmpty() ? List.of() : Collections.singletonList(null);
    }

    /**
     * {@code #} — every remaining <em>reachable</em> place: the dialed set ∪ the local place, in that order. The roster
     * is deliberately absent (step 4): reach is what this machine holds, not what it has heard about, and a shard
     * shipped to a known-but-unreachable machine is the accepted-twin failure the dialed set exists to prevent.
     *
     * <p><b>This is NOT {@link Places#reachable()}.</b> That reading is <em>roster ∪ dialed</em> — it answers "which
     * places does this machine know of", which is the right question for introspection and the wrong one for shipping
     * code; this one answers "which places can a share travel to", so the roster may not contribute. The two are
     * deliberately different and neither should be "fixed" into the other.
     *
     * <p>The order is the resolver's written order — the dials as the peer map holds them, then the local place — and
     * it is not the <em>lane</em> order: the emitter canonicalises lanes (the home place's shares first) because it must
     * do so for {@code [peer,local]} as well, where the design's S order does not hold either.
     */
    private static List<fURI> reachable(final Env env, final Placement requires) {
        final List<fURI> reachable = new ArrayList<>(dialed(env, requires));
        reachable.addAll(self(env));
        return reachable;
    }

    /**
     * {@code peer} — THE DIALED SET, one place per dialed peer that can take a share, in the peer map's own order (the
     * order the emitted routes have always had; the emitter canonicalises the lanes it emits, home shares first, which
     * is its rule and not this one's): a peer is a target when it carries a {@code host} (where to dial back from
     * here — an inbound accept carries none, being a client we cannot ship to) and {@link Places#serves} it (online ∧
     * declaring the route's {@code inst}). The authority is the peer's {@code origin}: the wire address it answers at,
     * never its key, which is a local path.
     * <p>
     * No dedupe: two dials of one service are two lanes, which is what the pre-resolver filter chain answered and what
     * the emitter's stride expects.
     */
    private static List<fURI> dialed(final Env env, final Placement requires) {
        return env.peers().values()
                .filter(peer -> peer.isRec() && peer.asRec().at(uri(HOST)).isUri())
                .filter(peer -> Places.serves(peer, requires))
                .map(Network.Helper::originOf)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * {@code //host[:port]} — the addressing redirect, read as {@code Places.place} reads it: the union of the two
     * tables for that host, deduped by transport and sorted (the canonical order {@code Places} gives an authority
     * set). The <em>host</em> is the selector: a path in the designator names no frame and the port names no socket
     * ({@code //ginger:9999} resolves through ginger's transports), so a place is an authority, never a frame path.
     * <p>
     * The two tables are gated by the half of liveness each one can answer: a dialed peer by {@link Places#serves}
     * (there is a client to ask), a roster row by {@link Signatures#resolves} (there is none — the declared half of the
     * test is all a machine this one only knows can be asked for).
     */
    private static List<fURI> address(final fURI address, final Env env, final Placement requires) {
        final String host = address.host();
        if (null == host)
            return List.of();
        final List<fURI> transports = new ArrayList<>();
        // the roster (known): the addresses discovery announced for the host, each admitted only if it can be dialed
        env.machines().elements()
                .filter(kv -> kv.first().isUri() && host.equals(kv.first().uriValue().toString()))
                .map(Rel::second)
                .forEach(reach -> reach.stream()
                        .filter(Obj::isUri)
                        .map(Obj::uriValue)
                        // a transport with no scheme names no dialer at all, so it is no place to ship to
                        .filter(transport -> null != transport.scheme())
                        .filter(transport -> Signatures.resolves(transport.scheme()))
                        .forEach(transports::add));
        // the peer map (realized): the authorities this machine actually dialed to that host, if it serves the route.
        // A DIALED PEER, NOT EVERY PEER — the same `host` marker dialed() keeps: an accepted inbound session serves
        // everything a shard needs (Places.serves says yes), but it has no host to dial back to, so its origin is an
        // ephemeral port nothing answers at.
        env.peers().values()
                .filter(peer -> peer.isRec() && peer.asRec().at(uri(HOST)).isUri())
                .filter(peer -> Places.serves(peer, requires))
                .map(Network.Helper::originOf)
                .filter(Objects::nonNull)
                .filter(origin -> host.equals(origin.host()))
                .forEach(transports::add);
        return transports.stream()
                .map(fURI::basePath)
                .distinct()
                .sorted(Comparator.comparing(fURI::toString))
                .toList();
    }

    /**
     * {@code scheme://host:port} — one authority, as written, so it is the author's own transport that is shipped to
     * and not a redirect through the two tables. Only the liveness declaration is read: a scheme whose dialer does not
     * resolve in this machine's ISA ({@link Signatures#resolves}) is no place at all, the same refusal
     * {@code relocate} makes of a move's target. The path and the query are normalised away — an authority names a
     * process boundary, never a place in a tree (step 5).
     */
    private static List<fURI> authority(final fURI authority) {
        if (!Signatures.resolves(authority.scheme()))
            return List.of();
        return List.of(origin(authority));
    }

    /**
     * The authority a written place names: scheme, host and port, with the coefficient, the path and the query gone.
     */
    private static fURI origin(final fURI place) {
        return place.qLess().path(List.of()).basePath();
    }

    /**
     * The requirement a target must satisfy, in the one form {@link Places#serves} reads it — a placement carrying the
     * {@code inst} set. The decision is not resolved here: this placement is a value handed to a predicate, never
     * emitted.
     */
    private static Placement requires(final Set<fURI> inst) {
        return Placement.peers().inst(null == inst ? new fURI[0] : inst.toArray(fURI[]::new));
    }
}
