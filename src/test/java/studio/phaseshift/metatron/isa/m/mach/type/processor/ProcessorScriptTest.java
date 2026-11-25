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

package studio.phaseshift.metatron.isa.m.mach.type.processor;

import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;

/**
 * THE COMPONENT AS A SPACE — the processor half of the component contract (the network half is
 * {@code NetworkComponentScriptTest}, the memory half {@code MemoryScriptTest}).
 * <p>
 * The processor is the machine's EXECUTION space: {@code ~/processor} answers the processor space itself, carrying
 * its configuration; its computers are {@code ~/processor/computer}; its uncaught fails are {@code ~/processor/fail};
 * and a processor attached after boot at {@code ~/processor/q} answers at the branch.
 * <p>
 * {@code BasicSwarmProcessor} does not satisfy this yet — it is a rec whose writes the machine space folds by hand —
 * so these scripts state the contract and the refactor is measured against them.
 */
@Disabled
public class ProcessorScriptTest extends AbstractMetatronTest {

    /**
     * {@code *~/processor} IS the processor space: it answers, its type is a {@code memSpace}, its vid is the branch
     * address, and its configuration is read below it.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/processor.count()                              % 1                  [-- the branch answers the processor --]",
            "*~/processor.?/m/mach/component/processor::T.count() % 1                [-- it is a processor::T --]",
            "*~/processor.?/m/space/memspace::T.count()         % 1                  [-- BasicSwarmProcessor IS a memSpace --]",
            "@~/processor.vid()                                % </processor>       [-- the branch address is its vid --]",
            "*~/processor/pattern.?uri::T.count()               % 1                  [-- the space carries its pattern --]",
            "*~/processor/q.?lst::T.count()                     % 1                  [-- the space carries its q list, a lst::T --]"
    })
    void testTheProcessorBranchIsTheProcessorSpace() {
    }

    /**
     * A q-proc attached after boot at {@code ~/processor/q} is in the LIVE list, answers at the branch, and is
     * retracted by {@code /processor/q -> noobj}.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/processor/q.>>.?~analq::T.count()               % 0                  [-- no analq processor yet --]",
            "[STATE] @~/processor/q >>= mult([analq::T])",
            "*~/processor/q.>>.?~analq::T.count()               % 1                  [-- the update landed on the live processor --]",
            "*~/processor/q?analq.?/m/mach/analysis::T.count()   % 1                  [-- the attached processor answers at the branch --]",
            "[STATE] /processor/q -> noobj",
            "*~/processor/q.>>.?~analq::T.count()               % 0                  [-- the deletion retracts it --]"
    })
    void testAQProcLandsOnTheLiveProcessor() {
    }

    /**
     * The computer index and the uncaught-fail stack are ADDRESSES below the branch — fields a reader resolves, not
     * Java side tables reached by a bespoke door.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/processor/computer.count()                     % 1                  [-- the computer index answers at its address --]",
            "*~/processor/fail.?rec::T.count()                  % 1                  [-- the fail stack answers at its address --]"
    })
    void testTheComputerIndexAndFailStackAreAddresses() {
    }
}
