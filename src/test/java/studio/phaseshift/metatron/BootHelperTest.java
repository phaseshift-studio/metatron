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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * a boot calls a helper by the names of the parameters the helper declares: arguments bind by name, so an argument
     * named for a parameter the helper lacks binds nothing. spaces.boot.mtron passed host, port and image to helpers
     * declaring saddr, sport and simg, and its chromadb helper read noobj for all three, so the space never mounted
     */
    @ParameterizedTest
    @CsvSource(value = {
            "boot/spaces.boot.mtron",
            "boot/docker.boot.mtron",
            "boot/agent-ide.boot.mtron",
    })
    void testABootCallsItsHelpersByTheirParametersNames(final String boot) throws IOException {
        final String text = Files.readString(Path.of(boot));
        final Matcher call = Pattern.compile("<fs:(boot/space/[^>]+\\.mtron)>\\(").matcher(text);
        int calls = 0;
        while (call.find()) {
            final Matcher declared = Pattern.compile("\\|inst\\?[^(]*\\(([^)]*)\\)").matcher(Files.readString(Path.of(call.group(1))));
            assertTrue(declared.find(), call.group(1) + " declares its parameters");
            // the call's arguments, to the parenthesis that closes them
            int depth = 1;
            int end = call.end();
            for (; depth > 0; end++)
                depth += text.charAt(end) == '(' ? 1 : text.charAt(end) == ')' ? -1 : 0;
            final Set<String> passed = names(text.substring(call.end(), end - 1));
            final Set<String> parameters = names(declared.group(1));
            assertTrue(parameters.containsAll(passed), boot + " calls " + call.group(1) + " with " + passed + ", but it declares " + parameters);
            calls++;
        }
        assertTrue(calls > 0, boot + " calls a helper");
    }

    /**
     * the names an argument list gives, each before its => at the top level of the list
     */
    private static Set<String> names(final String arguments) {
        final Set<String> names = new LinkedHashSet<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i <= arguments.length(); i++) {
            final char c = i < arguments.length() ? arguments.charAt(i) : ',';
            if (c == '(' || c == '[' || c == '{')
                depth++;
            else if (c == ')' || c == ']' || c == '}')
                depth--;
            else if (c == ',' && 0 == depth) {
                final String argument = arguments.substring(start, i);
                if (argument.contains("=>"))
                    names.add(argument.substring(0, argument.indexOf("=>")).trim());
                start = i + 1;
            }
        }
        return names;
    }
}
