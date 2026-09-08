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

package studio.phaseshift.metatron.docs;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MtronPreprocessorTest extends AbstractMetatronTest {

    @Test
    public void testMaxOutputDirective() {
        MtronPreprocessor preprocessor = new MtronPreprocessor(MtronPreprocessor.ADOC_HEADER);
        final String output = preprocessor.process("""
                                                   [mtron]
                                                   ----
                                                   [MAXOUTPUT 5] \"\"\"abc1 /
                                                   abc2 /
                                                   abc3 /
                                                   abc4 /
                                                   abc5 /
                                                   abc6 /
                                                   abc7\"\"\"
                                                   ----
                                                   """);

        for (int i = 1; i < 8; i++) {
            assertTrue(output.contains("abc" + i));
        }
        assertTrue(output.contains("..."));
        int resultStart = output.indexOf("==>");
        assertTrue(resultStart > 0);
        for (int i = 1; i < 6; i++) {
            assertTrue(output.substring(resultStart).contains("abc" + i));
        }
        for (int i = 6; i < 8; i++) {
            assertFalse(output.substring(resultStart).contains("abc" + i));
        }
    }

    /// Run one adoc [mtron] block body through MtronPreprocessor.
    private static String process(final String... bodyLines) {
        return new MtronPreprocessor(MtronPreprocessor.ADOC_HEADER)
                .process("[mtron]\n----\n" + String.join("\n", bodyLines) + "\n----");
    }

    /// [ERROR] (and friends) are statement-scoped: [NO_OUTPUT] on the first line of a
    /// multi-line statement must suppress the results of that statement only.
    @Test
    public void testNoOutputDirectiveMultiLineStatement() {
        final String output = process(
                "[NO_OUTPUT] \"\"\"whisper one/",
                "whisper two\"\"\"",
                "\"loud value\"");
        assertTrue(output.contains("whisper one"));    // input echoes in full
        assertTrue(output.contains("whisper two"));
        assertFalse(output.contains("==>\"\"\"whisper"));   // results suppressed
        assertTrue(output.contains("..."));           // [NO_OUTPUT] placeholder
        assertTrue(output.contains("loud value"));    // next statement unaffected
        assertTrue(output.contains("==>'loud value'"));
    }

    /// [NO_PROMPT] suppresses the "mtron> " prefix for the whole multi-line statement.
    @Test
    public void testNoPromptDirectiveMultiLineStatement() {
        final String output = process(
                "[NO_PROMPT] \"\"\"quiet one/",
                "quiet two\"\"\"",
                "\"prompted value\"");
        assertTrue(output.contains("quiet one"));
        assertTrue(output.contains("quiet two"));
        assertFalse(output.contains("mtron> \"\"\"quiet"));   // no prompt prefix
        assertTrue(output.contains("mtron> \"prompted value\""));  // prefix returns next statement
    }

    /// [HIDDEN] hides the entire multi-line statement (input and output).
    @Test
    public void testHiddenDirectiveMultiLineStatement() {
        final String output = process(
                "[HIDDEN] \"\"\"ghost one/",
                "ghost two\"\"\"",
                "\"visible value\"");
        assertFalse(output.contains("ghost"));
        assertTrue(output.contains("visible value"));
    }

    /// A failing statement without [ERROR] must log the "docs are buggy" warning.
    @Test
    public void testFailingStatementWithoutErrorDirectiveLogs() {
        final String output = processWithErrorCapture("1 + a");
        assertTrue(hasDocsBuggyWarning(), "failing statement without [ERROR] must log the docs-buggy warning");
        LOG.warn("{{r}}THE ABOVE ERROR IS EXPECTED -- TESTING DOC PROCESSOR LOGGING");
    }

    /// [ERROR] on the first line of a multi-line statement suppresses that warning
    /// while the fail result is still rendered and later statements still run.
    @Test
    public void testErrorDirectiveMultiLineStatement() {
        final String output = processWithErrorCapture(
                "[ERROR] 1 +/",
                "a",
                "\"still here\"");
        assertFalse(hasDocsBuggyWarning(), "[ERROR] must suppress the docs-buggy warning");
        assertTrue(output.contains("still here"));   // processing continues past the failure
    }

    /// [MAXOUTPUT] is per statement: a second statement without the directive must
    /// not inherit the earlier statement's clip.
    @Test
    public void testMaxOutputResetsPerStatement() {
        final String output = process(
                "[MAXOUTPUT 2] \"\"\"clip one/",
                "clip two/",
                "clip three\"\"\"",
                "\"\"\"full one/",
                "full two/",
                "full three\"\"\"");
        assertTrue(output.contains("clip one"));     // clipped statement input in full
        assertTrue(output.contains("clip two"));
        assertTrue(output.contains("clip three"));
        assertTrue(output.contains("full one"));     // following statement NOT clipped
        assertTrue(output.contains("full two"));
        assertTrue(output.contains("full three"));
        assertTrue(output.contains("..."));          // exactly one clip marker
        assertFalse(output.substring(output.indexOf("...") + 3).contains("..."));
    }

    /// [NO_HEADER] drops the listing-block wrapper entirely.
    @Test
    public void testNoHeaderDirective() {
        final String output = process("[NO_HEADER] \"unwrapped\"");
        assertTrue(output.contains("\"unwrapped\""));
        assertFalse(output.contains("[source,mtron]"));
        assertFalse(output.contains("----"));
    }

    /// [HEADER] prepends a header line to the evaluated listing block.
    @Test
    public void testHeaderDirective() {
        final String output = process("[HEADER] my listing header", "\"hello\"");
        assertTrue(output.contains("my listing header"));
        assertTrue(output.indexOf("my listing header") < output.indexOf("==>"));
    }

    // ── [ERROR] log capture ───────────────────────────────────────────

    private ListAppender<ILoggingEvent> appender;

    private String processWithErrorCapture(final String... bodyLines) {
        this.appender = new ListAppender<>();
        this.appender.start();
        // MtronPreprocessor.LOG is Graphitty.log(MtronPreprocessor.class): its
        // source is the Class object, so GraphittyLogger.logger() resolves to the
        // shared "java.lang.Class" logback logger.  Capture on the ROOT logger
        // instead and filter the captured events by message content.
        final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(this.appender);
        try {
            return process(bodyLines);
        } finally {
            root.detachAppender(this.appender);
        }
    }

    private boolean hasDocsBuggyWarning() {
        for (final ILoggingEvent event : this.appender.list) {
            if (event.getFormattedMessage().contains("no [ERROR] modifier")) return true;
        }
        return false;
    }
}

