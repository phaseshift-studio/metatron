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

package studio.phaseshift.metatron.lang;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class SpaceTest extends MetatronTest {

    public static final List<String> PREVIOUS_LINE = new ArrayList<>(List.of("", "", ""));
    public static Supplier<Space> SPACE;

    @ParameterizedTest
    @CsvSource(value = {
            "1.to(a)                                               % *a                       % 1",
            "/t -> [a,b,c]                                         % */t                      % [a,b,c]",
            ".                                                     % */t/#                    % {[a,b,c],a,b,c}",
            ".                                                     % */t/0                    % a",
            ".                                                     % */t/1                    % b",
            ".                                                     % */t/2                    % c",
            ".                                                     % */t/+                    % {a,b,c}",
            ".                                                     % */t/+/                   % [/t/0=>a,/t/1=>b,/t/2=>c]>-",
            ".                                                     % */t/                     % [/t/0=>a,/t/1=>b,/t/2=>c]>-",
            ".                                                     % */t/+                    % [a,b,c]>-",
            ".                                                     % */t/0                    % a",
            ".                                                     % */t/1                    % b",
            ".                                                     % */t/2                    % c",
            "/t -> [a,[b,[c,d],e],f]                               % */t/0                    % a",
            ".                                                     % */t                      % [a,[b,[c,d],e],f]",
        //    ".                                                     % */+                      % [a,[b,[c,d],e],f]",
        //    ".                                                     % */+/                     % [/t=>[a,[b,[c,d],e],f]]>-",
            ".                                                     % */t/+                      % {a, [b,[c,d],e],f}",
            ".                                                     % */t/+/                     % [/t/0=>a,/t/1=>[b,[c,d],e],/t/2=>f]>-",
            ".                                                     % */t/+/+                  % {b,[c,d],e}",
            ".                                                     % */t/+/+/                 % [/t/1/0=>b,/t/1/1=>[c,d],/t/1/2=>e]>-",
            ".                                                     % *t                       % noobj",
            ".                                                     % */t/0/0                  % noobj",
            ".                                                     % */t/1/0                  % b",
            ".                                                     % */t/1/1                  % [c,d]",
            ".                                                     % */t/0/1/1                % noobj",
            ".                                                     % */t/1/1/1                % d",
            ".                                                     % */t/1/1/+                % {c,d}",
            ".                                                     % */t/+                    % {a,[b,[c,d],e],f}",
            ".                                                     % */t/+/+                  % {b,[c,d],e}",
            ".                                                     % */t/+/+/+                % {c,d}",
            ".                                                     % */t/+/+/+/+              % noobj",
            ".                                                     % */t/+/+/+/+/+            % noobj",
            ".                                                     % */t/+/+/+/               % [/t/1/1/0=>c,/t/1/1/1=>d]>-",
            ".                                                     % */t/#                    % {[a,[b,[c,d],e],f],a,[b,[c,d],e],b,[c,d],c,d,e,f}",
            "/t -> [a=>1,b=>2,c=>3]                                % */t                      % [a=>1,b=>2,c=>3]",
            ".                                                     % */t/a                    % 1",
            ".                                                     % */t/b                    % 2",
            ".                                                     % */t/c                    % 3",
            ".                                                     % */t/c                    % 3",
            ".                                                     % */t/+                    % {1,2,3}",
            ".                                                     % *</t/+>.sum?int<=int{*}()% 6",
           // ".                                                     % *#.sum?int<=int{*}()      % .",
           // ".                                                     % *(/+/+).sum?int<=int{*}()    % .",
            "/t/ -> [a=>1,b=>2,c=>3]                               % */t/                      % [/t/a=>1,/t/b=>2,/t/c=>3]>-",
           // ".                                                     % */t                       % noobj",
            ".                                                     % */t/a                     % 1",
            "/t -> [a=>[b=>2,c=>3],d=>4]                           % */t/a/b                   % 2",
            ".                                                     % */t/#                     % [[a=>[b=>2,c=>3],d=>4],[b=>2,c=>3],2,3,4]>-",
            ".                                                     % *t/                       % noobj", // note there is no leading slash
            ".                                                     % */t/                      % [/t/a/b=>2,/t/a/c=>3,/t/d=>4]>-",
            ".                                                     % */t/a/c                   % 3",
            ".                                                     % */t/a                     % [b=>2,c=>3]",
            ".                                                     % */t/d                     % 4",
            ".                                                     % */t/+                     % [[b=>2,c=>3],4]>-",
            ".                                                     % */t/+/#                   % [[a=>[b=>2,c=>3],d=>4],[b=>2,c=>3],2,3,4]>-",
       //     ".                                                     % */t/+/+/#                 % [[b=>2,c=>3],2,3,4]>-",
            ".                                                     % */t/a/                    % [/t/a/b=>2,/t/a/c=>3]>-",
            ".                                                     % */t/a/+                   % {2,3}",
            ".                                                     % */t/a/+/                  % [/t/a/b=>2,/t/a/c=>3]>-"
            //"1.vid(abc)                                            % *abc                     % 1@abc",
            //"[1@a,2@b,3@c]@d.map(10).vid(b)                        % *d                       % [1@a,10@b,3@c]@d"
    }, delimiter = '%')
    void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {
        final Space space = SPACE.get();
        Router.global().addSpace(space);
        final Obj writeObj = mParser.parse(writeExpression.equals(".") ? PREVIOUS_LINE.get(0) : writeExpression).apply();
        final Obj readObj = mParser.parse(readExpression.equals(".") ? PREVIOUS_LINE.get(1) : readExpression).apply();
        final Obj resultObj = mParser.parse(expectedExpression.equals(".") ? PREVIOUS_LINE.get(2) : expectedExpression).apply();
        if (!writeExpression.equals("."))
            PREVIOUS_LINE.set(0, writeExpression);
        if (!readExpression.equals("."))
            PREVIOUS_LINE.set(1, readExpression);
        if (!expectedExpression.equals("."))
            PREVIOUS_LINE.set(2, expectedExpression);
        Graphitty.log(SPACE).debug("\n\twrite [%s => %s]\n\tread [%s => %s]\n\texpected [%s => %s]",
                writeExpression, writeObj,
                readExpression, readObj,
                expectedExpression, resultObj);
        try {
            assertEquals(resultObj, readObj);
        } catch (final Exception e) {
            LOG.error(e);
        } finally {
            Router.global().removeSpace(space.vid());
            assertDoesNotThrow(space::close);
        }
    }


    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "a -> 1                                               % 1"
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }

  /*  public void testMonoSpace() {
        space.
    }
*/
}
