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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;

/**
 * Machine ISOLATION as a stateful script — mutation and its observation are one line each, in order. The
 * {@code [STATE]} lines push/pop mid-script, which a plain {@code @CsvSource} cannot express.
 * <p>
 * The isolation invariants proven here, each as a script of mtron observations:
 * <ol>
 *     <li><b>thread count</b> — {@code /thread} is the root's own set, {@code ~/thread} is the current machine's.</li>
 *     <li><b>tilde tracks the machine</b> — {@code ~} and {@code /.} name a {@code machine::T} wherever you stand.</li>
 *     <li><b>write isolation</b> — a child's write is its own; it does not leak up or across.</li>
 *     <li><b>executor is global</b> — one funnel at {@code /sys/thread/executor}, not a per-machine component.</li>
 * </ol>
 */
public class MachineIsolationScriptTest extends AbstractMetatronTest {

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // at root: /thread and ~/thread name the same set — no machine pushed, current is the root
            "*/thread/+.count() % *~/thread/+.count()        % true",
            // push a child: /thread is still the root's own set; ~/thread is now the child's own (empty)
            "[STATE] *~.push(xyz)",
            "*/thread/+.count().gt(*~/thread/+.count())      % true",
            "*/xyz/thread/+.count() % *~/thread/+.count()    % true",
            // pop back to root: the two agree again
            "[STATE] *~.pop(_)",
            "*/thread/+.count() % *~/thread/+.count()        % true"
    })
    void testThreadCountAcrossPushPop() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // ~ and /. both dereference to a machine::T
            "*~.tid().eq(/m/mach/machine)                    % true",
            "*</.>.tid().eq(/m/mach/machine)                 % true",
            // inside a child, ~ still names a machine (the child itself)
            "[STATE] *~.push(xyz)",
            "*~.eq(*/xyz)                                    % true",
            "*</.>.eq(*/xyz)                                 % false",
            "*~.tid().eq(/m/mach/machine)                    % true",
            "*~.pop(_).eq(*</.>)                              % true"
    })
    void testTildeTracksTheCurrentMachine() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // a write into the child's own namespace lands under /xyz, not /
            "[STATE] *~.push(xyz)",
            "[STATE] ~/iso -> 1",
            "*/xyz/iso % *~/iso          % true",
            "*/xyz/iso % 1               % true",
            // after pop, the child's write is gone from the root's view — no leak up
            "[STATE] *~.pop(_)",
            "*/xyz/iso % noobj           % true"
    })
    void testWriteIsolationNoLeakUp() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // child a writes into its own namespace
            "[STATE] *~.push(a)",
            "[STATE] ~/iso -> 1",
            "*/a/iso                   % 1 % true",
            "[STATE] *~.pop(_)",
            // a sibling b does not see a's write — no leak across
            "[STATE] *~.push(b)",
            "*/b/iso % noobj           % true",
            "[STATE] *~.pop(_)"
    })
    void testWriteIsolationNoLeakAcross() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // the executor is a single global rec, not a per-machine component
            "*/sys/thread/executor % noobj % false"
    })
    void testThreadExecutorIsGlobal() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // at root: the short name and the absolute path dereference to the SAME instruction
            "*count % */m/inst/count",
            // inside a child: the short name still resolves to that SAME instruction via the union's routing tables
            "[STATE] *~.push(xyz)",
            "*count                                  % */m/inst/count",
            "[STATE] inst(a=>int::T){ + *a}@blah",
            "1.blah(2)                               % 3",
            "[STATE] *~.pop(_)",
            "1.blah(2)                               % <ERROR>",
            "[STATE] *~.push(xyz)",
            "1.blah(2)                               % <ERROR>",
            "[STATE] inst(a=>int::T){ + *a}@blah",
            "10.blah(22)                             % 32",
            "[STATE] move(/.)",
            "1.blah(2)                               % <ERROR>",
            "[STATE] move(/xyz)",
            "2.blah(7)                               % 9",
    })
    void testInstructionResolutionAcrossPush() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            "*</.>     % *<~>                 % true",
            "*~/memory % *</./memory> % true",
            "*~/instset % *</./instset> % true",
            "*~/network % *</./network> % true",
            "*~/compiler % *</./compiler> % true",
            "*~/processor % *</./processor> % true",
            // the machine's five components are all reachable through ~ (the machine's own slots)
            "*~/memory % noobj % false",
            "*~/instset % noobj % false",
            "*~/network % noobj % false",
            "*~/compiler % noobj % false",
            "*~/processor % noobj % false",
            // a child has its own components, reachable through the same ~
            "[STATE] *~.push(xyz)",
            "*~/memory % *</xyz/memory> % true",
            "*~/instset % *</xyz/instset> % true",
            "*~/network % *</xyz/network> % true",
            "*~/compiler % *</xyz/compiler> % true",
            "*~/processor % *</xyz/processor> % true",
            "*</.>     % *<~>                 % false",
            /*"~/memory() % /memory() % false",
            "*~/memory % *</memory> % false",
            "*~/instset % *</instset> % false",
            "*~/network % *</network> % false",
            "*~/compiler % *</compiler> % false",
            "*~/processor % *</processor> % false",*/
           /* "*~/memory % *</./memory> % false",
            "*~/instset % *</./instset> % false",
            "*~/network % *</./network> % false",
            "*~/compiler % *</./compiler> % false",
            "*~/processor % *</./processor> % false",*/
            "*~/memory % noobj % false",
            "*~/instset % noobj % false",
            "*~/network % noobj % false",
            "*~/compiler % noobj % false",
            "*~/processor % noobj % false",
            "[STATE] *~.pop(_)"
    })
    void testComponentIsolation() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // child 1 writes into its own namespace
            "[STATE] *~.push(xyz1)",
            "[STATE] ~/iso -> 1",
            "*/xyz1/iso % 1 % true",
            // move back to the root and push a sibling — xyz1 stays alive (move, not pop)
            "[STATE] move(/.)",
            "[STATE] *~.push(xyz2)",
            "*/xyz2/iso % noobj % true",
            // move between the siblings: each sees only its own state
            "[STATE] move(/xyz1)",
            "*/xyz1/iso % 1 % true",
            "[STATE] move(/xyz2)",
            "*/xyz2/iso % noobj % true",
            // and the root sees neither child's write
            "[STATE] move(/.)",
            "*/xyz1/iso % noobj % true"
    })
    void testSiblingIsolationAcrossMove() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // build a 7-machine hierarchy — root → {a→{a1,a2}, b→{b1}, c} — each machine stamps its root frame with `who`
            "[STATE] ~/who -> 0; who -> 0;",
            "[STATE] *~.push(a)", "[STATE] ~/who -> 1; who -> 11",
            "[STATE] *~.push(a1)", "[STATE] ~/who -> 2; who -> 22",
            "[STATE] move(/a)",
            "[STATE] *~.push(a2)", "[STATE] ~/who -> 3; who -> 33",
            "[STATE] move(/.)",
            "[STATE] *~.push(b)", "[STATE] ~/who -> 4; who -> 44",
            "[STATE] *~.push(b1)", "[STATE] ~/who -> 5; who -> 55",
            "[STATE] move(/.)",
            "[STATE] *~.push(c)", "[STATE] ~/who -> 6; who -> 66",
            "[STATE] move(/.)",
            // downward: root → a → a1
            "[STATE] move(/a)", "*~/who % 1 % true", "*who % 11 % true",
            "[STATE] move(/a/a1)", "*~/who % 2 % true", "*who % 22 % true",
            // horizontal (sibling): a1 → a2
            "[STATE] move(/a/a2)", "*~/who % 3 % true", "*who % 33 % true",
            // upward: a2 → a → root
            "[STATE] move(/a)", "*~/who % 1 % true", "*who % 11 % true",
            "[STATE] move(/.)", "*~/who % 0 % true", "*who % 0 % true",
            // horizontal (other branch): root → b → b1
            "[STATE] move(/b)", "*~/who % 4 % true", "*who % 44 % true",
            "[STATE] move(/b/b1)", "*~/who % 5 % true", "*who % 55 % true",
            // deep cross-branch: b1 → c
            "[STATE] move(/c)", "*~/who % 6 % true", "*who % 66 % true",
            // deep horizontal: c → a1 → b1
            "[STATE] move(/a/a1)", "*~/who % 2 % true", "*who % 22 % true",
            "[STATE] move(/b/b1)", "*~/who % 5 % true", "*who % 55 % true",
    })
    void testHierarchyMoves() {
    }
}
