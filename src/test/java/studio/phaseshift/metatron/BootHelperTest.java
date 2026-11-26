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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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
     * requires. a name the helper does not declare is kept in the bound args, where no parameter reads it, and a
     * required parameter the call leaves out binds noobj, or fails when its spec is a conversion: spaces.boot.mtron
     * passed host, port and image to helpers declaring saddr, sport and simg, and its chromadb helper read noobj for all
     * three, so the space never mounted. what a helper requires is read off its binder (the next test). the boots call
     * their helpers by name, which is this test's policy, not the binder's rule: an ordered call binds in declaration
     * order, so a reordered declaration would rebind it silently. the check is of names only: an argument that reads a
     * boot arg the boot lacks still binds noobj, and a default a helper's body supplies is invisible to its declaration
     * (a default declared in the spec, else(...), makes its parameter optional here). the parser reads both sides: each
     * call out of the boot statement that holds it, each declaration out of the helper evaluated to its inst.
     * socket.mtron does not evaluate (the note above), so its declaration alone goes unread, and only while that is
     * why. the agent-ide boot passes no arguments, so its row checks that the helpers it calls require none
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "boot/spaces.boot.mtron",
            "boot/docker.boot.mtron",
            "boot/agent-ide.boot.mtron",
    })
    void testABootCallsItsHelpersByTheirParametersNames(final String boot) throws IOException {
        // the statements as Machine.Helper.evaluate splits a file, and of them only those naming a boot/space helper: a
        // boot's other statements construct their literals as they parse, its console among them. each one holds its
        // call at the top, where the walk below reads, so a call nested in another instruction's argument fails here
        final List<String> statements = CommonUtil.splitOnNonQuotedSequence(mParser.stripComments(Files.readString(Path.of(boot))), ';', false).stream()
                .filter(statement -> statement.contains("boot/space/"))
                .toList();
        assertFalse(statements.isEmpty(), boot + " calls a helper");
        for (final String statement : statements) {
            final Obj parsed = mParser.parse(statement);
            assertTrue(!parsed.isFail() && parsed.isObjCall(), boot + " parses " + statement + ": " + parsed);
            final List<Inst> calls = parsed.<Call>as().insts().stream().filter(inst -> inst.tid().name().endsWith(".mtron")).toList();
            assertFalse(calls.isEmpty(), boot + " calls its helper at the top of " + statement);
            for (final Inst call : calls) {
                final fURI helper = call.tid().qLess().scheme(null);
                // a list that names something is a rec, and one that names nothing is the empty lst
                assertTrue(call.args().isEmpty() || call.args().isRec(), boot + " passes " + helper + " its arguments by name: " + call);
                final List<String> errors = new ArrayList<>();
                final List<Obj> evaluated = Machine.Helper.evaluate(Path.of(helper.toString()).toFile(), error -> errors.add(String.valueOf(error.parseException().getMessage()))).toList();
                if (evaluated.isEmpty() && helper.equals(f("boot/space/dckr/socket.mtron")) && errors.stream().anyMatch(error -> error.contains("unable to construct style::T")))
                    continue;
                assertFalse(evaluated.isEmpty(), helper + " evaluates to its instruction: " + errors);
                final Inst declaration = evaluated.getFirst().asInst();
                assertEquals(List.of(), undeclared(call, declaration), boot + " passes " + helper + " arguments it does not declare: " + call);
                assertEquals(List.of(), leftOut(call, declaration), boot + " leaves out parameters " + helper + " requires: " + call);
            }
        }
    }

    /**
     * what a helper requires is what its binder leaves unbound: a call that passes nothing binds each named parameter
     * to noobj or a fail, unless a default fills it, and a zeroable key or spec opts into absence. a filter with no
     * default binds noobj and a conversion fails, so both are required. a name is declared whatever its coefficient
     * ({?}port declares port), and a helper that declares nothing declares no name
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "(port=>int::T)                          % ()                             % port % ",
            "(port=>int::T)                          % (port=>8025)                   %      % ",
            "(port=>?int::T.else(8025))              % ()                             %      % ",
            "(port=>?int::T)                         % ()                             % port % ",
            "(port=>as(int::T))                      % ()                             % port % ",
            "({?}port=>int::T)                       % ()                             %      % ",
            "({?}port=>int::T)                       % (port=>8025)                   %      % ",
            "(port=>int{?}::T)                       % ()                             %      % ",
            "(host=>uri::T,port=>int::T)             % (host=><localhost>,prot=>8025) % port % prot",
            "(host=>uri::T,port=>as(int::T))         % ()                             % host,port % ",
            "()                                      % ()                             %      % ",
            "()                                      % (port=>8025)                   %      % port",
    })
    void testAHelperRequiresWhatItsBinderLeavesUnbound(final String parameters, final String arguments, final String leftOut, final String undeclared) {
        final Inst helper = ObjmtronSerializer.parse("|inst?#{?}<=#{?}" + parameters + "{ _ }").apply().asInst();
        final Inst call = mParser.parse("<fs:boot/space/fixture.mtron>" + arguments).<Call>as().insts().getFirst();
        final Function<String, List<fURI>> names = cell -> null == cell ? List.of() : Arrays.stream(cell.split(",")).map(name -> f(name.trim())).toList();
        assertEquals(names.apply(leftOut), leftOut(call, helper), "a call of " + parameters + " with " + arguments + " leaves out");
        assertEquals(names.apply(undeclared), undeclared(call, helper), "a call of " + parameters + " with " + arguments + " passes undeclared");
    }

    /**
     * the parameters a call leaves out that its helper requires: each declared parameter its binder
     * (Inst.Helper.resolveArgs) leaves at noobj or a fail in a call that passes nothing, less a zeroable key or spec, and
     * less what the call passes. the bound values are read raw, never through at(), which would run an argument's code
     */
    private static List<fURI> leftOut(final Inst call, final Inst helper) {
        if (!helper.args().isRec() || Inst.Helper.isOpenArgs(helper.args()))
            return List.of();
        final Set<fURI> passed = names(call.args());
        final Map<Obj, Obj> bound = Inst.Helper.resolveArgs(helper.args(rec0()), helper, noobj()).asRec().recValue();
        return helper.args().asRec().elements()
                .filter(parameter -> !parameter.first().uriValue().c().isZeroable() && !parameter.second().c().isZeroable())
                .filter(parameter -> {
                    final Obj value = bound.get(uri(parameter.first().uriValue()));
                    return null == value || value.isNoObj() || value.isFail();
                })
                .map(parameter -> parameter.first().uriValue().cLess())
                .filter(name -> !passed.contains(name))
                .toList();
    }

    /**
     * the names a call passes that its helper does not declare. an open declaration (a key that is a type,
     * Inst.Helper.isOpenArgs) names the shape of the caller's keys, so it declares every name
     */
    private static List<fURI> undeclared(final Inst call, final Inst helper) {
        if (Inst.Helper.isOpenArgs(helper.args()))
            return List.of();
        final Set<fURI> declared = names(helper.args());
        return names(call.args()).stream().filter(name -> !declared.contains(name)).toList();
    }

    /**
     * the names an argument list gives, in order and coefficients aside: the parser keeps a key's coefficient in its
     * uri ({?}port is port{?})
     */
    private static Set<fURI> names(final Poly<?, ?> arguments) {
        return arguments.keys().map(key -> key.uriValue().cLess()).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
