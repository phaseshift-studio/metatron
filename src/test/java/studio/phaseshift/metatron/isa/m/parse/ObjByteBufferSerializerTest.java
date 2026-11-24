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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * spelling, whose gaps are its value writers' (a value's span, a str holding a quote, a fail): no row holds one.
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
     * THIS SERIALIZER'S CLOSURE, run on every row it round-trips, the inherited corpus included: what it writes it reads
     * back as the obj, equal and node for node the same ({@link #nodes}: whole tid, span, point and vid), and writes again
     * as the same text. A code is compared as a code: a one-instruction program parses to the bare instruction.
     */
    @Override
    protected void idem(final Obj obj) {
        final String text = new String(this.serializer.write(obj).array());
        final Obj read = this.serializer.read(ByteBuffer.wrap(text.getBytes()));
        LOG.debug("testing {{b}}%s{{/b}} written as {{b}}%s{{/b}}", obj, text);
        assertFalse(read.isFail(), () -> "the text did not read back: " + text + " => " + read);
        final Obj back = obj.isCode() ? read.asCode() : read;
        checkEquality(LOG, obj, back, true);
        assertEquals(nodes(obj), nodes(back), () -> "every node keeps its tid, span, point and vid: " + text);
        assertEquals(text, new String(this.serializer.write(back).array()), () -> "the text is a fixpoint: " + text);
    }
}
