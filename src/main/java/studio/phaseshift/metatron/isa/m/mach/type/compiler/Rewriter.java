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

import java.util.*;

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
     * The rewrite stage's typing: the carrier flowing into each instruction of a chain, and the contract each resolves
     * to, probed with the machine's own resolver — the types a law-driven rule reads an instruction's laws at, before
     * the compiler has resolved anything.
     */
    class Helper {

        private Helper() {
            // do nothing
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
