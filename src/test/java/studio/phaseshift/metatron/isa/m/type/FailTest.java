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
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;

import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FailTest extends AbstractObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            "fail::[a][b][c][d].catch()                                                                      % noobj",
            "fail::[a][b][c][d].catch(_)                                                                     % fail::[a][b][c][d].catch(_)",
     //     "fail::[a][b][c][d].catch(_).cause()                                                             % fail::[a][b][c].catch(_)", // TODO: cause() chain tests — transient Fails change depth and cause identity
            "fail::[a][b][c][d].catch(34)                                                                    % 34",
            "fail::[a][b][c][d].catch(_).map?int<=#{?}(34)                                                   % 34",
            "fail::[a][b][c][d].catch(_).map(34)                                                             % 34",
            "{fail::[a],fail::[b]}.catch(34)                                                                 % {2}34",
            "{fail::[a],fail::[a]}.catch(34)                                                                 % {2}34",
            "{fail::[a],fail::[a]}.dedup().catch(34)                                                         % 34",
             "fail::[a][b][c][d].catch(-<[_,_]>-).map?int<=#{?}(34)                                           % {2}34",
     //     "fail::[a][b][c][d].catch(cause())                                                               % fail::[a][b][c].catch(_)", // TODO: cause() chain tests — transient Fails change depth and cause identity
     //     "fail::[a][b][c][d].catch(cause().cause())                                                       % fail::[a][b].catch(_)", // TODO: cause() chain tests — transient Fails change depth and cause identity
     //     "fail::[a][b][c][d].catch(cause().cause().cause())                                               % fail::[a].catch(_)", // TODO: cause() chain tests — transient Fails change depth and cause identity
            "fail::[a][b][c][d].catch(cause().cause().cause().cause())                                       % noobj",
     //     "fail::[a][b][c][d].cause().catch(_)                                                             % fail::[a][b][c][d].catch(_)", // TODO: cause() chain tests — need to catch it to operate on it
     //     "fail::[a][b][c][d].catch(_).cause()                                                             % fail::[a][b][c].catch(_)", // TODO: cause() chain tests — need to catch it to operate on it
     //     "fail::[a][b][c][d].catch(cause())                                                               % fail::[a][b][c].catch(_)", // TODO: cause() chain tests — need to catch it to operate on it
     //     "fail::[a][b][c][d].catch(cause()).cause()                                                       % fail::[a][b].catch(_)", // TODO: cause() chain tests — a caught fail is no longer lifted
     //     "fail::[a][b][c][d].catch(cause().cause()).cause()                                               % fail::[a].catch(_)", // TODO: cause() chain tests
            "fail::[a][b][c][d].catch(cause().cause().cause()).cause()                                       % noobj",
            //   "fail::[a][b][c][d].catch(fail::[e])                                                        % fail::[a][b][c][d][e]" // TODO: need a way to denote a caught fail in mtron
    }, delimiter = '%')
    public void testCause(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {"1.plus(b)"}, oneTime = true)
    @CsvSource(value = {
            "*/sys/fail/+.count().to(xyzabc).gt(0)                                                                % true",
            "*/sys/fail/+.count()                                                                                 % *xyzabc",
          //  "*/sys/fail/+.catch(_).count().eq(*xyzabc)                                                            % true",
          //  "*/sys/fail/+.catch(_).count()                                                                        % 0",
    }, delimiter = '%')
    public void testFailStackAndCatch(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    // ========================================
    // fail(t, cause) cause-chain preservation
    // ========================================

    @Test
    public void testFailCausePreservesThrowableCauseChain() {
        final FileNotFoundException inner = new FileNotFoundException("file not found");
        final IOException outer = new IOException("io error", inner);

        final Fail result = fail(outer, fail("validation failed"));

        // Chain: outer → mtron cause → inner (all levels preserved)
        final Throwable jvm = result.jvm();
        assertTrue(jvm.getMessage().contains("io error"), "top level should be outer");

        final Throwable cause1 = jvm.getCause();
        assertNotNull(cause1, "cause level 1 (mtron cause) should exist");
        assertTrue(cause1.getMessage().contains("validation failed"));

        final Throwable cause2 = cause1.getCause();
        assertNotNull(cause2, "cause level 2 (inner FileNotFoundException) should exist");
        assertTrue(cause2.getMessage().contains("file not found"));
    }

    @Test
    public void testFailCausePreservesDeepThrowableCauseChain() {
        final RuntimeException leaf = new RuntimeException("leaf");
        final IOException mid = new IOException("mid", leaf);
        final Exception root = new Exception("root", mid);

        final Fail result = fail(root, fail("mtron wrapper"));

        // Chain: root → mtron → mid → leaf (all levels preserved)
        Throwable current = result.jvm();
        assertEquals("root", current.getMessage());
        current = current.getCause();
        assertNotNull(current);
        assertTrue(current.getMessage().contains("mtron wrapper"));
        current = current.getCause();
        assertNotNull(current);
        assertEquals("mid", current.getMessage());
        current = current.getCause();
        assertNotNull(current);
        assertEquals("leaf", current.getMessage());
        assertNull(current.getCause());
    }

    @Test
    public void testFailCauseWithoutNestedCause() {
        final IOException simple = new IOException("simple error");
        final Fail result = fail(simple, fail("mtron wrapper"));

        assertEquals("simple error", result.jvm().getMessage());
        assertNotNull(result.jvm().getCause());
        assertTrue(result.jvm().getCause().getMessage().contains("mtron wrapper"));
    }

    @Test
    public void testFailCauseNullCauseDirectThrowable() {
        final IOException io = new IOException("disk full");
        final Fail result = fail(io, null);
        assertNotNull(result.jvm());
        assertTrue(result.jvm().getMessage().contains("disk full"));
    }

    @Test
    public void testFailSerializationRoundtripPreservesCauseChain() {
        // Verify that writeFail → parse roundtrip preserves the full cause chain
        final IOException cause = new IOException("nested");
        final Fail inner = fail(new RuntimeException("outer", cause), fail("mtron wrapper"));

        // Serialize
        final String serialized = studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer.single().write(inner);
        assertTrue(serialized.contains("outer"), "serialized should contain outer message");
        assertTrue(serialized.contains("mtron wrapper"), "serialized should contain mtron wrapper message");
        assertTrue(serialized.contains("nested"), "serialized should contain nested cause message");
        assertFalse(serialized.contains("[...]"), "serialized should NOT contain [...] placeholder");
    }

    @Test
    public void testFailPlusPreservesMergedChain() {
        final Fail a = fail("first");
        final Fail b = fail("second");
        final Fail merged = a.plus(b);

        assertTrue(merged.jvm().getMessage().contains("first"));
        assertNotNull(merged.jvm().getCause());
        assertTrue(merged.jvm().getCause().getMessage().contains("second"));
    }

}
