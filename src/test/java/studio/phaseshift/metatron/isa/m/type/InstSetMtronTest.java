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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * AN INSTRUCTION SET'S .mtron FILE LOADS WITH IT. Beside a java instruction set's class sits its .mtron file, one to
 * one ({@code ..isa.m.mach.machInstSet} has {@code ..isa/m/mach/mach.mtron}), and {@code AbstractInstSet.setup()}
 * evaluates it at the end. The two fixture sets below are set up the way BootLoader sets up /m/mach (mount, then
 * setup); their files are {@code src/test/mtron/isa/m/type/lighthouse.mtron} and {@code reef.mtron}.
 */
public class InstSetMtronTest extends AbstractMetatronTest {

    private static final fURI LIGHTHOUSE = f("/lighthouse");
    private static final fURI REEF = f("/reef");

    /**
     * a set whose .mtron file calls the set's {@code flash()} while it loads
     */
    public static final class lighthouseInstSet extends AbstractInstSet {

        public lighthouseInstSet() {
            super(mutableMap(uri(PATTERN), uri(LIGHTHOUSE.extend(HASH_FURI))), INSTSET_TID, LIGHTHOUSE);
        }

        @Override
        public void setup() {
            this.jvm().putAll(mutableMap(
                    uri(PATTERN), uri(LIGHTHOUSE.extend(ALL)),
                    uri(INST), lst(instC(LIGHTHOUSE.extend(INST).extend("flash").dom(INT_TID).rng(INT_TID), lst(),
                            (lhs, inst) -> jnt(lhs.intValue() * 3)))));
            super.setup();
        }
    }

    /**
     * a set whose .mtron file does not parse
     */
    public static final class reefInstSet extends AbstractInstSet {

        public reefInstSet() {
            super(mutableMap(uri(PATTERN), uri(REEF.extend(HASH_FURI))), INSTSET_TID, REEF);
        }

        @Override
        public void setup() {
            this.jvm().putAll(mutableMap(
                    uri(PATTERN), uri(REEF.extend(ALL)),
                    uri(INST), lst(instC(REEF.extend(INST).extend("sound").dom(INT_TID).rng(INT_TID), lst(),
                            (lhs, inst) -> jnt(lhs.intValue() * 2)))));
            super.setup();
        }
    }

    @BeforeAll
    static void mountTheFixtureSets() {
        Stream.of(new lighthouseInstSet(), new reefInstSet()).forEach(isa -> {
            InstSet.mount(isa);
            isa.setup();
        });
    }

    @AfterAll
    static void unmountTheFixtureSets() {
        Stream.of(LIGHTHOUSE, REEF).forEach(vid -> Machine.current().memory().removeSpace(vid));
        // the .mtron files' variable-stack names
        Stream.of("lighthouse", "reef").forEach(name -> Machine.write(f(name), noobj()));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // the file called the set's flash() while it loaded: it ran after the set's own setup
            "*lighthouse/range      % 12",
            // one setup, one evaluation
            "*lighthouse/lit        % 1",
            // an instruction the file put on the variable stack, calling the set's flash()
            "5.lighthouse/inst/ex() % 115",
            // a file that does not parse is one parse: not even its first statement applies ...
            "*reef/charted          % noobj",
            // ... and its set is mounted all the same, the failure only logged
            "3.sound()              % 6",
    }, delimiter = '%')
    void testTheMtronFileLoadsWithItsSet(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }
}
