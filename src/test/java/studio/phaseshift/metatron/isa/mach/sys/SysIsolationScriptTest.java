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

package studio.phaseshift.metatron.isa.mach.sys;

import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * SYS isolation as a stateful script — the fourth of the machine-isolation suite, beside the instset, memory and network scripts, and the one that pins the {@code ~/sys} directory the machine now owns.
 */
public class SysIsolationScriptTest extends AbstractMetatronTest {

    /**
     * where a probe directive leaves its answer, so a Java-side fact becomes a mtron observation.
     */
    private static final fURI PROBE = f("/sys/sys/probe");

    /**
     * One directive, asking the one question mtron cannot: whether an address is answered by a SPACE at all.
     */
    @Override
    protected Map<String, Directive> directives() {
        return Map.of(
                // an uncovered address drops its writes silently, so a read there answers noobj forever
                "COVERED", args -> this.probe("covered", jnt(null == Machine.current().memory().findSpace(f(args[0])) ? 0 : 1)));
    }

    private void probe(final String name, final Obj value) {
        Machine.write(PROBE.extend(name), value);
    }

    /**
     * A machine's {@code ~/sys} is its own while {@code /sys} is the process's, and the two coincide only at the root.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            // the root machine IS the process, so the two spellings name one address
            "[STATE] ~/sys/iso -> 1",
            "*~/sys/iso % */sys/iso % true",
            // a frame's ~/sys is a different address: the same key written there is not the root's
            "[STATE] *~.push(xyz)",
            "[STATE] ~/sys/iso -> 2",
            "*~/sys/iso % 2 % true",
            "*/xyz/sys/iso % 2 % true                  [-- the ~ spelling and the absolute one are one address --]",
            "*/sys/iso % 1 % true                      [-- and the PROCESS's copy is untouched --]",
            // no leak up, and the frame's own went with the frame
            "[STATE] *~.pop(_)",
            "*~/sys/iso % 1 % true",
            "*/xyz/sys/iso % noobj % true"
    })
    void testSysIsMachineLocalWhileSysIsTheProcess() {
    }

    /**
     * The isolation quartet on the sys directory — inherit, own, no leak up.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            // INHERIT: every frame is born with a log sink of its own, at its own address, seeded from the enclosing machine
            "*~/sys/log % noobj % false",
            "*~/sys/log/level % noobj % false",
            "[STATE] *~.push(xyz)",
            "*~/sys/log % noobj % false",
            "*~/sys/log % */xyz/sys/log % true        [-- the frame's sink is ITS address, spelled both ways --]",
            "*~/sys/log/level.count() % */sys/log/level.count() % true [-- seeded from the enclosing machine --]",
            // OWN: a key written into a frame's sys stays there and is NOT inherited by a frame inside it
            "[STATE] ~/sys/iso -> 1",
            "[STATE] *~.push(inner)",
            "*~/sys/iso % noobj % true                 [-- a different address is not an inheritance --]",
            "[STATE] *~.pop(_)",
            "*~/sys/iso % 1 % true",
            "[STATE] *~.pop(_)",
            // NO LEAK UP: the frame's write is gone from the root's view
            "*~/sys/iso % noobj % true",
            "*/xyz/sys/iso % noobj % true"
    })
    void testSysIsolationInheritOwnNoLeakUp() {
    }

    /**
     * A sibling frame never sees a write made in another, whether it was reached by move or by push.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(xyz1)",
            "[STATE] ~/sys/iso -> 1",
            "*~/sys/iso % 1 % true",
            // move, not pop: xyz1 stays alive and xyz2 never sees its write
            "[STATE] move(/.)",
            "[STATE] *~.push(xyz2)",
            "*~/sys/iso % noobj % true",
            "[STATE] move(/xyz1)",
            "*~/sys/iso % 1 % true                     [-- each frame reads only its own --]",
            "[STATE] move(/xyz2)",
            "*~/sys/iso % noobj % true",
            // and the root, which wrote neither, sees neither
            "[STATE] move(/.)",
            "*~/sys/iso % noobj % true"
    })
    void testSysWriteIsolationNoLeakAcross() {
    }

    /**
     * The log sink is per machine: one with no sink answers noobj, and GraphittyObjLogger then logs EVERY level.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            // bootstrap seeds one, so no frame is ever in that state
            "*~/sys/log % noobj % false",
            "*~/sys/log/level.count().gt(0) % true",
            "[STATE] *~.push(xyz)",
            "*~/sys/log % noobj % false",
            // shared until written: one mtron obj, many Java faces
            "*~/sys/log/level.count() % */sys/log/level.count() % true",
            // an update through @ anchors the write-back to THIS machine's address, so the obj bifurcates here
            "[STATE] @~/sys/log >>= +[iso=>1]",
            "*~/sys/log/iso % 1 % true",
            "*~/sys/log % */sys/log % false            [-- the frame's sink now differs in CONTENT --]",
            "[STATE] *~.pop(_)",
            "*~/sys/log/iso % noobj % true             [-- the parent's sink never took the frame's key --]",
            "*/xyz/sys/log/iso % noobj % true"
    })
    void testTheLogSinkIsPerMachineAndBifurcatesOnWrite() {
    }

    /**
     * The machine's sys addresses are answered by a space, which is what keeps a write from vanishing silently.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            // the probe itself proves the point: written under /sys, read back
            "[COVERED /sys/sys/probe]",
            "*/sys/sys/probe/covered % 1 % true",
            "[COVERED /sys/log]",
            "*/sys/sys/probe/covered % 1 % true",
            "[COVERED /sys/log/level]",
            "*/sys/sys/probe/covered % 1 % true",
            // the fail stack's home — a narrowed infra pattern de-homes this and the write is dropped without a word
            "[COVERED /sys/fail/_]",
            "*/sys/sys/probe/covered % 1 % true"
    })
    void testSysAddressesAreAnsweredByASpace() {
    }

    /**
     * A fail lands in the machine's sys directory, and the address it used to land at stays empty.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] 1.plus(b)                          [-- produce and stack a fail --]",
            "*~/sys/fail/+.count().gt(0) % true",
            // the counter REPLACED the `_`, so the stack holds numbered addresses — not the `_?incrq` pattern
            "*~/sys/fail/1 % noobj % false",
            // the old absolute path is orphaned — nothing writes there now
            "*~/fail/+.count() % 0 % true",
            // and the stack is per machine: a frame that has failed nothing has an empty one
            "[STATE] *~.push(xyz)",
            "*~/sys/fail/+ % noobj % true",
            "[STATE] *~.pop(_)"
    })
    void testFailsLandInTheMachineSysDirectory() {
    }
}
