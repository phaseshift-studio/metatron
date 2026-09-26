/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it/or modify
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

package studio.phaseshift.metatron.isa.sys.type;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/*
 * The mtron-level execution stack: one frame per instruction step the current
 * thread is inside (arg resolution, inst apply, space reads/writes).  The
 * frames are a render-only artifact — they are attached to fail generation so
 * that `catch(cause())` and the fail-space entries show *where in the mtron
 * pipeline* an error happened, alongside the java stack (Throwable) which is
 * captured for free at fail creation.
 *
 * Thread-locality: each thread owns its Deque.  Spawned worker threads start
 * with an empty stack (a deliberately conservative choice — never share the
 * parent's Deque, whose push/pop could corrupt the parent's history).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExecutionStack {

    public enum ExState {
        create_value,
        create_type,
        resolve_inst,
        resolve_inst_args,
        apply_inst,
        apply_args
    }

    // cap so a runaway pipeline cannot unboundedly grow a fail message
    private static final int MAX_FRAMES = 64;
    // clip frame text so the fail message stays readable
    private static final int MAX_FRAME_LEN = 120;

    private static final ThreadLocal<Deque<ExecutionState>> STACK = new ThreadLocal<>();

    public record ExecutionState(ExState state, String message, Obj obj, Obj... objs) {
    }

    /**
     * Compact frame factories — one for state+message only, one adding a safe obj tid note.
     */
    public static ExecutionState exec(final ExState state, final String message) {
        return new ExecutionState(state, message, null);
    }

    public static ExecutionState exec(final ExState state, final String message, final Obj obj) {
        return new ExecutionState(state, message, obj);
    }

    private static Deque<ExecutionState> stack() {
        Deque<ExecutionState> stack = STACK.get();
        if (null == stack)
            STACK.set(stack = new ArrayDeque<>());
        return stack;
    }

    /**
     * Push a frame unless the stack has already reached its cap, in which case
     * the push is skipped (and reported to the caller, so it will not later
     * pop a frame that never landed) — the cap bounds the rendered fail
     * message against runaway depth, and the pop-for-push invariant holds
     * either way.
     */
    public static boolean push(final ExecutionState state) {
        final Deque<ExecutionState> stack = stack();
        if (stack.size() < MAX_FRAMES) {
            stack.push(state);
            return true;
        }
        return false;
    }

    public static void pop() {
        final Deque<ExecutionState> stack = stack();
        if (null == stack.peek())
            // a pop with no landed push — only reachable if something popped
            // outside {@link #frame}; the thread identity is the first clue
            throw MTronException.of("execution state stack corrupted — pop on empty stack (thread: %s)", Thread.currentThread().getName());
        stack.pop();
    }

    public static <T> T frame(final ExecutionState state, final Supplier<T> supplier) {
        final boolean pushed = push(state);
        try {
            return supplier.get();
        } finally {
            if (pushed)
                pop();
        }
    }

    /**
     * Snapshot render of the stack — innermost step first, without mutating
     * it (safe to call from any thread and at fail-generation time).
     * Consecutive identical frames (the signature of a self-referential read
     * looping on itself) collapse to one line plus a counted marker, so a
     * runaway renders as the loop it is instead of a staircase of it — and a
     * collapsed run consumes a single indent level, not one per frame.
     */
    public static String generateStackTrace() {
        final Deque<ExecutionState> stack = STACK.get();
        if (null == stack || stack.isEmpty())
            return "";
        final List<ExecutionState> snapshot = List.copyOf(stack);
        final StringBuilder builder = new StringBuilder();
        int indent = 0;
        int i = 0;
        while (i < snapshot.size()) {
            final ExecutionState state = snapshot.get(i);
            int n = 1;
            while (i + n < snapshot.size() && linesEqual(state, snapshot.get(i + n)))
                n++;
            appendFrame(builder, indent++, state);
            if (n > 1)
                // the marker is its own visual level — the next frame nests one
                // level below it, keeping the staircase's one-level-per-line rule
                appendLine(builder, indent++, "… (×" + (n - 1) + " more — same frame repeated: " + state.state().name()
                        + (null == state.message() || state.message().isEmpty() ? "" : ": " + state.message()));
            i += n;
        }
        return builder.toString();
    }

    private static void appendFrame(final StringBuilder builder, final int level, final ExecutionState state) {
        appendLine(builder, level, renderLine(state));
    }

    private static void appendLine(final StringBuilder builder, final int level, final String text) {
        if (!builder.isEmpty())
            builder.append('\n');
        for (int i = 0; i < level; i++)
            builder.append("    ");
        builder.append("\\_").append(text);
    }

    private static String renderLine(final ExecutionState state) {
        StringBuilder line = new StringBuilder(state.state().name() + (null == state.message() || state.message().isEmpty() ? "" : ": " + state.message()));
        if (null != state.obj())
            line.append(" lhs=").append(tidOf(state.obj()));
        final Obj[] objs = state.objs();
        if (null != objs)
            for (final Obj obj : objs)
                line.append(", ").append(tidOf(obj));
        if (line.length() > MAX_FRAME_LEN)
            line = new StringBuilder(line.substring(0, MAX_FRAME_LEN) + "...");
        return line.toString();
    }

    private static boolean linesEqual(final ExecutionState a, final ExecutionState b) {
        return renderLine(a).equals(renderLine(b));
    }

    private static String tidOf(final Obj obj) {
        try {
            final var tid = obj.tid();
            return null == tid ? String.valueOf(obj) : tid.toString();
        } catch (final Exception e) {
            return obj.getClass().getSimpleName();
        }
    }

    public static boolean empty() {
        final Deque<ExecutionState> stack = STACK.get();
        return null == stack || stack.isEmpty();
    }

    public static void clear() {
        STACK.remove();
    }

}
