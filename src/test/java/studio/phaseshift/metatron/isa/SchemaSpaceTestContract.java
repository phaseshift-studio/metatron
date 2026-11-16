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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.INSTSET;

/**
 * ONE SCHEMA-INFERENCE SEMANTICS FOR EVERY DATABASE SPACE (Marko, 2026-11-16), proved on each backend that implements
 * this contract: SQLite, Mongo, TinkerGraph and JanusGraph.
 * <ul>
 *   <li>a field is required when every record of the collection has it, counted over every record (a sql NULL is an
 *   absent field), and optional ({@code {?}}) otherwise;</li>
 *   <li>a field's type is the LCD of the types of its values ({@code Type.Helper.findLCD});</li>
 *   <li>the space's own instset holds the type, at {@code <space>/instset/<collection>};</li>
 *   <li>a write to an undeclared collection widens its type, so the type still holds every record;</li>
 *   <li>a declared collection keeps its declared type, and a write that breaks it is refused.</li>
 * </ul>
 * A row names its own collection, seeds records straight into the backend (data metatron never wrote), runs its writes
 * through the space, and then evaluates its read. In a row's code {@code $c} is the collection's address and {@code $T}
 * the type at the collection's instset address. A write the type must refuse is marked with a leading {@code !}.
 * <p>
 * Every read names records the type must EXCLUDE: a type reference that resolves to nothing constrains nothing, so a
 * read that only counted matches could pass with no type at all.
 */
public interface SchemaSpaceTestContract {

    /**
     * The address of a collection of this space: {@code db:kennel}, {@code mongo:kennel}, {@code /g/kennel}.
     */
    fURI schemaCollection(final String name);

    /**
     * Store records straight into the backend, past the space: data the space must discover, not data it wrote.
     */
    void seedCollection(final String name, final List<Rec> records);

    /**
     * Drop a collection's data, so a row starts from a store without it.
     */
    void dropDeducedCollection(final String collectionName);

    static Stream<Arguments> schemaInferenceRows() {
        final List<String> none = List.of();
        final String declareFox = "$c -> rec::T[?[name=>str::T,age=>int::T]]@fox";
        final String declareWolf = "$c -> rec::T[?[name=>str::T,age=>int::T]]@wolf";
        return Stream.of(
                // ── writes through the space ──
                Arguments.of("a field every record has is required and typed by its values", "kennel", none,
                        List.of("$c/9001 -> [name=>'rex',age=>3]", "$c/9002 -> [name=>'fido',age=>5]"),
                        "{[name=>'kit',age=>2],[name=>'tom'],[age=>4],[name=>'kat',age=>'old']}.isa($T).count()", "1"),
                Arguments.of("a field only some records have is optional", "aviary", none,
                        List.of("$c/9001 -> [name=>'tweety',age=>3]", "$c/9002 -> [name=>'polly']"),
                        "{[name=>'kit',age=>2],[name=>'tom'],[age=>4]}.isa($T).count()", "2"),
                Arguments.of("a write that adds a field widens the type, which still holds every record", "stable", none,
                        List.of("$c/9001 -> [name=>'ed']", "$c/9002 -> [name=>'trigger']", "$c/9003 -> [name=>'silver',age=>9]"),
                        "*$c/+.isa($T).count()", "3"),
                Arguments.of("a write that leaves out a required field makes it optional", "burrow", none,
                        List.of("$c/9001 -> [name=>'bugs',age=>3]", "$c/9002 -> [name=>'peter',age=>5]", "$c/9003 -> [name=>'thumper']"),
                        "*$c/+.isa($T).count()", "3"),
                Arguments.of("a field given a value of another type widens, and every record still holds", "hive", none,
                        List.of("int::T[?>0]@larva", "$c/9001 -> [name=>'maya',age=>larva::3]", "$c/9002 -> [name=>'willy',age=>5]"),
                        "*$c/+.isa($T).count()", "2"),
                Arguments.of("a widened field takes the lcd of its types (not either type) and stays required", "apiary", none,
                        List.of("int::T[?>0]@pupa", "$c/9001 -> [name=>'maya',age=>pupa::3]", "$c/9002 -> [name=>'willy',age=>5]"),
                        "{[name=>'barry',age=>-2],[name=>'buzz'],[name=>'drone',age=>'old']}.isa($T).count()", "1"),
                // ── a declared collection ──
                Arguments.of("a declared collection keeps its declared type", "den", none,
                        List.of(declareFox, "$c/9001 -> [name=>'reynard',age=>4,toy=>'hen']"),
                        "*$c", "fox::T"),
                Arguments.of("a declared type refuses a write that breaks it, and stores nothing of it", "lair", none,
                        List.of(declareWolf, "$c/9001 -> [name=>'akela',age=>7]",
                                "!$c/9002 -> [name=>'raksha']", "!$c/9003 -> [name=>'grey',age=>'old']"),
                        "*$c/+.count()", "1"),
                // ── records the space never wrote ──
                Arguments.of("discovery requires a field every stored record has; one some lack is optional", "roost",
                        List.of("[name=>'hedwig',age=>3]", "[name=>'errol',age=>9,toy=>'mouse']"), none,
                        "{[name=>'kit',age=>2],[name=>'tom'],[name=>'kat',age=>2,toy=>'bone'],[name=>'kat',age=>'old']}.isa($T).count()", "2"),
                Arguments.of("discovery counts presence over every stored record, past any sample", "warren",
                        Stream.concat(IntStream.rangeClosed(1, 149).mapToObj(i -> "[name=>'kit" + i + "',age=>" + i + "]"),
                                Stream.of("[name=>'last']")).toList(), none,
                        "{[name=>'tom'],[age=>4]}.isa($T).count()", "1"),
                Arguments.of("discovery types a field by the lcd of its stored values", "coop",
                        List.of("[name=>'henny',age=>3]", "[name=>'penny',age=>'old']"), none,
                        "{[name=>'goosey',age=>true],[name=>'lucky']}.isa($T).count()", "1"));
    }

    @ParameterizedTest
    @MethodSource("studio.phaseshift.metatron.isa.SchemaSpaceTestContract#schemaInferenceRows")
    default void testSchemaInference(final String description, final String collection, final List<String> seeds,
                                     final List<String> writes, final String read, final String expected) {
        this.dropDeducedCollection(collection);
        this.seedCollection(collection, seeds.stream().map(seed -> ObjmtronSerializer.parse(seed).apply().<Rec>as()).toList());
        final fURI address = this.schemaCollection(collection);
        final fURI type = Machine.current().memory().getSpaceFor(address).vid().extend(INSTSET).extend(collection);
        final UnaryOperator<String> bind = code -> code.replace("$c", address.toString()).replace("$T", type + "::T");
        final Machine machine = Machine.current();
        for (final String write : writes) {
            final boolean refused = write.startsWith("!");
            final String code = bind.apply(refused ? write.substring(1) : write);
            Obj result;
            try {
                result = Machine.Helper.evalAt(machine, () -> ObjmtronSerializer.parse(code).apply());
            } catch (final MTronException e) {
                assertTrue(refused, description + ": " + code + " threw " + e.getMessage());
                continue;
            }
            if (refused)
                assertTrue(result.stream().anyMatch(Obj::isFail), description + ": should have been refused: " + code + " => " + result);
            else
                assertFalse(result.stream().anyMatch(Obj::isFail), description + ": " + code + " => " + result);
        }
        AbstractMetatronTest.checkCodeParseApply(Graphitty.log(SchemaSpaceTestContract.class), bind.apply(read), expected);
    }
}
