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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The table-driven home for <em>language-construct</em> nuances under distribution: one row is one mtron
 * expression plus its expected result, so a new corner case is a line of CSV rather than a new test method.
 *
 * <h2>The three declarations</h2>
 * <ul>
 *   <li><b>{@link TestSpace}</b> — the cluster's <em>shape</em>. Space definitions written once, with {@code $n}
 *       standing for the n-th peer's prefix, mounted on every peer at setup.</li>
 *   <li><b>{@link TestData}</b> — the cluster's <em>data</em>, per test method. Existing machinery, used as it
 *       is: a value is evaluated on this VM, and because the peers are declared here, a write to a peer's
 *       address reaches that peer. Its values accept {@code $n} too, so no value ever names a port.</li>
 *   <li><b>{@code @CsvSource}</b> — the <em>rows</em>. The left-hand column is written against {@code $i} and is
 *       rewritten by {@link PeerCluster#rewrite} <b>before</b> it is handed to any {@code check…()} helper; that
 *       ordering is the whole mechanism, and it is why rows stay readable and cluster-independent.</li>
 * </ul>
 * One rewrite serves all three, so {@code *$1/a}, {@code 7.to(<$1/shared/v>)} and
 * {@code memspace::[pattern => $1/#]} all resolve against the same live prefixes.
 * <p>
 * The seed matrix gives node {@code i} {@code /n/a = i}, {@code /n/b = i * 10}, {@code /n/c = i * 100}, so the
 * same path read from two peers yields different numbers and an authority mistake shows up as wrong arithmetic
 * rather than as a subtly stale value.
 * <p>
 * The CSV delimiter is {@code %} precisely because mtron spends {@code ,} {@code |} and {@code ;}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@TestSpace("memspace::[pattern => /shared/#]@/sys/space/shared")
public class DistributedmtronTest extends AbstractMetatronTest {

    private static PeerCluster cluster;

    @BeforeAll
    public static void setup() throws Exception {
        // data first, as part of provisioning: a peer holds its seed values from before it is addressable,
        // which is what makes a broken roster fail loudly instead of quietly answering from local state
        cluster = PeerCluster.of(3)
                .store("/n")
                .seed(PeerCluster.DEFAULT_SEED_MATRIX)
                .start()
                .connect();
        // then the declared shape: one @TestSpace definition, modulated to and evaluated on every peer
        TestSpace.Helper.attach(cluster.peers(), TestSpace.Helper.of(DistributedmtronTest.class));
    }

    @AfterAll
    public static void teardown() {
        if (null != cluster) {
            // the state of every peer, one row each — the first thing to read when a run looks wrong
            PeerCluster.peerAnalysis(cluster.peers(),
                    PeerCluster.call("*</sys/space/#>.count()"),
                    PeerCluster.call("*<" + cluster.root() + "/#>"));
            cluster.close();
            cluster = null;
        }
    }

    // ========================================================================
    // the rewrite — a row is not an expression until it has been through here
    // ========================================================================

    /**
     * rewrite {@code $i} to the i-th peer's prefix — shared with {@code @TestData}, see {@link PeerCluster#rewrite}
     */
    static String peers(final String row) {
        return cluster.rewrite(row);
    }

    /**
     * Rewrite a row, evaluate it, and on failure re-throw with the rewritten expression and the cluster report
     * attached — so a red row explains itself.
     */
    private void check(final String row, final String expected) {
        final String code = peers(row);
        try {
            checkCodeParseApply(LOG, code, expected);
        } catch (final AssertionError e) {
            throw new AssertionError("%s%n  row:      %s%n  rewritten: %s%n%s%s"
                    .formatted(e.getMessage(), row, code,
                            PeerCluster.peerAnalysisToString(cluster.peers(),
                                    PeerCluster.call("*<" + cluster.root() + "/#>")),
                            cluster.report()), e);
        }
    }

    // ========================================================================
    // the rewrite itself
    // ========================================================================

    @Test
    public void testPeerPlaceholderSubstitution() {
        assertEquals("*<" + cluster.prefix(1) + "/a>", peers("*$1/a"),
                "the trailing path must fold inside the uri literal");
        assertEquals("<" + cluster.prefix(2) + ">", peers("$2"),
                "a bare placeholder is the peer's data root");
        assertEquals("{*<" + cluster.prefix(1) + "/a>,*<" + cluster.prefix(3) + "/c>}", peers("{*$1/a,*$3/c}"));
        assertFalse(peers("$1").contains("$"), "no placeholder may survive the rewrite");
    }

    /**
     * one template, N addresses — what {@link TestSpace} is built on
     */
    @Test
    public void testSpaceTemplateModulation() {
        assertEquals("memspace::[pattern => " + cluster.root() + "/#]@/sys/space/t",
                cluster.modulate("memspace::[pattern => $root/#]@/sys/space/t", 1),
                "$root is the store root");
        assertEquals("1.to(" + cluster.root() + "/a)", cluster.modulate("$index.to($root/a)", 1),
                "a seed template resolves to real mtron per peer");
        assertEquals("2.to(" + cluster.root() + "/a)", cluster.modulate("$index.to($root/a)", 2),
                "the same template resolves differently for the next peer");
        assertEquals("'" + PeerCluster.probe(3) + "'.to(" + cluster.root() + "/probe)",
                cluster.modulate("'peer-$index-store-probe'.to($root/probe)", 3),
                "substitution is textual, so a placeholder works inside a string literal");
        assertEquals("<" + cluster.prefix(2) + ">", cluster.modulate("$self", 2), "$self is the peer's own address");
        assertEquals("<" + cluster.prefix(1) + ">, <" + cluster.prefix(2) + ">, <" + cluster.prefix(3) + ">",
                cluster.modulate("$peers", 1), "$peers is the whole cluster");
        assertEquals("<" + cluster.prefix(2) + "/data>", cluster.modulate("$2/data", 1),
                "$n names the same peer no matter which peer evaluates it");
    }

    // ========================================================================
    // cross-peer reads and their aggregation
    // ========================================================================

    /**
     * Reading the same path from several peers and combining the results — the map-reduce story written in the
     * syntax that exists today.
     * <p>
     * Node {@code i} holds {@code /n/a = i}, {@code /n/b = i * 10}, {@code /n/c = i * 100}, so
     * <pre>
     *   $1/a $2/a $3/a  =   1   2   3
     *   $1/b $2/b $3/b  =  10  20  30
     *   $1/c $3/c       = 100 300
     * </pre>
     * Every row crosses at least one socket, and because the peers hold <em>different</em> values at the same path,
     * an authority mistake surfaces as wrong arithmetic rather than as a plausible-looking read of local state.
     *
     * <h2>Why the rows chain instead of gathering into a multiplicity</h2>
     * The obvious spelling of a distributed gather is {@code {*$1/a,*$2/a,*$3/a}} — one dereference per peer inside
     * a multiplicity — and it does <b>not</b> work: {@code {...}} holds the members as a <em>program</em>, not as
     * data. {@code {*</n/a>,*</n/b>}} evaluates to a multiplicity of unapplied {@code from} insts (its
     * {@code .explain()} reports {@code rng=>from}), so {@code .sum()} and {@code .plus(10)} apply element-wise to
     * a pair of <em>instructions</em> and fail. Applying it explicitly ({@code .apply(1)}) does run the reads but
     * tags each result {@code #{*}}, which the arithmetic then rejects as a range mismatch.
     * <p>
     * A bare dereference in <em>argument</em> position is evaluated, so chaining works: {@code *$1/a.plus(*$2/a)}
     * is {@code 3}. That is what these rows use.
     * <p>
     * <b>Do not add a row that evaluates {@code {*…,*…}}.</b> Besides being a program rather than data, evaluating
     * one has a lasting side effect on this VM: it retypes the dereferenced vids to {@code *}, so every later read
     * of those paths renders as {@code {*}1} instead of {@code 1} — measured directly, before and after, in the
     * probe recorded in {@code docs/design/distributed-computing.md}. One such row silently re-types the store for
     * every row that follows it, which is why this suite has none.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // one peer, one socket
            "*$1/a                                   % 1",
            "*$2/a                                   % 2",
            "*$3/a                                   % 3",
            "*$1/probe                               % \"peer-1-store-probe\"",
            "*$3/probe                               % \"peer-3-store-probe\"",
            // two and three peers combined — the reduce
            "*$1/a.plus(*$2/a)                       % 3",
            "*$1/a.plus(*$2/a).plus(*$3/a)           % 6",
            "*$1/a.plus(*$2/b).plus(*$3/c)           % 321",
            "*$1/c.plus(*$3/c)                       % 400",
            "*$1/a.plus(*$1/a)                       % 2",
            // a mapper over the gathered values
            "*$1/a.plus(10)                          % 11",
            "*$1/a.plus(*$2/b).plus(10)              % 31",
            "*$1/b.plus(*$2/b).plus(*$3/b).plus(10)  % 70",
            "*$1/a.mult(2)                           % 2",
            "*$2/a.mult(2)                           % 4",
            "*$1/a.minus(1)                          % 0",
            "*$3/a.minus(1)                          % 2",
            "*$1/a.plus(10).minus(10)                % 1",
            "*$1/a.plus(*$2/a).plus(*$3/a).plus(1)   % 7",
            // a peer's own two paths, read in one expression
            "*$1/a.plus(*$1/b)                       % 11",
    }, delimiter = '%')
    void testCrossPeerReads(final String code, final String expected) {
        this.check(code, expected);
    }

    // ========================================================================
    // spaces + data — @TestSpace for shape, @TestData for content
    // ========================================================================

    /**
     * The declaration pair working together: {@link TestSpace} puts {@code /shared/#} on every peer, and
     * {@code @TestData} puts a different value in each peer's copy. Neither names a port — both go through the
     * same rewrite — and the data really did land on three separate VMs, because the write was addressed to a
     * declared peer and the read comes back over a socket.
     */
    @TestData({
            "7.to(<$1/shared/v>)",
            "14.to(<$2/shared/v>)",
            "21.to(<$3/shared/v>)",
    })
    @ParameterizedTest
    @CsvSource(value = {
            "*$1/shared/v                                      % 7",
            "*$2/shared/v                                      % 14",
            "*$1/shared/v.plus(*$2/shared/v).plus(*$3/shared/v)  % 42",
            "*$1/shared/v.plus(*$3/shared/v)                     % 28",
    }, delimiter = '%')
    void testSpacesAndData(final String code, final String expected) {
        this.check(code, expected);
    }

}
