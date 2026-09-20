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

package studio.phaseshift.metatron.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.sys.type.ExecutionStack;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MTronExceptionTest extends AbstractMetatronTest {

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(value = {
            "hello world                    | hello world                       | plain text passthrough",
            "text with literal %s           | text with literal %s              | bare %s format specifier",
            "result: %s and %d              | result: %s and %d                 | multiple format specifiers",
            "search results with %%s code   | search results with %s code       | escaped percent in text",
            "no format specifiers here      | no format specifiers here         | no format specifiers",
    }, delimiter = '|')
    void testOfWithNoArgs(final String input, final String expected, final String desc) {
        final MTronException e = assertDoesNotThrow(
                () -> MTronException.of((Object) input),
                () -> "of(Object) should not throw on: " + desc);
        assertEquals(expected, e.getMessage(), "message should match input for: " + desc);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            "hello %s world | hello    | hello hello world             | standard %s substitution",
            "count: %s      | 42       | count: 42                     | %s with string arg",
    }, delimiter = '|')
    void testOfWithArgs(final String format, final String arg, final String expected, final String desc) {
        final MTronException e = assertDoesNotThrow(
                () -> MTronException.of(format, arg),
                () -> "of(format, arg) should not throw on: " + desc);
        assertEquals(expected, e.getMessage(), "formatted message mismatch for: " + desc);
    }

    // ==================================================================
    // tracer emission — one trace per failure chain
    // ==================================================================

    private boolean mtronStackWasOn;
    private boolean javaStackWasOn;

    @BeforeEach
    public void rememberTracerState() {
        this.mtronStackWasOn = Tracer.mtron_stack.enabled();
        this.javaStackWasOn = Tracer.java_stack.enabled();
        ExecutionStack.clear();
    }

    @AfterEach
    public void restoreTracerState() {
        if (this.mtronStackWasOn) Tracer.enable(Tracer.mtron_stack);
        else Tracer.disable(Tracer.mtron_stack);
        if (this.javaStackWasOn) Tracer.enable(Tracer.java_stack);
        else Tracer.disable(Tracer.java_stack);
        ExecutionStack.clear();
    }

    @Test
    public void testTraceCapturedWhileFramesInFlight() {
        Tracer.enable(Tracer.mtron_stack);
        final int before = MTronException.mtronTracesEmitted();
        ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "/m/inst/as?rng=/m/lst&dom=/m/int"));
        ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst_args, "/m/inst/is?rng=/m/uri{?}&dom=/m/uri{?}"));
        final MTronException deep = MTronException.of("conversion failed");
        final String expected = String.join("\n",
                "\\_resolve_inst_args: /m/inst/is?rng=/m/uri{?}&dom=/m/uri{?}",
                "    \\_apply_inst: /m/inst/as?rng=/m/lst&dom=/m/int");
        assertEquals(expected, deep.mtronTrace());
        assertEquals(before, MTronException.mtronTracesEmitted(), "emission waits for the report");
    }

    @Test
    public void testBlankStackEmitsNothing() {
        Tracer.enable(Tracer.mtron_stack);
        final int before = MTronException.mtronTracesEmitted();
        final MTronException e = MTronException.of("no frames in flight");
        assertNull(e.mtronTrace());
        MTronException.emitStackTrace(e);   // reporting a frameless failure must not print a bare "[Tracer]" line
        assertEquals(before, MTronException.mtronTracesEmitted());
    }

    @Test
    public void testRewrappedChainEmitsOnceWithDeepestCapture() {
        Tracer.enable(Tracer.mtron_stack);
        final int before = MTronException.mtronTracesEmitted();
        ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "/m/inst/as?rng=/m/lst&dom=/m/int"));
        final MTronException deep = MTronException.of("3 is not a lst::T");
        ExecutionStack.clear();   // outer wraps are born after the frame unwound
        final MTronException wrap = MTronException.of(deep, "inst apply failure");
        final MTronException outer = MTronException.of(wrap, "inst apply failure");
        assertSame(deep, wrap.getCause());
        assertSame(wrap, outer.getCause());
        assertTrue(outer.getMessage().startsWith("inst apply failure"), "outer keeps the funnel prefix, got: " + outer.getMessage());
        assertEquals("3 is not a lst::T", deep.getMessage());
        assertEquals(before, MTronException.mtronTracesEmitted(), "capture is silent; emission waits for the report");
        MTronException.emitStackTrace(outer);
        MTronException.emitStackTrace(outer);   // re-reporting the same chain stays quiet
        assertEquals(before + 1, MTronException.mtronTracesEmitted());
        assertEquals("\\_apply_inst: /m/inst/as?rng=/m/lst&dom=/m/int", MTronException.lastMtronTrace());
    }

    @Test
    public void testOfCauseLinksInnerAndEmitsOnce() {
        Tracer.enable(Tracer.mtron_stack);
        final int before = MTronException.mtronTracesEmitted();
        ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "/m/inst/as?rng=/m/web/mcp/mcp_client&dom=/m/rec"));
        ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "/m/inst/ctor?rng=/m/web/mcp/mcp_client&dom=#{?}"));
        final MTronException deep = MTronException.of("unsupported transport");
        ExecutionStack.clear();
        final MTronException ctorWrap = MTronException.of(deep, "inst apply failure");
        final MTronException asWrap = MTronException.of(ctorWrap, "inst apply failure");
        assertTrue(asWrap.getMessage().startsWith("inst apply failure"), "got: " + asWrap.getMessage());
        assertSame(deep, ctorWrap.getCause());
        assertSame(ctorWrap, asWrap.getCause());
        MTronException.emitStackTrace(asWrap);
        MTronException.emitStackTrace(asWrap);
        assertEquals(before + 1, MTronException.mtronTracesEmitted());
        final String expected = String.join("\n",
                "\\_apply_inst: /m/inst/ctor?rng=/m/web/mcp/mcp_client&dom=#{?}",
                "    \\_apply_inst: /m/inst/as?rng=/m/web/mcp/mcp_client&dom=/m/rec");
        assertEquals(expected, MTronException.lastMtronTrace());
    }

    @Test
    public void testOfThrowableEmitsOnce() {
        Tracer.enable(Tracer.mtron_stack);
        final int before = MTronException.mtronTracesEmitted();
        ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "/m/inst/as?rng=/m/lst&dom=/m/int"));
        final MTronException converted = MTronException.of(new RuntimeException("raw failure", new ClassCastException("int cannot be cast to class lst")));
        assertNotNull(converted.getCause());
        ExecutionStack.clear();
        final MTronException again = MTronException.of(converted);           // early-return path
        final MTronException rewrapped = MTronException.of(converted, "inst apply failure: %s", converted);
        assertSame(converted, again);
        assertSame(converted, rewrapped.getCause());
        assertEquals(before, MTronException.mtronTracesEmitted(), "no emission before the report");
        MTronException.emitStackTrace(rewrapped);
        MTronException.emitStackTrace(rewrapped);
        assertEquals(before + 1, MTronException.mtronTracesEmitted());
    }

    @Test
    public void testJavaStackEmitsOncePerChain() {
        Tracer.enable(Tracer.java_stack);
        final int before = MTronException.javaTracesEmitted();
        final MTronException deep = MTronException.of("deep failure");
        final MTronException wrap = MTronException.of(deep, "inst apply failure");
        assertSame(deep, wrap.getCause());
        assertEquals(before, MTronException.javaTracesEmitted(), "emission waits for the report");
        MTronException.emitStackTrace(wrap);
        MTronException.emitStackTrace(wrap);
        assertEquals(before + 1, MTronException.javaTracesEmitted());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "3.as(lst::T)",
            "1.plus(ab)",
    }, delimiter = '%')
    public void testFailingExpressionsStillFail(final String code) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, "<ERROR>");
    }

    @Test
    public void testReportedFailureEmitsExactlyOneTrace() {
        Tracer.enable(Tracer.mtron_stack);
        final int before = MTronException.mtronTracesEmitted();
        final Obj result;
        try {
            result = ObjmtronSerializer.parse("3.as(lst::T)").apply(noobj());
        } catch (final MTronException e) {
            MTronException.emitStackTrace(e);   // an uncaught escape is itself the report
            assertEquals(before + 1, MTronException.mtronTracesEmitted());
            assertEquals("\\_apply_inst: /m/inst/as?rng=/m/lst&dom=/m/int", MTronException.lastMtronTrace());
            return;
        }
        if (!result.isFail())
            throw new AssertionError("expected a fail, got " + result);
        final String rendered = ObjmtronSerializer.single().write(result);   // serialization = the report (console result line)
        if (!rendered.contains("3 is not a lst"))
            throw new AssertionError("fail lost its message: " + rendered);
        assertEquals(before + 1, MTronException.mtronTracesEmitted(), "one logical failure → one mtron stack trace");
        assertEquals("\\_apply_inst: /m/inst/as?rng=/m/lst&dom=/m/int", MTronException.lastMtronTrace());
    }

    @Test
    public void testRetriedPipelineReportsOneTrace() {
        Tracer.enable(Tracer.mtron_stack);
        final int before = MTronException.mtronTracesEmitted();
        try {
            final Obj result = ObjmtronSerializer.parse("1+ab").apply(noobj());
            if (result.isFail())
                ObjmtronSerializer.single().write(result);   // serialize the reported failure
        } catch (final MTronException e) {
            MTronException.emitStackTrace(e);
        }
        assertEquals(before + 1, MTronException.mtronTracesEmitted(),
                "one reported failure → one trace, however many retries died before it");
    }

}
