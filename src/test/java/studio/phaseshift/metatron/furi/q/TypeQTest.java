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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.Assert.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeQTest extends AbstractMetatronTest {

    private final Space space;

    public TypeQTest() {
        //super(() -> {
        BootLoader.loadInstSetProvider(MACH_ISA_TID);
        this.space = memSpace.of(rec(uri("pattern"), uri("/t/#"), uri("q"), lst(QCollection.typeQ())), f("test"));
        //});
    }

    @ParameterizedTest
    @TestData(oneTime = true, value = {"nat -> int::T[?>0]"})
    @CsvSource(value = {
         //   "1                     % /t/a -> 1          % */t/a        % 1",
            "/t/a?T -> int::T      % /t/a -> 123         % */t/a        % 123",
            "/t/b?T -> str::T      % /t/b ->\"hello\"    % */t/b        % \"hello\"",
            "/t/c?T -> bool::T     % /t/c -> 23          % */t/c        % <ERROR>",
            "/t/d?T -> bool::T     % /t/d -> noobj       % */t/d        % <ERROR>",
          //  "/t/e?T -> bool{?}::T  % /t/e -> noobj       % */t/e        % noobj",
            "/t/f?T -> int{2}::T   % /t/f -> 32          % */t/f        % <ERROR>",
            "/t/g?T -> int{2}::T   % /t/g -> {12,34}     % */t/g        % {12,34}",
            "/t/h?T -> nat::T      % /t/h -> -12         % */t/h        % <ERROR>",
            "/t/i?T -> nat::T      % /t/i -> nat::-12    % */t/i        % <ERROR>",
            "/t/j?T -> nat::T      % /t/j -> nat::15     % */t/j        % nat::15",
            //  "/t/k?T -> #::T        % /t/k -> \"hello\"   % */t/k        % \"hello\"",
    }, delimiter = '%')
    public void testTypedVID(final String specifyType, final String writeTo, final String readFrom, final String result) {
        LOG.warn("%s\n%s", this.space, Router.global().spaces());
        final Obj obj = mParser.parse(specifyType).apply();
        if (obj.isType())
            assertEquals(obj.isType() ? obj : T(ALL).maybeSome(), Router.global().read(readFrom.substring(1).trim() + "?T"));
        checkCodeParseApply(LOG, writeTo, result);
        if (!result.trim().equals("<ERROR>"))
            checkCodeEvaluate(LOG, readFrom, result);
        else
            assertEquals(noobj(), mParser.eval(readFrom));
        assertEquals(obj.isType() ? obj : T(ALL).maybeSome(), mParser.eval(readFrom + "?T"));
    }
}
