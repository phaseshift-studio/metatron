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

package studio.phaseshift.metatron.isa.m.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class ObjmtronSerializerTest extends AbstractSerializerTest<String> {
    public ObjmtronSerializerTest() {
        super(ObjmtronSerializer.single(), null, null);
    }

    /**
     * AN ADDRESSED URI SURVIVES THE WIRE. The inherited corpus is TEXT IN, TEXT OUT, so it can only contain spellings
     * the parser already accepts — and a bare uri carrying a vid was not one of them: {@code @} was a legal bare-uri
     * character, so {@code /wire/noobj@<a3f91c2b>} read back as ONE uri whose value ended in {@code @}, the address
     * swallowed into the value. That is a wire-wide hole (any uri read from an address and re-sent lost its vid) that
     * only a JAVA-BUILT case can express, which is why this row could not live in {@link #testSerializeDeserializeObj}'s
     * corpus and why the round trip's absent-answer mark depended on it.
     */
    @Test
    void testAnAddressedUriKeepsItsAddress() {
        final Obj addressed = uri(f("/mtron/test")).selfVID(f("a3f91c2b"));
        final String text = ObjmtronSerializer.single().write(addressed);
        LOG.debug("an addressed uri on the wire: %s", text);
        checkEquality(LOG, addressed, ObjmtronSerializer.single().read(text), true);
        idem(addressed);
    }

    /**
     * THE TWO POSITIONS ARE ONE SPELLING: a value that carries an {@code @} is written wrapped, and the vid postfix
     * goes OUTSIDE the brackets — {@code <user@mail>@abc}. The wrapped form closes at {@code >}, so the postfix can
     * never be read as part of the value: no {@code @} fighting, and user-info comes back without the bare-uri
     * charset having to allow it.
     * <p>
     * THE VALUE HERE HAS NO DOT ON PURPOSE. A dotted value would be bracketed for the dot's sake alone, and this
     * test would then pass even if the {@code @} rule were deleted; {@code user@mail} can only be bracketed because
     * of its {@code @}, which is what makes the assertion a guard rather than a description.
     */
    @Test
    void testAUserInfoUriCarriesAVidOutsideItsBrackets() {
        final Obj addressed = uri(f("user@mail")).selfVID(f("abc"));
        final String text = ObjmtronSerializer.single().write(addressed);
        LOG.debug("an addressed user-info uri on the wire: %s", text);
        assertEquals("<user@mail>@abc", text, "the value is wrapped and the vid sits outside the brackets");
        final Obj read = ObjmtronSerializer.single().read(text);
        assertEquals(f("abc"), read.vid(), "the vid comes back outside the brackets");
        checkEquality(LOG, addressed, read, true);
        idem(addressed);
    }

    /**
     * A URI THAT REALLY CARRIES AN {@code @} IS WRITTEN WRAPPED — the other half of the same rule: {@code @} is not a
     * bare-uri character (it is the vid separator), so the writer has to wrap such a value, and the wrapped form still
     * parses. User-info in an authority is the case that will need this for real.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/mtron/test                       % /mtron/test",
            // no dot in either value: the brackets can only come from the '@' (a dotted value is bracketed for the
            // dot's sake, which would hide a deleted '@' rule)
            "user@mail                         % <user@mail>",
            "ws://user@host:8555/mtron         % <ws://user@host:8555/mtron>",
            "user@domain.com                   % <user@domain.com>",
            "ws://user@domain.com:8555/mtron   % <ws://user@domain.com:8555/mtron>",
    }, delimiter = '%')
    void testAUriIsWrittenWithAnUnambiguousSpelling(final String value, final String expected) {
        final Obj written = ObjmtronSerializer.parse(expected).apply();
        checkEquality(LOG, uri(f(value)), written, true);
        checkEquality(LOG, uri(f(value)), ObjmtronSerializer.single().read(ObjmtronSerializer.single().write(uri(f(value)))), true);
        idem(uri(f(value)));
    }

    /**
     * STRINGS ON THE WIRE: every quoting shape a str can be written in has to survive {@code write()}→{@code read()}.
     * <p>
     * A str is carried by QUOTING, not by escaping, so the whole risk is a value that can spell its own closing
     * delimiter: {@code """} in the content must not end the render early, and a quote at the very end must not leave
     * it unbalanced. The corpus is the mtron LITERAL itself — empty, one character, blank space at either end, an
     * apostrophe, a double quote, both at once, and the wire's own triple-quote delimiter inside the value — because
     * a row the reader cannot express would be testing the corpus, not the wire.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "''",                                     // empty
            "'a'",                                    // one character
            "' '",                                    // one blank
            "'  x  '",                                // blank space at both ends
            "'a b c'",                                // spaces inside
            "\"\"\"abc\"\"\"",                        // triple quoted
            "\"it's\"",                               // an apostrophe, double quoted
            "'say \"hi\"'",                           // double quotes, single quoted
            "\"\"\"it's a \"quoted\" word\"\"\"",     // both quote kinds at once
            "'\"\"\"'",                               // the value IS the triple-quote delimiter
            "'a\"\"\"b'",                             // the delimiter inside the value
            "'ends in a quote\"'",                    // one quote at the very end
            "'\\\\'",                                 // a backslash
            "'\\n'",                                  // backslash-n: two characters, as a regex or a path carries
            "'it\\'s'",                               // the delimiter escaped, as only the fallback form needs
            "\"say \\\"hi\\\"\"",                     // a double quote escaped
            "'a\\\\b'",                               // an escaped escape character
            "'\\\\s'",                                // an unknown escape is NOT decoded: it stays backslash-s
            "'trailing\\\\'",                         // a backslash at the very end
            "'{}[](),%#><@!?'",                       // punctuation that doubles as syntax everywhere else
    })
    void testAStringKeepsEveryQuotingShapeAcrossTheWire(final String literal) {
        final Obj parsed = ObjmtronSerializer.parse(literal).apply();
        assertTrue(parsed.isStr(), () -> "not a str literal: " + literal);
        final String wire = ObjmtronSerializer.single().write(parsed);
        LOG.debug("testing {{b}}%s{{/b}} serialized to {{b}}%s{{/b}}", literal, wire);
        assertEquals(parsed, ObjmtronSerializer.single().read(wire),
                () -> "wire form '" + wire + "' did not read back as the value it wrote");
        idem(parsed);
    }

    /**
     * THE CASE NO LITERAL CAN EXPRESS: a value carrying an apostrophe, a double quote, AND the triple-quote delimiter
     * — {@code it's """ here}. No single-, double- or triple-quoted literal spells it (that is why it cannot be a CSV
     * row: the corpus is literals, and this is the value Python's <code>'''</code>/<code>"""</code> problem leaves to
     * escaping). The writer must still render a value the reader gets back unchanged; if it cannot, this is the row
     * that says so.
     */
    @Test
    void testAStringWithBothQuoteKindsAndTheWireDelimiter() {
        final Obj value = str("it's \"\"\" here");
        final String wire = ObjmtronSerializer.single().write(value);
        LOG.debug("a mixed-quote string on the wire: %s", wire);
        assertEquals(value, ObjmtronSerializer.single().read(wire),
                () -> "wire form '" + wire + "' did not read back as the value it wrote");
        idem(value);
    }

    /**
     * A SPANNED INSTRUCTION KEEPS ITS SPAN ACROSS THE WIRE. The span is the instruction's LEADING coefficient
     * ({@code {m,n}op}) and the parser reads it back into the tid's span; {@code basePath()} carries no coefficient, so
     * the writer dropped it — a branch lane's narrowed share printed (and could be re-read) as the whole run. The round
     * trip is the assertion: the text must carry the span AND read back as the code it wrote.
     */
    @Test
    void testASpannedInstructionKeepsItsSpanAcrossTheWire() {
        final Obj code = ObjmtronSerializer.parse("start(0).{0,4}plus(1).sum()").asCode();
        final String wire = ObjmtronSerializer.single().write(code);
        LOG.debug("a spanned chain on the wire: %s", wire);
        assertTrue(wire.contains("{0,4}"), () -> "the span is written: " + wire);
        // READ IS THE WIRE'S OWN READER: a code expression is applied, so the round trip is the parser's code (the
        // text is a program, and reading it as a value would run it).
        checkEquality(LOG, code, ObjmtronSerializer.parse(wire).asCode(), true);
        idem(code);
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// STRICTNESS: NO COEFFICIENT IS LOST OR CORRUPTED ON THE WIRE
    ///
    /// An obj carries two coefficients (span-and-point-coefficient-algebra.md): a POINT, its mass ({c}x on a value,
    /// op{c} on an instruction), and a SPAN, its extent (x{n} on a value, {n}op on an instruction). The writer is
    /// rewritten often, and each time it can drop one without a single test failing: the 11-12 fix restored an
    /// instruction's span, the point stayed dropped, and law_shard's lanes, which reach their peers as text, ran
    /// unpointed for five days. Every row below, as every row this class writes, runs through idem(): every writer read
    /// back by every reader, the read obj EQUAL and node for node the same (nodes(): whole tid, span, point and vid),
    /// and the text a fixpoint. A coefficient lost anywhere in the tree fails at the node it was lost at, even if
    /// equality were ever loosened.
    /// /////////////////////////////////////////////////////////////////////////////////////

    /**
     * AN INSTRUCTION KEEPS EVERY COEFFICIENT ACROSS THE WIRE: a point alone and twice in a chain (the chain the peers
     * answered {@code {3}69} for {@code {9}69}), negative and ranged, on an instruction with no arguments, beside a query
     * and under a span; a span exact, negative, ranged and open; and either inside a branch, a rec value, a lst and a
     * nested code, which is where a lane is held. The written fragment, where a row states one, must appear in the text
     * as written: a point trails the name and precedes the query, the one place the parser reads it back. A ranged
     * coefficient states none, as its spelling is the coefficient's own to choose; the round trip still pins it.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start(1).plus{3}(34)                         % plus{3}(34)",
            "start(1).plus{3}(34).plus{3}(34)             % plus{3}(34)",
            "start(1).plus{-1}(34)                        % plus{-1}(34)",
            "start(1).plus{?}(2)                          %",
            "start(5).id{3}()                             % id{3}()",
            "start(1).plus{3}?hop=0(34)                   % plus{3}?hop=0(34)",
            "start(1).{2}plus(34)                         % {2}",
            "start(5).{-2}plus(1)                         % {-2}",
            "start(0).{0,4}plus(1)                        % {0,4}",
            "start(0).{*}plus(1)                          %",
            "start(1).{2}plus{3}(34)                      % plus{3}(34)",
            "start(1).{2,3}plus{3}(1)                     % plus{3}(1)",
            "start(5).{-1}plus{3}(1)                      % plus{3}(1)",
            "start(5).branch(plus{2}(34),mult{3}(2))      % mult{3}(2)",
            "start([a=>plus{2}(34)])                      % plus{2}(34)",
            "start([plus{2}(34),mult{3}(2)])              % mult{3}(2)",
            "start([a=>start(1).plus(34).plus{3}(34)])    % plus{3}(34)",
            // A SUGAR CARRIES NO COEFFICIENT: *a stays *a, while a spanned or pointed deref is written canonically
            "start(1).*a                                  % *a",
            "start(1).{2}*a                               % from(a)",
            "start(1).from{3}(a)                          % from{3}(a)",
            "start(1).{2}!*a                              %",
            "start(1).auto_from{3}(a)                     %",
            "start(1).{2}!@a                              %",
            "start(1).auto_at{3}(a)                       %",
            "start(1).{2}!a                               %",
            "start(1).{2}from?int{+}<=(a)                 %",
    }, delimiter = '%')
    void testAnInstructionKeepsEveryCoefficientAcrossTheWire(final String source, final String written) {
        final Obj parsed = ObjmtronSerializer.parse(source);
        // A PROGRAM IS A CALL: a one-instruction program (start([a=>...])) parses to the instruction itself, not a code
        assertTrue(parsed.isCall() && !parsed.isNoObj(), () -> "the row parsed as " + parsed + ", not a program: " + source);
        final Code code = parsed.asCode();
        final String wire = ObjmtronSerializer.single().write(code);
        LOG.debug("testing {{b}}%s{{/b}} on the wire as {{b}}%s{{/b}}", source, wire);
        if (null != written)
            assertTrue(wire.contains(written.trim()), () -> "the coefficient is written as " + written.trim() + ": " + wire);
        idem(code);
    }

    /**
     * A VALUE KEEPS EVERY COEFFICIENT ACROSS THE WIRE, through the wire's own writer and reader: a point positive,
     * negative and ranged, a span curled on the value, both at once, on a str and on a uri; a point at a lst slot and a
     * span laid along one, a point in a rec value and on a whole lst; and a type's point, and the slot mass and slot run
     * of a lst type.
     */
    @ParameterizedTest
    // THE DELIMITER IS NOT THE COMMA: a lst row ([{2}3,4]) split on ',' reached the method as the unclosed [{2}3, a parse
    // fail, and a fail round-trips as itself, so the row was green and tested nothing
    @CsvSource(delimiter = '%', quoteCharacter = '`', value = {
            "{3}5",
            "{-1}5",
            "{?}3",
            "5{3}",
            "{2}5{3}",
            "{-1}5{4}",
            "{3}'a'",
            "{2}a/b",
            "[{2}3,4]",
            "[3{2}]",
            "[a=>{3}5]",
            "{2}[1,2]",
            "int{3}::T",
            "lst[{2}int]::T",
            "lst[int{2}]::T",
    })
    void testAValueKeepsEveryCoefficientAcrossTheWire(final String literal) {
        // THE OBJ IS WHAT READING THE ROW GIVES, as ObjmtronSerializerPurityTest reads its rows (1f14fff4a): applying
        // the parse runs a type as a filter on nothing, so the three type rows were noobj and idem compared noobj with
        // noobj. A row that names something must not evaluate to nothing, or to a fail.
        final Obj value = ObjmtronSerializer.single().read(literal);
        assertFalse(value.isFail() || (value.isNoObj() && !ObjmtronSerializer.parse(literal).isNoObj()),
                () -> "the row read as " + value + ", so its round trip tests nothing: " + literal);
        final String wire = ObjmtronSerializer.single().write(value);
        LOG.debug("testing {{b}}%s{{/b}} on the wire as {{b}}%s{{/b}}", literal, wire);
        idem(value);
    }

    /**
     * A LANE KEEPS ITS POINTS ACROSS THE WIRE, built in Java as law_shard builds a layout, never parsed: a keyed branch
     * whose rec holds one code per place, each lane stamped with its budget and carrying a narrowed share
     * ({@code plus{2}}) and the run's other point ({@code plus{3}}). A peer reads exactly this text, so a point the
     * writer drops is a point the peer never runs.
     */
    @Test
    void testALaneKeepsItsPointsAcrossTheWire() {
        final Code lane = code(List.of(
                instB(START_INST_TID.q(HOP, "0"), lst(jnt(1))),
                instB(PLUS_INST_TID.d(cInt.of(2L)), lst(jnt(34))),
                instB(PLUS_INST_TID.d(cInt.of(3L)), lst(jnt(34)))));
        final Map<Obj, Obj> routes = new LinkedHashMap<>();
        routes.put(uri(f("http://localhost:8777")), lane);
        routes.put(uri(f("ws://192.168.1.72:8555")), lane);
        final Code layout = code(List.of(instB(BRANCH_INST_TID, rec(routes))));
        final String wire = ObjmtronSerializer.single().write(layout);
        LOG.debug("a pointed layout on the wire: %s", wire);
        assertTrue(wire.contains("plus{2}(34)") && wire.contains("plus{3}(34)"), () -> "every lane's points are written: " + wire);
        idem(layout);
    }

    /**
     * IDEMPOTENT ON THE WIRE — {@code fURITest.idem}, for objs, run on every row this class writes, the inherited corpus
     * included. Every writer of mtron text ({@code write()}, and {@code toString()}, which the string-keyed paths take)
     * is read back by every reader of its kind (the grammar, and for a type or a value also the wire's own
     * {@code read()}; a call is a program, read back by the grammar and never run). Each read must be the obj, equal
     * and node for node the same ({@link #nodes}), and each writer must write the read obj exactly as it wrote the obj.
     * So by induction any chain of writers and readers, of any length and in any order, is the identity, on the obj
     * and on its text.
     */
    @Override
    protected void idem(final Obj obj) {
        final List<String> expected = nodes(obj);
        final List<Function<Obj, String>> writers = List.of(ObjmtronSerializer.single()::write, Obj::toString);
        // THE READERS BY KIND, each read back in the obj's own kind. A call is a program: the grammar reads it back and
        // nothing runs it, a code as a code (a one-instruction program parses to the bare instruction) and a bare
        // instruction as itself. A type is read as itself, by the grammar and by the wire's read(); applying its parse
        // would run it as a filter on nothing and answer noobj. Any other value is the applied parse and the wire's read().
        final List<Function<String, Obj>> readers;
        if (obj.isCode())
            readers = List.of(text -> ObjmtronSerializer.parse(text).asCode());
        else if (obj.isCall())
            readers = List.of(ObjmtronSerializer::parse);
        else if (obj.isType())
            readers = List.of(ObjmtronSerializer::parse, ObjmtronSerializer.single()::read);
        else
            readers = List.of(text -> ObjmtronSerializer.parse(text).apply(), ObjmtronSerializer.single()::read);
        for (final Function<Obj, String> writer : writers) {
            final String text = writer.apply(obj);
            for (final Function<String, Obj> reader : readers) {
                final Obj back = reader.apply(text);
                checkEquality(LOG, obj, back, true);
                assertEquals(expected, nodes(back), () -> "every node keeps its tid, span, point and vid: " + text);
                for (final Function<Obj, String> rewriter : writers)
                    assertEquals(rewriter.apply(obj), rewriter.apply(back), () -> "the text is a fixpoint: " + text);
            }
        }
    }

    /**
     * EVERY NODE AS THE WIRE MUST KEEP IT: its whole tid (base path, coefficients, query and a type's own brackets), its
     * span and point, and its vid, depth first through a code's instructions, an instruction's arguments, a rec's keys
     * and values, a lst's entries and a stream's members.
     */
    private static List<String> nodes(final Obj obj) {
        final List<String> nodes = new ArrayList<>();
        nodes(obj, nodes);
        return nodes;
    }

    private static void nodes(final Obj obj, final List<String> nodes) {
        if (obj.isNoObj()) {
            nodes.add("noobj");
            return;
        }
        // THE WHOLE TID, not its base path: basePath() drops a type's own brackets, so a lst type's slot mass
        // (lst[{2}int]) and slot run (lst[int{2}]) fingerprinted alike, as a bare lst
        nodes.add((null == obj.tid() ? "tid=null" : obj.tid() + "{b=" + obj.tid().b() + ",d=" + obj.tid().d() + "}")
                + (obj.hasVID() ? "@" + obj.vid() : ""));
        if (obj.isCode())
            obj.asCode().insts().forEach(inst -> nodes(inst, nodes));
        else if (obj.isInst())
            nodes(obj.asInst().args(), nodes);
        else if (obj.isRec())
            obj.asRec().<Rel>elements().forEach(kv -> {
                nodes(kv.first(), nodes);
                nodes(kv.second(), nodes);
            });
        else if (obj.isLst())
            obj.asLst().<Obj>elements().forEach(entry -> nodes(entry, nodes));
        else if (obj.isObjs())
            obj.stream().forEach(member -> nodes(member, nodes));
    }
}
