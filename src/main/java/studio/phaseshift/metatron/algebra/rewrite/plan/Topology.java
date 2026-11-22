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

import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.algebra.TheoryHelper;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE DISTRIBUTION TOPOLOGY OF A COMPILED CHAIN (Marko, 2026-11-20), the explain extension read at
 * {@code explain()>>ext/topology/format}. It draws no instructions, only the point coefficients they leave: the seed,
 * each lane's share and what each of its instructions leaves, the lanes merging at home, and what the master's
 * instructions leave.
 * <pre>
 *       ┌─►//0─{10}─{1}─┐
 * {15}──┼─►//1─{2}──{1}─┼─►//local─{3}─{1}
 *       └─►//2─{3}──{1}─┘
 * </pre>
 * A REPEAT IS DRAWN AS A LOOP (Marko, 2026-11-22): its body once, as any flow, with a back edge from where it leaves
 * to where it enters, so law_bsp's superstep reads as what it is, a fan run again and again
 * ({@code {1,2,3}.repeat?<=int{*}(code=>plus(1),…)} dealt over two peers, each member reaching a lane at most once):
 * <pre>
 *        ┌─►//0─{0,3}─{0,3}─┐
 * {3}─┬──┤                  ├─►//local─{0,6}─┬─{*}
 *     ▲  └─►//1─{0,3}─{0,3}─┘                │
 *     └──────────────────────────────────────┘
 * </pre>
 * A COEFFICIENT IS A PRODUCT, READ LEFT TO RIGHT (Marko): each instruction's contract states the coefficient it takes
 * and the one it answers. One that takes at most one obj applies once per obj, so its range multiplies the stream's
 * ({@code {3}} through {@code plus}, {@code int{1} => int{1}}, stays {@code {3}}); one that takes the stream whole
 * applies once and leaves its range ({@code {15}} through {@code sum} is {@code {1}}); one that takes nothing starts a
 * stream ({@code start({1,2,3})} is {@code {3}}); a point copies what it answers. A branch is the ring's sum: its
 * lanes' coefficients add where they merge. A lane's place is kept as its address and named where the drawing is
 * made, by the name that machine's network calls it ({@code ws://localhost:1111} reads {@code //0}), so a child's lanes
 * read by the asker's names. An address means what it meant to the machine that planned the fan: one with no host is
 * that machine ({@code //local} for this one, the child's own place inside its subtree), and a loopback one is on
 * that machine's host, so a peer's localhost is never read as this machine's.
 * <p>
 * THE TOPOLOGY IS DATA, ITS DRAWING IS DERIVED (Marko, 2026-11-20: get back the explain rec, recreate the format
 * locally). The extension's rec is {@code [flow => …, format => <the drawing>]}: the flow travels where a drawing, a
 * lambda, cannot. A lane that still carries a hop budget re-plans where it runs, which only that machine can compile,
 * so that machine explains the lane: a peer sends its flow back, a machine here explains it in place. The flow is drawn
 * inside the lane, a tree within the tree whose merge is the lane's own place. Each child does the same for its own
 * lanes, so the whole tree comes back.
 */
public final class Topology {

    private static final String HOME = "//" + LOCAL;

    private Topology() {
        // static only
    }

    /**
     * The extension's rec, or noobj for a chain that fans out nowhere: a chain that runs in one place has no topology.
     */
    public static Obj explain(final Code code) {
        final List<Inst> insts = code.insts();
        if (insts.stream().noneMatch(Topology::fansAnywhere))
            return noobj();
        final List<Obj> flow = new ArrayList<>();
        walk(insts, Rewriter.Helper.contracts(code), null, flow); // nothing flows before the head
        return topology(lst(flow));
    }

    /**
     * THE FLOW of a run of instructions, appended to {@code flow} from the coefficient {@code in} flowing into it:
     * runs and fans in chain order. A run is the coefficients one place's instructions leave; a fan is the lst of its
     * lanes, each naming its place (two lanes may share a name, as two home lanes do), its stage (what the lane's
     * instructions leave) and, when the lane re-plans where it runs, that machine's own topology. A REPEAT IS A LOOP
     * (Marko, 2026-11-22): its body is walked as a flow of its own, from the frontier flowing into it, into the segment
     * {@code [loop => <the body's flow>]}, drawn once with a back edge; the run after it starts from what the repeat
     * leaves.
     */
    private static void walk(final List<Inst> insts, final List<Inst> contracts, final cInt in, final List<Obj> flow) {
        cInt c = in;
        List<Obj> run = new ArrayList<>();
        for (int i = 0; i < insts.size(); i++) {
            if (loops(insts.get(i))) {
                if (!run.isEmpty())
                    flow.add(lst(run));
                final List<Inst> body = insts.get(i).arg(f(CODE), 1).asCall().insts();
                final List<Obj> inner = new ArrayList<>();
                walk(body, Rewriter.Helper.contracts(code(body)), c, inner);
                flow.add(rec(uri(LOOP), lst(inner)));
                run = new ArrayList<>();
                run.add(label(c = leaves(c, insts.get(i), contracts.get(i))));
                continue;
            }
            if (!fans(insts.get(i))) {
                run.add(label(c = leaves(c, insts.get(i), contracts.get(i))));
                continue;
            }
            final List<Obj> fan = new ArrayList<>();
            final List<Obj> seeds = new ArrayList<>();
            cInt firsts = cInt.ZERO();
            cInt lasts = cInt.ZERO();
            cInt first = null;
            for (final Rel route : BranchInst.routes(insts.get(i)).<Rel>elements().toList()) {
                final List<Inst> program = route.second().isCall()
                        ? route.second().asCall().insts()
                        : List.of((Inst) map_(route.second()).tryToInst());
                final boolean seeded = !program.isEmpty() && START_INST_TID.equals(program.getFirst().tid().basePath());
                final String split = route.first().uriValue().q(SPLIT);
                final List<Inst> laneContracts = Rewriter.Helper.contracts(code(program));
                // a seeded lane's share is its start; a lane dealt a computed bag is dealt it when the branch runs, so
                // each member reaches it at most once ({n} times {?}), and a copy takes the whole bag
                cInt s = seeded ? null
                        : null == c ? cInt.MAYBESOME()
                        : null == split || split.endsWith(COPY) ? c
                        : c.mult(c.maybe());
                final List<cInt> stages = new ArrayList<>();
                if (!seeded)
                    stages.add(s);
                for (int j = 0; j < program.size(); j++)
                    stages.add(s = leaves(s, program.get(j), laneContracts.get(j)));
                seeds.add(seeded ? program.getFirst().arg(0) : null);
                first = null == first ? stages.getFirst() : first;
                firsts = firsts.plus(stages.getFirst());
                lasts = lasts.plus(stages.getLast());
                fan.add(rec(uri(PLACE), uri(route.first().uriValue().resolve().qLess()),
                        uri(STAGE), lst(stages.stream().map(t -> (Obj) label(t)).toList()),
                        uri(TOPOLOGY), seeded ? theirs(route.first().uriValue(), program) : noobj()));
            }
            // a head branch's seed: lanes that all start from one seed hold it once (a copy, or the run axis, whose
            // lanes split a span's exponent), and lanes that start from parts of it sum to it
            final boolean oneSeed = !seeds.isEmpty() && seeds.stream().allMatch(Objects::nonNull)
                    && 1 == seeds.stream().distinct().count();
            if (null == c)
                run.add(label(oneSeed ? first : firsts));
            if (!run.isEmpty())
                flow.add(lst(run));
            flow.add(lst(fan));
            run = new ArrayList<>();
            run.add(label(c = lasts));
        }
        flow.add(lst(run));
    }

    /**
     * A topology rec over its flow, the format drawn from the flow wherever the rec stands.
     */
    private static Rec topology(final Lst flow) {
        return rec(uri(FLOW), flow, uri(FORMAT), auto_(() -> str(draw(flow))));
    }

    /**
     * A LANE WITH LEVELS LEFT RE-PLANS WHERE IT RUNS (BranchInst#lane), so the machine it runs in explains it, by its
     * own compiler and network: a peer is sent the lane's code with {@code explain()>>ext/topology/flow} through the
     * network's eval ({@link Network#submit}) and answers the flow, and a place on this machine explains the lane in
     * the machine it names. Only a leaf in the form the planner stamps it is left alone (Shard#stampHop: a point on the
     * head and no other budget), since every run of it inherits that point. The planner reads any other lane's budgets
     * run by run (Shard#apply), at cuts only that machine types, so any other lane is asked, and its machine answers
     * what it plans. Only a lane that starts from its own part is asked (the caller's test): one dealt a bag the branch
     * computes is planned only when the bag arrives, and explain runs nothing. A refusal is kept as its message: a
     * peer's, a path that names no machine, and a head budget that cannot be read, this machine's own diagnosis (the
     * planner there declines it, the rewriter keeps the code as written, and the lane runs unplanned).
     */
    private static Obj theirs(final fURI key, final List<Inst> program) {
        try {
            final Hop head = Shard.hopAxis(List.of(program.getFirst()), null);
            if (null != head && (head.isPoint() || head.arity().isZero()) && program.stream().skip(1)
                    .noneMatch(inst -> inst.tid().hasQ(HOP) || inst.tid().hasQ(Placement.HOP_ALIAS)))
                return noobj();
            final fURI place = key.resolve().qLess();
            if (null == place.host())
                return Machine.Helper.evalAt(Machine.current().fetch(key.qLess()),
                        () -> explain(code(program).rewrite().resolve(noobj())));
            final List<Inst> asked = new ArrayList<>(program);
            asked.add(instB(EXPLAIN_INST_TID, lst()));
            asked.add(instB(RSHIFT_INST_TID, lst(uri(f(EXT).extend(TOPOLOGY).extend(FLOW)))));
            final Obj flow = Machine.current().network().submit(place, code(asked));
            return flow.isLst() ? topology(flow.asLst()) : flow.isFail() ? str(flow.toString()) : noobj();
        } catch (final MTronException e) {
            return str(e.getMessage());
        }
    }

    private static boolean fans(final Inst inst) {
        // by its address, not its class: a branch inside a repeat's (blocked) code is never bound to a BranchInst
        return (inst instanceof BranchInst || BRANCH_INST_TID.equals(inst.tid().basePath())) && inst.args().isRec();
    }

    /**
     * A repeat with a code to walk: drawn as a loop.
     */
    private static boolean loops(final Inst inst) {
        return REPEAT_INST_TID.equals(inst.tid().basePath()) && inst.arg(f(CODE), 1).isCall();
    }

    /**
     * A chain has a topology when something in it fans out: a branch, or a branch inside a repeat's body.
     */
    private static boolean fansAnywhere(final Inst inst) {
        return fans(inst) || (loops(inst)
                && inst.arg(f(CODE), 1).asCall().insts().stream().anyMatch(Topology::fansAnywhere));
    }

    /**
     * THE COEFFICIENT AN INSTRUCTION LEAVES, from its contract's: a dom of none starts a stream (its range is the
     * stream), a dom of at most one applies per obj (its range multiplies the stream's), and any other dom takes the
     * stream whole (its range is what is left). The instruction's own coefficients then apply: a point copies, and a
     * span range is a sum of terms, one per exponent, as Inst walks it: the zero step is the lhs itself, each positive
     * step leaves what the instruction leaves ({@code {0,8}plus} turns one obj into nine; {@code {0,1}sum} over three
     * is the three and their sum, four), and the negative steps walk the declared converse, one pointed step per
     * exponent, else run as one blocked limbo per obj, which takes its point only when it runs. An exact span is one
     * composition and keeps the count. An instruction no contract types leaves any number.
     */
    private static cInt leaves(final cInt in, final Inst inst, final Inst contract) {
        if (null == contract || !contract.tid().hasDom() || !contract.tid().hasRng())
            return cInt.MAYBESOME();
        final cInt dom = contract.dom().c();
        final cInt rng = contract.rng().c();
        final cInt out = null == in || dom.isZero() ? rng
                : dom.within(dom.maybe()) ? in.mult(rng)
                : rng;
        final cInt point = inst.tid().d();
        final cInt span = inst.tid().b();
        final cInt pointed = null == point || point.isOne() ? out : out.mult(point);
        if (null == span || span.isExact())
            return pointed;
        if (null == in || null == span.min() || null == span.max())
            return pointed.mult(span.maybeSome());
        final long below = Math.max(0L, Math.min(span.max(), -1L) - span.min() + 1);
        final long above = Math.max(0L, span.max() - Math.max(span.min(), 1L) + 1);
        final cInt pointedIn = null == point || point.isOne() ? in : in.mult(point);
        final cInt zero = span.min() <= 0 && 0 <= span.max() ? pointedIn : cInt.ZERO();
        // the converse is claimed by the bare operation, read as Inst reads it (no span, no point)
        final Obj claim = 0 < below
                ? Machine.read(PendingWrites.decq(Inst.Helper.operationTid(contract.tid().b(null).cLess())))
                : noobj();
        final cInt negative = 0 >= below ? cInt.ZERO()
                : claim.isRec() && null != TheoryHelper.roleAddress(claim.asRec(), CONVERSE)
                ? pointedIn.mult(cInt.of(below))
                : in;
        return pointed.mult(cInt.of(above)).plus(zero).plus(negative);
    }

    /**
     * A LANE'S PLACE, BY THE NAME THE NETWORK CALLS IT (Marko, 2026-11-20): the key's origin resolved against the
     * network's prefixes, so a peer named ginger reads //ginger, and a peer the network names nothing keeps its
     * authority. The key is the address the machine that planned the fan wrote: one with no host is that machine,
     * {@code home}, and a loopback one is on that machine's {@code host} (null for this one), so a peer's localhost is
     * never read as this machine's.
     */
    private static String place(final fURI key, final String home, final String host) {
        final fURI written = key.resolve().qLess();
        if (null == written.host())
            return home;
        final fURI place = null != host && written.isLoopbackHost() ? written.host(host) : written;
        final Network network = Machine.current().network();
        final List<Rel> prefixes = null == network ? List.of()
                : Component.Prefix.Helper.effectivePrefixes(network).<Rel>elements().toList();
        return prefixes.stream()
                .filter(row -> row.second().isUri() && place.host().equals(row.second().uriValue().host())
                        && place.port() == row.second().uriValue().port())
                .map(row -> Network.Helper.markedName(row.first().uriValue()).toString())
                .findFirst()
                .orElse("//" + place.host() + (0 > place.port() ? "" : ":" + place.port()));
    }

    private static Obj label(final cInt c) {
        return str("{" + c + "}");
    }

    /**
     * THE DRAWING OF A FLOW: its runs and fans side by side along one main line, a lane that re-planned where it ran
     * drawn inside its fan as that machine planned it, a tree within the tree (Marko, 2026-11-20).
     */
    static String draw(final Lst flow) {
        return block(flow, HOME, null).rows().stream()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n", "\n", "\n"));
    }

    private record Block(List<String> rows, int main) {
    }

    /**
     * A flow as one block: each run and fan drawn around its own main row, laid side by side. A run after a fan starts
     * where the fan's lanes merge, at {@code home}: //local for the explaining machine, a lane's own place inside it,
     * whose loopback addresses are on {@code host}. A run is never empty, so an empty segment is a fan of no lanes (a
     * branch keyed by nothing).
     */
    private static Block block(final Lst flow, final String home, final String host) {
        final List<Block> blocks = new ArrayList<>();
        boolean merged = false;
        boolean looped = false;
        for (final Obj segment : flow.<Obj>elements().toList()) {
            if (segment.isRec() && segment.asRec().at(uri(LOOP)).isLst()) {
                blocks.add(loop(segment.asRec().at(uri(LOOP)).asLst(), home, host));
                merged = false;
                looped = true;
                continue;
            }
            final List<Obj> parts = segment.asLst().<Obj>elements().toList();
            if (parts.isEmpty() || parts.getFirst().isRec()) {
                blocks.add(fan(parts, home, host));
                merged = true;
                looped = false;
            } else {
                blocks.add(new Block(List.of((merged ? "─►" + home + "─" : looped ? "─" : "")
                        + parts.stream().map(Obj::strValue).collect(Collectors.joining("─"))), 0));
                merged = false;
                looped = false;
            }
        }
        final int above = blocks.stream().mapToInt(Block::main).max().orElse(0);
        final int under = blocks.stream().mapToInt(block -> block.rows().size() - 1 - block.main()).max().orElse(0);
        final List<StringBuilder> rows = IntStream.rangeClosed(0, above + under)
                .mapToObj(r -> new StringBuilder()).toList();
        for (final Block block : blocks) {
            final int width = block.rows().stream().mapToInt(String::length).max().orElse(0);
            for (int r = 0; r < rows.size(); r++) {
                final int row = r - (above - block.main());
                final String text = 0 <= row && row < block.rows().size() ? block.rows().get(row) : "";
                rows.get(r).append(text).repeat(" ", width - text.length());
            }
        }
        return new Block(rows.stream().map(StringBuilder::toString).toList(), above);
    }

    /**
     * A FAN: its lanes stacked, each joining the fork and the join on its own main row. A plain lane is its stages, the
     * columns padded to the widest coefficient among the plain lanes; a lane that re-planned at its peer is the peer's
     * own drawing, its merge at the lane's place, and a lane its machine could not explain carries the message under
     * its stages. Every lane runs out to the join, and the fan carries a │ between its outer lanes. An odd fan puts its
     * middle lane on the main line; an even one puts a joint row between its halves, and the main line enters the fork
     * through two dashes, {11}──┤.
     */
    private static Block fan(final List<Obj> lanes, final String home, final String host) {
        if (lanes.isEmpty())
            return new Block(List.of("──"), 0);
        // a place is named where the drawing is made, so a child's lanes read by the names this machine calls them;
        // a name written as one draws as written
        final List<Obj> addresses = lanes.stream().map(lane -> (Obj) lane.asRec().at(uri(PLACE))).toList();
        final List<String> places = addresses.stream()
                .map(place -> place.isUri() ? place(place.uriValue(), home, host) : place.strValue()).toList();
        final List<Obj> theirs = lanes.stream().map(lane -> (Obj) lane.asRec().at(uri(TOPOLOGY))).toList();
        final List<List<String>> stages = lanes.stream()
                .map(lane -> lane.asRec().at(uri(STAGE)).asLst().<Obj>elements().map(Obj::strValue).toList()).toList();
        final int placeWidth = places.stream().mapToInt(String::length).max().orElse(0);
        final int[] widths = new int[stages.stream().mapToInt(List::size).max().orElse(0)];
        for (int l = 0; l < lanes.size(); l++)
            for (int j = 0; !theirs.get(l).isRec() && j < stages.get(l).size(); j++)
                widths[j] = Math.max(widths[j], stages.get(l).get(j).length());
        final List<Block> blocks = new ArrayList<>();
        for (int l = 0; l < lanes.size(); l++) {
            final String entry = "─►" + places.get(l) + "─".repeat(placeWidth - places.get(l).length()) + "─";
            final String indent = " ".repeat(entry.length());
            if (theirs.get(l).isRec()) {
                // the lane's machine planned its subtree, so a loopback address in it is on that machine's host
                final fURI at = addresses.get(l).isUri() ? addresses.get(l).uriValue().resolve().qLess() : null;
                final String inner = null == at || null == at.host() || at.isLoopbackHost() ? host : at.host();
                final Block child = block(theirs.get(l).asRec().at(uri(FLOW)).asLst(), places.get(l), inner);
                blocks.add(new Block(IntStream.range(0, child.rows().size())
                        .mapToObj(r -> (r == child.main() ? entry : indent) + child.rows().get(r)).toList(),
                        child.main()));
            } else {
                final StringBuilder body = new StringBuilder(entry);
                for (int j = 0; j < stages.get(l).size(); j++) {
                    final String stage = stages.get(l).get(j);
                    body.append(0 == j ? "" : "─").append(stage).repeat("─", widths[j] - stage.length());
                }
                // a lane its machine could not explain carries the message under its stages
                blocks.add(new Block(Stream.concat(Stream.of(body.toString()), theirs.get(l).isStr()
                        ? theirs.get(l).strValue().lines().map(line -> indent + line) : Stream.empty()).toList(), 0));
            }
        }
        final int width = blocks.stream().flatMap(block -> block.rows().stream())
                .mapToInt(String::length).max().orElse(0) + 1;
        // an even fan's joint row sits between its halves, where the main line meets the fork
        final int half = blocks.size() / 2;
        final int joint = 0 == blocks.size() % 2
                ? blocks.subList(0, half).stream().mapToInt(block -> block.rows().size()).sum()
                : -1;
        final List<String> rows = new ArrayList<>();
        final List<Integer> mains = new ArrayList<>();
        for (int l = 0; l < blocks.size(); l++) {
            if (-1 != joint && half == l)
                rows.add(" ".repeat(width));
            final Block lane = blocks.get(l);
            for (int r = 0; r < lane.rows().size(); r++) {
                final String row = lane.rows().get(r);
                rows.add(row + (r == lane.main() ? "─" : " ").repeat(width - row.length()));
            }
            mains.add(rows.size() - lane.rows().size() + lane.main());
        }
        final int main = -1 == joint ? mains.get(half) : joint;
        final int first = mains.getFirst();
        final int last = mains.getLast();
        final List<String> drawn = new ArrayList<>(rows.size());
        for (int r = 0; r < rows.size(); r++) {
            final boolean lane = mains.contains(r);
            drawn.add((main == r ? "──" : "  ") + side(r, first, last, main, lane, "┌├┼└┤")
                    + rows.get(r) + side(r, first, last, main, lane, "┐┤┼┘├"));
        }
        return new Block(drawn, main);
    }

    /**
     * A LOOP (Marko, 2026-11-22): its body drawn as any flow, between two gutters. The main line enters the body
     * through a ┬ and leaves it through another, where it also goes on to whatever follows the repeat. From the right ┬
     * the back edge runs down (│), back under the body along the bottom rail, and up the left gutter into the entry,
     * the ▲ just under it marking which way it runs: the frontier a pass leaves is the next pass's.
     */
    private static Block loop(final Lst inner, final String home, final String host) {
        final Block drawn = block(inner, home, host);
        // a body that starts with a run joins the entry through a dash, as a fan's own ── does
        final boolean run = !drawn.rows().get(drawn.main()).startsWith("─");
        final List<String> lines = new ArrayList<>(IntStream.range(0, drawn.rows().size())
                .mapToObj(r -> (run ? r == drawn.main() ? "─" : " " : "") + drawn.rows().get(r)).toList());
        // a one-row body (a fan of one lane, a run) gets a row under its main line, where the ▲ stands
        if (lines.size() - 1 == drawn.main())
            lines.add("");
        final Block body = new Block(lines, drawn.main());
        final int width = body.rows().stream().mapToInt(String::length).max().orElse(0);
        final List<String> rows = new ArrayList<>();
        for (int r = 0; r < body.rows().size(); r++) {
            final String row = body.rows().get(r);
            final String padded = row + (r == body.main() ? "─" : " ").repeat(width - row.length());
            final String left = r < body.main() ? "  " : r == body.main() ? "─┬" : r == body.main() + 1 ? " ▲" : " │";
            final String right = r < body.main() ? "  " : r == body.main() ? "─┬" : " │";
            rows.add(left + padded + right);
        }
        rows.add(" └" + "─".repeat(width + 1) + "┘");
        return new Block(rows, body.main());
    }

    /**
     * One side of a fan at a row, from its five glyphs (top, lane, main lane, bottom, joint): a lane's own row joins
     * it, the joint row of an even fan meets the main line, the rows between the outer lanes carry a │, and a fan of
     * one lane is a straight line.
     */
    private static char side(final int row, final int first, final int last, final int main, final boolean lane,
                             final String glyphs) {
        if (first == last)
            return lane ? '─' : ' ';
        if (lane)
            return row == first ? glyphs.charAt(0)
                    : row == last ? glyphs.charAt(3)
                    : row == main ? glyphs.charAt(2)
                    : glyphs.charAt(1);
        if (row == main)
            return glyphs.charAt(4);
        return first < row && row < last ? '│' : ' ';
    }
}
