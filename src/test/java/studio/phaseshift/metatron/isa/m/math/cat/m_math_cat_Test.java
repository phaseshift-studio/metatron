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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.isa.m.mInstSetTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;

import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class m_math_cat_Test extends mInstSetTest {

    public m_math_cat_Test() {
        super();
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(CAT_ISA_TID);
    }

    @Training(
            instruction = "what does the expression {{{code}}} rewrite to?",
            input = "{{{code}}}",
            output = "{{{expected}}}")
    @Training(
            instruction = "what does the expression {{{code}}} evaluate to?",
            input = "{{{code}}}",
            output = "{{{expectedResult}}}")
    @Training(
            instruction = "what does the rewritten expression {{{expected}}} evaluate to?",
            input = "{{{expected}}}",
            output = "{{{expectedResult}}}")
    @ParameterizedTest
    @CsvSource(value = {
            // map_nest_rewrite tests
            "1.map(+2)                              % 1+2   % 3",
           /* "1.map(map(map(+2)))                    % 1+2   % 3",
            "1.map(map(map(map(+2))))               % 1+2   % 3",
            "1.map(map(map(map(map(+2)))))          % 1+2   % 3",
            "1.map(map(map(map(map(map(+2))))))     % 1+2   % 3",*/
            "1.map(map(+2))._                       % 1+2   % 3",
            "1._.map(map(+2))._                     % 1+2   % 3",
            "1.+2.map(map(_))._                    % 1.plus(2)   % 3",

            // id_removal_rewrite tests
            "1._._._                    % start(1)            % 1",
            "1._._._._                  % start(1)            % 1",
            "1._._._._._                % start(1)            % 1",
            "1._._._._._._              % start(1)            % 1",
            "1._._._._._._._            % start(1)            % 1",
            "1._._._._._._._._          % start(1)            % 1",
            "1._._._._._._._.+2         % start(1).plus(2)    % 3",
            "1._._._._._._._.+2._       % start(1).plus(2)    % 3",
            "1._._._._._._._.+(_)._     % start(1).plus(_)    % 2",

            // else_after_count_rewrite tests
            "{1,2,3}.count().else(0)                % {1,2,3}.count()              % 3",
            "{1,2,3}.count().else(999)              % {1,2,3}.count()              % 3",

            // plus_zero_rewrite tests - Int
            "5.plus(0)                              % start(5)                     % 5",
            "10.plus(0)                             % start(10)                    % 10",
            "0.plus(0)                              % start(0)                     % 0",
            "-5.plus(0)                             % start(-5)                    % -5",
            "5.plus(0).plus(3)                      % start(5).plus(3)             % 8",

            // plus_zero_rewrite tests - Real
            "5.0.plus(0.0)                          % start(5.0)                   % 5.0",
            "10.5.plus(0.0)                         % start(10.5)                  % 10.5",
            "0.0.plus(0.0)                          % start(0.0)                   % 0.0",
            "-5.5.plus(0.0)                         % start(-5.5)                  % -5.5",
            "5.0.plus(0.0).plus(3.0)                % start(5.0).plus(3.0)         % 8.0",

            // plus_zero_rewrite tests - Str (string concatenation with empty string)
            "\"hello\".plus(\"\")                   % start(\"hello\")             % \"hello\"",
            "\"ab\".plus(\"\")                      % start(\"ab\")                % \"ab\"",
            "\"\".plus(\"\")                        % start(\"\")                  % \"\"",
            "\"test\".plus(\"\").plus(\"123\")      % start(\"test\").plus(\"123\") % \"test123\"",

            // plus_zero_rewrite tests - Lst (list concatenation with empty list)
            "[1,2,3].plus([,])                      % start([1,2,3])               % [1,2,3]",
            "[a,b].plus([,])                        % start([a,b])                 % [a,b]",
            "[,].plus([,])                          % start([,])                   % [,]",
            "[1,2].plus([,]).plus([3,4])            % start([1,2]).plus([3,4])     % [1,2,3,4]",

            // plus_zero_rewrite tests - Rec (record merging with empty record)
            "[a=>1,b=>2].plus([=>])                 % start([a=>1,b=>2])           % [a=>1,b=>2]",
            "[x=>y].plus([=>])                      % start([x=>y])                % [x=>y]",
            "[=>].plus([=>])                        % start([=>])                  % [=>]",
            "[a=>1].plus([=>]).plus([b=>2])         % start([a=>1]).plus([b=>2])   % [a=>1,b=>2]",

            // plus_zero_rewrite tests - Bytes (byte concatenation with empty bytes)
            // Note: Bytes zero is represented as bytes(ByteBuffer.wrap(new byte[0]))
            // Skipping bytes tests as empty bytes literal syntax needs investigation

            // mult_one_rewrite tests - Int
            "5.mult(1)                              % start(5)                     % 5",
            "10.mult(1)                             % start(10)                    % 10",
            "0.mult(1)                              % start(0)                     % 0",
            "-5.mult(1)                             % start(-5)                    % -5",
            "5.mult(1).mult(3)                      % start(5).mult(3)             % 15",

            // mult_one_rewrite tests - Real
            "5.0.mult(1.0)                          % start(5.0)                   % 5.0",
            "10.5.mult(1.0)                         % start(10.5)                  % 10.5",
            "0.0.mult(1.0)                          % start(0.0)                   % 0.0",
            "-5.5.mult(1.0)                         % start(-5.5)                  % -5.5",
            "5.0.mult(1.0).mult(3.0)                % start(5.0).mult(3.0)         % 15.0",

            // mult_one_rewrite tests - Lst (Cartesian product with smart identity)
            // Lst.mult() performs Cartesian product: [a,b] × [c,d] = [[a,c], [a,d], [b,c], [b,d]]
            // The identity is [id()] - a list containing the id() instruction
            // Note: The mult_one_rewrite doesn't currently work for Lst because the matcher
            // checks equality at match-time, and [id(noobj)] != [id()] due to domain differences
            // The mult() method itself handles the identity case correctly at runtime
            // Skipping these tests for now - they would require enhancing the rewrite matcher

            // Combined rewrites (plus_zero + mult_one)
            "5.plus(0).mult(1)                      % start(5)                     % 5",
            "{1,2,3}.count().plus(0)                % {1,2,3}.count()              % 3",
            "{1,2,3}.count().else(0).plus(0)        % {1,2,3}.count()              % 3",
            "5.0.plus(0.0).mult(1.0)                % start(5.0)                   % 5.0",
            "\"hello\".plus(\"\").plus(\"world\")   % start(\"hello\").plus(\"world\") % \"helloworld\"",
            "[1,2].plus([,]).plus([3])              % start([1,2]).plus([3])       % [1,2,3]",
            "[a=>1].plus([=>]).plus([b=>2])         % start([a=>1]).plus([b=>2])   % [a=>1,b=>2]",

            // split_collapse_rewrite tests (ring-theoretic branch collapsing)
            "{1}-<[plus(3),plus(3)]>-               % {1}.plus{2}(3)                % int{2}::4",
            "{1}-<[plus(2),plus(2),plus(2)]>-       % {1}.plus{3}(2)                % int{3}::3",
            "{5}-<[mult(2),mult(2)]>-               % {5}.mult{2}(2)                % int{2}::10",

            // split_merge_left_factor_rewrite tests (distributive property - left factoring)
            // Pattern: a-<[b.c.d, b.c.e]>- → a.b.c-<[d, e]>-
            "1-<[plus(2).mult(3), plus(2).mult(4)]>-                    % 1.plus(2).-<[mult(3),mult(4)]>-              % {9,12}",
            "5-<[plus(1).plus(1), plus(1).plus(2)]>-                    % 5.plus(1)-<[plus(1),plus(2)]>-               % {7,8}",
            "10-<[mult(2).plus(5), mult(2).plus(10)]>-                  % 10.mult(2).-<[plus(5),plus(10)]>-            % {25,30}",
            "{1,2}-<[plus(10).mult(2), plus(10).mult(3)]>-              % {1,2}.plus(10)-<[mult(2),mult(3)]>-          % {22,33,24,36}",
            "3-<[plus(1).plus(1).mult(2), plus(1).plus(1).mult(3)]>-    % 3.plus(1).plus(1)-<[mult(2),mult(3)]>-       % {10,15}",

            // split_merge_right_factor_rewrite tests (distributive property - right factoring)
            // Pattern: a-<[b.d, c.d]>- → a-<[b, c]>-.d
            // Note: The following test expects split_merge_collapse to work, which is currently not applying
            //"1-<[_.plus(2),plus(2)._,_.plus(2)._]>-._.sum()                       % 1.plus{3}(2).sum()                 % 9",
            "1-<[plus(2).mult(5), plus(3).mult(5)]>-                    % 1.-<[plus(2),plus(3)]>-.mult(5)              % {15,20}",
            "10-<[mult(2).plus(100), mult(3).plus(100)]>-               % 10.-<[mult(2),mult(3)]>-.plus(100)           % {120,130}",
            "5-<[plus(1).mult(2), plus(2).mult(2)]>-                    % 5.-<[plus(1),plus(2)]>-.mult(2)              % {12,14}",
            "{1,2}-<[plus(5).mult(10), plus(6).mult(10)]>-              % {1,2}-<[plus(5),plus(6)]>-.mult(10)          % {60,{2}70,80}",
            "2-<[mult(3).mult(2).plus(1), mult(4).mult(2).plus(1)]>-    % 2.-<[mult(3),mult(4)]>-.mult(2).plus(1)      % {13,17}",

            // Combined left and right factoring (both common prefix and suffix)
            // Note: With .repeat(), both rewrites apply iteratively - left factor extracts plus(1), then right factor extracts plus(10)
            "1-<[plus(1).mult(2).plus(10), plus(1).mult(3).plus(10)]>-  % start(1).plus(1).-<[mult(2),mult(3)]>-.plus(10)    % {14,16}",
    }, delimiter = '%')
    public void testCatRewrites(final String code, final String expected, final String expectedResult) throws Exception {
        AbstractMetatronTest.checkCodeRewrite(LOG, code, expected, expectedResult, false);
    }

}
