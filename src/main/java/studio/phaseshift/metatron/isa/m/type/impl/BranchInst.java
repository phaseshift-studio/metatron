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

import studio.phaseshift.metatron.algebra.rewrite.PlaceResolver;
import studio.phaseshift.metatron.algebra.rewrite.plan.Env;
import studio.phaseshift.metatron.algebra.rewrite.plan.PlaceSet;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.machInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MOVE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Rec.rec_noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * BRANCH — the instruction that applies its lhs to each of its arguments, as a class rather than a lambda: the branch
 * definition is minted as a BranchInst ({@code InstBuilder.inst}), and a call bound from it is a clone of it, so the
 * body runs on the bound call itself ({@link #route}).
 * <ul>
 *     <li>An ORDERED call is the local branch: each member applied where the code stands. The dom coefficient is the
 *     shipment grain: {@code {?}} (the default) one obj per call; {@code {n}} a page; {@code {*}} the whole lhs — a
 *     barrier a DISTRIBUTION REWRITE states on the branch it emits, never the instruction's own default.</li>
 *     <li>A LAYOUT routes its lanes ({@link #lanes}): a member headed by a move, or the cut it compiles to, runs where
 *     that cut carries it, a path to that local machine, a host to that peer, every machine change on the one
 *     mechanism, and a member headed by neither is a home lane. {@code ?split=part} deals the lhs evenly over the
 *     lanes, and a place set, {@code ?split=[{2}//a,//b]}, deals it by the BIAS it states, each lane the demand of the
 *     designator naming its place ({@link #share}, {@link #deal}); a lane's point is copies of what it answers. A KEYED
 *     call is read as the lanes it means, {@code <p> => program} the lane {@code cut(<p>, program)}, a lane keyed
 *     {@code <~>} a home lane (timed by a cut that stays), and a key's {@code ?split={w}part} its share.</li>
 *     <li>EVERY LANE IS A {@code virtual::T}, submitted before any is awaited, so the lanes overlap and the branch
 *     waits once, for the slowest; each answers through its {@code rhs} (the {@code >>rhs} await). A lane's
 *     {@code ?timeout} is its cut's to keep ({@link #lane}).</li>
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
     * key's own query overrides the branch's (Marko, 2026-11-05). An ordered call has none. Any keyed branch has them,
     * bound or as written: a branch inside a repeat's blocked code is never bound.
     */
    public static Rec routes(final Inst branch) {
        final Map<String, String> defaults = branch.tid().qMap();
        return !branch.args().isRec() ? rec_noobj() : branch.args().asRec().elements()
                .filter(kv -> !uri(LHS).equals(kv.first()))
                // the branch's query first, then the key's own on top (addQ puts its map over what is there)
                .map(kv -> rel(uri(kv.first().uriValue().q(defaults).addQ(kv.first().uriValue().qMap())), kv.second()))
                .collect(new CommonUtil.RecCollector());
    }

    /**
     * ONE LANE, as the branch runs it and as every reader of a layout reads it (Law.nomadic, stage 2): the place it
     * runs at ({@code null} at home), the program it runs there, its weight (its share of the lhs when the branch deals
     * it, {@link #share}, else {@code 0}: the whole lhs) and the member the branch runs on the lane's thread.
     */
    public record Lane(fURI place, List<Inst> program, long weight, Obj member) {

        public boolean home() {
            return null == this.place;
        }

        /**
         * Whether the program starts from its own seed, a {@code start(...)}, rather than from the part of the lhs the
         * branch deals it.
         */
        public boolean seeded() {
            return !this.program.isEmpty() && START_INST_TID.equals(this.program.getFirst().tid().basePath());
        }
    }

    /**
     * THE BRANCH'S LANES, in order (Marko, 2026-11-24): a lane is a MEMBER HEADED BY A MOVE, or the cut a move
     * compiles to, so its place is the cut's target and the program is the cut's tail. A member headed by neither is a
     * HOME LANE, and so is one whose move's target is computed when it runs (it names no place yet), as is a cut to
     * {@code <~>} (a stay, which a timed home member is). A lane's WEIGHT is its share of the lhs ({@link #share}): the
     * bias the branch's {@code ?split} states for the lane's place, never a point, which is copies of what the lane
     * answers ("cut{2}() will duplicate data"). A KEYED branch is read as the lanes it means: each key {@code <p>} the
     * lane {@code cut(<p>, program)}, its share its {@code ?split}, and its {@code ?timeout} (the key's, else the
     * branch's) the cut's; a home key's program is timed by a cut that stays, {@code cut(<~>, program)}, as every
     * timeout is a cut's. A DESIGNATOR KEY, {@code <peer>} or {@code <#>}, is a lane at each place it names when the
     * branch runs, as {@code ?place} resolves it: no place, no lane.
     */
    public static List<Lane> lanes(final Inst branch) {
        if (branch.args().isRec()) {
            final List<Lane> lanes = routes(branch).<Rel>elements().flatMap(route -> {
                final fURI key = route.first().uriValue();
                final List<Inst> program = program(route.second());
                final fURI cut = null == key.q(TIMEOUT) ? CUT_INST_TID : CUT_INST_TID.q(TIMEOUT, key.q(TIMEOUT));
                final Obj tail = program.isEmpty() ? code(new ArrayList<>()) : code(program).tryToInst();
                final Obj split = split(key.q(SPLIT));
                // A DESIGNATOR KEY NAMES ITS PLACES WHEN THE BRANCH RUNS (stage 3): peer is every dialed peer and # every
                // reachable place, as ?place resolves them (PlaceResolver), one lane at each; any other key is the one
                // place its path names (none at home), aligned as a move's target
                final List<fURI> places = PEER.equals(key.qLess().toString()) || HASH_FURI.equals(key.qLess())
                        ? PlaceResolver.resolve(PlaceSet.of(uri(key.qLess())), Env.snapshot()).stream().map(PlaceResolver.ResolvedPlace::authority).toList()
                        : Collections.singletonList(home(key) ? null : key.qLess());
                // a home program whose head names another place would read as a lane there once its key is gone, so a cut
                // that stays keeps it home, as it times a home key's program, and it keeps the head's ?timeout when the key
                // states none, so the lane's thread still waits as long as the head may run. A head that names home or a
                // computed place reads as home already, and stays bare, every later timed move in sight of the lane
                final fURI headed = program.isEmpty() ? null : headed(program.getFirst());
                final boolean leaves = null != headed && !home(headed);
                final fURI stay = !leaves || null != key.q(TIMEOUT) || !program.getFirst().tid().hasQ(TIMEOUT) ? cut
                        : CUT_INST_TID.q(TIMEOUT, program.getFirst().tid().q(TIMEOUT));
                return places.stream().map(at -> {
                    // the hop budget the key states rides with its place
                    final fURI place = null == at || null == key.q(HOP) ? at : at.q(HOP, key.q(HOP));
                    return new Lane(place, program, share(split, place),
                            null != place ? instB(cut, lst(uri(Machine.Helper.align(Machine.current(), place)), tail))
                                    : null == key.q(TIMEOUT) && !leaves ? tail : instB(stay, lst(uri(fURI.Singleton.f("~")), tail)));
                });
            }).toList();
            // A BRANCH DEALS ITS LHS OR COPIES IT (Marko, 2026-11-24: mixed copy and part keys are refused, "default")
            if (lanes.stream().anyMatch(lane -> 0 < lane.weight()) && lanes.stream().anyMatch(lane -> 0 == lane.weight()))
                throw MTronException.of("a branch either deals its lhs or copies it, so its keys cannot mix copy and part: %s", branch);
            return lanes;
        }
        // the split is read once, and each lane takes its share of it
        final Obj split = split(branch.tid().q(SPLIT));
        return branch.args().<Obj>elements().filter(member -> !uri(LHS).equals(member)).map(member -> {
            final List<Inst> insts = program(member);
            final fURI place = insts.isEmpty() ? null : headed(insts.getFirst());
            final boolean lane = null != place && !home(place);
            return new Lane(lane ? place : null,
                    null == place ? insts : CUT_INST_TID.equals(insts.getFirst().tid().basePath()) ? program(insts.getFirst().arg(1))
                            : insts.subList(1, insts.size()),
                    share(split, lane ? place : null), member);
        }).toList();
    }

    /**
     * The place a member's head names when it is a move, or the cut a move compiles to: {@code move()} is
     * {@code move(<..>)}, and a target computed when the move runs names no place here ({@code null}, as for any other
     * head).
     */
    private static fURI headed(final Inst head) {
        final fURI base = head.tid().basePath();
        return CUT_INST_TID.equals(base) ? head.arg(0).uriValue()
                : MOVE_INST_TID.equals(base) && head.arg(0).isUri() ? head.arg(0).uriValue()
                : MOVE_INST_TID.equals(base) && head.arg(0).isNoObj() ? fURI.Singleton.f("..")
                : null;
    }

    /**
     * Whether a branch is a LAYOUT: it routes its lanes (a key, a lane headed by a move or a cut) or splits its lhs
     * ({@code ?split}, a deal or a copy), so a planner does not plan around it, a rewrite neither sums nor factors its
     * members, and only a lone lane that runs here is inlined.
     */
    public static boolean isLayout(final Inst inst) {
        return BRANCH_INST_TID.equals(inst.tid().basePath())
                && (inst.args().isRec() || inst.tid().hasQ(SPLIT) || lanes(inst).stream().anyMatch(lane -> !lane.home()));
    }

    /**
     * A member's instructions: a call's own, else the map that emits the value.
     */
    public static List<Inst> program(final Obj member) {
        return member.isCall() ? member.asCall().insts() : List.of((Inst) map_(member).tryToInst());
    }

    /**
     * THE BODY: route the lhs through the branches. Every lane is submitted on its own {@code virtual::T} before any
     * is awaited, and a lane dealt nothing runs nothing. A SEEDED lane — a program that starts from its own
     * {@code start(...)} — takes none of the lhs: it was dealt its part when it was planned, so its weight records that
     * share rather than asking for one, and a branch of seeded lanes alone needs no lhs at all.
     */
    public Obj route(final Obj lhs) {
        if (!isLayout(this)) {
            // the member's own application already carries its multiplicity (an inst's coefficient, or a code's
            // head-inst coefficient); the branch contributes its lhs. UNDER A BATCHING GRAIN ({n} a page, {*} the whole
            // lhs) the lhs is a bag, and a member runs over it as a lane does, start(bag).member: the start streams the
            // bag, so a map meets one obj at a time and a fold the whole bag, and the objs carry their own multiplicity.
            // Applied to the bag whole, a one-instruction member read it as one lhs (plus(1) against int{2}), and the
            // bag's count multiplied what its objs already count ({3,2}.branch?<=int{*}(id()) was {{2}3,{2}2}).
            // THE MERGE IS A RETURN HOME (Marko, 2026-11-24): a member runs on the caller's thread, and a cut into
            // another machine persists there, as a move does, so each member runs from the machine the branch stands
            // in and the caller is left there: the next member and the instruction after the branch start from home
            final Machine home = Machine.current();
            return objs(this.args().elements().map(o -> Machine.Helper.evalAt(home, () -> this.isBatching()
                    ? start_(lhs).addInsts(program(o)).apply(noobj())
                    : o.apply(lhs).c(c -> lhs.c().mult(c)))));
        }
        final List<Lane> lanes = lanes(this);
        final long[] weights = lanes.stream().mapToLong(lane -> lane.seeded() ? 0L : lane.weight()).toArray();
        final List<List<Obj>> parts = deal(lhs, weights);
        final List<Obj> threads = IntStream.range(0, lanes.size())
                .mapToObj(r -> 0 == weights[r] ? lane(lanes.get(r), lhs)
                        : parts.get(r).isEmpty() ? noobj()
                        : lane(lanes.get(r), objs(parts.get(r))))
                .toList();
        return objs(threads.stream().map(thread -> {
            if (thread.isNoObj())
                return thread;
            // A LANE WHOSE WAIT FAILED IS STOPPED: the branch answers its failure, so nothing the lane does after counts
            final AbstractThread lane = (AbstractThread) thread;
            try {
                final Obj answer = lane.at(uri(RHS));
                if (!lane.future().isDone())
                    lane.stop();
                return answer;
            } catch (final RuntimeException e) {
                lane.stop();
                throw e;
            }
        }));
    }

    /**
     * THE SPLIT a branch or a key states, read as the object it is (the grammar reads a leading coefficient,
     * {@code {4}part}, as it reads {@code ?hop={2}4}): {@code copy}, {@code part} or a place set, the bias. None is
     * {@code noobj}, and any other split is refused, never read as a copy: a misspelled part would hand every lane the
     * whole lhs and double-count its fold.
     */
    private static Obj split(final String split) {
        final Obj value = null == split ? noobj() : ObjmtronSerializer.parse(split);
        // a stated split that reads as nothing ({0}part) is no split either, never the copy no split is
        if (null != split && (value.isNoObj() || (!value.isLst() && !value.isRec()
                && !(value.isUri() && (PART.equals(value.uriValue().name()) || COPY.equals(value.uriValue().name()))))))
            throw MTronException.of("?%s=%s is not a split a branch performs: %s, %s or a place set", SPLIT, split, PART, COPY);
        return value;
    }

    /**
     * A LANE'S SHARE of the lhs under the split its branch (or its key) states ({@link #split}), for the lane at
     * {@code place} ({@code null} at home). {@code copy}, or no split, hands every lane the whole lhs: a share of zero.
     * {@code part} deals the lhs evenly, one share each ({@code {w}part}, w apiece, as a key marks itself). A PLACE SET
     * IS THE BIAS (Marko, 2026-11-24: "?split= is bias and stays on branch as a query param as its a concept relative to
     * all lanes of the branch"): {@code ?split=[{2}//a,//b]} deals each lane the demand of the designator that names its
     * place, PER LANE ("treat each lane as if they are unique"), so two lanes at //a take two shares apiece. A designator
     * names a lane as {@code ?place} reads it ({@link PlaceResolver}): {@code local} a home lane; an address a lane at
     * its host, through its alias, its port no selector unless the alias states one ({@link Network.Helper#names}); an
     * authority the one service it spells, exactly its origin; {@code peer} a lane at any host; and {@code #} any lane.
     * The most specific naming wins ({@link PlaceResolver#specificity}), its demands summed, and a lane no designator
     * names takes one share. A point is never a share: it is copies of what its lane answers. A share below one is
     * refused, and so is a demand with no least count ({@code {**}}).
     */
    protected static long share(final Obj split, final fURI place) {
        if (split.isNoObj() || (split.isUri() && COPY.equals(split.uriValue().name())))
            return 0L;
        final fURI at = split.isUri() || null == place ? null : Machine.Helper.align(Machine.current(), place);
        final List<PlaceSet.Designator> bias = split.isUri() ? List.of() : PlaceSet.of(split).designators();
        final List<PlaceSet.Designator> named = IntStream.range(0, 3).boxed()
                .map(pass -> bias.stream().filter(designator -> pass == PlaceResolver.specificity(designator.form())
                        && switch (designator.form()) {
                    case LOCAL -> null == place;
                    case PEER -> null != at && at.hasHost();
                    case ANY -> true;
                    case ADDRESS -> {
                        final fURI address = Machine.Helper.align(Machine.current(), designator.uri());
                        yield null != at && address.hasHost() && address.host().equals(at.host()) && Network.Helper.names(address, at);
                    }
                    case AUTHORITY -> null != at && designator.uri().origin().equals(at.origin());
                }).toList())
                .filter(designators -> !designators.isEmpty())
                .findFirst()
                .orElse(List.of());
        final Long share = split.isUri() ? split.c().min()
                : named.isEmpty() ? Long.valueOf(1L)
                : named.stream().anyMatch(designator -> null == designator.demand().min()) ? null
                : Long.valueOf(named.stream().mapToLong(designator -> designator.demand().min()).sum());
        if (null == share || 1L > share)
            throw MTronException.of("a lane's share is one or more: %s gives %s", split, null == place ? LOCAL : place);
        return share;
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
     * ONE LANE: a {@code virtual::T} running the lane's member, submitted and not awaited (Marko, 2026-11-05). A LANE IS
     * A MOVE (Law.nomadic, stage 2): its member is headed by the cut its move compiles to ({@code <p> => program} is
     * {@code cut(<p>, program)}), so the cut routes it as it routes any move: a path is a machine of this server's tree
     * ({@code <..>} its parent, {@code </.>} the root, {@code </abc>} from the root, {@code <abc>} a child), switched to
     * and compiled there, and a host is a peer, which compiles what it is shipped; and the cut times it, as it holds the
     * lane's {@code ?timeout}. The member runs over {@code start(input)}, so a {@code {*}} dom's bag reaches a
     * {@code {1}} program member by member and the input's coefficient rides the start; a SEEDED lane starts from its own
     * part, so its member runs as it stands. A keyed lane's program is its cut's whole tail: its own {@code explain()}
     * or {@code profile()} run where the lane runs (a profiled lane answers its place's report, which nests the place's
     * own lanes), where the extractor would end a moved chain's stretch before them, the return home.
     * <p>
     * A HOME LANE GETS NO MOVE ({@link #home}): it runs in the machine the branch runs in. The lane's thread starts
     * where the caller stands, so a lane whose cut moves into another machine moves only its own thread: the caller is
     * never left anywhere else.
     */
    private static Obj lane(final Lane lane, final Obj input) {
        final List<Inst> member = program(lane.member());
        final Obj code = lane.seeded() ? code(member) : start_(input).addInsts(member);
        // THE LANE'S THREAD WAITS AS LONG AS ITS CUTS MAY RUN, and a second more: a cut times its own run from when it
        // starts, after the lane has compiled, so the branch's wait (the thread's own default, else) must not cut a timed
        // lane short; the cut's wait is the one that times out. A move not yet compiled to its cut times the same
        final long seconds = member.stream()
                .filter(inst -> (CUT_INST_TID.equals(inst.tid().basePath()) || MOVE_INST_TID.equals(inst.tid().basePath()))
                        && inst.tid().hasQ(TIMEOUT))
                .mapToLong(inst -> machInstSet.Helper.seconds(inst.tid().q(TIMEOUT)).realValue().longValue())
                .max().orElse(-1L);
        return (0 > seconds ? VirtualThread.virtual(code) : new VirtualThread(CommonUtil.mutableMap(uri(CODE), code,
                uri(TIMEOUT), machInstSet.Helper.seconds(String.valueOf(seconds + 1))), MACH_VIRTUAL_THREAD_TID, null))
                .apply(noobj());
    }

    /**
     * Whether a key names the machine the branch runs in: {@code <~>} or {@code <.>}, whatever its query. Every other
     * place is another machine, even one that turns out to be this one ({@code </.>} at the root).
     */
    public static boolean home(final fURI key) {
        final fURI place = key.qLess();
        return fURI.Singleton.f("~").equals(place) || fURI.Singleton.f(".").equals(place);
    }

}
