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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.BINDER;
import static studio.phaseshift.metatron.Tokens.SELECTOR;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * InstResolver — the resolution stage of {@code compiler::T}: lowers {@code code::T} to
 * {@code code::T} by threading the type through the instruction chain and resolving one inst at a
 * time. A resolver is a machine component ({@code resolver::T}); {@link Helper#resolveCode} owns
 * the threading algorithm, and the per-instruction work is delegated to a {@link Selector} (which
 * fetches and orders the candidates) and a {@link Binder} (which makes the chosen candidate
 * concrete or rejects it). The two are collaborators of the one stage, not stages themselves:
 * binding gates selection, so it runs inside the selector's candidate walk. Any selector therefore
 * composes with any binder — {@code resolver::[selector=>specificity_selector::T,
 * binder=>generic_binder::T]}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Resolver extends Component {

    /**
     * Resolve {@code code} against {@code noobj} (the compile-time lhs — the element type threads
     * through the initial inst's own argument).
     *
     * @param code the code to resolve
     * @return the resolved code
     */
    @Override
    default Code apply(final Obj code) {
        return this.apply(noobj(), code.asCode());
    }

    default Code apply(final Obj lhs, final Code code) {
        // compile-once: already fully-resolved code skips the resolve walk — but "resolved" is not
        // the whole precondition. The resolver is also what NUMBERS a chain: resolveCode stamps each
        // inst with its position vid (selfVID), and the processor walks a chain by that number
        // (Code.nextInst). A whole-chain rewriter (explain_analysis/profile_analysis) mints its
        // replacement with instC, whose lambda makes it read as resolved (Inst.isResolved == hasf)
        // while its vid is still null — so a bare `1.plus(2).explain()`, whose rewrite output is that
        // one inst, short-circuited here and reached the processor un-numbered:
        //   NullPointerException: Inst.vid() is null   (Code.nextInst -> StatefulMonad.next)
        // Requiring the numbering keeps the gate from accepting a chain the processor cannot walk.
        if (code.isResolved(true) && Helper.isNumbered(code))
            return code;
        final fURI typeId = Obj.Helper.specificTypeId(lhs);
        if (!typeId.isGeneric()) {
            final Resolver.Helper.ResolveKey key = new Resolver.Helper.ResolveKey(code, typeId, this.selector(), this.binder());
            final Code cached = Resolver.Helper.RESOLVE_CACHE.get(key);
            if (cached != null)
                return cached;
            final Code resolved = Resolver.Helper.resolveCode(lhs, code, this);
            if (resolved.isResolved(true)) {
                if (Resolver.Helper.RESOLVE_CACHE.size() >= Resolver.Helper.RESOLVE_CACHE_MAX)
                    Resolver.Helper.RESOLVE_CACHE.clear();
                Resolver.Helper.RESOLVE_CACHE.putIfAbsent(key, resolved);
            }
            return resolved;
        }
        return Resolver.Helper.resolveCode(lhs, code, this);
    }

    default Selector selector() {
        return this.at(uri(SELECTOR)).orThrow("resolver has no selector: %s", this);
    }

    default Binder binder() {
        return this.at(uri(BINDER)).orThrow("resolver has no binder: %s", this);
    }

    public static class Helper {

        private Helper() {
            // do nothing
        }

        // compile-once memo of nested-code resolution, keyed by (code identity, runtime lhs type id, and the selector and
        // binder that resolve it): every machine owns its compiler, so one code resolved by two machines is two entries
        public static final ConcurrentHashMap<ResolveKey, Code> RESOLVE_CACHE = new ConcurrentHashMap<>();
        public static final int RESOLVE_CACHE_MAX = 4096;

        /**
         * Whether every inst of {@code code} carries the position vid the resolver stamps on it.
         * A null vid means the chain has not been through {@link #resolveCode} — a rewrite's freshly
         * minted insts are exactly that — and the processor walks a chain by that number
         * ({@code Code.nextInst}), so such a chain must not be treated as compile-once.
         */
        public static boolean isNumbered(final Code code) {
            // NO TWO SHARE A NUMBER (2026-11-26): Code.nextInst finds an instruction by its vid, so a repeated vid walks
            // back to its first holder and the chain never ends. A rewrite that splices a compiled branch member into the
            // chain brings the member's own numbers (start@<0>.plus@<0>.mult@<1>). No set: this runs on every resolve
            final List<Inst> insts = code.jvm();
            for (int i = 0; i < insts.size(); i++) {
                final fURI vid = insts.get(i).vid();
                if (null == vid)
                    return false;
                for (int j = 0; j < i; j++)
                    if (vid.equals(insts.get(j).vid()))
                        return false;
            }
            return true;
        }

        public static final class ResolveKey {
            final Code code;
            final fURI typeId;
            final Selector selector;
            final Binder binder;
            final int identity;

            ResolveKey(final Code code, final fURI typeId, final Selector selector, final Binder binder) {
                this.code = code;
                this.typeId = typeId;
                this.selector = selector;
                this.binder = binder;
                this.identity = 31 * (31 * System.identityHashCode(code) + System.identityHashCode(selector)) + System.identityHashCode(binder);
            }

            @Override
            public int hashCode() {
                return 31 * this.identity + this.typeId.hashCode();
            }

            @Override
            public boolean equals(final Object other) {
                return other instanceof ResolveKey k && k.code == this.code && k.selector == this.selector && k.binder == this.binder
                        && k.typeId.equals(this.typeId);
            }
        }


        public static Inst getPolyAutoInst(final Poly<?, ?> poly, final Obj key) {
            final Obj slot = poly.atDirect(key);
            if (slot.isObjInst()) {
                if (slot.asInst().isInitial()) {
                    return slot.asInst();
                } else if (slot.isAuto()) {
                    return Obj.Helper.getAuto(slot).filter(Obj::isInst).orElse(noobj()).as();
                }
            }
            return noobj();
        }

        /**
         * Resolve a full instruction chain — threads the output type of each instruction as the
         * input type of the next via {@code Inst.resolve(Obj, Selector, Binder)}, selecting and
         * binding each inst with {@code sel} and {@code binder}. An inst that resolves without a
         * dom stays unresolved (resolved again at runtime); a filter's {0,1} wing is dropped for
         * compile-time typing only.
         *
         * @param lhs      the compile-time lhs ({@code noobj()} at the machine level)
         * @param code     the code to resolve
         * @param resolver the resolver for this stage
         * @return the resolved (or semi-resolved) code
         */
        public static Code resolveCode(final Obj lhs, final Code code, final Resolver resolver) {
            final Selector sel = resolver.selector();
            final Binder binder = resolver.binder();
            final GraphittyLogger LOG = Graphitty.log(Resolver.class);
            Obj token = lhs.isType() ? lhs : lhs.type();
            final List<Inst> resolvedCode = new ArrayList<>();
            boolean fullResolution = true;
            int i = 0;
            for (final Inst inst : code.insts()) {
                final Obj before = token;
                try {
                    final Inst bound = inst.resolve(token, sel, binder);
                    // A CALL A BUNDLE'S TYPE BINDS THROUGH ONE OF ITS FIBERS COMPILES TO THE LIFT OF THE CALL (FiberedRec):
                    // left bound, one fiber's overload would be offered to every fiber. An unbound call stays for run time
                    final Inst resolvedInst = bound.hasf() ? FiberedRec.Helper.resolve(token.asType(), bound, inst) : bound;
                    if (!resolvedInst.hasDom()) {
                        resolvedCode.add(inst.clone().selfVID(f(i)).as());
                        token = inst.hasRng() ? inst.rng() : token;
                    } else {
                        // A BRANCH'S OWN MEMBERS COMPILE WITH IT (Marko, 2026-11-25): a positional member of a branch that
                        // is no layout runs here, so it is resolved against the carrier flowing in and held in the branch's
                        // args, and route runs it as compiled rather than compiling it again for every obj. A keyed or a
                        // laid-out member runs where its place is, so it compiles there (ed27688a1). The carrier must be
                        // the type of each obj a member is fed, which holds for a branch that feeds its members one obj at
                        // a time, once: a batching branch feeds them a bag, a spanned one feeds its later runs its own
                        // output, and a stream of two types ({2,'ab'} carries </m/{int,str}>::T) has no one type. A member
                        // is held compiled only when the carrier resolves it whole and it reads no walked path (its path
                        // starts at Code.apply's start(lhs), a step the compiled member never takes); any other member is
                        // held as written and compiles per obj, as before
                        final Obj carrier = token;
                        final List<Obj> members = BRANCH_INST_TID.equals(inst.tid().basePath()) && resolvedInst.args().isLst()
                                && !resolvedInst.isBatching() && resolvedInst.tid().b().isOne() && !carrier.tid().hasBranches()
                                && resolvedInst.args().lstValue().stream().anyMatch(member -> member.isCode() && 1 < member.asCode().insts().size())
                                && !BranchInst.isLayout(resolvedInst)
                                ? resolvedInst.args().lstValue() : List.of();
                        final List<Obj> compiledMembers = members.stream().map(member -> {
                            if (!member.isCode() || 2 > member.asCode().insts().size())
                                return member;
                            final Code compiledMember = resolver.apply(carrier, member.asCode());
                            return compiledMember.isResolved(true) && !Code.Helper.computesPath(compiledMember) ? compiledMember : member;
                        }).toList();
                        // the args are rebuilt only when a member compiled: an untouched branch keeps its own args poly
                        final Inst compiled = IntStream.range(0, members.size()).allMatch(k -> members.get(k) == compiledMembers.get(k))
                                ? resolvedInst : resolvedInst.args(lst(compiledMembers));
                        resolvedCode.add(compiled.clone().selfVID(f(i)).as());
                        // A filter's rng is X{0,1} ("one-or-none": emit the object or ∅). Threading that
                        // {0,1} into the next inst makes a map's dom X{1,1} fail to match, but the ∅ branch
                        // can never reach the map — a filter that emits ∅ short-circuits the chain. So drop
                        // the 0 wing ({0,n} → {n,n}) for compile-time typing only, leaving rng() itself maybe
                        // so isFilter()/isPredicate()/gather-classification still read the filter cardinality.
                        token = resolvedInst.isFilter() ? resolvedInst.rng().c(resolvedInst.tid().d().max()) : resolvedInst.rng();
                        if (resolvedInst.isInitial() && !resolvedInst.hasRng() && resolvedInst.tid().basePath().equals(START_INST_TID)) {
                            LOG.trace("  {{g}}==>{{/g}} marking {{y}}initial{{/y}} at %s", resolvedInst);
                            token = resolvedInst.arg(0).isType() ? resolvedInst.arg(0) : resolvedInst.arg(0).type();
                        }
                    }
                    // A SPAN TYPES ITS RUNS (Marko, 2026-11-15), never span × point copies: c() counts a value's copies,
                    // while a run of a one-to-one step emits one obj, at the step's point. The zero-step run is the lhs as
                    // it came and a backward run returns to the lhs's type, so {10}plus(4) types one int, {2,3}plus(1) two,
                    // and {?}plus(1) the lhs and one more. A step whose type changes between runs is resolved once per run
                    // type, until the runs' common type stops changing
                    final cInt span = resolvedInst.tid().b();
                    if (span.isOne() || null == span.min())
                        token = token.c(c -> c.mult(resolvedInst.c()));
                    else {
                        final boolean zero = span.min() <= 0 && (null == span.max() || span.max() >= 0);
                        Type met = span.min() <= 0 ? before.c(cInt.ONE()).asType() : null;
                        Obj run = token;
                        for (long k = 1; null == span.max() || k <= span.max(); k++) {
                            final Type joined = null == met ? run.c(cInt.ONE()).asType() : Type.Helper.findLCD(List.of(met, run.c(cInt.ONE()).asType()));
                            if (joined.equals(met))
                                break;
                            met = joined;
                            if (!resolvedInst.hasDom() || (null != span.max() && k == span.max()))
                                break;
                            final Inst again = inst.resolve(run, sel, binder);
                            if (!again.hasDom())
                                break;
                            run = again.isFilter() ? again.rng().c(again.tid().d().max()) : again.rng();
                        }
                        final cInt runs = null == span.max() ? cInt.MAYBESOME() : cInt.of(span.max() - span.min() + (zero ? 0L : 1L));
                        token = met.c(runs.mult(resolvedInst.tid().d()).plus(zero ? before.c() : cInt.ZERO()));
                    }
                } catch (final Exception e) {
                    resolvedCode.add(inst.clone().selfVID(f(i)).as());
                    LOG.debug("runtime resolution of %s required", inst);
                    fullResolution = false;
                }
                i++;
            }
            final Code resolved = code.jvm(resolvedCode);
            // the code is printed only when it is logged: its constants may be as large as a lst a span's copies share,
            // whose text is every letter it stands for
            if (LOG.logger().isDebugEnabled())
                LOG.debug("%s code:\n        [{{g}}COMPILED{{/g}}]\n%s",
                        fullResolution ? "{{g}}resolved{{/g}}" : "{{y}}semi-resolved{{/y}}",
                        ObjmtronSerializer.prettyPrintCode(resolved));
            return resolved;
        }
    }
}
