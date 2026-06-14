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

package studio.phaseshift.metatron.isa.mach.space;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.AUTHORITY_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_SECOND_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.isa.web.webInstSet.CLIENT_TYPE;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Cluster management space that handles distributed machine evaluation.
 * <p>
 * This space manages the cluster topology, host connections, and cross-host routing.
 * It integrates seamlessly with the Router system through pattern-based routing.
 * <p>
 * [peers=>[xyz:1234=>[host=>ws://xyz:1234/mtron, handler=>mtron_ws,state=>OK,stat=>[in=>bytes::0.0,out=>bytes:0.0]]]
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class clusterSpace extends AbstractSpace<Map<Obj, Obj>> implements Space {

   

    // Pattern for cluster-related operations
    private static final fURI CLUSTER_PATTERN = f("ws://+/cluster/#");

    // Host information storage
    private final fURI localHost;

    public clusterSpace(final Map<Obj, Obj> sjvm, final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(sjvm, jvm, tid, vid);
        try {
            this.localHost = this.at(HOST).orThrow(MTronException.of("no host provided")).uriValue();
            // Initialize from boot arguments
            this.at(PEERS).orElse(rec0()).asRec().jvm().forEach((key, value) -> {
                if (key.isUri() && value.isRec()) {
                    final Rec hostRec = value.asRec();
                    final String hostName = key.uriValue().toString();
                    try {
                        HostInfo hostInfo = new HostInfo();
                        hostInfo.hostName = hostName;
                        hostInfo.port = key.uriValue().port();
                        hostInfo.handlerType = extractHandlerType(hostRec);
                        hostInfo.protocol = key.uriValue().scheme();
                        hostInfo.url = buildHostUrl(hostInfo);
                        LOG.info("Added host to cluster: {{b}}%s{{X}} => %s", hostName, hostInfo.url);

                    } catch (Exception e) {
                        LOG.warn("failed to parse host configuration for {{b}}%s{{X}}: %s", hostName, e.getMessage());
                    }
                }
            });

            // Start cluster discovery and maintenance
            startDiscovery();

            LOG.info("ClusterSpace initialized with {{b}}%d{{X}} hosts", this.at(PEERS).asRec().count());

        } catch (Exception e) {
            LOG.error("Failed to initialize ClusterSpace: %s", e.getMessage(), e);
            throw MTronException.of(e);
        }
    }


    /**
     * Extract handler type from host configuration
     */
    private String extractHandlerType(Rec hostRec) {
        final Obj handler = hostRec.at(uri(SERVER)).asRec().at(uri("handler"));
        if (!handler.isNoObj()) {
            return handler.toString();
        }
        return "mtron"; // Default
    }

    /**
     * Build complete host URL
     */
    private String buildHostUrl(HostInfo hostInfo) {
        return String.format("%s://%s:%d", hostInfo.protocol, hostInfo.hostName, hostInfo.port);
    }

    /**
     * Start cluster discovery and maintenance
     */
    private void startDiscovery() {
        final VirtualThread healthThread = new VirtualThread(mutableMap(uri(CODE), instLambda((lhs, inst) -> {
            try {
                LOG.info("performing cluster health check...");
                // In a real implementation, this would:
                // 1. Check connectivity to all hosts
                // 2. Update host status
                // 3. Discover new hosts if needed
                // 4. Maintain consistent cluster view
            } catch (Exception e) {
                LOG.warn("cluster health check failed: %s", e.getMessage());
            }
            return uri("OK");
        }), uri(LOOP), real(30.0, MATH_SECOND_TID, null)), MACH_VIRTUAL_THREAD_TID, this.vid.extend("health"));// Check every 30 seconds
        this.jvm().put(uri("health"), healthThread);
        healthThread.applyAsync();
        LOG.info("cluster health management thread started");
    }

    // ======================== Router Integration ========================

    @Override
    public Obj read(final fURI vid) {
        LOG.debug("clusterSpace read: {{b}}%s{{X}}", vid);

        // Delegate to the router for normal operations
        return Router.readFromSpace(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        LOG.debug("clusterSpace write: {{b}}%s{{X}} => %s", vid, obj);
        final fURI authority = f(vid.authority());
        if (authority.test(f(this.localHost.host()))) {
            return Router.writeToSpace(Space.Helper.routeFromSpace(vid, this.routes()), obj.vid(Space.Helper.routeFromSpace(obj.vid(), this.routes())));
        } else {
            this.at(PEERS)
                    .orElse(rec0())
                    .asRec()
                    .elements()
                    .filter(e -> e.first().uriValue().test(authority))
                    .forEach(e -> {
                        LOG.info("sending obj to remote metatron: (%s,%s) => %s", vid, obj, e.second().orElse(rec0()).asRec().at(PROTOCOL));
                        e.second().asRec().orElse(rec0()).at(CLIENT).orElse(rec0()).at(SEND).orElse(noobj()).apply(obj);
                    });

        }
        return obj;
    }


    // ======================== Space Interface ========================


    @Override
    public void close() {
        final Obj healthThread = this.at("health");
        if (!healthThread.isNoObj() && !healthThread.<VirtualThread>as().state().equals(uri(STOP))) {
            LOG.info("halting cluster health thread");
            healthThread.<VirtualThread>as().close();
        }
        super.close();
    }

    // ======================== Inner Classes ========================

    /**
     * Information about a host in the cluster
     */
    public static class HostInfo {
        public String hostName;
        public int port;
        public String protocol;
        public String handlerType;
        public String url;

        @Override
        public String toString() {
            return String.format("HostInfo{name='%s', url='%s'}", hostName, url);
        }
    }
}