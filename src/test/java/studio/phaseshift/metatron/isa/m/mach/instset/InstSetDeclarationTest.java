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

package studio.phaseshift.metatron.isa.m.mach.instset;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * DECLARED BY ONE INSTRUCTION SET, ADDRESSED BY ANOTHER — the two facts an instruction has, and the reason a
 * contract contributed to another set's operation must be recorded by its AUTHOR as well as placed at its
 * ADDRESS.
 * <p>
 * Memory routes an instruction write to the most specific instruction set ("the most specific gains the
 * write"), so a set that declares {@code as?point<=str} has that contract placed in /m's table — /m's
 * address — while the declaration was made by /m/math/geo. The address cannot say who wrote it: the tid is
 * the shared operation's, and the docs follow the object into the address owner's {@code ?docq} space. What
 * can say it is the declaring set's own {@code inst} list, which this test pins for both ways an instruction
 * set declares one — a row in its setup's {@code inst} list, and a row a TYPE it registers declared through
 * the builder.
 * <p>
 * The stores here are throwaway instruction sets mounted inside a pushed frame, so the machine's real
 * vocabulary is never written and everything leaves with the frame.
 */
public class InstSetDeclarationTest extends AbstractMetatronTest {

    private static final fURI OWNER_ISA = f("/t/decl/owner");
    private static final fURI AUTHOR_ISA = f("/t/decl/author");

    private static InstSet store(final fURI vid, final java.util.Map<Obj, Obj> jvm) {
        return new AbstractInstSet(jvm, INSTSET_TID, vid) {
        };
    }

    private static InstSet library(final fURI vid) {
        return store(vid, mutableMap(uri(PATTERN), uri(vid.extend(ALL))));
    }

    private static void mount(final InstSet isa) {
        InstSet.mount(isa);
        isa.setup();
    }

    /** the set's own declaration list — the record of what it PROVIDED, as opposed to what it addresses. */
    private static List<Inst> declared(final InstSet isa) {
        final Obj list = isa.jvm().get(uri(INST));
        if (null == list || list.isNoObj())
            return List.of();
        assertTrue(list.isLst(), "a declaration list is a lst");
        return list.lstValue().stream().filter(Obj::isInst).<Inst>map(Obj::as).toList();
    }

    private static boolean holds(final Iterable<Inst> insts, final Inst inst) {
        for (final Inst candidate : insts)
            if (candidate.tid().toString().equals(inst.tid().toString()))
                return true;
        return false;
    }

    @Test
    public void testADeclarationAtAnotherSetsAddressIsRecordedByItsAuthor() {
        final Machine child = Machine.current().push(f("isa_declaration"));
        try {
            final InstSet owner = library(OWNER_ISA);
            mount(owner);
            // an instruction the AUTHOR declares, at the OWNER's operation address
            final Inst echo = instC(OWNER_ISA.extend("inst/echo").dom(INT_TID).rng(INT_TID), lst(), (lhs, inst) -> lhs);
            final InstSet author = store(AUTHOR_ISA, mutableMap(
                    uri(PATTERN), uri(AUTHOR_ISA.extend(ALL)),
                    uri(INST), lst(echo)));
            mount(author);

            assertTrue(holds(owner.insts(), echo), "the address owner holds the contract — jurisdiction is unchanged");
            assertFalse(holds(author.insts(), echo), "and the author's own table does not: memory routed by address");
            assertTrue(holds(declared(author), echo), "the author still records that it PROVIDED the contract");
            assertFalse(holds(declared(owner), echo), "the address owner does not record it as its own declaration");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testATypeDeclaredInstructionIsRecordedByTheSetThatRegistersTheType() {
        final Machine child = Machine.current().push(f("isa_type_declaration"));
        try {
            final InstSet owner = library(OWNER_ISA);
            mount(owner);
            // a contract declared through a TYPE's builder: create() STAGES the write (PendingWrites) and stages
            // the declaration against the type's address; the set that registers the type drains the declaration
            // into its own `inst` list, and the queue publishes the write at the owner's address on the next
            // drain — the same order a boot follows, which is why the test drains explicitly here.
            final Inst cast = instC(OWNER_ISA.extend("inst/cast").dom(INT_TID).rng(STR_TID), lst(), (lhs, inst) -> lhs);
            final Type widget = TypeBuilder.build()
                    .tid(REC_TID)
                    .vid(AUTHOR_ISA.extend("widget"))
                    .inst(cast)
                    .create();
            final InstSet author = store(AUTHOR_ISA, mutableMap(
                    uri(PATTERN), uri(AUTHOR_ISA.extend(ALL)),
                    uri(TYPE), lst(widget)));
            mount(author);
            PendingWrites.drainRest();

            assertTrue(holds(owner.insts(), cast), "the address owner holds the builder-declared contract");
            assertFalse(holds(author.insts(), cast), "the author's table does not");
            assertTrue(holds(declared(author), cast), "the set that REGISTERED THE TYPE records the type's contract");
        } finally {
            PendingWrites.clear();
            child.pop();
        }
    }
}
