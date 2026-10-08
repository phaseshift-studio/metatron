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

package studio.phaseshift.metatron.isa.mach.type.compiler.rewriter;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.Rewriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_FIXPOINT_REWRITER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * FixPointRewriter — the concrete {@code fixpoint_rewriter::T}: rewrites code to a fixpoint over the
 * rewrite rules. It carries the whole rewrite algorithm — the {@code max} rec entry (default 2) is
 * the convergence window, i.e. the number of stable passes before the fixpoint is accepted.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FixPointRewriter extends MRec implements Rewriter {

    private static final FixPointRewriter INSTANCE = new FixPointRewriter(mutableMap(), MACH_FIXPOINT_REWRITER_TID, null);

    // rewrite-rule timing + inst reduction, keyed by rule leaf name (read by the profile() instruction)
    public static final ConcurrentHashMap<String, AtomicLong> REWRITE_TIMINGS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, AtomicLong> REWRITE_INS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, AtomicLong> REWRITE_OUTS = new ConcurrentHashMap<>();

    public static FixPointRewriter single() {
        return INSTANCE;
    }

    public static void resetRewriteTimings() {
        REWRITE_TIMINGS.clear();
        REWRITE_INS.clear();
        REWRITE_OUTS.clear();
    }

    public FixPointRewriter() {
        this(mutableMap(), MACH_FIXPOINT_REWRITER_TID, null);
    }

    public FixPointRewriter(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Code apply(final Obj code) {
        final Code c = code.asCode();
        final AtomicReference<Code> rewritten = new AtomicReference<>(c);
        int hash = c.hashCode();
        int done = this.at(MAX).orElse(jnt(2)).intValue().intValue();
        while (done != 0) {
            Machine.root().spaces()
                    .elements()
                    .filter(r -> r.second() instanceof InstSet)
                    .flatMap(r -> r.second().<InstSet>as().rewrites().stream())
                    .forEach(r -> {
                        final long t0 = System.nanoTime();
                        final Code before = rewritten.get();
                        // capture the inst count BEFORE apply: rewriters mutate the code in place
                        // (selfJVM), so reading before.size() after the fact reports the post size.
                        final int in = before.codeValue().size();
                        final Obj out = r.apply(before);
                        final long t1 = System.nanoTime();
                        final String name = r.tid().name();
                        REWRITE_TIMINGS.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(t1 - t0);
                        final int outSize = out.isCode() ? out.asCode().codeValue().size() : in;
                        REWRITE_INS.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(in);
                        REWRITE_OUTS.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(outSize);
                        if (out.isCode())
                            rewritten.set(out.asCode());
                    });
            if (hash == (hash = rewritten.get().hashCode()))
                done--;
        }
        return rewritten.get();
    }
}
