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

package studio.phaseshift.metatron.distributed;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the distributed <em>tooling</em>, not for distribution.
 * <p>
 * Everything here is the pure half of {@link PeerCluster} — the transformations every row, every space
 * definition and every {@code @TestData} value goes through — run against a {@link PeerCluster#detached detached}
 * cluster, which is addresses only: no forked JVMs, no VM boot, no sockets. That makes it fast, and it means a
 * break in the rewrite is reported as a rewrite failure rather than as a mysterious cross-host asymmetry three
 * layers away.
 * <p>
 * The rewrite is worth this much attention because it is load-bearing in a way that is easy to forget: mtron
 * cannot append a path outside a uri literal ({@code *<uri>/a} is a parse error, {@code *<uri/a>} is fine), so a
 * row's trailing path must be folded <em>inside</em> the brackets, and the fold has to stop at exactly the
 * characters that end an address in a row.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PeerClusterInfraTest {

    private static final int BASE_PORT = 9001;
    private static PeerCluster cluster;

    @BeforeAll
    public static void setup() throws Exception {
        cluster = PeerCluster.detached(3, "/n", BASE_PORT);
    }

    // ========================================================================
    // $n rewriting — what every row and every @TestData value goes through
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // a bare placeholder is the peer's data root
            "$1                                      % <ws://localhost:9001/n>",
            "$3                                      % <ws://localhost:9003/n>",
            // a trailing path folds INSIDE the uri literal, because *<uri>/a does not parse
            "*$1/a                                   % *<ws://localhost:9001/n/a>",
            "7.to(<$2/v>)                            % 7.to(<ws://localhost:9002/n/v>)",
            // several placeholders in one row, each to its own peer
            "{*$1/a,*$2/a,*$3/a}                     % {*<ws://localhost:9001/n/a>,*<ws://localhost:9002/n/a>,*<ws://localhost:9003/n/a>}",
            // the fold stops at ',' and '}' so a multiplicity survives
            "{*$1/a,*$3/c}                           % {*<ws://localhost:9001/n/a>,*<ws://localhost:9003/n/c>}",
            // ...and at '.' so a mapper chained onto a read survives
            "*$1/a.sum()                             % *<ws://localhost:9001/n/a>.sum()",
            // ...and at '(' so a call survives
            "*$1/a.count()                           % *<ws://localhost:9001/n/a>.count()",
            // a deep path folds whole
            "*$2/a/b/c                               % *<ws://localhost:9002/n/a/b/c>",
            // a bracketed placeholder must NOT gain a second pair — the bug this row was written for
            "*<$1/a>                                 % *<ws://localhost:9001/n/a>",
            "<$1/a>                                  % <ws://localhost:9001/n/a>",
            // a bare trailing slash is a path too, so it folds inside
            "$self/                                  % <ws://localhost:9001/n/>",
            // text with no placeholder is returned untouched — @TestData values that are not distributed
            "1.plus(1)                               % 1.plus(1)",
    }, delimiter = '%')
    public void testRewrite(final String row, final String expected) {
        assertEquals(expected, cluster.rewrite(row));
    }

    @Test
    public void testRewriteLeavesNothingBehind() {
        assertFalse(cluster.rewrite("$1 $2 $3").contains("$"), "every placeholder must be substituted");
    }

    @Test
    public void testRewriteOfAnUnknownPeerIsLeftAlone() {
        // $4 names a peer that does not exist: better a visible literal than a silently wrong address
        assertEquals("<$4/a>", cluster.rewrite("<$4/a>"));
    }

    // ========================================================================
    // modulation — one definition, N peers
    // ========================================================================

    @Test
    public void testModulatePeerSpecificPlaceholders() {
        assertEquals("1", cluster.modulate("$index", 1));
        assertEquals("3", cluster.modulate("$index", 3));
        assertEquals("9002", cluster.modulate("$port", 2));
        assertEquals("/n", cluster.modulate("$root", 1));
        assertEquals("<ws://localhost:9002/n>", cluster.modulate("$self", 2));
        assertEquals("'peer-3'", cluster.modulate("'peer-$index'", 3),
                "substitution is textual, so a placeholder works inside an mtron string literal");
        assertEquals("<ws://localhost:9001/n>, <ws://localhost:9002/n>, <ws://localhost:9003/n>",
                cluster.modulate("$peers", 1));
    }

    @Test
    public void testModulateSpaceDefinition() {
        assertEquals("memspace::[pattern => /n/#]@/sys/space/each",
                cluster.modulate("memspace::[pattern => $root/#]@/sys/space/each", 2));
        assertEquals("<ws://localhost:9001/n/data>", cluster.modulate("$1/data", 3),
                "$n names the SAME peer no matter which peer evaluates the definition");
        assertEquals("memspace::[pattern => /n/#, route => [<ws://localhost:9002/n/> => <>]]@/sys/space/t",
                cluster.modulate("memspace::[pattern => $root/#, route => [$self/ => <>]]@/sys/space/t", 2));
    }

    @Test
    public void testModulateSeedTemplate() {
        assertEquals("2.to(/n/a); 2.mult(10).to(/n/b)",
                cluster.modulate("$index.to($root/a); $index.mult(10).to($root/b)", 2));
        assertEquals("'peer-2'.to(/n/probe)", cluster.modulate("'peer-$index'.to($root/probe)", 2));
    }

    // ========================================================================
    // addressing
    // ========================================================================

    @Test
    public void testAddressing() {
        assertEquals(3, cluster.size());
        assertEquals(BASE_PORT, cluster.port(1));
        assertEquals(BASE_PORT + 2, cluster.port(3));
        assertEquals("ws://localhost:" + (BASE_PORT + 1) + "/n", cluster.prefix(2));
        assertEquals(3, cluster.peers().size());
        assertEquals(1, cluster.peers().get(0).index());
        assertEquals(cluster.prefix(3), cluster.peers().get(2).prefix());
    }

    @Test
    public void testDetachedClusterHasNoProcesses() {
        assertFalse(cluster.alive(1), "a detached cluster forks nothing");
        assertTrue(cluster.trace(1).contains("never forked"),
                "asking a detached cluster for a trace must say so, not throw");
        cluster.close();
    }

    @Test
    public void testPeerAddressingIsOneBased() {
        assertThrows(IndexOutOfBoundsException.class, () -> cluster.port(0),
                "peer indices are 1-based, matching $n");
    }

    // ========================================================================
    // calls — the column type of peerAnalysis
    // ========================================================================

    @Test
    public void testCallParsing() {
        final Call call = PeerCluster.call("*</sys/space/#>.count()");
        assertEquals(call, ObjmtronSerializer.parse("*</sys/space/#>.count()"));
        assertEquals(2, PeerCluster.calls("*</sys/peer>", "*</n/#>").length);
    }

    // ========================================================================
    // @TestSpace collection
    // ========================================================================

    @Test
    public void testDeclaredSpacesAreCollectedSupertypeFirst() {
        assertEquals(2, TestSpace.Helper.of(SpacesChild.class).length);
        assertEquals("parent", TestSpace.Helper.of(SpacesChild.class)[0],
                "a parent's spaces come first, so a subclass appends rather than replaces");
        assertEquals("child", TestSpace.Helper.of(SpacesChild.class)[1]);
        assertEquals(1, TestSpace.Helper.of(SpacesParent.class).length);
        assertEquals(0, TestSpace.Helper.of(PeerClusterInfraTest.class).length,
                "a class that declares none contributes none");
    }

    @TestSpace("parent")
    static class SpacesParent {
    }

    @TestSpace("child")
    static class SpacesChild extends SpacesParent {
    }
}
