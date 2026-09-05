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
import java.util.Collections;
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

    /** Compact frame factories — one for state+message only, one adding a safe obj tid note. */
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

    public static void push(final ExecutionState state) {
        final Deque<ExecutionState> stack = stack();
        if (stack.size() < MAX_FRAMES)
            stack.push(state);
    }

    public static void pop() {
        final Deque<ExecutionState> stack = stack();
        if (null == stack.peek())
            throw MTronException.of("execution state stack corrupted");
        stack.pop();
    }

    public static <T> T frame(final ExecutionState state, final Supplier<T> supplier) {
        ExecutionStack.push(state);
        try {
            return supplier.get();
        } finally {
            ExecutionStack.pop();
        }
    }

    /**
     * Snapshot render of the stack — innermost step first, without mutating
     * it (safe to call from any thread and at fail-generation time).
     */
    public static String generateStackTrace() {
        final Deque<ExecutionState> stack = STACK.get();
        if (null == stack || stack.isEmpty())
            return "no execution state stack";
        final List<ExecutionState> snapshot = List.copyOf(stack);
        final StringBuilder builder = new StringBuilder();
        int indent = 0;
        for (final ExecutionState state : snapshot) {
            if (!builder.isEmpty())
                builder.append('\n');
            for (int i = 0; i < indent; i++)
                builder.append("    ");
            indent++;
            String line = state.state().name() + (null == state.message() || state.message().isEmpty() ? "" : ": " + state.message());
            if (null != state.obj())
                line += " lhs=" + tidOf(state.obj());
            final Obj[] objs = state.objs();
            if (null != objs)
                for (final Obj obj : objs)
                    line += ", " + tidOf(obj);
            if (line.length() > MAX_FRAME_LEN)
                line = line.substring(0, MAX_FRAME_LEN) + "...";
            builder.append("\\_").append(line);
        }
        return builder.toString();
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
