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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.CONVERSE;
import static studio.phaseshift.metatron.Tokens.COPY;
import static studio.phaseshift.metatron.Tokens.EXT;
import static studio.phaseshift.metatron.Tokens.FLOW;
import static studio.phaseshift.metatron.Tokens.FORMAT;
import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.Tokens.LOCAL;
import static studio.phaseshift.metatron.Tokens.PLACE;
import static studio.phaseshift.metatron.Tokens.SPLIT;
import static studio.phaseshift.metatron.Tokens.STAGE;
import static studio.phaseshift.metatron.Tokens.TOPOLOGY;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.EXPLAIN_INST_TID;
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
 * stream ({@code start({1,2,3})} is {@code {3}}); a point copies what it answers. A branch is the ring's sum: its lanes'
 * coefficients add where they merge. A lane is labelled by the name the network calls its place, and home is
 * {@code //local}.
 * <p>
 * THE TOPOLOGY IS DATA, ITS DRAWING IS DERIVED (Marko, 2026-11-20: get back the explain rec, recreate the format
 * locally). The extension's rec is {@code [flow => …, format => <the drawing>]}: the flow travels where a drawing, a
 * lambda, cannot. A lane that still carries a hop budget re-plans at its peer, which no other machine can compile for
 * it, so its peer explains the lane and sends its explanation back, and its flow is drawn here, under the peer's name.
 * Each child does the same for its own lanes, so the whole tree comes back.
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
        // THE FLOW: runs and fans in chain order. A run is the coefficients one place's instructions leave; a fan is the lst
        // of its lanes, each naming its place (two lanes may share a name, as two home lanes do), its stage (what the lane's
        // instructions leave) and, when the lane re-plans at its peer, the peer's own topology
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
            cInt firsts = cInt.ZERO(), lasts = cInt.ZERO(), first = null;
            for (final Rel route : ((BranchInst) insts.get(i)).routes().<Rel>elements().toList()) {
                final List<Inst> program = route.second().isCall()
                        ? route.second().asCall().insts()
                        : List.of((Inst) map_(route.second()).tryToInst());
                final boolean seeded = !program.isEmpty() && START_INST_TID.equals(program.getFirst().tid().basePath());
                final String split = route.first().uriValue().q(SPLIT);
                final List<Inst> laneContracts = Rewriter.Helper.contracts(code(program));
                // a seeded lane's share is its start; a lane dealt a computed bag is dealt it when the branch runs, so each
                // member reaches it at most once ({n} times {?}), and a copy takes the whole bag
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
                fan.add(rec(uri(PLACE), str(place(route.first().uriValue())), uri(STAGE), lst(stages.stream().map(t -> (Obj) label(t)).toList()),
                        uri(TOPOLOGY), theirs(route.first().uriValue(), program)));
            }
            // a head branch's seed: lanes that all start from one seed hold it once (a copy, or the run axis, whose lanes
            // split a span's exponent), and lanes that start from parts of it sum to it
            if (null == c)
                run.add(label(!seeds.isEmpty() && seeds.stream().allMatch(Objects::nonNull) && 1 == seeds.stream().distinct().count()
                        ? first : firsts));
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
     * A LANE WITH LEVELS LEFT RE-PLANS AT ITS PEER, so its peer explains it: the lane's own code, explained there by the
     * peer's compiler and network, sent through the peer's eval as a branch ships a lane (BranchInst.remote). What comes
     * back is the explanation's data; its topology's flow is kept, and its format drawn here. A leaf states the bare
     * point 0 (Shard.emit), a place on this machine plans nothing new, and a failure is kept as its message.
     */
    private static Obj theirs(final fURI key, final List<Inst> program) {
        final fURI place = key.resolve().qLess();
        final String hop = program.isEmpty() ? null : program.getFirst().tid().q(HOP);
        if (null == hop || "0".equals(hop) || null == place.host())
            return noobj();
        final Inst eval = Machine.current().network().overrideOf(place, EVAL_INST_TID);
        if (!eval.isObjInst())
            return noobj();
        final List<Inst> asked = new ArrayList<>(program);
        asked.add(instB(EXPLAIN_INST_TID, lst()));
        final Obj explained = eval.apply(code(asked));
        if (explained.isFail())
            return str(explained.toString());
        final Obj ext = explained.isRec() ? explained.asRec().at(uri(EXT)) : noobj();
        final Obj topology = ext.isRec() ? ext.asRec().at(uri(TOPOLOGY)) : noobj();
        final Obj flow = topology.isRec() ? topology.asRec().at(uri(FLOW)) : noobj();
        return flow.isLst() ? topology(flow.asLst()) : noobj();
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
     * exponent, else run as one blocked limbo per obj, which takes its point only when it runs. An exact span is one composition and keeps the count. An instruction no contract
     * types leaves any number.
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
        final Obj claim = 0 < below ? Machine.read(PendingWrites.decq(Inst.Helper.operationTid(contract.tid().b(null).cLess()))) : noobj();
        final cInt negative = 0 >= below ? cInt.ZERO()
                : claim.isRec() && null != TheoryHelper.roleAddress(claim.asRec(), CONVERSE) ? pointedIn.mult(cInt.of(below))
                : in;
        return pointed.mult(cInt.of(above)).plus(zero).plus(negative);
    }

    /**
     * A LANE'S PLACE, BY THE NAME THE NETWORK CALLS IT (Marko, 2026-11-20): the key's origin resolved against the
     * network's prefixes, so a peer named ginger reads //ginger. A peer the network names nothing keeps its authority,
     * and a place on this machine is home.
     */
    private static String place(final fURI key) {
        final fURI place = key.resolve().qLess();
        if (null == place.host())
            return HOME;
        final Network network = Machine.current().network();
        return (null == network ? List.<Rel>of() : Component.Prefix.Helper.effectivePrefixes(network).<Rel>elements().toList()).stream()
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
     * THE DRAWING OF A FLOW: each run and fan drawn around its own main row, laid side by side along one main line (a run
     * after a fan starts where the fan's lanes merge, at home), and then each lane that re-planned at its peer, drawn
     * below under the peer's name. A fan with an odd number of lanes puts its middle lane on the main line; an even one
     * puts a joint row between its halves. Each lane's columns are padded to the widest coefficient in that column.
     */
    static String draw(final Lst flow) {
        final List<Block> blocks = new ArrayList<>();
        final StringBuilder below = new StringBuilder();
        boolean merged = false;
        for (final Obj segment : flow.<Obj>elements().toList()) {
            final List<Obj> parts = segment.asLst().<Obj>elements().toList();
            if (!parts.isEmpty() && parts.getFirst().isRec()) {
                blocks.add(fan(parts, below));
                merged = true;
            } else {
                blocks.add(new Block(List.of((merged ? "──" + HOME + "─►" : "")
                        + parts.stream().map(Obj::strValue).collect(Collectors.joining("─"))), 0));
                merged = false;
            }
        }
        final int above = blocks.stream().mapToInt(Block::main).max().orElse(0);
        final int under = blocks.stream().mapToInt(block -> block.rows().size() - 1 - block.main()).max().orElse(0);
        final List<StringBuilder> rows = IntStream.rangeClosed(0, above + under).mapToObj(r -> new StringBuilder()).toList();
        for (final Block block : blocks) {
            final int width = block.rows().stream().mapToInt(String::length).max().orElse(0);
            for (int r = 0; r < rows.size(); r++) {
                final int row = r - (above - block.main());
                final String text = 0 <= row && row < block.rows().size() ? block.rows().get(row) : "";
                rows.get(r).append(text).append(" ".repeat(width - text.length()));
            }
        }
        return rows.stream().map(row -> row.toString().stripTrailing()).collect(Collectors.joining("\n", "\n", "\n")) + below;
    }

    private record Block(List<String> rows, int main) {
    }

    private static Block fan(final List<Obj> lanes, final StringBuilder below) {
        final List<String> places = lanes.stream().map(lane -> lane.asRec().at(uri(PLACE)).strValue()).toList();
        final List<List<String>> stages = lanes.stream()
                .map(lane -> lane.asRec().at(uri(STAGE)).asLst().<Obj>elements().map(Obj::strValue).toList()).toList();
        final int placeWidth = places.stream().mapToInt(String::length).max().orElse(0);
        final int columns = stages.stream().mapToInt(List::size).max().orElse(0);
        final int[] widths = new int[columns];
        for (final List<String> lane : stages)
            for (int j = 0; j < lane.size(); j++)
                widths[j] = Math.max(widths[j], lane.get(j).length());
        final List<String> bodies = new ArrayList<>();
        for (int l = 0; l < lanes.size(); l++) {
            final StringBuilder body = new StringBuilder("─").append(places.get(l))
                    .append("─".repeat(placeWidth - places.get(l).length())).append("─►");
            for (int j = 0; j < columns; j++) {
                final String stage = j < stages.get(l).size() ? stages.get(l).get(j) : "";
                body.append(0 == j ? "" : "─").append(stage).append("─".repeat(widths[j] - stage.length()));
            }
            bodies.add(body.append('─').toString());
        }
        final int width = bodies.stream().mapToInt(String::length).max().orElse(0);
        final int k = lanes.size();
        final int height = 1 == k % 2 ? k : k + 1;
        final int main = height / 2;
        final List<String> rows = new ArrayList<>(height);
        int next = 0;
        // the main line enters the fork through two dashes, {11}──┤ (Marko, 2026-11-20); the other rows keep their place
        for (int r = 0; r < height; r++) {
            final boolean lane = 1 == k % 2 || main != r;
            rows.add((main == r ? "──" : "  ") + fork(r, height, main, lane)
                    + (lane ? bodies.get(next++) : " ".repeat(width)) + join(r, height, main, lane));
        }
        // each lane that re-planned at its peer: the peer's drawing, from its flow, under the peer's name
        for (int l = 0; l < lanes.size(); l++) {
            final Obj theirs = lanes.get(l).asRec().at(uri(TOPOLOGY));
            if (theirs.isRec())
                below.append(places.get(l)).append(":\n").append(draw(theirs.asRec().at(uri(FLOW)).asLst()).substring(1).indent(3));
            else if (theirs.isStr())
                below.append(places.get(l)).append(":\n").append(theirs.strValue().indent(3));
        }
        return new Block(rows, main);
    }

    private static char fork(final int row, final int height, final int main, final boolean lane) {
        if (1 == height)
            return '─';
        if (0 == row)
            return '┌';
        if (height - 1 == row)
            return '└';
        if (main == row)
            return lane ? '┼' : '┤';
        return lane ? '├' : '│';
    }

    private static char join(final int row, final int height, final int main, final boolean lane) {
        if (1 == height)
            return '─';
        if (0 == row)
            return '┐';
        if (height - 1 == row)
            return '┘';
        if (main == row)
            return lane ? '┼' : '├';
        return lane ? '┤' : '│';
    }
}
