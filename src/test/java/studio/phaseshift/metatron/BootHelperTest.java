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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * A BOOT HELPER IS ONE INSTRUCTION. A boot applies each file under {@code boot/space} to its argument —
 * {@code *boot/args/web/ws.<fs:boot/space/web/ws.mtron>()} — so a helper that evaluates to nothing is a boot that
 * silently builds nothing. An apostrophe in a helper's comment once did exactly that to the ws and http helpers, and the
 * docker and agent-ide boots served no websocket until it was found.
 */
public class BootHelperTest extends AbstractMetatronTest {

    @BeforeAll
    public static void importEverything() {
        InstSet.importInstSet(f("#"));
    }

    /**
     * {@code boot/space/dckr/socket.mtron} is not a row: under the default typer stages its {@code style::[...]}
     * construction fails an inst-range check ({@code expected /m/rec}), which is a defect of its own, not a comment's.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "boot/space/web/ws.mtron",
            "boot/space/web/http.mtron",
            "boot/space/mem/home.mtron",
            "boot/space/tble/sqlite.mtron",
            "boot/space/tble/mariadb.mtron",
            "boot/space/vec/chromadb.mtron",
            "boot/space/grph/gremlin.mtron",
    })
    void testABootHelperIsOneInstruction(final String helper) throws IOException {
        final List<String> errors = new ArrayList<>();
        final List<Obj> results = Machine.Helper.evaluate(Path.of(helper).toFile(), error -> errors.add(error.lineNumber() + ": " + error.parseException())).toList();
        assertEquals(List.of(), errors, helper + " should parse");
        assertEquals(1, results.size(), helper + " should evaluate to exactly one instruction: " + results);
        assertTrue(results.getFirst().isObjInst(), helper + " should be an instruction: " + results.getFirst());
    }
}
