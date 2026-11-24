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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.sys.sysInstSet.SYS_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A BRANCH LANE ASKS ITS PLACE FOR THE VERB IT NEEDS. A lane that only reads ({@code from(</path>)}) or only writes
 * ({@code to(</path>)}), at a span of one, is served by a client that declares that verb and runs no program, as
 * {@code http_client::T} answers from with a GET and to with a POST; any other program, a spanned read among them,
 * needs the place's {@code eval}. Every lane runs
 * on its own {@code virtual::T}, so the lanes run concurrently. The clients here are this machine's own (their far side is this JVM), so the rows need no socket.
 */
public class BranchLaneTest extends AbstractMetatronTest {

    /**
     * A KEY'S {@code ?timeout} IS A {@code time::T} ({@code second::n}) and its await converts it with
     * {@code as(millis::T)}: those types are {@code /m/math}'s, so the test mounts the ISA it reads rather than
     * relying on whichever class in the fork happened to mount it first (an unmounted type converts nothing, and the
     * key waited its {@code second::20} as 20 ms). A home lane that runs long sleeps, which is {@code /m/sys}'s.
     */
    @BeforeAll
    public static void importMathInstSet() {
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(SYS_ISA_TID);
    }

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
            // THE DOOR IS THE CUT'S (a keyed lane is a move): a move reaches the place the same way, branch or not
            AbstractMetatronTest.checkCodeParseApply(LOG, "8.move(<loop://shelf>).to(</branch/shelf>)", "8");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.move(<loop://shelf>).from(</branch/shelf>)", "8");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.move(<loop://shelf>).from{2}(</branch/shelf>)", "{2}8");
            // one read is one obj's: over several, each obj is a read of its own, a run the place cannot take
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2}.move(<loop://shelf>).from(</branch/shelf>)", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2}.branch?<=int{*}(<loop://shelf> => to(</branch/shelf>))", "<ERROR>");
            // one obj of any count is one write, its count back on the answer, as an instruction of one obj puts it
            AbstractMetatronTest.checkCodeParseApply(LOG, "5.move{3}(<loop://shelf>).to(</branch/shelf>)", "{3}5");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.move(<loop://shelf>).from(</branch/shelf>)", "5");
            // nothing writes nothing: the place's value goes, as a write of nothing does at home
            AbstractMetatronTest.checkCodeParseApply(LOG, "move(<loop://shelf>).to(</branch/shelf>)", "noobj");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.move(<loop://shelf>).from(</branch/shelf>)", "noobj");
            // THE DOOR IS THE PEER'S OWN: another authority is no address at this place, and a machine in the peer runs
            // the program it is shipped, which this place cannot; neither touches the shelf
            AbstractMetatronTest.checkCodeParseApply(LOG, "6.move(<loop://shelf>).to(<loop://elsewhere/branch/shelf>)", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "6.move(<loop://shelf/branch_none>).to(</branch/shelf>)", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.move(<loop://shelf>).from(</branch/shelf>)", "noobj");
            AbstractMetatronTest.checkCodeParseApply(LOG, "8.move(<loop://shelf>).to(</branch/shelf>)", "8");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.branch(<loop://shelf> => from(</branch/shelf>))", "8");
            AbstractMetatronTest.checkCodeParseApply(LOG, "7.move(<loop://shelf>).to(</branch/shelf>)", "7");
            // A LANE'S POINT IS COPIES OF ITS ANSWER: the door reads or writes once, and the point goes on the answer
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.branch(<loop://shelf> => from{3}(</branch/shelf>))", "{3}7");
            AbstractMetatronTest.checkCodeParseApply(LOG, "7.branch(<loop://shelf> => to{2}(</branch/shelf>))", "{2}7");
            // a span is a run, no single door, and this place runs no program: a span of two, as a range
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.branch(<loop://shelf> => {2}from(</branch/shelf>))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "0.branch(<loop://shelf> => {0,1}from(</branch/shelf>))", "<ERROR>");
            // a program is more than a read or a write, and this place cannot run one
            AbstractMetatronTest.checkCodeParseApply(LOG, "5.branch(<loop://shelf> => plus(1))", "<ERROR>");
        } finally {
            Machine.current().network().drop(f("loop://shelf"));
            Machine.write(f("/branch/shelf"), noobj());
        }
    }

    /**
     * A SPANNED READ RUNS AT ITS PLACE, AS IT RUNS AT HOME: a span is a run, no single door, so the lane ships to the
     * place's eval and answers what the same run answers here, over a value held with a count of its own.
     */
    @Test
    public void testASpannedReadRunsAtItsPlaceAsAtHome() {
        final BiFunction<Obj, Inst, Obj> run = (lhs, inst) -> Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()));
        dial("loop://left", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, run)));
        try {
            Machine.write(f("/branch/shelf"), ObjmtronSerializer.parse("{3}7").apply());
            for (final String read : List.of("{2}from(</branch/shelf>)", "{1,2}from(</branch/shelf>)")) {
                final Obj home = ObjmtronSerializer.parse("0." + read).apply();
                assertFalse(home.isNoObj() || home.isFail(), () -> "the run reads something at home: " + read);
                AbstractMetatronTest.checkCodeParseApply(LOG, "0.branch(<loop://left> => " + read + ")",
                        ObjmtronSerializer.single().write(home));
            }
        } finally {
            Machine.current().network().drop(f("loop://left"));
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

    /**
     * A MEMBER HEADED BY A MOVE IS A LANE (Marko, 2026-11-24): it runs on its own thread from the machine the branch
     * stands in, as a keyed lane does, so two of them meet at their places, and a home member beside them runs too.
     */
    @Test
    public void testMovedMembersRunConcurrently() {
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
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(move(<loop://left>).plus(1),move(<loop://right>).plus(2),plus(3)).sum()", "9");
        } finally {
            Machine.current().network().drop(f("loop://left"));
            Machine.current().network().drop(f("loop://right"));
        }
    }

    @Test
    public void testMarkedLanesShipOnlyTheirShare() {
        // each place counts what reached it. A KEY IS SUGAR FOR ITS LANE (Law.nomadic, stage 3): the marked keys are the
        // bias that splits the six by weight (one and two), and a key's split is not part of its place. Keys that mix a
        // copy with a part are refused when the branch compiles (Marko, 2026-11-24: "default")
        final BiFunction<Obj, Inst, Obj> run = (lhs, inst) -> Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()));
        dial("loop://left", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, run)));
        dial("loop://right", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, run)));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}(<loop://left?split=part> => count(), "
                    + "<loop://right?split={2}part> => count())", "{2,4}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}(<loop://left?split=part> => count(), "
                    + "<loop://right?split={2}part> => count(), <loop://left> => count())", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}(<loop://left> => count(), "
                    + "<loop://right> => count())", "{2}6");
            // ... two keys at one place take its demand apiece; two shares at one place, which no bias states, stay keyed
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}(<loop://left?split={2}part> => count(), "
                    + "<loop://left?split={2}part&timeout=20> => sum(), <loop://right?split=part> => count())", "{3,7,1}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}(<loop://left?split=part> => count(), "
                    + "<loop://left?split={2}part> => sum())", "{2,16}");
            // A LANE'S SHARE IS THE BRANCH'S BIAS (Marko, 2026-11-24: "?split= is bias and stays on branch"): a place set
            // deals each lane the demand of the designator naming its place, per lane; local names a home lane, and a
            // lane no designator names takes one share
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}&split=[//left,{2}//right]("
                    + "move(<loop://left>).count(), move(<loop://right>).count())", "{2,4}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}&split=[{2}//left]("
                    + "move(<loop://left>).count(), count())", "{4,2}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}&split=[{2}local,//left]("
                    + "count(), move(<loop://left>).count())", "{4,2}");
            // ... and a designator repeated is its demand summed, the brace-free spelling of the same bias
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}&split=[//right,//right]("
                    + "move(<loop://left>).count(), move(<loop://right>).count())", "{2,4}");
            // ... "per lane. treat each lane as if they are unique": two lanes at one place take its demand apiece, 2:2:1
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6,7,8,9,10}.branch?<=int{*}&split=[{2}//left,//right]("
                    + "move(<loop://left>).count(), move(<loop://left>).count(), move(<loop://right>).count())", "{4,4,2}");
            // A POINT IS COPIES, NEVER A SHARE ("cut{2}() will duplicate data"): an even part deals 1:1, and the lane's
            // point doubles what it answers, at a place or at home
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}&split=part(move(<loop://left>).count(), "
                    + "move{2}(<loop://right>).count())", "{3}3");
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}&split=part(move(<loop://left>).sum(), "
                    + "count{2}())", "{9,{2}3}");
            // a split the branch does not perform is refused, never read as a copy
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?<=int{*}&split=halves(move(<loop://left>).count(), "
                    + "count())", "<ERROR>");
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
            // A KEYED LANE IS A MOVE, ALREADY CUT: the place is shipped what a move to it ships, the lhs and the cut to
            // where in it the program runs (its root, as the key names no machine past the place's surface)
            AbstractMetatronTest.checkEquality(LOG, Machine.parse("start(1).cut(</.>,plus(1))"), shipped.get(), true);
            // ... and the program is the cut's tail whole: a lane's own explain() runs where the lane runs, where in a
            // chain the stretch after a move ends before it, the return home
            Machine.Helper.evalAt(Machine.current(), () -> Machine.parse("1.branch(<loop://probe> => plus(1).explain())").apply(noobj()));
            AbstractMetatronTest.checkEquality(LOG, Machine.parse("start(1).cut(</.>,plus(1).explain())"), shipped.get(), true);
            // A LANE'S BUDGET RIDES ON ITS CUT'S TARGET (Marko, 2026-11-24), shipped on the cut the place runs, which
            // hands it to the head of the tail it compiles
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(move(<loop://probe?hop=0>).plus(1))", "2");
            AbstractMetatronTest.checkEquality(LOG, Machine.parse("start(1).cut(</.?hop=0>,plus(1))"), shipped.get(), true);
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
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://slow?timeout=20> => plus(1))", "2");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://slow?timeout=soon> => plus(1))", "<ERROR>");
            // a timeout on the branch itself is every key's, and a key's own overrides it
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=1(<loop://slow> => plus(1))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=1(<loop://slow?timeout=2> => plus(1))", "2");
            // THE CUT HOLDS THE TIMEOUT (Marko, 2026-11-24): a move's compiles onto its cut, branch or no branch; a
            // branch's is copied onto each lane's cut, a lane's own winning; and a wait longer than a thread's own
            // default (five seconds) is the cut's, which the lane's thread does not cut short
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.move?timeout=1(<loop://slow>).plus(1)", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.move?timeout=2(<loop://slow>).plus(1)", "2");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=1(move(<loop://slow>).plus(1))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=1(move?timeout=2(<loop://slow>).plus(1))", "2");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.move?timeout=soon(<loop://slow>).plus(1)", "<ERROR>");
            // A HOME LANE HAS NO MOVE, so a cut that stays times it, a key's as a member's: it answers in time or fails
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<~?timeout=1> => sleep(second::3.0))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<~?timeout=5> => sleep(second::3.0))", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=1(sleep(second::3.0))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=5(sleep(second::3.0))", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=2(sleep(second::3.0),move(<loop://slow>).plus(1))", "<ERROR>");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=5(sleep(second::3.0),move(<loop://slow>).plus(1))", "{1,2}");
        } finally {
            Machine.current().network().drop(f("loop://slow"));
        }
    }

    /**
     * A WAIT LONGER THAN A THREAD'S OWN DEFAULT (five seconds) IS THE CUT'S: a lane's thread waits as long as any of its
     * cuts may run, so a place that answers in six seconds answers a lane that waits ten, shipped or home, keyed or
     * moved, and a home lane that moves after its head.
     */
    @Test
    public void testALongWaitIsTheCuts() {
        dial("loop://slower", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> {
            CommonUtil.sleepThread(6000);
            return Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()));
        })));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://slower?timeout=10> => plus(1))", "2");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(move?timeout=10(<loop://slower>).plus(1),plus(2))", "{2,3}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?timeout=10(<~> => sleep(second::6.0))", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<~?timeout=10> => sleep(second::6.0),<loop://slower?timeout=10> => plus(1))", "{1,2}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch?<=int{*}&split=part(plus(1).move?timeout=10(<loop://slower>).plus(1))", "3");
        } finally {
            Machine.current().network().drop(f("loop://slower"));
        }
    }

    /**
     * A RUN THAT OUTLIVES ITS TIME IS STOPPED (the cut's): the caller is told it failed and stays where it stood, as only
     * a run that answers in time persists its move, and the run's thread is stopped before it writes.
     */
    @Test
    public void testATimedOutRunIsStopped() {
        final Machine root = Machine.current();
        final Machine late = root.push(f("branch_late"));
        Machine.current(root);
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.move?timeout=1(<branch_late>).sleep(second::2.0).to(<~/branch/late>)", "<ERROR>");
            AbstractMetatronTest.checkEquality(LOG, uri(root.vid()), uri(Machine.current().vid()), true);
            CommonUtil.sleepThread(4000);
            Machine.Helper.evalAt(late, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/late>", "noobj");
                return noobj();
            });
            // the same run given the time it needs writes, so the write above was stopped, not missed
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.move?timeout=5(<branch_late>).sleep(second::2.0).to(<~/branch/late>)", "1");
            Machine.Helper.evalAt(late, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/late>", "1");
                return noobj();
            });
        } finally {
            Machine.Helper.evalAt(late, () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * A LANE THE BRANCH GAVE UP ON IS STOPPED: a lane with no timed cut is waited the thread's own five seconds, and one
     * that runs longer fails the branch and is stopped before its write, where the same lane in time writes.
     */
    @Test
    public void testALaneTheBranchGaveUpOnIsStopped() {
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "3.branch?<=int{*}&split=part(sleep(second::7.0).to(</branch/given_up>))", "<ERROR>");
            CommonUtil.sleepThread(5000);
            AbstractMetatronTest.checkCodeParseApply(LOG, "*</branch/given_up>", "noobj");
            AbstractMetatronTest.checkCodeParseApply(LOG, "3.branch?<=int{*}&split=part&timeout=10(sleep(second::7.0).to(</branch/given_up>))", "3");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*</branch/given_up>", "3");
        } finally {
            Machine.write(f("/branch/given_up"), noobj());
        }
    }

    /**
     * A TARGET COMPUTED WHEN THE MOVE RUNS NAMES NO PLACE YET: a member headed by such a move is a home lane, so a deal
     * hands it one share and its point is copies of what it answers. (A cut's target is a uri as written: a computed one
     * is refused when the cut binds.)
     */
    @Test
    public void testAComputedTargetIsAHomeLane() {
        try {
            Machine.write(f("/branch/to"), uri(f("~")));
            AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4}.branch?<=int{*}&split=part(move{2}(*<~/branch/to>).count(),count())", "{4,2}");
        } finally {
            Machine.write(f("/branch/to"), noobj());
        }
    }

    /**
     * A DEAL'S LANES ARE NEVER SUMMED OR FACTORED: under {@code ?split=part} each lane takes its own share, so two equal
     * lanes are two shares, never one lane with their summed point, and a head they share stays in each, as run ahead
     * of the deal it would change what each lane is dealt (a filter moves members between lanes). Each row: the
     * expression % its answer.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.branch?<=int{*}&split=part(count(),count())                      % {2,1}",
            "{1,2,3,4}.branch?<=int{*}&split=part(is(gt(1)).sum(),is(gt(1)).count())  % {3,2}",
            "{1,2,3,4,5,6}.branch?<=int{*}&split=part(count{2}(),sum())               % {{2}3,12}",
    }, delimiter = '%')
    public void testADealsLanesAreNeverSummed(final String mtron, final String answer) {
        AbstractMetatronTest.checkCodeParseApply(LOG, mtron.trim(), answer.trim());
    }

    @Test
    public void testAPathLaneRunsAtItsMachine() {
        // a path names a local machine: its lane is compiled and run there, so the lane's ~ is that machine's own. This
        // machine (<~>), a pushed machine and the root (</.>) are three lanes that share the parts, which a copy key
        // cannot join; a path key's own share, which no designator names, keeps its branch keyed; a path naming no
        // machine is refused, and so is a bare key, a path relative to this machine
        final Machine root = Machine.current();
        final Machine machine = root.push(f("branch_m"));
        try {
            Machine.Helper.evalAt(root, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "7.branch(</branch_m> => to(<~/branch/lane>))", "7");
                AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?dom=int{*}&split=part(<~> => count(), "
                        + "</branch_m> => count(), </.> => count())", "{3}2");
                AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?dom=int{*}&split=part(<~> => count(), "
                        + "</branch_m> => count(), </.?split=copy> => count())", "<ERROR>");
                AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?dom=int{*}(<~?split=part> => count(), "
                        + "</branch_m?split=part> => count())", "{2}3");
                AbstractMetatronTest.checkCodeParseApply(LOG, "{1,2,3,4,5,6}.branch?dom=int{*}(<~?split=part> => count(), "
                        + "</branch_m?split={2}part> => count())", "{2,4}");
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

    /**
     * A HOST KEY'S PATH IS WHERE ITS LANE RUNS, past the surface its peer was dialed at: a keyed lane is a move, so
     * {@code <loop://left/branch_far>} ships {@code move(</branch_far>)} ahead of the program and the place runs it in
     * that machine, whose {@code ~} the lane writes, as {@code move(<loop://left/branch_far>)} does. A peer dialed at
     * {@code loop://surf/mtron} answers there, its route an endpoint, never one of its machines, so a key or a move
     * naming the dial lands where the place's eval runs it and {@code <loop://surf/mtron/branch_far>} is its
     * {@code /branch_far}. The places here are this server (the loop client's eval runs what it is shipped at the root),
     * so the lanes land in the pushed {@code /branch_far} or at the root.
     */
    @Test
    public void testAHostKeysPathIsWhereItsLaneRuns() {
        final BiFunction<Obj, Inst, Obj> run = (lhs, inst) -> Network.Helper.boundary(Machine.root(), () -> lhs.apply(noobj()));
        dial("loop://left", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, run)));
        dial("loop://surf/mtron", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, run)));
        final Machine root = Machine.current();
        final Machine far = root.push(f("branch_far"));
        Machine.current(root);
        // a peer whose eval stands in a machine of its own, as a ws peer stands in the machine that owns its route
        dial("loop://deep", rec(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID,
                (lhs, inst) -> Network.Helper.boundary(far, () -> lhs.apply(noobj())))));
        try {
            AbstractMetatronTest.checkCodeParseApply(LOG, "9.branch(<loop://left/branch_far> => to(<~/branch/far>))", "9");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/far>", "noobj");
            Machine.Helper.evalAt(far, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/far>", "9");
                return noobj();
            });
            AbstractMetatronTest.checkCodeParseApply(LOG, "6.move(<loop://left/branch_far>).to(<~/branch/far>)", "6");
            Machine.Helper.evalAt(far, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/far>", "6");
                return noobj();
            });
            // A PEER IS ITS ROOT: a target that names no machine in the peer lands at its root, whatever machine the
            // peer's eval stands in (this one's stands in /branch_far), as a move to it lands
            AbstractMetatronTest.checkCodeParseApply(LOG, "7.move(<loop://deep>).to(<~/branch/deep>)", "7");
            AbstractMetatronTest.checkCodeParseApply(LOG, "8.branch(<loop://deep> => plus(0).to(<~/branch/deeper>))", "8");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/deep>", "7");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/deeper>", "8");
            Machine.Helper.evalAt(far, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/deep>", "noobj");
                return noobj();
            });
            // the dial's own address is the peer, not a machine in it named for its route
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.move(<loop://surf/mtron>).plus(1)", "2");
            AbstractMetatronTest.checkCodeParseApply(LOG, "1.branch(<loop://surf/mtron> => plus(1))", "2");
            AbstractMetatronTest.checkCodeParseApply(LOG, "5.branch(<loop://surf/mtron/branch_far> => to(<~/branch/far>))", "5");
            Machine.Helper.evalAt(far, () -> {
                AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/far>", "5");
                return noobj();
            });
            AbstractMetatronTest.checkCodeParseApply(LOG, "*<~/branch/far>", "noobj");
        } finally {
            Machine.current().network().drop(f("loop://left"));
            Machine.current().network().drop(f("loop://surf/mtron"));
            Machine.current().network().drop(f("loop://deep"));
            Machine.Helper.evalAt(far, () -> Machine.current().pop());
            Machine.current(root);
            Machine.write(f("/branch/far"), noobj());
            Machine.write(f("/branch/deep"), noobj());
            Machine.write(f("/branch/deeper"), noobj());
        }
    }

    /**
     * THE MERGE IS A RETURN HOME (Marko, 2026-11-24): a member that moves runs from the machine the branch stands in,
     * and its cut persists only until the member is done, so the next member and whatever follows the branch read this
     * machine's {@code ~}, where {@code where} is 1, never the {@code 2} of {@code /branch_away} the member moved to. A
     * keyed lane runs on a thread of its own, which the caller never shares. Each row: the expression % its answer.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.branch(move(<branch_away>).plus(1),*<~/branch/where>)                  % {6,1}",
            "5.branch(move(<branch_away>).plus(1),plus(2)).map(*<~/branch/where>)     % {2}1",
            "5.branch(move(<branch_away>).map(*<~/branch/where>),*<~/branch/where>)   % {2,1}",
            "5.branch?<=int{*}(move(<branch_away>).plus(1),*<~/branch/where>)         % {6,1}",
            "5.branch(<branch_away> => *<~/branch/where>,<~> => *<~/branch/where>)    % {2,1}",
    }, delimiter = '%')
    public void testTheMergeIsAReturnHome(final String mtron, final String answer) {
        final Machine root = Machine.current();
        final Machine away = root.push(f("branch_away"));
        Machine.current(root);
        try {
            Machine.write(f("/branch/where"), jnt(1));
            Machine.Helper.evalAt(away, () -> Machine.write(f("~/branch/where"), jnt(2)));
            AbstractMetatronTest.checkCodeParseApply(LOG, mtron.trim(), answer.trim());
        } finally {
            Machine.Helper.evalAt(away, () -> Machine.current().pop());
            Machine.current(root);
            Machine.write(f("/branch/where"), noobj());
        }
    }
}
