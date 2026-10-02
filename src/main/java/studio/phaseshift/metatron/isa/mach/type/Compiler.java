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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.mach.type.compiler.TypeTyper;
import studio.phaseshift.metatron.isa.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.IdentityResolver;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.ScoringResolver;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.FixPointRewriter;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.IdentityRewriter;

import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.isa.m.mInstSet.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * Compiler — lowers {@code code::T} to {@code code::T} by composing three atomic, overridable
 * stages, each a machine component ({@code rewriter::T}, {@code resolver::T}, {@code typer::T}):
 * <ul>
 *   <li>{@link #rewrite()} — the {@link Rewriter} (fixpoint over the rewrite rules);</li>
 *   <li>{@link #resolver()} — the {@link Resolver} (threads the type, resolves one inst at a
 *       time; generic binding runs inside per-candidate selection, so there is no separate
 *       binder);</li>
 *   <li>{@link #typer()} — the {@link Typer} (runtime type assertions).</li>
 * </ul>
 * {@link #apply} is the default schedule over that vocabulary — {@code rewrite → resolve → type}.
 * The stage methods ({@link #rewrite(Code)}, {@link #resolve(Code)}, {@link #type(Code)}) remain as
 * the overridable seams a compiler author reaches for, each defaulting to its component accessor.
 * <p>
 * <b>Java first, inst-ify later.</b> The stage components are mtron recs now; the composition
 * {@code typer(resolver(rewriter(code)))} is expressible in mtron once the stage insts exist.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Compiler extends Machine.Component, Rec {

    // ======================== schedule ========================

    /**
     * The default schedule — {@code rewrite → resolve → type}. Override to re-order, repeat, or
     * interleave the stages; a compiler is the composition, the stages are the vocabulary.
     */
    @Override
    default Code apply(final Obj code) {
        Type.Helper.typeCheck(code, CODE_TYPE);
        Code c = code.asCode();
        c = this.rewrite().apply(c);
        c = this.resolver().apply(c);
        c = this.typer().apply(c);
        return c;
    }

    // ======================== stage components ========================

    /**
     * @return the rewriter stage component ({@code rewriter::T}) — identity by default
     */
    default Rewriter rewrite() {
        return IdentityRewriter.single();
    }

    /**
     * @return the resolver stage component ({@code resolver::T}) — identity by default
     */
    default Resolver resolver() {
        return IdentityResolver.single();
    }

    /**
     * @return the typer stage component ({@code typer::T})
     */
    default Typer typer() {
        return TypeTyper.single();
    }

    // ======================== stage · rewrite ========================

    /**
     * Identity — the rewrite algorithm lives in the {@link #rewrite()} component
     * ({@code fixpoint_rewriter::T}), not on the compiler. Override to re-order or interleave the
     * stage, but the default carries no algorithm.
     */
    default Code rewrite(final Code code) {
        return code;
    }

    // ======================== stage · resolve ========================

    /**
     * Resolve the whole code — threads the type, one inst at a time. The compile-time lhs is
     * {@code noobj}: the element type is threaded through the initial inst's own argument
     * ({@code start(1)} seeds {@code int}), and resolving against the start <em>value</em> would
     * bind {@code start}'s domain to a non-zeroable type, short-circuiting {@code start.apply(noobj)}
     * to {@code noobj} at runtime.
     */
    default Code resolve(final Code code) {
        Type.Helper.typeCheck(code, CODE_TYPE);
        return this.resolve(noobj(), code);
    }

    /**
     * Resolve the whole code against an explicit lhs — threads the type, one inst at a time.
     */
    default Code resolve(final Obj lhs, final Obj code) {
        Type.Helper.typeCheck(code, CODE_TYPE);
        return Helper.resolve(lhs, code.asCode());
    }

    // ======================== stage · type ========================

    /**
     * Identity — the type-assertion algorithm lives in the {@link #typer()} component
     * ({@code typer::T}), not on the compiler.
     */
    default Code type(final Code code) {
        return code;
    }

    // ======================== Helper ========================

    class Helper {

        private Helper() {
            // do nothing
        }

        // compile-once memo of nested-code resolution, keyed by (code identity, runtime lhs type id).
        public static final ConcurrentHashMap<ResolveKey, Code> RESOLVE_CACHE = new ConcurrentHashMap<>();
        public static final int RESOLVE_CACHE_MAX = 4096;

        public static final class ResolveKey {
            final Code code;
            final fURI typeId;
            final int identity;

            ResolveKey(final Code code, final fURI typeId) {
                this.code = code;
                this.typeId = typeId;
                this.identity = System.identityHashCode(code);
            }

            @Override
            public int hashCode() {
                return 31 * this.identity + this.typeId.hashCode();
            }

            @Override
            public boolean equals(final Object other) {
                return other instanceof ResolveKey k && k.code == this.code && k.typeId.equals(this.typeId);
            }
        }

        /**
         * Resolve {@code code} against {@code lhs}. Compile-once: already fully-resolved code skips
         * the resolve walk; nested-code resolution is memoized by (code identity, runtime lhs type).
         * The type matters — overload selection (e.g. pointwise vs gather sum) depends on the lhs
         * type, so generic types are not cached and only fully-resolved results are cached. The
         * rewrite is delegated to the rewriter component.
         */
        public static Code resolve(final Obj lhs, final Code code) {
            // compile-once: already fully-resolved code skips the resolve walk.
            if (code.isResolved(true))
                return code;
            final fURI typeId = Obj.Helper.specificTypeId(lhs);
            if (!typeId.isGeneric()) {
                final ResolveKey key = new ResolveKey(code, typeId);
                final Code cached = RESOLVE_CACHE.get(key);
                if (cached != null)
                    return cached;
                final Code resolved = ScoringResolver.resolveCode(lhs, FixPointRewriter.single().apply(code));
                if (resolved.isResolved(true)) {
                    if (RESOLVE_CACHE.size() >= RESOLVE_CACHE_MAX)
                        RESOLVE_CACHE.clear();
                    RESOLVE_CACHE.putIfAbsent(key, resolved);
                }
                return resolved;
            }
            return ScoringResolver.resolveCode(lhs, FixPointRewriter.single().apply(code));
        }
    }
}
