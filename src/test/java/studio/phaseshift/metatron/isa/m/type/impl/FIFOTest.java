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
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Computer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.processor.BasicSwarmProcessor;
import studio.phaseshift.metatron.isa.m.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.STOP;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.m.mach.type.thread.mThread.CURRENT_SYS_THREAD;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A BOUNDED HISTORY (Marko, 2026-11-25): {@link FIFOLst} and {@link FIFORec}, and the two places that keep one, the
 * executor's ended threads ({@code /sys/thread/executor/stop}) and a processor's computers ({@code ~/processor/computer}).
 * Isolated: the tests of those two places end a burst of threads and computers, which would hurry the history of every
 * test that ran beside them.
 */
@Isolated
public class FIFOTest extends AbstractMetatronTest {

    private static List<String> names(final String csv) {
        return null == csv || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).toList();
    }

    private static List<Obj> strs(final String csv) {
        return names(csv).stream().map(name -> (Obj) str(name)).toList();
    }

    /**
     * wait for a thread to be listed under stop: offered last, so it is the newest member the history holds
     */
    private static void awaitListedAsEnded(final fURI vid) {
        final long deadline = System.currentTimeMillis() + 5_000;
        while (!listedThreads(STOP).contains(vid)) {
            assertTrue(System.currentTimeMillis() < deadline, () -> "listed as ended: " + vid);
            CommonUtil.sleepThread(2);
        }
    }

    /**
     * end {@code count} minted threads one after another, each listed under stop before the next starts, so they are
     * offered in the order they are answered
     */
    private static List<fURI> endMintedThreads(final int count) throws Exception {
        final List<fURI> minted = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final VirtualThread thread = VirtualThread.virtual(instLambda(_ -> noobj()));
            thread.applyAsync().get(5, TimeUnit.SECONDS);
            awaitListedAsEnded(thread.vid());
            minted.add(thread.vid());
        }
        return minted;
    }

    /**
     * A FIFOLst KEEPS EVERY PINNED MEMBER AND THE NEWEST {@code cap} OF THE REST, in the order they arrived, and each
     * offer answers what it dropped, eldest first. Equal members are two arrivals. The rows are
     * {@code cap % offered % pinned % kept % dropped}.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "2 % ember,flint,slate        %                   % flint,slate        % ember",
            "2 % ember,flint,slate,tinder % ember             % ember,slate,tinder % flint",
            "1 % ember,flint,slate        % flint             % flint,slate        % ember",
            "0 % ember,flint              %                   %                    % ember,flint",
            "0 % ember,flint              % flint             % flint              % ember",
            "3 % ember,flint              %                   % ember,flint        % ",
            "1 % ember,flint,slate        % ember,flint,slate % ember,flint,slate  % ",
            "1 % ember,ember,ember        %                   % ember              % ember,ember",
    })
    void shouldKeepThePinnedAndTheNewestOfTheRest(final int cap, final String offered, final String pinned,
                                                  final String kept, final String dropped) {
        final FIFOLst history = new FIFOLst(cap);
        final Set<String> pins = Set.copyOf(names(pinned));
        final List<Obj> out = new ArrayList<>();
        for (final String name : names(offered))
            out.addAll(history.offer(str(name), pins.contains(name)));
        assertEquals(strs(kept), history.lstValue(), "what the history keeps");
        assertEquals(strs(dropped), out, "what it dropped, eldest first");
    }

    /**
     * A PIN IS AN ARRIVAL, NOT A VALUE: of two equal members offered, the first pinned, the second is the one to go.
     */
    @Test
    void shouldPinAnArrivalNotAValue() {
        final FIFOLst history = new FIFOLst(0);
        assertEquals(List.of(), history.offer(str("ember"), true), "a pinned member stays past the cap");
        assertEquals(List.of(str("ember")), history.offer(str("ember"), false), "an equal one offered unpinned goes");
        assertEquals(List.of(str("ember")), history.lstValue(), "and the pinned one is kept");
    }

    /**
     * OFFERS FROM MANY THREADS AT ONCE lose nothing and keep the bound: every member offered is kept or dropped exactly
     * once, and the cap holds once they are done.
     */
    @Test
    void shouldKeepTheBoundUnderConcurrentOffers() throws Exception {
        final FIFOLst history = new FIFOLst(100);
        final List<Obj> dropped = Collections.synchronizedList(new ArrayList<>());
        try (final ExecutorService pool = Executors.newFixedThreadPool(8)) {
            final List<Future<?>> offers = IntStream.range(0, 8).<Future<?>>mapToObj(t -> pool.submit(() -> {
                for (int i = 0; i < 500; i++)
                    dropped.addAll(history.offer(str(t + "/" + i), false));
            })).toList();
            for (final Future<?> offer : offers)
                offer.get(30, TimeUnit.SECONDS);
        }
        assertEquals(100, history.lstValue().size(), "the cap holds");
        final Set<Obj> seen = new HashSet<>(history.lstValue());
        dropped.forEach(obj -> assertTrue(seen.add(obj), () -> "kept or dropped once: " + obj));
        assertEquals(4000, seen.size(), "and every offer is one of them");
    }

    /**
     * A FIFORec KEEPS EVERY LIVE ENTRY AND THE NEWEST {@code cap} OF THE REST, by key, in the order they arrived; a
     * rewritten key keeps its place, and a write in place is the history itself. The rows are
     * {@code cap % written % live % kept}.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "2 % ember,flint,slate        %                   % flint,slate",
            "2 % ember,flint,slate,tinder % ember             % ember,slate,tinder",
            "2 % ember,flint,ember        %                   % ember,flint",
            "2 % ember,flint,ember,slate  %                   % flint,slate",
            "1 % ember,flint,ember        %                   % ember",
            "0 % ember,flint              % flint             % flint",
            "3 % ember,flint              %                   % ember,flint",
            "1 % ember,flint,slate        % ember,flint,slate % ember,flint,slate",
    })
    void shouldKeepTheLiveAndTheNewestOfTheRest(final int cap, final String written, final String live, final String kept) {
        final Set<Obj> lives = Set.copyOf(strs(live));
        final Rec history = new FIFORec(cap, lives::contains);
        for (final String name : names(written))
            assertSame(history, history.at(uri(name), str(name), Poly.MUTABLE), "a write in place is the history itself");
        assertEquals(names(kept).stream().map(name -> (Obj) uri(name)).toList(), List.copyOf(history.recValue().keySet()));
    }

    /**
     * ONLY AN ARRIVAL IS TRIMMED: a write below a kept entry drops nothing, and a write that answers a copy (IMMUTABLE)
     * leaves the history as it was, the copy trimmed as the history would have been.
     */
    @Test
    void shouldTrimOnlyAnArrival() {
        final Rec history = new FIFORec(1, _ -> false);
        history.at(uri("ember"), rec(), Poly.MUTABLE);
        history.at(uri("ember/flare"), str("bright"), Poly.MUTABLE);
        assertEquals(List.of(uri("ember")), List.copyOf(history.recValue().keySet()), "a write below a kept entry drops nothing");
        checkEquality(LOG, str("bright"), history.<Rec>at(uri("ember")).at(uri("flare")), true);
        final Rec copy = history.at(uri("flint"), rec(), Poly.IMMUTABLE);
        assertEquals(List.of(uri("ember")), List.copyOf(history.recValue().keySet()), "a copy leaves the history as it was");
        assertEquals(List.of(uri("flint")), List.copyOf(copy.recValue().keySet()), "and is trimmed itself");
    }

    /**
     * THE EXECUTOR KEEPS THE NEWEST {@link ThreadExecutor#HISTORY} ENDED THREADS AND EVERY NAMED ONE: a minted thread past
     * the history is dropped from stop and deleted at its vid, and its doc with it, since the doc holds the thread; a
     * named thread that ended first is still listed, and still read, after more minted threads than the history holds
     * have ended behind it.
     */
    @Test
    void shouldKeepTheNewestEndedThreadsAndEveryNamedOne() throws Exception {
        final VirtualThread named = VirtualThread.virtual(instLambda(_ -> noobj()),
                CURRENT_SYS_THREAD.extend("fifo_kiln_" + System.nanoTime()));
        named.applyAsync().get(5, TimeUnit.SECONDS);
        awaitListedAsEnded(named.vid());
        final VirtualThread documented = QCollection.docWrap(VirtualThread.virtual(instLambda(_ -> noobj())), "a kiln's spark");
        documented.applyAsync().get(5, TimeUnit.SECONDS);
        awaitListedAsEnded(documented.vid());
        assertInstanceOf(VirtualThread.class, Machine.read(documented.vid().addQ(DOCQ)).orElse(rec0()).at(uri(OBJ)),
                "a documented thread's doc holds the thread");
        final List<fURI> minted = endMintedThreads(ThreadExecutor.HISTORY + 20);
        final List<fURI> stopped = listedThreads(STOP);
        assertTrue(minted.stream().filter(stopped::contains).count() <= ThreadExecutor.HISTORY,
                () -> "no more than the history is listed: " + minted.stream().filter(stopped::contains).count());
        for (final fURI vid : minted.subList(0, 20)) {
            assertFalse(stopped.contains(vid), () -> "an eldest thread is dropped: " + vid);
            checkEquality(LOG, noobj(), Machine.read(vid), true);
        }
        assertFalse(stopped.contains(documented.vid()), "the documented thread is dropped");
        checkEquality(LOG, noobj(), Machine.read(documented.vid()), true);
        checkEquality(LOG, noobj(), Machine.read(documented.vid().addQ(DOCQ)).orElse(rec0()).at(uri(OBJ)), true);
        assertTrue(stopped.contains(named.vid()), "the named thread is still listed");
        assertInstanceOf(VirtualThread.class, Machine.read(named.vid()), "and still read at its vid");
    }

    /**
     * A COMPUTER RUN ON A THREAD OF ITS OWN (as a console line runs) IS NEVER NAMED: it leaves the executor's history as
     * a minted thread does, and its record, its processor's to keep or drop, is not deleted with it.
     */
    @Test
    void shouldDropAnAsyncComputerFromTheThreadsAndLeaveItsRecordToItsProcessor() throws Exception {
        final Machine machine = Machine.current().push(f("async_computer"));
        try {
            final AbstractThread computer = (AbstractThread) machine.processor().apply(MCode.of(List.of(instLambda(obj -> obj))));
            computer.applyAsync(jnt(1)).get(5, TimeUnit.SECONDS);
            awaitListedAsEnded(computer.vid());
            endMintedThreads(ThreadExecutor.HISTORY + 20);
            assertFalse(listedThreads(STOP).contains(computer.vid()), "the computer leaves the thread history");
            assertInstanceOf(Computer.class, Machine.read(computer.vid()), "and its record stays its processor's");
        } finally {
            machine.pop();
        }
    }

    /**
     * A PROCESSOR KEEPS EVERY RUNNING COMPUTER AND THE NEWEST {@link BasicSwarmProcessor#HISTORY} OF THE REST: past the
     * history the eldest computer that is not running is dropped from {@code ~/processor/computer}, a run that outlasts
     * them all keeps its computer indexed, and once that run has ended its computer goes next.
     */
    @Test
    void shouldKeepEveryRunningComputerAndTheNewestOfTheRest() throws Exception {
        final Machine machine = Machine.current().push(f("computer_history"));
        final CountDownLatch release = new CountDownLatch(1);
        try {
            final CountDownLatch underway = new CountDownLatch(1);
            final Computer walk = machine.processor().apply(MCode.of(List.of(instLambda(obj -> {
                underway.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return obj;
            }))));
            final VirtualThread walker = VirtualThread.virtual(instLambda(_ -> walk.apply(jnt(1))));
            walker.applyAsync();
            assertTrue(underway.await(5, TimeUnit.SECONDS), "the run is under way");
            final List<fURI> ended = new ArrayList<>();
            for (int i = 0; i < BasicSwarmProcessor.HISTORY + 20; i++) {
                final Computer computer = machine.processor().apply(MCode.of(List.of(instLambda(obj -> obj))));
                computer.apply(jnt(i));
                ended.add(computer.vid());
            }
            assertInstanceOf(Computer.class, Machine.read(walk.vid()), "a running computer is never dropped");
            assertTrue(ended.stream().filter(vid -> !Machine.read(vid).isNoObj()).count() <= BasicSwarmProcessor.HISTORY,
                    "no more than the history of the rest is kept");
            checkEquality(LOG, noobj(), Machine.read(ended.getFirst()), true);
            assertInstanceOf(Computer.class, Machine.read(ended.getLast()), "and the newest is kept");
            release.countDown();
            walker.future().get(5, TimeUnit.SECONDS);
            machine.processor().apply(MCode.of(List.of(instLambda(obj -> obj)))).apply(jnt(0));
            checkEquality(LOG, noobj(), Machine.read(walk.vid()), true);
        } finally {
            release.countDown();
            machine.pop();
        }
    }
}
