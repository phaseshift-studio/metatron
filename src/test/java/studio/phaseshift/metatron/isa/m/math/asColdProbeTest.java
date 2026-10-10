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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;

/**
 * Isolates what made {@code mathInstSet.setup()} cost ~4.2s instead of ~0.1s: the order of
 * {@code addSpace} and {@code setup}. {@code mathInstSet.setup()} doc-wraps every definition it
 * declares, and {@code QCollection.internalDocWrap} resolves each id's owning space with
 * {@code Machine.current().memory().getSpaceFor(id)}. When the instset is NOT yet mounted, those
 * ~78 documentation writes fall through to the enclosing space's doc-query space, where each deep
 * write costs tens of milliseconds. Mounting first keeps them local: measured 4934ms vs 95ms
 * (52x). Production already mounts first — {@code InstSet.INSTSET_TYPE}'s constructor and
 * {@code importInstSetStream} both {@code addSpace} before {@code setup()} — and
 * {@link studio.phaseshift.metatron.isa.AbstractInstSetTest} was corrected to do the same.
 * <p>
 * This class deliberately extends {@link AbstractMetatronTest} (not AbstractInstSetTest), so no
 * instset fixture runs before the test and every setup here is under the probe's control.
 * <p>
 * Run: {@code ./mvnw -o test -Dtest=asColdProbeTest -DfailIfNoTests=false}.
 */
public class asColdProbeTest extends AbstractMetatronTest {

    @Test
    void probeColdSetup() {
        // establish a mounted math space
        final InstSet a = new mathInstSet();
        a.setup();
        Machine.current().memory().addSpace(a);
        System.out.println("### FIX a set up + mounted");

        // the AbstractInstSetTest pattern: close (unmount), then setup() BEFORE mounting
        a.close();
        Machine.current().memory().removeSpace(a.vid());
        final InstSet b = new mathInstSet();
        final long b0 = System.nanoTime();
        b.setup();
        final long b1 = System.nanoTime();
        Machine.current().memory().addSpace(b);
        System.out.println("### FIX b setup(before mount, the fixture pattern)=" + ms(b0, b1) + "ms");

        // the proposed fix: mount FIRST, then setup()
        b.close();
        Machine.current().memory().removeSpace(b.vid());
        final InstSet c = new mathInstSet();
        Machine.current().memory().addSpace(c);
        final long c0 = System.nanoTime();
        c.setup();
        final long c1 = System.nanoTime();
        System.out.println("### FIX c setup(after mount)=" + ms(c0, c1) + "ms");

        // once more, to be sure
        c.close();
        Machine.current().memory().removeSpace(c.vid());
        final InstSet d = new mathInstSet();
        Machine.current().memory().addSpace(d);
        final long d0 = System.nanoTime();
        d.setup();
        final long d1 = System.nanoTime();
        System.out.println("### FIX d setup(after mount)=" + ms(d0, d1) + "ms");
    }

    @Test
    void probeDocSpace() {
        report("no math mounted", mathInstSet.MATH_KBYTE_TID);
        final InstSet a = new mathInstSet();
        Machine.current().memory().addSpace(a);
        a.setup();
        report("math mounted", mathInstSet.MATH_KBYTE_TID);
        a.close();
        Machine.current().memory().removeSpace(a.vid());
        report("math unmounted again", mathInstSet.MATH_KBYTE_TID);
    }

    private static void report(final String label, final fURI id) {
        final Space space = Machine.current().memory().getSpaceFor(id);
        final boolean docq = null != space && space.qs().jvm().stream()
                .anyMatch(q -> q.tid().basePath().equals(QCollection.DOCQ_TID));
        System.out.printf("### SPACE [%s] id=%s -> space=%s vid=%s pattern=%s docq=%s%n",
                label, id, null == space ? "null" : space.getClass().getSimpleName(),
                null == space ? "-" : space.vid(), null == space ? "-" : space.pattern(), docq);
    }

    private static long ms(final long a, final long b) {
        return (b - a) / 1_000_000L;
    }
}
