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

package studio.phaseshift.metatron.isa.m.type.impl;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.ARGS;
import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * THE INLINE DOCS OF A BUILT INSTRUCTION — that the doc strings a builder states as the second argument of the method
 * they document reach {@code ?docq}, that the argument docs are POSITIONAL (two arguments of one type keep two docs in
 * order), and that {@code create()} publishes a value, its docs and its claim as QUEUED WRITES at the addresses the
 * mount replays: the instruction's contract address, its {@code ?docq} and its {@code ?decq}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstBuilderDocsTest extends AbstractMetatronTest {

    private InstBuilderDocsTest() {
    }

    /**
     * THE DOCS A CHAIN STATES ARE THE DOCS {@code ?docq} ANSWERS — desc, dom, rng, args and example, after the queue
     * the mount would replay has been drained.
     */
    @Test
    public void inlineDocsLandAtTheDocqAddress() {
        final Inst built = InstBuilder.build()
                .tid(f("/m/inst/builder_doc_probe"), "sum an addend and an augend")
                .dom(INT_TID, "the addend int")
                .rng(INT_TID, "the sum int")
                .argLst(INT_TYPE, "the augend int")
                .body((lhs, inst) -> lhs.jvm(lhs.intValue() + inst.arg(0).intValue()))
                .example("2.plus(3) [-- 5 --]")
                .create();
        PendingWrites.drainUnder(f("/m"));
        final QCollection.Docs docs = new QCollection.Docs(Machine.read(PendingWrites.docq(built.tid())).asRec());
        assertEquals("sum an addend and an augend", docs.description());
        assertEquals("the addend int", docs.at(DOM).strValue());
        assertEquals("the sum int", docs.at(RNG).strValue());
        assertEquals(List.of("the augend int"),
                docs.at(ARGS).lstValue().stream().map(Obj::strValue).toList(),
                "the positional argument docs are the list the doc rec's args slot carries");
        assertEquals(List.of("2.plus(3) [-- 5 --]"), docs.examples());
        assertEquals(built, docs.at(OBJ), "the doc rec names the obj it documents");
    }

    /**
     * TWO ARGUMENTS OF ONE TYPE ARE TWO POSITIONS — {@code argLst(int::T, "the addend", int::T, "the augend")} is legal
     * and keeps both strings, in the order the arguments were declared, which a doc keyed by the argument TYPE cannot.
     */
    @Test
    public void twoArgumentsOfTheSameTypeKeepBothPositionalDocs() {
        final Inst built = InstBuilder.build()
                .tid(f("/m/inst/builder_two_arg_probe"), "sum two int arguments")
                .dom(INT_TID).rng(INT_TID)
                .argLst(INT_TYPE, "the addend int", INT_TYPE, "the augend int")
                .body((lhs, inst) -> lhs.jvm(lhs.intValue() + inst.arg(0).intValue() + inst.arg(1).intValue()))
                .create();
        assertEquals(2, built.args().count(), "both declared arguments reached the contract");
        PendingWrites.drainUnder(f("/m"));
        final QCollection.Docs docs = new QCollection.Docs(Machine.read(PendingWrites.docq(built.tid())).asRec());
        assertEquals(List.of("the addend int", "the augend int"),
                docs.at(ARGS).lstValue().stream().map(Obj::strValue).toList(),
                "the same-typed arguments keep one doc each, in declaration order");
    }

    /**
     * {@code create()} QUEUES, IT DOES NOT WRITE — the object at its contract address, the docs at {@code ?docq} and the
     * claim at {@code ?decq}, which is what lets an instruction set build its instructions before its space exists.
     */
    @Test
    public void createQueuesTheObjectDocAndClaimWrites() {
        final Inst built = InstBuilder.build()
                .tid(f("/m/inst/builder_queue_probe"), "observe the queued writes")
                .dom(INT_TID, "the lhs int").rng(INT_TID, "the lhs int, unchanged")
                .argLst()
                .body((lhs, inst) -> lhs)
                .law(Law.pure)
                .create();
        final List<PendingWrites.Write> writes = PendingWrites.writes();
        assertTrue(writes.stream().anyMatch(write -> write.address().equals(built.tid()) && write.value().equals(built)),
                "the object is queued at its contract address, got " + writes);
        assertTrue(PendingWrites.has(PendingWrites.docq(built.tid())),
                "the docs are queued at the contract's ?docq");
        final fURI claimAddress = PendingWrites.decq(Inst.Helper.operationTid(built.tid()));
        assertTrue(PendingWrites.has(claimAddress), "the claim is queued at the operation's ?decq");
        final Obj claim = writes.stream()
                .filter(write -> write.address().equals(claimAddress))
                .map(PendingWrites.Write::value)
                .findFirst()
                .orElse(noobj());
        assertTrue(claim.asRec().has(LAW), "the queued claim carries the declared law, got " + claim);
        PendingWrites.drainUnder(f("/m"));
    }

    /**
     * NO MACHINE IS CONSULTED TO CREATE — a declaration is answerable from memory the moment the instruction exists
     * ({@link InstBuilder#declaredForm}), and the three writes it will publish are queued rather than attempted, so a
     * builder that runs during an instruction set's static initialization cannot fail for want of a space.
     */
    @Test
    public void createStagesBeforeAnythingWrites() {
        final int before = PendingWrites.size();
        final Inst built = InstBuilder.build()
                .tid(f("/m/inst/builder_staged_probe"), "a declaration staged before any space receives it")
                .dom(INT_TID).rng(INT_TID)
                .argLst()
                .body((lhs, inst) -> lhs)
                .typer()
                .create();
        assertEquals(Inst.Form.typer, InstBuilder.declaredForm(built.tid()),
                "the declared form answers from memory, with nothing written yet");
        assertTrue(PendingWrites.has(built.tid()), "the object write is staged");
        assertTrue(PendingWrites.has(PendingWrites.docq(built.tid())), "the doc write is staged");
        assertTrue(PendingWrites.has(PendingWrites.decq(Inst.Helper.operationTid(built.tid()))),
                "the claim write is staged");
        assertTrue(PendingWrites.size() >= before + 3, "create() added its three writes to the queue");
        PendingWrites.drainUnder(f("/m"));
    }
}
