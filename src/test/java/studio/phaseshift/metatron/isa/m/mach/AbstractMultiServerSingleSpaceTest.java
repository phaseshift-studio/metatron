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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.network.PeerCluster;
import studio.phaseshift.metatron.isa.m.mach.network.TransportRewriteHarness;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.to_;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * MANY SERVERS, ONE SPACE: a distributed computation over whichever backend a suite opts into.
 * <p>
 * THE MASTER SETS UP EVERYTHING. This JVM is the master. It starts the backend, mounts its own space on it, and
 * creates every table -- the partitions and the result rows -- before any satellite has a space at all. Satellites are
 * forked JVMs ({@link PeerCluster}) that boot with nothing but the instruction sets the space needs. So a satellite
 * never runs DDL, and its first look at the backend already sees every table it will ever read.
 * <p>
 * CONSTRUCTION IS CONNECTION. Each satellite is sent a WORKLOAD -- a rec of config and code -- and evaluates it in a
 * closure:
 * <pre>
 *   [db=>tblespace::[...]@/sys/space/data, xyz=>'east', code=>|(...)](workload=>_){ !*workload/code.apply() }
 * </pre>
 * Evaluating the workload constructs the space it carries, so the satellite binds its own connection to the same
 * backend -- as though the master's connection had travelled -- and the code then reads its partition through the
 * workload on the satellite's frame stack ({@code ${*workload/xyz}}). The code pulls its partition from the backend,
 * computes, and the harness pushes the result back to the backend at {@code /usr/data/result/<xyz>/total}. Nothing
 * but an acknowledgement travels home.
 * <p>
 * THE LATCH IS THE GATHER. The workloads ship in the {@code law_shard} layout --
 * {@code start([w_1,w_2,w_3]).select([to(s_1),to(s_2),to(s_3)]).merge()} -- and the master reads the results back from
 * the backend only once every satellite has answered. A gather holding a fail is a failed row, never a finished
 * satellite: an uncaught failure on a satellite arrives as a timeout fail in its place, and a count would count it.
 * A satellite that catches its own failure ({@code .catch(...)}) answers its recovery instead.
 * <p>
 * NOTHING HERE IS HOST-SPECIFIC: the backend is pulled by its suite (TestContainers), the satellites inherit this JVM's
 * classpath and flags, and every port is allocated at run time -- the suite runs on an empty Docker service.
 * <p>
 * NO FALLBACK. A suite whose backend is unreachable fails here, in its own class, saying what it could not reach.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractMultiServerSingleSpaceTest extends AbstractMetatronTest {

    /**
     * the namespace every machine mounts the backend at
     */
    protected static final fURI DATA_ROOT = f("/usr/data");

    /**
     * where every machine registers its space
     */
    protected static final fURI DATA_VID = f("/sys/space/data");

    /**
     * the partitions, one per satellite, in satellite order
     */
    protected static final List<String> PARTITIONS = List.of("east", "west", "north");

    /**
     * the expected value that says the row must fail -- the convention AbstractMetatronTest's checks use
     */
    private static final String ERROR = "<ERROR>";

    /**
     * the rows the master writes before any satellite has a space: {@code partition => [amount, cat]} per entry
     */
    private static final List<String> SEED = List.of(
            "/usr/data/east/1 -> [amount=>10,cat=>'a']",
            "/usr/data/east/2 -> [amount=>20,cat=>'b']",
            "/usr/data/east/3 -> [amount=>30,cat=>'a']",
            "/usr/data/west/1 -> [amount=>5,cat=>'b']",
            "/usr/data/west/2 -> [amount=>7,cat=>'b']",
            "/usr/data/north/1 -> [amount=>100,cat=>'a']",
            "/usr/data/north/2 -> [amount=>200,cat=>'c']",
            "/usr/data/north/3 -> [amount=>300,cat=>'a']",
            "/usr/data/north/4 -> [amount=>400,cat=>'c']");

    private PeerCluster cluster;
    private final List<WebSocketRecClient> clients = new ArrayList<>();
    private Obj space;

    // ========================================================================
    // what a backend supplies
    // ========================================================================

    /**
     * start the backend -- after this, {@link #spaceSource()} must name a reachable instance
     */
    protected abstract void startBackend() throws Exception;

    /**
     * stop the backend; never throws past the suite
     */
    protected abstract void stopBackend() throws Exception;

    /**
     * the space as an mtron literal, unanchored -- the master mounts it and every workload carries it, e.g.
     * {@code tblespace::[pattern=></usr/data/#>,host=><...>,driver=><...>,route=>[</usr/data>=><>]]}
     */
    protected abstract String spaceSource();

    /**
     * the statements every machine evaluates before the space can construct there, e.g. {@code import(/m/db/tble)}
     */
    protected abstract List<String> spaceImports();

    // ========================================================================
    // the topology
    // ========================================================================

    @BeforeAll
    void startTopology() throws Exception {
        // typing is not solved across distributed executions yet -- run type-check-free, as the satellites do
        TypeCheck.disable(TypeCheck.values());
        this.startBackend();
        spaceImports().forEach(AbstractMultiServerSingleSpaceTest::evaluate);
        // the master's own space, then every table -- all before any satellite exists
        this.space = evaluate(this.spaceSource() + "@" + DATA_VID);
        SEED.forEach(AbstractMultiServerSingleSpaceTest::evaluate);
        for (final String partition : PARTITIONS)
            evaluate("/usr/data/result/" + partition + " -> [total=>0]");
        this.cluster = PeerCluster.of(PARTITIONS.size()).seed(spaceImports().toArray(String[]::new)).start();
        for (int i = 1; i <= this.cluster.size(); i++) {
            // a satellite starts with NO space -- what makes the provenance check in testWorkload mean something
            checkEquality(LOG, jnt(0), TransportRewriteHarness.single(
                    this.cluster.transport(i).apply(ObjmtronSerializer.parse("*" + DATA_VID + ".count()"))), true);
            this.clients.add(dial(this.cluster, i));
        }
    }

    @AfterAll
    void stopTopology() {
        for (final WebSocketRecClient client : this.clients)
            try {
                client.close();
            } catch (final Exception ignored) {
                // a satellite already down just means its client has nothing left to hang up
            }
        if (null != this.cluster)
            this.cluster.close();
        // REMOVING A SPACE DOES NOT CLOSE IT (Memory.removeSpace only logs), so the connection is released here
        if (this.space instanceof Space opened)
            opened.close();
        try {
            this.stopBackend();
        } catch (final Exception e) {
            LOG.warn("backend did not stop cleanly: %s", e.getMessage());
        }
    }

    /**
     * every row starts from the same results, so a row can never pass on the one before it
     */
    @BeforeEach
    void resetResults() {
        for (final String partition : PARTITIONS)
            evaluate("/usr/data/result/" + partition + " -> [total=>0]");
    }

    // ========================================================================
    // the workloads
    // ========================================================================

    /**
     * ONE GENERIC CODE, SHIPPED TO EVERY SATELLITE. Each row is a computation over one partition, written against
     * {@code ${*workload/xyz}}; the harness binds {@code xyz} per satellite in its workload, appends the push of the
     * result back into the backend, ships the three workloads, latches on their acknowledgements, and reads the
     * results back from the backend. Each row: the computation % the results, keyed by partition.
     */
    @Training.SkipTraining(reason = "the rows run across forked satellites against a containerized backend")
    @ParameterizedTest
    @CsvSource(value = {
            "*</usr/data/${*workload/xyz}/+>.count()                     % [east=>3,west=>2,north=>4]",
            "*</usr/data/${*workload/xyz}/+/amount>.sum()                % [east=>60,west=>12,north=>1000]",
            "*</usr/data/${*workload/xyz}/+>.is(>>amount.gt(15)).count() % [east=>2,west=>0,north=>4]",
            // a failure on a satellite, caught THERE: the master only ever sees the recovery
            "*</usr/data/${*workload/xyz}/+/amount>.sum().plus(a).catch(34) % [east=>34,west=>34,north=>34]",
            // the same failure uncaught: the gather holds fails, so the row fails -- and nothing was pushed
            "*</usr/data/${*workload/xyz}/+/amount>.sum().plus(a)          % <ERROR>",
    }, delimiter = '%')
    public void testWorkload(final String computation, final String expected) {
        final List<Obj> workloads = new ArrayList<>();
        final List<Obj> routes = new ArrayList<>();
        for (int i = 0; i < PARTITIONS.size(); i++) {
            workloads.add(ObjmtronSerializer.parse(this.workload(PARTITIONS.get(i), computation)));
            routes.add(to_(uri(satellite(this.cluster, i + 1))).tryToInst());
        }
        final Code master = start_(lst(workloads)).select_(lst(routes)).merge_();
        final Obj replies = TransportRewriteHarness.evaluate(master);
        LOG.debug("replies %s => %s", computation, replies);
        // THE LATCH REJECTS FAILURES. A satellite's uncaught failure does not arrive as itself: the master waits out
        // its future and holds a timeout fail in that satellite's place -- and a count of the gather would count it
        // as a completion. So a fail in the gather is a failed row, never a finished satellite.
        final boolean failed = replies.stream().anyMatch(Obj::isFail);
        if (ERROR.equals(expected.trim())) {
            checkEquality(LOG, bool(true), bool(failed), true);
            // and nothing was pushed: every satellite failed before its result reached the backend
            final Map<Obj, Obj> untouched = new LinkedHashMap<>();
            PARTITIONS.forEach(partition -> untouched.put(uri(partition), jnt(0)));
            checkEquality(LOG, rec(untouched), results(), true);
            return;
        }
        checkEquality(LOG, bool(false), bool(failed), true);
        // THE LATCH COUNTS THROUGH COEFFICIENTS: equal acknowledgements bulk ({3}34), so the gather is counted by
        // mtron's count -- every satellite answered -- never by the size of the Java list
        checkEquality(LOG, jnt(PARTITIONS.size()), TransportRewriteHarness.single(
                TransportRewriteHarness.evaluate(start_(replies).count_())), true);
        // PROVENANCE: the workload ran THERE. A satellite boots with no space at all, so a space at DATA_VID exists
        // only if that satellite evaluated its workload -- otherwise the master computed and the satellites merely
        // echoed its results. A count comes back, never the space: parsing a reply evaluates it, and a space rec
        // evaluated here would construct a second space on the master.
        for (int i = 1; i <= this.cluster.size(); i++)
            checkEquality(LOG, jnt(1), TransportRewriteHarness.single(
                    this.cluster.transport(i).apply(ObjmtronSerializer.parse("*" + DATA_VID + ".count()"))), true);
        checkEquality(LOG, ObjmtronSerializer.parse(expected), results(), true);
    }

    /**
     * the workload a satellite evaluates: the space it connects through, its partition, and the code that pulls the
     * partition, computes, and pushes the result back into the backend
     */
    protected String workload(final String partition, final String computation) {
        return "[db=>" + this.spaceSource() + "@" + DATA_VID
                + ",xyz=>'" + partition + "'"
                + ",code=>|(" + computation + ".to(</usr/data/result/${*workload/xyz}/total>))"
                + "](workload=>_){ !*workload/code.apply() }";
    }

    /**
     * the results, read back from the backend by the master: {@code partition => total}
     */
    private static Obj results() {
        final Map<Obj, Obj> results = new LinkedHashMap<>();
        for (final String partition : PARTITIONS)
            results.put(uri(partition), Machine.read(DATA_ROOT.extend("result").extend(partition).extend("total")));
        return rec(results);
    }

    // ========================================================================
    // plumbing
    // ========================================================================

    private static Obj evaluate(final String source) {
        return ObjmtronSerializer.parse(source).apply();
    }

    /**
     * a satellite's service authority -- the key its dialed connection is held under, and the route a workload ships to
     */
    private static String satellite(final PeerCluster cluster, final int oneBased) {
        return "ws://localhost:" + cluster.port(oneBased);
    }

    /**
     * construct a {@code ws_client} to one satellite: the constructor dials, and {@link Network.Helper#index} mints
     * the peer this machine ships to
     */
    private static WebSocketRecClient dial(final PeerCluster cluster, final int oneBased) {
        final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                uri(HOST), uri(satellite(cluster, oneBased) + PeerCluster.MTRON_ROUTE),
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
        final WebSocketRecClient client = new WebSocketRecClient(wsRec);
        Network.Helper.index(client);
        return client;
    }
}
