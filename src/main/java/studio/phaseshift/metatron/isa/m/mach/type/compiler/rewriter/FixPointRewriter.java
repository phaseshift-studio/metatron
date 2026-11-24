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

package studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.Tokens.TIME;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_FIXPOINT_REWRITER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * FixPointRewriter — the concrete {@code fixpoint_rewriter::T}: rewrites code to a fixpoint over the
 * rewrite rules. It carries the whole rewrite algorithm — the {@code max} rec entry (default 2) is
 * the convergence window, i.e. the number of stable passes before the fixpoint is accepted.
 * <p>
 * The rules are ordered once per vocabulary and remembered ({@code Rewriter.Helper.sort}), so the pass
 * looks their applied order up rather than re-deriving it for every code value.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FixPointRewriter extends AbstractRewriter {

    private static final FixPointRewriter INSTANCE = new FixPointRewriter(mutableMap(), MACH_FIXPOINT_REWRITER_TID, null);

    public static FixPointRewriter single() {
        return INSTANCE;
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
            // THE RULES ARE THIS REWRITER'S OWN (rewrites()): by default they come from INSTRUCTION MEMORY, not from
            // the data index — the machine's own instset is the union of every ISA this frame uses (own level, its
            // imports, the enclosing machine's), so a rewrite declared in a child applies in that child and dies with
            // its frame, and an ISA no longer has to be a registered space for its rules to fire. A refinement may name
            // another set. THE ORDER IS WHAT THE RULES DECLARE (?pre, ?post, ?zindex), memoized per rule set
            // (Rewriter.Helper.sort): this method runs once per value AND once per branch member it descends into.
            final List<Inst> rules = Rewriter.Helper.sort(this.rewrites());
            // a PROFILED run measures each rule, keyed by its leaf name: its time, and the instructions it took in and
            // gave back (rewrite/<rule>/time, in, out) -- the reduction profile() reports
            final Analyzer profile = Analyzer.PROFILE.get();
            rules
                    .forEach(r -> {
                        //this.logger().warn("rewrite: %s", r);
                        //this.logger().warn("code: %s", rewritten.get());
                        final long t0 = Analyzer.Helper.start(profile);
                        final Code before = rewritten.get();
                        // capture the inst count BEFORE apply: rewriters mutate the code in place
                        // (selfJVM), so reading before.size() after the fact reports the post size.
                        final int in = before.codeValue().size();
                        final Obj out = r.apply(before);
                        // the rule's probes are named by the rule, so their path is built only while a run is profiled
                        if (null != profile) {
                            final fURI rule = f(REWRITE).extend(r.tid().name());
                            Analyzer.Helper.stop(profile, rule.extend(TIME), t0);
                            Analyzer.Helper.incr(profile, rule.extend(IN), in);
                            Analyzer.Helper.incr(profile, rule.extend(OUT), out.isCode() ? out.asCode().codeValue().size() : in);
                        }
                        if (out.isCode())
                            rewritten.set(out.asCode());
                    });
            // bottom-up after the pass: the rules are flat on the top-level chain, so a call-type inst argument
            // — a branch's member, a map's function — is rewritten by the very same rules before the top-level
            // pass sees it, and the fixpoint iterates over a branch that carries a branch. Recursion is
            // strictly descending (an argument is a proper sub-code), so it terminates.
            rewritten.set(descend(rewritten.get()));
            if (hash == (hash = rewritten.get().hashCode()))
                done--;
        }
        return rewritten.get();
    }

    /**
     * The same rules, one level down into the additive members: branch is the code ring's additive op, and its
     * members are the one place like terms compose, so a member that is itself a call is rewritten to the
     * fixpoint by {@link #apply} before the top-level pass groups the members (a branch that carries a branch
     * reduces itself first). Only the members of branch insts are descended into — the arguments of every other
     * inst stay exactly as written, because the chain rules are not compositional over a derivation's slots.
     * A branch whose members all come back unchanged keeps its object identity, and a member that came in as an
     * inst stays an inst (a single-instruction rewrite unwraps back to its inst).
     */
    private Code descend(final Code code) {
        boolean changed = false;
        final List<Inst> out = new ArrayList<>(code.insts().size());
        for (final Inst inst : code.insts()) {
            if (!BRANCH_INST_TID.equals(inst.tid().basePath()) || 0 == inst.args().count()) {
                out.add(inst);
                continue;
            }
            final List<Obj> args = inst.args().elements().toList();
            final List<Obj> built = new ArrayList<>(args.size());
            boolean argChanged = false;
            for (final Obj arg : args) {
                // A MEMBER REWRITTEN TO NOTHING IS THE UNIT, NEVER THE ZERO: neg().neg() and plus(0) do nothing to an obj,
                // so the empty code is 1, the path that passes its obj on, where tryToInst() answers noobj, the 0 that
                // kills it (1.branch(id(),neg(),neg().neg()) answered {1,-1}). As id() it is a like term of an id()
                // beside it, so the two collapse and bulk: id{2}() (Marko, 2026-11-10: a rewrite also aligns a
                // member with its parallel branches)
                final Code rewritten = arg.isCall() && 0 < arg.asCall().insts().size() ? apply(arg) : null;
                final Obj next = null == rewritten ? arg
                        : rewritten.insts().isEmpty() ? instB(ID_INST_TID, lst()).c(rewritten.c())
                        : rewritten.tryToInst();
                if (arg != next)
                    argChanged = true;
                built.add(next);
            }
            if (argChanged) {
                changed = true;
                out.add(inst.args(lst(built)));
            } else
                out.add(inst);
        }
        return changed ? code(out) : code;
    }
}
