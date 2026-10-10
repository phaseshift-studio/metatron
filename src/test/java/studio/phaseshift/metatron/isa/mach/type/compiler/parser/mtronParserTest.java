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

package studio.phaseshift.metatron.isa.mach.type.compiler.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.parser.Parser;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Compiler;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.BasicCompiler;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The parse stage of {@code compiler::T} — {@code mtron_parser::T}.
 * <p>
 * Three things are being pinned here, and they are different. The first is the stage contract: a
 * parser reads a {@code str}, emits {@code code::T}, and never re-reads what is not source text. The
 * second is that the stage is WIRED — that {@code compiler::[=>]} carries it as a rec entry and that
 * the compiler's schedule actually runs it, so source text is compiled rather than rejected; a stage
 * that nothing calls is a declaration, not a stage. The third is the claim the type exists to make:
 * that a parser emitting mtron code is a new language on metatron, which the toy parser at the bottom
 * demonstrates end to end without adding an instruction set, an evaluator or a type.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtronParserTest extends AbstractMetatronTest {

    // ======================== the stage contract ========================

    /**
     * A grammar's three shapes, and the stage's answer to each. Nothing about what mtron's own grammar
     * returns is uniform — a value-led chain is a code, a head-first call is a lone inst, and a literal
     * is just the literal — so the raw shapes are read here through the grammar, while the STAGE is
     * asked for code and must answer with code whatever shape came back.
     */
    @Test
    public void testParseYieldsCodeWhateverTheGrammarMade() {
        assertTrue(ObjmtronSerializer.parse("6.plus(1)").isCode(), "the grammar reads a value-led chain as code");
        assertTrue(ObjmtronSerializer.parse("plus(1)").isInst(), "the grammar reads a head-first call as a lone inst");
        assertTrue(ObjmtronSerializer.parse("5").isInt(), "the grammar reads a literal as itself");
        assertTrue(ObjmtronSerializer.parse("[a=>1]").isRec(), "the grammar reads a rec literal as a rec");
        for (final String source : List.of("6.plus(1)", "plus(1)", "5", "[a=>1]")) {
            assertTrue(mtronParser.single().apply(str(source)).isCode(),
                    "the stage emits code for '" + source + "' whatever the grammar produced");
        }
    }

    /**
     * The stage is total over objs: what is not source text is not read. A code passes through
     * identically — same instance, so nothing is re-rendered and no vid is lost — and a value is
     * carried into the lift as it stands, addressing intact.
     */
    @Test
    public void testNonSourceTextIsNotReRead() {
        final Code code = ObjmtronSerializer.parse("6.plus(1)");
        assertSame(code, mtronParser.single().apply(code), "an already-parsed code passes through as the same instance");
        final Obj located = ObjmtronSerializer.parse("6").vid(f("/tmp/scalar"));
        final Code lifted = mtronParser.single().apply(located);
        assertEquals(located, lifted.insts().getFirst().arg(0),
                "a value is carried into the start argument with its vid, not re-parsed from its rendering");
    }

    /**
     * The lift every parser shares — {@link Parser.Helper#toCode}. All three grammar shapes become a
     * code, and a code that is already a code is not rebuilt.
     */
    @Test
    public void testToCodeLiftsAllThreeShapes() {
        final Code fromValue = Parser.Helper.toCode(mtronParser.single().apply(str("5")));
        assertTrue(fromValue.isCode(), "a bare value lifts into a code");
        assertEquals(ObjmtronSerializer.parse("5"), fromValue.apply(noobj()), "the lifted code evaluates to the value it was lifted from");
        final Code fromInst = Parser.Helper.toCode(ObjmtronSerializer.parse("plus(1)"));
        assertTrue(fromInst.isCode(), "a lone inst lifts into a one-inst code");
        assertEquals(ObjmtronSerializer.parse("7"), fromInst.apply(jnt(6)),
                "the fragment is the inst applied to whatever lhs it is handed — the lift adds no start and no argument");
        assertEquals(noobj(), fromInst.apply(noobj()), "a head-first call carries no start, so on nothing it is nothing");
        final Code already = ObjmtronSerializer.parse("6.plus(1)");
        assertSame(already, Parser.Helper.toCode(already), "a code passes through the lift untouched");
    }

    // ======================== the stage is wired ========================

    /**
     * The defaults are the rec entries, so all four stages are readable off the compiler — the parse
     * stage included. A compiler whose stages were Java-side fallbacks would answer {@code noobj}
     * here and be invisible to mtron.
     */
    @Test
    public void testDefaultStagesCarryAllFourInTheRec() {
        final Compiler compiler = BasicCompiler.defaults();
        assertInstanceOf(mtronParser.class, compiler.at(uri(PARSER)), "the parse stage is a rec entry, not a fallback");
        assertInstanceOf(Parser.class, compiler.parser(), "the accessor reads it back");
        assertEquals(MACH_MTRON_PARSER_TID, compiler.parser().tid(), "the default parse stage is mtron_parser::T");
        assertFalse(compiler.at(uri(REWRITER)).isNoObj(), "the rewrite stage is present");
        assertFalse(compiler.at(uri(RESOLVER)).isNoObj(), "the resolve stage is present");
        assertFalse(compiler.at(uri(TYPER)).isNoObj(), "the type stage is present");
    }

    /**
     * The same four, reached the way a user reaches them: {@code compiler::[=>]} through the type's
     * constructor. This is the seam that would silently re-default if the constructor and the rec
     * defaults ever disagreed.
     */
    @Test
    public void testBareCompilerGetsAllFourDefaults() {
        // the load-bearing claim: an EMPTY rec is enough. The type's constructor runs the rec through
        // BasicCompiler.stages, which merges defaultStages() — so the defaulting is the constructor's
        // job and does not need the predicate to carry an else-default for each field. (The predicate
        // only has to make an absent field LEGAL, which the {?} key does.)
        final Compiler compiler = ObjmtronSerializer.parse("compiler::[=>]");
        assertInstanceOf(BasicCompiler.class, compiler, "compiler::[=>] mints the rec-backed compiler");
        assertInstanceOf(mtronParser.class, compiler.at(uri(PARSER)), "mtron reads the parse stage off a compiler::[=>]");
        assertEquals(MACH_MTRON_PARSER_TID, compiler.parser().tid(), "the default parse stage is mtron_parser::T");
        assertEquals(MACH_FIXPOINT_REWRITER_TID, compiler.at(uri(REWRITER)).tid(), "the default rewriter is fixpoint_rewriter::T");
        assertEquals(MACH_SCORING_RESOLVER_TID, compiler.at(uri(RESOLVER)).tid(), "the default resolver is scoring_resolver::T");
        assertEquals(MACH_TYPER_TID, compiler.at(uri(TYPER)).tid(), "the default typer is typer::T");
    }

    /**
     * A parser is nameable in the language, so a compiler's read stage can be pointed at explicitly.
     */
    @Test
    public void testParserTypeIsConstructible() {
        final Parser parser = ObjmtronSerializer.parse("mtron_parser::[=>]");
        assertInstanceOf(mtronParser.class, parser, "mtron_parser::[=>] constructs the parser");
        assertEquals(MACH_MTRON_PARSER_TID, parser.tid());
        final Compiler compiler = ObjmtronSerializer.parse("compiler::[parser=>mtron_parser::[=>]]");
        assertInstanceOf(mtronParser.class, compiler.at(uri(PARSER)), "a compiler wired to a parser keeps it");
    }

    // ======================== the schedule runs it ========================

    /**
     * Source text in, program out — the thing the stage exists for. Every grammar shape that is a
     * complete program is compiled and then evaluated, so the row says the whole path works, not just
     * that parsing returned something.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "6.plus(1)     %  7",
            "5             %  5",
            "'hello'       %  'hello'",
            "[a=>1]        %  [a=>1]",
            "[a=>1]>>a     %  1",
            "1.plus(2).mult(3)  %  9",
    }, delimiter = '%')
    public void testCompileSourceText(final String source, final String expected) {
        final Code compiled = Machine.current().compiler().apply(str(source));
        assertTrue(compiled.isCode(), "compiling source text yields code");
        assertEquals(ObjmtronSerializer.parse(expected).apply(), compiled.apply(noobj()),
                "the compiled program evaluates to the source's value");
    }

    /**
     * The remaining grammar shape — a head-first call — is a fragment rather than a whole program, and
     * it compiles to one: it runs when it is handed an lhs, which is how the console runs a line that
     * is only an instruction.
     */
    @Test
    public void testCompileLoneInstFragment() {
        final Code compiled = Machine.current().compiler().apply(str("plus(1)"));
        assertTrue(compiled.isCode(), "a head-first call compiles");
        assertEquals(ObjmtronSerializer.parse("7"), compiled.apply(jnt(6)), "the compiled fragment runs on its lhs");
    }

    /**
     * The parse stage is in the schedule, not beside it: a compiler handed text and a compiler handed
     * that same text's parsed obj produce the same program. If the stage were skipped for text, the
     * first call would fail on a non-code; if it re-read parsed input, the second would differ.
     */
    @Test
    public void testTextAndParsedObjCompileAlike() {
        final Compiler compiler = Machine.current().compiler();
        final Code fromText = compiler.apply(str("1.plus(2).mult(3)"));
        final Code fromObj = compiler.apply(ObjmtronSerializer.parse("1.plus(2).mult(3)"));
        assertEquals(fromObj, fromText, "the same program either way in");
    }

    /**
     * The front door used to be a type assertion — a compiler handed anything but a code failed with
     * a type error. It is now the parse stage's job, and the failure moved with it: the grammar
     * reports the malformed text (as a fail obj or a thrown parse error, depending on where it gave
     * up), which is a statement about the source rather than about the obj handed in.
     */
    @Test
    public void testMalformedSourceStaysAFailure() {
        try {
            final Code parsed = mtronParser.single().apply(str("plus(1,2"));
            assertTrue(parsed.insts().stream().anyMatch(Obj::isFail) || parsed.apply(noobj()).isFail(),
                    "malformed source text is carried as a failure, not quietly minted into a program");
        } catch (final RuntimeException e) {
            // the grammar throws at its own reporting points; either way the source is what failed
            assertNotNull(e.getMessage(), "a parse error says what it could not read");
        }
    }

    // ======================== the language seam ========================

    /**
     * What {@code parser::T} is FOR. A tiny prefix language — {@code add 6 to 1} — lowered onto mtron
     * by a parser that emits {@code plus(1)}: the program then rewrites, resolves, types and runs on
     * the ordinary machinery, with no instruction set, no evaluator, no type and no processor added.
     * That is the sense in which writing a parser is writing a language here, and it is why the
     * stage's contract is "emit code" rather than "return whatever you parsed".
     * <p>
     * A real language registers its own {@code parser::T} subtype in its own instset; the base tid
     * stands in for one here, because this test is about the wiring and not about type registration.
     */
    private static final class ToyLanguageParser extends MRec implements Parser {

        private ToyLanguageParser() {
            super(mutableMap(), MACH_PARSER_TID, null);
        }

        @Override
        public Code apply(final Obj source) {
            if (!source.isStr())
                return Parser.Helper.toCode(source);
            final String[] words = source.strValue().trim().split("\\s+");
            if (words.length == 4 && words[0].equals("add") && words[2].equals("to"))
                return ObjmtronSerializer.parse("%s.plus(%s)".formatted(words[1], words[3]));
            throw MTronException.of("a toy program reads 'add <int> to <int>': %s", source.strValue());
        }
    }

    @Test
    public void testAnotherLanguageLowersOntoMtron() {
        final Code lowered = new ToyLanguageParser().apply(str("add 6 to 1"));
        assertEquals(ObjmtronSerializer.parse("6.plus(1)"), lowered,
                "the foreign source lowers to mtron code and nothing else — no new node type, no new inst");
        final Compiler toy = new BasicCompiler(
                BasicCompiler.stages(mutableMap(uri(PARSER), new ToyLanguageParser())), MACH_COMPILER_TID, null);
        assertEquals(ObjmtronSerializer.parse("7"), toy.apply(str("add 6 to 1")).apply(noobj()),
                "a foreign surface syntax becomes a running mtron program, on mtron's own plus");
        assertThrows(MTronException.class, () -> toy.apply(str("multiply 6 by 1")),
                "the toy language rejects what is not its syntax, with its own message");
    }
}
