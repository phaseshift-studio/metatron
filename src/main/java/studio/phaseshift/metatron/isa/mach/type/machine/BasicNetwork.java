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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_NETWORK_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BasicNetwork — one level of a machine's network: a roster of {@code <authority-uri> => <transport-inst>}.
 * <p>
 * Like {@link BasicMemory} it holds <b>no state in Java fields</b>: the roster <em>is</em> this rec, so mtron can
 * read and write the peers of a frame directly.
 * <p>
 * The value is the transport — an inst that takes the message and returns the peer's response. Keeping it an inst
 * is what keeps the machine free of any transport dependency: swapping ws for http, mqtt or a gRPC client is a
 * roster change, not a code change.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicNetwork extends MRec implements Network {

    public BasicNetwork() {
        this(mutableMap());
    }

    public BasicNetwork(final Map<Obj, Obj> jvm) {
        super(jvm, MACH_NETWORK_TID, null);
    }

    /**
     * Release what this level dialed. The roster holds transports today, which are insts and hold no resource —
     * but the declaration is what makes the reach correct the moment a frame's roster holds a live connection,
     * rather than leaving a socket to leak silently.
     */
    @Override
    public void close() {
        this.jvm().values().stream()
                .filter(v -> v instanceof AutoCloseable)
                .forEach(CommonUtil::close);
        this.jvm().clear();
    }

    @Override
    public Set<String> authorities() {
        final Set<String> authorities = new LinkedHashSet<>();
        this.jvm().keySet().stream()
                .filter(Obj::isUri)
                .map(k -> k.uriValue().authority())
                .filter(Objects::nonNull)
                .forEach(authorities::add);
        return authorities;
    }
}
