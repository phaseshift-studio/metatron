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

package studio.phaseshift.metatron.isa.m.mach.type.processor;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.PROCESSOR;
import static studio.phaseshift.metatron.Tokens.TRACER;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tracer — the processor's diagnostics, in the form of the compiler's {@code Typer} (Marko, 2026-11-10: "We should make
 * Tracer like Typer ... they should have similar forms"): stack traces, computational tracing (in development), and
 * the control of a processor's stack.
 * <p>
 * THE STATE IS THE TRACER'S REC ({@code ~/processor/tracer}), not a registry: a {@code stack} field holds one flag
 * per trace point ({@link #MTRON_STACK}, {@link #JAVA_STACK}), a {@code bool::T} each, and grouping them under
 * {@code stack} leaves the tracer free to acquire more features beside it.
 * <pre>
 * tracer::[stack=>[mtron_stack=>bool::T, java_stack=>bool::T]]
 * </pre>
 * {@link Helper} reads the current machine's tracer live, as {@code Typer.Helper} reads its typer.
 */
public interface Tracer extends Component {

    // ======================== the tracer's state ========================

    /**
     * the state field: one flag per trace point.
     */
    fURI STACK = f("stack");

    /**
     * render the mtron stack trace of an uncaught {@code fail::T}.
     */
    fURI MTRON_STACK = f("mtron_stack");

    /**
     * render the java stack trace of an uncaught {@code fail::T}.
     */
    fURI JAVA_STACK = f("java_stack");

    /**
     * every trace point, in the order the boot profile and the console present them.
     */
    List<fURI> STACKS = List.of(MTRON_STACK, JAVA_STACK);

    /**
     * the boot default: every trace point off.
     */
    static boolean defaultsOn(final fURI flag) {
        return false;
    }

    /**
     * the boot default as a rec — LAZY, as {@code Typer.defaults()} is: building the flags mints bools, whose
     * construction runs a type check.
     */
    static Rec defaults() {
        return Defaults.STACK;
    }

    final class Defaults {
        private static final Rec STACK = rec(
                uri(MTRON_STACK), BOOL_FALSE,
                uri(JAVA_STACK), BOOL_FALSE);

        private Defaults() {
        }
    }

    /**
     * this tracer's state — its {@code stack} rec, the boot defaults when it declares none.
     */
    default Rec stack() {
        final Obj stack = this.at(uri(STACK));
        return stack.isNoObj() ? rec() : stack.asRec();
    }

    /**
     * is a trace point on IN THIS TRACER? A point the state does not name falls back to the boot default.
     */
    default boolean traces(final fURI flag) {
        final Obj on = this.stack().atDirect(uri(flag));
        return on.isNoObj() ? defaultsOn(flag) : on.boolValue();
    }

    /**
     * turn a trace point on or off IN THIS TRACER — the state is the tracer's own rec, so a machine, a pushed frame,
     * or a test boot can each set its own.
     */
    default Tracer traces(final fURI flag, final boolean value) {
        this.at(uri(STACK.name() + "/" + flag.name()), bool(value), MUTABLE);
        return this;
    }

    final class Helper {

        private Helper() {
        }

        /**
         * the current machine's tracer state, read LIVE (a write replaces the node it writes). Before the boot has a
         * machine to ask there is none, and the boot default answers.
         */
        private static Obj stack() {
            if (!Machine.loaded())
                return noobj();
            final Machine machine = Machine.current();
            if (null == machine)
                return noobj();
            final Obj processor = machine.atDirect(uri(PROCESSOR));
            final Obj tracer = processor.isRec() ? processor.asRec().atDirect(uri(TRACER)) : noobj();
            return tracer.isRec() ? tracer.asRec().atDirect(uri(STACK)) : noobj();
        }

        /**
         * is a trace point on in the current machine's tracer?
         */
        public static boolean traces(final fURI flag) {
            final Obj stack = stack();
            final Obj on = stack.isRec() ? stack.asRec().atDirect(uri(flag)) : noobj();
            return on.isNoObj() ? defaultsOn(flag) : on.boolValue();
        }

        public static boolean mtronStack() {
            return traces(MTRON_STACK);
        }

        public static boolean javaStack() {
            return traces(JAVA_STACK);
        }

        /**
         * turn a trace point on or off in the current machine's tracer — the ordinary nested write, whose mtron
         * spelling is {@code @~/processor/tracer/stack >>= [java_stack => true]}.
         */
        public static void set(final fURI flag, final boolean value) {
            if (!Machine.loaded() || null == Machine.current())
                return;
            final Obj processor = Machine.current().atDirect(uri(PROCESSOR));
            final Obj tracer = processor.isRec() ? processor.asRec().atDirect(uri(TRACER)) : noobj();
            if (tracer.isRec())
                tracer.asRec().at(uri(STACK.name() + "/" + flag.name()), bool(value), MUTABLE);
        }

        /**
         * flip a trace point in the current machine's tracer; answers its new state.
         */
        public static boolean toggle(final fURI flag) {
            final boolean on = !traces(flag);
            set(flag, on);
            return on;
        }

        /**
         * the trace points currently on, in declaration order.
         */
        public static Set<fURI> enabled() {
            final Set<fURI> enabled = new LinkedHashSet<>();
            STACKS.stream().filter(Helper::traces).forEach(enabled::add);
            return enabled;
        }

        /**
         * the trace point named {@code name}, when it is one — by its name or its short form ({@code java} for
         * {@code java_stack}), as the console spells it.
         */
        public static Optional<fURI> flag(final String name) {
            return STACKS.stream()
                    .filter(flag -> flag.name().equalsIgnoreCase(name) || flag.name().equalsIgnoreCase(name + "_stack"))
                    .findFirst();
        }
    }
}
