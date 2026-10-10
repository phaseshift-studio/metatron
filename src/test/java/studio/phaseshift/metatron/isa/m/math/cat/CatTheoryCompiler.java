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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.compiler.BasicCompiler;

import static studio.phaseshift.metatron.isa.m.mInstSet.CODE_TYPE;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_COMPILER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CatTheoryCompiler extends BasicCompiler {

    private static final CatTheoryCompiler INSTANCE = new CatTheoryCompiler();

    private CatTheoryCompiler() {
        super(MACH_COMPILER_TID, null);
    }

    public Code apply(final Obj code) {
        Type.Helper.typeCheck(code, CODE_TYPE);
        Code c = code.asCode();
        c = this.resolver().apply(c);
        c = this.rewrite().apply(c);
        c = this.resolver().apply(c);
        c = this.typer().apply(c);
        return c;
    }

    public static CatTheoryCompiler single() {
        return INSTANCE;
    }
}
