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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.petitparser.context.Context;
import org.petitparser.parser.Parser;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Bytes;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Field;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mParser.m_bool;
import static studio.phaseshift.metatron.isa.m.parser.mParser.m_bytes;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class mParserTest extends AbstractMetatronTest {

    // ========================================
    // Type Parsing (simple, data-free tests)
    // ========================================

    @Test
    public void testBoolParse() {
        assertEquals(Tokens.BOOL_TID, m_bool().parse("true").<Obj>get().tid());
        assertEquals(bool(true), ObjmtronSerializer.parse("true"));
        assertEquals(bool(false), ObjmtronSerializer.parse("false"));
    }

    @Test
    public void testBytesParse() {
        assertEquals(Tokens.BYTES_TID, m_bytes().parse("0xabc123").<Bytes>get().tid());
        assertArrayEquals(HexFormat.of().parseHex("abc123"), m_bytes().parse("0xabc123").<Bytes>get().jvm().array());
    }

    @Test
    public void testIntParse() {
        assertEquals(jnt(1234), ObjmtronSerializer.parse("1234 "));
        Obj t = jnt(1);
        assertEquals(t, ObjmtronSerializer.parse("1 "));
        assertEquals(objs(List.of(jnt(1), jnt(5))), t.append(jnt(5)));
        assertEquals(objs(List.of(jnt(1), jnt(4))), t.append(jnt(4)));
        assertEquals(objs(List.of(jnt(1), jnt(3), jnt(4), jnt(5))),
                t.append(jnt(3)).append(jnt(4)).append(jnt(5)));
        assertEquals(objs(List.of(jnt(1), jnt(3), jnt(4), jnt(5))),
                t.append(jnt(3)).append(jnt(4)).append(jnt(5)));
    }

    @Test
    public void testTParse() {
        assertEquals(uri("T"), ObjmtronSerializer.parse("T"));
        assertEquals(uri("T"), ObjmtronSerializer.parse("<T>"));
        assertEquals(uri("TEST"), ObjmtronSerializer.parse("TEST"));
        assertEquals(uri("TEST"), ObjmtronSerializer.parse("<TEST>"));
    }

    @Test
    public void testRealParse() {
        assertEquals(real(1234.23), ObjmtronSerializer.parse("1234.23"));
    }

    // ========================================
    // String Parsing (Parameterized)
    // ========================================

    @ParameterizedTest
    @CsvSource(value = {
            "'abc'                                                          % 'abc'",
            "'aBc35 4e6'                                                    % 'aBc35 4e6'",
    }, delimiter = '%', quoteCharacter = '~')
    public void testStrParse(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================
    // String Escape Sequences (Parameterized)
    // ========================================

    @ParameterizedTest
    @CsvSource(value = {
            // Escaped dot — both chars preserved
            "'\\.'                                                          % '\\.'",
            // Escaped backslash — both chars preserved
            "'\\\\'                                                         % '\\\\'",
            // Escaped single quote inside single-quoted string
            "'\\''                                                          % '\\''",
            // Escaped double quote inside single-quoted string
            "'\\\"'                                                         % '\\\"'",
            // Multiple escapes: escaped backslash + escaped dot
            "'\\\\\\.'                                                      % '\\\\\\.'",
            // Escape in the middle of normal text
            "'hello\\.world'                                                % 'hello\\.world'",
            // Backslash-n — literal, not a newline
            "'\\n'                                                          % '\\n'",
            // Backslash-t — literal, not a tab
            "'\\t'                                                          % '\\t'",
    }, delimiter = '%', quoteCharacter = '~')
    public void testStrEscapeSequences(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================
    // String Escape Edge Cases (Parameterized)
    // ========================================

    @ParameterizedTest
    @CsvSource(value = {
            // Consecutive escape sequences
            "'\\.\\!'                                                       % '\\.\\!'",
            // Only escape sequences, no normal characters
            "'\\.\\@\\#'                                                    % '\\.\\@\\#'",
            // Leading and trailing escape sequences
            "'\\.abc\\$'                                                    % '\\.abc\\$'",
            // Escaped backslash at start
            "'\\\\abc'                                                      % '\\\\abc'",
            // Mixed path-like escapes
            "'path\\\\to\\\\file'                                           % 'path\\\\to\\\\file'",
            // Escaped single quotes in text
            "'it\\'s working'                                               % 'it\\'s working'",
    }, delimiter = '%', quoteCharacter = '~')
    public void testStrEscapeEdgeCases(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================
    // String Escape Round-Trip (loop-based)
    // ========================================

    @Test
    public void testStrEscapeRoundTrip() {
        final String[] inputs = {
                "'\\.'",
                "'\\\\'",
                "'\\''",
                "'\\\\\\.'",
                "'hello\\.world'",
                "'\\n'",
                "'a\\bb\\cc\\dd'"
        };
        for (final String input : inputs) {
            final Obj firstParse = ObjmtronSerializer.parse(input);
            final String serialized = ObjmtronSerializer.single().write(firstParse);
            final Obj secondParse = ObjmtronSerializer.parse(serialized);
            assertEquals((Object) firstParse.jvm(), secondParse.jvm(),
                    "Round-trip failed for input: " + input + " → serialized: " + serialized);
        }
    }

    // ========================================
    // Comments (Parameterized)
    // ========================================

    @ParameterizedTest
    @CsvSource(value = {
            // ; sugar path through operator chain
            "1+2;map(4)                                                     % start(1).plus(2).end().map(4)",
    }, delimiter = '%', quoteCharacter = '~')
    public void testCommentParse(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // Comment tests that don't fit in CSV (brackets, multi-line)
    @ParameterizedTest
    @CsvSource(value = {
            // ; sugar path through operator chain
            "[-- a comment --]                                                     % noobj",
            "[== a comment ==]                                                     % noobj",
            "[== a comment ==]1+2                                                  % 1.plus(2)",
            "1+[== a comment ==]2                                                  % 1.plus(2)",
            "1[== a comment ==]+[--more--]2                                        % 1.plus(2)",
            "~[-- a comment --]\n\n~                                               % noobj",
            "~[-- a comment \n\n --]\n1+2\n~                                       % <ERROR>",
            "~[== a comment \n\n ==]\n1+2\n~                                       % 1.plus(2)",
            "~[== [-- a comment --] \n\n ==]\n1+2\n~                               % 1.plus(2)",
            "~[== [-- a; comment; --] \n\n ==]\n;map(2)\n~                         % map(2)",
            "~[== [-- a; comment; --]\r\n\r\n \n\n ==]\n;map(2)\n~                 % map(2)",
            "~[----]~                                                              % noobj",
            "~[====]~                                                              % noobj",
            // [-- closed by EOL (no --]), not by --]
            "~[-- just to eol\n1+2~                                                % 1.plus(2)",
            // [-- with --] appearing twice — non-greedy stops at first
            "~[-- a -- b --]c--]~                                                  % <ERROR>",
            // [== containing bare [-- that closes at EOL mid-block
            "~[== line1 [-- inline\nline3 ==]\n1+2~                                % 1.plus(2)",
            // Stray --] / ==] without opener — should parse as expression? error?
            "~--]~                                                                 % <ERROR>",
            "~==]~                                                                 % <ERROR>",

    }, delimiter = '%', quoteCharacter = '~')
    public void testCommentParseEdgeCases(final String lhs, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, lhs, expected);
    }

    // ========================================
    // Comments Inside String Literals
    // ========================================

    @Test
    public void testStripCommentsIsQuoteAware() {
        // Comments outside string literals are still stripped...
        assertEquals("x => 1", mParser.stripComments("x => 1[-- drop --]"));
        assertEquals("x => 1", mParser.stripComments("x => 1[== drop ==]"));
        // ...but comment markers inside string literals survive.
        assertEquals("\"a [-- keep --] b\"", mParser.stripComments("\"a [-- keep --] b\""));
        assertEquals("'a [== keep ==] b'", mParser.stripComments("'a [== keep ==] b'"));
        assertEquals("\"\"\"a [-- keep --] b\"\"\"", mParser.stripComments("\"\"\"a [-- keep --] b\"\"\""));
        // The scratch project tree annotation the parser previously corrupted.
        assertEquals("\"\"\"SKILL.md [-- current file --]\"\"\"",
                mParser.stripComments("\"\"\"SKILL.md [-- current file --]\"\"\""));
    }

    @Test
    public void testStringWithCommentMarkersSurvivesParse() {
        final Obj str = mParser.parse("\"\"\"SKILL.md [-- current file --]\"\"\"");
        assertTrue(str.isStr());
        assertEquals("SKILL.md [-- current file --]", str.jvm());
    }

    // ========================================
    // Semicolon Behavior (Parameterized)
    // ========================================

    @ParameterizedTest
    @CsvSource(value = {
            // ';' is sugar for an end() INST, so it appears in the parsed program
            "1+2;map(4)                                                     % start(1).plus(2).end().map(4)",
            // ... and inside a string it is just text
            "'\\.'                                                          % '\\.'",
            "'a;b'                                                          % 'a;b'",
    }, delimiter = '%', quoteCharacter = '~')
    public void testSemicolonBehavior(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Surface forms that must parse to exactly the same Obj — the rewrites the grammar performs for
     * the author. A bare value in a chain becomes a map of it; a statement boundary is start/end
     * around the chains; a comment is invisible; a literal keeps its dot.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2).445                   % 1.plus(2).map(445)",
            "1.plus(2).9                     % 1.plus(2).map(9)",
            "1.plus(2).5.plus(3)             % 1.plus(2).map(5).plus(3)",
            "a.x(); y()                      % a.x().end().y()",
            "a.x(); 42                       % a.x().end().start(42)",
            "1.plus(2).plus(5); 244.plus(30) % start(1).plus(2).plus(5).end().start(244).plus(30)",
            "1;                              % start(1).end()",
            ";1                              % end().start(1)",
            "[-- a note --] 1 + 2            % 1 + 2",
            "1 [-- a note --] + 2            % 1 + 2",
            "1 + 2 [== a note ==]            % 1 + 2",
            "plus(1, [-- a note --] 2)       % plus(1,2)",
            "1.plus(2).{plus(4),plus(10)}.mult(2) % 1.plus(2).branch(plus(4),plus(10)).mult(2)",
            "1.plus(2).{plus(4),plus(10),plus(20)} % 1.plus(2).branch(plus(4),plus(10),plus(20))",
            "1.plus(2).{plus(4)}             % 1.plus(2).branch(plus(4))",
            "1.plus(2).{7,3}                 % 1.plus(2).branch(7,3)",
    }, delimiter = '%')
    void testParsesToTheSameObjAs(final String code, final String equivalent) {
        checkEquality(LOG, mParser.parse(code), mParser.parse(equivalent), true);
    }

    /**
     * What an expression is worth. apply() is one program; eval() runs the statements of a source
     * in turn, which is the only difference between the last two columns.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1                                        % 1",
            "1.plus(2)                                % 3",
            "1.plus(2).plus(3).plus(4)                % 10",
            "100.minus(1)                             % 99",
            "1.plus(2).445                            % 445",
            "1.plus(2).445.plus(4)                    % 449",
            "1.plus(2).9                              % 9",
            "1.plus(2).5.plus(3)                      % 8",
            "1.plus(2).plus(5); 244.plus(30)          % 274",
            "1.plus(2).plus(5).to(x); 244.plus(*x)    % 244",
            "1.plus(2).{plus(4),plus(10)}             % {7,13}",
            "1.plus(2).{plus(4),plus(10),plus(20)}    % {7,13,23}",
            "1.plus(2).{plus(4)}                      % 7",
            "1.plus(2).{7,3}                          % {7,3}",
            "{1,2,3,4}.map(+2)                        % {3,4,5,6}",
            "1.445                                    % 1.445",
            "1.5                                      % 1.5",
    }, delimiter = '%')
    void testApply(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================
    // eval() Statement Splitting
    // ========================================

    @Test
    public void testEvalDoesNotSplitQuotedSemicolons() {
        // eval() splits the whole source on ; — quoted ; must stay inside its span.
        // A lone " inside a triple-quoted span used to defeat the old boolean-toggle
        // splitter (early close exposed the inner ; as a statement separator).
        assertEquals(jnt(42), Machine.evaluate("s -> \"\"\"a\"b;c\"\"\"; 42"));
        assertEquals(jnt(42), Machine.evaluate("s -> \"a;b\"; 42"));
    }

    // ========================================
    // Comment Mid-Expression (Parameterized)
    // ========================================

    @Test
    public void testCommentMidExpression() {
        // Comment-only inputs
        assertTrue(ObjmtronSerializer.parse("[-- just a comment --]").isNoObj());
        assertTrue(ObjmtronSerializer.parse("[== just a block ==]").isNoObj());
        assertTrue(ObjmtronSerializer.parse("[-- just a comment --]").isNoObj());
        assertTrue(ObjmtronSerializer.parse("[== just a block ==]").isNoObj());

        // Leading comments via parse
        assertEquals(42L, (Object) ObjmtronSerializer.parse("[== header ==]42").jvm());

        // Mid-expression comments: parser limitation — m_comment() in obj_parser
        // greedily matches as a standalone expression, stopping before the value.
        assertDoesNotThrow(() -> ObjmtronSerializer.parse("1+[-- comment --]2"));
        assertDoesNotThrow(() -> ObjmtronSerializer.parse("1.[-- comment --]plus(2)"));
        assertDoesNotThrow(() -> ObjmtronSerializer.parse("[1,[-- c --]2,3]"));
        assertDoesNotThrow(() -> ObjmtronSerializer.parse("[a=>1,[-- c --]b=>2]"));
        assertDoesNotThrow(() -> ObjmtronSerializer.parse("1+[== block ==]2"));
    }

    // ========================================
    // multi-statement source
    // ========================================

    @Test
    public void testParseMulti() {
        // Single expression — same shape as parse()
        assertEquals(jnt(42).jvm(), ObjmtronSerializer.parse("42").jvm());

        // Two bare values
        final Obj multi1 = ObjmtronSerializer.parse("42; true");
        assertTrue(multi1.isCode());
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(multi1.asCode()).size());

        // Three expressions
        assertEquals(3, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("1; 2; 3").asCode()).size());

        // Single string — no splitting inside quotes
        final Obj strResult = ObjmtronSerializer.parse("'hello;world'");
        assertTrue(strResult.isStr());
        assertEquals("hello;world", strResult.jvm());

        // ; with whitespace variations
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("1 ; 2").asCode()).size());
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("1;2").asCode()).size());

        // Multiple consecutive ;
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("1;;;2").asCode()).size());

        // Mixed expression types
        assertEquals(3, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("42; true; 'hello'").asCode()).size());

        // Instruction + bare value
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("map(1); 42").asCode()).size());

        // Empty input
        assertTrue(ObjmtronSerializer.parse("").isNoObj());
        assertTrue(ObjmtronSerializer.parse("   ").isNoObj());
    }

    @Test
    public void testParseMultiComments() {
        // Block comment between expressions
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("1;[== block ==]2").asCode()).size());

        // Block comment with ; inside — not treated as separator
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse(
                        "1;[== this ; is not ; a separator ==]2").asCode()).size());

        // Line comment between ; and expression
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse("1;[-- between --]2").asCode()).size());

        // Comments around ; separator
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parse(
                        "1;[-- before --];[-- after --]2").asCode()).size());

        // Leading/trailing comments
        assertEquals(42L, (Object) ObjmtronSerializer.parse("[== header ==]42").jvm());
        assertEquals(42L, (Object) ObjmtronSerializer.parse("42[-- trailing --]").jvm());
    }

    // ========================================
    // splitCodeAtEnd() Structural Tests
    // ========================================

    @Test
    public void testSplitCodeAtEnd() {
        // Single instruction, no end()
        assertEquals(1, ObjmtronSerializer.splitCodeAtEnd(
                MCode.of(List.of(instB(START_INST_TID, lst(jnt(1)))))).size());

        // Two segments separated by end()
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                MCode.of(List.of(
                        instB(START_INST_TID, lst(jnt(1))),
                        instB(END_INST_TID, lst()),
                        instB(f("map"), lst(jnt(4)))))).size());

        // Only end() — returns empty
        assertEquals(0, ObjmtronSerializer.splitCodeAtEnd(
                MCode.of(List.of(instB(END_INST_TID, lst())))).size());

        // Trailing end() — discarded
        assertEquals(1, ObjmtronSerializer.splitCodeAtEnd(
                MCode.of(List.of(
                        instB(START_INST_TID, lst(jnt(1))),
                        instB(END_INST_TID, lst())))).size());

        // Consecutive end() — empty segment skipped
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                MCode.of(List.of(
                        instB(START_INST_TID, lst(jnt(1))),
                        instB(END_INST_TID, lst()),
                        instB(END_INST_TID, lst()),
                        instB(f("map"), lst(jnt(2)))))).size());
    }

    // ========================================
    // URI / Rel / Inst / Sugar (unchanged)
    // ========================================

    @Test
    public void testUriParse() {
        assertEquals(
                f("http://metatron.com?a=2&b=3"),
                uri("http://metatron.com?a=2&b=3").uriValue());
        assertEquals(
                uri("http://metatron.com?a=2&b=3").uriValue(),
                ObjmtronSerializer.parse("<http://metatron.com?a=2&b=3>").uriValue());
        assertEquals(
                f("http://metatron.com?a=2&b=3"),
                ObjmtronSerializer.parse("<http://metatron.com?a=2&b=3>").uriValue());
        for (fURI x : List.of(
                ObjmtronSerializer.parse("<http://metatron.com?a=2&b=3>").uriValue(),
                f("http://metatron.com?a=2&b=3"),
                uri("http://metatron.com?a=2&b=3").uriValue())) {
            assertEquals("http", x.scheme());
            assertEquals("metatron.com", x.host());
            assertEquals(-1, x.port());
            assertEquals(List.of(), x.path());
            assertEquals(cInt.ONE(), x.c());
            assertEquals(List.of(), x.poly());
            assertEquals(Map.of("a", "2", "b", "3"), x.qMap());
        }
        assertEquals(uri("http://metatron.com?a&b").uriValue(), ObjmtronSerializer.parse("<http://metatron.com?a&b>").uriValue());
        assertEquals(uri("http://metatron.com?a&b"), ObjmtronSerializer.parse("<http://metatron.com?a&b>"));
        assertEquals(uri("http://metatron.com?a=a/b/c&b=a"), ObjmtronSerializer.parse("<http://metatron.com?a=a/b/c&b=a>"));
        assertTrue(ObjmtronSerializer.parse("/metatron.com?a&b").isFail(),
                "a quri missing its ?key=value is a fail");
        assertEquals(uri("metatron/com?a&b"), ObjmtronSerializer.parse("metatron/com?a&b"));
    }

    @Test
    public void testRelParse() {
        assertEquals(rel(uri("a"), uri("b")).jvm(), ObjmtronSerializer.parse("a => b").jvm());
        assertEquals(rel(jnt(1), uri("b")).jvm(), ObjmtronSerializer.parse("1 => b").jvm());
        assertEquals(rel(jnt(1), real(4.3)).jvm(), ObjmtronSerializer.parse("1 => 4.3").jvm());
    }

    @Test
    public void testInstParse() {
        assertEquals(instB(PLUS_INST_TID, lst(jnt(1), jnt(2))), ObjmtronSerializer.parse("plus(1,2)"));
        assertEquals(instB(PLUS_INST_TID, lst()), ObjmtronSerializer.parse("plus()"));
        assertTrue(ObjmtronSerializer.parse("plus()").asInst().jvm().get0().isEmpty());
        assertEquals(0, ObjmtronSerializer.parse("plus()").asInst().jvm().get0().count());
    }

    @Test
    public void testSugar() {
        assertEquals(lst(jnt(1), lst(jnt(1), lst(jnt(1)))), ObjmtronSerializer.parse("1-<[_,-<[_,-<[_]]]").apply());
    }

    // ========================================
    // Parse Error Messages
    // ========================================

    /**
     * Every diagnostic has the same shape and names the source position it belongs to: header with
     * line:col, the source as written, a caret, an explanation, and no combinator dump. The expected
     * fragment is the one part that may change as labels improve; the shape may not.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "lst[1,2,3                        % unclosed bracket",
            "plus(1,2                         % unclosed paren",
            "<http://example.com              % unclosed angle",
            "1]                               % stray list closer",
            "1)                               % stray paren closer",
            "1+                               % dangling binary operator",
            "12ab                             % two adjacent terms",
            "[1 2]                            % poly entry",
            "a.b                              % dot is not a name separator",
            ".5                               % no leading-dot reals",
            "~'abc~                           % unclosed single quote",
            "~\"abc~                           % unclosed double quote",
            "~\"\"\"abc~                           % unclosed triple quote",
            "plus(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20 % long expression, trimmed snippet",
    }, delimiter = '%', quoteCharacter = '~')
    void testDiagnostics(final String code, final String desc) {
        final Obj parsed = mParser.parse(code);
        assertTrue(parsed.isFail(), () -> desc + ": must fail: " + parsed);
        final mParser.ParseFault fault = mParser.faultOf(parsed);
        assertNotNull(fault, () -> desc + ": the fail must carry the fault");
        assertSame(parsed.asFail().jvm(), fault, () -> desc + ": the fault is the fail own cause");
        assertEquals(code, fault.source(), () -> desc + ": the source as sent");

        final String message = fault.getMessage();
        assertTrue(message.contains("parse error at"), () -> desc + ": header missing: " + message);
        assertTrue(message.contains("line ") && message.contains("col "),
                () -> desc + ": position missing: " + message);
        assertTrue(message.contains("^"), () -> desc + ": caret missing: " + message);
        assertFalse(message.contains("Parser"), () -> desc + ": combinator names leaked: " + message);
        assertTrue(message.length() < code.length() + 200,
                () -> desc + ": snippet must be trimmed, got " + message.length() + " chars: " + message);

        assertTrue(fault.position() >= 0 && fault.position() <= code.length(),
                () -> desc + ": position inside the source");
        final int lineStart = code.lastIndexOf('\n', fault.position() - 1) + 1;
        assertEquals(fault.position() - lineStart + 1, fault.column(),
                () -> desc + ": column must be the source column of the position");
    }

    @Test
    public void testPickOutOfRangeUsesDesignedText() {
        // pick() is the inst-chain token accessor — an out-of-range ask is a
        // malformed parse structure, not an IndexOutOfBounds to be shown
        final MTronException ex = assertThrows(MTronException.class, () -> mParser.pick(List.of(1, 2, 3), 5),
                "an out-of-range pick must fail");
        assertTrue(ex.getMessage().contains("token list end — asked for token 5 of 3"),
                "the designed text should name the ask and the list, got: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("IndexOutOfBounds"),
                "a raw java exception name must not leak, got: " + ex.getMessage());
        assertTrue(java.util.Objects.equals(mParser.pick(List.of(1, 2, 3), 1), 2),
                "an in-range pick must still return the token");
    }

    // ========================================
    // THE ONE DOOR — parse() returns an obj, or a fail::T
    // ========================================
}
