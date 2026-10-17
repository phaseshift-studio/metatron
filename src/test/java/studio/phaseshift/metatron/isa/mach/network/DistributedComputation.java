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

package studio.phaseshift.metatron.isa.mach.network;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.ArrayList;
import java.util.List;

/**
 * One distributed rewrite run, captured as a topology-free value: the original source, the flat list of peers each
 * running some code to some result, and the aggregate result. Master/slave, mapper/combiner, and the like are
 * interpretations layered on top (see {@link Reduction}); this core only says "this peer ran this code and produced
 * this result."
 *
 * <pre>{@code
 * final DistributedComputation c = DistributedComputation.of("{1,2,3,4,5,6}.plus(1).sum()");
 * c.original();   // start({1,2,3,4,5,6}).plus(1).sum()
 * c.peers();      // [ local: master, ws://…:1: start({1,3,5})…, ws://…:2: start({2,4,6})… ]
 * c.result();     // 27
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class DistributedComputation {

    /**
     * One peer's contribution: the authority it answers at ({@code null} = the local machine), the code it ran, and
     * the result it produced.
     */
    public record PeerRun(fURI peer, Code code, Obj result) {
        public boolean isLocal() {
            return null == this.peer;
        }
    }

    private final Code original;
    private final List<PeerRun> peers;
    private final Obj result;

    private DistributedComputation(final Code original, final List<PeerRun> peers, final Obj result) {
        this.original = original;
        this.peers = peers;
        this.result = result;
    }

    /**
     * Compile the source through the distributed rewrite, run it, and capture every peer's code + result. The peel is
     * the reduction ({@code law_shard}) one for now; the captured shape is already topology-free, so other topologies
     * add their own peel without changing this value.
     */
    public static DistributedComputation of(final String source) {
        final Code original = ObjmtronSerializer.parse(source).asCode();
        final Code master = Machine.current().compiler().rewriter().apply(original).asCode();
        final List<Code> slaves = Reduction.slaves(master);
        final List<fURI> routes = Reduction.routes(master);

        final List<PeerRun> peers = new ArrayList<>();
        final Obj complete = TransportRewriteHarness.single(TransportRewriteHarness.evaluate(master));
        peers.add(new PeerRun(null, master, complete));
        for (int i = 0; i < slaves.size(); i++)
            peers.add(new PeerRun(routes.get(i), slaves.get(i),
                    TransportRewriteHarness.single(TransportRewriteHarness.evaluate(slaves.get(i)))));

        return new DistributedComputation(original, peers, complete);
    }

    public Code original() {
        return this.original;
    }

    public List<PeerRun> peers() {
        return this.peers;
    }

    public Obj result() {
        return this.result;
    }
}
