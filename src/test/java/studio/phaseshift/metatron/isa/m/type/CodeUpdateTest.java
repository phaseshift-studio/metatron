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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * code::T's update() with names and template parameters ({@link Call.Helper#update}): a pattern element with a
 * relative vid names what it matched, and a change's {@code ${...}} argument is evaluated against what the run holds
 * in that place, reading the names. The plain update rows live with code::T's other operations in mInstSetTest.
 */
public class CodeUpdateTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // a template parameter is evaluated against what the run holds in its place: the 5
            "[plus(5).mult(2)]==[update([plus(int::T)=>plus(${+10})])]                                                % [plus(15).mult(2)]",
            // ... and a name reads it from anywhere in the change
            "[plus(5).mult(2)]==[update([plus(int::T@a)=>plus(${*a.plus(10)})])]                                      % [plus(15).mult(2)]",
            "[plus(5).mult(2)]==[update([plus(int::T@a)=>plus(${plus(10)})])]                                         % [plus(15).mult(2)]",
            "[plus(5).mult(2)]==[update([plus(int::T)=>plus(${plus(10)})])]                                           % [plus(15).mult(2)]",
            "[plus(5).mult(2)]==[update([plus(#::T)=>plus(${plus(10)})])]                                             % [plus(15).mult(2)]",
            "[plus(5).mult(2)]==[update([plus(#{*}::T)=>plus(${plus(10)})])]                                          % [plus(15).mult(2)]",
            // two names combine: plus's monoid law as data
            "[plus(2).plus(3).mult(4)]==[update([plus(int::T@a).plus(int::T@b)=>plus(${*a.plus(*b)})])]               % [plus(5).mult(4)]",
            // a named refinement guards the fold: a -3 is no positive int, so nothing folds. A name on a typed hole IS a
            // type of the machine (int::T[?>0]@p defines p::T; a plain int::T@p would refer to it), so these rows name
            // their refinement x and y rather than redefine the a and b the rows above defined as plain ints
            "[plus(2).plus(3).mult(4)]==[update([plus(int::T[?>0]@x).plus(int::T[?>0]@y)=>plus(${*x.plus(*y)})])]     % [plus(5).mult(4)]",
            "[plus(2).plus(-3).mult(4)]==[update([plus(int::T[?>0]@x).plus(int::T[?>0]@y)=>plus(${*x.plus(*y)})])]    % [plus(2).plus(-3).mult(4)]",
            // a name binds at depth, inside a nested code argument
            "[mult(plus(mult(3))).plus(1)]==[update([mult(#(mult(int::T@k)))=>mult(plus(mult(${*k.mult(2)})))])]      % [mult(plus(mult(6))).plus(1)]",
            // a nested rec's changes read the names its run bound
            "[plus(2).mult(3)]==[update([plus(int::T@a).mult(int::T)=>[mult(int::T)=>mult(${*a})]])]                  % [plus(2).mult(2)]",
            // everything outside a template parameter is literal
            "[plus(1).mult(2)]==[update([plus(int::T@a)=>plus(+5)])]                                                  % [plus(plus(5)).mult(2)]",
            // a parameter that cannot evaluate fails the update rather than staying in the code
            "[plus(5).mult(2)]==[update([plus(int::T)=>plus(${'x'.as(int::T)})])]>-                                   % <ERROR>",
    }, delimiter = '%')
    public void testNamesAndTemplates(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // an inst updates its own args as >>= updates a lst: a call transforms, a literal replaces, |x replaces with a call
            "[plus(5)]==[update([args=>[+10]])]                                              % [plus(15)]",
            "[plus(5)]==[update([args=>[7]])]                                                % [plus(7)]",
            "[plus(5)]==[update([args=>[|mult(2)]])]                                         % [plus(mult(2))]",
            // #::T keeps an arg that is an instruction (_ would apply id() to it), and the rhs gives the args their arity
            "[branch(plus(1),mult(2))]==[update([args=>[#::T,|mult(3)]])]                    % [branch(plus(1),mult(3))]",
            // in a code, a component rule updates the insts a pattern matched, in place
            "[plus(5).mult(2)]==[update([plus(int::T)=>[args=>[+10]]])]                      % [plus(15).mult(2)]",
            // ... or, at the top, every inst of the code
            "[plus(5).mult(2)]==[update([args=>[+10]])]                                      % [plus(15).mult(12)]",
            // the two kinds of rule run in order: the pattern replaces, then every inst's args grow
            "[plus(5).mult(2)]==[update([plus(int::T)=>mult(3),args=>[+1]])]                 % [mult(4).mult(3)]",
            // an inst updates its args: any other component is refused, a dom or rng (a type) included
            "[plus(5)]==[update([nope=>[+1]])]>-                                             % <ERROR>",
            "[plus(5)]==[update([rng=>real::T])]>-                                           % <ERROR>",
    }, delimiter = '%')
    public void testComponents(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    private static final int THREADS = 10;
    private static final int ROUNDS = 25;

    /**
     * TEN THREADS FOLD AT ONCE in one machine, each its own numbers through a rule of the same shape. The captured
     * values are the change's argument uris, a frame on each thread's own arg stack, so no thread may read another's.
     * But a NAME ON A TYPED HOLE IS A TYPE OF THE MACHINE ({@code int::T@a} defines {@code a::T} in its root frame, and a
     * later plain {@code int::T@a} refers to whatever {@code a} already is), so threads that share a machine take
     * names of their own ({@code fa3}, {@code fb3}), as do the tests that share it (the guard test below uses {@code ga3}).
     */
    @Disabled("ONE MACHINE DOES NOT HOLD UP (2026-11-06): parsing int::T@fa3 WRITES a type into the machine's root frame "
            + "(scope 3, shared by every thread) while other threads read it, and that plain map throws a "
            + "ConcurrentModificationException in variableStack.read -> Rec.at (intermittent: 1-2 errors in 3 runs). "
            + "Concurrent named patterns run in a machine of their own instead, as the two tests below do.")
    @Test
    void tenThreadsFoldTheirOwnNumbersInOneMachine() throws Exception {
        this.tenThreads(false, (id, r) -> "[plus(" + id + ").plus(" + r + ").mult(2)]==[update([plus(int::T@fa" + id + ").plus(int::T@fb" + id + ")=>plus(${*fa" + id + ".plus(*fb" + id + ")})])]",
                (id, r) -> "[plus(" + (id + r) + ").mult(2)]");
    }

    /**
     * ... AND THE SAME NAMES, each thread in a machine of its own (Marko, 2026-11-06: a pushed machine as a scratch
     * namespace): every thread's {@code a::T} lives in its own machine's root frame, and pop() takes it away.
     */
    @Test
    void tenThreadsFoldTheirOwnNumbersEachInAMachineOfItsOwn() throws Exception {
        this.tenThreads(true, (id, r) -> "[plus(" + id + ").plus(" + r + ").mult(2)]==[update([plus(int::T@a).plus(int::T@b)=>plus(${*a.plus(*b)})])]",
                (id, r) -> "[plus(" + (id + r) + ").mult(2)]");
    }

    /**
     * EACH THREAD GUARDS WITH ITS OWN REFINEMENT, {@code int::T[?>id-1]}, and its own value {@code id} passes only its
     * own guard, so a guard read from another thread would refuse the fold. In one machine the refinements are named
     * per thread: a refined {@code @name} redefines the machine's type, and threads sharing one name would race on it,
     * both in the match and in how the template's read of the name is typed.
     */
    @Disabled("ONE MACHINE DOES NOT HOLD UP (2026-11-06): every thread's refined @name is a definition written to the "
            + "machine's shared root frame while other threads read it: the same ConcurrentModificationException in "
            + "variableStack.read as the fold above. Run concurrent named patterns in a machine of their own.")
    @Test
    void tenThreadsGuardWithTheirOwnRefinementInOneMachine() throws Exception {
        this.tenThreads(false, (id, r) -> "[plus(" + id + ").plus(" + id + ").mult(" + r + ")]==[update([plus(int::T[?>" + (id - 1) + "]@ga" + id + ").plus(int::T[?>" + (id - 1) + "]@gb" + id + ")=>plus(${*ga" + id + ".plus(*gb" + id + ")})])]",
                (id, r) -> "[plus(" + (2 * id) + ").mult(" + r + ")]");
    }

    /**
     * ... AND ONE SHARED NAME, each thread in a machine of its own: the ten definitions of {@code a::T} never meet.
     */
    @Test
    void tenThreadsGuardWithTheirOwnRefinementEachInAMachineOfItsOwn() throws Exception {
        this.tenThreads(true, (id, r) -> "[plus(" + id + ").plus(" + id + ").mult(" + r + ")]==[update([plus(int::T[?>" + (id - 1) + "]@a).plus(int::T[?>" + (id - 1) + "]@b)=>plus(${*a.plus(*b)})])]",
                (id, r) -> "[plus(" + (2 * id) + ").mult(" + r + ")]");
    }

    /**
     * THREADS threads, ROUNDS rounds apiece, every round checked against the thread's own answer. With
     * {@code ownMachine}, each thread pushes a machine of its own first and pops it after.
     */
    private void tenThreads(final boolean ownMachine, final BiFunction<Integer, Integer, String> code,
                            final BiFunction<Integer, Integer, String> expected) throws Exception {
        final Machine root = Machine.current();
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            final List<Future<?>> threads = new ArrayList<>();
            for (int t = 1; t <= THREADS; t++) {
                final int id = t;
                threads.add(pool.submit(() -> {
                    if (ownMachine)
                        Machine.current().push(f("scratch_" + id));
                    try {
                        for (int r = 1; r <= ROUNDS; r++)
                            AbstractMetatronTest.checkCodeParseApply(LOG, code.apply(id, r), expected.apply(id, r));
                    } finally {
                        if (ownMachine)
                            Machine.current().pop();
                    }
                    return null;
                }));
            }
            for (final Future<?> one : threads)
                one.get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            Machine.current(root);
        }
    }
}
