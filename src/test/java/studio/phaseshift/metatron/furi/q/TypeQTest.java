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
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeQTest extends AbstractMetatronTest {

    private final Space space;

    public TypeQTest() {
        this.space = memSpace.of(rec(uri(PATTERN), uri("/t/#"), uri(QPROC), lst(QCollection.typeQ())), f("test"));
    }

    @ParameterizedTest
    @TestData(oneTime = true, value = {"nat -> int::T[?>0]"})
    @CsvSource(value = {
            "int::T       % /t/a  % /t/a -> 123         % */t/a        % 123",
            "str::T       % /t/b  % /t/b ->\"hello\"    % */t/b        % \"hello\"",
            "bool::T      % /t/c  % /t/c -> 23          % */t/c        % <ERROR>",
            "bool::T      % /t/d  % /t/d -> noobj       % */t/d        % <ERROR>",
           // "bool{0}::T   % /t/e  % /t/e -> noobj       % */t/e        % noobj",
            "int{2}::T    % /t/f  % /t/f -> 32          % */t/f        % <ERROR>",
            "int{2}::T    % /t/g  % /t/g -> {12,34}     % */t/g        % {12,34}",
            "int{3}::T    % /t/g  % /t/g -> {12,34}     % */t/g        % <ERROR>",
            "nat::T       % /t/h  % /t/h -> -12         % */t/h        % <ERROR>",
            "nat::T       % /t/i  % /t/i -> nat::-12    % */t/i        % <ERROR>",
            "nat::T       % /t/j  % /t/j -> nat::15     % */t/j        % nat::15",
            "#::T         % /t/k  % /t/k -> \"hello\"   % */t/k        % \"hello\"",
    }, delimiter = '%')
    public void testTypedVID(final String specifyType, final String writeVID, final String writeTo, final String readFrom, final String result) {
        LOG.warn("%s\n%s", this.space, Router.global().spaces());
        final Type type = ObjmtronSerializer.parse(specifyType);
        final fURI write = f(writeVID);
        Router.writeToSpace(write.addQ("T"), type);
        assertEquals(type, Router.readFromSpace(write.addQ("T")));
        assertTrue(type.isType());
        checkCodeParseApply(LOG, writeTo, result);
        if (!result.trim().equals("<ERROR>"))
            checkCodeEvaluate(LOG, readFrom, result);
        else
            assertEquals(noobj(), ObjmtronSerializer.parse(readFrom).apply());
    }
}
