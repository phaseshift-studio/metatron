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

package studio.phaseshift.metatron.isa.m.mach.type.memory;

import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;

/**
 * THE COMPONENT AS A SPACE — the memory half of the component contract (the network half is
 * {@code NetworkComponentScriptTest}, the processor half {@code ProcessorScriptTest}).
 * <p>
 * The memory is the machine's ADDRESS SPACE, and its shape must say so: {@code ~/memory} answers the memory space
 * itself, carrying its configuration; its index of mounted spaces is {@code ~/memory/space} (a mount at
 * {@code ~/memory/space/dev} is read back there); its name table is {@code ~/memory/redirect}; and a processor
 * attached after boot at {@code ~/memory/q} answers at the branch.
 * <p>
 * {@code BasicMemory} does not satisfy this yet — it is a rec beside a private index map — so these scripts state
 * the contract and the refactor is measured against them.
 */
@Disabled
public class MemoryScriptTest extends AbstractMetatronTest {

    /**
     * {@code *~/memory} IS the memory space: it answers, its type is a {@code memSpace}, its vid is the branch
     * address, and its configuration is read below it.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/memory.count()                                % 1                  [-- the branch answers the memory --]",
            "*~/memory.?/m/mach/component/memory::T.count()     % 1                  [-- it is a memory::T --]",
            "*~/memory.?/m/space/memspace::T.count()           % 1                  [-- BasicMemory IS a memSpace --]",
            "@~/memory.vid()                                  % </memory>          [-- the branch address is its vid --]",
            "*~/memory/pattern.?uri::T.count()                 % 1                  [-- the space carries its pattern --]",
            "*~/memory/q.?lst::T.count()                       % 1                  [-- the space carries its q list, a lst::T --]"
    })
    void testTheMemoryBranchIsTheMemorySpace() {
    }

    /**
     * A q-proc attached after boot at {@code ~/memory/q} is in the LIVE list, answers at the branch, and is
     * retracted by {@code /memory/q -> noobj}.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/memory/q.>>.?~analq::T.count()                 % 0                  [-- no analq processor yet --]",
            "[STATE] @~/memory/q >>= +[analq::T]",
            "*~/memory/q.>>.?~analq::T.count()                 % 1                  [-- the update landed on the live memory --]",
            "*~/memory/q?analq.?/m/mach/analysis::T.count()     % 1                  [-- the attached processor answers at the branch --]",
            "[STATE] /memory/q -> noobj",
            "*~/memory/q.>>.?~analq::T.count()                 % 0                  [-- the deletion retracts it --]"
    })
    void testAQProcLandsOnTheLiveMemory() {
    }

    /**
     * The index of mounted spaces IS an address: a space mounted at {@code ~/memory/space/dev} is read back at that
     * address as the live space, and the name table answers at {@code ~/memory/redirect}.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] memspace::[pattern=><ms-script/#>]@~/memory/space/ms-script",
            "*~/memory/space/ms-script.count()                 % 1                  [-- the mount is read back at its address --]",
            "*~/memory/redirect.count()                        % 1                  [-- the name table answers at its address --]",
            "[STATE] /memory/space/ms-script -> noobj"
    })
    void testTheSpaceIndexAndNameTableAreAddresses() {
    }
}
