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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.COPY;
import static studio.phaseshift.metatron.Tokens.LOCAL;
import static studio.phaseshift.metatron.Tokens.SPLIT;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;

/**
 * THE DISTRIBUTION TOPOLOGY OF A COMPILED CHAIN (Marko, 2026-11-20), the explain extension read at
 * {@code explain()>>ext/topology/format}. It draws no instructions, only the point coefficients they leave: the seed,
 * each lane's share and what each of its instructions leaves, the lanes merging at home, and what the master's
 * instructions leave.
 * <pre>
 *     ┌──//0─►{10}──{1}─┐
 * {15}┼──//1─►{2}───{1}─┼──//local─►{3}──{1}
 *     └──//2─►{3}───{1}─┘
 * </pre>
 * A COEFFICIENT IS A PRODUCT, READ LEFT TO RIGHT (Marko): each instruction's contract states the coefficient it takes
 * and the one it answers. One that takes at most one obj applies once per obj, so its range multiplies the stream's
 * ({@code {3}} through {@code plus}, {@code int{1} => int{1}}, stays {@code {3}}); one that takes the stream whole
 * applies once and leaves its range ({@code {15}} through {@code sum} is {@code {1}}); one that takes nothing starts a
 * stream ({@code start({1,2,3})} is {@code {3}}); a point copies what it answers. A branch is the ring's sum: its lanes'
 * coefficients add where they merge. A seeded lane's share is its start; a lane dealt a computed bag is dealt it when
 * the branch runs, so each member reaches it at most once ({@code {n}} times {@code {?}}: a copy takes the whole bag). A lane is labelled by the name the network
 * calls its place, and home is {@code //local}.
 */
public final class Topology {

    private static final String HOME = "//" + LOCAL;

    private Topology() {
        // static only
    }

    /**
     * One lane: the place it runs and the coefficient each of its stages leaves.
     */
    record Lane(String place, List<cInt> stages) {
    }

    /**
     * A stretch of the drawing: a run of coefficients along the main line, or a fan of lanes.
     */
    sealed interface Segment permits Run, Fan {
    }

    record Run(String text) implements Segment {
    }

    record Fan(List<Lane> lanes) implements Segment {
    }

    /**
     * The drawing of a compiled chain, or null when it fans out nowhere: a chain that runs in one place has no topology.
     */
    public static String draw(final Code code) {
        final List<Inst> insts = code.insts();
        if (insts.stream().noneMatch(Topology::fans))
            return null;
        final List<Inst> contracts = Rewriter.Helper.contracts(code);
        final List<Segment> segments = new ArrayList<>();
        final StringBuilder run = new StringBuilder();
        cInt c = null; // nothing flows before the head
        for (int i = 0; i < insts.size(); i++) {
            if (fans(insts.get(i))) {
                final List<Lane> lanes = lanes((BranchInst) insts.get(i), c);
                // a planned head: lanes dealt parts of the seed sum to it, while lanes that copy it (?split=copy, or the
                // run axis, whose lanes share the seed and split a span's exponent) each hold all of it
                if (null == c)
                    run.append(label(((BranchInst) insts.get(i)).routes().<Rel>elements().allMatch(route -> {
                        final String split = route.first().uriValue().q(SPLIT);
                        return null == split || split.endsWith(COPY);
                    }) ? lanes.getFirst().stages().getFirst()
                            : lanes.stream().map(lane -> lane.stages().getFirst()).reduce(cInt::plus).orElse(cInt.ZERO())));
                segments.add(new Run(run.toString()));
                segments.add(new Fan(lanes));
                c = lanes.stream().map(lane -> lane.stages().getLast()).reduce(cInt::plus).orElse(cInt.ZERO());
                run.setLength(0);
                run.append("──").append(HOME).append("─►").append(label(c));
            } else {
                c = flow(c, insts.get(i), contracts.get(i));
                run.append(run.isEmpty() ? "" : "──").append(label(c));
            }
        }
        segments.add(new Run(run.toString()));
        return draw(segments);
    }

    private static boolean fans(final Inst inst) {
        return inst instanceof BranchInst && inst.args().isRec();
    }

    private static List<Lane> lanes(final BranchInst branch, final cInt in) {
        final List<Lane> lanes = new ArrayList<>();
        for (final Rel route : branch.routes().<Rel>elements().toList()) {
            final List<Inst> program = route.second().isCall()
                    ? route.second().asCall().insts()
                    : List.of((Inst) map_(route.second()).tryToInst());
            final boolean seeded = !program.isEmpty() && START_INST_TID.equals(program.getFirst().tid().basePath());
            final String split = route.first().uriValue().q(SPLIT);
            final List<Inst> contracts = Rewriter.Helper.contracts(code(program));
            final List<cInt> stages = new ArrayList<>();
            cInt c = seeded ? null
                    : null == in ? cInt.MAYBESOME()
                    : null == split || split.endsWith(COPY) ? in
                    : in.mult(in.maybe());
            if (!seeded)
                stages.add(c);
            for (int i = 0; i < program.size(); i++)
                stages.add(c = flow(c, program.get(i), contracts.get(i)));
            lanes.add(new Lane(place(route.first().uriValue()), stages));
        }
        return lanes;
    }

    /**
     * THE COEFFICIENT AN INSTRUCTION LEAVES, from its contract's: a dom of none starts a stream (its range is the
     * stream), a dom of at most one applies per obj (its range multiplies the stream's), and any other dom takes the
     * stream whole (its range is what is left). The instruction's own coefficients then apply: a point copies, and a
     * span range is a sum of terms, one per exponent ({@code {0,8}plus} turns one obj into nine), while an exact span
     * is one composition and keeps the count. An instruction no contract types leaves any number.
     */
    private static cInt flow(final cInt in, final Inst inst, final Inst contract) {
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
        return null == span || span.isExact() ? pointed
                : pointed.mult(null == span.min() || null == span.max() ? span.maybeSome() : cInt.of(span.max() - span.min() + 1));
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

    private static String label(final cInt c) {
        return "{" + c + "}";
    }

    /**
     * THE DRAWING: each segment drawn around its own main row, the segments laid side by side along one main line. A
     * fan with an odd number of lanes puts its middle lane on the main line; an even one puts a joint row between its
     * halves. Each lane's columns are padded to the widest coefficient in that column, so the fan reads as a table.
     */
    static String draw(final List<Segment> segments) {
        final List<Block> blocks = segments.stream().map(Topology::block).toList();
        final int above = blocks.stream().mapToInt(Block::main).max().orElse(0);
        final int below = blocks.stream().mapToInt(block -> block.rows().size() - 1 - block.main()).max().orElse(0);
        final List<StringBuilder> rows = IntStream.rangeClosed(0, above + below).mapToObj(r -> new StringBuilder()).toList();
        for (final Block block : blocks) {
            final int width = block.rows().stream().mapToInt(String::length).max().orElse(0);
            for (int r = 0; r < rows.size(); r++) {
                final int row = r - (above - block.main());
                final String text = 0 <= row && row < block.rows().size() ? block.rows().get(row) : "";
                rows.get(r).append(text).append(" ".repeat(width - text.length()));
            }
        }
        return rows.stream().map(row -> row.toString().stripTrailing()).collect(Collectors.joining("\n", "\n", "\n"));
    }

    private record Block(List<String> rows, int main) {
    }

    private static Block block(final Segment segment) {
        if (segment instanceof Run run)
            return new Block(List.of(run.text()), 0);
        final List<Lane> lanes = ((Fan) segment).lanes();
        final int placeWidth = lanes.stream().mapToInt(lane -> lane.place().length()).max().orElse(0);
        final int columns = lanes.stream().mapToInt(lane -> lane.stages().size()).max().orElse(0);
        final int[] widths = new int[columns];
        for (final Lane lane : lanes)
            for (int j = 0; j < lane.stages().size(); j++)
                widths[j] = Math.max(widths[j], label(lane.stages().get(j)).length());
        final List<String> bodies = new ArrayList<>();
        for (final Lane lane : lanes) {
            final StringBuilder body = new StringBuilder("──").append(lane.place())
                    .append("─".repeat(placeWidth - lane.place().length())).append("─►");
            for (int j = 0; j < columns; j++) {
                final String stage = j < lane.stages().size() ? label(lane.stages().get(j)) : "";
                body.append(0 == j ? "" : "──").append(stage).append("─".repeat(widths[j] - stage.length()));
            }
            bodies.add(body.append('─').toString());
        }
        final int width = bodies.stream().mapToInt(String::length).max().orElse(0);
        final int k = lanes.size();
        final int height = 1 == k % 2 ? k : k + 1;
        final int main = height / 2;
        final List<String> rows = new ArrayList<>(height);
        int next = 0;
        for (int r = 0; r < height; r++) {
            final boolean lane = 1 == k % 2 || main != r;
            rows.add(fork(r, height, main, lane) + (lane ? bodies.get(next++) : " ".repeat(width)) + join(r, height, main, lane));
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
