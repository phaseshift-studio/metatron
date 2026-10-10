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

package studio.phaseshift.metatron.isa.m.type.parser;

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.util.CommonUtil.mutableList;

/**
 * Parser — the first stage of {@code compiler::T}, and the seam where a LANGUAGE is hosted: it reads
 * source text and emits {@code code::T}. A parser is a machine component ({@code parser::T}); the
 * concrete {@code mtron_parser::T} reads metatron's own language, so what it accepts is what a
 * console line and a source file accept.
 * <p>
 * <b>The one obligation a new language has.</b> Because this stage emits code, and because every
 * stage after it — rewrite, resolve, type — plus the processor and the distributed runtime all take
 * code, writing a parser IS writing a language on metatron. A new surface syntax needs no new
 * instruction set, no new evaluator and no new type system: it needs a {@code parser::T} that emits
 * mtron code, wired into a {@code compiler::T} — {@code compiler::[parser=>my_parser::T]}. What that
 * buys is the substrate — mtron is Turing complete, has a rich type system, and carries both the
 * object-oriented and the functional paradigm — so the target of the lowering is a language worth
 * lowering into, rather than an IR that has to be taught about types, higher-order functions or
 * distribution after the fact. And a language whose surface syntax is elaborate but whose semantics
 * are mtron's is a parser and nothing else: no rewriter is needed, because the default one already
 * runs on the code the parser produced.
 * <p>
 * A parser is total over objs: an obj that is not a {@code str} is not source text, so it is not
 * re-read — it is lifted as it stands ({@link Helper#toCode}). That is what lets one compiler be
 * handed either a program's text or the obj it already read into, and it is required rather than
 * merely convenient for the two to be the same compiler: re-reading what is already objs would mean
 * parsing a rendering of the input, and a rendering carries no vid and no non-default tid, so the
 * addressing that later stages dispatch on would quietly be discarded.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Parser extends Machine.Component {

    /**
     * Parse {@code source} — a {@code str} of source text — and return the program it denotes. Any
     * other obj is not source text: it is lifted as it stands, never re-read.
     *
     * @param source the source text, or an obj that has already been parsed
     * @return {@code source} as {@code code::T}
     */
    Code apply(final Obj source);

    class Helper {

        private Helper() {
            // do nothing
        }

        /**
         * Lift what a parser read into the {@code code::T} the rest of the compiler takes. The three
         * branches are the three shapes a grammar produces: a code passes through, a lone inst becomes
         * a one-inst code, and anything else — a value the grammar read as itself — becomes a code that
         * starts with it.
         * <p>
         * The inst case deliberately gets no start: a head-first call is a fragment relative to its
         * lhs ({@code plus(1)} applied to {@code noobj} is {@code noobj}), and inventing a start would
         * turn a pipeline stage into a program that runs on nothing. The value case is the same
         * {@code start} lift the console gives a bare value typed at the prompt.
         *
         * @param parsed the obj a parser read
         * @return {@code parsed} as {@code code::T}
         */
        public static Code toCode(final Obj parsed) {
            if (parsed.isCode())
                return parsed.asCode();
            if (parsed.isInst())
                return MCode.code(mutableList(parsed.<Inst>asInst()));
            return MCode.code(mutableList(instB(START_INST_TID, lst(parsed))));
        }
    }
}
