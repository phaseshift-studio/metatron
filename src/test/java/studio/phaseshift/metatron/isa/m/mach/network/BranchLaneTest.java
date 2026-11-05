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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A BRANCH LANE ASKS ITS PLACE FOR THE VERB IT NEEDS. A lane that only reads ({@code from(</path>)}) or only writes
 * ({@code to(</path>)}) is served by a client that declares that verb and runs no program, as {@code http_client::T}
 * answers from with a GET and to with a POST; any other program needs the place's {@code eval}. Remote lanes run
 * concurrently. The clients here are this machine's own (their far side is this JVM), so the rows need no socket.
 */
public class BranchLaneTest extends AbstractMetatronTest {

    /**
     * a client at {@code host} that serves exactly the instructions in {@code inst}, indexed as a peer of this machine
     */
    private static void dial(final String host, final Rec inst) {
        Network.Helper.index(rec(
                uri(HOST), uri(host),
                uri(INST), inst,
                uri(STATUS), Network.Helper.statusOf(() -> true),
                uri(CLOSE), instLambda((lhs, i) -> noobj())));
    }

    @Test
    public void testAReadOrWriteLaneNeedsNoEval() {
        // reads and writes, as http_client::T does, and runs no program: no eval
        dial("loop://shelf", rec(
                uri(FROM_INST_TID), Network.Helper.override(FROM_INST_TID, (lhs, inst) -> Machine.read(inst.arg(0).uriValue().localize())),
                uri(TO_INST_TID), Network.Helper.override(TO_INST_TID, (lhs, inst) -> Machine.write(inst.arg(0).uriValue().localize(), lhs))));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "7.branch(<loop://shelf> => to(</branch/shelf>))", "7");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.branch(<loop://shelf> => from(</branch/shelf>))", "7");
            // a program is more than a read or a write, and this place cannot run one
            AbstractMetatronTest.checkCodeParseApply(LOG, "5.branch(<loop://shelf> => plus(1))", "<ERROR>");
        } finally {
            Machine.current().network().drop(f("loop://shelf"));
            Machine.write(f("/branch/shelf"), noobj());
        }
    }

    @Test
    public void testALaneKeepsItsAddress() {
        // the lane's address is homed at the place whole: its path and its query reach the client as written
        dial("loop://echo", rec(uri(FROM_INST_TID), Network.Helper.override(FROM_INST_TID, (lhs, inst) -> uri(inst.arg(0).uriValue().localize()))));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.branch(<loop://echo> => from(</branch/x?depth=3>))", "</branch/x?depth=3>");
        } finally {
            Machine.current().network().drop(f("loop://echo"));
        }
    }

    @Test
    public void testRemoteLanesRunConcurrently() {
        // each lane's eval waits for the other to arrive: one after another, the first would wait out its timeout
        final CountDownLatch meeting = new CountDownLatch(2);
        final BiFunction<Obj, Inst, Obj> meet = (lhs, inst) -> {
            meeting.countDown();
            return MTronException.wrap(() -> meeting.await(5, TimeUnit.SECONDS))
                    ? Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()))
                    : str("the lanes ran one after another");
        };
        dial("loop://left", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, meet)));
        dial("loop://right", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, meet)));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://left> => plus(1), <loop://right> => plus(2)).sum()", "5");
        } finally {
            Machine.current().network().drop(f("loop://left"));
            Machine.current().network().drop(f("loop://right"));
        }
    }
}
