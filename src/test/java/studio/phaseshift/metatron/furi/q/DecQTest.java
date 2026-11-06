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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.TestCategory;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.REAL_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.DECQ;
import static studio.phaseshift.metatron.furi.q.QCollection.DECQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.DECS_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Contract for spaces that host the {@code decQ} QProc — a declaration (the process laws, inverse and derivation an
 * instruction or type claims) is written beside its address at {@code ?decq} and read back through the same q, so any
 * space that hosts {@code decQ} must answer the round trip, answer {@code noobj} for the undeclared, and key the store
 * by the OPERATION address (an instruction's tid with its {@code args} component removed).
 */
public interface DecQTest extends QProcTest {

    /**
     * Base URI for decq test writes (e.g. {@code /t/decq}).
     */
    fURI decqBaseURI();

    /**
     * Optional hook for backend-specific setup.
     */
    default void setupDecq() {
    }

    /**
     * The space with {@code decQ} attached, adding it when the backend did not. There is no {@code DECQ_TYPE} (unlike
     * {@code DOCQ_TYPE}), so the q-proc is attached as a live instance rather than materialised from a tid.
     */
    default Space decqSpace() {
        final Space space = this.getSpace();
        if (space.qs().lstValue().stream().noneMatch(q -> ((QProc) q).pattern().equals(DECQ_PATTERN)))
            space.addQ(QCollection.decQ());
        this.setupDecq();
        return space;
    }

    /**
     * A declaration written at {@code ?decq} answers itself on the read back — the store is a record, not a rewrite.
     */
    @TestCategory.Write
    @TestCategory.Read
    @Test
    default void testDecqRoundTrip() {
        final Space space = this.decqSpace();
        final fURI base = this.decqBaseURI();
        final Obj declaration = rec(mutableMap(uri(OBJ), str("vellum"), uri(LAW), lst(uri("pure"), uri("commutative"))), DECS_TID, null);
        space.write(base.addQ(DECQ), declaration);
        assertEquals(declaration, space.read(base.addQ(DECQ)),
                "a declaration round-trips through ?decq");
    }

    /**
     * An address that declared nothing answers {@code noobj}, never the value that lives at the bare address.
     */
    @TestCategory.Read
    @Test
    default void testDecqUndeclaredAnswersNoobj() {
        final Space space = this.decqSpace();
        final Obj read = space.read(this.decqBaseURI().extend("undeclared").addQ(DECQ));
        assertTrue(read.isNoObj(), "an undeclared ?decq answers noobj, got " + read);
    }

    /**
     * THE OPERATION ADDRESS IS THE KEY. A declaration is a fact about the operation, not about the argument types the
     * instruction happened to be minted with, so writing the declaration at the full n-tid (with {@code args}) and
     * reading it back at the operation address (args stripped) must answer the same declaration.
     */
    @TestCategory.Write
    @TestCategory.Read
    @Test
    default void testDecqKeyedByTheOperationAddress() {
        final Space space = this.decqSpace();
        final fURI op = this.decqBaseURI().dom(INT_TID).rng(INT_TID);
        final fURI full = op.args(List.of(INT_TID));
        final Obj declaration = rec(mutableMap(uri(OBJ), str("vellum"), uri(LAW), lst(uri("pure"))), DECS_TID, null);
        space.write(full.addQ(DECQ), declaration);
        assertEquals(declaration, space.read(op.addQ(DECQ)),
                "the operation address is the declaration's key, args stripped");
    }

    /**
     * A DECLARATION IS ABOUT AN OBJ. A rec that names none is not a {@code decs::T}, so the q-proc refuses the write and
     * stores nothing.
     */
    @TestCategory.Write
    @TestCategory.Read
    @Test
    default void testDecqRefusesADeclarationWithoutAnObj() {
        final Space space = this.decqSpace();
        final fURI base = this.decqBaseURI().extend("objless");
        assertTrue(space.write(base.addQ(DECQ), rec(uri(LAW), lst(uri("pure")))).isFail(),
                "a declaration without an obj is refused");
        assertTrue(space.read(base.addQ(DECQ)).isNoObj(), "the refused declaration is not stored");
    }

    /**
     * AN OPERATION'S DECLARATIONS ARE SERVED THROUGH THE STANDARD READ DOOR. The claims a builder stages beside each
     * contract of an instruction (its process laws, inverse, derivation) must be answerable at the OPERATION's bare
     * {@code ?decq} — {@code *}{@code /m/inst/plus?decq} — not only at one specific contract address. Reading it must answer the
     * declaration recs, never the instruction rows the bare address otherwise resolves to.
     */
    @TestCategory.Read
    @Test
    default void testDecqServesAnInstructionDeclaration() {
        this.decqSpace();
        final Obj decl = Machine.read(PLUS_INST_TID.addQ(DECQ));
        assertFalse(decl.isNoObj(), "an operation's ?decq answers its declarations, got noobj");
        assertTrue(decl.stream().allMatch(d -> d.isRec() && d.asRec().has(LAW)),
                "each ?decq answer is a declaration rec carrying its laws, got " + decl);
        assertTrue(decl.stream().allMatch(d -> d.isRec() && d.asRec().has(OBJ)),
                "each declaration names its obj, so identical laws do not collapse, got " + decl);
    }

    /**
     * A SUBSUMED READ NARROWS BY CARRIER. The declaration store is keyed by contract address, and a read that names a
     * carrier's dom/rng ({@code plus?real<=#&decq}) must answer only the contracts whose carrier REFINES the queried
     * type — the same type subsumption the InstSet read applies — not every contract of the operation.
     */
    @TestCategory.Read
    @Test
    default void testDecqSubsumesByCarrier() {
        this.decqSpace();
        final fURI realPlus = PLUS_INST_TID.rng(REAL_TID).dom(ALL);
        final Obj subsumed = Machine.read(realPlus.addQ(DECQ));
        final Obj bare = Machine.read(PLUS_INST_TID.addQ(DECQ));
        assertFalse(subsumed.isNoObj(), "a subsumed ?decq answers the refining contracts, got noobj");
        assertTrue(subsumed.stream().allMatch(d -> d.isRec() && d.asRec().has(LAW)),
                "each subsumed answer is a declaration rec, got " + subsumed);
        assertTrue(subsumed.stream().count() < bare.stream().count(),
                "subsumption narrows the answers (" + subsumed.stream().count() + " < " + bare.stream().count() + ")");
    }
}
