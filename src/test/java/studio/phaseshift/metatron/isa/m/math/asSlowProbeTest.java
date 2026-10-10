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

package studio.phaseshift.metatron.isa.m.math;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;

/**
 * A diagnostic probe for the as() slowness reported against mathInstSetTest. It cherry-picks a
 * handful of as() mappings from mathInstSetTest (rather than running its 18-minute suite) and
 * separates the two costs that a @ParameterizedTest row conflates:
 * <ol>
 *   <li>the row's expression — parse + resolve + apply + assert;</li>
 *   <li>the harness's per-row fixture — {@code AbstractInstSetTest.setup()}, which constructs a
 *       fresh mathInstSet and calls its {@code setup()}.</li>
 * </ol>
 * The measurements show the expression costs ~15–25ms while the fixture used to cost ~4.2s, and
 * that a non-as() row (testComplex) paid the same ~4.2s — so the cost was never as() resolution.
 * The fixture has since been corrected to mount the instset before setting it up (see
 * {@link AbstractInstSetTest}); {@link asColdProbeTest} reproduces the old order and its cost.
 * {@link #probeAsScaling()} shows as() resolution growing only mildly with the number of registered
 * as() candidates.
 * <p>
 * Run: {@code ./mvnw -o test -Dtest=asSlowProbeTest -DfailIfNoTests=false} and read
 * {@code target/surefire-reports/*asSlowProbeTest-output.txt} for the {@code ###} lines.
 */
public class asSlowProbeTest extends AbstractInstSetTest {

    public asSlowProbeTest() {
        super(mathInstSet::new);
    }

    private static final String[][] CASES = {
            {"bB::1024.0.as(kB::T)", "kB::1.0"},
            {"millis::1500.0.as(second::T)", "second::1.5"},
            {"cm::100.0.as(meter::T)", "meter::1.0"},
            {"1024.0.as(kB::T)", "kB::1024.0"},
            {"nanos::1000.0.as(micros::T)", "micros::1.0"},
    };

    /**
     * The as() mappings themselves are cheap: the resolve + apply of every cherry-picked mapping is
     * a few tens of milliseconds, and the read of the whole as() contract table (/m/inst/as) is ~1ms.
     */
    @Test
    void probeAsExpressions() {
        System.out.println("### AS candidates=" + asCandidates());
        for (final String[] c : CASES) {
            final long t0 = System.nanoTime();
            final Obj actual = ObjmtronSerializer.eval(c[0]);
            final long evalMs = ms(t0, System.nanoTime());
            System.out.printf("### AS expr [%s] eval=%dms actual=%s expected=%s%n", c[0], evalMs, actual, c[1]);
        }
    }

    /**
     * The per-row lifecycle, with the mount order that {@link AbstractInstSetTest} now uses (matching
     * production): mount the instset, then set it up. The row's own expression stays ~15–25ms and the
     * setup drops to ~0.1s. Compare with {@link asColdProbeTest}, which reproduces the old
     * setup-before-mount order and its ~4.2s cost.
     */
    @Test
    void probeRowLifecycle() {
        for (int i = 0; i < 6; i++) {
            final long a = System.nanoTime();
            final InstSet space = this.spaceSupplier.get();
            Machine.current().memory().addSpace(space);
            final long b = System.nanoTime();
            space.setup();
            final long c = System.nanoTime();
            checkCodeParseApply(LOG, "bB::1024.0.as(kB::T)", "kB::1.0");
            final long d = System.nanoTime();
            space.close();
            final long e = System.nanoTime();
            Machine.current().memory().removeSpace(space.vid());
            final long f = System.nanoTime();
            System.out.printf("### ROW %d new+add=%dms setup=%dms expr=%dms close=%dms remove=%dms%n",
                    i, ms(a, b), ms(b, c), ms(c, d), ms(d, e), ms(e, f));
        }
    }

    /**
     * as() resolution vs. the number of registered as() contracts. Each extra instset adds only a
     * few candidates (math alone has 52), and the cost tracks that count gently — it is not the
     * source of the multi-second rows.
     */
    @Test
    void probeAsScaling() {
        System.out.println("### SCALE candidates=" + asCandidates() + " eval=" + bestEval() + "ms (math only)");
        InstSet.importInstSet(studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID);
        System.out.println("### SCALE candidates=" + asCandidates() + " eval=" + bestEval() + "ms (+cat)");
        InstSet.importInstSet(studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID);
        System.out.println("### SCALE candidates=" + asCandidates() + " eval=" + bestEval() + "ms (+web)");
    }

    private static long asCandidates() {
        return Machine.read(AS_INST_TID).stream().filter(Obj::isObjInst).count();
    }

    private static long bestEval() {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            final long t0 = System.nanoTime();
            ObjmtronSerializer.eval("bB::1024.0.as(kB::T)");
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best / 1_000_000L;
    }

    private static long ms(final long a, final long b) {
        return (b - a) / 1_000_000L;
    }
}
