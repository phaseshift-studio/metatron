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

package studio.phaseshift.metatron.lang.core.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.lang.MetatronObjTest;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.FAIL_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

public class TypeTest extends MetatronObjTest {
    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);
    private static String LAST_TYPE_DEF = null;

    @ParameterizedTest
    @CsvSource(value = {
            // obj                | type                            | matches?
            "1                    | /m/int                      | true",
            "\"a_string\"         | /m/int                      | false",
            "213.0                | /m/int                      | false",
            "1                    | #                               | true",
            "1                    | /+/+                            | true",
            "1                    | +                               | false",
            "/m/int{0}::1     | {0}                             | true",
            "/m/int{0}::1     | {,0}                           | true",
            "/m/int{0}::1     | +{+}                            | false",
            "/m/int{0}::1     | /+/+{?}                         | true",
            "/m/int{0}::1     | /+/+{0,1}                       | true",
            "/m/int{0}::1     | /+/+{0,99}                      | true",
            "/m/int{0}::1     | /+/+{*}                         | true",
            "1                    | /+/#                            | true",
            "int:1                | /+/#                            | true",
            "</m/int>::1      | /m/int                      | true",
            "</m/int>::1      | /m/+                        | true",
            "</m/int>::1      | /m/+/+                      | false",
            "</m/int>::1      | /m/+/#                      | true",
            "/m/int::1        | /m/int                      | true",
            "/m/int::1        | /m/+                        | true",
            "/m/int{2}::1     | /m/+                        | false",
            "/m/int{2}::1     | /m/+{*}                     | true",
            "/m/int::1        | /m/+{?}                     | true",
            "/m/int::1        | /m/+/+                      | false",
            "/m/int::1        | /m/+/#                      | true",
            "{c,d}                | /m/uri{2}                   | true",
            "{c,d}                | /m/+{2}                     | true",
            "str::\"abc\"         | /+/+/#                          | true",
            "/m/int::\"abc\"  | /+/+/+                          | false",
            "/m/int::1        | /+/+                            | true",
            "/m/str::'abc'    | /+/int                          | false",
            "str::'abc'           | /+/int                          | false",
            "1                    | /+/int                          | true",
            "1                    | /+/str                          | false",
            "1                    | /m/+                        | true",
            "1                    | /m/+/+                      | false",
            "1                    | /m/int{+}                   | true",
            "int{2}::1            | /m/int{1}                   | false",
            "{1,2,3,4}            | /m/int{4}                   | true",
            "{1,2,3,4}            | /m/int{3}                   | false",
            "{1,2,3,4}            | /m/int{0,3}                 | false",
            "{1,2,3,4}            | /m/int{3}                   | false",
            "{1,2,3,4}            | /m/int{0,5}                 | true",
            "{1,2,3,4}            | /m/int{*}                   | true",
            "{1,2,3,'abc'}        | /m/int{*}                   | false",
            "{1,2,3,'abc'}        | /m/+{*}                     | true",
            "{1,2,3,'abc'}        | /m/+{0,}                    | true",
            "{1,2,3,'abc'}        | /m/+{1,}                    | true",
            "{1,2,3,'abc'}        | /m/+{+}                     | true",
            "{1,2,3,'abc'}        | /m/+{2}                     | false",
            "{1,2,3,'abc'}        | /m/+{17,}                   | false",
            "{1,2,3,'abc'}        | /m/+{5,}                    | false",
            "{1,2,3,4}            | /m/str{*}                   | false",
            "{1,2,3,4}            | #{+}                            | true",
            "{1,2,3,4}            | int{+}                          | true",
            "{1,2,3,4}            | int{4}                          | true",
            "{1,2,3,4}            | int{3}                          | false",
            "{int{2}::1,int{2}::4}| int{3,5}                        | true",
            "{int{2}::1,int{2}::4}| int{4}                          | true",
            "{int{2}::1,int{2}::4}| int{3}                          | false",
            "{/m/int{2}::1,2} | /m/int{3}                   | true", // TODO: think this through more carefully
            "noobj                | #{0}                            | true",
            "noobj                | #{0,0}                          | true",
            "noobj                | #{?}                            | true",
            "noobj                | #{1}                            | false",
            "noobj                | +{0}                            | true",
            "noobj                | a/b/c{0}                        | true",
            "[a=>b]               | #                               | true",
            "plus::(2)            | /m/inst/plus                | true",
            "plus::(2)            | /m/+/plus                   | true",
            "plus{2}::(2)         | /m/inst/plus{2}             | true",
            "plus{5}::(2)         | /m/inst/plus{2,7}           | true",
            "plus{4}::()          | #{1,3}                          | false",
            "plus{4}::()          | /m/+/plus{4}                | true",
            "plus{4}::()          | /m/+/+{*}                   | true"
    }, delimiter = '|')
    public void testType(final String obj, final String typefURI, final boolean matches) {
        try {
            Obj o = mParser.m_obj().parse(obj).get();
            Type t = T(f(typefURI.trim()));
            LOG.debug("testing %s %s %s", o, matches ? "{{c}}in{{/c}}" : "{{c}}not in{{/c}}", t);
            assertEquals(matches, o.matches(t));
            //if (!typefURI.startsWith("#") && !o.isNoObj())
            //    this.testType(obj, fURI.of("#[" + o.tid().coefficientValue() + "]").toString(), !o.isNoObj());
            //final boolean a = t.matches(o);
            // assertEquals(matches, a);
        } catch (Exception e) {
            assertFalse(matches, "an exception occurred: " + e);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            // obj               | type                                         | matches?
            "noobj               | noobj{0}::T                                | true",
            "noobj               | abc{*}::T                                  | true",
            "noobj               | abc{?}::T                                  | true",
            "noobj               | abc{+}::T                                  | false",
            "1                   | noobj::T                                   | false",
            "1                   | str::T                                     | false",
            "1                   | lst::T                                     | false",
            "1                   | int::T                                     | true",
            "'a_string'          | int::T                                     | false",
            "213.0               | int::T                                     | false",
            "1                   | int::T[is(eq(1))]                            | true",
            "1                   | int::T[is(eq(2))]                            | false",
            "{1,1}               | int{2}::T[is(eq({2,2}))]                     | false",
           // "{1,1}               | int{2}::T[?={1,1}]                     | true",
            "{1,2}               | int{2}::T                                     | true",
            // "{1,1}               | int{2}::T[is(eq({1,1}))]                   | true",
            "{1,1}               | int{2}::T                                  | true",
            "{1,1}               | int::T[is(gt(0))]                            | false",
            "{1,1}               | int{2}::T[is(gt(0))]                         | true",
            "1                   | int{2}::T[is(gt(0))]                         | false",
            "{0,0}               | int{2}::T[is(gt(0))]                         | false",
             "{2,3}               | int{2}::T[is(gt(1))]                         | true",
            //"{0,1}               | int{2}::T[is(gt(0))]                         | false",
            "{0,0}               | int{2}::T[is(gt(1))]                         | false",
            //"{0,-1}               | int{2}::T[is(gt(1))]                         | false",
            //  "1               | int^:is(gt(0))                               | false"},
    },
            delimiter = '|')
    public void testTypeObj(final String obj, final String type, final boolean matches) {
        Obj o = mParser.m_obj().parse(obj).get();
        Type t = mParser.m_obj().parse(type).get();
        LOG.trace("testing %s %s %s", o, matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", t);
        assertEquals(matches, o.matches(t));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // tid   |  typedef                                 | instance                                         | matches?
            "person  % rec::T[?[name=>?str::T,age=>?int::T]]    % person::[name=>'enoch',age=>365]                 % true",
            "person  % .                                        % person::7                                        % false",
            "person  % .                                        % person::'a person'                               % false",
            "person  % .                                        % person::[name=>'enoch']                          % false",
            "person  % .                                        % person::[age=>333]                               % false",
            "person  % .                                        % person::[=>]                                     % false",
            "person  % .                                        % person::[name=>'a',age=>1,b=>2]                  % true",
            "person  % .                                        % person::[name=>'a',age=>1,b=>noobj]              % true",
            "person  % .                                        % person::[name=>'a',age=>1.2,b=>noobj]            % false",
            "person  % .                                        % [name=>'base',age=>1]                            % true",
            "person  % .                                        % [name=>'base']                                   % false",
            "person  % .                                        % [name=>'base',age=>'the number one']             % false",
            "person  % .                                        % [name=>'base',age=>1,another=>[a=>b]]            % true",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[is(gt(0))]                        % nat::23                                          % true",
            "nat     % .                                        % nat::-23                                         % false",
            "nat     % .                                        % nat::'a big number'                              % false",
            "nat     % .                                        % nat::2 + 6                                       % true",
            "nat     % .                                        % nat::2 + -6                                      % false",
            "nat     % .                                        % 23.as(nat::T)                                    % true",
            "nat     % .                                        % -23.as(nat::T)                                   % false",
            "nat     % .                                        % 2.as(plus(6).as(nat::T))                         % true",
            "nat     % .                                        % 2.as(plus(-6).as(nat::T))                        % false",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[?>0][-<|[?<0 => _⋅-1,_ => _]>>]   % nat::23                                          % true",
            "nat     % .                                        % nat::-23                                         % false",
            "nat     % .                                        % nat::'a big number'                              % false",
            "nat     % .                                        % nat::2 + 6                                       % true",
            "nat     % .                                        % nat::2 + -6                                      % false",
            "nat     % .                                        % 23.as(nat::T)                                    % true",
            "nat     % .                                        % -23.as(nat::T)                                   % true",
            "nat     % .                                        % 2.as(plus(6).as(nat::T))                         % true",
            "nat     % .                                        % 2.as(plus(-6).as(nat::T))                        % true",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[?>0]                              % nat::150                                          % true",
            ".       % .                                        % nat::-150                                         % false",
            "agenat  % nat::T[?<125]                            % agenat::150                                       % false",
            ".       % .                                        % agenat::-1                                        % false",
            ".       % .                                        % agenat::29                                        % true",
    }, delimiter = '%')
    public void testTyping(final String tid, final String typeDef, final String instance, final boolean shouldSucceed) {
        try {
            Router.writeToSpace(tid, noobj());
            Obj type = mParser.parse(typeDef.trim().equals(".") ? LAST_TYPE_DEF : typeDef.trim());
            LAST_TYPE_DEF = typeDef.trim().equals(".") ? LAST_TYPE_DEF : typeDef.trim();
            Router.writeToSpace(tid, type);
            assertEquals(type, Router.readFromSpace(tid));
            LOG.debug("testing %s %s %s", instance, shouldSucceed ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", type);
            try {
                Obj inst = mParser.eval(instance.trim());
                //LOG.debug("instance: %s", inst);
                if (!shouldSucceed) {
                    LOG.debug("instance: %s %s %s", inst.type(), inst.isFail(), inst.tid().equals(FAIL_TID));
                    if (inst.tid().equals(FAIL_TID))
                        assertFalse(shouldSucceed);
                    else if (!inst.tid().equals(f(tid)))
                        assertEquals(shouldSucceed, inst.matches(type)); // type checking for base types that are not :: specified
                    else
                        assertEquals(noobj(), inst);
                }
            } catch (final Exception e) {
                assertFalse(shouldSucceed);
            }
            assertTrue(type.isType());
        } finally {
            Router.writeToSpace(tid, noobj());
        }
    }
}
