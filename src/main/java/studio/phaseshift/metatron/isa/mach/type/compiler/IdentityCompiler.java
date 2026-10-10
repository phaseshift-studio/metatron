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

package studio.phaseshift.metatron.isa.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.IdentityRewriter;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.REWRITER;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_COMPILER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * IdentityCompiler — the {@code compiler::T} whose rewrite stage does nothing: the default stages
 * with identity_rewriter::T in the rewriter slot, so the no-op is a rec entry like any other stage
 * rather than a Java override.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class IdentityCompiler extends BasicCompiler {

    private static final IdentityCompiler INSTANCE = new IdentityCompiler(
            stages(mutableMap(uri(REWRITER), IdentityRewriter.single())), MACH_COMPILER_TID, null);

    public static final IdentityCompiler single() {
        return INSTANCE;
    }

    protected IdentityCompiler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }
}
