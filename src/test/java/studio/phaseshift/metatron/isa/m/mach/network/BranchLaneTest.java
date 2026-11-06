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
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A BRANCH LANE ASKS ITS PLACE FOR THE VERB IT NEEDS. A lane that only reads ({@code from(</path>)}) or only writes
 * ({@code to(</path>)}) is served by a client that declares that verb and runs no program, as {@code http_client::T}
 * answers from with a GET and to with a POST; any other program needs the place's {@code eval}. Every lane runs
 * on its own {@code virtual::T}, so the lanes run concurrently. The clients here are this machine's own (their far side is this JVM), so the rows need no socket.
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

    @Test
    public void testMarkedLanesShipOnlyTheirShare() {
        // each place counts what reached it: the marked keys split the six by weight (one and two), the unmarked key
        // gets all six, and a key's split is not part of its place (both left keys reach the one left client)
        final BiFunction<Obj, Inst, Obj> run = (lhs, inst) -> Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()));
        dial("loop://left", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, run)));
        dial("loop://right", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, run)));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}(<loop://left?split=part> => count(), "
                    + "<loop://right?split={2}part> => count(), <loop://left> => count())", "{2,4,6}");
        } finally {
            Machine.current().network().drop(f("loop://left"));
            Machine.current().network().drop(f("loop://right"));
        }
    }

    @Test
    public void testEveryLaneIsAVirtualThread() {
        // the place's eval sees the lane it runs on: a virtual::T (introspectable at ~/sys/thread like any thread),
        // handed the program to ship as code, never as the value it computes here
        final AtomicReference<Obj> lane = new AtomicReference<>(noobj());
        final AtomicReference<Obj> shipped = new AtomicReference<>(noobj());
        dial("loop://probe", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> {
            lane.set(BootLoader.CURRENT_THREAD.get());
            shipped.set(lhs);
            return Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()));
        })));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://probe> => plus(1))", "2");
            AbstractMetatronTest.checkEquality(LOG, uri(MACH_VIRTUAL_THREAD_TID), uri(lane.get().tid().basePath()), true);
            AbstractMetatronTest.checkEquality(LOG, bool(true), bool(shipped.get().isCode()), true);
        } finally {
            Machine.current().network().drop(f("loop://probe"));
        }
    }

    @Test
    public void testAKeyTimesItsLane() {
        // the place answers after a little over a second: a key that waits one second gives up, a key that waits two
        // answers, and a timeout that is not a whole number of seconds is refused before anything ships
        dial("loop://slow", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> {
            CommonUtil.sleepThread(1200);
            return Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()));
        })));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://slow?timeout=1> => plus(1))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://slow?timeout=2> => plus(1))", "2");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://slow?timeout=soon> => plus(1))", "<ERROR>");
            // a timeout on the branch itself is every key's, and a key's own overrides it
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=1(<loop://slow> => plus(1))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=1(<loop://slow?timeout=2> => plus(1))", "2");
        } finally {
            Machine.current().network().drop(f("loop://slow"));
        }
    }

    @Test
    public void testAPathLaneRunsAtItsMachine() {
        // a path names a local machine: its lane is compiled and run there, so the lane's ~ is that machine's own. This
        // machine (<~>), a pushed machine and the root (</.>) are three lanes that share the parts; a path naming no
        // machine is refused, and so is a bare key, a path relative to this machine
        final Machine root = Machine.current();
        final Machine machine = root.push(f("branch_m"));
        try {
            Machine.Helper.evalAt(root, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "7.branch(</branch_m> => to(<~/branch/lane>))", "7");
                AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?dom=int{*}&split=part(<~> => count(), "
                        + "</branch_m> => count(), </.?split=copy> => count())", "{3,3,6}");
                AbstractMetatronTest.checkCodeParseApply(LOG, "7.branch(</branch_none> => plus(1))", "<ERROR>");
                AbstractMetatronTest.checkCodeParseApply(LOG, "7.branch(a => plus(1))", "<ERROR>");
                return noobj();
            });
            Machine.Helper.evalAt(machine, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/lane>", "7");
                // <~> is this machine wherever it stands: from the pushed machine its lane writes the machine's own ~;
                // <..> is the parent, so that lane writes the root's
                AbstractMetatronTest.checkCodeParseApply(LOG, "8.branch(<~> => to(<~/branch/here>))", "8");
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/here>", "8");
                AbstractMetatronTest.checkCodeParseApply(LOG, "9.branch(<..> => to(<~/branch/up>))", "9");
                return noobj();
            });
            Machine.Helper.evalAt(root, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*</branch/up>", "9");
                return noobj();
            });
        } finally {
            machine.pop();
            Machine.write(f("/branch/up"), noobj());
        }
    }
}
