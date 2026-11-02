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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.math.cat.Places;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE ONE PLACE RESOLVER — {@link PlaceResolver}'s seven steps (block S2 of
 * {@code distributed-qparams-implementation.md}: <em>"specificity, exclusion, wildcard, reach-not-knowledge,
 * offline/no-eval drops"</em>).
 *
 * <p><b>Hermetic by construction.</b> Every row resolves a {@link PlaceSet} against a synthetic {@link Env} —
 * {@link Env#of} over hand-built peer and roster tables — so there is no dial, no listener and no forked peer: the
 * sweep is a pure function answering in microseconds, which is exactly the seam C4 exists to open (the emitter is a
 * function of {@code (plan, code, env)}). The one exception is {@link #testThePeerPlaceIsThePreC4TargetList()}, which
 * writes the same four peers into the <em>live</em> map so the resolver's answer can be compared, verbatim, with the
 * filter chain it replaced: that is the "same targets" witness, and it dials nothing either.
 *
 * <p><b>The synthetic universe</b> is four dialed peers — the one that takes a route, and the three ways a peer fails to
 * be a target — plus a machine this one only <em>knows</em> and the machine's own listen authority:
 *
 * <table>
 *   <caption>the synthetic dials</caption>
 *   <tr><td>ginger</td><td>online, declares {@code eval}</td><td>the one target</td></tr>
 *   <tr><td>booger</td><td>online, declares {@code to} but not {@code eval}</td><td>dropped: it would store the
 *       program, not run it</td></tr>
 *   <tr><td>quiet</td><td>serves {@code eval}, offline</td><td>dropped: a shard down it answers {@code not
 *       connected}</td></tr>
 *   <tr><td>twin</td><td>online, serves {@code eval}, no {@code host}</td><td>never a target: an accepted inbound
 *       twin is a client</td></tr>
 *   <tr><td>nutmeg</td><td>a roster row only in the rich universe; a second good dial in the 0/1/2 rows</td>
 *       <td>known, not reachable — unless the author names it</td></tr>
 *   <tr><td>mantis</td><td>this machine's own listen authority</td><td>the local place</td></tr>
 * </table>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class PlaceResolverTest extends AbstractMetatronTest {

    /**
     * Online, declares {@code eval}, carries a {@code host} — a target.
     */
    private static final fURI SERVANT = f("loop://ginger:8555");
    /**
     * Online, declares {@code to} but not {@code eval} — no target: the route is {@code eval(<target>)}.
     */
    private static final fURI WRITER = f("loop://booger:8555");
    /**
     * Serves {@code eval} but its link is down — no target.
     */
    private static final fURI SLEEPER = f("loop://quiet:8555");
    /**
     * Serves {@code eval} but carries no {@code host} — an accepted twin, never a target.
     */
    private static final fURI TWIN = f("loop://twin:8555");
    /**
     * A second good peer, used to prove order and the 0/1/2 dial counts.
     */
    private static final fURI SECOND = f("loop://nutmeg:8555");
    /**
     * This machine's own authority — what makes the local place a place.
     */
    private static final fURI MINE = f("loop://mantis:8555");

    /**
     * A roster row for a machine this one knows and has not dialed: nutmeg, at its one transport.
     */
    private static final Rec ROSTER = rec(uri("nutmeg"), objs(uri(SECOND)));

    @BeforeAll
    public static void importAllInstSets() {
        // the wildcard import is what declares the schemes' dialers (ws_client, id_client), which is the declared half
        // of the liveness test a spelled authority and a roster transport are admitted by
        InstSet.importInstSet(f("#"));
    }

    /**
     * THE DESIGNATOR FORMS, one row each, over the rich universe — the whole of §2's resolution table as data. Each
     * row: the place set as written % its resolution, {@code authority{demand}} per place, {@code local} for the home
     * place, in answer order.
     *
     * <p>What the rows pin, one rule at a time:
     * <ul>
     *   <li><b>expand</b> — {@code local} alone, {@code peer} alone, and their union in the design's S order
     *       ({@code local} first: the master is a place too);</li>
     *   <li><b>reach, never knowledge</b> — {@code #} is the dialed set ∪ the local place and <em>not</em> nutmeg,
     *       which this machine merely knows; but {@code //nutmeg} — the author naming it — does resolve, through the
     *       roster, since an explicit address is the one way knowledge becomes a candidate;</li>
     *   <li><b>an accepted twin is no place by address either</b> — {@code //twin} names a machine this one holds an
     *       inbound session with, and that is no place to ship to: the twin has no host to dial back to, so its origin
     *       is an ephemeral port nothing answers at (the same rule {@code peer} keeps, and the reason the address
     *       branch keeps it too);</li>
     *   <li><b>no frame paths, and the port is not a selector</b> — {@code //ginger/frame/path} and
     *       {@code //ginger:9999} both answer ginger's own transport, because a place is a host and its authority
     *       ({@code Places.place} reads the same way);</li>
     *   <li><b>a spelled authority is normalised to its origin</b> — {@code ws://ginger:8555/mtron} and
     *       {@code ws://ginger:8555/mtron?clientq=builder} answer {@code ws://ginger:8555}, and the counted
     *       {@code {2}ws://ginger:8555/p} answers two of it: the path and the query are the shipped code's business,
     *       never a target's;</li>
     *   <li><b>liveness</b> — an unknown host is no place, and a spelled authority whose scheme has no dialer
     *       ({@code nope://}) is no place either, while a dialable one is itself as written;</li>
     *   <li><b>specificity, not summation</b> — {@code [{2}//ginger:8555,#]} gives ginger exactly two and the pattern
     *       the rest (the local place), never three;</li>
     *   <li><b>exclusions first</b> — a zero demand removes the local place before expansion, so neither {@code peer}
     *       nor {@code #} can resurrect a home lane;</li>
     * </ul>
     *
     * <p><b>The counted spelling is the LEADING one</b>, and the sweep writes it that way because that is what the
     * object grammar reads: a trailing coefficient has no projection onto the obj's {@code c} — measured here as
     * {@code //ginger{2} => c = 1} — while {@code {2}//ginger:8555 => c = 2}. The design records the same measurement
     * for a path-only designator; it holds for an addressed one too, so a counted designator is written
     * {@code {n}designator} and a zero demand in the {@code rec} spelling ({@code local=>0}), which is the only
     * spelling of a zero mtron can carry at all (a zero coefficient collapses to {@code noobj}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "peer                       % loop://ginger:8555{1}",
            "local                      % local{1}",
            "'[local,peer]'             % local{1}, loop://ginger:8555{1}",
            "'#'                        % loop://ginger:8555{1}, local{1}",
            "[]                         % ",
            "//ginger:8555              % loop://ginger:8555{1}",
            "//ginger:9999              % loop://ginger:8555{1}",
            "//ginger/frame/path        % loop://ginger:8555{1}",
            "//nutmeg                   % loop://nutmeg:8555{1}",
            "//nobody:8555              % ",
            "//twin                     % ",
            "ws://ginger:8555           % ws://ginger:8555{1}",
            "ws://ginger:8555/mtron     % ws://ginger:8555{1}",
            "ws://ginger:8555/mtron?clientq=builder % ws://ginger:8555{1}",
            "'{2}ws://ginger:8555/p'    % ws://ginger:8555{2}",
            "nope://ginger:8555         % ",
            "'[{2}//ginger:8555,#]'    % loop://ginger:8555{2}, local{1}",
            "'[local=>0,peer=>1]'      % loop://ginger:8555{1}",
            "'[local=>0,#=>1]'         % loop://ginger:8555{1}",
            "'[//ginger,//ginger]'     % loop://ginger:8555{2}",
            "'[//ginger,//ginger,#]'   % loop://ginger:8555{2}, local{1}",
    }, delimiter = '%')
    public void testThePlaceSetResolves(final String written, final String expected) {
        assertEquals(null == expected ? "" : expected.trim(), rendered(PlaceResolver.resolve(places(written), universe())),
                "resolving " + written);
    }

    /**
     * THE DIALED COUNT AND ORDER — {@code peer} over none, one, and two dialed peers. Nothing dialed is nothing to ship
     * to (the empty answer, which the emitter reads as the decline); one peer is one lane; two are two lanes
     * <em>in the peer map's order</em>, which is the order the emitted routes have always had. Each row: the dials (in
     * dial order) % the place set % the answer.
     *
     * <p><b>The dial order is deliberately NOT alphabetical</b> — nutmeg before ginger — because a row whose dials were
     * already in {@code toString} order could not tell the map's order from a sort, and the routes the emitter has
     * always emitted followed the map's order.
     *
     * <p><b>{@code [{2}peer]} is resolved over TWO peers on purpose</b>: over one peer a demand of two shares
     * <em>per place</em> and a total of two shares are the same answer, so only two peers can tell them apart. The
     * rows answer {@code {2}} on both places, which is the design's "the coefficient applies per resolved place".
     */
    @ParameterizedTest
    @CsvSource(value = {
            "none             % peer        % ",
            "ginger           % peer        % loop://ginger:8555{1}",
            "nutmeg,ginger    % peer        % loop://nutmeg:8555{1}, loop://ginger:8555{1}",
            "ginger,nutmeg    % '[{2}peer]' % loop://ginger:8555{2}, loop://nutmeg:8555{2}",
    }, delimiter = '%')
    public void testTheDialedSetIsThePeerPlace(final String dial, final String written, final String expected) {
        assertEquals(null == expected ? "" : expected.trim(),
                rendered(PlaceResolver.resolve(places(written), env(Set.of(MINE), ROSTER, dialed(dial)))),
                written + " over " + dial);
    }

    /**
     * THE THREE WAYS A DIALED PEER IS NOT A TARGET, and the fourth that is — resolved as {@code peer} and as the
     * wildcard, because the same liveness gate must hold in both: a peer whose client cannot {@code eval} (online is
     * not enough: the route applies a program, it does not post a value), a peer whose link is down (a shard down it
     * answers {@code not connected} and poisons the reduction), and a peer with no {@code host} (the accepted twin: the
     * far end of the socket is a client this machine cannot ship to). Each row: the peer % the place set % the answer.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ginger  % peer  % loop://ginger:8555{1}",
            "ginger  % #     % loop://ginger:8555{1}, local{1}",
            "booger  % peer  % ",
            "booger  % #     % local{1}",
            "quiet   % peer  % ",
            "quiet   % #     % local{1}",
            "twin    % peer  % ",
            "twin    % #     % local{1}",
    }, delimiter = '%')
    public void testAPeerThatCannotTakeTheRouteIsDropped(final String peer, final String written, final String expected) {
        final Env env = env(Set.of(MINE), ROSTER, dialed(peer));
        assertEquals(null == expected ? "" : expected.trim(), rendered(PlaceResolver.resolve(places(written), env)),
                written + " over " + peer);
    }

    /**
     * THE EXCLUSION'S OTHER SPELLING — a zero demand read as a value, not as text. The design writes it
     * {@code [{0}local,peer]}; mtron's own grammar collapses a zero coefficient to {@code noobj} (measured, see
     * {@link #testALeadingZeroCollapsesToNoObj()}), so a place set read from source spells the zero the {@code rec}
     * way — {@code [local=>0,peer=>1]}. The RULE is the demand, not the spelling: a designator read with an explicit
     * {@link cInt#ZERO()} is the same exclusion.
     */
    @Test
    public void testAZeroDemandIsAnExclusionWhateverItsSpelling() {
        assertEquals("", rendered(PlaceResolver.resolve(PlaceSet.of(f("local"), cInt.ZERO()), universe())),
                "a zero demand is no lane");
        assertEquals("", rendered(PlaceResolver.resolve(PlaceSet.of(f("peer"), cInt.ZERO()), universe())),
                "and no peer either");
        assertEquals("loop://ginger:8555{1}",
                rendered(PlaceResolver.resolve(places("[local=>0,peer=>1]"), universe())),
                "the weighted spelling removes the local place before expansion");
    }

    /**
     * AND THE DESIGN'S OWN ZERO SPELLING DOES NOT SURVIVE THE PARSER — measured here so the row above is not read as
     * an arbitrary choice: {@code {0}local} is {@code noobj} (a zero coefficient is no object at all), so a place set
     * read from source cannot carry a leading zero, and a list whose member collapsed to {@code noobj} names no
     * designator ({@link PlaceSet} refuses it). The {@code rec} spelling is therefore the only textual one, and it
     * states the same zero demand the leading coefficient would.
     */
    @Test
    public void testALeadingZeroCollapsesToNoObj() {
        assertTrue(PlaceSet.of(ObjmtronSerializer.parse("{0}local")).isEmpty(),
                "a zero coefficient is noobj, so the set it names is empty rather than an exclusion");
        assertThrows(MTronException.class, () -> PlaceSet.of(ObjmtronSerializer.parse("[{0}local,peer]")),
                "a list memo that collapsed to noobj names no designator -- the rec spelling is the one that reads");
    }

    /**
     * A SILENT MACHINE HAS NO LOCAL PLACE — {@code Places.self()} is {@code noobj} when nothing listens (a program on a
     * silent machine is not placeable), so {@code local} resolves to nothing and {@code #} resolves to the dials alone.
     * The rule lives in the snapshot: the empty authority set <em>is</em> the test.
     */
    @Test
    public void testASilentMachineHasNoLocalPlace() {
        final Env silent = env(Set.of(), ROSTER, dialed("ginger"));
        assertEquals("", rendered(PlaceResolver.resolve(places("local"), silent)), "nothing listens: no local place");
        assertEquals("loop://ginger:8555{1}", rendered(PlaceResolver.resolve(places("#"), silent)),
                "the wildcard's universe is the dials when there is no home place");
    }

    /**
     * THE ENV IS THE WHOLE INPUT. The factory freezes the three tables (a peer or an authority added to the caller's
     * tables afterwards does not reach the snapshot), and two envs of the same tables carry the same
     * {@link Env#fingerprint()}, which is the comparison a later "re-plan iff the fingerprint differs" reads.
     */
    @Test
    public void testTheFactoryFreezesTheThreeTables() {
        final Rec peers = rec(uri("dial/one"), peer(SERVANT, true, true, EVAL_INST_TID));
        final Set<fURI> authorities = new LinkedHashSet<>(List.of(MINE));
        final Env env = Env.of(peers, ROSTER, authorities);
        peers.at(uri("dial/two"), peer(SECOND, true, true, EVAL_INST_TID), MUTABLE);
        authorities.add(f("loop://elsewhere:8555"));
        assertEquals(1L, env.peers().elements().count(), "the dial set is frozen: " + env.peers());
        assertEquals(Set.of(MINE), env.authorities(), "the authority set is frozen: " + env.authorities());
        assertEquals("loop://ginger:8555{1}", rendered(PlaceResolver.resolve(places("peer"), env)),
                "and the resolution reads the frozen tables, not the caller's");
        final Env same = Env.of(rec(uri("dial/one"), peer(SERVANT, true, true, EVAL_INST_TID)), ROSTER, Set.of(MINE));
        assertEquals(env.fingerprint(), same.fingerprint(), "the same tables fingerprint the same");
    }

    /**
     * THE DECISION IS UNTOUCHED (the chunk's "no production behaviour change" clause, from the model's side):
     * resolution reads a placement's place set and leaves it alone — the same designators, the same obligations — so
     * the emitter's refactor is a refactor. Each row: the recorded distributing shape % the laws its axes demand.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "peer   % commutative,monoidic",
            "peers  % commutative,monoidic",
            "tree   % commutative,monoidic",
    }, delimiter = '%')
    public void testResolutionLeavesTheDecisionAlone(final String shape, final String obligations) {
        final Placement placement = switch (shape) {
            case "peer" -> Placement.peer(SERVANT, Span.at());
            case "peers" -> Placement.peers(Span.at());
            case "tree" -> Placement.tree(1, Span.at());
            default -> throw new IllegalArgumentException(shape);
        };
        final PlaceSet places = placement.place();
        final List<PlaceResolver.ResolvedPlace> resolved = PlaceResolver.resolve(places, universe(), placement.inst());
        assertFalse(resolved.isEmpty(), "a distributing decision resolves over the synthetic dials: " + shape);
        assertEquals(laws(obligations), placement.obligations(), "obligations(" + shape + ")");
        assertEquals(Placement.Shape.valueOf(shape.toUpperCase()).obligations(), placement.obligations(),
                "the axes still reproduce the recorded enum's obligations: " + shape);
    }

    /**
     * THE SAME TARGETS — the "byte-identical" witness, read off the <em>live</em> peer map (the same four members the
     * sweep uses, written where a dial would write them; nothing is dialed). {@link #legacyTargets} is the pre-C4 filter
     * chain kept verbatim: the resolver's {@code peer} answer must be that list, in that order — the one target, with
     * the no-{@code eval}, offline and no-{@code host} peers dropped, and the empty case when nothing serves.
     */
    @Test
    public void testThePeerPlaceIsThePreC4TargetList() {
        final Network network = Machine.current().network();
        final Placement route = Placement.tree(1, Span.at());
        // the live peer field is process-global, so this test owns it while it runs and hands back exactly what it
        // found -- not the empty field, which would be a second, quieter change to the machine
        final Obj prior = network.at(uri(PEER));
        try {
            network.at(uri(PEER), rec(
                    uri("dial/one"), peer(SERVANT, true, true, EVAL_INST_TID),
                    uri("dial/two"), peer(WRITER, true, true, TO_INST_TID),
                    uri("dial/three"), peer(SLEEPER, true, false, EVAL_INST_TID),
                    uri("dial/four"), peer(TWIN, false, true, EVAL_INST_TID)), MUTABLE);
            final Env env = Env.snapshot();
            assertEquals(legacyTargets(env, route), targets(env, route),
                    "the resolver answers the filter chain it replaced: " + env.peers());
            assertEquals(List.of(SERVANT), targets(env, route), "the one dialed peer that serves the route");
            // the empty case: nothing dialed, nothing shipped
            network.at(uri(PEER), noobj(), MUTABLE);
            assertEquals(List.of(), legacyTargets(Env.snapshot(), route), "nothing dialed, nothing to ship to");
            assertEquals(List.of(), targets(Env.snapshot(), route), "and the resolver agrees");
        } finally {
            network.at(uri(PEER), prior, MUTABLE);
        }
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// THE SYNTHETIC UNIVERSE
    /// /////////////////////////////////////////////////////////////////////////////////////

    /**
     * The rich universe the designator rows resolve against: the four dialed peers in their defined order, nutmeg known
     * only through the roster, and mantis as this machine's own listen authority.
     */
    private static Env universe() {
        return env(Set.of(MINE), ROSTER,
                peer(SERVANT, true, true, EVAL_INST_TID),
                peer(WRITER, true, true, TO_INST_TID),
                peer(SLEEPER, true, false, EVAL_INST_TID),
                peer(TWIN, false, true, EVAL_INST_TID));
    }

    /**
     * An environment from three tables — {@link Env#of}'s factory, which is the whole point of the heredity: the
     * resolver below reads this and nothing off the machine.
     */
    private static Env env(final Set<fURI> authorities, final Rec machines, final Obj... peers) {
        final Map<Obj, Obj> dials = new LinkedHashMap<>();
        for (int i = 0; i < peers.length; i++)
            dials.put(uri("dial/" + i), peers[i]);
        return Env.of(rec(dials), machines, authorities);
    }

    /**
     * The synthetic peers a row names, in the order it names them.
     */
    private static Obj[] dialed(final String spec) {
        if (null == spec || spec.isBlank() || "none".equals(spec.trim()))
            return new Obj[0];
        return Arrays.stream(spec.split(","))
                .map(String::trim)
                .map(PlaceResolverTest::peer)
                .toArray(Obj[]::new);
    }

    /**
     * One synthetic peer by name — the four failure modes and the two extra places, so a row can say {@code quiet}
     * rather than spell out a rec.
     */
    private static Obj peer(final String name) {
        return switch (name) {
            case "ginger" -> peer(SERVANT, true, true, EVAL_INST_TID);
            case "nutmeg" -> peer(SECOND, true, true, EVAL_INST_TID);
            case "booger" -> peer(WRITER, true, true, TO_INST_TID);
            case "quiet" -> peer(SLEEPER, true, false, EVAL_INST_TID);
            case "twin" -> peer(TWIN, false, true, EVAL_INST_TID);
            default -> throw new IllegalArgumentException("no synthetic peer called " + name);
        };
    }

    /**
     * A dialed peer as the network writes one: its {@code origin} (the wire address), a {@code host} when it was
     * <em>dialed</em> (an inbound accept carries none), and the client it holds — the client's {@code inst} is what it
     * can be talked to with and its {@code status} is whether it is live.
     */
    private static Obj peer(final fURI origin, final boolean dialed, final boolean online, final fURI... serves) {
        final Rec overrides = rec();
        for (final fURI instTID : serves)
            overrides.jvm().put(uri(instTID), Network.Helper.override(instTID, (lhs, inst) -> inst.arg(0).apply(lhs)));
        final Map<Obj, Obj> client = new LinkedHashMap<>();
        client.put(uri(HOST), uri(origin));
        client.put(uri(INST), overrides);
        client.put(uri(STATUS), Network.Helper.statusOf(() -> online));
        final Map<Obj, Obj> member = new LinkedHashMap<>();
        member.put(uri(ORIGIN), uri(origin));
        if (dialed)
            member.put(uri(HOST), uri(origin));
        member.put(uri(CLIENT), new MRec(client, REC_TID, null));
        return new MRec(member, REC_TID, null);
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// THE ORACLE AND THE READING
    /// /////////////////////////////////////////////////////////////////////////////////////

    /**
     * THE PRE-C4 FILTER CHAIN, verbatim — the oracle {@link PlaceResolver} must agree with. It is kept here, not in
     * {@code src/main}, because the resolver replaced it: the test's job is to say the two are the same list.
     */
    private static List<fURI> legacyTargets(final Env env, final Placement route) {
        return env.peers().values()
                .filter(peer -> peer.isRec() && peer.asRec().at(uri(HOST)).isUri())
                .filter(peer -> Places.serves(peer, route))
                .map(peer -> peer.asRec().<Obj>atDirect(uri(ORIGIN)))
                .filter(Obj::isUri)
                .map(Obj::uriValue)
                .toList();
    }

    /**
     * What the emitter now reads: the resolver's answer restricted to the places a lane can ship to — the local place
     * (no authority) is not a target until C5 emits the lane at distance zero.
     */
    private static List<fURI> targets(final Env env, final Placement route) {
        return PlaceResolver.resolve(route.place(), env, route.inst()).stream()
                .filter(place -> !place.isLocal())
                .map(PlaceResolver.ResolvedPlace::authority)
                .toList();
    }

    private static PlaceSet places(final String written) {
        return PlaceSet.of(ObjmtronSerializer.parse(written));
    }

    /**
     * A resolution as {@code place{demand}}, one per line of text — the form the CSV's expected column reads.
     */
    private static String rendered(final List<PlaceResolver.ResolvedPlace> places) {
        return places.stream().map(PlaceResolver.ResolvedPlace::toString).collect(Collectors.joining(", "));
    }

    private static Set<Law> laws(final String rendered) {
        if (null == rendered || rendered.isBlank())
            return Set.of();
        return Arrays.stream(rendered.split(","))
                .map(String::trim)
                .map(Law::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
