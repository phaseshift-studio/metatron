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

package studio.phaseshift.metatron.isa.mach.type.compiler.parser;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.parser.Parser;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.Map;

import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MTRON_PARSER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * MtronParser — the concrete {@code mtron_parser::T}: reads metatron's own language. It carries no
 * grammar of its own and delegates to the same seam every mtron reader in the codebase has always
 * used ({@link ObjmtronSerializer#parse}, itself a delegation to the mtron grammar), so what this
 * stage accepts is exactly what a console line, a source file and a test row accept.
 * <p>
 * The wrapper is the point rather than a formality: writing {@code source::'6.plus(1)'} inline is a
 * static call that cannot be named, introspected, replaced or read back, whereas a component with a
 * tid is a stage like the compiler's other three — so a compiler's whole lowering axis is visible in
 * its rec ({@code compiler::[=>]>>parser}) and swappable field by field.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtronParser extends MRec implements Parser {

    private static final mtronParser INSTANCE = new mtronParser(mutableMap(), MACH_MTRON_PARSER_TID, null);

    /**
     * The one instance — a parser holds no per-instance configuration (yet), so a singleton is
     * faithful to it and keeps the default compiler cheap to mint.
     */
    public static mtronParser single() {
        return INSTANCE;
    }

    public mtronParser() {
        this(mutableMap(), MACH_MTRON_PARSER_TID, null);
    }

    public mtronParser(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Code apply(final Obj source) {
        // an obj that is not source text is not read -- see Parser for why the identity case is
        // required rather than a convenience. Either way what leaves this stage is code: what the
        // grammar made, lifted by the helper every parser shares.
        return Parser.Helper.toCode(source.isStr() ? ObjmtronSerializer.parse(source.strValue()) : source);
    }
}
