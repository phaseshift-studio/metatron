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

import org.jspecify.annotations.Nullable;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_SECOND_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
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
public class clstrSpace extends AbstractSpace<Map<Obj, Obj>> implements Space {

    private final fURI localHost;

    public clstrSpace(final Map<Obj, Obj> sjvm, final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(sjvm, jvm, tid, vid);
        try {
            this.localHost = this.at(HOST).orThrow(MTronException.of("no host provided")).uriValue();
            // Initialize from boot arguments
            this.at(PEER).orElse(rec0()).elements().forEach(rel -> {
                final Obj key = rel.first();
                final Obj val = rel.second();
                if (key.isUri() && val.isRec()) {
                    final String hostName = key.uriValue().toString();
                    final Rec hostRec = val.asRec();
                    try {
                        LOG.info("Added host to cluster: {{b}}%s{{X}} => %s", hostName, hostRec);
                    } catch (Exception e) {
                        LOG.warn("failed to parse host configuration for {{b}}%s{{X}}: %s", hostName, e.getMessage());
                    }
                }
            });
            // Start cluster discovery and maintenance
            this.startDiscovery();
            LOG.info("cluster initialized with {{b}}%d{{X}} hosts", this.at(PEER).orElse(rec0()).asRec().count());
        } catch (Exception e) {
            LOG.error("failed to initialize cluster: %s", e.getMessage(), e);
            throw MTronException.of(e);
        }
    }


    /**
     * Start cluster discovery and maintenance
     */
    private void startDiscovery() {
        final VirtualThread healthThread = new VirtualThread(mutableMap(uri(CODE), instLambda((lhs, inst) -> {
            try {
                this.at(PEER).orElse(rec0()).elements().forEach(rel -> {
                    final Obj peerUri = rel.first();
                    final Obj peerClient = rel.second();
                    if (!peerClient.asRec().at(STATE).apply().asBool().boolValue()) {
                        LOG.warn("faulty connection to: %s", peerUri);
                    }
                });
                LOG.debug("performing cluster health check...");
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
        LOG.debug("cluster health management thread started");
    }

    // ======================== Router Integration ========================

    private Obj readWrite(final fURI vid, @Nullable final Obj maybeObj) {
        LOG.info("read/writing %s at %s", vid, vid.authority());
        final fURI authority = f(vid.authority());
        if (authority.test(f(this.localHost.authority()))) {
            LOG.info("converting vid: %s", Space.Helper.routeFromSpace(vid, this.routes()));
            return null == maybeObj ?
                    Router.readFromSpace(Space.Helper.routeFromSpace(vid, this.routes())) :
                    Router.writeToSpace(Space.Helper.routeFromSpace(vid, this.routes()), maybeObj);
        } else {
            return objs(this.at(PEER)
                    .orElse(rec0())
                    .elements()
                    .filter(e -> f(e.first().uriValue().authority()).test(authority))
                    .map(e -> {
                        if (null == maybeObj)
                            LOG.info("fetching obj from remote peer %s: %s", e.first(), vid);
                        else
                            LOG.info("sending obj to remote peer %s: (%s,%s)", e.first(), vid, maybeObj);
                        final Rec peerRec = e.second().asRec().orElse(rec0());
                        final Obj sendInst = peerRec.at(SEND_RECV)
                                .orElse(peerRec.at(SEND))
                                .orElse(peerRec.at(CLIENT).orElse(rec0()).at(SEND));
                        if (!sendInst.isNoObj()) {
                            return null == maybeObj ?
                                    sendInst.apply(from_(uri(vid)).tryToInst()) :
                                    sendInst.apply(start_(vid.toUri()).ref_(maybeObj));
                        } else {
                            throw MTronException.of("no accessible send() for clstr peer");
                        }
                    }));
        }
    }


    @Override
    public Obj read(final fURI vid) {
        return this.readWrite(vid, null);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.readWrite(vid, obj);
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
}