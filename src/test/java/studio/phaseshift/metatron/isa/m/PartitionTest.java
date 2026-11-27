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
 * PARTITION ROUTES EACH OBJ DOWN THE FIRST ROUTE ITS KEY ADMITS (Marko, 2026-11-27): the walk join of the rec algebra,
 * [# => p(x)] * [k => v, …], with the first walk kept, and p evaluated once per obj. These rows are the local half; a
 * walk that passes a place runs its code there (AbstractDistributedComputingTest).
 */
public class PartitionTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // EACH OBJ DOWN THE FIRST ROUTE ITS KEY ADMITS: ? on a value admits that value (Marko, 2026-11-27)
            "{1,2,3,4}.partition(mod(2), ?0 => plus(10), ?1 => plus(20))                          % {12,14,21,23}",
            // VARARGS OF RELATIONS (Marko, 2026-11-27): the selector keys the table its routes form, f => {k => v, …} ...
            "{1,2,3,4}.partition(mod(2) => {?0 => plus(10), ?1 => plus(20)})                      % {12,14,21,23}",
            // ... which the written paths form themselves, the rec's + factoring their common selector
            "{1,2,3,4}.partition(mod(2) => ?0 => plus(10), mod(2) => ?1 => plus(20))              % {12,14,21,23}",
            // THE KEYS STAY RAW: a partition gathers its lhs and applies each key to each member itself, so a key template
            // spells each member's key, not the gathered lhs's ...
            "{1,2,3,4}.partition(<${mod(2)}> => {<0> => plus(10), <1> => plus(20)})                % {12,14,21,23}",
            // ... where a call that is not blocking has its keys computed against its lhs (Marko's inst: ?>0 keys 2 by 2)
            "2.inst(?>0 => a => x){*args>>*lhs}                                                   % a=>x",
            // THE KEY IS WHAT THE SELECTOR ANSWERS, applied to each obj once as written (Marko, 2026-11-27): 64 keys by 0 ...
            "64.partition(mod(2) => {0 => plus(1), _ => plus(2)})                                  % 65",
            "{64,65}.partition(mod(2) => {?0 => plus(1), ?1 => plus(2)})                           % {65,67}",
            // ... and a blocked key is a block, never unwrapped or applied again: 64.|mod(2) is the code mod(2), its key
            "64.partition(|mod(2) => {0 => plus(1), _ => plus(2)})                                 % 66",
            // A GUARD answers its input or nothing, so it admits a member or not, and a guard that admits opens its table
            "{1,2,3,4}.partition(?>2 => plus(10), _ => plus(20))                                   % {13,14,21,22}",
            "{1,2,3}.partition(?>1 => {a => plus(10), b => plus(20)}, _ => plus(100))              % {101,12,13}",
            // ... so _ as a key opens its whole table, its first route taking every obj, where the positional _ selects
            "{1,2,3,4}.partition(_ => {?>2 => plus(10), _ => plus(20)})                            % {11,12,13,14}",
            "{1,2,3,4}.partition(_, ?>2 => plus(10), _ => plus(20))                                % {13,14,21,22}",
            // ... and a member no guard admits is a member no route admits, which fails the call
            "{1,2,3}.partition(?>2 => plus(10))                                                    % <ERROR>",
            // a value admits its equal, and a type its instances, each obj tested (5 and 6 both int)
            "{1,2,7}.partition(1 => plus(10), 2 => plus(20), _ => plus(100))                       % {11,22,107}",
            "5.partition(int::T => map(<int>), str::T => map(<str>))                               % int",
            "{5,6,true}.partition(int::T => map(<int>), bool::T => map(<bool>))                    % {{2}int,bool}",
            // ... and a value route admits its equal too, as ? on it does
            "{1,2,3,4}.partition(mod(2), 0 => plus(10), 1 => plus(20))                            % {12,14,21,23}",
            // ... which is what the guarded branch its derivation names answers, every guard evaluated on every obj
            "{1,2,3,4}.branch(filter(mod(2).eq(0)).plus(10), filter(mod(2).eq(1)).plus(20))       % {21,12,23,14}",
            // THE IDENTITY: identical lanes are one lane, each obj counted once (a branch of them counts it twice)
            "{1,2,3,4}.partition(mod(2), ?0 => plus(1), ?1 => plus(1))                            % {2,3,4,5}",
            "{1,2,3,4}.branch(plus(1), plus(1))                                                   % {{2}2,{2}3,{2}4,{2}5}",
            // A PARTITION IS A BARRIER INSTRUCTION (Marko, 2026-11-27): its dom gathers the whole lhs by default, so a
            // route's fold folds its whole share, the evens 6 and the odds 4 ...
            "{1,2,3,4}.partition(mod(2), ?0 => sum(), ?1 => sum())                                % {6,4}",
            "{1,2,3,4}.partition?<=int{*}(mod(2), ?0 => sum(), ?1 => sum())                       % {6,4}",
            // ... and a smaller maximum is a lazy barrier: one obj at a time, a fold folds each obj alone
            "{1,2,3,4}.partition?<=int{?}(mod(2), ?0 => sum(), ?1 => sum())                       % {1,2,3,4}",
            // ... a page of two, a fold per page: {1,2} gives ?0 2 and ?1 1, {3,4} gives 4 and 3
            "{1,2,3,4}.partition?<=int{2}(mod(2), ?0 => sum(), ?1 => sum())                       % {2,1,4,3}",
            "{1,2,3,4}.partition?<=int{2}(mod(2), ?0 => sum(), ?1 => sum()).sum()                 % 10",
            // ... and the three grains told apart by how many folds answer: one, one per page, one per obj
            "{1,3,5,7}.partition(mod(2), ?1 => sum()).count()                                     % 1",
            "{1,3,5,7}.partition?<=int{2}(mod(2), ?1 => sum()).count()                            % 2",
            "{1,3,5,7}.partition?<=int{?}(mod(2), ?1 => sum()).count()                            % 4",
            // a route whose share is empty runs nothing: no obj keys 2, so its count never answers 0
            "{1,3}.partition(mod(2), ?0 => count(), ?1 => count(), ?2 => count())                 % 2",
            // GUARDS: the selector _ keys each obj by itself, a route's key tests it, and _ written last is the else
            "{1,2,3,4}.partition(_, ?>2 => plus(10), _ => plus(20))                                % {13,14,21,22}",
            "{1,2,7}.partition(_, ?1 => plus(10), ?2 => plus(20), _ => plus(100))                  % {11,22,107}",
            // THE FIRST ROUTE TAKES IT: overlapping guards route each obj once, and a key naming two routes the first
            "{1,2,3}.partition(_, ?>0 => plus(10), ?>1 => plus(20))                                % {11,12,13}",
            "{1,2}.partition(mod(2), ?1 => plus(10), ?1 => plus(20), ?0 => id())                  % {11,2}",
            // ? on a type admits its instances
            "5.partition(_, ?int::T => map(<int>), ?str::T => map(<str>))                          % int",
            "start('a').partition(_, ?int::T => map(<int>), ?str::T => map(<str>))                 % str",
            // a key template keys each obj as its spelling says
            "{1,2,3,4}.partition(<${mod(2)}>, <0> => plus(10), <1> => plus(20))                    % {12,14,21,23}",
            // a bulked obj goes whole to one route, its count riding
            "{{2}1,{3}2}.partition(mod(2), ?0 => plus(10), ?1 => plus(20))                        % {{3}12,{2}21}",
            // A KEY OF NOOBJ FORMS NO RELATION: is(gt(1)) keys 1 nothing, so 1 is dropped ...
            "{1,2,3}.partition(is(gt(1)), ?2 => id(), ?3 => id())                                  % {2,3}",
            // ... as a typed zero is ({0}1 is an int's zero) ...
            "{1,2}.partition(map({0}1), _ => plus(10))                                             % noobj",
            // A KEY NO ROUTE ADMITS FAILS THE CALL: 2 keys 2, and no route admits it ...
            "{1,2,3}.partition(mod(3), ?0 => id(), ?1 => id())                                     % <ERROR>",
            // ... and the same call with a ?2 route answers
            "{1,2,3}.partition(mod(3), ?0 => id(), ?1 => id(), ?2 => id())                         % {3,1,2}",
            // a bool keys as any value does
            "{1,2}.partition(gt(1), ?true => plus(10), ?false => plus(20))                         % {12,21}",
            "{1,2,3}.partition(gt(1) => {true => plus(10), false => plus(20)})                     % {21,12,13}",
            // A GUARD ADMITS FALSE, as it answers its input, where a predicate's false refuses
            "{true,false}.partition(?true => map(<yes>), _ => map(<no>))                           % {yes,no}",
            "{1,2,3}.partition(_, gt(1) => plus(10), _ => plus(20))                                % {21,12,13}",
            // a partition of nothing is nothing
            "noobj.partition(mod(2), ?0 => plus(10), ?1 => plus(20))                               % noobj",
    }, delimiter = '%')
    void testPartition(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A READ KEYS BY WHAT IT READS, as is (Marko, 2026-11-27: "don't unwrap it, don't double apply it"): a stored value
     * keys every obj by itself, and a stored instruction keys every obj by the instruction, not by what it answers.
     */
    @Test
    void testAReadKeysByWhatItReads() {
        try {
            Machine.evaluate("0.to(/partition/key)");
            checkCodeParseApply(LOG, "{1,2,3,4}.partition(*/partition/key, ?0 => plus(10), _ => plus(20))", "{11,12,13,14}");
            // | blocks the instruction, so the write stores mod(2) itself, and its read, the code, is no 0
            Machine.evaluate("|mod(2).to(/partition/key)");
            checkCodeParseApply(LOG, "{1,2,3,4}.partition(*/partition/key, 0 => plus(10), _ => plus(20))", "{21,22,23,24}");
        } finally {
            Machine.evaluate("noobj.to(/partition/key)");
        }
    }

    /**
     * THE PARTITION FUNCTION RUNS ONCE PER OBJ: five objs, five calls, whatever the number of lanes, the obj it keys
     * nothing (5) included, which is dropped. The guarded branch its derivation names would run one guard per lane per
     * obj.
     */
    @Test
    void testThePartitionFunctionRunsOncePerObj() {
        final AtomicInteger calls = new AtomicInteger();
        final Obj counting = MInst.instLambda((lhs, inst) -> {
            calls.incrementAndGet();
            return 5L == lhs.intValue().longValue() ? noobj() : uri(f(String.valueOf(lhs.intValue().longValue() % 3)));
        });
        final Obj answer = start_(objs(jnt(1), jnt(2), jnt(3), jnt(4), jnt(5)))
                .addInsts(java.util.List.of(instB(PARTITION_INST_TID, lst(counting,
                        rel(uri(f("0")), ObjmtronSerializer.parse("plus(10)")),
                        rel(uri(f("1")), ObjmtronSerializer.parse("plus(20)")),
                        rel(uri(f("2")), ObjmtronSerializer.parse("plus(30)"))))))
                .apply(noobj());
        checkEquality(LOG, ObjmtronSerializer.parse("{13,21,24,32}"), answer, true);
        assertEquals(5, calls.get(), "one call per obj, not one per lane per obj, nor a second for the obj it drops");
    }
}
