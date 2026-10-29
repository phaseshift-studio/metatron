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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.algebra.rewrite.Placement;
import studio.phaseshift.metatron.algebra.rewrite.PlanBuilder;
import studio.phaseshift.metatron.algebra.rewrite.PlanInterpreter;
import studio.phaseshift.metatron.algebra.rewrite.Span;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.network.TransportRewriteHarness;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The geometry coordinate (§11.4, Phase 5): an abstraction's {@code where} is the α place-class word — the places the
 * code can be distributed TO, read off the machine's dialed peers, never its origin. With no reach there is no word,
 * and the distribution rewrite ({@code law_shard}) is not a live morphism: a rewrite that cannot move code is the
 * identity, so it is pruned from the path's out-edges (§4 liveness).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AbstractionGeometryTest extends AbstractInstSetTest {

    public AbstractionGeometryTest() {
        super(catInstSet::new);
    }

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
        InstSet.importInstSet(f("/m/math"));
    }

    @BeforeEach
    public void clearNetwork() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
        Machine.current().network().at(uri(PEER), noobj(), MUTABLE);
    }

    /**
     * The demand row dials two extra peers; drop them, so a later row's "no peers" premise stays true.
     */
    @AfterEach
    public void clearDials() {
        Machine.current().network().disconnect(f("loop://ginger:8555"));
        Machine.current().network().disconnect(f("loop://booger:8555"));
    }

    /**
     * No reach, no word: a machine with nothing dialed lifts to an abstraction with no place-class word.
     */
    @Test
    public void testNoReachNoWord() {
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()").asCode();
        final Obj abstraction = catInstSet.ABSTRACTION_TYPE.constructor().apply(code);
        assertTrue(abstraction.isRec(), "the code lifts: " + abstraction);
        assertTrue(abstraction.asRec().at(uri(WHERE)).isNoObj(), "no peers → no place-class word: " + abstraction);
    }

    /**
     * A dialed peer is the word: the place the code can ship to, one entry per distinct host.
     */
    @Test
    public void testDialedPeerIsTheWord() {
        Machine.current().network().connect(f("loop://ginger.local:8555"));
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()").asCode();
        final Obj abstraction = catInstSet.ABSTRACTION_TYPE.constructor().apply(code);
        final Obj word = abstraction.asRec().at(uri(WHERE));
        assertTrue(word.isLst() && 1 == word.asLst().count(), "the word is the dialed peer: " + word);
        final Obj member = word.asLst().elements().findFirst().orElse(noobj());
        assertTrue(member.isRec(), "the word member is a place: " + member);
        assertEquals(f("//ginger.local"), Place.of(member.asRec()).obj(), "the word names the peer, not the origin");
    }

    /**
     * Liveness (§4): {@code law_shard} is a live out-edge only when there is reach — with nothing dialed it is the
     * identity and is pruned; a dialed peer makes it live.
     */
    @Test
    public void testLawShardRequiresReach() {
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()").asCode();
        final Obj unpeered = catInstSet.PATH_TYPE.constructor().apply(code);
        assertTrue(unpeered.asRec().at(uri("law_shard")).isNoObj(), "no peers → law_shard is pruned: " + unpeered);
        Machine.current().network().connect(f("loop://ginger.local:8555"));
        final Obj peered = catInstSet.PATH_TYPE.constructor().apply(code);
        assertTrue(peered.asRec().has(uri("law_shard")), "a dialed peer makes law_shard live: " + peered);
    }

    /**
     * An abstraction carrying a place-class word is a valid {@code abstraction::T}.
     */
    @Test
    public void testAbstractionWithWordIsAbstractionT() {
        Machine.current().network().connect(f("loop://ginger.local:8555"));
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()").asCode();
        final Obj abstraction = catInstSet.ABSTRACTION_TYPE.constructor().apply(code);
        assertTrue(abstraction.test(catInstSet.ABSTRACTION_TYPE),
                "an abstraction with a word is abstraction::T: " + abstraction);
    }

    /**
     * The shape half of {@code law_shard}'s guard, judged with reach present: a keyed fold's combiner is pointwise,
     * computed from its spec's folds — count, sum and order recombine, mean and a bare key do not.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3}).mean()  % false",
            "start({1,2,3}).group([mod(2)=>count()])                   % true",
            "start({1,2,3}).plus(1).group([mod(2)=>sum()])             % true",
            "start({1,2,3}).group([mod(2)=>group([mod(3)=>sum()])])    % true",
            "start({1,2,3}).group([mod(2)=>mean()])                    % false",
            "start({1,2,3}).group([mod(2)=>sum(),mod(3)=>sum()])       % true",
            "start({1,2,3}).group([mod(2)=>sum(),mod(3)=>count()])     % false",
            "start({1,2,3}).group([mod(2)=>order(_)])                  % true",
            "start({1,2,3}).group([mod(2)=>_])                         % false",
            "start({[a=>1],[b=>2]}).sum()                              % true",
    }, delimiter = '%')
    void testShardShapePruning(final String code, final boolean live) {
        Machine.current().network().connect(f("loop://localhost:8555"));
        final Obj path = catInstSet.PATH_TYPE.constructor().apply(ObjmtronSerializer.parse(code));
        checkEquality(LOG, bool(path.asRec().has(uri("law_shard"))), bool(live), true);
    }

    /**
     * The realized placement: a shipped shard writes {@code path.where} = {@code {[span=>at] => [peer place]}}.
     */
    @Test
    public void testLoweredShardCarriesRealizedWhere() {
        Machine.current().network().connect(f("loop://localhost:8555"));
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4,5,6}).plus(1).sum()").asCode();
        final Obj lifted = catInstSet.realized(PlanInterpreter.lower(catRewrite.planOf("law_shard"), code));
        assertTrue(lifted.isRec() && lifted.asRec().has(uri(WHERE)), "a shipped shard writes a realized where: " + lifted);
        final Obj places = lifted.asRec().at(uri(WHERE)).asRec().values().findFirst().orElse(noobj());
        assertEquals("[//localhost=>1]", places.toString(),
                "the AT span maps to the one place that ran, with the lane it ran: " + lifted);
    }

    /**
     * A HOME LANE'S ROUTE IS READ, NOT SKIPPED BY FAILURE — {@code ?place=[local,peer]} emits one addressless
     * {@code eval()} (the lane at distance zero) beside the shipped {@code eval(<peer>)}. The realized read-back must
     * walk the addressless route as the local place, so no {@code arg(0)} is ever asked of it. The local place is
     * {@link Places#self()}, which a listener-less test VM does not have: the peer is what this row can pin, and the
     * home lane's own capture is the route's shape, asserted first.
     */
    @Test
    public void testLoweredShardWithAHomeLaneReadsTheAddresslessRoute() {
        Machine.current().network().connect(f("loop://localhost:8555"));
        // a synthetic home authority: a test VM listens on nothing, so `local` would name no place on its own
        final studio.phaseshift.metatron.algebra.rewrite.Env env = studio.phaseshift.metatron.algebra.rewrite.Env.of(
                Machine.current().network().peers(),
                Machine.current().network().machines(),
                java.util.Set.of(f("loop://mantis:8555")));
        final Code emitted = PlanInterpreter.apply(catRewrite.planOf("law_shard"),
                ObjmtronSerializer.parse("start?place=[local,peer]({1,2,3,4,5,6}).plus(1).sum()").asCode(), env);
        assertTrue(emitted.insts().get(1).arg(0).asLst().lstValue().getFirst().asRec().at(uri(CODE)).asInst().args().isEmpty(),
                "the home lane's route is the addressless eval: " + emitted);
        final Obj lifted = catInstSet.realized(emitted);
        assertTrue(lifted.isRec() && lifted.asRec().has(uri(WHERE)), "a home lane writes a realized where: " + lifted);
        final Obj places = lifted.asRec().at(uri(WHERE)).asRec().values().findFirst().orElse(noobj());
        assertEquals("[local=>1,//localhost=>1]", places.toString(),
                "both lanes are realized: the home one as `local`, the shipped one by its authority: " + lifted);
    }

    /**
     * THE REALIZED DEMANDS, NOT JUST WHO RAN — {@code ?place=[//ginger,//ginger,//booger]} asks ginger for two lanes and
     * booger for one, and the emitted layout runs exactly that, so {@code path.where} records the COUNTS against the
     * designators: {@code [[span=><at]=>[//ginger=>2,//booger=>1]]}. This is the realized lane count the emitter
     * clamped to, not the declared band — the rec is the weighted {@code ?place} spelling, so the read-back is a place
     * set again.
     */
    @Test
    public void testRealizedWhereRecordsTheRealizedDemands() {
        Machine.current().network().connect(f("loop://ginger:8555"));
        Machine.current().network().connect(f("loop://booger:8555"));
        final Code emitted = PlanInterpreter.lower(catRewrite.planOf("law_shard"),
                ObjmtronSerializer.parse("start?place=[//ginger,//ginger,//booger]({1,2,3,4,5,6}).plus(1).sum()").asCode());
        final Obj lifted = catInstSet.realized(emitted);
        final Obj demands = lifted.asRec().at(uri(WHERE)).asRec().values().findFirst().orElse(noobj());
        assertEquals("[//ginger=>2,//booger=>1]", demands.toString(),
                "each realized place carries the lanes it actually ran: " + lifted);
        assertEquals(2, demands.asRec().jvm().size(), "and only the places that ran are recorded: " + lifted);
    }

    /**
     * An effect stays home: no {@code select} is emitted, so no realized {@code where} is written — the code stays
     * un-rec'd.
     */
    @Test
    public void testUnshippedLowerHasNoWhere() {
        Machine.current().network().connect(f("loop://localhost:8555"));
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4,5,6}).plus(1).side(plus(1)).sum()").asCode();
        final Obj lifted = catInstSet.realized(PlanInterpreter.lower(catRewrite.planOf("law_shard"), code));
        assertTrue(lifted.isCode(), "an unshipped lower leaves the code as-is (no realized where): " + lifted);
    }

    /**
     * A declared (roster) machine, dialed or not, is a distribution candidate: it appears in the place-class word and
     * makes {@code law_shard} live.
     */
    @Test
    public void testRosterMachineEnablesDistribution() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                uri("ginger.local"), uri("ws://ginger.local:8555"))))));
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()").asCode();
        final Obj abstraction = catInstSet.ABSTRACTION_TYPE.constructor().apply(code);
        final Obj word = abstraction.asRec().at(uri(WHERE));
        assertTrue(word.isLst() && 1 == word.asLst().count(), "a roster machine is in the word: " + word);
        assertEquals(f("//ginger.local"), Place.of(word.asLst().elements().findFirst().orElse(noobj()).asRec()).obj(),
                "the word names the declared machine");
        final Obj path = catInstSet.PATH_TYPE.constructor().apply(code);
        assertTrue(path.asRec().has(uri("law_shard")), "a roster machine makes law_shard live: " + path);
    }

    /**
     * {@code ?hop=0} opts the fold out, so {@code law_shard} is a proof of identity — pruned even with reach present.
     */
    @Test
    public void testZeroHopPrunesDistribution() {
        Machine.current().network().connect(f("loop://localhost:8555"));
        final Code code = ObjmtronSerializer.parse("start?hop=0({1,2,3,4}).plus(1).sum()").asCode();
        final Obj path = catInstSet.PATH_TYPE.constructor().apply(code);
        assertTrue(path.asRec().at(uri("law_shard")).isNoObj(), "?hop=0 opts the fold out: " + path);
    }

    /**
     * The parser memo must not leak a q from one parse into the next: a {@code ?hop=0} on one source must not make
     * the next source's seed carry it (the position-keyed memo was cleared by buffer identity, which two consecutive
     * parses can defeat).
     */
    @Test
    public void testHopQDoesNotLeakAcrossParses() {
        final Code first = ObjmtronSerializer.parse("start?hop=0({1,2,3,4}).plus(1).sum()").asCode();
        assertTrue(first.insts().getFirst().tid().hasQ(HOP), "the first parse declares ?hop=0: " + first);
        final Code second = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()").asCode();
        assertFalse(second.insts().getFirst().tid().hasQ(HOP), "?hop=0 must not leak into the next parse: " + second);
    }

    /**
     * The declared-liveness test (§4): a scheme is dialable only when its dialer resolves in the ISA; an unknown
     * scheme never is.
     */
    @Test
    public void testSchemeSignatureResolves() {
        assertTrue(Signatures.resolves("ws"), "the ws dialer resolves: /m/web/ws/ws_client");
        assertFalse(Signatures.resolves("nope"), "an unknown scheme has no dialer");
    }

    /**
     * A place is an admissible candidate when some authority's scheme has a dialer in the ISA.
     */
    @Test
    public void testPlaceAdmissibility() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                uri("ginger.local"), uri("ws://ginger.local:8555"))))));
        final Obj place = Places.place(uri("//ginger.local"));
        assertTrue(Places.admissible(place), "a ws place is admissible: " + place);
    }

    /**
     * {@code move}: a cut-less plan with a {@code chain} decision ships the whole code to the target place — the
     * receiver re-plans it. Over a loop peer the shipped program evaluates locally, so the value is the local fold's.
     */
    @Test
    public void testMoveShipsWholeCodeToTarget() {
        Machine.current().network().connect(f("loop://localhost:8555"));
        final Code code = ObjmtronSerializer.parse("start(5).plus(1)").asCode();
        final Code emitted = PlanInterpreter.bind(
                PlanBuilder.plan("move").where(Placement.chain(f("loop://localhost:8555"), Span.at())).create(), code);
        checkEquality(LOG, jnt(6),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(emitted)).clone().selfVID(null), true);
    }
}
