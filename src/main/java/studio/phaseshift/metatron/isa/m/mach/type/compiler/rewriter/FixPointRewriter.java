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
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.ANALYSIS;
import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_FIXPOINT_REWRITER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * FixPointRewriter — the concrete {@code fixpoint_rewriter::T}: rewrites code to a fixpoint over the
 * rewrite rules. It carries the whole rewrite algorithm — the {@code max} rec entry (default 2) is
 * the convergence window, i.e. the number of stable passes before the fixpoint is accepted.
 * <p>
 * The rules are ordered once per vocabulary and remembered (see orderedRules), so the pass
 * looks their applied order up rather than re-deriving it for every code value.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FixPointRewriter extends MRec implements Rewriter {

    private static final FixPointRewriter INSTANCE = new FixPointRewriter(mutableMap(), MACH_FIXPOINT_REWRITER_TID, null);

    // rewrite-rule timing + inst reduction, keyed by rule leaf name (read by the profile() instruction)
    public static final ConcurrentHashMap<String, AtomicLong> REWRITE_TIMINGS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, AtomicLong> REWRITE_INS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, AtomicLong> REWRITE_OUTS = new ConcurrentHashMap<>();

    /**
     * The budget on remembered rule orders. A VM's vocabulary changes when an ISA is mounted, imported, redefined or
     * unmounted — a handful of times per configuration, never per evaluation — so a small number is a real ceiling
     * rather than a thrash point. Reaching it clears the cache instead of evicting one entry: a rule-set change is a
     * configuration event, and the cost it pays (one sort) is the cost a miss would have paid anyway.
     */
    private static final int MAX_RULE_ORDERS = 16;

    /**
     * The ordered rule lists, keyed by the rules that produced them — see {@link #orderedRules(Set)}.
     */
    private static final ConcurrentHashMap<RuleOrder, List<Inst>> RULE_ORDERS = new ConcurrentHashMap<>();

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
            // THE RULES COME FROM INSTRUCTION MEMORY, not from the data index: the machine's own instset is the
            // union of every ISA this frame uses (own level, its imports, the enclosing machine's), so a rewrite
            // declared in a child applies in that child and dies with its frame — and an ISA no longer has to be
            // a registered space for its rules to fire. rewrites() merges by tid identity, nearest level first.
            //
            // ANALYSIS RULES RUN LAST, and the order is stated here rather than inherited from the mount order.
            // An analysis rule (profile_analysis, explain_analysis) CAPTURES the code before its marker and
            // REPLACES the chain with one inst, so anything it captures is invisible to every later rule. The
            // base ISA is mounted before /m/math/cat, so without this ordering a base-ISA analysis rule runs
            // ahead of the category's distribution rules and swallows the fold: profile_analysis captured
            // start.plus.sum, law_shard then found nothing to anchor on, and the profile described a chain that
            // never distributed. Distribution is a rewrite like any other; ANALYSIS IS ABOUT THE RESULT OF
            // REWRITING, so it runs after — measured, not merely preferred.
            //
            // The ordering is memoized against the rules themselves (orderedRules), so a pass pays it once per
            // rule set rather than once per code value — and this method runs once per value AND once per branch
            // member it descends into.
            final List<Inst> rules = orderedRules(Machine.current().instset().rewrites());
            rules
                    .forEach(r -> {
                        //this.logger().warn("rewrite: %s", r);
                        //this.logger().warn("code: %s", rewritten.get());
                        final long t0 = System.nanoTime();
                        final Code before = rewritten.get();
                        // capture the inst count BEFORE apply: rewriters mutate the code in place
                        // (selfJVM), so reading before.size() after the fact reports the post size.
                        final int in = before.codeValue().size();
                        final Obj out = r.apply(before);
                        final long t1 = System.nanoTime();
                        final String name = r.tid().name();
                        REWRITE_TIMINGS.computeIfAbsent(name, _ -> new AtomicLong()).addAndGet(t1 - t0);
                        final int outSize = out.isCode() ? out.asCode().codeValue().size() : in;
                        REWRITE_INS.computeIfAbsent(name, _ -> new AtomicLong()).addAndGet(in);
                        REWRITE_OUTS.computeIfAbsent(name, _ -> new AtomicLong()).addAndGet(outSize);
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
     * The rules in the order this pass applies them — analysis rules last, registration order otherwise — cached
     * against the rules themselves.
     * <p>
     * The order is a pure function of the rule sequence: the sort reads the rule's own tid ({@code ?analysis}, the
     * declared marker a capture rule carries), nothing else. So the same rules always answer the same order, and the
     * sort is work that only a CHANGED vocabulary needs to redo. It matters because the two things it costs are hot:
     * {@code apply} runs once per code value AND once per branch member it descends into, and the sort is a
     * comparison that resolves a tid per candidate ({@code O(n log n)} of them). A hit is instead one list copy and
     * {@code O(n)} identity hashes.
     * <p>
     * The key is the rules IN ORDER, compared by IDENTITY rather than by {@link Inst#equals}: the instset union
     * hands back the very instances its tables hold, so identity is exactly "the same rules are provided", while
     * value equality would compare rule functions — object graphs that answer a different question at a higher
     * price. Order is part of the key because the sort is STABLE: the same rules handed in a different order are
     * a different pass.
     * <p>
     * Package-private so a test can pin the contract (a repeat is the SAME list, a reordered input is not), the
     * same way {@code fURI.Singleton.parse} exposes the uncached path to its performance test.
     *
     * @param rules the rewrite rules, as the machine's instset answers them
     * @return the ordered rules, shared between callers — do not mutate
     */
    static List<Inst> orderedRules(final Set<Inst> rules) {
        if (null == rules || rules.isEmpty())
            return List.of();
        final RuleOrder key = new RuleOrder(List.copyOf(rules));
        final List<Inst> cached = RULE_ORDERS.get(key);
        if (null != cached)
            return cached;
        final List<Inst> ordered = key.rules().stream()
                .sorted(Comparator.comparingInt(rule -> rule.tid().hasQ(ANALYSIS) ? 1 : 0))
                .toList();
        // get/putIfAbsent rather than computeIfAbsent: the sort then happens OUTSIDE the map's own update, and a
        // racing loser keeps the winner's list — the two are interchangeable, since the order is a pure function of
        // the key.
        if (RULE_ORDERS.size() >= MAX_RULE_ORDERS)
            RULE_ORDERS.clear();
        final List<Inst> raced = RULE_ORDERS.putIfAbsent(key, ordered);
        return null == raced ? ordered : raced;
    }

    /**
     * The cache key: the rules in the order they were provided, compared by IDENTITY and never by value — see
     * {@link #orderedRules(Set)} for why both of those are the right choice. The sequence carries the sort's
     * input order, and the map's hashing is over {@link System#identityHashCode(Object)}, so a look-up never
     * touches a rule's tid, args or function.
     * Whether a rule is an ?analysis rule — one that captures the code before a marker and answers a report
     * about it ({@code profile_analysis}, {@code explain_analysis}).
     * <p>
     * Such a rule REMOVES the chain it captures, so its position decides what every other rule can still see.
     * It must therefore run after the rewriting it describes, never before: a profile that captures an
     * un-distributed chain reports a computation that never happened, and the miss is silent because the
     * report is well-formed either way.
     */
    private record RuleOrder(List<Inst> rules) {

        @Override
        public int hashCode() {
            int hash = 1;
            for (final Inst rule : this.rules)
                hash = 31 * hash + System.identityHashCode(rule);
            return hash;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof RuleOrder that) || that.rules.size() != this.rules.size())
                return false;
            for (int i = 0; i < this.rules.size(); i++)
                if (this.rules.get(i) != that.rules.get(i))
                    return false;
            return true;
        }
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
                final Obj next = arg.isCall() && 0 < arg.asCall().insts().size()
                        ? apply(arg).tryToInst()
                        : arg;
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
