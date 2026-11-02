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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.m.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * Topology-agnostic extractors for distributed rewrite tests: an op chain, a seed, a base-frame partial, and a
 * compile-and-run. A topology (reduction, peer-to-peer, embarrassingly parallel) is a higher-level reading of a
 * {@link DistributedComputation}; these helpers do not assume master/slave or a fold.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class TransportRewriteHarness {

    private TransportRewriteHarness() {
    }

    /**
     * A code's top-level op addresses in order.
     */
    public static List<fURI> ops(final Code code) {
        return code.insts().stream().map(i -> i.tid().basePath()).toList();
    }

    /**
     * The seed a {@code start}-leading code runs from.
     */
    public static List<Obj> seedOf(final Code code) {
        return seedOf(code.insts().getFirst());
    }

    /**
     * The seed ONE instruction runs from — the same reading as {@link #seedOf(Code)}, for a caller that already holds
     * the code's head (a lane reads its part and its hop budget off that one instruction).
     */
    public static List<Obj> seedOf(final Inst head) {
        return head.arg(0).stream().toList();
    }

    /**
     * The base-frame entries whose resolved value is an int partial — {@code key = minted hex vid, value = partial}.
     */
    public static List<Map.Entry<Obj, Obj>> partials(final Rec base) {
        return base.jvm().entrySet().stream()
                .filter(e -> FutureObj.resolveFuture(e.getValue()).isInt())
                .toList();
    }

    /**
     * Compile + run a code to its value.
     */
    public static Obj evaluate(final Code code) {
        final Code compiled = Machine.current().compiler().apply(code).asCode();
        final SwarmProcessor processor = Machine.current().processor().code(compiled).as();
        try {
            return processor.applyAsync(noobj()).get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw MTronException.of(e, "distributed computation did not finish");
        }
    }

    /**
     * A result folded to its single member (or {@code noobj()} when empty).
     */
    public static Obj single(final Obj result) {
        return result.stream().findFirst().orElse(noobj());
    }
}
