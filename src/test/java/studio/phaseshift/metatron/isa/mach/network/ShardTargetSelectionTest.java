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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.algebra.rewrite.Placement;
import studio.phaseshift.metatron.algebra.rewrite.Plan;
import studio.phaseshift.metatron.algebra.rewrite.Span;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.math.cat.Places;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.math.cat.catRewrite;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A SHARD TARGET IS ONLINE AND SERVES THE ROUTE (Marko, 2026-10-24). law_shard ships each slave as
 * {@code to(<target>)}, so a peer is a target only when it is online AND its client declares {@code to}. The
 * requirement is {@code plan::T} data — each placement's {@code inst} (the key mirrors a client's {@code inst}),
 * defaulted from the placement's shape — so every distribution rule reads it through one check,
 * {@link Places#serves}.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class ShardTargetSelectionTest extends AbstractMetatronTest {

    private static final fURI SERVANT = f("loop://servant");   // online, serves to
    private static final fURI EVALER = f("loop://evaler");     // online, serves eval alone

    @BeforeEach
    void setup() {
        // typing is not solved across distributed executions yet — run type-check-free, as the forked-peer suite does
        TypeCheck.disable(TypeCheck.values());
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
    }

    @AfterEach
    void teardown() {
        Machine.current().network().disconnect(SERVANT);
        Machine.current().network().disconnect(EVALER);
    }

    /**
     * A loop-style client that declares exactly {@code instTIDs}, each the native with an identity-shaped body; one
     * with {@code eval} carries a transport (derived from it), which is what makes a link-less peer online.
     */
    private static Obj client(final fURI host, final fURI... instTIDs) {
        final Rec overrides = rec();
        for (final fURI instTID : instTIDs)
            overrides.jvm().put(uri(instTID), Network.Helper.override(instTID, (lhs, inst) -> inst.arg(0).apply(lhs)));
        return rec(uri(HOST), uri(host), uri(INST), overrides);
    }

    /**
     * The authorities the lowered layout ships to: its {@code select}'s routes, each {@code to(<authority>)}.
     */
    private static List<fURI> routes(final Code code) {
        final List<fURI> routes = new ArrayList<>();
        for (final Inst inst : code.insts())
            if (SELECT_INST_TID.equals(inst.tid().basePath()))
                inst.arg(0).asLst().elements().forEach(element -> {
                    final Obj route = (Obj) element;
                    final Inst to = route.isObjCall() ? route.asCall().insts().getFirst() : route.asInst();
                    routes.add(to.arg(0).uriValue());
                });
        return routes;
    }

    @Test
    void testADistributingShapeRequiresTo() {
        assertEquals(Set.of(TO_INST_TID), Placement.tree(1, Span.at()).inst(), "a tree ships through to(<target>)");
        assertEquals(Set.of(TO_INST_TID), Placement.peers(Span.at()).inst(), "so does every distributing shape");
        assertEquals(Set.of(), Placement.local(Span.at()).inst(), "a shape that stays home requires nothing");
        assertEquals(Set.of(TO_INST_TID, FROM_INST_TID), Placement.tree(1, Span.at()).inst(FROM_INST_TID).inst(),
                "a placement adds what its shipped chunk needs to what its shape ships through");
    }

    @Test
    void testLawShardsPlanStatesWhatItsTargetMustServe() {
        final Plan shard = catRewrite.planOf("law_shard");
        final Placement route = shard.where().stream().filter(p -> p.shape().distributes()).findFirst().orElseThrow();
        assertEquals(lst(uri(TO_INST_TID)), route.asRec().at(uri(INST)), "the plan says it: inst=>[</m/inst/to>] on its tree placement");
        assertTrue(shard.test(catInstSet.PLAN_TYPE), "and it is still a plan::T: " + shard);
        // a placement written as a rec, without inst, still requires what its shape ships through
        assertEquals(Set.of(TO_INST_TID), Placement.of(rec(uri(SHAPE), uri("tree"), uri(SPAN), lst()).asRec()).inst());
    }

    @Test
    void testServesNeedsBothOnlineAndTo() {
        final Placement route = Placement.tree(1, Span.at());
        final Obj transport = instLambda(message -> message);
        final Obj online = rec(uri(AUTHORITY), uri(SERVANT), uri(HOST), uri(SERVANT), uri(TRANSPORT), transport,
                uri(CLIENT), client(SERVANT, EVAL_INST_TID, TO_INST_TID));
        assertTrue(Places.serves(online, route), "online and serving to: a target");
        final Obj cannotTo = rec(uri(AUTHORITY), uri(EVALER), uri(HOST), uri(EVALER), uri(TRANSPORT), transport,
                uri(CLIENT), client(EVALER, EVAL_INST_TID));
        assertFalse(Places.serves(cannotTo, route), "online, but its client declares no to: not a target");
        final Obj offline = rec(uri(AUTHORITY), uri(SERVANT), uri(HOST), uri(SERVANT),
                uri(CLIENT), new DownLink(client(SERVANT, EVAL_INST_TID, TO_INST_TID).asRec().jvm()));
        assertFalse(Places.serves(offline, route), "serving to, but its link is down: not a target");
    }

    @Test
    void testLawShardShipsOnlyToPeersThatServeTo() {
        Network.Helper.index(client(SERVANT, EVAL_INST_TID, TO_INST_TID));
        Network.Helper.index(client(EVALER, EVAL_INST_TID));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6}.plus(1).sum()").asCode()).asCode();
        assertEquals(List.of(SERVANT), routes(compiled),
                "the shard fans out over the peers that serve to, and only those: " + compiled);
    }

    @Test
    void testNoPeerThatServesToLeavesTheFoldAtHome() {
        Network.Helper.index(client(EVALER, EVAL_INST_TID));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6}.plus(1).sum()").asCode()).asCode();
        assertEquals(List.of(), routes(compiled), "an online peer that cannot to is no target, so nothing ships: " + compiled);
    }

    /**
     * A client whose link is down — the supervised state between a failure and its redial.
     */
    private static final class DownLink extends MRec implements Network.Link {
        DownLink(final Map<Obj, Obj> jvm) {
            super(jvm, REC_TID, null);
        }

        @Override
        public boolean connected() {
            return false;
        }

        @Override
        public boolean hungUp() {
            return false;
        }

        @Override
        public boolean redial() {
            return false;
        }
    }
}
