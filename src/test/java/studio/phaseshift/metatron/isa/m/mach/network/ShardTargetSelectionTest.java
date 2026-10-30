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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A SHARD TARGET IS ONLINE AND SERVES THE ROUTE (Marko, 2026-10-24). law_shard ships each slave as
 * {@code eval(<target>)}, a program the far side must apply, so a peer is a target only when it is online AND its
 * client declares {@code eval} — eval is exactly "apply this program and answer its value", and {@code to} alone only
 * writes an obj to an address. The requirement is {@code plan::T} data — each placement's {@code inst} (the key
 * mirrors a client's {@code inst}), defaulted from the placement's shape — so every distribution rule reads it through
 * one check, {@link Places#serves}.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
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
        Machine.current().network().drop(SERVANT);
        Machine.current().network().drop(EVALER);
    }

    /**
     * A loop-style client that declares exactly {@code instTIDs}, each the native with an identity-shaped body, and
     * vouches for itself with a live {@code status} — what makes a link-less peer online.
     */
    private static Obj client(final fURI host, final fURI... instTIDs) {
        final Rec overrides = rec();
        for (final fURI instTID : instTIDs)
            overrides.jvm().put(uri(instTID), Network.Helper.override(instTID, (lhs, inst) -> inst.arg(0).apply(lhs)));
        return rec(uri(HOST), uri(host), uri(INST), overrides, uri(STATUS), Network.Helper.statusOf(() -> true));
    }

    @Test
    void testADistributingShapeRequiresEval() {
        // eval is the route: the target applies the program it is shipped and answers its value — eval alone suffices,
        // and to only writes an obj to an address (an http POST stores the program and runs nothing)
        assertEquals(Set.of(EVAL_INST_TID), Placement.tree(1, Span.at()).inst(), "a tree ships a program through eval(<target>)");
        assertEquals(Set.of(EVAL_INST_TID), Placement.peers(Span.at()).inst(), "so does every distributing shape");
        assertEquals(Set.of(), Placement.local(Span.at()).inst(), "a shape that stays home requires nothing");
        assertEquals(Set.of(EVAL_INST_TID, FROM_INST_TID), Placement.tree(1, Span.at()).inst(FROM_INST_TID).inst(),
                "a placement adds what its shipped chunk needs to what its shape ships through");
    }

    @Test
    void testLawShardsPlanStatesWhatItsTargetMustServe() {
        final Plan shard = catRewrite.planOf("law_shard");
        final Placement route = shard.where().stream().filter(p -> p.shape().distributes()).findFirst().orElseThrow();
        assertEquals(lst(uri(EVAL_INST_TID)), route.asRec().at(uri(INST)),
                "the plan says it: inst=>[</m/inst/eval>] on its tree placement");
        assertTrue(shard.test(catInstSet.PLAN_TYPE), "and it is still a plan::T: " + shard);
        // a placement written as a rec, without inst, still requires what its shape ships through
        assertEquals(Set.of(EVAL_INST_TID), Placement.of(rec(uri(SHAPE), uri("tree"), uri(SPAN), lst()).asRec()).inst());
    }

    @Test
    void testServesNeedsBothOnlineAndEval() {
        final Placement route = Placement.tree(1, Span.at());
        final Obj online = rec(uri(ORIGIN), uri(SERVANT), uri(HOST), uri(SERVANT),
                uri(CLIENT), client(SERVANT, EVAL_INST_TID, TO_INST_TID));
        assertTrue(Places.serves(online, route), "online and serving eval: a target");
        final Obj evalOnly = rec(uri(ORIGIN), uri(EVALER), uri(HOST), uri(EVALER),
                uri(CLIENT), client(EVALER, EVAL_INST_TID));
        assertTrue(Places.serves(evalOnly, route), "online and serving eval alone: a target — eval is all the route needs");
        final Obj writer = rec(uri(ORIGIN), uri(EVALER), uri(HOST), uri(EVALER),
                uri(CLIENT), client(EVALER, FROM_INST_TID, TO_INST_TID));
        assertFalse(Places.serves(writer, route), "online and serving to, but no eval — a POST writer stores a program, it runs none");
        final Obj offline = rec(uri(ORIGIN), uri(SERVANT), uri(HOST), uri(SERVANT),
                uri(CLIENT), new DownLink(client(SERVANT, EVAL_INST_TID, TO_INST_TID).asRec().jvm()));
        assertFalse(Places.serves(offline, route), "serving eval, but its link is down: not a target");
        final Obj silent = rec(uri(ORIGIN), uri(SERVANT), uri(HOST), uri(SERVANT),
                uri(CLIENT), rec(uri(HOST), uri(SERVANT), uri(INST), client(SERVANT, EVAL_INST_TID, TO_INST_TID).asRec().at(uri(INST))));
        assertFalse(Places.serves(silent, route), "serving eval, but its client never says it is live: not a target");
    }

    @Test
    void testLawShardShipsOnlyToPeersThatServeEval() {
        Network.Helper.index(client(SERVANT, EVAL_INST_TID, TO_INST_TID));
        Network.Helper.index(client(EVALER, EVAL_INST_TID));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6}.plus(1).sum()").asCode()).asCode();
        assertEquals(List.of(SERVANT, EVALER), DistributedSweep.layout(compiled).routes(),
                "the shard fans out over the peers that serve eval, and only those: " + compiled);
        // and the route is BOUND in the compiled code — the target peer's own eval, not a bare eval(<target>) left for
        // select to resolve when it runs
        final Inst to = compiled.insts().stream()
                .filter(inst -> SELECT_INST_TID.equals(inst.tid().basePath()))
                .map(select -> (Obj) select.arg(0).asLst().elements().findFirst().orElseThrow())
                .map(Reduction::ship)
                .findFirst().orElseThrow();
        assertTrue(to.hasf(), "the emitted route carries its function: " + to);
        assertTrue(to.functionClassName().endsWith("Network$Helper$AtPeer"), "the target peer's own eval: " + to.functionClassName());
    }

    @Test
    void testNoPeerThatServesEvalLeavesTheFoldAtHome() {
        Network.Helper.index(client(EVALER, TO_INST_TID));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6}.plus(1).sum()").asCode()).asCode();
        assertEquals(List.of(), DistributedSweep.layout(compiled).routes(), "an online peer that serves no eval is no target, so nothing ships: " + compiled);
    }

    /**
     * A client whose link is down — the supervised state between a failure and its redial. It carries no status
     * field of its own, so the network asks the link ({@link Network.Link#connected}).
     */
    private static final class DownLink extends MRec implements Network.Link {
        DownLink(final Map<Obj, Obj> jvm) {
            super(withoutStatus(jvm), REC_TID, null);
        }

        private static Map<Obj, Obj> withoutStatus(final Map<Obj, Obj> jvm) {
            final Map<Obj, Obj> copy = new java.util.LinkedHashMap<>(jvm);
            copy.remove(uri(STATUS));
            return copy;
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
