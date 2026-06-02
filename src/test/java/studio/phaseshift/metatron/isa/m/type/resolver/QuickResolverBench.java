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

package studio.phaseshift.metatron.isa.m.type.resolver;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;

import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * Throwaway: one-shot resolver benchmark. Delete after running.
 * Usage: mvn test -Dtest=QuickResolverBench -pl .
 */
@Disabled
public class QuickResolverBench extends AbstractMetatronTest {

    private static final int WARMUP = 500;
    private static final int ITERS = 5000;

    private record Result(String name, double avgUs, long minNs, long maxNs, int n) {
        public String toString() {
            return String.format("  %-30s avg=%.2f us  min=%.2f us  max=%.2f us  (n=%d)", name, avgUs, minNs/1000.0, maxNs/1000.0, n);
        }
    }

    private Result bench(String name, Runnable op) {
        for (int i = 0; i < WARMUP; i++) op.run();
        List<Long> times = new ArrayList<>(ITERS);
        for (int i = 0; i < ITERS; i++) {
            long start = System.nanoTime();
            op.run();
            times.add(System.nanoTime() - start);
        }
        LongSummaryStatistics s = times.stream().mapToLong(Long::longValue).summaryStatistics();
        return new Result(name, s.getAverage() / 1000.0, s.getMin(), s.getMax(), ITERS);
    }

    @Test
    @DisplayName("Quick resolver benchmark")
    public void runAll() {
        List<Result> results = new ArrayList<>();

        // --- single-candidate paths (benefits from short-circuit) ---
        results.add(bench("1.plus(2)", () -> {
            instB(mInstSet.PLUS_INST_TID, lst(jnt(2))).resolve(jnt(1));
        }));
        results.add(bench("1.0.plus(2.0)", () -> {
            instB(mInstSet.PLUS_INST_TID, lst(real(2.0))).resolve(real(1.0));
        }));
        results.add(bench("1.mult(3)", () -> {
            instB(mInstSet.MULT_INST_TID, lst(jnt(3))).resolve(jnt(1));
        }));
        results.add(bench("1.neg()", () -> {
            instB(mInstSet.NEG_INST_TID, lst()).resolve(jnt(1));
        }));
        results.add(bench("1.zero()", () -> {
            instB(mInstSet.ZERO_INST_TID, lst()).resolve(jnt(1));
        }));
        results.add(bench("\"hi\".plus(\" there\")", () -> {
            instB(mInstSet.PLUS_INST_TID, lst(str(" there"))).resolve(str("hi"));
        }));
        results.add(bench("1.as(real::T)", () -> {
            instB(mInstSet.AS_INST_TID, lst(REAL_TYPE)).resolve(jnt(1));
        }));
        results.add(bench("42.as(str::T)", () -> {
            instB(mInstSet.AS_INST_TID, lst(STR_TYPE)).resolve(jnt(42));
        }));
        results.add(bench("5.gt(3)", () -> {
            instB(mInstSet.GT_INST_TID, lst(jnt(3))).resolve(jnt(5));
        }));
        results.add(bench("5.eq(3)", () -> {
            instB(mInstSet.EQ_INST_TID, lst(jnt(3))).resolve(jnt(5));
        }));

        // --- already-resolved (hasf early exit - not affected by our change) ---
        results.add(bench("already-resolved", () -> {
            Inst r = instB(mInstSet.PLUS_INST_TID, lst(jnt(2))).resolve(jnt(1));
            r.resolve(jnt(1));
        }));

        // --- full apply (resolve + execute) ---
        results.add(bench("1.plus(2) [apply]", () -> {
            instB(mInstSet.PLUS_INST_TID, lst(jnt(2))).apply(jnt(1));
        }));

        System.out.println("\n========================================");
        System.out.println("RESOLVER: " + InstResolver.get().getClass().getSimpleName());
        System.out.println("========================================");
        results.forEach(r -> System.out.println(r.toString()));
        System.out.println("========================================");
    }
}
