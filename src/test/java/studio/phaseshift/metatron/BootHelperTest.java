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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * a boot calls a helper by the names of the parameters the helper declares, and passes every one the helper
     * requires. arguments bind by name, so an argument named for a parameter the helper lacks binds nothing, and a
     * required parameter the call leaves out binds noobj just the same: spaces.boot.mtron passed host, port and image to
     * helpers declaring saddr, sport and simg, and its chromadb helper read noobj for all three, so the space never
     * mounted. a call may leave out what {@code Inst.Helper.argsFillSlots} lets go, a call-shaped spec (a default,
     * {@code else(...)}) or a zeroable key or spec. the parser reads both sides: a call out of the boot statement that
     * holds it, a declaration out of the helper evaluated to its inst. socket.mtron does not evaluate (the note above),
     * so its calls alone go unchecked, and only until it does. the agent-ide boot passes no arguments, so its row checks
     * that the helpers it calls require none
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "boot/spaces.boot.mtron",
            "boot/docker.boot.mtron",
            "boot/agent-ide.boot.mtron",
    })
    void testABootCallsItsHelpersByTheirParametersNames(final String boot) throws IOException {
        // the statements as Machine.Helper.evaluate splits a file, and of them only those naming a helper: a boot's other
        // statements construct their literals as they parse, its console among them
        final List<Inst> calls = CommonUtil.splitOnNonQuotedSequence(mParser.stripComments(Files.readString(Path.of(boot))), ';', false).stream()
                .filter(statement -> statement.contains(".mtron"))
                .map(statement -> {
                    final Obj parsed = mParser.parse(statement);
                    assertFalse(parsed.isFail(), boot + " parses " + statement + ": " + parsed);
                    return parsed;
                })
                .filter(Obj::isObjCall)
                .flatMap(parsed -> parsed.<Call>as().insts().stream())
                .filter(inst -> inst.tid().name().endsWith(".mtron"))
                .toList();
        assertFalse(calls.isEmpty(), boot + " calls a helper");
        for (final Inst call : calls) {
            final String helper = call.tid().qLess().scheme(null).toString();
            final List<Obj> evaluated = Machine.Helper.evaluate(Path.of(helper).toFile(), error -> {}).toList();
            if (evaluated.isEmpty() && "boot/space/dckr/socket.mtron".equals(helper))
                continue;
            assertFalse(evaluated.isEmpty(), helper + " evaluates to its instruction");
            // a list that names something is a rec, and one that names nothing is the empty lst, on either side
            final Poly<?, ?> given = call.args();
            final Poly<?, ?> declaration = evaluated.getFirst().asInst().args();
            assertTrue(given.isEmpty() || given.isRec(), boot + " passes " + helper + " its arguments by name: " + call);
            assertTrue(declaration.isEmpty() || declaration.isRec(), helper + " declares its parameters by name: " + declaration);
            final Set<String> passed = given.keys().map(key -> key.uriValue().toString()).collect(Collectors.toSet());
            final Set<String> declared = declaration.keys().map(key -> key.uriValue().toString()).collect(Collectors.toSet());
            assertEquals(List.of(), passed.stream().filter(name -> !declared.contains(name)).toList(),
                    boot + " passes " + helper + " arguments it does not declare, of " + passed + " against " + declared);
            assertEquals(List.of(), declaration.<Rel>elements()
                            .filter(parameter -> parameter.second().isType() && !parameter.second().c().isZeroable() && !parameter.first().c().isZeroable())
                            .map(parameter -> parameter.first().uriValue().toString())
                            .filter(name -> !passed.contains(name))
                            .toList(),
                    boot + " leaves out parameters " + helper + " requires, passing " + passed);
        }
    }
}
