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

package studio.phaseshift.metatron.isa.sys.type;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExecutionStack {

    public enum ExState {
        create_value,
        create_type,
        resolve_inst,
        resolve_inst_args,
        eval_inst
    }

    private final Deque stack = new ArrayDeque<>();

    public static InheritableThreadLocal<Deque<ExecutionState>> THREAD_EXECUTION_STACK = new InheritableThreadLocal<>();

    public record ExecutionState(ExState state, String message, Obj obj, Obj... objs) {
    }

    public static void push(final ExecutionState state) {
        Deque<ExecutionState> stack = THREAD_EXECUTION_STACK.get();
        if (null == stack)
            THREAD_EXECUTION_STACK.set(stack = new ArrayDeque<>());
        stack.push(state);
    }

    public static void pop() {
        Deque<ExecutionState> stack = THREAD_EXECUTION_STACK.get();
        if (null == stack)
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

    public String generateStackTrace() {
        Deque<ExecutionState> stack = THREAD_EXECUTION_STACK.get();
        if (null == stack)
            return "no execution state stack";
        final StringBuilder builder = new StringBuilder();
        int counter = 0;
        while (!stack.isEmpty()) {
            builder.repeat(" ", counter++)
                    .append("\\_")
                    .append(stack.pop())
                    .append("\n");
        }
        return builder.toString().trim();

    }


}
