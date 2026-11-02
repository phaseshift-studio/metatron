package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;

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
}
