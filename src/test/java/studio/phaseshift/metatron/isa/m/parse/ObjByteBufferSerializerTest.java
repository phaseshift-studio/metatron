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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class ObjByteBufferSerializerTest extends AbstractSerializerTest<ByteBuffer> {

   public ObjByteBufferSerializerTest() {
       super(new ObjByteBufferSerializer(), null, null);
   }

    /**
     * A PROGRAM READS BACK AS ITSELF. An instruction's head is written as the mtron serializer writes it (span, name,
     * point, typing, query), a keyed argument as {@code <name>=>value}, and every value as this serializer writes values;
     * a code is its bare chain. The head was the whole tid, {@code <tid>(…)}, which did not parse once a tid carried its
     * {@code args=[…]} signature, so no instruction with one read back, nor any code holding one. A one-instruction
     * program parses to the instruction itself, which is written as one. An argument is a value in this serializer's own
     * spelling ({@link #testAValueReadsBackAsItself}).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            // the program that did not read back, a longer chain, and a chain with no start
            "start(1).plus(2)",
            "start(1).plus(2).mult(7)",
            "plus(2).mult(7)",
            // an instruction alone, and the unit, whose args are empty
            "plus(2)",
            "id()",
            "id{3}()",
            // a point twice in a chain, negative and ranged; a span exact, ranged and open; both; a point beside a query
            "start(1).plus{3}(34).plus{3}(34)",
            "start(1).plus{-1}(34)",
            "start(1).plus{?}(2)",
            "start(1).{2}plus(34)",
            "start(0).{0,4}plus(1)",
            "start(0).{*}plus(1)",
            "start(1).{2}plus{3}(34)",
            "start(1).plus{3}?hop=0(34)",
            // a typed instruction, typed as the compiler types a chain (a zero dom), and a typed deref under a span
            "start(1).plus?int<=int(2)",
            "start?int<={0}(1).plus?int<=int(2)",
            "start(1).{2}from?int{+}<=(a)",
            // each kind of value as an argument
            "start('a').plus('b')",
            "start(2.5).mult(2.0)",
            "start(true).and(false)",
            "start(<a b/c>)",
            "start(0x0a0b)",
            "start(noobj)",
            "start([1,2]).count()",
            "start([,])",
            "start([=>])",
            "start({1,2,3}).plus(1)",
            "start(1).as(real::T)",
            // a spanned value and a str holding a quote as arguments: start's arguments and keyed ones keep a span curled,
            // while a positional argument lays it (plus(2{3}) is plus(2,2,2)), so the laid str is two strs
            "start(2{3})",
            "start(1).plus(a=>2{3})",
            "start(\"it's\")",
            "start(a=>\"it's\",b=>'trailing\\\\')",
            "start('a').plus(\"it's\"{2})",
            // keyed arguments: a value, two, a lane whose name the mtron writer brackets, and a positional rel beside them
            "start(a=>b)",
            "start(a=>1,b=>'x')",
            "start(5).branch(<ws://127.0.0.1:8555>=>plus(1))",
            "start((a=>b))",
            // instructions and codes as arguments: nested, branched, keyed, in a lst and a rec, a cut's tail, a reply
            "start(1).is(gt(0))",
            "start(5).branch(plus{2}(34),mult{3}(2))",
            "start(5).branch(<//worker>=>plus(1))",
            "start(5).branch(<//worker>=>plus(1),<//archive>=>mult(2).plus{3}(1))",
            "start(5).branch(<//worker>=>branch(<//shard>=>plus{2}(1)))",
            "start([plus{2}(34),mult{3}(2)])",
            "start([a=>start(1).plus(34).plus{3}(34)])",
            // a code in every other slot: a positional argument, a lst entry and a rel's right side
            "start(5).branch(plus(1).mult{2}(2),id())",
            "start([plus(1).mult(2),3])",
            "start((a=>plus(1).mult(2)))",
            "start(5).cut(<//worker>,plus(1))",
            "/m/mach/inst/reply(id(),<0x91cc5da9>)",
            // a body, a sugar bare and spanned, and an addressed instruction, alone and pointed in a chain
            "start(1).plus(1){7}",
            "start(1).*a",
            "start(1).{2}*a",
            "start(1)@/a/b",
            "start(1).plus{3}(2)@/a/b",
    })
    void testAProgramReadsBackAsItself(final String source) {
        final Obj program = ObjmtronSerializer.parse(source);
        // A ROW THAT IS NOT A PROGRAM TESTS NO INSTRUCTION: a row that parsed to noobj would round-trip as itself
        assertTrue(program.isCall() && !program.isNoObj(), () -> "the row parsed as " + program + ", not a program: " + source);
        this.idem(program);
    }

    /**
     * A VALUE READS BACK AS ITSELF. A scalar is {@code {point}value{span}}: its span trails it, where m_int, m_real,
     * m_str, m_bool and m_bytes read it (ONE when nothing trails), so the span the tid prefix carried, {@code
     * <{3}/m/int>::2}, read back as {@code 2}. A named type's scalar trails its span too. A uri keeps its span in the
     * prefix, and no row holds one: with type_pred on, as here, a uri read with a span on its tid fails its type check
     * ({@code <{*}/m/uri>::<a/b>} is not a {@code {*}uri::T}). A str is quoted as the mtron serializer quotes it, its
     * delimiter one the value does not hold and an escape where none is safe: wrapped in {@code '} and escaped nothing,
     * an apostrophe or a closing backslash ended it early. A lst lays a span out ({@code [3{2}]} is {@code [3,3]}), so a
     * spanned value stands in a rec or a rel here, which keep it.
     */
    @ParameterizedTest
    @TestData(value = {"int::T[is(gt(0))]@nat"})
    @CsvSource(delimiter = '%', quoteCharacter = '`', value = {
            // a span on each kind whose parser reads one, beside a point, before a vid, and inside a rec and a rel
            "2{3}",
            "-7{2}",
            "{2}5{3}",
            "2.5{3}",
            "'a'{3}",
            "true{2}",
            "0x0a0b{2}",
            "2{3}@/a/b",
            "[a=>2{3},b=>'c'{2}]",
            "(a=>2{3})",
            // a named type's span, alone and beside its point
            "nat::5{3}",
            "nat{2}::5{3}",
            // an apostrophe, both quote kinds (triple quoted, then ending on a quote), a closing backslash, an escape
            "\"it's\"",
            "\"\"\"it's a \"quoted\" word\"\"\"",
            "'it\\'s \"x\"'",
            "'trailing\\\\'",
            "'a\\\\\\'b'",
            // a quote and a span, a quote before a vid, and quotes as a rec's key and a lst's entry
            "\"it's\"{2}",
            "\"it's\"@/a/b",
            "[\"it's\"=>'say \"hi\"']",
            "[\"it's\",'trailing\\\\']",
    })
    void testAValueReadsBackAsItself(final String literal) {
        final Obj value = ObjmtronSerializer.parse(literal);
        // A ROW THAT IS NOT A VALUE TESTS NO VALUE: noobj and a fail round-trip as themselves
        assertTrue(!value.isFail() && !value.isNoObj() && !value.isCall(), () -> "the row parsed as " + value + ", not a value: " + literal);
        this.idem(value);
    }

    /**
     * A FAIL READS BACK AS ITSELF, every level of it. It is written as the mtron serializer writes it, {@code
     * fail::[level][cause]…@vid} with each level's message a str, which m_fail reads: the tid prefix, {@code
     * </m/fail>::}, is no spelling m_fail reads, so a fail read back as a parse error, its brackets a lst typed fail
     * ("[arraylist] is not a fail::T") and its message and its cause one raw str joined by a comma. That writer leaves
     * out a level whose message the level above already quotes ("inst apply failure: the dial timed out" caused by "the
     * dial timed out"), so such a fail reads back without it, and a caught fail reads back uncaught (m_fail has no
     * caught spelling): no row holds either. A row is the fail's messages, outermost first, split at {@code |}.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            // one level: prose, the grammar's own syntax in it, an apostrophe, and a comma (the old level separator)
            "the dial timed out",
            "inst apply failure: a [value] unable to convert uri::T (at /m/inst/plus)",
            "it's not a peer",
            "no peer answered, the trip timed out",
            // two levels and three, each its own bracket
            "no peer answered | the dial timed out",
            "it's \"unreachable\" | the dial timed out",
            "no peer answered | the dial timed out | the socket closed",
    })
    void testAFailReadsBackAsItself(final String row) {
        final List<String> messages = Arrays.stream(row.split("\\|")).map(String::strip).toList();
        // built as m_fail builds a read fail: the root first, each level above it wrapping the chain so far (a sequential
        // stream never combines)
        final MTronException chain = messages.reversed().stream().skip(1).reduce(MTronException.of(messages.getLast()),
                (cause, message) -> MTronException.of(cause, message), (inner, outer) -> outer);
        final Fail failed = fail(chain);
        // A ROW THAT IS NOT THE FAIL IT NAMES TESTS NO FAIL: its levels are the row's
        assertEquals(messages, levels(failed), () -> "the row's fail is " + failed);
        this.idem(failed);
    }

    /**
     * THIS SERIALIZER'S CLOSURE, run on every row it round-trips, the inherited corpus included: what it writes it reads
     * back as the obj, equal and node for node the same ({@link #nodes}: whole tid, span, point and vid), and writes again
     * as the same text. A code is compared as a code: a one-instruction program parses to the bare instruction. A fail
     * reads back as a fail with every level's message: a fail's equality reads one cause deep.
     */
    @Override
    protected void idem(final Obj obj) {
        final String text = new String(this.serializer.write(obj).array());
        final Obj read = this.serializer.read(ByteBuffer.wrap(text.getBytes()));
        LOG.debug("testing {{b}}%s{{/b}} written as {{b}}%s{{/b}}", obj, text);
        assertFalse(read.isFail() && !obj.isFail(), () -> "the text did not read back: " + text + " => " + read);
        final Obj back = obj.isCode() ? read.asCode() : read;
        checkEquality(LOG, obj, back, true);
        if (obj.isFail())
            assertEquals(levels(obj.asFail()), levels(back.asFail()), () -> "every level of the fail reads back: " + text);
        assertEquals(nodes(obj), nodes(back), () -> "every node keeps its tid, span, point and vid: " + text);
        assertEquals(text, new String(this.serializer.write(back).array()), () -> "the text is a fixpoint: " + text);
    }

    /**
     * A fail's messages, outermost first: its own, then each cause's.
     */
    private static List<String> levels(final Fail fail) {
        return Stream.iterate(fail, Objects::nonNull, level -> level.cause().orElse(null)).map(Fail::message).toList();
    }
}
