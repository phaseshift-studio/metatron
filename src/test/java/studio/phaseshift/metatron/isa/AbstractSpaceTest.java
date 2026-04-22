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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestCategory;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractSpaceTest extends AbstractMetatronTest {

    protected int sleepBetweenReads = 0;
    protected Space space;
    protected final Supplier<Space> spaceSupplier;
    protected Space spaceStorage = null;
    protected static final List<String> PREVIOUS_LINE = new ArrayList<>(List.of("", "", ""));
    /** Set to true when a seed write is skipped (rejected by root enforcement); cleared on next non-"." row. */
    protected static boolean seedWriteSkipped = false;
    protected final fURI baseURI;

    public AbstractSpaceTest(final Supplier<Space> spaceSupplier) {
        this(f("/t"), spaceSupplier);
    }

    public AbstractSpaceTest(final fURI baseURI, final Supplier<Space> spaceSupplier) {
        super();
        this.baseURI = baseURI;
        this.spaceSupplier = spaceSupplier;
    }

    public Space getSpace() {
        return this.space;
    }

    /**
     * Called when a write expression evaluates to a fail object during testMonoReadWrite.
     * Override to return true in spaces that enforce root type constraints (e.g. dcmntSpace)
     * so that tests writing non-conforming values at the document root are skipped gracefully
     * rather than failed.
     *
     * @param writeFailObj the fail obj returned by the write expression
     * @return true if this failure is expected and the test case should be skipped
     */
    protected boolean expectWriteRejection(final Obj writeFailObj) {
        return false;
    }

    @BeforeEach
    protected void setup() {
        //if (!Router.global().hasSpaceFor(this.baseURI))
        //    this.spaceStorage = memSpace.of(rec(uri(PATTERN), uri(this.baseURI.retract(1).extend("#"))), f("/sys/space").extend(this.baseURI.retractPattern().name()));
        this.space = this.spaceSupplier.get();
        if (null == this.space)
            Assertions.fail("space supplier yielded a null space");
        if (this.space.vid() == null)
            LOG.debug("provided space has no vid and thus can not be shutdown automatically");
    }

    @AfterEach
    protected void stop() {
        if (null == this.space) {
            Assertions.fail("space nullified over course of testing");
            return;
        }
        if (null != this.space.vid()) {
            Router.global().removeSpace(this.space.vid());
            assertDoesNotThrow(this.space::close);
        }
        if (null != this.spaceStorage) {
            Router.global().removeSpace(this.spaceStorage.vid());
            this.spaceStorage.close();
            this.spaceStorage = null;
        }
        this.space = null;
    }

    public static Map<fURI, Obj> generateRandomData(final fURI furiPrefix, int size) {
        final Map<fURI, Obj> data = new HashMap<>();
        for (int i = 0; i < size; i++) {
            data.put(furiPrefix.extend("x" + i), str("value" + i));
        }
        return data;
    }

    /*@TestCategory.Crud
    @TestCategory.Concurrent
    @ParameterizedTest(name = "[{index}] String: {0}")
    @CsvSource(value = {
            "$$/xyz?subq     ->(||(>>1.to($$/abc)))             % $$/xyz -> 32            % *$$/abc.eq(32)",
            "$$/xyz?subq     ->(||(>>1.plus(10).to($$/abc)))    % $$/xyz -> 12            % *$$/abc.eq(22)",
            "$$/xyz/a?subq   ->(||(>>0.to($$/abc)))             % $$/xyz/a -> 12          % *$$/abc.eq($$/xyz/a)",
            "$$/xyz/#?subq   ->(||(>>0.to($$/abc)))             % $$/xyz/a -> 12          % *$$/abc.eq($$/xyz/a)",
            "$$/xyz/+/+?subq ->(||(>>0.to($$/abc)))             % $$/xyz/a -> 12          % *$$/abc.else(true)",
            "$$/xyz/+/+?subq ->(||(>>1.to($$/abc)))             % $$/xyz/a/b -> 12        % *$$/abc.eq(12)"
    }, delimiter = '%')
    public void testSubQ(String subscription, String writing, String expecting) {
        space.addQ(QCollection.subq());
        final Obj sub = mParser.eval(make(subscription));
        assertEquals(SUBSCRIPTION_TID, sub.tid());
        final Obj writeObj = mParser.eval(make(writing));
        //LOG.info("SUBSCRIPTIONS: %s", mParser.eval(make("*$$/xyz?subq")));
        CommonUtil.sleepThread(100);
        assertTrue(mParser.eval(make(expecting)).boolValue());
    }*/

    @TestCategory.Crud
    @TestCategory.ReadWrite
    @ParameterizedTest
    @TestData(oneTime = false, value = {""})
    @CsvSource(value = {
            "1.to(a)                                               % *a                                % 1",
            "$$ -> [a,b,c]                                         % *<$$>                              % [a,b,c]",
            ".                                                     % *<$$/#>                            % {[a,b,c],a,b,c}",
            ".                                                     % *<$$/0>                            % a",
            ".                                                     % *<$$/1>                            % b",
            ".                                                     % *<$$/2>                            % c",
            ".                                                     % *<$$/+>                            % {a,b,c}",
            ".                                                     % *<$$/+/>                           % [<$$/0>=>a,<$$/1>=>b,<$$/2>=>c]>-",
            ".                                                     % *<$$/>                             % [<$$/0>=>a,<$$/1>=>b,<$$/2>=>c]>-",
            ".                                                     % *<$$/+>                            % [a,b,c]>-",
            ".                                                     % *<$$/0>                            % a",
            ".                                                     % *<$$/1>                            % b",
            ".                                                     % *<$$/2>                            % c",
            "$$ -> [a,[b,[c,d],e],f]                               % *<$$/0>                            % a",
            ".                                                     % *<$$>                              % [a,[b,[c,d],e],f]",
            ".                                                     % *<$$/+>                            % {a,[b,[c,d],e],f}",
            ".                                                     % *<$$/+/>                           % {$$/0=>a,$$/1=>[b,[c,d],e],$$/2=>f}",
            ".                                                     % *<$$/+/+>                          % {b,[c,d],e}",
            ".                                                     % *<$$/+/+/>                         % {$$/1/0=>b,$$/1/1=>[c,d],$$/1/2=>e}",
            ".                                                     % *<$$/+/+/+/>                       % {$$/1/1/0=>c,$$/1/1/1=>d}",
            ".                                                     % *<$$/+/+/+>                        % {c,d}",
            // ".                                                     % *<$$/+/+/#>                        % {c,d}",
            ".                                                     % *<$$/+>                            % {a,[b,[c,d],e],f}",
            ".                                                     % *<$$/+/>                           % [$$/0=>a,$$/1=>[b,[c,d],e],$$/2=>f]>-",
            ".                                                     % *<$$/+/+>                          % {b,[c,d],e}",
            ".                                                     % *<$$/+/+/>                         % [<$$/1/0>=>b,<$$/1/1>=>[c,d],<$$/1/2>=>e]>-",
            ".                                                     % *<$$/RaNDoM>                       % noobj",
            ".                                                     % *<$$/0/0>                          % noobj",
            ".                                                     % *$$/1/0                            % b",
            ".                                                     % *$$/1/1                            % [c,d]",
            ".                                                     % *$$/0/1/1                          % noobj",
            ".                                                     % *$$/1/1/1                          % d",
            ".                                                     % *$$/1/1/+                          % {c,d}",
            ".                                                     % *$$/+                              % {a,[b,[c,d],e],f}",
            ".                                                     % *$$/+/+                            % {b,[c,d],e}",
            ".                                                     % *$$/+/+/+                          % {c,d}",
            ".                                                     % *$$/+/+/+/+                        % noobj",
            ".                                                     % *$$/+/+/+/+/+                      % noobj",
            ".                                                     % *$$/+/+/+/                         % [$$/1/1/0=>c,$$/1/1/1=>d]>-",
            //   ".                                                     % *$$/#                              % {[a,[b,[c,d],e],f],a,[b,[c,d],e],b,[c,d],c,d,e,f}",
            "$$ -> [a=>1,b=>2,c=>3]                                % *<$$>                              % [a=>1,b=>2,c=>3]",
            ".                                                     % *<$$/a>                            % 1",
            ".                                                     % *$$/b                              % 2",
            ".                                                     % *$$/c                              % 3",
            ".                                                     % *$$/c                              % 3",
            ".                                                     % *$$/+                              % {1,2,3}",
            // ".                                                     % *$$/#                              % {1,2,3,[a=>1,b=>2,c=>3]}",
            // ".                                                     % *$$/#/                             % {$$/a=>1,$$/b=>2,$$/c=>3,$$=>[a=>1,b=>2,c=>3]}",
            ".                                                     % *<$$/+>.sum()                      % 6",
            // ".                                                     % *<$$/#>.>>.sum()                   % .",
            ".                                                     % *<$$/+>.sum?int<=int{*}()          % .",
            // ".                                                     % *<$$/#>.>>.sum?int<=int{*}()       % .",
            //  ".                                                     % *</+/+>.sum?int<=int{*}()          % .",
            //         "$$/ -> [a=>1,b=>2,c=>3]                               % *<$$/>                               % [$$/a=>1,$$/b=>2,$$/c=>3]>-",
            ".                                                     % *<$$/x>                            % noobj",
            ".                                                     % *$$/a                              % 1",
            "$$ -> [a=>[b=>2,c=>3],d=>4]                           % *$$/a/b                            % 2",
            //".                                                     % *$$/#                              % [[a=>[b=>2,c=>3],d=>4],[b=>2,c=>3],2,3,4]>-", TODO: make poly.at() consistent with space.read()
            ".                                                     % *<$$/x>                            % noobj",
            //      ".                                                     % *$$/                               % [$$/a=>[b=>2,c=>3],$$/d=>4]>-",
            ".                                                     % *$$/+/                             % [$$/a=>[b=>2,c=>3],$$/d=>4]>-",
            ".                                                     % *$$/a/c                            % 3",
            ".                                                     % *$$/a                              % [b=>2,c=>3]",
            ".                                                     % *$$/d                              % 4",
            ".                                                     % *$$/+                              % [[b=>2,c=>3],4]>-",
            //   ".                                                     % *$$/+/#                            % [[a=>[b=>2,c=>3],d=>4],[b=>2,c=>3],2,3,4]>-",
            //     ".                                                     % *$$/+/+/#                          % [[b=>2,c=>3],2,3,4]>-",
            //         ".                                                     % *$$/a/                             % [$$/a/b=>2,$$/a/c=>3]>-",
            ".                                                     % *$$/a/+                            % {2,3}",
            //          ".                                                     % *$$/a/+/                           % [$$/a/b=>2,$$/a/c=>3]>-",
            //  "[$$/a/b -> 2, $$/a/c -> 3, $$/d -> 4]                 % *$$/+/                             % [$$/a/b=>2,$$/a/c=>3]>-",
            // Additional wildcard pattern tests
            "$$ -> [x=>1,y=>2,z=>3]                                % *<$$/+>                            % {1,2,3}",
            ".                                                     % *<$$/+/>                           % [$$/x=>1,$$/y=>2,$$/z=>3]>-",
            "$$ -> [a=>[b=>1,c=>2],d=>[e=>3,f=>4]]                 % *<$$/+/+>                          % {1,2,3,4}",
            ".                                                     % *<$$/a/+>                          % {1,2}",
            ".                                                     % *<$$/d/+>                          % {3,4}",
            ".                                                     % *<$$/+/b>                          % 1",
            ".                                                     % *<$$/+/e>                          % 3",
            "$$ -> [a=>[b=>[c=>10,d=>20],e=>[f=>30,g=>40]],h=>[i=>[j=>50,k=>60]]]  % *<$$/a/+/+>        % {10,20,30,40}",
            ".                                                     % *<$$/a/b/+>                        % {10,20}",
            ".                                                     % *<$$/+/+/+>                        % {10,20,30,40,50,60}",
            "$$ -> [a=>1,b=>2,c=>3,d=>4,e=>5]                      % *<$$/+>                            % {1,2,3,4,5}",
            ".                                                     % *<$$/b>                            % 2",
            ".                                                     % *<$$/+/>                           % [$$/a=>1,$$/b=>2,$$/c=>3,$$/d=>4,$$/e=>5]>-",
            "1.vid(abc)                                            % *abc                               % 1@abc",
            "1.vid(abc)                                            % *abc.vid(<.>)                    % 1",
            // "[1@a,2@b,3@c]@d.map(10).vid(b)                        % *d                               % [1@a,10@b,3@c]@d",
            // "[1@a,2@b,3@c]@d.map(10@b)                             % *d                               % [1@a,10@b,3@c]@d",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b)                        % *d                               % [1@a,12@b,3@c]@d",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b).to(d)                  % *d                               % 12@d",
            // "[1@a,2@b,3@c]@d                                       % *d._/_.vid(<.>)\\_.vid(<.>)      % [1,2,3]",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b).to(d)                  % *d._/_.vid(<.>)\\_               % [1,2,3]@d",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b).to(d)                  % *d._.vid(<.>)                    % 12",
            // ========================================
            // ADDITIONAL TEST CASES - Added for enhanced coverage
            // ========================================
            // EDGE CASES - Empty and Null-like Values
            "$$ -> []                                              % *<$$>                              % []",
            "$$ -> [a=>[]]                                         % *$$/a                              % []",
            "$$ -> [a=>[b=>[]]]                                    % *$$/a/b                            % []",
            "$$ -> [a=>[],b=>[]]                                   % *<$$/+>                            % {[],[]}",
            // DEEP NESTING - Test limits of nested structures
            "$$ -> [a=>[b=>[c=>[d=>[e=>5]]]]]                      % *$$/a/b/c/d/e                      % 5",
            // ".                                                     % *<$$/+/+/+/+/+>                    % {5}",
            ".                                                     % *$$/a/b/c/d                        % [e=>5]",
            "$$ -> [a=>[b=>[c=>[d=>[e=>[f=>[g=>[h=>8]]]]]]]]       % *$$/a/b/c/d/e/f/g/h                % 8",
            //  ".                                                     % *<$$/+/+/+/+/+/+/+/+>              % {8}",
            // MIXED TYPES IN RECORDS - Different value types at same level
            "$$ -> [int=>42,str=><hello>,bool=>true,real=>3.14]   % *$$/int                            % 42",
            ".                                                     % *$$/str                            % <hello>",
            ".                                                     % *$$/bool                           % true",
            ".                                                     % *$$/real                           % 3.14",
            ".                                                     % *<$$/+>                            % {42,<hello>,true,3.14}",
            // MIXED TYPES IN LISTS
            "$$ -> [42,<hello>,true,3.14,[nested=>1]]             % *$$/0                              % 42",
            ".                                                     % *$$/1                              % <hello>",
            ".                                                     % *$$/2                              % true",
            ".                                                     % *$$/3                              % 3.14",
            ".                                                     % *$$/4                              % [nested=>1]",
            ".                                                     % *$$/4/nested                       % 1",
            ".                                                     % *<$$/+>                            % {42,<hello>,true,3.14,[nested=>1]}",
            // LARGE RECORDS - Many fields at same level
            "$$ -> [a=>1,b=>2,c=>3,d=>4,e=>5,f=>6,g=>7,h=>8,i=>9,j=>10] % *<$$/+>                       % {1,2,3,4,5,6,7,8,9,10}",
            ".                                                     % *$$/e                              % 5",
            ".                                                     % *$$/j                              % 10",
            ".                                                     % *<$$/+>.sum()                      % 55",
            // LARGE LISTS
            "$$ -> [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]        % *$$/0                              % 0",
            ".                                                     % *$$/15                             % 15",
            ".                                                     % *<$$/+>                            % {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}",
            // WILDCARD PATTERNS - More complex combinations
            "$$ -> [a=>[x=>1,y=>2],b=>[x=>3,y=>4],c=>[x=>5,y=>6]] % *<$$/+/x>                          % {1,3,5}",
            ".                                                     % *<$$/+/y>                          % {2,4,6}",
            ".                                                     % *<$$/a/+>                          % {1,2}",
            ".                                                     % *<$$/b/+>                          % {3,4}",
            ".                                                     % *<$$/c/+>                          % {5,6}",
            // SYMMETRIC STRUCTURES - Same structure repeated
            "$$ -> [left=>[a=>1,b=>2],right=>[a=>3,b=>4]]         % *<$$/+/a>                          % {1,3}",
            ".                                                     % *<$$/+/b>                          % {2,4}",
            ".                                                     % *$$/left                           % [a=>1,b=>2]",
            ".                                                     % *$$/right                          % [a=>3,b=>4]",
            // OVERWRITE TESTS - Writing to same path multiple times
            "$$ -> [a=>1]                                          % *$$/a                              % 1",
            "$$ -> [a=>2]                                          % *$$/a                              % 2",
            "$$ -> [a=>3]                                          % *$$/a                              % 3",
            "$$ -> [a=>[b=>1]]                                     % *$$/a/b                            % 1",
            "$$ -> [a=>[b=>2]]                                     % *$$/a/b                            % 2",

            // SPECIAL KEY NAMES - Keys that might cause issues
            "$$ -> [0=>1,1=>2,2=>3]                                % *$$/0                              % 1",
            ".                                                     % *$$/1                              % 2",
            ".                                                     % *$$/2                              % 3",
            // "$$ -> [+=>1,-=>2,*=>3]                                % *$$/+                              % 1",
            // ".                                                     % *$$/-                              % 2",
            // ".                                                     % *$$/*                              % 3",
            // NUMERIC KEYS IN RECORDS (not list indices)
            "$$ -> [100=>a,200=>b,300=>c]                          % *$$/100                            % a",
            ".                                                     % *$$/200                            % b",
            ".                                                     % *$$/300                            % c",
            ".                                                     % *<$$/+>                            % {a,b,c}",
            // BOUNDARY - Very long key names
            "$$ -> [veryLongKeyNameThatGoesOnAndOnAndOn=>42]       % *$$/veryLongKeyNameThatGoesOnAndOnAndOn % 42",
            "$$ -> [a123456789012345678901234567890=>99]           % *$$/a123456789012345678901234567890     % 99",
            // UNICODE IN KEYS
            "$$ -> [你好=>1,世界=>2]                                % *$$/你好                            % 1",
            ".                                                     % *$$/世界                            % 2",
            ".                                                     % *<$$/+>                            % {1,2}",
            // NESTED LISTS
            "$$ -> [[1,2],[3,4],[5,6]]                             % *$$/0                              % [1,2]",
            ".                                                     % *$$/0/0                            % 1",
            ".                                                     % *$$/0/1                            % 2",
            ".                                                     % *$$/1/0                            % 3",
            ".                                                     % *$$/2/1                            % 6",
            ".                                                     % *<$$/+/+>                          % {1,2,3,4,5,6}",
            // RECORDS IN LISTS
            "$$ -> [[a=>1],[b=>2],[c=>3]]                          % *$$/0/a                            % 1",
            ".                                                     % *$$/1/b                            % 2",
            ".                                                     % *$$/2/c                            % 3",
            ".                                                     % *<$$/+/+>                          % {1,2,3}",
            // LISTS IN RECORDS
            "$$ -> [a=>[1,2,3],b=>[4,5,6]]                         % *$$/a/0                            % 1",
            ".                                                     % *$$/a/2                            % 3",
            ".                                                     % *$$/b/0                            % 4",
            ".                                                     % *<$$/+/+>                          % {1,2,3,4,5,6}",
            ".                                                     % *<$$/a/+>                          % {1,2,3}",
            // HETEROGENEOUS NESTING - Mix of lists and records
            "$$ -> [a=>[1,[x=>2],3],b=>[[y=>4],5,6]]               % *$$/a/0                            % 1",
            ".                                                     % *$$/a/1/x                          % 2",
            ".                                                     % *$$/a/2                            % 3",
            ".                                                     % *$$/b/0/y                          % 4",
            ".                                                     % *$$/b/1                            % 5",
            // SINGLE ELEMENT STRUCTURES
            "$$ -> [a=>1]                                          % *<$$>                              % [a=>1]",
            ".                                                     % *$$/a                              % 1",
            ".                                                     % *<$$/+>                            % {1}",
            "$$ -> [1]                                             % *<$$>                              % [1]",
            ".                                                     % *$$/0                              % 1",
            ".                                                     % *<$$/+>                            % {1}",
            // NEGATIVE NUMBERS
            "$$ -> [a=>-1,b=>-42,c=>-999]                          % *$$/a                              % -1",
            ".                                                     % *$$/b                              % -42",
            ".                                                     % *$$/c                              % -999",
            ".                                                     % *<$$/+>                            % {-1,-42,-999}",
            // FLOATING POINT EDGE CASES
            "$$ -> [a=>0.0,b=>-0.0,c=>1.5,d=>-1.5]                 % *$$/a                              % 0.0",
            ".                                                     % *$$/c                              % 1.5",
            ".                                                     % *$$/d                              % -1.5",
            // BOOLEAN COMBINATIONS
            "$$ -> [t=>true,f=>false]                              % *$$/t                              % true",
            ".                                                     % *$$/f                              % false",
            ".                                                     % *<$$/+>                            % {true,false}",
            // WILDCARD WITH TRAILING SLASH - Return as records
            "$$ -> [a=>1,b=>2,c=>3]                                % *<$$/+/>                           % [$$/a=>1,$$/b=>2,$$/c=>3]>-",
            "$$ -> [x=>[y=>1,z=>2]]                                % *<$$/+/+/>                         % [$$/x/y=>1,$$/x/z=>2]>-",
            // CROSS-LEVEL WILDCARDS
            "$$ -> [a=>[b=>[c=>1]],d=>[e=>[f=>2]]]                 % *<$$/+/+/+>                        % {1,2}",
            "$$ -> [a=>[x=>1,y=>2],b=>[x=>3,y=>4]]                 % *<$$/+/x>                          % {1,3}",
            // ========================================
            // REGRESSION TESTS - Bugs found in real usage
            // ========================================
            // Bug: Strings were being converted to URIs (biasTowardsURI issue)
            "$$ -> <hello>                                         % *<$$>                              % <hello>",
            ".                                                     % *$$                                % <hello>",
            "$$ -> <world>                                         % *$$                                % <world>",
            "$$ -> <test123>                                       % *$$                                % <test123>",
            "$$ -> <a/b/c>                                         % *$$                                % <a/b/c>",
            // Bug: Nested records with lists were failing JSON parse
            "$$ -> [a=>[b=>[c=>d,e=>[1,2,3]]]]                     % *<$$>                              % [a=>[b=>[c=>d,e=>[1,2,3]]]]",
            ".                                                     % *$$                                % [a=>[b=>[c=>d,e=>[1,2,3]]]]",
            ".                                                     % *$$/a                              % [b=>[c=>d,e=>[1,2,3]]]",
            ".                                                     % *$$/a/b                            % [c=>d,e=>[1,2,3]]",
            ".                                                     % *$$/a/b/c                          % d",
            ".                                                     % *$$/a/b/e                          % [1,2,3]",
            ".                                                     % *$$/a/b/e/0                        % 1",
            ".                                                     % *$$/a/b/e/1                        % 2",
            ".                                                     % *$$/a/b/e/2                        % 3",
            // Deep nested structure with multiple lists
            "$$ -> [a=>[b=>[c=>[1,2,3,3],d=>2]]]                   % *<$$>                              % [a=>[b=>[c=>[1,2,3,3],d=>2]]]",
            ".                                                     % *$$                                % [a=>[b=>[c=>[1,2,3,3],d=>2]]]",
            ".                                                     % *$$/a                              % [b=>[c=>[1,2,3,3],d=>2]]",
            ".                                                     % *$$/a/b                            % [c=>[1,2,3,3],d=>2]",
            ".                                                     % *$$/a/b/c                          % [1,2,3,3]",
            ".                                                     % *$$/a/b/c/0                        % 1",
            ".                                                     % *$$/a/b/c/1                        % 2",
            ".                                                     % *$$/a/b/c/2                        % 3",
            ".                                                     % *$$/a/b/c/3                        % 3",
            ".                                                     % *$$/a/b/d                          % 2",
            // Strings that look like URIs but should stay strings
            "$$ -> <http://example.com>                            % *$$                                % <http://example.com>",
            "$$ -> <file:///path/to/file>                          % *$$                                % <file:///path/to/file>",
            "$$ -> <user@domain.com>                               % *$$                                % <user@domain.com>",
            "$$ -> <192.168.1.1>                                   % *$$                                % <192.168.1.1>",
            // Strings with special characters that were problematic
            // <hello world> not allowed by MQTT ... force fURI to have no spaces?
            "$$ -> <hello_world>                                   % *$$                                % <hello_world>",
            "$$ -> <a+b>                                           % *$$                                % <a+b>",
            "$$ -> <a-b>                                           % *$$                                % <a-b>",
            "$$ -> <a*b>                                           % *$$                                % <a*b>",
            // Complex nested structures with mixed types
            "$$ -> [users=>[alice=>[age=>30,tags=>[admin,user]],bob=>[age=>25,tags=>[user]]]] % *<$$> % [users=>[alice=>[age=>30,tags=>[admin,user]],bob=>[age=>25,tags=>[user]]]]",
            ".                                                     % *$$/users/alice/age                % 30",
            ".                                                     % *$$/users/alice/tags               % [admin,user]",
            ".                                                     % *$$/users/alice/tags/0             % admin",
            ".                                                     % *$$/users/bob/age                  % 25",
            ".                                                     % *$$/users/bob/tags/0               % user",
            ".                                                     % *<$$/users/+/age>                  % {30,25}",
            ".                                                     % *<$$/users/+/tags/+>               % {admin,user,user}",
            // Records with numeric string keys (not list indices)
            "$$ -> [0=><zero>,1=><one>,2=><two>]                   % *$$/0                              % <zero>",
            ".                                                     % *$$/1                              % <one>",
            ".                                                     % *$$/2                              % <two>",
            ".                                                     % *<$$/+>                            % {<zero>,<one>,<two>}",
            // Empty strings
            "$$ -> <>                                              % *$$                                % <>",
            "$$ -> [a=><>,b=><test>]                               % *$$/a                              % <>",
            ".                                                     % *$$/b                              % <test>",
            // Strings with only special characters
            "$$ -> </>                                             % *$$                                % </>",
            "$$ -> <///>                                           % *$$                                % <///>",
            "$$ -> <...>                                           % *$$                                % <...>",
            "$$ -> <_>                                              % *$$                                % <_>",
            // Very deeply nested lists and records (10 levels!)
            "$$ -> [a=>[b=>[c=>[d=>[e=>[f=>[g=>[h=>[i=>[j=>[1,2,3]]]]]]]]]]] % *$$/a/b/c/d/e/f/g/h/i/j % [1,2,3]",
            ".                                                     % *$$/a/b/c/d/e/f/g/h/i/j/0          % 1",
            ".                                                     % *$$/a/b/c/d/e/f/g/h/i/j/2          % 3",
            // Lists containing records containing lists
            "$$ -> [[a=>[1,2]],[b=>[3,4]],[c=>[5,6]]]              % *$$/0/a                            % [1,2]",
            ".                                                     % *$$/0/a/0                          % 1",
            ".                                                     % *$$/1/b/1                          % 4",
            ".                                                     % *$$/2/c                            % [5,6]",
            ".                                                     % *<$$/+/+/+>                        % {1,2,3,4,5,6}",
            // Records with list values containing records
            "$$ -> [x=>[[a=>1],[b=>2]],y=>[[c=>3],[d=>4]]]         % *$$/x/0/a                          % 1",
            ".                                                     % *$$/x/1/b                          % 2",
            ".                                                     % *$$/y/0/c                          % 3",
            ".                                                     % *$$/y/1/d                          % 4",
            ".                                                     % *<$$/+/+/+>                        % {1,2,3,4}",
            // Alternating nesting patterns
            "$$ -> [a=>[[b=>[c=>[[d=>1]]]]]]                        % *$$/a/0/b/c/0/d                    % 1",
            "$$ -> [[[[a=>1]]]]                                     % *$$/0/0/0/a                        % 1",
            // Overwriting entire structures
            "$$ -> [data=>[v1=>1]]                                 % *$$/data/v1                        % 1",
            "$$ -> [data=>[v1=>1,v2=>2]]                           % *$$/data/v1                        % 1",
            ".                                                     % *$$/data/v2                        % 2",
            ".                                                     % *<$$/data/+>                       % {1,2}",
            "$$ -> [data=>[v1=>1,v2=>2,nested=>[a=>3,b=>4]]]       % *$$/data/nested/a                  % 3",
            ".                                                     % *$$/data/nested/b                  % 4",
            ".                                                     % *<$$/data/+>                       % {1,2,[a=>3,b=>4]}"
    }, delimiter = '%')
    public void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {
        if (!writeExpression.equals(".")) {
            Router.global().write(this.testUri("#"), noobj());
            seedWriteSkipped = false; // reset on every new seed write
        }
        // If the current seed write was rejected, skip all dependent "." rows too
        if (seedWriteSkipped) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "skipping: depends on a seed write that was rejected by this space's root constraint");
            return;
        }
        final Obj writeObj = ObjmtronSerializer.parse(make(writeExpression.equals(".") ? PREVIOUS_LINE.get(0) : writeExpression)).apply();
        // If the write was explicitly rejected by the space (e.g. root type enforcement),
        // allow subclasses to declare this failure expected and skip the test gracefully.
        if (writeObj.isFail() && expectWriteRejection(writeObj)) {
            seedWriteSkipped = true;
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "space rejected write (root type constraint): " + writeObj);
            return;
        }
        if (this.sleepBetweenReads > 0)
            CommonUtil.sleepThread(this.sleepBetweenReads);
        final Obj readObj = ObjmtronSerializer.parse(make(readExpression.equals(".") ? PREVIOUS_LINE.get(1) : readExpression)).apply();
        final Obj resultObj = ObjmtronSerializer.parse(make(expectedExpression.equals(".") ? PREVIOUS_LINE.get(2) : expectedExpression)).apply();
        if (!writeExpression.equals("."))
            PREVIOUS_LINE.set(0, make(writeExpression));
        if (!readExpression.equals("."))
            PREVIOUS_LINE.set(1, make(readExpression));
        if (!expectedExpression.equals("."))
            PREVIOUS_LINE.set(2, make(expectedExpression));
        Graphitty.log(this.space).debug("\n\twrite [%s => %s]\n\tread [%s => %s]\n\texpected [%s => %s]",
                make(writeExpression), writeObj,
                make(readExpression), readObj,
                make(expectedExpression), resultObj);
        try {
            assertEquals(resultObj, readObj);
        } catch (final Exception e) {
            LOG.error(e);
        }
    }


    @ParameterizedTest
    @TestData(oneTime = false, value = {""})
    @CsvSource(value = {
            "[a=>[b=>[c=>d]]]@$$                            % >>=[bb=>cc]               % *$$          % [a=>[b=>[c=>d]],bb=>cc]@$$",
            "[a=>[b=>[c=>d]@$$/a]]@$$/b                     % >>=[bb=>cc]@$$/c          % *$$/b        % [a=>[b=>[c=>d]@$$/a],bb=>cc]@$$/b",
            "[a=>[b=>[c=>d]@$$/a]]@$$/b                     % >>=[bb=>cc]@$$/c          % *$$/c        % [bb=>cc]@$$/c",
            "[a=>[b=>[c=>d]@$$/a]]@$$/b                     % >>=[a=>[bb=>cc]]          % *$$/b        % [a=>[b=>[c=>d]@$$/a,bb=>cc]]@$$/b",
            "[a=>[b=>[c=>d]@$$/a]]@$$/b                     % >>=[a=>[_=> * cc]]        % *$$/b        % <ERROR>",
            "[a=>[b=>c,d=>e]]@$$/a                          % >>=[a=>[_=> * cc]]        % *$$/a        % [a=>[b=>c/cc,d=>e/cc]]@$$/a",
            "[a=>[b=>c,d=>e]]@$$/a                          % >>=[a=>[_=> * cc]]        % *$$/a/a/b    % c/cc",
           // "[a=>[b=>c,d=>e]]@$$/a                          % >>=[a=>[b=>|!*$$/a]]      % *$$/a/a/b  % [a=>[b=>|!*$$/a,d=>e]]@$$/a",
           // "[a=>[b=>c,d=>e]]@$$/a                          % >>=[a=>[b=>|!*$$/a]]      % *$$/a      % [a=>[b=>|!*$$/a,d=>e]]@$$/a",
            "[a=>[b=>c,d=>e]]@$$/a                          % >>=[a=>[b=>|!*$$/a]]      % *$$/a/a/d    % e",
    }, delimiter = '%')
    public void testPolyReadWrite(final String writeExpression, final String mutationExpression, final String readExpression, final String expectedExpression) {
        /*final Obj writeObj = ObjmtronSerializer.parse(make(writeExpression)).apply();
        final Obj mutationObj = ObjmtronSerializer.parse(make(mutationExpression)).apply(writeObj);
        final Obj readObj = ObjmtronSerializer.parse(make(readExpression)).apply(mutationObj);
        final Obj expectedObj = ObjmtronSerializer.parse(make(expectedExpression)).apply();
        this.space.logger().error("\n\twrite [%s => %s]\n\tmutation [%s => %s]\n\tread [%s => %s]\n\texpected [%s => %s]",
                make(writeExpression), writeObj,
                make(mutationExpression), mutationObj,
                make(readExpression), readObj,
                make(expectedExpression), expectedObj);
        try {
            if (readObj.toString().contains("fail::"))
                assertEquals("<ERROR>", expectedExpression);
            else
                assertEquals(expectedObj, readObj);
        } catch (final Exception e) {
            LOG.error(e);
        }*/
    }

    public String make(final String expression) {
        return expression.contains("$$") ? expression.replace("$$", this.baseURI.toString()) : expression;
    }

    // ========================================
    // Reusable Parameterized Tests for All Space Implementations
    // ========================================

    /**
     * Provides a base URI pattern for test data.
     * Subclasses can override to customize the URI pattern for their space.
     * Default: baseURI + "/test/"
     */
    public fURI getTestDataUriPrefix() {
        return f(this.baseURI.toString() + "/test/");
    }

    /**
     * Helper method to create test URIs.
     */
    protected fURI testUri(String suffix) {
        return f(getTestDataUriPrefix() + suffix);
    }

    // ========================================
    // String Corner Cases Tests
    // ========================================

    /**
     * Test string corner cases: empty, special chars, unicode, SQL injection attempts, etc.
     * Subclasses can add more test cases by providing additional data via @MethodSource.
     */
    @TestCategory.Boundary
    @ParameterizedTest(name = "[{index}] String: {0}")
    @CsvSource(value = {
            "empty string              | ''",
            "single space              | ' '",
            "multiple spaces           | '   '",
            "special chars             | 'test!@#$%^&*()'",
            "unicode                   | '你好世界'",
            //   "SQL injection attempt     | 'DROP TABLE users; --'",
            //   "single quote              | 'it''s'",
            "double quotes             | '\"quoted\"'",
            //   "backslashes               | 'path\\to\\file'",
            "very long string          | 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'",
            "mixed case                | 'MiXeD CaSe StRiNg'",
            "punctuation               | 'Hello, World! How are you?'",
            "ampersand                 | 'Tom & Jerry'",
            "percent sign              | '100% complete'",
            "underscore                | 'test_value_123'"
    }, delimiter = '|')
    public void testStringCornerCases(String description, String value) {
        final fURI uri = testUri("string/" + description.replaceAll("\\s+", "_"));

        // Write string value
        this.space.write(uri, str(value));

        // Read back and verify
        final Obj result = this.space.read(uri);
        assertEquals(str(value), result, description);
    }

    // ========================================
    // Integer Boundary Tests
    // ========================================

    /**
     * Test integer boundary values: zero, min/max, negative, various magnitudes.
     */
    @TestCategory.Boundary
    @ParameterizedTest(name = "[{index}] Integer: {0} = {1}")
    @CsvSource(value = {
            "zero                      | 0",
            "one                       | 1",
            "negative one              | -1",
            "small positive            | 42",
            "small negative            | -42",
            "hundred                   | 100",
            "thousand                  | 1000",
            "million                   | 1000000",
            "max int32                 | 2147483647",
            "min int32                 | -2147483648",
            "max int64                 | 9223372036854775807",
            "min int64                 | -9223372036854775808"
    }, delimiter = '|')
    public void testIntegerBoundaries(String description, long value) {
        final fURI uri = testUri("integer/" + description.replaceAll("\\s+", "_"));

        // Write integer value
        this.space.write(uri, jnt(value));

        // Read back and verify
        final Obj result = this.space.read(uri);
        assertEquals(jnt(value), result, description);
    }

    // ========================================
    // Real/Double Boundary Tests
    // ========================================

    /**
     * Test real/double boundary values with tolerance for floating point precision.
     */
    @TestCategory.Boundary
    @ParameterizedTest(name = "[{index}] Real: {0} = {1}")
    @CsvSource(value = {
            "zero                      | 0.0",
            "one                       | 1.0",
            "negative one              | -1.0",
            "small decimal             | 0.5",
            "negative decimal          | -0.5",
            "large decimal             | 12345.67",
            "very small positive       | 0.0001",
            "very small negative       | -0.0001",
            "pi approximation          | 3.14159",
            "e approximation           | 2.71828",
            "large positive            | 999999.99",
            "large negative            | -999999.99"
    }, delimiter = '|')
    public void testRealBoundaries(String description, double value) {
        final fURI uri = testUri("real/" + description.replaceAll("\\s+", "_"));

        // Write real value
        this.space.write(uri, real(value));

        // Read back and verify with tolerance
        final Obj result = this.space.read(uri);
        assertTrue(result.isReal(), "Result should be a real number");
        assertEquals(value, result.asReal().jvm(), 0.01, description);
    }

    // ========================================
    // Boolean Tests
    // ========================================

    /**
     * Test boolean values.
     */
    @TestCategory.Boundary
    @ParameterizedTest(name = "[{index}] Boolean: {0} = {1}")
    @CsvSource(value = {
            "true value                | true",
            "false value               | false",
            "true again                | true",
            "false again               | false"
    }, delimiter = '|')
    public void testBooleanValues(String description, boolean value) {
        final fURI uri = testUri("boolean/" + description.replaceAll("\\s+", "_"));

        // Write boolean value
        this.space.write(uri, bool(value));

        // Read back and verify
        final Obj result = this.space.read(uri);
        assertEquals(bool(value), result, description);
    }

    // ========================================
    // Non-Existent Access Tests
    // ========================================

    /**
     * Test reading non-existent keys returns noobj.
     */
    @TestCategory.Crud
    @ParameterizedTest(name = "[{index}] Non-existent: {0}")
    @CsvSource(value = {
            "simple missing key",
            "missing/nested/key",
            "missing_with_underscores",
            "missing-with-dashes",
            "missing.with.dots"
    })
    public void testNonExistentAccess(String key) {
        final fURI uri = testUri("nonexistent/" + key);

        // Read non-existent key
        final Obj result = this.space.read(uri);
        assertTrue(result.isNoObj(), "Non-existent key should return noobj: " + key);
    }

    // ========================================
    // Sequential Update Tests
    // ========================================

    /**
     * Test sequential updates to the same key.
     */
    @TestCategory.Crud
    @ParameterizedTest(name = "[{index}] Sequential updates: {0} iterations")
    @CsvSource(value = {
            "3",
            "5",
            "10"
    })
    public void testSequentialUpdates(int iterations) {
        final fURI uri = testUri("sequential/updates");

        // Perform sequential updates
        for (int i = 0; i < iterations; i++) {
            this.space.write(uri, jnt(i));

            // Verify each update
            final Obj result = this.space.read(uri);
            assertEquals(jnt(i), result, "Iteration " + i + " should match");
        }

        // Final verification
        final Obj finalResult = this.space.read(uri);
        assertEquals(jnt(iterations - 1), finalResult, "Final value should be last iteration");
    }

    // ========================================
    // CRUD Operations Tests
    // ========================================

    /**
     * Test basic CRUD operations: Create, Read, Update, Delete.
     */
    @TestCategory.Crud
    @ParameterizedTest(name = "[{index}] CRUD: {0}")
    @CsvSource(value = {
            "string value  | test_string  | 'Hello World'",
            "integer value | test_int     | 42",
            "boolean value | test_bool    | true"
    }, delimiter = '|')
    public void testBasicCRUD(String description, String key, String valueStr) {
        final fURI uri = testUri("crud").extend(key);
        final Obj value = parseTestValue(valueStr);

        // CREATE: Write initial value
        this.space.write(uri, value);

        // READ: Verify it exists
        Obj result = this.space.read(uri);
        assertFalse(result.isNoObj(), "Value should exist after create");
        assertEquals(value, result, "Read value should match written value");

        // UPDATE: Write new value
        final Obj updatedValue = str("updated_" + valueStr);
        this.space.write(uri, updatedValue);

        result = this.space.read(uri);
        assertEquals(updatedValue, result, "Updated value should match");

        // DELETE: Write noobj
        this.space.write(uri, noobj());

        result = this.space.read(uri);
        assertEquals(noobj(), result, "Value should not exist after delete");
    }

    /**
     * Helper to parse test values from CSV strings.
     */
    private Obj parseTestValue(String valueStr) {
        if (valueStr.equals("true") || valueStr.equals("false")) {
            return bool(Boolean.parseBoolean(valueStr));
        } else if (valueStr.startsWith("'") && valueStr.endsWith("'")) {
            return str(valueStr.substring(1, valueStr.length() - 1));
        } else {
            try {
                return jnt(Long.parseLong(valueStr));
            } catch (NumberFormatException e) {
                try {
                    return real(Double.parseDouble(valueStr));
                } catch (NumberFormatException e2) {
                    return str(valueStr);
                }
            }
        }
    }

    // ========================================
    // Type Preservation Tests
    // ========================================

    /**
     * Test that types are preserved through write/read cycles.
     */
    @TestCategory.Type
    @ParameterizedTest(name = "[{index}] Type preservation: {0}")
    @MethodSource("provideTypePreservationTestCases")
    public void testTypePreservation(String description, Obj value) {
        final fURI uri = testUri("type_preservation/" + description.replaceAll("\\s+", "_"));

        // Write value
        this.space.write(uri, value);

        // Read back
        final Obj result = this.space.read(uri);

        // Verify value and type
        if (value.isReal())
            assertEquals(value.asReal().jvm(), result.asReal().jvm(), 0.0001, description);
        else
            assertEquals(value, result, description);
        assertEquals(value.getClass(), result.getClass(), "type class should be preserved: " + description);
    }

    protected static Stream<Arguments> provideTypePreservationTestCases() {
        return Stream.of(
                Arguments.of("boolean true", bool(true)),
                Arguments.of("boolean false", bool(false)),
                Arguments.of("integer zero", jnt(0)),
                Arguments.of("integer positive", jnt(42)),
                Arguments.of("integer negative", jnt(-999)),
                Arguments.of("integer max", jnt(Long.MAX_VALUE)),
                Arguments.of("integer min", jnt(Long.MIN_VALUE)),
                Arguments.of("real zero", real(0.0)),
                Arguments.of("real positive", real(3.14159)),
                Arguments.of("real negative", real(-273.15)),
                Arguments.of("string empty", str("")),
                Arguments.of("string simple", str("hello")),
                Arguments.of("string with spaces", str("hello world")),
                Arguments.of("string unicode", str("你好世界")),
                Arguments.of("record simple", rec(uri("name"), str("Alice"), uri("age"), jnt(30))),
                Arguments.of("list simple", lst(jnt(1), jnt(2), jnt(3))),
                Arguments.of("list mixed", lst(str("a"), jnt(1), bool(true)))
        );
    }

    // ========================================
    // Nested Structure Tests
    // ========================================

    /**
     * Test nested records (documents).
     */
    @TestCategory.Nested
    @ParameterizedTest(name = "[{index}] Nested record: depth {0}")
    @CsvSource(value = {
            "1",
            "2",
            "3",
            "5"
    })
    public void testNestedRecords(int depth) {
        final fURI uri = testUri("nested/depth_" + depth);

        // Build nested structure
        Obj nested = str("deepest value");
        for (int i = 0; i < depth; i++) {
            nested = rec(uri("level" + i), nested);
        }

        // Write nested structure
        this.space.write(uri, nested);

        // Read back
        final Obj result = this.space.read(uri);
        assertFalse(result.isNoObj(), "Nested structure should exist");

        // Navigate down the nesting
        Obj current = result;
        for (int i = depth - 1; i >= 0; i--) {
            assertTrue(current.isRec(), "Level " + i + " should be a record");
            current = current.asRec().at(uri("level" + i));
        }

        assertEquals(str("deepest value"), current, "Should reach deepest value");
    }

    // ========================================
    // List/Array Handling Tests
    // ========================================

    /**
     * Test list handling: empty lists, single element, multiple elements.
     */
    @TestCategory.List
    @ParameterizedTest(name = "[{index}] List: {0}")
    @MethodSource("provideListTestCases")
    public void testListHandling(String description, studio.phaseshift.metatron.isa.m.type.Lst listValue, int expectedCount) {
        final fURI uri = testUri("list/" + description.replaceAll("\\s+", "_"));

        // Write list
        this.space.write(uri, listValue);

        // Read back
        final Obj result = this.space.read(uri);
        assertTrue(result.isLst(), "Result should be a list");
        assertEquals(expectedCount, result.asLst().count(), "List should have correct count");
        assertEquals(listValue, result, description);
    }

    protected static Stream<Arguments> provideListTestCases() {
        return Stream.of(
                Arguments.of("empty list", lst(), 0),
                Arguments.of("single element", lst(jnt(1)), 1),
                Arguments.of("multiple integers", lst(jnt(1), jnt(2), jnt(3)), 3),
                Arguments.of("multiple strings", lst(str("a"), str("b"), str("c")), 3),
                Arguments.of("mixed types", lst(jnt(1), str("two"), bool(true)), 3),
                Arguments.of("large list", lst(jnt(1), jnt(2), jnt(3), jnt(4), jnt(5), jnt(6), jnt(7), jnt(8), jnt(9), jnt(10)), 10)
        );
    }

    // ========================================
    // Update Operations Tests
    // ========================================

    /**
     * Test updating from one type to another.
     */
    @TestCategory.Type
    @ParameterizedTest(name = "[{index}] Type change: {0} -> {1}")
    @MethodSource("provideTypeChangeTestCases")
    public void testTypeChanges(String description, Obj initialValue, Obj updatedValue) {
        final fURI uri = testUri("type_change/" + description.replaceAll("\\s+", "_"));

        // Write initial value
        this.space.write(uri, initialValue);
        Obj result = this.space.read(uri);
        assertEquals(initialValue, result, "Initial value should match");

        // Update to different type
        this.space.write(uri, updatedValue);
        result = this.space.read(uri);
        assertEquals(updatedValue, result, "Updated value should match");
    }

    protected static Stream<Arguments> provideTypeChangeTestCases() {
        return Stream.of(
                Arguments.of("int to string", jnt(42), str("forty-two")),
                Arguments.of("string to int", str("123"), jnt(456)),
                Arguments.of("bool to string", bool(true), str("yes")),
                Arguments.of("int to bool", jnt(1), bool(false)),
                Arguments.of("simple to record", str("simple"), rec(uri("key"), str("value"))),
                Arguments.of("record to simple", rec(uri("key"), str("value")), str("simple"))
        );
    }

    // ========================================
    // Concurrent Field Updates Tests
    // ========================================

    /**
     * Test updating multiple fields in a record independently.
     */
    @TestCategory.Crud
    @ParameterizedTest(name = "[{index}] Multi-field update: {0} fields")
    @CsvSource(value = {
            "2",
            "3",
            "5"
    })
    public void testMultiFieldUpdates(int fieldCount) {
        final fURI baseUri = testUri("multifield/record");

        // Write initial record with multiple fields
        final Map<Obj, Obj> fields = new HashMap<>();
        for (int i = 0; i < fieldCount; i++) {
            fields.put(uri("field" + i), str("initial" + i));
        }
        this.space.write(baseUri, rec(fields));

        // Update each field independently
        for (int i = 0; i < fieldCount; i++) {
            final fURI fieldUri = f(baseUri.toString() + "/field" + i);
            this.space.write(fieldUri, str("updated" + i));
        }

        // Read back entire record and verify all fields updated
        final Obj result = this.space.read(baseUri);
        if (result.isRec()) {
            final Rec resultRec = result.asRec();
            for (int i = 0; i < fieldCount; i++) {
                final Obj fieldValue = resultRec.at(uri("field" + i));
                assertEquals(str("updated" + i), fieldValue, "Field " + i + " should be updated");
            }
        }
        // Note: Some spaces may not support field-level updates, so we don't fail if not a record
    }

    // ========================================
    // Special Value Tests
    // ========================================

    /**
     * Test special string values that might cause issues.
     */
    @TestCategory.Special
    @ParameterizedTest(name = "[{index}] Special string: {0}")
    @CsvSource(value = {
            "newline              | 'line1\\nline2'",
            "tab                  | 'col1\\tcol2'",
            "carriage return      | 'line1\\rline2'",
            "null character       | 'before\\0after'",
            // "emoji                | '😀🎉🚀'",
            "rtl text             | 'مرحبا'",
            "mixed scripts        | 'Hello世界مرحبا'"
    }, delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
    public void testSpecialStringValues(String description, String value) {
        final fURI uri = testUri("special_string/" + description.replaceAll("\\s+", "_"));

        // Unescape special characters
        String unescaped = value
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\0", "\0");

        // Remove surrounding quotes if present
        if (unescaped.startsWith("'") && unescaped.endsWith("'")) {
            unescaped = unescaped.substring(1, unescaped.length() - 1);
        }

        // Write and read back
        this.space.write(uri, str(unescaped));
        final Obj result = this.space.read(uri);
        assertEquals(str(unescaped), result, description);
    }

    // ========================================
    // Empty/Null Handling Tests
    // ========================================

    /**
     * Test empty record handling.
     */
    @TestCategory.Type
    @ParameterizedTest(name = "[{index}] Empty record test {0}")
    @CsvSource(value = {
            "1",
            "2",
            "3"
    })
    public void testEmptyRecords(int testNumber) {
        final fURI uri = testUri("empty_record/test_" + testNumber);

        // Write empty record
        final Rec emptyRec = rec();
        this.space.write(uri, emptyRec);

        // Read back
        final Obj result = this.space.read(uri);

        // Should either be empty record or noobj depending on space implementation
        assertTrue(result.isRec() || result.isNoObj(),
                "Empty record should return record or noobj");

        if (result.isRec()) {
            assertTrue(result.asRec().jvm().isEmpty() || result.asRec().jvm().size() == 1,
                    "Empty record should have 0 or 1 fields (may include auto-generated ID)");
        }
    }
}
