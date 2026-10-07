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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.io.Closeable;

import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;

/**
 * The <b>Network</b> component of a Machine: the peers this frame can reach, and the authority arithmetic that
 * decides what "reachable" and "mine" mean.
 * <p>
 * It is a {@link Rec} — a map of authority to transport, which is exactly the shape the declared roster already
 * has ({@code /sys/peer}). Like Memory and the ISA, it is an <em>accumulating</em> component, so a frame wraps it
 * with a {@code ComponentUnion} rather than replacing it: a frame that dials a peer adds to its own
 * {@code current()} and inherits the rest.
 * <p>
 * The authority algebra lives here rather than on the Router because it is not routing — it is what the network
 * <em>is</em>. Every question about whether two addresses denote the same service, or whether a host names this
 * machine, is a question about reachability.
 * <p>
 * <b>Denotation is not scoped by this component.</b> Scoping decides which peers a frame can reach; it never
 * changes what an absolute URI means. An unreachable authority fails loudly rather than resolving elsewhere.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Network extends Machine.Component, Closeable {

    /**
     * The authorities this level declares. Membership is configuration, never traffic: a URI must not be able to
     * make itself a peer merely by being addressed.
     */
    Set<String> authorities();


    /**
     * where the cluster view lives — a sibling of {@code /sys/peer}, {@code /sys/thread} and {@code /sys/typer},
     * and deliberately <em>not</em> under {@code /sys/mach}: the Machine claims that address and
     * {@code AbstractSpace}'s default writer is a no-op, so anything written there is silently dropped.
     */
    fURI CLUSTER_PATH = f("/sys/cluster");

    /**
     * The local cluster: a rec of fields and methods over <em>this</em> network's declared roster — the awareness
     * half of distribution, and deliberately a <b>view</b> rather than a second registry. Its {@code peer} field
     * is an auto pointer to the roster, so there is exactly one source of truth for membership and no copy to
     * drift.
     * <p>
     * It lives on the component rather than in a class of its own because every input it takes is this
     * component's: the roster, and the transports the roster declares. A separate class could do nothing but read
     * the network and report on it.
     * <p>
     * Two invariants the shape protects:
     * <ul>
     *   <li><b>Membership is declared, never emergent.</b> Nothing here adds a peer; it only asks after the ones
     *       that were configured. A healthy cluster is one whose declared peers answer.</li>
     *   <li><b>Tools compute on call.</b> No status is stored — a remembered status would make transient state
     *       look like declared state, and "is the cluster up?" would answer from a stale read.</li>
     * </ul>
     * Usage (all three spellings work): {@code /sys/cluster/status()}, {@code /sys/cluster.status()},
     * {@code *&#47;sys/cluster/status.apply()}.
     */
    default Rec cluster() {
        return rec(
                uri(NAME), str("local"),
                uri(PEER), auto_from_(Helper.peerRosterPath()).tryToInst(),
                uri(STATUS), this.status());
    }

    /**
     * the {@code status} method, as an <b>auto</b> inst that closes over <em>this</em> network.
     * <p>
     * A static method reference would report on whatever network happened to be global; closing over the receiver
     * means the view reports on the component it was minted from.
     * <p>
     * The tid has to be {@link studio.phaseshift.metatron.isa.m.mInstSet#AUTO_INST_TID}: a field holding a plain
     * inst holds a <em>value</em> and is handed back unevaluated, while an auto inst is evaluated on access
     * ({@code Obj.Helper.isAuto}). That is what makes the field behave as a method rather than as data, so
     * {@code *x/status} and {@code *x.status()} reach it while the same inst built with a plain tid sits inert.
     * <p>
     * rng is {@code ALL_STAR}, not {@code peer::T}: the report is a <em>multiplicity</em> of peers, one per
     * declared peer, and a single-peer rec rng fails the range check as soon as a second peer is declared.
     */
    default Inst status() {
        return auto_(instLambda(lhs -> this.status(lhs))).tryToInst().asInst();
    }

    /**
     * Ask each declared peer whether it is answering, and hand back one {@code peer::T} per peer carrying its own
     * status. A peer that is declared but silent reports {@code down} rather than disappearing — the roster is
     * what was configured, and a report that hid the failures would be worse than no report.
     * <p>
     * Reads its own {@code peer} field first and falls back to the roster path, so the method works both when it
     * is invoked on the stored singleton and when it is applied as a bare inst.
     */
    default Obj status(final Obj lhs) {
        final Obj field = lhs.isRec() ? lhs.asRec().at(uri(PEER)) : noobj();
        final Obj roster = field.isRec() ? field : Machine.read(Helper.peerRosterPath());
        if (!roster.isRec())
            return noobj(); // nothing declared: an empty cluster, not an error
        return objs(roster.asRec().jvm().entrySet().stream()
                .filter(e -> e.getKey().isUri())
                .map(e -> (Obj) Helper.probe(e.getKey(), e.getValue())));
    }

    class Helper {

        /**
         * Hosts that all denote "this machine" — a server bound to the first is reachable at the others.
         */
        private static final Set<String> LOOPBACK_HOSTS = Set.of("0.0.0.0", "127.0.0.1", "localhost", "::1", "[::1]");

        private Helper() {
            // static only
        }

        /**
         * Host component of an authority ({@code "localhost:8555"} to {@code "localhost"}), handling the
         * bracketed IPv6 form ({@code "[::1]:8555"} to {@code "[::1]"}).
         */
        public static String hostOf(final String authority) {
            if (null == authority)
                return null;
            if (authority.startsWith("[")) {
                final int close = authority.indexOf(']');
                return close < 0 ? authority : authority.substring(0, close + 1);
            }
            final int colon = authority.lastIndexOf(':');
            return colon < 0 ? authority : authority.substring(0, colon);
        }

        /**
         * Port component of an authority, or {@code null} when none was given.
         */
        public static String portOf(final String authority) {
            if (null == authority)
                return null;
            if (authority.startsWith("[")) {
                final int close = authority.indexOf(']');
                return close < 0 || close + 1 >= authority.length() ? null : authority.substring(close + 2);
            }
            final int colon = authority.lastIndexOf(':');
            return colon < 0 || colon + 1 >= authority.length() ? null : authority.substring(colon + 1);
        }

        /**
         * True when {@code host} names this machine. A server declared as {@code 0.0.0.0:8555} (all local
         * interfaces) is the same service as {@code localhost:8555} — and getting this wrong is not cosmetic:
         * the boot binds the wildcard while a peer addresses the loopback name, so an alias-blind comparison
         * makes the Router forward a request to <em>itself</em>.
         */
        public static boolean isLoopbackHost(final String host) {
            return null != host && LOOPBACK_HOSTS.contains(host);
        }

        /**
         * True when two authorities denote the same service. Ports must agree when both are present; hosts
         * match exactly, or by loopback aliasing. This is the ownership test the authority guard runs before it
         * will delegate anywhere.
         */
        public static boolean sameAuthority(final String a, final String b) {
            if (null == a || null == b)
                return false;
            if (a.equals(b))
                return true;
            final String aPort = portOf(a);
            final String bPort = portOf(b);
            if (null != aPort && null != bPort && !aPort.equals(bPort))
                return false;
            final String aHost = hostOf(a);
            final String bHost = hostOf(b);
            return aHost.equals(bHost) || (isLoopbackHost(aHost) && isLoopbackHost(bHost));
        }

        /**
         * the liveness payload — cheap, total, and free of side effects.
         */
        private static final Obj PROBE = ObjmtronSerializer.parse("1.plus(1)");

        /**
         * one declared peer, asked once: {@code [authority, transport, status=>[kind=>up|down, time=>ms]]}
         */
        static Rec probe(final Obj authority, final Obj transport) {
            final long start = System.currentTimeMillis();
            boolean up;
            try {
                // a silent peer does NOT answer noobj — the declared transports report it as a fail, so both
                // spellings of "nothing came back" have to count as down or a dead peer reads as healthy
                final Obj answer = transport.isObjInst() ? transport.apply(PROBE) : noobj();
                up = !answer.isNoObj() && !answer.isFail();
            } catch (final Exception ex) {
                up = false;
            }
            final long time = System.currentTimeMillis() - start;
            return rec(
                    uri(AUTHORITY), authority,
                    uri(TRANSPORT), transport,
                    uri(STATUS), rec(uri(KIND), str(up ? "up" : "down"), uri(TIME), jnt(time)));
        }

        /**
         * The declared peer roster: a rec of {@code <authority-uri> => <transport-inst>}. The value is the
         * transport — an inst that takes the message ({@code from(localized)} for a read,
         * {@code start(localized).ref(obj)} for a write) and returns the peer's response. Keeping it an inst
         * is what keeps the machine free of any transport dependency: swapping ws for http, mqtt or a gRPC
         * client is a roster change.
         * <p>
         * It lives at {@code /sys/peer}, beside {@code /sys/thread}, and <em>not</em> under {@code /sys/mach}:
         * the machine claims {@code /sys/mach} and {@code AbstractSpace}'s default writer is a no-op, so a
         * roster written there is silently dropped — a failure mode worth remembering, because the symptom is
         * a peer that quietly resolves to the local wildcard-host space and <em>appears to work</em>.
         */
        public static fURI peerRosterPath() {
            return SYS.extend(PEER);
        }
    }
}
