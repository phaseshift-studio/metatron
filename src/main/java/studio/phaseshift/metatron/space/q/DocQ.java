/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.space.q;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.mem.KVSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MStr.str;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.MTRON_SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocQ extends BaseQ {

    public static final fURI DOC_TID = MTRON_SPACE_TID.extend("doc");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    // <source,pattern,callback>
    protected final Space docSpace;


    public DocQ(final Space space) {
        super(space, f("doc"), DOC_TID);
        this.docSpace = new KVSpace(f("doc"), fURI.NULL);
        this.onRead = new DocQ.OnRead();
        this.onWrite = new DocQ.OnWrite();
    }

    public static class Documentation extends MRec {
        public Documentation(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public static Documentation of(final fURI instvid, final String domDesc, final String rngDesc, final Map<fURI, String> argDescription) {
            return new Documentation((Map) Map.of(
                    uri("dom"), str(domDesc),
                    uri("rng"), str(rngDesc),
                    uri("args"), argDescription.entrySet().stream().map(kv -> List.of(uri(kv.getKey()), str(kv.getValue()))).collect(Collectors.toMap(kv -> (Obj) kv.get(0), kv -> (Obj) kv.get(1), (a, b) -> b, LinkedHashMap::new))), DOC_TID, instvid);
        }
    }

    public class OnRead extends BaseQ.OnRead {

        @Override
        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            LOG.trace("evaluating {{y}}preread{{/y}}: %s", vid);
            return Optional.of(docSpace.read(source.qLess()));
        }
    }

    public class OnWrite extends BaseQ.OnWrite {

        @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}pree write{{/y}}: %s => %s", obj, vid);
            return Optional.of(docSpace.write(vid.qLess(), obj));
        }
    }
}
