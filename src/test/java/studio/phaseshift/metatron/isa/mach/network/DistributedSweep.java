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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.HOP;

/**
 * AN EMITTED SHARD, READ AS DATA. One value — {@link Layout} — says what a {@code law_shard}/{@code PlanInterpreter}
 * lowering actually emitted: the lanes it fanned out to, each lane's part and hop budget, the route each lane travels
 * by, and the op chain the master runs. A test asserts the layout through this reader instead of peeling the code
 * itself, so a change in the emitted form is one diff in one place rather than a re-derivation per suite.
 * <p>
 * ONE PEEL, ONE PLACE. This reader reuses the peels that already exist — {@link Reduction#slaves} (the summands, and
 * their {@code block} un-quoting), {@link Reduction#routes} (the fan-out routes) and
 * {@link TransportRewriteHarness#ops}/{@link TransportRewriteHarness#seedOf} (the op chain and a seed) — and adds no
 * fourth reading of "lane", "route" or "summand".
 *
 * <pre>{@code
 * final Layout layout = DistributedSweep.layout(compiled);
 * layout.ops();              // [start, select, select, merge, sum]
 * layout.lanes().size();     // one per route
 * layout.lane(0).part();     // the slice that lane runs
 * layout.lane(0).budget();   // its ?hop, as the lane states it
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class DistributedSweep {

    private DistributedSweep() {
        // do nothing
    }

    /**
     * ONE PLACE THE SHARD WAS EMITTED FOR: the authority its route answers at, and the code it runs. The authority is
     * {@code null} for the addressless {@code eval()} — that lane is the local place (a place at distance zero), not a
     * shipped one. The emitter emits one whenever the place set names the home place ({@code ?place=[local,peer]}, or
     * the lanes case {@code ?place=[{4}local]} on a peerless machine), so a test asks this rather than inspects.
     *
     * @param authority the route's target, {@code null} for the local place
     * @param code      the code that lane runs
     */
    public record Lane(fURI authority, Code code) {

        /**
         * Whether this lane runs where the shard was planned (the addressless route) rather than on a peer.
         */
        public boolean local() {
            return null == this.authority;
        }

        /**
         * The lane's share of the seed.
         */
        public List<Obj> part() {
            return TransportRewriteHarness.seedOf(this.seed());
        }

        /**
         * The lane's hop budget, exactly as the emitter states it, or {@code null} when the lane states none. THE
         * BUDGET RIDES THE FOLD: the emitter restates the height on the reducer the lane runs (the fold), so a peer
         * re-plans from its own recombine; a chain written by hand that states it on the seed still answers, which is
         * the read-compat the reader keeps. The emitted text, not a parsed number: the budget is a q value, and
         * parsing it here would be a second reading of {@code PlanInterpreter.hopAxis}.
         */
        public String budget() {
            final fURI fold = this.fold().tid();
            if (fold.hasQ(HOP))
                return fold.qMap().get(HOP);
            final fURI seed = this.seed().tid();
            return seed.hasQ(HOP) ? seed.qMap().get(HOP) : null;
        }

        /**
         * The lane's seed instruction — the {@code start(…)} head its part is read off.
         */
        private Inst seed() {
            return this.code.insts().getFirst();
        }

        /**
         * The lane's fold — the reducer the lane recombines through, the last instruction it runs.
         */
        private Inst fold() {
            return this.code.insts().getLast();
        }
    }

    /**
     * THE WHOLE LAYOUT: the lanes in route order, the route authorities (positionally aligned with the lanes,
     * {@code null} = the local place), and the code it was read from.
     * <p>
     * {@code routes} MIRRORS THE LANES' AUTHORITIES DELIBERATELY: {@code routes.get(i)} is {@code lanes.get(i).authority()}
     * for every {@code i}, because they are the same peel ({@link Reduction#routes}) read twice — once as a list a test can
     * compare against the old {@code Reduction.routes(...)} expectation, once per lane. It is not a second source of
     * truth: keep them position-aligned (a local lane keeps its slot as {@code null}) so C5's place sweep cannot make the
     * two disagree.
     *
     * @param lanes   one lane per route
     * @param routes  the route authorities, in lane order, {@code null} for the local place
     * @param emitted the code this layout was read from
     */
    public record Layout(List<Lane> lanes, List<fURI> routes, Code emitted) {

        /**
         * The lane at {@code i}, in route order.
         */
        public Lane lane(final int i) {
            return this.lanes.get(i);
        }

        /**
         * The master's top-level op chain — what the shard lowers to, read through
         * {@link TransportRewriteHarness#ops}.
         */
        public List<fURI> ops() {
            return TransportRewriteHarness.ops(this.emitted);
        }
    }

    /**
     * Read an emitted shard as a {@link Layout}. A code that was not shard-planned (the rewrite's identity, a fold the
     * plan declined, a chain that stays home) is a layout with no lanes and no routes — the honest reading of "nothing
     * was shipped", not an error.
     */
    public static Layout layout(final Code emitted) {
        final List<Code> slaves = Reduction.slaves(emitted);
        final List<fURI> routes = Reduction.routes(emitted);
        // a lane is one slave AND its route: a layout where the emitter disagrees with itself must not be read as a
        // shorter, quietly-consistent one
        if (slaves.size() != routes.size())
            throw MTronException.of("a lane is one slave and one route: %s slaves, %s routes in %s", slaves.size(), routes.size(), emitted);
        return new Layout(
                IntStream.range(0, slaves.size())
                        .mapToObj(i -> new Lane(routes.get(i), slaves.get(i)))
                        .toList(),
                routes,
                emitted);
    }
}
