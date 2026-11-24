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

package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.POST;
import static studio.phaseshift.metatron.Tokens.PRE;
import static studio.phaseshift.metatron.Tokens.ZINDEX;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * Rewriter — the first stage of {@code compiler::T}: lowers {@code code::T} to {@code code::T} by
 * applying rewrite rules to a fixpoint. A rewriter is a machine component ({@code rewriter::T}); the
 * concrete {@code fixpoint_rewriter::T} carries its convergence window as the {@code max} rec entry.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Rewriter extends Component {

    /**
     * Rewrite {@code code} to a fixpoint over the applicable rewrite rules.
     *
     * @param code the code to rewrite
     * @return the rewritten code
     */
    Code apply(final Obj code);

    default Set<Inst> rewrites() {
        return Machine.current().instset().rewrites();
    }

    /**
     * The rewrite stage's helpers: the pass order the rules declare ({@link #sort}), which every rewriter applies its
     * rules in, and the typing — the carrier flowing into each instruction of a chain, and the contract each resolves
     * to, probed with the machine's own resolver — the types a law-driven rule reads an instruction's laws at, before
     * the compiler has resolved anything.
     */
    class Helper {

        private Helper() {
            // do nothing
        }

        private static final GraphittyLogger LOG = Graphitty.log(Rewriter.class);

        /**
         * The budget on remembered rule orders. A VM's vocabulary changes when an ISA is mounted, imported, redefined
         * or unmounted — a handful of times per configuration, never per evaluation — so a small number is a real
         * ceiling rather than a thrash point. Reaching it clears the cache instead of evicting one entry: a rule-set
         * change is a configuration event, and the cost it pays (one sort) is the cost a miss would have paid anyway.
         */
        private static final int MAX_RULE_ORDERS = 16;

        /**
         * The sorted rule lists, keyed by the rules that produced them — see {@link #sort(Set)}.
         */
        private static final ConcurrentHashMap<RuleOrder, List<Inst>> RULE_ORDERS = new ConcurrentHashMap<>();

        /**
         * The rules in the order a pass applies them — what they declare ({@code ?pre}, {@code ?post}, {@code ?zindex};
         * see {@link #order}) — cached against the rules themselves. Every rewriter sorts its rules here.
         * <p>
         * ?ZINDEX ORDERS THE PASS (Marko, 2026-11-20), read as CSS reads a z-index: unset is 0, a bigger number runs
         * later, so a rule that must come after everything states a big one and a rule that must follow another states
         * that one's plus one; equal numbers keep their registration order. The capture rules run last
         * ({@code ?zindex=1000}), and the order is stated rather than inherited from the mount order. A capture rule
         * ({@code profile_analysis}, {@code explain_analysis}) CAPTURES the code before its marker and REPLACES the
         * chain with one inst, so anything it captures is invisible to every later rule. Without the order a capture
         * rule ran ahead of the distribution rules and swallowed the fold: {@code profile_analysis} captured
         * {@code start.plus.sum}, {@code law_shard} then found nothing to anchor on, and the profile described a chain
         * that never distributed. Distribution is a rewrite like any other; ANALYSIS IS ABOUT THE RESULT OF REWRITING,
         * so it runs after — measured, not merely preferred.
         * <p>
         * The order is a pure function of the rule sequence: it reads the rules' own tids, nothing else. So the same
         * rules always answer the same order, and the sort is work that only a CHANGED vocabulary needs to redo. It
         * matters because a fixpoint pass runs once per code value AND once per branch member it descends into, and the
         * order reads every rule's tid and weighs every pair of rules. A hit is instead one list copy and {@code O(n)}
         * identity hashes.
         * <p>
         * The key is the rules IN ORDER, compared by IDENTITY rather than by {@link Inst#equals}: the instset union
         * hands back the very instances its tables hold, so identity is exactly "the same rules are provided", while
         * value equality would compare rule functions — object graphs that answer a different question at a higher
         * price. Order is part of the key because registration order breaks ties: the same rules handed in a different
         * order are a different pass.
         *
         * @param rewrites the rewrite rules, as the machine's instset answers them
         * @return the ordered rules, shared between callers — do not mutate
         */
        public static List<Inst> sort(final Set<Inst> rewrites) {
            if (null == rewrites || rewrites.isEmpty())
                return List.of();
            final RuleOrder key = new RuleOrder(List.copyOf(rewrites));
            final List<Inst> cached = RULE_ORDERS.get(key);
            if (null != cached)
                return cached;
            final List<Inst> ordered = order(key.rules());
            // get/putIfAbsent rather than computeIfAbsent: the sort then happens OUTSIDE the map's own update, and a
            // racing loser keeps the winner's list — the two are interchangeable, since the order is a pure function of
            // the key.
            if (RULE_ORDERS.size() >= MAX_RULE_ORDERS)
                RULE_ORDERS.clear();
            final List<Inst> raced = RULE_ORDERS.putIfAbsent(key, ordered);
            return null == raced ? ordered : raced;
        }

        /**
         * THE PASS ORDER (Marko, 2026-11-24), declared on each rule's tid. Every rewrite is
         * {@code <instset>/inst/rewrite/<name>}, and its GROUPING is that folder, the instset's own rules. Three
         * queries place a rule:
         * <ul>
         *   <li>{@code ?zindex=<n>} ranks it within its grouping (unset is 0, bigger is later, registration order among
         *       equals), and that rank is never broken;</li>
         *   <li>{@code ?pre=[{+}uri::T]} — every rule of another grouping whose tid has one of the prefixes runs before
         *       it;</li>
         *   <li>{@code ?post=[{+}uri::T]} — every rule of another grouping whose tid has one of the prefixes runs after
         *       it;</li>
         *   <li>whatever that leaves unordered goes by {@code ?zindex}, then registration, across groupings too.</li>
         * </ul>
         * So {@code law_shard?pre=[/m/db/+/inst/rewrite]} lets every database pushdown shape the chain before it is
         * distributed, whatever zindex a pushdown states, and a rule its own grouping ranks after law_shard (a capture
         * rule at 1000) still waits for it. With no {@code ?pre} or {@code ?post} anywhere the order is the zindex sort
         * it always was. A prefix may be a pattern, as that one is, and one prefix may be written bare.
         * <p>
         * THE DECLARATIONS NEED NOT FORM A POSET: Y may wait for X's grouping while X waits for Y's. A declaration that
         * lies on such a cycle contradicts another, so the declarations along every cycle are void and zindex ranks
         * their rules; every other declaration still holds, as does every grouping's own rank. The voided declarations
         * are logged.
         */
        private static List<Inst> order(final List<Inst> rules) {
            final int n = rules.size();
            final int[] zindex = new int[n];
            final fURI[] path = new fURI[n];
            final fURI[] folder = new fURI[n];
            for (int i = 0; i < n; i++) {
                zindex[i] = Objects.requireNonNullElse(rules.get(i).tid().qValue(ZINDEX, Integer.class), 0);
                path[i] = rules.get(i).tid().basePath();
                folder[i] = path[i].retract(1);
            }
            final Comparator<Integer> rank = Comparator.<Integer>comparingInt(i -> zindex[i]).thenComparingInt(i -> i);
            // a grouping's own rank is a chain, so no declaration elsewhere can let a later member overtake an earlier
            // one
            final boolean[][] edge = new boolean[n][n]; // edge[i][j]: i runs before j
            final Map<fURI, List<Integer>> groupings = new LinkedHashMap<>();
            for (int i = 0; i < n; i++)
                groupings.computeIfAbsent(folder[i], key -> new ArrayList<>()).add(i);
            for (final List<Integer> members : groupings.values()) {
                members.sort(rank);
                for (int k = 1; k < members.size(); k++)
                    edge[members.get(k - 1)][members.get(k)] = true;
            }
            final boolean[][] declared = new boolean[n][n];
            for (int i = 0; i < n; i++) {
                final List<fURI> pre = families(rules.get(i), PRE);
                final List<fURI> post = families(rules.get(i), POST);
                for (int j = 0; j < n; j++) {
                    if (folder[i].equals(folder[j]))
                        continue;
                    final fURI other = path[j];
                    if (pre.stream().anyMatch(other::hasPrefix))
                        declared[j][i] = edge[j][i] = true;
                    if (post.stream().anyMatch(other::hasPrefix))
                        declared[i][j] = edge[i][j] = true;
                }
            }
            // a declaration on a cycle is one whose later rule reaches back to its earlier one: void them all, judged
            // against the graph as declared, so no cycle survives and no order depends on which was voided first
            final List<int[]> voided = new ArrayList<>();
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (declared[i][j] && reaches(edge, j, i))
                        voided.add(new int[]{i, j});
            voided.forEach(v -> edge[v[0]][v[1]] = false);
            if (!voided.isEmpty())
                LOG.warn("rewrite order: ?pre/?post declarations contradict each other, so zindex ranks %s",
                        voided.stream().map(v -> path[v[0]] + " -> " + path[v[1]]).toList());
            final int[] waiting = new int[n];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (edge[i][j])
                        waiting[j]++;
            final PriorityQueue<Integer> ready = new PriorityQueue<>(rank);
            for (int i = 0; i < n; i++)
                if (0 == waiting[i])
                    ready.add(i);
            final List<Inst> ordered = new ArrayList<>(n);
            while (!ready.isEmpty()) {
                final int next = ready.poll();
                ordered.add(rules.get(next));
                for (int j = 0; j < n; j++)
                    if (edge[next][j] && 0 == --waiting[j])
                        ready.add(j);
            }
            return List.copyOf(ordered);
        }

        /**
         * The families a rule names in {@code ?pre} or {@code ?post}, a {@code [{+}uri::T]}.
         */
        private static List<fURI> families(final Inst rule, final String key) {
            final List<fURI> declared = rule.tid().qValue(key, List.class);
            return null == declared ? List.of() : declared;
        }

        /**
         * Whether {@code from} reaches {@code to} along the edges.
         */
        private static boolean reaches(final boolean[][] edge, final int from, final int to) {
            final boolean[] seen = new boolean[edge.length];
            final Deque<Integer> frontier = new ArrayDeque<>(List.of(from));
            seen[from] = true;
            while (!frontier.isEmpty()) {
                final int at = frontier.pop();
                if (at == to)
                    return true;
                for (int next = 0; next < edge.length; next++)
                    if (edge[at][next] && !seen[next]) {
                        seen[next] = true;
                        frontier.push(next);
                    }
            }
            return false;
        }

        /**
         * The cache key: the rules in the order they were provided, compared by IDENTITY and never by value — see
         * {@link #sort(Set)} for why both of those are the right choice. The sequence carries the sort's input order,
         * and the map's hashing is over {@link System#identityHashCode(Object)}, so a look-up never touches a rule's
         * tid, args or function.
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
         * The carrier flowing INTO each instruction of the chain, index-aligned with {@code code.insts()}: the type a rule
         * reads that instruction's laws at. Reading every law at the SEED's type let real's {@code div⁻¹ = mult} cancel an
         * int's truncating division, so {@code 5.5.as(int::T).div(2).mult(2)} answered 5, not 4.
         * <p>
         * The types are the compiler's own: the chain is probed with the machine's resolver before it is rewritten, so a
         * law is read for the contract dispatch picks — its dom, so a {@code nat} reads the int {@code plus} it admits.
         * The probe is trusted while the type it threads stays concrete. Where it stops, its later answers rest on a
         * guessed token, so the rest of the chain is probed again from the carrier that instruction hands on: the same
         * one when it maps a generic to itself ({@code is}, {@code A ⇒ A}), else {@code #}, against which only the
         * contracts that hold for every carrier resolve.
         */
        public static List<Type> carriers(final Code code) {
            return typing(code).carriers();
        }

        /**
         * The contract the resolver picks for each instruction of the chain, index-aligned with {@code code.insts()},
         * read in the same probe as {@link #carriers}; null where none resolves. An op no {@code ?decq} claim declares
         * is keyed by it, so its form is its own: the seed's {@code {0} ⇒ A} is initial, where the bare instruction's
         * {@code # ⇒ #} reads as a mapper.
         */
        public static List<Inst> contracts(final Code code) {
            return typing(code).contracts();
        }

        /**
         * The carrier flowing into each instruction and the contract it resolves to, from one probe of the chain.
         */
        private record Typing(List<Type> carriers, List<Inst> contracts) {
        }

        private static Typing typing(final Code code) {
            final Map.Entry<Code, Typing> last = LAST_TYPING.get();
            if (null != last && last.getKey() == code)
                return last.getValue();
            final Typing typing = resolveTyping(code);
            LAST_TYPING.set(Map.entry(code, typing));
            return typing;
        }

        /**
         * The last chain {@link #typing} typed on this thread. The rules of one rewrite pass are handed the SAME code
         * object until one of them changes it, and each would otherwise probe the resolver again. Identity, not equality:
         * a hit is that very object, typed moments ago.
         */
        private static final ThreadLocal<Map.Entry<Code, Typing>> LAST_TYPING = new ThreadLocal<>();

        private static Typing resolveTyping(final Code code) {
            final List<Inst> insts = code.insts();
            final List<Type> carriers = new ArrayList<>(insts.size());
            final List<Inst> contracts = new ArrayList<>(insts.size());
            Type carrier = T(ALL);
            List<Inst> probe = probe(noobj(), code);
            int offset = 0;     // the chain position the current probe starts at
            for (int i = 0; i < insts.size(); i++) {
                final Inst inst = insts.get(i);
                carriers.add(carrier);
                if (START_INST_TID.equals(inst.tid().basePath())) {
                    contracts.add(seedContract(inst, null == probe ? null : probe.get(i - offset)));
                    carrier = inst.args().isEmpty() ? T(ALL) : carrierOf(inst.arg(0));
                    continue;
                }
                Inst resolved = null == probe ? null : probe.get(i - offset);
                // unresolved where the probe threaded its own token: that token may carry a coefficient the dom refuses
                // (is hands on int{?}, and plus wants int), which the walked carrier — a base type — does not, so the
                // rest of the chain is probed again from here before the type is given up
                if ((null == resolved || !resolved.tid().hasDom()) && offset < i
                        && null != carrier.vid() && !ALL.basePath().equals(carrier.vid().basePath())) {
                    offset = i;
                    probe = probe(carrier, code(insts.subList(i, insts.size())));
                    resolved = null == probe ? null : probe.getFirst();
                }
                contracts.add(resolved);
                final fURI dom = concrete(resolved, true);
                if (null != dom)
                    carriers.set(i, T(dom));
                final fURI rng = concrete(resolved, false);
                if (null != rng) {
                    carrier = T(rng);
                    continue;
                }
                carrier = passesThrough(resolved) ? carrier : T(ALL);
                offset = i + 1;
                probe = offset < insts.size() ? probe(carrier, code(insts.subList(offset, insts.size()))) : null;
            }
            return new Typing(Collections.unmodifiableList(carriers), Collections.unmodifiableList(contracts));
        }

        /**
         * The seed's contract: the one the chain's probe resolved, else the seed resolved alone. Its dom is
         * {@code noobj}, so it answers whatever stream it arrives in, and a probe that fails further along
         * the chain does not leave the seed untyped.
         */
        private static Inst seedContract(final Inst seed, final Inst resolved) {
            if (null != resolved && resolved.tid().hasDom())
                return resolved;
            final List<Inst> alone = probe(noobj(), code(List.of(seed)));
            return null == alone ? null : alone.getFirst();
        }

        /**
         * The chain resolved by the machine's resolver from {@code lhs}, index-aligned with the chain, or null when it
         * cannot be: a probe only, so it never fails the rewrite that asked. The whole chain goes through the resolver's
         * memo (keyed by code identity); a restarted rest is a fresh code each time, so it bypasses the memo.
         */
        private static List<Inst> probe(final Obj lhs, final Code code) {
            try {
                final Resolver resolver = Machine.current().compiler().resolver();
                final Code resolved = lhs.isNoObj()
                        ? resolver.apply(lhs, code)
                        : Resolver.Helper.resolveCode(lhs, code, resolver);
                return resolved.insts().size() == code.insts().size() ? resolved.insts() : null;
            } catch (final Exception e) {
                return null;
            }
        }

        /**
         * The base type at a resolved instruction's dom (or rng), or null when it has none, or it is generic or
         * {@code #} — a type no law is declared at.
         */
        private static fURI concrete(final Inst resolved, final boolean dom) {
            if (null == resolved || !(dom ? resolved.tid().hasDom() : resolved.tid().hasRng()))
                return null;
            final fURI type = (dom ? resolved.tid().dom() : resolved.tid().rng()).basePath();
            return type.isGeneric() || type.equals(ALL.basePath()) ? null : type;
        }

        /**
         * Whether a resolved instruction maps a generic to itself ({@code A ⇒ A}), handing on the carrier it consumes.
         */
        private static boolean passesThrough(final Inst resolved) {
            if (null == resolved || !resolved.tid().hasDom() || !resolved.tid().hasRng())
                return false;
            final fURI dom = resolved.tid().dom().basePath();
            return dom.isGeneric() && dom.equals(resolved.tid().rng().basePath());
        }

        /**
         * The carrier a seed fixes — the type of ONE obj of its stream. A bulk seed types as its stream: the member type
         * with the bulk's multiplicity as its coefficient ({@code {1,2,3}} is {@code int{3}::T}). A multiplicity is not an
         * object of the category — no theory is declared over {@code int{3}}, and {@code object::T} (dom {@code {?}})
         * refuses it — so it is dropped and the member type ({@code int::T}) carries the chain. The rules and the
         * {@code Profile} both read the carrier here, so the pruner sees exactly the carrier the rule it prunes would.
         */
        public static Type carrierOf(final Obj seed) {
            final Type type = seed.type();
            final fURI id = type.typeId();
            return null == id || id.c().isOne() || id.c().isZero() ? type : T(id.one());
        }
    }
}
