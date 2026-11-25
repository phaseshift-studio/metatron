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

package studio.phaseshift.metatron.isa.m.web.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

/**
 * A DESCRIPTION MUST TERMINATE. A type can name itself — {@code percent::T}'s constructor is
 * {@code percent?<=(<#{*}>::T)}, whose argument IS the type — so
 * {@code writeType -> generateType -> the constructor -> writeInst -> generateInst -> writeType}
 * has no natural end. Nothing stopped it, either, because the walk is entered from a VALUE's tid:
 * the failure message of a type error printed the offending value (and so the whole type), the
 * stack died inside the message, and the frame that should have answered {@code fail::T} was left
 * with nothing to send — its socket was dropped instead (a remote {@code sys_stat()} whose
 * {@code free_jvm} was {@code percent::0.77} looked like a dead machine).
 * <p>
 * Two guard rails are pinned here: no value's description re-enters the serializer
 * ({@code writeClip} writes every kind through its own writer), and a description that runs deep
 * into a type's own predicate/constructor stops at a depth bound with legal text.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjmtronSerializerRecursionTest extends AbstractMetatronTest {

    /**
     * The shapes that walk a type's self-reference: a fraction typed {@code percent} (the shape
     * {@code sys_stat} used to build — a real in an int type), the type itself, and containers of
     * them. Printing each must answer text instead of recursing.
     */
    @ParameterizedTest(name = "[{index}] prints without recursing: {0}")
    @CsvSource(delimiter = '%', value = {
            "1.as(percent::T)",
            "0.75.as(percent::T)",
            "1.as(math::percent)",
            "*</m/math/percent>",
            "[a=>0.75.as(percent::T)]",
            "[a=>true,b=>1.5,c=>'x']",
            "1=>2",
            "{2}5"
    })
    public void theSerializerTerminatesOnASelfDescribingType(final String program) {
        final Obj value = Machine.evaluate(program);
        assertFalse(value.isFail(), "program must evaluate: " + program);
        final String printed = assertDoesNotThrow(() -> Obj.Helper.objToString(value),
                "printing " + program + " must terminate");
        assertFalse(printed.isBlank(), "printing " + program + " answers text");
    }

    /**
     * The path that actually died: a type failure formats the offending value INTO its message, so
     * the message may not print the stack away. This is the same value {@code sys_stat} used to
     * hand to the type checker.
     */
    @Test
    public void aFailureMessageNamesAnAwkwardValueWithoutRecursing() {
        final Obj odd = Machine.evaluate("0.75.as(percent::T)");
        final MTronException named = assertDoesNotThrow(
                () -> MTronException.of("%s is not a %s", odd.selfTID(odd.baseTypeID()), odd.type()),
                "naming a value in a failure message must terminate");
        assertFalse(named.getMessage().isBlank(), "the failure names the value");
    }

    /**
     * THE VALUE ITSELF, built the way {@code sys_stat} used to build its percentages: a real wearing
     * an int type, with no cast to correct it. {@code percent::T} refines {@code int::T}, so this
     * value is not its own type — and naming it (a type failure's message, chief among them) must
     * still terminate.
     */
    @Test
    public void aRealWearingAnIntTypePrints() {
        final Obj odd = real(0.75d, mathInstSet.MATH_PERCENT_TID, null);
        assertDoesNotThrow(() -> Obj.Helper.objToString(odd),
                "printing a real that wears an int type must terminate");
        assertDoesNotThrow(() -> MTronException.of("%s is not a %s", odd.selfTID(odd.baseTypeID()), odd.type()),
                "naming a real that wears an int type in a failure message must terminate");
    }

    /**
     * THE CONTEXT THAT DIED: the strict typer stage — {@code type_pred => true}, the docker profile's
     * default — is what makes a description RESOLVE the type's own predicate and constructor, and a
     * resolution that fails formats itself through the same printer. That is how a type failure's
     * message popped the stack, left the frame with nothing to answer, and dropped its socket: the
     * value was {@code sys_stat}'s free_jvm, a real wearing {@code percent::T}.
     */
    @Test
    public void printsUnderTheStrictTyper() {
        Typer.Helper.set(Typer.TYPE_PRED, true);
        try {
            assertTrue(Typer.Helper.typePred(), "the strict typer is on");
            final Obj odd = real(0.75d, mathInstSet.MATH_PERCENT_TID, null);
            assertDoesNotThrow(() -> Obj.Helper.objToString(odd),
                    "printing a value that is not its type under the strict typer must terminate");
            assertDoesNotThrow(() -> MTronException.of("%s is not a %s", odd.selfTID(odd.baseTypeID()), odd.type()),
                    "naming it in a failure message under the strict typer must terminate");
        } finally {
            Typer.Helper.set(Typer.TYPE_PRED, false);
        }
    }
}
