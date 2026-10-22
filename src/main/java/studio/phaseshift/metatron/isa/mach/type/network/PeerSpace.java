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

package studio.phaseshift.metatron.isa.mach.type.network;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE PEER MAP AS AN ADDRESS SPACE — a machine's {@code <vid>/network/peer/#}, mounted at bootstrap, so every member of
 * the machine's reach sits at a valid path and destruction on deletion works from mtron:
 * <pre>
 *   *</network/peer/ws/localhost/8555/mtron>             [-- the peer::T --]
 *   *</network/peer/ws/localhost/8555/mtron/authority>   [-- one of its fields --]
 *   *</network/peer/ws/#>                                [-- every ws member --]
 *   </network/peer/ws/localhost/8555/mtron> -> noobj     [-- hang up: the member leaves the map --]
 *   </network/peer> -> noobj                             [-- hang up every member --]
 * </pre>
 * IT STORES NOTHING. Every read answers from the machine's network component as it stands, so the map is never copied
 * and never stale, and every write is the network's own retraction ({@link Network#disconnect}). This is the mount
 * {@link BasicNetwork} anticipated — "once the network slot is mounted as a writable space" — kept to the {@code peer}
 * branch, so the roster (an address of its own at {@code /network/roster}) is untouched.
 * <p>
 * A PEER IS CONSTRUCTED, NOT WRITTEN. Construction is connection: a client indexes itself at its own key, so the only
 * write this space accepts is the deletion; a value written at a member's address (a field edit included) is refused,
 * loudly, rather than stored beside the member it would contradict.
 * <p>
 * Closing the space only unmounts it. The reach is the network's to close ({@code Network.close()}, from the frame's
 * pop or the VM's shutdown) — never a side effect of an index being torn down.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PeerSpace extends AbstractSpace<Object> {

    private final Machine machine;
    private final fURI prefix;

    private PeerSpace(final Machine machine, final fURI prefix, final fURI vid) {
        super(null, mutableMap(uri(PATTERN), uri(prefix.extend(ALL))), SPACE_TID, vid, false);
        this.machine = machine;
        this.prefix = prefix;
    }

    /**
     * The peer space of a machine: {@code <vid>/network/peer/#}, indexed at {@code <vid>/sys/space/network} beside
     * the machine's other infra spaces, and unregistered — {@link Machine#bootstrap()} mounts it into the machine's
     * own memory level, so a pushed frame's members are its own and leave with it.
     */
    public static PeerSpace of(final Machine machine) {
        final fURI resolved = machine.vid().resolve();
        return new PeerSpace(machine, resolved.extend(NETWORK).extend(PEER), resolved.extend("sys").extend(SPACE).extend(NETWORK));
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return qpattern -> {
            final fURI pattern = qpattern.qLessExceptDomRng().resolve();
            final Rec peers = this.machine.network().peers();
            // the map itself, as the machine rec's own field reads it
            if (pattern.asNode().equals(this.prefix.asNode()))
                return peers.isEmpty() ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(this.prefix, peers));
            return peers.elements()
                    .filter(kv -> kv.first().isUri())
                    .map(kv -> IdObj.of(this.prefix.extend(kv.first().uriValue()), kv.second()))
                    .filter(member -> member.furi().test(pattern.asNode()))
                    .iterator();
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (vid, obj) -> {
            final Network network = this.machine.network();
            final fURI at = vid.qLess().resolve().asNode();
            if (!obj.isNoObj()) {
                LOG.warn("a peer is constructed, not written: %s -> %s refused (dial a client: ws_client::[pattern=>...])", at, obj);
                return noobj();
            }
            if (at.equals(this.prefix.asNode()))
                network.peers().keys().filter(Obj::isUri).map(Obj::uriValue).toList().forEach(network::disconnect);
            else {
                final fURI key = this.memberKey(at);
                if (null != key)
                    network.disconnect(key);
            }
            return obj;
        };
    }

    /**
     * the member key an address names — what follows {@code <vid>/network/peer/}, in the one encoding every key has
     * ({@link fURI#toPath()}) — or null for an address outside it
     */
    private fURI memberKey(final fURI at) {
        return at.hasPrefix(this.prefix) && !at.equals(this.prefix.asNode()) ? at.removeSubpath(this.prefix).toPath() : null;
    }

    @Override
    public void close() {
        Space.Helper.closeSpace(this);
    }
}
