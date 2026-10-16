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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instA;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CodeTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)                                          % true",
            "1.plus(\"abc\")                                    % false",
            "{1,2,3}.plus(2)                                    % true",
            "3.plus(2)                                          % true",
            "{1,2,3}.plus(\"abc\")                              % false",
            "{\"1\",\"2\",\"3\"}.plus(\"abc\")                  % true",
            "*abc.plus(2)                                       % false",
            "*?int<=(abc).plus(2)                               % true",
            "1.-<[_,_]                                          % true",
            "{1,2,3}.-<[_,_]                                    % true",
            "1.-<[_,_]>-                                        % true",     // TODO:this resolves because of ring algebra
            "{1,2,3}.>-                                         % true",
            "{1,2,3}.plus(34).sum()                             % true",
            "1.plus(34).sum()                                   % true",
            "'a'.plus('b').sum()                                % true",
            "{'a','b','c'}.plus('def').sum()                    % true",
            "{'a','b','c'}.plus?str<=str('def').sum()           % true",
            "1._                                                % true",
            "1.to(a).plus(4).from(a)                            % false",  // TODO: requires variable tracking in compilation
            "1.to(a).plus(from(a))                              % false",  // TODO: requires variable tracking in compilation
            "*abc._                                             % true",
            "1.*abc._                                           % true"
    }, delimiter = '%', quoteCharacter = '^')
    public void testDomRng(final String code, final boolean resolved) {
        Code obj = ObjmtronSerializer.parse(code);
        LOG.debug("testing code resolution %s %s resolve", obj, resolved ? "{{g}}should{{X}}" : "{{r}}should not{{X}}");
        assertFalse(obj.isResolved(true));
        obj = obj.resolve(noobj());
        if (resolved) assertTrue(obj.isResolved(true));
        else assertFalse(obj.isResolved(true));
    }

    /**
     * \(\varepsilon\) — THE CODE MONOID'S IDENTITY, asserted here because it cannot be asserted at the console:
     * every other unit has a literal (\(0\), \(1\), {@code ""}, {@code <>}, {@code .}) but the chain of ZERO
     * instructions is typed by typing nothing. It is nonetheless a value the rewriter produces — a law-driven
     * rule returns {@code code(kept)}, and a rule that eliminates a whole chain leaves {@code kept} empty — so
     * which element \(\varepsilon\) is decides whether elimination is sound: the identity passes its lhs
     * through (\(\varepsilon \cdot x = x\)), the ANNIHILATOR would destroy it. The bare identity operation
     * ({@code id}, sugar {@code _}) is \(\varepsilon\)'s utterable proxy, so the two must agree.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5",
            "'abc'",
            "{1,2,3}"
    }, delimiter = '%', quoteCharacter = '^')
    public void testEmptyCodeIsTheIdentityMorphism(final String mtron) {
        final Obj lhs = ObjmtronSerializer.parse(mtron).apply();
        assertFalse(lhs.isNoObj(), "the lhs under test must be a value");
        final Code empty = code(List.of());                             // ε — what code(kept) answers when all of kept dropped
        final Code identity = code(List.of(instA(ID_INST_TID)));         // its utterable proxy — `_`
        LOG.debug("testing the code monoid identity against lhs %s", lhs);
        assertEquals(lhs, empty.apply(lhs), "ε is the identity morphism, never the annihilator");
        assertEquals(lhs, identity.apply(lhs), "a bare id is the identity morphism");
        assertEquals(empty.apply(lhs), identity.apply(lhs), "code([]) and code([id]) are the same morphism");
    }
}
