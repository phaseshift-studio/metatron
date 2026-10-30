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

package studio.phaseshift.metatron.isa.m.mach.type;

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.parser.Parser;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.typer.TypeTyper;

import static studio.phaseshift.metatron.Tokens.*;

/**
 * Compiler — lowers source to {@code code::T} by composing four atomic, overridable stages, each a
 * machine component ({@code parser::T}, {@code rewriter::T}, {@code resolver::T},
 * {@code typer::T}):
 * <ul>
 *   <li>{@link #parser()} — the {@link Parser} (reads source text and emits {@code code::T}; for
 *       what is already parsed it is the identity, lifted into code). This is the language seam: a
 *       parser emitting mtron code IS another language hosted on metatron;</li>
 *   <li>{@link #rewriter()} — the {@link Rewriter} (fixpoint over the rewrite rules);</li>
 *   <li>{@link #resolver()} — the {@link Resolver} (threads the type, resolves one inst at a
 *       time; generic binding runs inside per-candidate selection, so there is no separate
 *       binder);</li>
 *   <li>{@link #typer()} — the {@link Typer} (runtime type assertions).</li>
 * </ul>
 * {@link #apply} is the default schedule over that vocabulary —
 * {@code parse → rewrite → resolve → type}.
 * <p>
 * <b>Java first, inst-ify later.</b> The stage components are mtron recs now; the composition
 * {@code typer(resolver(rewriter(parse(source))))} is expressible in mtron once the stage insts
 * exist.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Compiler extends Component, Rec {

    // ======================== schedule ========================

    /**
     * The default schedule — {@code parse → rewrite → resolve → type}. Override to re-order, repeat,
     * or interleave the stages; a compiler is the composition, the stages are the vocabulary.
     * <p>
     * The {@code code::T} assertion this schedule used to open with is not lost, it moved: the parse
     * stage now owns the front door and is contractually code-producing ({@link Parser#apply}), so a
     * program handed in as text is accepted where it was previously a type error, and malformed text
     * is reported by the parser as a statement about the source instead of failing a check about the
     * obj that came in.
     */
    @Override
    default Code apply(final Obj source) {
        return this.typer().apply(this.resolver().apply(this.rewriter().apply(Parser.Helper.toCode(this.parser().apply(source)))));
    }

    // ======================== stage components ========================

    /**
     * @return the parse stage component ({@code parser::T}) — {@code mtron_parser::T}, the only
     * parser metatron ships. This is the language seam of the compiler: a parser that emits mtron
     * code IS another language on metatron, since the three stages after it and the processor all
     * take code.
     */
    default Parser parser() {
        return this.at(PARSER).orThrow("compiler has no resolver: %s", this);
    }

    /**
     * @return the rewriter stage component ({@code rewriter::T}) — fixpoint_rewriter::T by default
     */
    default Rewriter rewriter() {
        return this.at(REWRITER).orThrow("compiler has no rewriter: %s", this);
    }

    /**
     * @return the resolver stage component ({@code resolver::T}) — scoring_resolver::T by default
     */
    default Resolver resolver() {
        return this.at(RESOLVER).orThrow("compiler has no resolver: %s", this);
    }

    /**
     * @return the typer stage component ({@code typer::T})
     */
    default Typer typer() {
        return TypeTyper.single();
    }
}
