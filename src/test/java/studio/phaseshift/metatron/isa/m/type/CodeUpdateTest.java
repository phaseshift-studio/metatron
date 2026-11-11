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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * code::T's update() with names and template parameters ({@link Call.Helper#update}): a pattern element with a
 * relative vid names what it matched, and a change's {@code ${...}} argument is evaluated against what the run holds
 * in that place, reading the names. The plain update rows live with code::T's other operations in mInstSetTest.
 */
public class CodeUpdateTest extends AbstractMetatronTest {

    // each test's own machine is named apart: a class's methods run in parallel in one fork
    private static final AtomicLong MACHINES = new AtomicLong();

    /**
     * EVERY TEST RUNS IN A MACHINE OF ITS OWN (Marko, 2026-11-11: push, run, pop). A name on a typed hole is a type of
     * the machine ({@code int::T@a} defines {@code a::T}), and a fork's classes share its root, where this class's
     * {@code a} and {@code b} would be every other test's too.
     */
    @BeforeEach
    protected void pushOwnMachine() {
        Machine.current().push(f("code_update_" + MACHINES.incrementAndGet()));
    }

    @AfterEach
    protected void popOwnMachine() {
        Machine.current().pop();
    }

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
            // ... and inside a code argument of several instructions, each name in its own place
            "[plus(map(3).neg()).mult(4)]==[update([plus(map(int::T@m).neg()).mult(int::T@n)=>mult(${*n}).minus(${*m})])]  % [mult(4).minus(3)]",
            // a code argument of another length is another pattern: nothing matches, nothing is named
            "[plus(map(3).neg().neg())]==[update([plus(map(int::T@m).neg())=>minus(${*m})])]                         % [plus(map(3).neg().neg())]",
            // an all-caps name is a generic, a type and not a value (Marko, 2026-11-08): it captures nothing of the 5
            "[plus(5).mult(2)]==[update([plus(int::T@A)=>plus(${*A})])]                                               % [plus(A::T).mult(2)]",
            // a run binds the pattern as any instruction binds its declaration (Code.Helper.bind): a generic is pinned for
            // the run's length, so A, bound to int by plus, refuses mult's 'x' ...
            "[plus(2).mult(3).plus(1)]==[update([plus(int::T@A).mult(A::T)=>mult(9)])]                                 % [mult(9).plus(1)]",
            "[plus(2).mult('x').plus(1)]==[update([plus(int::T@A).mult(A::T)=>mult(9)])]                               % [plus(2).mult('x').plus(1)]",
            // ... and a value name met twice must meet the same value: plus(2).mult(3) is passed over, plus(2).mult(2) folds
            "[plus(2).mult(3).plus(2).mult(2)]==[update([plus(int::T@n).mult(int::T@n)=>mult(${*n.mult(*n)})])]        % [plus(2).mult(3).mult(4)]",
            // a hole over # keeps its name, a g that is anything: two of them bind apart (both were once filed under #)
            "[plus(2).plus(3).mult(4)]==[update([plus(#::T@g).plus(#::T@h)=>plus(${*g.mult(10).plus(*h)})])]          % [plus(23).mult(4)]",
            // ... and anything includes an instruction argument, which no name makes a nominal type
            "[order(neg()).plus(1)]==[update([order(#::T@s)=>kmerge(${*s})])]                                         % [kmerge(neg()).plus(1)]",
            // a named hole in the change is what its name matched, so the rule reversed is the same equation
            "[plus(map(3).neg()).mult(2)]==[update([plus(map(#::T@u).neg())=>minus(#::T@u)])]                         % [minus(3).mult(2)]",
            "[minus(3).mult(2)]==[update([minus(#::T@u)=>plus(map(#::T@u).neg())])]                                   % [plus(map(3).neg()).mult(2)]",
            // a code argument is typed by its range (Marko, 2026-11-08), so a value of that range meets it: neg() names nothing
            "[mult(2).plus(1)]==[update([mult(neg())=>div(1)])]                                                         % [div(1).plus(1)]",
            // ... but every name a pattern declares must bind, and a name inside a code argument has nothing to bind in a
            // value: the 2 and the 2.0 fit mult(map(v).inv()) by type, and v binds nothing, so the rule does not fire
            "[mult(2).plus(1)]==[update([mult(map(#::T@v).inv())=>div(#::T@v)])]                                       % [mult(2).plus(1)]",
            "[mult(2.0).plus(1)]==[update([mult(map(#::T@v).inv())=>div(#::T@v)])]                                     % [mult(2.0).plus(1)]",
            "[mult(map(3).inv()).plus(1)]==[update([mult(map(#::T@v).inv())=>div(#::T@v)])]                            % [div(3).plus(1)]",
            // a name binds what the call holds in its place, and a value holds no places: v stays unbound against the 2
            "[mult(2).plus(1)]==[update([mult(neg(#::T@v))=>div(#::T@v)])]                                             % [mult(2).plus(1)]",
            "[mult(neg(3)).plus(1)]==[update([mult(neg(#::T@v))=>div(#::T@v)])]                                        % [div(3).plus(1)]",
            // an optional hole takes an absent argument, and its name in the change is that absence
            "[order().plus(1)]==[update([order(#{?}::T@s)=>kmerge(#{?}::T@s)])]                                       % [kmerge(noobj).plus(1)]",
            "[order(neg()).plus(1)]==[update([order(#{?}::T@s)=>kmerge(#{?}::T@s)])]                                  % [kmerge(neg()).plus(1)]",
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

    /**
     * AN INSTRUCTION'S SPAN IS A RUN OF INSTRUCTIONS (Marko, 2026-11-09: {@code {span}inst{point}}) — a pattern element
     * with a ranged span takes as many instructions as its span allows, laid over the code as a lst type's entries are
     * laid over its indices ({@link Code.Helper#bind}): {@code {+}} one or more, {@code {*}} any, {@code {?}} none or one.
     * The longest run is tried first, and a run whose names do not bind gives way to a shorter one
     * ({@link Call.Helper#update}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            // the longest run first: one change for the three plus, and the mult is no plus
            "[plus(1).plus(2).plus(3).mult(4)]==[update([{+}plus(int::T)=>neg()])]                                    % [neg().mult(4)]",
            // any instructions before a fixed one, and nothing after it
            "[plus(1).neg().zero().plus(2)]==[update([{+}#({*}#::T).zero()=>zero()])]                                  % [zero().plus(2)]",
            // {+} takes one at least, so an op with nothing before it is no run
            "[zero().plus(2)]==[update([{+}#({*}#::T).zero()=>neg()])]                                                 % [zero().plus(2)]",
            // {*} may take none, so the fixed elements either side of it meet ...
            "[plus(1).mult(2)]==[update([plus(int::T).{*}#({*}#::T).mult(int::T)=>neg()])]                             % [neg()]",
            // ... or it takes every instruction between them
            "[plus(1).neg().neg().mult(2)]==[update([plus(int::T).{*}#({*}#::T).mult(int::T)=>neg()])]                 % [neg()]",
            // {?} takes none or one, never two
            "[plus(1).neg().mult(2)]==[update([plus(int::T).{?}neg().mult(int::T)=>neg()])]                            % [neg()]",
            "[plus(1).neg().neg().mult(2)]==[update([plus(int::T).{?}neg().mult(int::T)=>neg()])]                      % [plus(1).neg().neg().mult(2)]",
            // a name met twice must meet what it bound: n meets 1, 1 and then 2, so the run of three gives way to the two
            // ones, and the 2 is a run of its own
            "[plus(1).plus(1).plus(2)]==[update([{+}plus(int::T@n)=>mult(${*n})])]                                     % [mult(1).mult(2)]",
    }, delimiter = '%')
    public void testSpans(final String code, final String expected) throws Exception {
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
     * The root frame they all write is a concurrent map, so one thread's definition never breaks another's read.
     */
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
     * THREADS threads, ROUNDS rounds apiece, every round checked against the thread's own answer. Every thread starts
     * in the test's own machine, as a fresh pool thread would otherwise stand in the fork's root; with
     * {@code ownMachine}, each pushes a machine of its own there first and pops it after.
     */
    private void tenThreads(final boolean ownMachine, final BiFunction<Integer, Integer, String> code,
                            final BiFunction<Integer, Integer, String> expected) throws Exception {
        final Machine machine = Machine.current();
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            final List<Future<?>> threads = new ArrayList<>();
            for (int t = 1; t <= THREADS; t++) {
                final int id = t;
                threads.add(pool.submit(() -> Machine.Helper.evalAt(machine, () -> {
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
                })));
            }
            for (final Future<?> one : threads)
                one.get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            Machine.current(machine);
        }
    }
}
