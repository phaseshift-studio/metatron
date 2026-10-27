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

package studio.phaseshift.metatron.isa.mach.type.memory;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.REDIRECT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE NAME INDEX IS AN OBJ — a PROJECTION of memory's routing table at {@code *</memory/redirect>}. The resolver reads
 * the raw tables directly ({@code fURI.big()/small()}), because an {@code Obj}-keyed lookup re-enters {@code MObj}'s
 * constructor and the lookup becomes a recursion; the {@code Obj}-keyed rows are built only when this rec is
 * dereferenced, and a Java field cannot be read from the language, which is why the projection exists at all.
 * <p>
 * What these tests pin is that the two views agree: the address answers, and every row it exposes holds what
 * {@code big()} picked from the same relation.
 */
public class RedirectTableTest extends AbstractMetatronTest {

    @Test
    void testTheIndexAnswersAtItsAddress() {
        final Obj table = Machine.read(f("/memory/redirect"));
        assertTrue(table.isRec(), "the name index is an obj: " + table);
        // `count` is an inst of the core vocabulary, so its short name is in the table with its home as the value
        final Obj count = table.asRec().at(uri("count"));
        assertTrue(count.isUri() || count.isObjs(), "count names its home: " + count);
    }

    @Test
    void testTheIndexBacksBig() {
        // the relation the table exposes IS the one big() consults
        assertEquals(f("/m/inst/count"), f("count").big(), "count's home is /m/inst/count");
        assertEquals(f("/m/inst/count"), Machine.read(f("/memory/redirect")).asRec()
                .at(uri("count")).uriValue(), "and the exposed row says the same thing");
    }

    /**
     * THE INVARIANT THE EAGER WIRING BROKE. The index is an obj, and an obj can be saved back to the address it names
     * — which for memory is a row of the machine's own component rec. So the index carries NO vid and is built only
     * when something dereferences it: a memory, with its index built, must leave {@code processor} exactly where the
     * machine put it. That row is the one that went missing ("machine has no processor: noobj").
     */
    @Test
    void testAMemoryLeavesItsMachineWhole() {
        final Machine machine = Machine.current();
        final BasicMemory memory = new BasicMemory();
        assertFalse(machine.processor().isNoObj(), "a memory's construction keeps the machine's processor: " + machine.processor());
        final Obj table = memory.at(uri(REDIRECT));
        assertTrue(table.isRec(), "the index answers at its address, minted on dereference: " + table);
        assertFalse(machine.processor().isNoObj(), "and dereferencing it does not write back into the machine: " + machine.processor());
    }

    /**
     * THE HOT INDEX IS THE ONE {@code big()} READS, and the exposed rows say the same thing it does — checked over every
     * row rather than a sample, because "the obj agrees with the index" is the only claim this table makes today.
     */
    @Test
    void testEveryExposedRowAgreesWithTheIndex() {
        final Obj table = Machine.read(f("/memory/redirect"));
        assertTrue(table.isRec(), "the name index is an obj: " + table);
        int rows = 0;
        for (final Obj key : table.asRec().jvm().keySet().stream().toList()) {
            if (!key.isUri())
                continue;
            final fURI name = key.uriValue();
            final java.util.List<fURI> homes = table.asRec().at(key).stream().filter(Obj::isUri)
                    .map(Obj::uriValue).toList();
            if (homes.isEmpty())
                continue;
            rows++;
            // A NAME MAY DENOTE SEVERAL PATHS (`stdout` lives in both /sys/io and /m/sys/inst); the exposed row holds
            // the whole candidate set, and big() PICKS from it by its own rule (primary /m, then shortest). So the
            // invariant is membership, not equality.
            assertTrue(homes.contains(name.big()), "the row for " + name + " holds what big() picked: " + homes + " vs " + name.big());
        }
        assertTrue(rows > 100, "the vocabulary is in the table: " + rows + " rows");
    }
}
