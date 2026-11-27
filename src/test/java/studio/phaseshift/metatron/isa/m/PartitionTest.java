/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.PARTITION_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PARTITION ROUTES EACH OBJ TO ONE LANE (Marko, 2026-11-27): partition(p, k => c, …) means the branch whose lanes are
 * guarded by the fibres of p, branch(filter(p.eq(k)).c, …), and evaluates p once per obj. These rows are the local half,
 * keys that are labels; a hosted key's lane runs at its place (AbstractDistributedComputingTest).
 */
public class PartitionTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // each obj to the lane its key names: the odds to <1>, the evens to <0>
            "{1,2,3,4}.partition(mod(2), <0> => plus(10), <1> => plus(20))                       % {21,12,23,14}",
            // ... which is what the guarded branch its derivation names answers, every guard evaluated on every obj
            "{1,2,3,4}.branch(filter(mod(2).eq(0)).plus(10), filter(mod(2).eq(1)).plus(20))       % {21,12,23,14}",
            // THE IDENTITY: identical lanes are one lane, each obj counted once (a branch of them counts it twice)
            "{1,2,3,4}.partition(mod(2), <0> => plus(1), <1> => plus(1))                          % {2,3,4,5}",
            "{1,2,3,4}.branch(plus(1), plus(1))                                                   % {{2}2,{2}3,{2}4,{2}5}",
            // THE GRAIN IS THE DOM: one obj at a time, a fold in a lane folds each obj alone
            "{1,2,3,4}.partition(mod(2), <0> => sum(), <1> => sum())                              % {1,2,3,4}",
            // ... the whole lhs bucketed, a lane's fold folds its whole share: the evens 6, the odds 4
            "{1,2,3,4}.partition?<=int{*}(mod(2), <0> => sum(), <1> => sum())                     % {6,4}",
            // ... a page of two bucketed, a fold per page: {1,2} gives <0> 2 and <1> 1, {3,4} gives 4 and 3
            "{1,2,3,4}.partition?<=int{2}(mod(2), <0> => sum(), <1> => sum())                     % {2,1,4,3}",
            "{1,2,3,4}.partition?<=int{2}(mod(2), <0> => sum(), <1> => sum()).sum()               % 10",
            // a lane with an empty share runs nothing: no obj keys <2>, so its count never answers 0
            "{1,3}.partition?<=int{*}(mod(2), <0> => count(), <1> => count(), <2> => count())     % 2",
            // A <#> LANE TAKES THE REST: 3 keys <0>, and 1 and 2 key nothing else named
            "{1,2,3}.partition(mod(3), <0> => id(), <#> => plus(100))                             % {101,102,3}",
            // a key template keys each obj as its spelling says
            "{1,2,3,4}.partition(<${mod(2)}>, <0> => plus(10), <1> => plus(20))                   % {21,12,23,14}",
            // a bulked obj goes whole to one lane, its count riding
            "{{2}1,{3}2}.partition(mod(2), <0> => plus(10), <1> => plus(20))                      % {{2}21,{3}12}",
            // A KEY NAMING TWO LANES NAMES THEIR BRANCH: 1 goes to both <1> lanes, as the guarded branch sends it
            "{1,2}.partition(mod(2), <1> => plus(10), <1> => plus(20), <0> => id())               % {11,21,2}",
            // AN OBJ KEYED NOOBJ IS IN NO FIBRE: is(gt(2)) keys 1 and 2 nothing, so the rest takes them
            "{1,2,3}.partition(is(gt(2)), <3> => plus(10), <#> => plus(100))                      % {101,102,13}",
            "{1,2,3}.partition(is(gt(2)), <3> => plus(10))                                        % <ERROR>",
            // AN OBJ NO LANE TAKES FAILS THE CALL: 2 keys <2>, and no lane is keyed so ...
            "{1,2,3}.partition(mod(3), <0> => id(), <1> => id())                                  % <ERROR>",
            // ... and the same call with a <2> lane answers
            "{1,2,3}.partition(mod(3), <0> => id(), <1> => id(), <2> => id())                     % {1,2,3}",
            // a key that is neither a uri nor an int is refused ...
            "{1,2}.partition(gt(1), <true> => id(), <false> => id())                              % <ERROR>",
            // ... and the same bool spelled as a uri keys
            "{1,2}.partition(<${gt(1)}>, <true> => plus(10), <false> => plus(20))                 % {21,12}",
            // a partition of nothing is nothing
            "noobj.partition(mod(2), <0> => plus(10), <1> => plus(20))                            % noobj",
    }, delimiter = '%')
    void testPartition(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A STORED PARTITION FUNCTION ({@code *xyz}) keys each obj: what the read answers is an instruction, which is applied
     * to the obj in turn.
     */
    @Test
    void testAStoredPartitionFunctionKeysEachObj() {
        try {
            // | blocks the instruction, so the write stores mod(2) itself, not what it answers applied to nothing
            Machine.evaluate("|mod(2).to(/partition/pf)");
            checkCodeParseApply(LOG, "{1,2,3,4}.partition(*/partition/pf, <0> => plus(10), <1> => plus(20))", "{21,12,23,14}");
        } finally {
            Machine.evaluate("noobj.to(/partition/pf)");
        }
    }

    /**
     * THE PARTITION FUNCTION RUNS ONCE PER OBJ: four objs, four calls, whatever the number of lanes. The guarded branch
     * its derivation names would run one guard per lane per obj.
     */
    @Test
    void testThePartitionFunctionRunsOncePerObj() {
        final AtomicInteger calls = new AtomicInteger();
        final Obj counting = MInst.instLambda((lhs, inst) -> {
            calls.incrementAndGet();
            return uri(f(String.valueOf(lhs.intValue().longValue() % 3)));
        });
        final Obj answer = start_(objs(jnt(1), jnt(2), jnt(3), jnt(4)))
                .addInsts(java.util.List.of(instB(PARTITION_INST_TID, lst(counting,
                        rel(uri(f("0")), ObjmtronSerializer.parse("plus(10)")),
                        rel(uri(f("1")), ObjmtronSerializer.parse("plus(20)")),
                        rel(uri(f("2")), ObjmtronSerializer.parse("plus(30)"))))))
                .apply(noobj());
        assertEquals(ObjmtronSerializer.parse("{21,32,13,24}"), answer);
        assertEquals(4, calls.get(), "one call per obj, not one per lane per obj");
    }
}
