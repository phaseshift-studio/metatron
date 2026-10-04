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

package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.mInstSet.BARRIER_INST_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class machInstSetTest extends AbstractInstSetTest {

    public machInstSetTest() {
        super(machInstSet::new);
    }

    /**
     * THE REWRITE'S SHAPE, and nothing else. No evaluation and no resolve: vids and types are bound downstream of
     * rewriting, so this stage is the only place where the DISTRIBUTION ITSELF is visible, and asserting it here
     * says what the rule produced rather than what the later stages happened to make of it.
     *
     * <p>A row declares its own cluster -- one barrier key per peer, so a key IS a machine -- and states the
     * gathers the rule should mint, in order, plus where the chain ends. Those are what the rule decides: one
     * gather per peer addressed at that peer's mailbox, and the reducer keeping its place, so that work written
     * after the reducer stays after it and on the home.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // no peers: nothing to gather from, so the reducer is left exactly as written
            "{1,2,3}.plus(1).plus(2).sum()           %  (none)  %  (none)                                      %  sum",
            // one peer: a gather for it, immediately before the reducer
            "{1,2,3}.plus(1).plus(2).sum()           %  b       %  /sys/peer/a/barrier/b                       %  sum",
            // two peers: one gather each, in a stable order
            "{1,2,3}.plus(1).plus(2).sum()           %  b,c     %  /sys/peer/a/barrier/b;/sys/peer/a/barrier/c %  sum",
            // a reducer that is NOT last: the gathers precede it and the trailing work stays after it
            "{1,2,3}.plus(1).plus(2).sum().plus(10)  %  b       %  /sys/peer/a/barrier/b                       %  plus",
    }, delimiter = '%')
    public void testTheRewriteShape(final String code, final String peers, final String mailboxes, final String endsWith) {
        if (peers.startsWith("("))
            AbstractMachineTest.clearPeers();
        else
            AbstractMachineTest.declarePeers(peers.split(","));

        // THE STAGE UNDER TEST, and nothing else -- no apply, no resolve, no type
        final Code rewritten = Machine.root().compiler().rewrite().apply(ObjmtronSerializer.parse(code)).asCode();
        LOG.warn("rewritten %s => %s", code, rewritten);

        assertEquals(mailboxes.startsWith("(") ? List.of() : List.of(mailboxes.split(";")),
                rewritten.insts().stream()
                        .filter(inst -> inst.tid().basePath().equals(BARRIER_INST_TID))
                        .map(inst -> inst.arg(0).toString())
                        .toList(),
                "one gather per declared peer, in order, each addressed at its own mailbox");

        assertEquals(endsWith, rewritten.insts().getLast().tid().basePath().name(),
                "the chain still ends where it should, so a reducer that is not last keeps its trailing work");

        AbstractMachineTest.clearPeers();
    }
}
