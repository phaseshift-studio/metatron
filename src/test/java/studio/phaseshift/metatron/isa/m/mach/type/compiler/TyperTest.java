package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.typer.BasicTyper;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_TYPER_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE TYPER'S STATE IS THE TYPER'S REC, at {@code /compiler/typer/check}, and there are exactly two ways to change
 * it — both the ordinary nested write:
 * <pre>
 * java:  Machine.current().compiler().typer().at("check/inst_dom", BOOL_TRUE, Poly.MUTABLE)
 * mtron: &#64;/compiler/typer/check >>= [inst_dom => false]
 * <p>
 * The write REPLACES, which is what setting a flag means. A MERGE ({@code >>= +[inst_dom => true]}) is not a setter:
 * the rec merge turns the flag into a coefficient ({@code bool{2}::T}), and the typer's type refuses it.
 * </pre>
 * The flags are {@code bool::T} values under a {@code check} field, so the typer can grow other features beside them,
 * and each point can later become a finer realization than true/false without moving.
 */
public class TyperTest extends AbstractMetatronTest {

    @Test
    public void theTyperStateIsTheCompilerStage() {
        // the boot default: every check point on except code_resolve
        assertTrue(Typer.Helper.instDom(), "inst_dom is on by default");
        assertTrue(Typer.Helper.instRng(), "inst_rng is on by default");
        assertTrue(Typer.Helper.typePred(), "type_pred is on by default");
        assertTrue(Typer.Helper.objWrite(), "obj_write is on by default");
        assertFalse(Typer.Helper.codeResolve(), "code_resolve is the one check point the boot leaves off");

        // the java spelling: one nested write into the typer's state
        Machine.current().compiler().typer().at("check/inst_dom", BOOL_FALSE, Poly.MUTABLE);
        assertFalse(Typer.Helper.instDom(), "the java write turns a check point off");
        Machine.current().compiler().typer().at("check/inst_dom", BOOL_TRUE, Poly.MUTABLE);
        assertTrue(Typer.Helper.instDom(), "and on again");

        // the mtron spelling: the same act through the space
        ObjmtronSerializer.parse("@/compiler/typer/check >>= [inst_dom => false]").apply();
        assertFalse(Typer.Helper.instDom(), "the mtron write turns a check point off");
        ObjmtronSerializer.parse("@/compiler/typer/check >>= [inst_dom => true]").apply();
        assertTrue(Typer.Helper.instDom(), "and on again");


        // a point nobody wrote keeps the boot default
        assertFalse(Typer.Helper.codeResolve(), "an unwritten point still reads the boot default");
    }

    /**
     * A TYPER BUILT AFTER BOOT IS A typer::T. MObj's constructor checks a rec against its type before BasicTyper seeds
     * its check points, so every slot of typer::T is optional, and an absent point reads the boot default. A compiler
     * built after boot builds its typer that way ({@link BasicCompiler#defaultStages()}).
     */
    @Test
    public void aTyperBuiltAfterBootIsATyper() {
        assertTrue(new BasicTyper().asserts(Typer.TYPE_PRED), "a typer built after boot seeds the boot default");
        assertTrue(BasicCompiler.defaults().typer().asserts(Typer.TYPE_PRED), "a compiler built after boot builds its typer");
    }

    /**
     * A TYPER BUILT FROM A REC (typer::T's constructor) KEEPS THE CHECK IT IS GIVEN AND WORKS ON A COPY: BasicTyper seeds
     * the boot default only when no check is given, a point the given check leaves out still reads the default, and
     * the caller's rec never receives the typer's own entries.
     */
    @Test
    public void aTyperBuiltFromARecKeepsItsCheck() {
        final Typer given = ObjmtronSerializer.parse("typer::[check=>[type_pred=>false]]");
        assertFalse(given.asserts(Typer.TYPE_PRED), "a given check point is kept");
        assertTrue(given.asserts(Typer.INST_DOM), "a point the given check leaves out reads the boot default");
        final Typer empty = ObjmtronSerializer.parse("typer::[=>]");
        assertTrue(empty.asserts(Typer.TYPE_PRED), "the empty rec builds a typer with the boot default");
        final Rec state = rec(mutableMap(uri(Typer.CHECK), rec(mutableMap(uri(Typer.TYPE_PRED.name()), BOOL_FALSE))));
        MACH_TYPER_TYPE.constructor().apply(state);
        assertFalse(state.has(uri("verify")), "the typer is built on a copy of the caller's rec");
    }

    /**
     * A CHECK CHECKS, IT DOES NOT COUNT (Marko, 2026-11-13; 2026-11-11: "as it is with the typer's dom check off"): the
     * processor carries a lhs's count with the dom check on or off, so turning it off changes no answer. With it off,
     * >> answered the field as stored and {2}[name=>5]>>name was 5; the others already agreed, and must not count twice.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "{2}[name=>5]>>name                     % {2}5",
            "[name=>5].branch(id(),id())>>name      % {2}5",
            "{2}5.plus(1)                           % {2}6",
            "{3}5{3}.plus(1)                        % {3}6{3}",
            "{3}5.count()                           % 3",
            "{1,1,2}.plus(1)                        % {{2}2,3}"})
    public void testTheDomCheckChangesNoAnswer(final String code, final String expected) {
        try {
            for (final Bool check : List.of(BOOL_TRUE, BOOL_FALSE)) {
                Machine.current().compiler().typer().at("check/inst_dom", check, Poly.MUTABLE);
                assertDoesNotThrow(() -> checkCodeParseApply(LOG, code, expected), "inst_dom=" + check);
            }
        } finally {
            Machine.current().compiler().typer().at("check/inst_dom", BOOL_TRUE, Poly.MUTABLE);
        }
    }

    /**
     * AN INITIAL INST'S RANGE IS WHAT ITS ARGUMENT COMPUTES: a call argument runs before start does, so start(5.plus(1))
     * answers an int, not a code. Typed as the call itself, its range check failed ("rhs does not match inst range") and
     * the instruction after it resolved for a code — plus composed instead of added — with the checks on or off; a
     * forked peer evaluating a shipped start(…) failed every reply that way. A call's range is per input, so the count is
     * open: start({1,2,3}.plus(1)) answers three. Under ?block the argument rides raw, so start answers the one code.
     * Each row answers the same with every check on (the boot default) and off. Each row: the chain % its value.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "start(5.plus(1))                 % 6",
            "start(5.plus(1)).plus(1)         % 7",
            "start({1,2,3}.plus(1)).sum()     % 9",
            "start(5).plus(1)                 % 6",
            "start?block(5.plus(1)).count()   % 1"})
    public void testAnInitialInstAnswersWhatItsArgumentComputes(final String code, final String expected) {
        try {
            for (final boolean on : List.of(true, false)) {
                Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, on && Typer.defaultsOn(flag)));
                assertDoesNotThrow(() -> checkCodeParseApply(LOG, code, expected), "checks " + (on ? "on" : "off"));
            }
        } finally {
            Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, Typer.defaultsOn(flag)));
        }
    }

    /**
     * HOW FAR A SPAN MAY UNFOLD IS THE TYPER'S STATE (Marko, 2026-11-24): {@code unfold=>[uri=>int::T, lst=>int::T]},
     * 100 each by default. A uri's span unfolds its path into |n| copies ({@code <a>{n}}, {@code pow(n)}, a span it
     * takes from its lhs), a lst's axis lays a span's copies ({@code [5{n}]}), and a span past its bound fails. A span
     * of one copy unfolds nothing, so the converse is never refused. Each row: the chain % its value.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "*/compiler/typer/unfold          % [uri=>100,lst=>100]",
            "<a>{100}.count()                 % 1",
            "<a>{-100}.count()                % 1",
            "<a>{101}                         % <ERROR>",
            "<a>{-101}                        % <ERROR>",
            "a.pow(100).count()               % 1",
            "a.pow(-101)                      % <ERROR>",
            "\"a\"{100}.as(uri::T).count()    % 1",
            "\"a\"{101}.as(uri::T)            % <ERROR>",
            "[5{100}]>-.count()               % 100",
            "[5{101}]                         % <ERROR>",
            "<a/b>.reverse()                  % b/a"})
    public void testASpanUnfoldsNoFurtherThanItsBound(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * THE BOUND IS THE TYPER'S TO CHANGE, by the same nested write as a check point, in java or mtron, and it is read
     * live, so the next span sees it. The typer's type holds a bound to an int.
     */
    @Test
    public void theUnfoldBoundIsTheTypersToChange() {
        try {
            Machine.current().compiler().typer().at("unfold/lst", jnt(200), Poly.MUTABLE);
            checkCodeParseApply(LOG, "[5{101}]>-.count()", "101");
            ObjmtronSerializer.parse("@/compiler/typer/unfold >>= [uri => 2]").apply();
            checkCodeParseApply(LOG, "<a>{2}", "a/a");
            checkCodeParseApply(LOG, "<a>{3}", "<ERROR>");
            checkCodeParseApply(LOG, "a.pow(-3)", "<ERROR>");
            checkCodeParseApply(LOG, "@/compiler/typer/unfold >>= [uri => 'many']", "<ERROR>");
        } finally {
            Machine.current().compiler().typer().at("unfold/uri", jnt(Typer.UNFOLD_DEFAULT), Poly.MUTABLE);
            Machine.current().compiler().typer().at("unfold/lst", jnt(Typer.UNFOLD_DEFAULT), Poly.MUTABLE);
        }
        checkCodeParseApply(LOG, "<a>{3}", "a/a/a");
    }
}
