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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.sysInstSet;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * Foreground job control: the poll loop that waits out a foreground job while
 * the watcher classifies the keystrokes, {@code <alt>+b} detaching the job to
 * the background, ctrl-c stopping it, and the {@code [q]} cancel offer.
 * <p>
 * Owns the job's control state ({@code watching}, the three request flags,
 * the hotkeys classifier, the detached-jobs list) and the terminal handoff
 * protocol between the watcher's reads and the prompt.  The facade keeps the
 * {@code machine} field and the echo/inject/cross-domain calls — those belong
 * to the repl and the reader, not to the job.
 */
public final class ForegroundJobs {

    // ========== cadence ==========
    /**
     * cadence of the foreground poll — small enough that {@code <alt>+b} feels instant
     */
    private static final long FOREGROUND_POLL_MS = 120;
    /**
     * how long a job may hold the console before it offers to be cancelled
     */
    private static final long CANCEL_OFFER_MS = 10_000;
    /**
     * how often the idle watcher re-checks whether a job took the console
     */
    private static final long WATCHER_IDLE_MS = 25;
    /**
     * how long the prompt waits for the watcher to leave its read before taking the terminal back
     */
    private static final long WATCH_HANDOFF_MS = 400;
    /**
     * how long one watcher read waits for a keystroke.  It must be finite: with
     * timeout 0 a raw-mode read never expires, so the watcher would still be
     * parked inside a read when the job ends, the handoff would time out, and
     * restoring the cooked attributes would turn that read into a blocking one
     * that swallows the user's next keystroke — the half-eaten arrow key, where
     * the escape goes to the watcher and the rest lands in the line as text.
     */
    private static final long WATCH_READ_MS = 100;
    private final Console console;

    /**
     * Keystrokes typed while a foreground job runs.  The repl polls the job's
     * future instead of blocking on it, so {@code <alt>+b} can detach the job
     * and give the keyboard back; the classifier turns those keystrokes into a
     * detach, a cancel, or text for the next prompt.
     */
    private final Hotkeys hotkeys = new Hotkeys();

    /**
     * True while a foreground job holds the console — the window in which the
     * repl thread is not inside {@code readLine()} and the {@link #watchTerminal}
     * thread owns the terminal's keystrokes.
     */
    private volatile boolean watching = false;

    /**
     * set by the watcher when {@code <alt>+b} is pressed while a job runs
     */
    private final AtomicBoolean detachRequested = new AtomicBoolean(false);

    /**
     * set by the watcher when ctrl-c arrives as a byte — sigint is a no-op off the prompt
     */
    private final AtomicBoolean interruptRequested = new AtomicBoolean(false);

    /**
     * set by the watcher when the cancel key answers an armed cancel offer
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    /**
     * stops the watcher when the console closes
     */
    private volatile boolean watcherClosed = false;

    /**
     * Orders the terminal handoff between the watcher's read and the prompt:
     * {@code watching} and {@code watchingRead} are only touched under it.
     */
    private final Object watchGate = new Object();

    /**
     * true while the watcher sits inside a terminal read
     */
    private boolean watchingRead = false;

    /**
     * Jobs detached with {@code <alt>+b} that are still running.  A detached
     * job is untouched — its own thread keeps updating widgets — this list only
     * tracks it so its result can be printed when it halts.
     */
    private final List<Machine> backgroundJobs = java.util.Collections.synchronizedList(new ArrayList<>());

    public ForegroundJobs(final Console console) {
        this.console = console;
    }

    public Hotkeys hotkeys() {
        return this.hotkeys;
    }

    public boolean watching() {
        return this.watching;
    }

    /**
     * @return true when ctrl-c was requested for the running job — the repl's
     * result stream stops consuming past it
     */
    public boolean interruptRequested() {
        return this.interruptRequested.get();
    }

    /**
     * The ctrl-c path while a job holds the console: stop the active pane's
     * machine when there is one, else the turn's machine — sigint is a no-op
     * off the prompt, so this handler is the interrupt.
     */
    public void interruptActive() {
        final Console console = this.console;
        if (console.getActivePane() != null && console.getActivePane().machine() != null) {
            console.getActivePane().machine().stop();
        } else if (null != console.machine) {
            console.machine.stop();
        }
    }

    /**
     * Tell the watcher to stop and give the terminal back to the prompt.
     */
    public void close() {
        synchronized (this.watchGate) {
            this.watcherClosed = true;
            this.watching = false;
            this.watchGate.notifyAll();
        }
    }

    /**
     * Poll a foreground job's future until it completes, watching the terminal
     * while we wait.  The repl thread holds the reader here — jline is not
     * reading — so keystrokes typed during the job belong to the console:
     * {@code <alt>+b} detaches the job, {@code [q]} cancels it once the cancel
     * offer has been printed, and anything else is kept for the next prompt.
     *
     * @param mach   the machine running the foreground job
     * @param future the job's future
     * @param line   the console line that started the job
     * @return true when the job was detached with {@code <alt>+b} (it is still
     * running — the console returns to the prompt)
     */
    public boolean awaitForeground(final Machine mach, final FutureObj<Obj> future, final String line) {
        long offerAtMs = System.currentTimeMillis() + CANCEL_OFFER_MS;
        this.detachRequested.set(false);
        this.cancelRequested.set(false);
        this.interruptRequested.set(false);
        // jline restores the terminal's cooked attributes when readLine()
        // returns, so outside the prompt keystrokes are line-buffered: alt+b
        // would not arrive until Enter.  Raw mode for the life of the job makes
        // every keypress a byte the watcher can classify.  jline saves and
        // restores its own attributes around each readLine, so handing the
        // cooked attributes back before returning is exactly what it expects.
        final Terminal terminal = Console.getTerminal();
        final Attributes cooked = terminal.enterRawMode();
        // the watcher owns the terminal from here until this job is over: the
        // repl thread is parked on the future below, so jline is not reading
        synchronized (this.watchGate) {
            this.watching = true;
            this.watchGate.notifyAll();
        }
        try {
            final FutureObj<Obj> job = future;
            while (!job.isDone()) {
                try {
                    job.get(FOREGROUND_POLL_MS);
                } catch (final Exception e) {
                    // a poll window ending is the normal path — the job is still running
                }
                if (job.isDone())
                    break;
                final boolean detach = this.detachRequested.getAndSet(false);
                final boolean interrupt = this.interruptRequested.getAndSet(false);
                final boolean cancel = this.cancelRequested.getAndSet(false);
                switch (Console.foregroundStep(detach, interrupt, cancel, Console.userMode.get(),
                        System.currentTimeMillis(), offerAtMs)) {
                    case DETACH -> {
                        this.detachForegroundJob(mach, job, line);
                        return true;
                    }
                    case INTERRUPT -> mach.stop();
                    case CANCEL -> {
                        this.hotkeys.disarmCancel();
                        this.console.write(Graphitty.string("{{-X-&|0}}"));
                        job.cancel(true);
                    }
                    case OFFER_CANCEL -> {
                        this.hotkeys.armCancel();
                        offerAtMs = System.currentTimeMillis() + CANCEL_OFFER_MS;
                        terminal.writer().write(Highlighter.format(
                                "{{-X-}}{{k}}cancel stream with [q] or background with <" + Hotkeys.DETACH_COMBO + "> {{X}}\r"));
                        terminal.writer().flush();
                    }
                    case WAIT -> {
                    }
                }
            }
            return false;
        } finally {
            this.releaseTerminal(cooked);
        }
    }

    /**
     * Read the terminal while a foreground job holds the console, one keystroke
     * at a time, and classify each one through {@link Hotkeys}.  This is the
     * only reader in that window — jline is parked outside {@code readLine()} —
     * and a bounded read is exactly right here: the reading happens on this
     * thread, never on the repl thread, so the console's poll loop stays live
     * and the read can still be released when the job ends.
     *
     * <p>A terminal read cannot be interrupted, so a read that started while the
     * job ran may complete after it ended.  A keystroke taken that way is handed
     * back to the line being typed ({@link Console#injectTypedText}) — the prompt
     * is live by then and jline never saw it.
     */
    public void watchTerminal() {
        final Terminal terminal = Console.getTerminal();
        while (!this.watcherClosed) {
            if (!this.beginWatch()) {
                // The prompt owns the terminal: keep the pointer alive for
                // whatever widgets are on screen (a widget floated while the
                // user sat at the prompt turns the mouse on within a tick).
                if (!this.watching) this.console.syncWidgetMouseTracking();
                CommonUtil.sleepThread(WATCHER_IDLE_MS);
                continue;
            }
            try {
                final int c = terminal.reader().read(WATCH_READ_MS);
                if (org.jline.utils.NonBlockingReader.READ_EXPIRED == c) {
                    // nothing typed: a half-seen escape sequence ends here, so a
                    // stale escape can never swallow the next keystroke
                    this.hotkeys.reset();
                    continue;
                }
                if (c < 0)
                    continue;  // eof — nothing to classify
                // a job asking the human takes the keystrokes first: this thread is the only reader
                // while the console is busy, so the answer has to be served from here (see readHumanLine)
                if (this.console.human().answer(c)) continue;
                final boolean onWatch = this.watching;
                switch (this.hotkeys.accept(c)) {
                    case DETACH -> this.detachRequested.set(true);
                    case INTERRUPT -> this.interruptRequested.set(true);
                    case CANCEL -> this.cancelRequested.set(true);
                    case TEXT -> {
                        if (onWatch)
                            this.console.echoTypedChar(c);
                        else
                            this.console.injectTypedText();
                    }
                    case NONE -> {
                    }
                }
            } catch (final Exception e) {
                // a failed read must never kill the watcher — the repl still works
                CommonUtil.sleepThread(WATCHER_IDLE_MS);
            } finally {
                this.endWatch();
            }
        }
    }

    /**
     * Take the terminal for one read.  False when no job holds the console —
     * the prompt owns the keystrokes then, and the watcher must not be a second
     * reader racing jline for them.
     */
    private boolean beginWatch() {
        synchronized (this.watchGate) {
            if (!this.watching && !this.hotkeys.inSequence())
                return false;
            this.watchingRead = true;
            return true;
        }
    }

    /**
     * give the terminal back, waking anyone waiting on the handoff
     */
    private void endWatch() {
        synchronized (this.watchGate) {
            this.watchingRead = false;
            this.watchGate.notifyAll();
        }
    }

    /**
     * Hand the terminal back to the prompt.  The order matters: restoring the
     * cooked attributes while a raw-mode read is still pending turns that read
     * into a blocking one, which then swallows the user's next keystroke.  So
     * the watcher is told to stop first and given a moment to leave its read —
     * in raw mode a read with no input expires in about 100ms — and only then do
     * the cooked attributes go back.
     */
    private void releaseTerminal(final Attributes cooked) {
        synchronized (this.watchGate) {
            this.watching = false;
            this.watchGate.notifyAll();
            final long end = System.currentTimeMillis() + WATCH_HANDOFF_MS;
            while (this.watchingRead && System.currentTimeMillis() < end) {
                try {
                    this.watchGate.wait(WATCH_HANDOFF_MS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        Console.getTerminal().setAttributes(cooked);
    }

    /**
     * Hand a running foreground job to the background — {@code <alt>+b}.  The
     * job is not touched: it keeps its own thread, keeps updating widgets, and
     * stays addressable at {@code /sys/thread/<vid>}.  The console only stops
     * waiting on it and stops treating it as foreground work, so ctrl-c at the
     * returning prompt cannot kill it.  A collector thread prints its result
     * when it halts.
     */
    private void detachForegroundJob(final Machine mach, final FutureObj<Obj> future, final String line) {
        this.backgroundJobs.add(mach);
        // the turn no longer owns this machine: clear both places the interrupt
        // paths read (the ctrl-c signal handler and UserInterrupt handling)
        this.console.machine = null;
        if (this.console.getActivePane() != null)
            this.console.getActivePane().clearMachine();
        QCollection.docWrap(
                VirtualThread.virtual(MInst.instLambda((lhs, inst) -> {
                    this.printBackgroundResult(mach, future);
                    return noobj();
                }), sysInstSet.SYS.extend("thread/console_background")),
                "background result collector").applyAsync();
        this.console.logger().none("{{-X-&|0}}");
        this.console.logger().info("<%s> => background %s (:bg to list)", Hotkeys.DETACH_COMBO, Console.preview(line));
        Console.getTerminal().flush();
    }

    /**
     * Print the result of a detached job, once its future resolves.  Runs on
     * the job's collector thread.
     */
    private void printBackgroundResult(final Machine mach, final FutureObj<Obj> future) {
        final Obj result;
        try {
            result = future.get();
        } catch (final Exception e) {
            this.backgroundJobs.remove(mach);
            this.console.logger().none("{{-X-&|0}}");
            this.console.printResult(MFail.fail(e));
            return;
        }
        // the job has halted — only now drop it from the tracking list, so
        // :bg lists it for the whole time it is still running
        this.backgroundJobs.remove(mach);
        final fURI vid = vidOf(mach);
        this.console.logger().none("{{-X-&|0}}\r[{{g}}result start{{/g}}:%s]\n", null == vid ? "?" : vid.toString());
        this.console.printResult(result);
        this.console.logger().none("[{{g}}result end{{/g}}:%s]\n", null == vid ? "?" : vid.toString());
    }

    /**
     * a machine's thread vid, when it has one — every machine is a rec at run-time
     */
    private static fURI vidOf(final Machine mach) {
        return (mach instanceof Obj obj) ? obj.vid() : null;
    }

    /**
     * @return the jobs detached with {@code <alt>+b} that have not yet halted,
     * in detach order
     */
    public List<Machine> backgroundJobs() {
        synchronized (this.backgroundJobs) {
            return List.copyOf(this.backgroundJobs);
        }
    }

    /**
     * @return the thread vids of the jobs detached with {@code <alt>+b} — each
     * one is addressable at {@code /sys/thread/<vid>}
     */
    public List<fURI> backgroundJobVids() {
        final List<fURI> vids = new ArrayList<>();
        synchronized (this.backgroundJobs) {
            for (final Machine job : this.backgroundJobs) {
                final fURI vid = vidOf(job);
                if (null != vid)
                    vids.add(vid);
            }
        }
        return vids;
    }

    /**
     * Stop every detached job.  Returns the number of jobs stopped.
     */
    public int stopBackgroundJobs() {
        final List<Machine> jobs = this.backgroundJobs();
        jobs.forEach(Machine::stop);
        synchronized (this.backgroundJobs) {
            this.backgroundJobs.clear();
        }
        return jobs.size();
    }
}
