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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_SECOND_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Rec.rec_noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * BRANCH — the instruction that applies its lhs to each of its arguments, as a class rather than a lambda: the branch
 * definition is minted as a BranchInst ({@code InstBuilder.inst}), and a call bound from it is a clone of it, so the
 * body runs on the bound call itself ({@link #route}).
 * <ul>
 *     <li>An ORDERED call is the local branch: each member applied where the code stands.</li>
 *     <li>A KEYED call routes: {@code place => program} runs the lhs through the program at that place: a path at that
 *     local machine ({@code <~>} this one), a host at that peer ({@link #lane}). The key may mark itself
 *     {@code ?split={w}part} to take a weighted share of the lhs ({@link #weight}, {@link #deal}).</li>
 *     <li>EVERY LANE IS A {@code virtual::T}, submitted before any is awaited, so the lanes overlap and the branch
 *     waits once, for the slowest; each answers through its {@code rhs} (the {@code >>rhs} await), within the key's
 *     {@code ?timeout} ({@link #thread}).</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BranchInst extends MInst {

    public BranchInst(final Tuple.Triplet<Poly, Inst.f, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The keyed routes, {@code place => program}, as each lane sees them: the args of a keyed call without the reserved
     * {@code lhs} key the args frame carries (Processor), each key carrying the branch's own query as its default.
     * {@code branch?timeout=24(...)} times every lane and {@code branch?split=part(...)} parts the lhs equally, while a
     * key's own query overrides the branch's (Marko, 2026-11-05). An ordered call has none.
     */
    public Rec routes() {
        final Map<String, String> defaults = this.tid().qMap();
        return !this.args().isRec() ? rec_noobj() : this.args().asRec().elements()
                .filter(kv -> !uri(LHS).equals(kv.first()))
                // the branch's query first, then the key's own on top (addQ puts its map over what is there)
                .map(kv -> rel(uri(kv.first().uriValue().q(defaults).addQ(kv.first().uriValue().qMap())), kv.second()))
                .collect(new CommonUtil.RecCollector());
    }

    /**
     * THE BODY: route the lhs through the branches. Every keyed lane is submitted on its own {@code virtual::T}
     * before any is awaited, and a marked key dealt nothing runs nothing. A SEEDED lane — a program that starts from
     * its own {@code start(...)} — takes none of the lhs: it was dealt its part when it was planned, so its split
     * records that share rather than asking for one, and a branch of seeded lanes alone needs no lhs at all.
     */
    public Obj route(final Obj lhs) {
        if (!this.args().isRec())
            // the member's own application already carries its multiplicity (an inst's coefficient, or a code's
            // head-inst coefficient); the branch contributes its lhs
            return objs(this.args().elements().map(o -> o.apply(lhs).c(c -> lhs.c().mult(c))));
        final List<Rel> routes = this.routes().elements().toList();
        final long[] weights = routes.stream()
                .mapToLong(route -> seeded(route) ? 0L : this.weight(route.first().uriValue()))
                .toArray();
        final List<List<Obj>> parts = deal(lhs, weights);
        final List<Obj> lanes = IntStream.range(0, routes.size())
                .mapToObj(r -> 0 == weights[r] ? this.lane(routes.get(r), lhs)
                        : parts.get(r).isEmpty() ? noobj()
                        : this.lane(routes.get(r), objs(parts.get(r))))
                .toList();
        return objs(lanes.stream().map(lane -> lane.isNoObj() ? lane : lane.asRec().at(uri(RHS))));
    }

    /**
     * Whether a lane's program starts from its own seed, a {@code start(...)}, rather than from the part of the lhs
     * the branch deals it.
     */
    private static boolean seeded(final Rel route) {
        return route.second().isCall() && !route.second().asCall().insts().isEmpty()
                && START_INST_TID.equals(route.second().asCall().insts().getFirst().tid().basePath());
    }

    /**
     * THE SPLIT a key marks itself with (Marko, 2026-11-05): {@code ?split={w}part} shares the lhs with the other
     * marked keys by its weight {@code w} (one when unstated, as the element count is rarely known), while an unmarked
     * key, or {@code ?split=copy}, gets the whole lhs: weight zero. Any other value is refused, never read as a copy —
     * a misspelled part would hand every lane the whole lhs and double-count its fold.
     */
    protected long weight(final fURI key) {
        final String split = key.q(SPLIT);
        if (null == split)
            return 0L;
        // the object grammar reads the leading coefficient ({4}part), as it reads ?hop={2}4
        final Obj share = ObjmtronSerializer.parse(split);
        if (share.isUri() && PART.equals(share.uriValue().name()))
            return share.c().min();
        if (share.isUri() && COPY.equals(share.uriValue().name()))
            return 0L;
        throw MTronException.of("?%s=%s is not a split a branch performs: %s or %s", SPLIT, split, PART, COPY);
    }

    /**
     * THE PART GROUP: member {@code j} of the lhs (a bulked member goes whole) goes to slot {@code j mod W} of the
     * summed weight {@code W}, the slots dealt to the marked keys in key order, {@code w} apiece; a copy key is dealt
     * nothing. Under a {@code {1}} dom an application holds one member, always slot 0, so the first marked key takes
     * them all: no round-robin state is kept across applications. The plan deals a literal seed by this same rule when
     * it seeds its lanes ({@code PlanInterpreter}), so a planned part is the part the branch would have dealt.
     */
    public static List<List<Obj>> deal(final Obj lhs, final long[] weights) {
        final List<List<Obj>> parts = Arrays.stream(weights).mapToObj(w -> (List<Obj>) new ArrayList<Obj>()).toList();
        final long total = Arrays.stream(weights).sum();
        final List<Obj> members = 0 == total ? List.of() : lhs.stream().toList();
        for (int j = 0; j < members.size(); j++) {
            int lane = 0;
            for (long slot = j % total; slot >= weights[lane]; lane++)
                slot -= weights[lane];
            parts.get(lane).add(members.get(j));
        }
        return parts;
    }

    /**
     * ONE LANE: a {@code virtual::T} running the lane where its key points, submitted and not awaited (Marko,
     * 2026-11-05). The lane is {@code start(input).program}, the code a remote lane ships. The start streams the input,
     * so a {@code {*}} dom's bag reaches a {@code {1}} program member by member, and the input's coefficient rides the
     * start. A seeded program already starts from its own part, so it runs as written.
     * <ul>
     *     <li>A PATH is a local MACHINE, resolved as {@code Machine.fetch} resolves it (Marko, 2026-11-05): {@code <~>} and
     *     {@code <.>} this machine, {@code <..>} its parent, {@code </.>} the root, {@code </abc>} from the root and
     *     {@code <~/abc>} from this one. The lane's thread is submitted from that machine's perspective, so its compiler
     *     compiles the lane and its processor runs it. A path that names no machine is refused, and a path ending in
     *     {@code /} is a branching uri, which reads relations rather than a machine.</li>
     *     <li>A HOST is a peer ({@link #remote}).</li>
     * </ul>
     */
    protected Obj lane(final Rel route, final Obj input) {
        final fURI key = route.first().uriValue();
        final List<Inst> program = route.second().isCall()
                ? route.second().asCall().insts()
                : List.of((Inst) map_(route.second()).tryToInst());
        // the key's own query (its split, its timeout) is the branch's to read, not part of the place
        final fURI place = key.resolve().qLess();
        final Code code = seeded(route) ? code(program) : start_(input).addInsts(program);
        if (null != place.host())
            return this.remote(key, place, program, code, input);
        return Machine.Helper.evalAt(Machine.current().fetch(key.qLess()),
                () -> this.thread(code, key).apply(noobj()));
    }

    /**
     * A LANE'S THREAD: a {@code virtual::T} running {@code code}. The key's {@code ?timeout} bounds the wait for its
     * result in whole seconds, as a q value cannot carry a typed time yet (Marko, 2026-11-05); the thread holds it as the
     * time it is ({@code second::n}). Without one, the thread's own default applies.
     */
    protected VirtualThread thread(final Obj code, final fURI key) {
        final String timeout = key.q(TIMEOUT);
        if (null == timeout)
            return VirtualThread.virtual(code);
        final Obj seconds = ObjmtronSerializer.parse(timeout);
        if (!seconds.isInt())
            throw MTronException.of("?%s=%s is not a whole number of seconds", TIMEOUT, timeout);
        return new VirtualThread(CommonUtil.mutableMap(uri(CODE), code, uri(TIMEOUT),
                real(seconds.intValue().doubleValue(), MATH_SECOND_TID, null)), MACH_VIRTUAL_THREAD_TID, null);
    }

    /**
     * A REMOTE LANE, through its place's peer::T: the client's mini-instset ({@link Network#overrideOf}) serves the
     * verb the lane needs. A lane that is one {@code from(</path>)} or one {@code to(</path>)} goes through the
     * machine's own door to the place (its client's from/to, else its eval), so a client that reads and writes but runs
     * no program — {@code http_client::T}, GET and POST — serves it. Any other program ships to the place's eval as
     * the lane's {@code code}, uncompiled, to be compiled there. A place that serves neither is unreachable.
     */
    protected Obj remote(final fURI key, final fURI place, final List<Inst> program, final Code code, final Obj input) {
        final Network network = Machine.current().network();
        final Inst only = 1 == program.size() ? program.getFirst() : null;
        final fURI verb = null != only
                && (FROM_INST_TID.equals(only.tid().basePath()) || TO_INST_TID.equals(only.tid().basePath()))
                && only.arg(0).isUri() && only.arg(0).uriValue().isAbsolute()
                ? only.tid().basePath()
                : EVAL_INST_TID;
        final Inst eval = network.overrideOf(place, EVAL_INST_TID);
        if (!eval.isObjInst() && (EVAL_INST_TID.equals(verb) || !network.overrideOf(place, verb).isObjInst()))
            throw Network.Helper.unreachable(Machine.current(), verb, place);
        if (EVAL_INST_TID.equals(verb))
            return this.thread(eval, key).apply(code);
        // the lane's own address homed at the place, its query kept (from(</x?docq>)). The written value rides the
        // lane's code, not the thread's lhs: a thread reads its lhs with at(), which fires an auto or dom-{0} inst
        final fURI address = only.arg(0).uriValue().origin(place);
        return this.thread(instLambda((l, i) -> FROM_INST_TID.equals(verb)
                        ? Machine.read(address)
                        : Machine.write(address, input)), key)
                .apply(noobj());
    }
}
