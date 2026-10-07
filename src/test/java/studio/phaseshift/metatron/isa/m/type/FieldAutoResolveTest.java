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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * A rec field that <em>acts</em> rather than <em>holds</em>: access alone can evaluate a stored inst.
 * <p>
 * {@code Obj.autoResolve} ({@code Obj.java:517}) collects two kinds of inst for that treatment — one by
 * construction, one by type:
 * <ol>
 *   <li><b>the auto wrap</b> — {@code !inst?(){…}}, whose tid is {@code auto} (or {@code auto_from}/{@code auto_at});</li>
 *   <li><b>being initial</b> — {@code inst?<={0}(){…}}, whose dom coefficient is zero. {@code Inst.isInitial()} is
 *       exactly {@code dom().c().isZero()}: an inst that needs <em>nothing</em> to evaluate has nothing to wait
 *       for, so looking at it is enough to run it. The type gives the permission away.</li>
 * </ol>
 * The auto wrap is verified here in both spellings. The initial form is <b>not</b> asserted — see below.
 * <p>
 * The negative control is the point of the last case: a plain inst with a non-zero domain is a <b>value</b>. It
 * sits in the field inert, and a read hands it back unevaluated — the failure that reads as "my method is
 * broken" when the method is perfectly well stored.
 *
 * <h2>There is no {@code this}</h2>
 * The two mechanisms are not two spellings of one thing — they differ in <b>arity</b>, and that is the whole
 * distinction:
 * <ul>
 *   <li>An <b>initial</b> inst is <em>nullary</em>. Its receiver is {@code noobj} — there is no {@code this}, and
 *       in particular it is <em>not</em> rooted to the poly that contains it. It is a computed value that happens
 *       to be computed on access.</li>
 *   <li>The <b>{@code !} auto wrap</b> applies the lhs, which is where Java-style {@code this} behaviour comes
 *       from. If a field's body needs to see its container, this is the one to use — the receiver arrives and is
 *       read with {@code _}.</li>
 * </ul>
 * So an initial inst handed a rec fails its own domain check
 * ({@code lhs range does not match inst domain: rec::T =&gt; noobj}) — not a defect but the type system holding
 * the line on arity. Ask for a receiver and the answer is no; ask for {@code !} and it is applied.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FieldAutoResolveTest extends AbstractMetatronTest {

    private static final String FIELD = "/sys/space/probe/autofield/rec";

    /**
     * the auto-wrapped field is a method under invocation
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[status=>!inst?(){42}]     % 42",
    }, delimiter = '%')
    void testAutoWrappedFieldIsInvokedAsAMethod(final String recSource, final String expected) {
        Machine.write(f(FIELD), eval(recSource));
        assertEquals(expected, String.valueOf(eval("*" + FIELD + ".status()")),
                "invoking a self-evaluating field should evaluate it");
    }

    /**
     * and it resolves on a plain path read too — the auto wrap does not care which spelling you use
     */
    @Test
    public void testAutoWrappedFieldAlsoResolvesOnAPathRead() {
        Machine.write(f(FIELD), eval("[status=>!inst?(){42}]"));
        assertEquals("42", String.valueOf(eval("*" + FIELD + "/status")),
                "an auto-wrapped field should resolve on a path read");
    }

    /**
     * the {@code this} behaviour, which only the auto wrap provides: the field's body reads its container
     * through {@code _}, which is the lhs arriving as a receiver
     */
    @Test
    public void testAutoWrappedFieldReceivesItsLhsAsThis() {
        Machine.write(f(FIELD), eval("[n=>5,f=>!inst?(){ _>>n }]"));
        assertEquals("5", String.valueOf(eval("*" + FIELD + ".f()")),
                "the auto wrap applies the lhs, so the body can read its container");
    }

    /**
     * the control: without a zero domain and without the auto wrap, the field is data and stays data
     */
    @Test
    public void testPlainInstFieldIsAValueNotAMethod() {
        Machine.write(f(FIELD), eval("[status=>inst?(){42}]"));
        final Obj read = eval("*" + FIELD + "/status");
        assertTrue(read.isInst(), "a plain inst field must come back unevaluated, got " + read);
        assertNotEquals("42", String.valueOf(read), "a plain inst field must not have been applied on access");
    }

    private static Obj eval(final String code) {
        return ObjmtronSerializer.parse(code).apply();
    }
}
