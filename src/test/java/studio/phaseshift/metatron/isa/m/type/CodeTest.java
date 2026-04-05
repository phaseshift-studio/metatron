/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CodeTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // furi | tid | dom | range
                "1.plus(2)                                          % true",
                "1.plus(\"abc\")                                    % false",
                "{1,2,3}.plus(2)                                    % false",
                "3.plus(2)                                          % true",
                "{1,2,3}.plus(\"abc\")                              % false",
                "*abc.plus(2)                                       % false",
                "*?int<=(abc).plus(2)                               % true",
             //   "1.-<[_,_]                                          % true",   // TODO: this should pass . ??
                "{1,2,3}.-<[_,_]                                    % false",    // TODO: this should pass . ??
                "1.-<[_,_]>-                                        % true",     // TODO:this resolves because of ring algebra
                "{1,2,3}.>-                                         % true",
                "{1,2,3}.plus(34).sum()                             % false",
                "1.plus(34).sum()                                   % true",
                "'a'.plus('b').sum()                                % true",
                "{'a','b','c'}.plus('def').sum()                    % false", // TODO: this should pass . ??
                "{'a','b','c'}.plus?str<=str('def').sum()           % true",
                "1._                                                % true",
                "1.to(a).plus(4).from(a)                            % false",  // TODO: requires variable tracking in compilation
                "1.to(a).plus(from(a))                              % false",  // TODO: requires variable tracking in compilation
                "*abc._                                             % true",
                "1.*abc._                                           % true"
    }, delimiter = '%',quoteCharacter = '^')
    public void testDomRng(final String code, final boolean resolved) {
        Code obj = mParser.parse(code);
        LOG.debug("testing code resolution %s %s resolve", obj, resolved ? "{{g}}should{{X}}" : "{{r}}should not{{X}}");
        assertFalse(obj.isResolved(true));
        obj = obj.resolve(noobj()).rewrite();
        if (resolved) assertTrue(obj.isResolved(true));
        else assertFalse(obj.isResolved(true));
    }

}
