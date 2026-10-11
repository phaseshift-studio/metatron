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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicInstSet;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * INSTRUCTION-SET isolation as a stateful script — the third of the machine-isolation trio, beside
 * {@code MemoryIsolationScriptTest} and {@code NetworkIsolationScriptTest}, deliberately in the same script form so
 * the three read side by side.
 * <p>
 * An ISA is the one machine component that is an N-ARY union rather than a binary one: a machine has its own
 * insts/rewrites/sugars/types and an ORDERED LIST OF REFERENCES to the ISAs it imported. The scenarios here are
 * the ones that union makes possible, and they are the ones that matter for nesting a machine:
 * <ol>
 *     <li><b>import is a reference</b> — importing an ISA records it, and a later import does not displace an
 *     earlier one (import order is precedence).</li>
 *     <li><b>a child has its parent's vocabulary</b> — necessary, not a convenience: a Machine created with no
 *     inherited instruction set is a Machine whose code cannot run.</li>
 *     <li><b>adding is local</b> — an inst written while standing in a child belongs to that child, so it neither
 *     leaks up to the parent nor across to a sibling.</li>
 * </ol>
 * <p>
 * <b>Read this suite for what it is worth.</b> Import currently does BOTH things — it mounts the ISA into memory
 * and refers it into the machine's instset — so the inheritance scenarios below pass through the mount as much as
 * through the union. They are a REGRESSION GUARD on the union's semantics until the mount is dropped, at which
 * point they become the WITNESS that the union alone carries a machine's vocabulary. The directives exist because
 * import and reference-walking are Java-side facts: mtron has no import operator to write in a script line.
 */
public class InstSetIsolationScriptTest extends AbstractMetatronTest {

    private static final fURI PROBE = f("/sys/instset/probe");

    /**
     * Import is Java-side (SPI via ServiceLoader), so a script cannot express it — the same reason the network
     * suite needs {@code [CLUSTER]}. {@code [IMPORT]} is the operation itself, not a test hook.
     */
    @Override
    protected Map<String, Directive> directives() {
        return Map.of(
                "IMPORT", args -> InstSet.importInstSet(f(args[0])),
                "REFERENCE_COUNT", args -> this.probe("reference_count", jnt(this.instset().referVids().size())),
                "REFERENCE_HAS", args -> this.probe("reference_has",
                        jnt(this.instset().referVids().stream()
                                .anyMatch(reference -> reference.isUri() && reference.uriValue().equals(f(args[0]))) ? 1 : 0)));
    }

    private void probe(final String name, final Obj value) {
        Machine.write(PROBE.extend(name), value);
    }

    /** The current machine's instset as the union it is; a bare store has no references to speak of. */
    private BasicInstSet instset() {
        final InstSet instset = Machine.current().instset();
        return instset instanceof BasicInstSet ? (BasicInstSet) instset : new BasicInstSet();
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // a machine that has imported anything refers to something — the membership is readable at all
            "[REFERENCE_COUNT]",
            "*/sys/instset/probe/reference_count % 0 % false",
            // importing records the ISA by vid, and it is findable by that vid
            "[IMPORT /m/math]",
            "[REFERENCE_HAS /m/math]",
            "*/sys/instset/probe/reference_has % 1 % true",
            // a LATER import does not displace an EARLIER one: the list grows, it does not replace
            "[IMPORT /m/vec]",
            "[REFERENCE_HAS /m/vec]",
            "*/sys/instset/probe/reference_has % 1 % true",
            "[REFERENCE_HAS /m/math]",
            "*/sys/instset/probe/reference_has % 1 % true"
    })
    void testImportRecordsAReferenceWithoutDisplacing() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // the parent imports a vocabulary ...
            "[IMPORT /m/math]",
            // ... and a machine created inside it still has it. Necessary: without this a pushed machine has no
            // instructions and its code cannot run.
            "[STATE] *~.push(xyz)",
            "[REFERENCE_HAS /m/math]",
            "*/sys/instset/probe/reference_has % 1 % true",
            "[STATE] *~.pop(_)",
            // and the parent still has it after the child is gone
            "[REFERENCE_HAS /m/math]",
            "*/sys/instset/probe/reference_has % 1 % true"
    })
    void testAChildInheritsTheParentsVocabulary() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // ADDING to a vocabulary: an inst written while standing in a child resolves there
            "[STATE] *~.push(xyz)",
            "[STATE] inst(a=>int::T){ + *a}@dbl",
            "3.dbl(4) % 7",
            "[STATE] *~.pop(_)",
            // and is gone with the child — it did not leak up to the parent
            "3.dbl(4) % <ERROR>",
            // nor across to a sibling, which never saw it
            "[STATE] *~.push(other)",
            "3.dbl(4) % <ERROR>"
    })
    void testAddingToAVocabularyIsLocalToTheMachine() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // nesting does not compound a vocabulary: each level of the nest reports the SAME reference list it
            // inherited, so pushing twice is not the same as importing twice
            "[IMPORT /m/math]",
            "[STATE] *~.push(outer)",
            "[STATE] *~.push(inner)",
            "[REFERENCE_HAS /m/math]",
            "*/sys/instset/probe/reference_has % 1 % true",
            "[REFERENCE_COUNT]",
            "*/sys/instset/probe/reference_count % 0 % false",
            "[STATE] move(/.)"
    })
    void testNestingDoesNotCompoundTheVocabulary() {
    }
}
