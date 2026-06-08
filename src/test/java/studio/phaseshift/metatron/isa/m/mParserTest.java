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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Bytes;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.AbstractMetatronTest.checkCodeParseApply;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.plus_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
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
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class mParserTest extends AbstractMetatronTest {

    // ========================================
    // Type Parsing (simple, data-free tests)
    // ========================================

    @Test
    public void testBoolParse() {
        assertEquals(BOOL_TID, m_bool().parse("true").<Obj>get().tid());
        assertEquals(bool(true), ObjmtronSerializer.parse("true"));
        assertEquals(bool(false), ObjmtronSerializer.parse("false"));
    }

    @Test
    public void testBytesParse() {
        assertEquals(BYTES_TID, m_bytes().parse("0xabc123").<Bytes>get().tid());
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
            final String serialized = new ObjmtronSerializer().write(firstParse);
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
    @Test
    public void testCommentParseEdgeCases() {
        // Comment-only
        assertTrue(ObjmtronSerializer.parse("[-- a comment --]").isNoObj());
        assertTrue(ObjmtronSerializer.parse("[== a block ==]").isNoObj());
        assertTrue(ObjmtronSerializer.parse("[-- a comment \n\n --]\n\n").isNoObj());
        // Leading line comment + expression
        assertEquals(start_(jnt(1)).plus_(jnt(2)),
                ObjmtronSerializer.parse("[-- a comment --]\n\r1+2"));
        assertEquals(plus_(jnt(2)).tryToInst(),
                ObjmtronSerializer.parse("[-- a comment --]\n\rplus(2)"));
        assertEquals(start_(jnt(1)).plus_(jnt(2)),
                ObjmtronSerializer.parse("[-- a comment\n\r\n\r --]1+2"));
        // Invalid comment syntax
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("-- a comment\n\n --"));
    }

    // ========================================
    // Semicolon Behavior (Parameterized)
    // ========================================

    @ParameterizedTest
    @CsvSource(value = {
            // parse() — ; inside operator chain produces end()
            "1+2;map(4)                                                     % start(1).plus(2).end().map(4)",
            // parse() — ; inside strings is NOT a separator
            "'\\.'                                                          % '\\.'",
            "'a;b'                                                          % 'a;b'",
            // parseMulti() — leading ;
            ";42                                                            % 42",
            // parseMulti() — trailing ;
            "42;                                                            % 42",
            // parseMulti() — only semicolons → noobj
            ";;;                                                            % noobj",
    }, delimiter = '%', quoteCharacter = '~')
    public void testSemicolonBehavior(final String code, final String expected) {
        if (expected.equals("noobj")) {
            assertTrue(ObjmtronSerializer.parseMulti(code).isNoObj());
        } else if (code.startsWith(";") || code.endsWith(";")) {
            // Leading/trailing ; tests — use parseMulti
            final Obj result = ObjmtronSerializer.parseMulti(code);
            final Obj ex = ObjmtronSerializer.parse(expected);
            assertEquals(ex, result);
        } else {
            checkCodeParseApply(LOG, code, expected);
        }
    }

    // ========================================
    // Comment Mid-Expression (Parameterized)
    // ========================================

    @Test
    public void testCommentMidExpression() {
        // Comment-only inputs
        assertTrue(ObjmtronSerializer.parse("[-- just a comment --]").isNoObj());
        assertTrue(ObjmtronSerializer.parse("[== just a block ==]").isNoObj());
        assertTrue(ObjmtronSerializer.parseMulti("[-- just a comment --]").isNoObj());
        assertTrue(ObjmtronSerializer.parseMulti("[== just a block ==]").isNoObj());

        // Leading comments via parseMulti
        assertEquals(42L, (Object) ObjmtronSerializer.parseMulti("[== header ==]42").jvm());

        // Mid-expression comments: parser limitation — m_comment() in obj_parser
        // greedily matches as a standalone expression, stopping before the value.
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("1+[-- comment --]2"));
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("1.[-- comment --]plus(2)"));
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("[1,[-- c --]2,3]"));
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("[a=>1,[-- c --]b=>2]"));
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("1+[== block ==]2"));
    }

    // ========================================
    // parseMulti() Structural Tests
    // ========================================

    @Test
    public void testParseMulti() {
        // Single expression — same shape as parse()
        assertEquals(jnt(42).jvm(), ObjmtronSerializer.parseMulti("42").jvm());

        // Two bare values
        final Obj multi1 = ObjmtronSerializer.parseMulti("42; true");
        assertTrue(multi1.isCode());
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(multi1.asCode()).size());

        // Three expressions
        assertEquals(3, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("1; 2; 3").asCode()).size());

        // Single string — no splitting inside quotes
        final Obj strResult = ObjmtronSerializer.parseMulti("'hello;world'");
        assertTrue(strResult.isStr());
        assertEquals("hello;world", strResult.jvm());

        // ; with whitespace variations
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("1 ; 2").asCode()).size());
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("1;2").asCode()).size());

        // Multiple consecutive ;
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("1;;;2").asCode()).size());

        // Mixed expression types
        assertEquals(3, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("42; true; 'hello'").asCode()).size());

        // Instruction + bare value
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("map(1); 42").asCode()).size());

        // Empty input
        assertTrue(ObjmtronSerializer.parseMulti("").isNoObj());
        assertTrue(ObjmtronSerializer.parseMulti("   ").isNoObj());
    }

    @Test
    public void testParseMultiComments() {
        // Block comment between expressions
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("1;[== block ==]2").asCode()).size());

        // Block comment with ; inside — not treated as separator
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti(
                        "1;[== this ; is not ; a separator ==]2").asCode()).size());

        // Line comment between ; and expression
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti("1;[-- between --]2").asCode()).size());

        // Comments around ; separator
        assertEquals(2, ObjmtronSerializer.splitCodeAtEnd(
                ObjmtronSerializer.parseMulti(
                        "1;[-- before --];[-- after --]2").asCode()).size());

        // Leading/trailing comments via parseMulti
        assertEquals(42L, (Object) ObjmtronSerializer.parseMulti("[== header ==]42").jvm());
        assertEquals(42L, (Object) ObjmtronSerializer.parseMulti("42[-- trailing --]").jvm());
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
        assertThrows(MTronException.class, () -> ObjmtronSerializer.parse("/metatron.com?a&b"));
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
}
