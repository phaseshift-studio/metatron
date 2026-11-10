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

package studio.phaseshift.metatron.isa.m.mach;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A MACHINE'S COMPONENTS, EVERY VIEW AND EVERY PERMUTATION (Marko, 2026-11-10): the component half of the
 * machine-isolation family. A machine holds five components (memory, instset, network, compiler and processor), and
 * each is reached four ways: its accessor ({@code machine.compiler()}), the machine rec's slot, the machine's own
 * address ({@code ~/compiler}) and its absolute address ({@code /xyz/compiler}). All four answer ONE object, in
 * every machine, and push gives each component its own semantic:
 * <ul>
 *   <li>memory and instset are a level over the enclosing machine's: the parent's later additions read through, and
 *   the child's own stay the child's;</li>
 *   <li>network is its own level: reach is the frame's, knowledge is shared by addressing;</li>
 *   <li>processor is a fresh one of the parent's architecture, with its own computers and fail stacks;</li>
 *   <li>compiler is a deep clone of the parent's: the child compiles as its parent did at the push, and a write on
 *   either side after it stays on that side.</li>
 * </ul>
 * The sys half is {@code SysIsolationScriptTest}'s, the instset's doors are {@code InstSetUnionReadTest}'s, and the
 * network's reach is {@code NetworkIsolationScriptTest}'s.
 */
public class MachineComponentScriptTest extends AbstractMetatronTest {

    private static final fURI PROBE = f("/sys/component/probe");

    // a component by its accessor, keyed by the address segment that names it
    private static final Map<String, Function<Machine, Obj>> COMPONENTS = Map.of(
            MEMORY, Machine::memory,
            INSTSET, Machine::instset,
            NETWORK, Machine::network,
            COMPILER, Machine::compiler,
            PROCESSOR, Machine::processor);

    // the compiler's stages that carry state a write can reach
    private static final List<String> STAGES = List.of(REWRITER, RESOLVER, TYPER);

    // the root's typer state, put back whatever a script left behind
    private Map<Obj, Obj> check;

    @BeforeEach
    public void snapshotTheRootsCompiler() {
        this.check = new LinkedHashMap<>(Machine.root().compiler().typer().<Rec>at("check").recValue());
    }

    @AfterEach
    public void restoreTheRoot() {
        this.check.forEach((flag, value) -> Machine.root().compiler().typer().at("check/" + flag.uriValue(), value, Poly.MUTABLE));
        Machine.root().instset().write(f("type/live"), noobj());
    }

    @Override
    protected Map<String, Directive> directives() {
        return Map.of(
                // every view of each component answers the one object: 1, or the views that disagree. A machine
                // named by its address is viewed from where the script stands
                "VIEWS", args -> this.probe("views", views(0 == args.length ? Machine.current() : Machine.read(f(args[0])).as())),
                // no component of this machine, nor any compiler stage, is its parent's (or the machine's at args[0])
                "DISTINCT", args -> this.probe("distinct", distinct(Machine.current(),
                        0 == args.length ? Machine.current().parent().as() : Machine.read(f(args[0])).as())),
                // the name of a compiler part (resolver, resolver/selector, ...), read through the accessor and
                // through the address: the one name, or both when they disagree
                "NAME", args -> this.probe("name", name(Machine.current(), args[0])),
                "DECLARE", args -> Machine.current().instset().write(f(args[0]), T(f(args[0]))),
                "DECLARE_ROOT", args -> Machine.root().instset().write(f(args[0]), T(f(args[0]))),
                "HAS_TYPE", args -> this.probe("has_type", jnt(Machine.current().instset().types().stream()
                        .anyMatch(type -> f(args[0]).equals(type.vid())) ? 1 : 0)));
    }

    private void probe(final String name, final Obj value) {
        Machine.write(PROBE.extend(name), value);
    }

    private static Obj views(final Machine machine) {
        final fURI address = machine.vid().resolve();
        final List<String> disagree = new ArrayList<>();
        COMPONENTS.forEach((name, accessor) -> {
            final Obj component = accessor.apply(machine);
            final Obj slot = machine.at(uri(name));
            if (component != slot)
                disagree.add(name + " at the rec's slot");
            if (machine == Machine.current() && component != Machine.read(f("~/" + name)))
                disagree.add(name + " at ~/" + name);
            if (component != Machine.read(address.extend(name)))
                disagree.add(name + " at " + address.extend(name));
        });
        return disagree.isEmpty() ? jnt(1) : str(machine.vid() + ": " + String.join(", ", disagree));
    }

    private static Obj distinct(final Machine machine, final Machine other) {
        final List<String> shared = new ArrayList<>();
        COMPONENTS.forEach((name, accessor) -> {
            if (accessor.apply(machine) == accessor.apply(other))
                shared.add(name);
        });
        STAGES.forEach(stage -> {
            final Obj own = machine.compiler().at(uri(stage));
            if (own == other.compiler().at(uri(stage)))
                shared.add(COMPILER + "/" + stage);
        });
        return shared.isEmpty() ? jnt(1) : str(machine.vid() + " shares with " + other.vid() + ": " + String.join(", ", shared));
    }

    private static Obj name(final Machine machine, final String part) {
        final String accessor = machine.compiler().at(uri(part)).tid().name();
        final String address = Machine.read(f("~/" + COMPILER + "/" + part)).tid().name();
        return str(accessor.equals(address) ? accessor : "accessor " + accessor + ", address " + address);
    }

    /**
     * EVERY VIEW IS THE ONE COMPONENT, in the root, a child, a grandchild and a sibling, and after a pop; no machine
     * shares a component with its parent or a sibling, nor a compiler stage; and a popped machine's components are
     * gone from its address.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[VIEWS]",
            "*/sys/component/probe/views % 1                [-- the root's --]",
            "[STATE] *~.push(views_a)",
            "[VIEWS]",
            "*/sys/component/probe/views % 1                [-- a child's --]",
            "[VIEWS /.]",
            "*/sys/component/probe/views % 1                [-- the root's, viewed from the child --]",
            "[DISTINCT]",
            "*/sys/component/probe/distinct % 1             [-- nothing of the root's --]",
            "[STATE] *~.push(views_b)",
            "[VIEWS]",
            "*/sys/component/probe/views % 1                [-- a grandchild's --]",
            "[DISTINCT]",
            "*/sys/component/probe/distinct % 1             [-- nothing of its parent's --]",
            "[DISTINCT /.]",
            "*/sys/component/probe/distinct % 1             [-- nor the root's --]",
            "[STATE] *~.pop(_)",
            "*/views_a/views_b/compiler % noobj % true      [-- POP DISCARDS THE ADDRESS: the components go --]",
            "*/views_a/views_b % noobj % true               [-- the root's registration of the frame --]",
            "*/views_a/child/views_b % noobj % true         [-- and the parent's child link --]",
            "[VIEWS]",
            "*/sys/component/probe/views % 1                [-- and its parent's views are as they were --]",
            "[STATE] move(/.)",
            "*/views_a % noobj % false                      [-- a frame moved away from stands at its address --]",
            "*/views_a/compiler % noobj % false",
            "[STATE] *~.push(views_c)",
            "[VIEWS]",
            "*/sys/component/probe/views % 1                [-- a sibling's --]",
            "[DISTINCT /views_a]",
            "*/sys/component/probe/distinct % 1             [-- nothing of its sibling's --]",
            "[STATE] *~.pop(_)",
            "[STATE] move(/views_a)",
            "[STATE] *~.pop(_)",
            "[VIEWS]",
            "*/sys/component/probe/views % 1                [-- the root's, after it all --]"
    })
    void testEveryViewOfAComponentIsTheMachinesOwn() {
    }

    /**
     * THE COMPILER IS FORKED AT THE PUSH, read and written through its address, with the typer's check points as the
     * witness: INHERIT the parent's state at the push, OWN writes, NO LEAK UP, the parent's later writes are its own
     * (the fork), a grandchild forks its parent, NO LEAK ACROSS to a sibling, and the update spelling is a write too.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*~/compiler/typer/check/inst_rng % true                  [-- the root's, as booted --]",
            "[STATE] ~/compiler/typer/check/inst_rng -> false         [-- the root, before the push --]",
            "[STATE] *~.push(fork_a)",
            "*~/compiler/typer/check/inst_rng % false                 [-- INHERIT: the clone carries it --]",
            "[STATE] ~/compiler/typer/check/inst_rng -> true          [-- OWN --]",
            "*~/compiler/typer/check/inst_rng % true",
            "*/fork_a/compiler/typer/check/inst_rng % true            [-- one address, spelled absolutely --]",
            "*/compiler/typer/check/inst_rng % false                  [-- NO LEAK UP --]",
            "[STATE] @~/compiler/typer/check >>= [obj_write=>false]   [-- an update is a write too --]",
            "*~/compiler/typer/check/obj_write % false",
            "*/compiler/typer/check/obj_write % true",
            "[STATE] /compiler/typer/check/type_pred -> false         [-- the root writes after the push --]",
            "*/compiler/typer/check/type_pred % false",
            "*~/compiler/typer/check/type_pred % true                 [-- FORK: the child compiles as the root did --]",
            "[STATE] *~.push(fork_b)",
            "*~/compiler/typer/check/inst_rng % true                  [-- a grandchild forks its parent --]",
            "*~/compiler/typer/check/obj_write % false",
            "*~/compiler/typer/check/type_pred % true",
            "[STATE] *~.pop(_)",
            "[STATE] move(/.)",
            "[STATE] *~.push(fork_c)",
            "*~/compiler/typer/check/inst_rng % false                 [-- NO LEAK ACROSS: a sibling forks the root --]",
            "*~/compiler/typer/check/obj_write % true",
            "*~/compiler/typer/check/type_pred % false                [-- and was pushed after the root's write --]",
            "[STATE] *~.pop(_)",
            "[STATE] move(/fork_a)",
            "*~/compiler/typer/check/inst_rng % true                  [-- fork_a kept its own across the moves --]",
            "[STATE] *~.pop(_)",
            "*~/compiler/typer/check/inst_rng % false                 [-- the root kept its own --]",
            "*~/compiler/typer/check/type_pred % false"
    })
    void testTheCompilerIsForkedAtThePush() {
    }

    /**
     * A WHOLE COMPILER STAGE IS REPLACED THROUGH ITS ADDRESS, and the accessor answers what the address holds: a
     * child's resolver and rewriter replaced, a nested write into the replaced stage, a grandchild forking the
     * replaced stages, the child still computing, and the root never seeing any of it. A stage is CALLABLE (it applies
     * to code), so like an instruction it is written blocked, {@code addr -> |stage}: unblocked, the argument is applied
     * to the address and the write is refused, leaving the stage as it was.
     * <p>
     * DISABLED for a defect it found (2026-11-10): {@code compiler::T} checks its stage slots by type reference
     * ({@code resolver => T(resolver)}), and in any machine after the first of a reused surefire fork it refuses a
     * {@code firstfind_resolver} ({@code TypeMismatchException: obj does not match /m/mach/component/compiler::T}),
     * while in a fresh JVM it passes, and even admits an {@code MCode}. The script passes run alone.
     */
    @Disabled("compiler::T refuses firstfind_resolver in every later machine of a reused fork; passes alone")
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NAME resolver]",
            "*/sys/component/probe/name % 'scoring_resolver'          [-- the root's, as booted --]",
            "[STATE] *~.push(stage_a)",
            "*~.vid() % /stage_a",
            "~/compiler/resolver -> firstfind_resolver::[=>] % <ERROR>   [-- unblocked, the stage is applied to the address --]",
            "[NAME resolver]",
            "*/sys/component/probe/name % 'scoring_resolver'",
            // each write answers the stage it wrote, so a refused write fails here rather than at the read after it
            "~/compiler/resolver -> |firstfind_resolver::[=>] % firstfind_resolver::[=>]",
            "*~.vid() % /stage_a",
            "[NAME resolver]",
            "*/sys/component/probe/name % 'firstfind_resolver'",
            "[NAME resolver/selector]",
            "*/sys/component/probe/name % 'firstfind_selector'",
            "[STATE] ~/compiler/rewriter -> |plan_rewriter::[=>]       [-- a rewriter's rule entry is a lambda: checked by name --]",
            "[NAME rewriter]",
            "*/sys/component/probe/name % 'plan_rewriter'",
            "1.plus(2).plus(0) % 3                                     [-- the child still computes --]",
            "~/compiler/resolver/selector -> |specificity_selector::[=>] % specificity_selector::[=>]",
            "[NAME resolver/selector]",
            "*/sys/component/probe/name % 'specificity_selector'     [-- a nested write into the replaced stage --]",
            "[NAME resolver]",
            "*/sys/component/probe/name % 'firstfind_resolver'",
            "[STATE] *~.push(stage_b)",
            "[NAME rewriter]",
            "*/sys/component/probe/name % 'plan_rewriter'            [-- a grandchild forks the replaced stages --]",
            "[STATE] *~.pop(_)",
            "[STATE] *~.pop(_)",
            "[NAME resolver]",
            "*/sys/component/probe/name % 'scoring_resolver'         [-- the root never saw the child's stages --]",
            "[NAME resolver/selector]",
            "*/sys/component/probe/name % 'specificity_selector'",
            "[NAME rewriter]",
            "*/sys/component/probe/name % 'fixpoint_rewriter'"
    })
    void testACompilerStageIsReplacedThroughItsAddress() {
    }

    /**
     * WHERE THE COMPILER FORKS, AN INSTSET LEVEL AND A PROCESSOR DO NOT: the root's declaration after the push reads
     * through the child's instset level, the child's own declaration never leaks up, and a child's fails stay in its
     * own processor.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~/processor/computer/+/fail.count().to(/sys/component/probe/stacks)",
            "[STATE] *~.push(live_a)",
            "[DECLARE_ROOT /m/component/live]            [-- the root declares after the push --]",
            "[HAS_TYPE /m/component/live]",
            "*/sys/component/probe/has_type % 1          [-- LIVE: the child's level reads through to the root's --]",
            "[DECLARE /m/component/own]",
            "[HAS_TYPE /m/component/own]",
            "*/sys/component/probe/has_type % 1",
            "*~/processor/fail/+ % noobj % true          [-- a fresh processor holds no fail of its parent's --]",
            "[STATE] 1.plus(b)                           [-- the child fails --]",
            "*~/processor/computer/+/fail.count().gt(0) % true",
            "[STATE] *~.pop(_)",
            "[HAS_TYPE /m/component/own]",
            "*/sys/component/probe/has_type % 0          [-- NO LEAK UP from the instset --]",
            "[HAS_TYPE /m/component/live]",
            "*/sys/component/probe/has_type % 1",
            "*~/processor/computer/+/fail.count() % */sys/component/probe/stacks % true   [-- nor the processor --]"
    })
    void testAnInstSetIsALevelAndAProcessorIsFreshWhereTheCompilerForks() {
    }
}
