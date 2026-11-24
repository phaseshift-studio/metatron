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

package studio.phaseshift.metatron.isa.m.sys.ui.console;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE CONSOLE'S LINE ({@link Executor#compile}): compiled by the current machine before anything reads its
 * resolution, as the ws eval compiles a program. The console once resolved a line first, so the nomadic extractor
 * met resolved instructions (Marko's console answered {@code 1.move(//0).*}{@code /sys/tmp} with "found no machine"
 * while wsplus answered 345, 2026-11-24), and a tail's instructions were reported as this machine's to resolve.
 */
public class ExecutorTest extends AbstractMetatronTest {

    /**
     * THE TAIL IS LEFT UNBOUND for the machine it lands in: the line's machine change is cut off, and the cut carries
     * its tail as written. Each row: the line.
     */
    @ParameterizedTest
    @CsvSource({
            "5.move(<..>).plus(1).mult(2)",
            "5.push(<a>).plus(1).mult(2)",
            "5.pop().plus(1).mult(2)",
    })
    void testALinesTailIsLeftUnbound(final String line) {
        final Code compiled = Executor.compile(ObjmtronSerializer.parse(line.trim()).asCall().asCode(), unresolved -> {
        });
        final Inst cut = compiled.insts().getLast();
        checkEquality(LOG, uri(CUT_INST_TID), uri(cut.tid().basePath()), true);
        checkEquality(LOG, bool(false), bool(cut.arg(1).isResolved(false)), true);
    }

    /**
     * WHAT THE COMPILER LEAVES OPEN HERE IS REPORTED, AND ONLY THAT: a plus on a variable of no known type runs
     * unresolved, while the same instructions in a move's tail are the landing machine's, and a profile's program is
     * the profile's to apply (a line resolved first reports the tail). Each row: the line % whether anything is
     * reported.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*abc.plus(2)               % true",
            "5.move(<..>).*abc.plus(2)  % false",
            "1.plus(2).profile()        % false",
    }, delimiter = '%')
    void testWhatTheCompilerLeavesOpenIsReported(final String line, final boolean reports) {
        final AtomicReference<List<Inst>> reported = new AtomicReference<>(List.of());
        Executor.compile(ObjmtronSerializer.parse(line.trim()).asCall().asCode(), reported::set);
        checkEquality(LOG, bool(reports), bool(!reported.get().isEmpty()), true);
    }
}
