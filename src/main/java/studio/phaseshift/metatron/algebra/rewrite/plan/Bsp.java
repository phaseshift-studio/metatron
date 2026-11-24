/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.algebra.rewrite.plan;

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.rewrite.InstPattern;
import studio.phaseshift.metatron.algebra.rewrite.PlaceResolver;
import studio.phaseshift.metatron.algebra.rewrite.PlaceResolver.ResolvedPlace;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REPEAT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.carriers;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * law_bsp, THE SUPERSTEP RULE (Marko, 2026-11-22): a whole-frontier repeat whose code distributes over the stream sum
 * deals its frontier over the dialed peers every pass.
 * <pre>
 *   x.repeat?&lt;=A{*}(code=&gt;f, …)  |-&gt;  x.repeat?&lt;=A{*}(code=&gt;branch?&lt;=A{*}&amp;split=part(&lt;//p&gt; =&gt; f?hop=h-1, …), …)
 * </pre>
 * where h is the superstep's height, one when unstated, so a lane is {@code f?hop=0} unless a tree is asked for.
 * The loop stays home. Each pass gathers the frontier (the repeat's own barrier), and the branch deals it by its own
 * rule ({@code BranchInst#deal}: member j to lane j mod W, a bulked member whole), runs f where each lane points, and
 * merges the lanes' answers before the next pass begins: lock-step BSP. Its own rule beside law_shard, not a cut in
 * law_shard's plan (Marko): fold it into the plan when the frontier should stay on the peers between passes.
 * <p>
 * WHAT IS DEALT: f must be pure and floatable instruction by instruction (law_shard's floatable run: a mapper, filter
 * or flatmapper, declared pure), each typed at the carrier flowing into it. Such an f distributes over the stream sum,
 * so dealing the frontier changes nothing but where f runs. A fold or a gather in f ({@code sum()}) would answer a
 * partial per lane, and an effect must not move, so either keeps the repeat as written. So does a per-monad repeat
 * (nothing to deal), a superstep whose budget is the point ({@code ?hop=0}), and a code a keyed branch already deals.
 * <p>
 * WHERE: the DIALED peers that serve {@code eval}, never the roster. A frontier goes only where a connection already
 * stands (the roster counts every interface of a machine as a place).
 * <p>
 * HOW DEEP, A TREE OF SUPERSTEPS (Marko, 2026-11-22: tree BSP unites two patterns, E and N): the superstep's budget
 * is read as law_shard reads a run's, the earliest {@code ?hop} on the repeat or its code, else one level. Its arity
 * caps the lanes, and each lane states the budget one level down on its head ({@code f?hop=0} at a leaf), where the
 * peer reads it: handed {@code start(part).f?hop=h}, a peer whose budget is left plans its part over its own dialed
 * peers (law_shard's cutless run), and so on down. Every pass then partitions the frontier recursively down the tree
 * and merges it back up, and the root's merge is still the one barrier per pass: f distributes over the stream sum at
 * every level, so the tree answers what the flat deal answers.
 */
public final class Bsp {

    private static final InstPattern FLOATS = InstPattern.law(Law.floatable);

    private Bsp() {
    }

    /**
     * The rule as the pool runs it: a chain with no whole-frontier repeat is left as written before any snapshot is
     * taken, so a pass over every other chain costs one scan.
     */
    public static Code apply(final Code code) {
        return code.insts().stream().noneMatch(Bsp::wholeFrontier)
                || Machine.current().network().peers().isEmpty() ? code : apply(code, Env.snapshot());
    }

    /**
     * The rule over one code and one snapshot: the code itself when nothing is dealt. A repeat past a machine change is
     * never seen here: the nomadic extractor cut the chain there, and the tail is compiled where it lands.
     */
    public static Code apply(final Code code, final Env env) {
        final List<Inst> insts = code.insts();
        if (insts.stream().noneMatch(Bsp::wholeFrontier))
            return code;
        final List<fURI> peers = peers(env);
        if (peers.isEmpty())
            return code;
        final List<Type> carriers = carriers(code);
        final List<Inst> rewritten = new ArrayList<>(insts.size());
        boolean dealt = false;
        for (int i = 0; i < insts.size(); i++) {
            final Inst inst = insts.get(i);
            final Inst superstep = wholeFrontier(inst) && i < carriers.size()
                    ? deal(inst, carriers.get(i), peers) : null;
            rewritten.add(null == superstep ? inst : superstep);
            dealt |= null != superstep;
        }
        return dealt ? code(rewritten) : code;
    }

    /**
     * A repeat that gathers its whole frontier.
     */
    private static boolean wholeFrontier(final Inst inst) {
        return REPEAT_INST_TID.equals(inst.tid().basePath()) && inst.isGather();
    }

    /**
     * The repeat with its code dealt over {@code peers}, or null when its code may not be dealt.
     */
    private static Inst deal(final Inst repeat, final Type carrier, final List<fURI> peers) {
        final Obj codeArg = repeat.arg(f(CODE), 1);
        if (!codeArg.isCall())
            return null;
        final List<Inst> f = codeArg.asCall().insts();
        if (f.isEmpty() || !floats(f, carrier))
            return null;
        // THE SUPERSTEP'S BUDGET: the earliest ?hop on the repeat or its code, else one level; the point, or an arity of
        // zero, keeps the pass home. A lane states the height one level down (0 at a leaf), the arity as stated.
        final Hop hop = Shard.hopAxis(Stream.concat(Stream.of(repeat), f.stream()).toList(), Hop.tree(1));
        if (hop.isPoint() || hop.arity().isZero())
            return null;
        final Obj budget = 1 == hop.height() ? jnt(0) : Hop.of(hop.height() - 1, hop.arity()).toObj();
        final long arity = null == hop.arity().max() ? Long.MAX_VALUE : hop.arity().max();
        // A LANE IS A MOVE (Law.nomadic, stage 2), emitted as the cut it compiles to, its budget on the cut's target
        final List<Obj> lanes = new ArrayList<>();
        for (final fURI peer : peers.subList(0, (int) Math.min(peers.size(), arity))) {
            final List<Inst> lane = new ArrayList<>(f.stream().map(Shard::raw).toList());
            Shard.stampHop(lane, null);
            lanes.add(instB(CUT_INST_TID, lst(uri(peer.q(HOP, budget)), code(lane).tryToInst())));
        }
        final Inst branch = instB(BRANCH_INST_TID.dom(InstSet.A.maybeSome()).q(SPLIT, PART), lst(lanes));
        final Map<Obj, Obj> args = new LinkedHashMap<>(repeat.args().asRec().jvm());
        final Obj key = args.keySet().stream().filter(k -> k.isUri() && f(CODE).equals(k.uriValue())).findFirst().orElse(null);
        if (null == key)
            return null;
        args.put(key, code(List.of(branch)));
        return instB(repeat.tid().removeQ(ARGS), rec(args));
    }

    /**
     * Every instruction of f pure and floatable at the carrier flowing into it: f distributes over the stream sum.
     */
    private static boolean floats(final List<Inst> f, final Type carrier) {
        Type at = carrier;
        for (final Inst inst : f) {
            // a branch, or anything that gathers as written (a nested repeat?<=A{*}, whose declared form reads as a step)
            if (BRANCH_INST_TID.equals(inst.tid().basePath()) || inst.isGather())
                return false;
            final Inst typed = Law.Helper.keyed(inst, at);
            if (!FLOATS.matches(typed))
                return false;
            at = typed.rng();
        }
        return true;
    }

    /**
     * The dialed peers that serve {@code eval}, by authority, in the resolver's order.
     */
    private static List<fURI> peers(final Env env) {
        if (env.peers().isEmpty())
            return List.of();
        return PlaceResolver.resolve(PlaceSet.peer(), env).stream()
                .filter(place -> !place.isLocal())
                .map(ResolvedPlace::authority)
                .distinct()
                .toList();
    }

    private static fURI f(final String name) {
        return fURI.Singleton.f(name);
    }
}
