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
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.CONVERSE;
import static studio.phaseshift.metatron.Tokens.COPY;
import static studio.phaseshift.metatron.Tokens.EXT;
import static studio.phaseshift.metatron.Tokens.FLOW;
import static studio.phaseshift.metatron.Tokens.FORMAT;
import static studio.phaseshift.metatron.Tokens.LOCAL;
import static studio.phaseshift.metatron.Tokens.PLACE;
import static studio.phaseshift.metatron.Tokens.SPLIT;
import static studio.phaseshift.metatron.Tokens.STAGE;
import static studio.phaseshift.metatron.Tokens.TOPOLOGY;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EXPLAIN_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.RSHIFT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
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
 *       ┌─//0─►{10}─{1}─┐
 * {15}──┼─//1─►{2}──{1}─┼──//local─►{3}─{1}
 *       └─//2─►{3}──{1}─┘
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
        if (insts.stream().noneMatch(Topology::fans))
            return noobj();
        final List<Inst> contracts = Rewriter.Helper.contracts(code);
        // THE FLOW: runs and fans in chain order. A run is the coefficients one place's instructions leave; a fan is
        // the lst of its lanes, each naming its place (two lanes may share a name, as two home lanes do), its stage
        // (what the lane's instructions leave) and, when the lane re-plans where it runs, that machine's own topology
        final List<Obj> flow = new ArrayList<>();
        List<Obj> run = new ArrayList<>();
        cInt c = null; // nothing flows before the head
        for (int i = 0; i < insts.size(); i++) {
            if (!fans(insts.get(i))) {
                run.add(label(c = leaves(c, insts.get(i), contracts.get(i))));
                continue;
            }
            final List<Obj> fan = new ArrayList<>();
            final List<Obj> seeds = new ArrayList<>();
            cInt firsts = cInt.ZERO();
            cInt lasts = cInt.ZERO();
            cInt first = null;
            for (final Rel route : ((BranchInst) insts.get(i)).routes().<Rel>elements().toList()) {
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
        return topology(lst(flow));
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
     * the machine it names. The planner there reads a budget run by run (Shard#apply): the head's run its own
     * {@code ?hop}, else the shard plan's (Shard#hop), and a later run its own, else the one before it. So a lane plans
     * nothing only when its head states a point and so does every {@code ?hop} it states, and a leaf asks nothing; any
     * other lane is asked, and its machine answers what it plans. Only a lane that starts from its own part is asked
     * (the caller's test): one dealt a bag the branch computes is planned only when the bag arrives, and explain runs
     * nothing. A refusal is kept as its message, a peer's and this machine's alike: a path that names no machine, and
     * a budget that cannot be read, which the planner there refuses too. A plan that declines refuses nothing, though:
     * the rewriter keeps the code as written (FixPointRewriter), so such a lane runs unplanned.
     */
    private static Obj theirs(final fURI key, final List<Inst> program) {
        try {
            // every budget the lane states is read, so one that cannot be read is its message wherever it stands
            final Predicate<Hop> plans = hop -> !hop.isPoint() && !hop.arity().isZero();
            final List<Hop> stated = program.stream().map(inst -> Shard.hopAxis(List.of(inst), Hop.point())).toList();
            final boolean unstated = null == Shard.hopAxis(List.of(program.getFirst()), null);
            if (!(unstated && plans.test(Shard.hop(PlanInterpreter.SHARD_PLAN))) && stated.stream().noneMatch(plans))
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
        return inst instanceof BranchInst && inst.args().isRec();
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
        for (final Obj segment : flow.<Obj>elements().toList()) {
            final List<Obj> parts = segment.asLst().<Obj>elements().toList();
            if (parts.isEmpty() || parts.getFirst().isRec()) {
                blocks.add(fan(parts, home, host));
                merged = true;
            } else {
                blocks.add(new Block(List.of((merged ? "──" + home + "─►" : "")
                        + parts.stream().map(Obj::strValue).collect(Collectors.joining("─"))), 0));
                merged = false;
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
                rows.get(r).append(text).append(" ".repeat(width - text.length()));
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
            final String entry = "─" + places.get(l) + "─".repeat(placeWidth - places.get(l).length()) + "─►";
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
                    body.append(0 == j ? "" : "─").append(stage).append("─".repeat(widths[j] - stage.length()));
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
