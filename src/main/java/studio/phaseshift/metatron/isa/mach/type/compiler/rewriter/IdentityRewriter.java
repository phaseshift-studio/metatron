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
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.compiler.Rewriter;

import java.util.Map;

import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_REWRITER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * IdentityRewriter — the no-op {@code rewriter::T}: the default rewriter stage when a compiler does
 * not wire a concrete strategy. {@code rewrite(code) = code}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class IdentityRewriter extends MRec implements Rewriter {

    private static final IdentityRewriter INSTANCE = new IdentityRewriter(mutableMap(), MACH_REWRITER_TID, null);

    public static IdentityRewriter single() {
        return INSTANCE;
    }

    public IdentityRewriter() {
        this(mutableMap(), MACH_REWRITER_TID, null);
    }

    public IdentityRewriter(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Code apply(final Obj code) {
        return code.asCode();
    }
}
