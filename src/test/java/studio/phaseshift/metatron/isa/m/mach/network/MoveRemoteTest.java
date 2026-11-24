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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

/**
 * move(uri) with an authority is a scatter: the nomadic extractor cuts the chain at the move into
 * {@code cut(<uri>, tail)}, the cut ships {@code start(lhs).move(clipped).<tail>} through the peer's eval, and the
 * reply scatters.
 * The loop peer is the identity client, so a shipped program evaluates against the local machine.
 * <p>
 * WHICH HALF RESOLVES WHAT — the distinction these rows exist to pin. Only the <b>cut's target</b> is this
 * machine's to resolve, because only that is what this machine DIALS: {@code move(<//self/a>)} ships to
 * {@code loop://self/a}, never to the bare name, which the machine that lands the tail has never heard of. The
 * <b>tail ships verbatim</b>: it is a program, re-planned where it lands, so a nested {@code move(<//self/b>)}
 * keeps its spelling and is resolved by the receiving machine's own tables. Aligning it here would bake THIS
 * machine's denotation into a program meant to be re-homed — a peer whose {@code self} means something else (or
 * nothing) would then run a place its tables never named. The seams AROUND this rewrite — the crossing rule, the
 * state boundary, the refusal, and the name table that decides — are the matrix in {@link MoveSeamsTest}.
 */
public class MoveRemoteTest extends AbstractMetatronTest {

    @BeforeEach
    void connect() {
        // TWO TABLES, TWO SLOTS, ONE PLACE. A name can be spelled in the SCHEME slot (`self:/a` — the ISA keeps
        // import spellings) or in the AUTHORITY slot (`//self/a` — the network keeps peer names), and each table
        // resolves only the slot it owns. Both answer loop://self, so the rows below spell one place three ways.
        Machine.current().instset().registerPrefix(f("self"), f("loop://self"));
        Machine.current().network().registerPrefix(f("self"), f("loop://self"));
        // A THIRD THING AGAIN IS REACH: a name is denotation, a dial is reach, and a cut can only ship to a peer
        // this machine level dialed -- which is also why the rows are evaluated where this machine stands.
        Network.Helper.connect(Machine.current().network(), f("loop://self"));
    }

    @AfterEach
    void disconnect() {
        Machine.current().network().drop(f("loop://self"));
    }

    @TestData(value = {
            // THE SAME NAME DECLARED AS DATA, in the spelling the door validates: the table is /network/prefix and
            // its rows are keyed by the marked name the accessor shows (`//self`), stored under the bare label.
            "/network/prefix -> [//self=>loop://self]",
            "push(a)",
            "push(<../b>)",
            "move(</.>)",
            "push(c)",
            "move(</.>)"}, oneTime = true)
    @ParameterizedTest
    @CsvSource(value = {
            "5.move(<//self/a>).plus(1).mult(2)                                                                                  % start(5).cut(<loop://self/a>,plus(1).mult(2))                                                                                  % 12",
            "5.move(<//self/a>).mult(2).plus(1)                                                                                  % start(5).cut(<loop://self/a>,mult(2).plus(1))                                                                                  % 11",
            "{1,2,3}.move(<//self/a>).mult(2).plus(1).sum()                                                                      % start({1,2,3}).cut(<loop://self/a>,mult(2).plus(1).sum())                                                                      % 15",
            // THE TAIL SHIPS VERBATIM, nested moves and all: `move(<//self/b>)` keeps the name, because the machine
            // that lands the tail is the one that resolves it. Here the loop peer IS this machine, so the name
            // resolves against the same table and /b is the seeded frame -- the answer is unchanged either way.
            "{1,2,3}.move(<//self/a>).mult(2).plus(1).sum().move(<//self/b>).plus(7).plus(1)                                % start({1,2,3}).cut(<loop://self/a>,mult(2).plus(1).sum().move(<//self/b>).plus(7).plus(1))                                % 23",
            // ... INCLUDING ONE NESTED IN A CODE ARGUMENT, which the extractor never walks: a branch's arguments
            // are the receiver's to re-plan too. The `loop://self/c` beside it is spelled out, so this row pins
            // that BOTH spellings survive untouched.
            "{1,2,3}.move(<//self/a>).branch(move(<//self/b>).mult(2),plus(7).move(<loop://self/c>).plus(1)).plus(8).prod() % start({1,2,3}).cut(<loop://self/a>,branch(move(<//self/b>).mult(2),plus(7).move(<loop://self/c>).plus(1)).plus(8).prod()) % 9767520",
            // A MEMBER THAT IS ONE CUT ANSWERS THE PEER'S RESULT: the rewriter descends into a branch's members, so
            // move(<//self/b>).mult(2).plus(1) becomes the lone inst cut(<loop://self/b>,mult(2).plus(1)), and the branch
            // applies it as an inst. The cut answered its shipping code, unrun, and the branch handed that code on as a value.
            "1.branch(move(<//self/b>).mult(2).plus(1),plus(7))                                                                 % start(1).branch(cut(<loop://self/b>,mult(2).plus(1)),plus(7))                                                                 % {3,8}",
            // A KEY IS SUGAR FOR ITS LANE (Law.nomadic, stage 3; Marko, 2026-11-24: "whole lane at p straight to cut"): a
            // keyed branch compiles to the cuts its keys name, each lane whole at its place, the keys gone
            "5.branch(<//self/a> => plus(1),<//self/b> => mult(2))                                                                    % start(5).branch(cut(<loop://self/a>,plus(1)),cut(<loop://self/b>,mult(2)))                                                % {6,10}",
            // THE CUT'S TARGET IS THE SENDER'S TO RESOLVE -- the one thing that IS aligned: `self:/a` (the ISA slot)
            // and `//self/a` (the network slot) are two spellings of one peer, and both ship as loop://self/a.
            "5.move(<self:/a>).plus(1).mult(2)                                                                                        % start(5).cut(<loop://self/a>,plus(1).mult(2))                                                                                        % 12",
            // ... AND THE SAME TARGET FROM THE OTHER SLOT: the authority spelling resolves through the network's
            // table (`self:` through the ISA's), so `//self/a` ends at the very same cut.
            "5.move(<//self/a>).plus(1).mult(2)                                                                                        % start(5).cut(<loop://self/a>,plus(1).mult(2))                                                                                        % 12",
            // ... AND A LOCAL MOVE IS A CUT TOO (Law.nomadic, Marko 2026-11-24): code is never compiled past a machine
            // change, so the tail is the cut's, compiled by the machine the move lands in. `..` from the root
            // saturates at the root, which is where every row starts -- checkCodeRewrite re-stands the caller's
            // machine before each evaluation, so no earlier row can leave the thread somewhere else.
            "5.move(<..>).plus(1)                                                                                                     % start(5).cut(<..>,plus(1))                                                                                               % 6",
            // A BARE MOVE IS A MOVE UP, and the extractor runs before every other rule: the tail is as written
            "5.move().plus(1)                                                                                                         % start(5).cut(<..>,plus(1))                                                                                               % 6",
            "5.move(<a>).plus(0)                                                                                                      % start(5).cut(<a>,plus(0))                                                                                                % 5",
            // THE CUT GATHERS ITS STREAM (dom A{*}), and a tail of one instruction maps over all of it
            "{1,2,3}.move(<..>).plus(1)                                                                                               % start({1,2,3}).cut(<..>,plus(1))                                                                                         % {2,3,4}",
            // THE TAIL IS COMPILED BY THE MACHINE IT LANDS IN: only /b's network calls loop://self `//only`, so the
            // move inside the tail reaches the peer only because /b compiled it (the root would ship to a name it never
            // declared). A tail of one instruction too: /b compiles move(<//only/c>) into the cut that reaches the peer,
            // where run as it was written, a move instruction cannot leave this server
            "5.move(<b>).move(<//only/c>).plus(1)                                                                                     % start(5).cut(<b>,move(<//only/c>).plus(1))                                                                               % 6",
            "5.move(<b>).move(<//only/c>)                                                                                             % start(5).cut(<b>,move(<//only/c>))                                                                                       % 5",
    }, delimiter = '%')
    void testMoveRewrite(final String code, final String expected, final String expectedResult) {
        // REACH IS PER MACHINE, and every frame the shipped program STANDS IN needs the dial: the first move drops
        // the tail in /a, and the tail's own move then ships from THERE -- /a never inherits the root's peer (that
        // is what `push` refuses), and a branch may address the sibling frames, so each of them declares the peer a
        // receiving machine's own frames would have. connect is ensure-peered, so the declaration is idempotent, and
        // evalAt puts the thread back before the row runs.
        for (final String frame : List.of("/a", "/b", "/c"))
            Machine.Helper.evalAt(f(frame), () -> Network.Helper.connect(Machine.current().network(), f("loop://self")));
        // A NAME ONLY /b DECLARED: a prefix row lands in its own level's table, so the root has no //only
        Machine.Helper.evalAt(f("/b"), () -> Machine.current().network().registerPrefix(f("only"), f("loop://self")));
        checkCodeRewrite(LOG, code, expected, expectedResult, false);
    }

    /**
     * A MOVE TO A HOST THAT ENDS THE CHAIN IS STILL A CUT: the move instruction never leaves this server, so the cut
     * ships the lhs with nothing to run, and the peer answers it.
     */
    @Test
    void testAMoveToAHostThatEndsTheChainShips() {
        checkCodeParseApply(LOG, "5.move(<//self>)", "5");
    }

    /**
     * A HOST MOVE'S POINT IS THE VALUE'S: the value passes through a move unchanged, so the point rides on the lhs the
     * cut ships, and a fold in the tail counts it; a span on a host is idempotent, the target being absolute. Each
     * row: the expression % its answer.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.move{3}(<//self>).plus(1)  % {3}6",
            "5.move{3}(<//self>).count()  % 3",
            "5.{2}move(<//self>).plus(1)  % 6",
    }, delimiter = '%')
    void testAHostMoveCarriesItsPoint(final String mtron, final String answer) {
        checkCodeParseApply(LOG, mtron.trim(), answer.trim());
    }

    /**
     * A CHAIN RESOLVED BEFORE IT IS COMPILED IS CUT ALL THE SAME (the console resolved a line first): the extractor
     * matches the move by its base path, and the resolved tid's range and domain do not hide it.
     */
    @Test
    void testAResolvedChainStillShips() {
        final Code resolved = ObjmtronSerializer.parse("5.move(<//self>).plus(1).mult(2)").asCode().resolve(noobj()).asCode();
        checkEquality(LOG, jnt(12), Machine.evaluate(resolved), true);
    }
}
