package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.impl.MType;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static studio.phaseshift.metatron.Tokens.STR_TID;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * THE KILL-FIRST RULE for types: a defining request ({@code X::T[...]@name}) that differs from what the name already
 * holds is a REDEFINITION, and the rule is to clear the name first ({@code name -> noobj()}). Detection and reporting
 * always happen; REFUSING is {@link MType#REFUSE_REDEFINITION}, off by default because the fixtures re-declare names.
 */
public class TypeRedefinitionTest extends AbstractMetatronTest {

    private static final studio.phaseshift.metatron.furi.fURI VID = f("redefinition_probe");
    private static final String KILL = "redefinition_probe -> noobj";
    private boolean savedRefuse;

    @BeforeEach
    void remember() {
        this.savedRefuse = MType.REFUSE_REDEFINITION;
    }

    @AfterEach
    void restore() {
        MType.REFUSE_REDEFINITION = this.savedRefuse;
        ObjmtronSerializer.parse(KILL).apply();
    }

    private static void define(final String field, final studio.phaseshift.metatron.furi.fURI fieldType) {
        ObjmtronSerializer.parse("rec::T[?[" + field + "=>" + fieldType + "]]@" + VID).apply();
    }

    @Test
    void redefinitionReplacesTheNameWhileRefusalIsOff() {
        MType.REFUSE_REDEFINITION = false;
        this.define("a", INT_TID);
        // THE DECLARATION IS SNAPSHOT AS TEXT: the registered Type is a shared object, and a redefinition lands by
        // re-typing it in place, so object identity cannot tell before from after -- the rendered definition can.
        final String before = String.valueOf(T(VID));
        this.define("b", STR_TID);
        assertNotEquals(before, String.valueOf(T(VID)), "with refusal off the new definition lands (warned, not refused)");
    }

    @Test
    void refusalKeepsTheRegisteredTypeUntilTheNameIsKilled() {
        MType.REFUSE_REDEFINITION = true;
        this.define("a", INT_TID);
        final String first = String.valueOf(T(VID));
        this.define("b", STR_TID);
        assertEquals(first, String.valueOf(T(VID)), "the registered type is kept — noobj() its vid first, then redefine");
        // the escape hatch: clear the name, then the next definition is the first one
        ObjmtronSerializer.parse(KILL).apply();
        this.define("b", STR_TID);
        assertNotEquals(first, String.valueOf(T(VID)), "after the kill the new definition lands");
    }

    @Test
    void theSameDeclarationIsIdempotentAndNeverWarns() {
        MType.REFUSE_REDEFINITION = true;
        this.define("a", INT_TID);
        final String first = String.valueOf(T(VID));
        this.define("a", INT_TID);   // the SAME declaration, re-parsed (distinct lambda bodies)
        assertEquals(first, String.valueOf(T(VID)), "a declaration that spells the same type is a no-op, not a redefinition");
    }
}
