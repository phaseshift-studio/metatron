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

package studio.phaseshift.metatron.isa.m.mach.type.analyzer;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static studio.phaseshift.metatron.Tokens.ANALYZER;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * analyzer::T — a component's statistics, in the form of the compiler's typer and the processor's tracer (Marko,
 * 2026-11-13). A component holds its analyzer at {@code ~/<component>/analyzer}; the analyzer's STATE is the
 * {@link Analysis} it has measured, derived as it is read at {@code ~/<component>/analyzer/analysis}, and its SETTINGS
 * sit beside that state:
 * <pre>
 * analyzer::[analysis => analysis::T, step => int::T, profile => bool::T]
 * </pre>
 * <ul>
 *   <li><b>recording</b> ({@link #incr}, {@link #record}, {@link #set}) is the producer's, in Java, where the thing it
 *   counts happens. It never writes a rec: the meters are lock-free cells, so a socket frame or a monad step pays an
 *   atomic add — never a map copy, a type check or a store write;</li>
 *   <li><b>reading</b> is anyone's, in mtron: {@code *~/network/analyzer/analysis/wire/sent};</li>
 *   <li><b>subscribing</b> is the space's own pubsub: {@code ~/+/analyzer/analysis?subq -> sub::[code=>…]} receives
 *   {@code pub::[<address>, analysis::T]} at most once per {@code step} (millis), and only after a change;</li>
 *   <li><b>{@code profile}</b> switches on a component's costly instrumentation (the processor's per-monad timings),
 *   which is off by default: a meter that costs a clock read per step is a meter no machine can leave on.</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Analyzer extends Component {

    /**
     * the key a component holds its analyzer under — a constant, so a producer's lookup is one map read
     */
    Obj KEY = uri(ANALYZER);

    /**
     * THE ANALYZER THIS THREAD'S RUN IS PROFILED INTO, or {@code null} when nothing profiles it. {@code profile()} sets
     * one of its own for the runs it times, and a computer whose processor's analyzer has {@code profile} on sets that
     * one; a computer minted under a profile carries it to whichever thread runs it. The costly instrumentation reads
     * it once and measures only when it is set, so a run nobody profiles pays one thread-local read per probe.
     */
    ThreadLocal<Analyzer> PROFILE = new ThreadLocal<>();

    /**
     * count {@code delta} more at {@code group/probe} — a {@code counter}, the monotonic meter.
     */
    Analyzer incr(fURI group, fURI probe, long delta);

    /**
     * one more observation of {@code nanos} at {@code group/probe} — a {@code timer}: count, total, min, max.
     */
    Analyzer record(fURI group, fURI probe, long nanos);

    /**
     * the value at {@code group/probe} now — a {@code gauge}, the point-in-time meter (the one kind that is no monoid).
     */
    Analyzer set(fURI group, fURI probe, double value);

    /**
     * @return the analysis the meters hold as they stand — a value built for this read, which the reader may keep
     */
    Analysis analysis();
}
