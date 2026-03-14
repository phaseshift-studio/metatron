/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class IncrQ extends BaseQ {

    public static final fURI INCRQ_TID = Q_TID.extend("incrq");
    public static final String INCR = "incr";
    // <source,pattern,callback>
    protected final AtomicInteger counter = new AtomicInteger(0);

    public static final Type INCRQ_TYPE = Type.Builder.build()
            .vid(INCRQ_TID)
            .tid(REC_TID)
            .constructor(IncrQ::new).create();


    public IncrQ() {
        super(mutableMap(uri(PATTERN), uri(INCR)), f(INCR), INCRQ_TID);
        this.onRead = null;
        this.onWrite = new OnWrite();
    }

    public class OnWrite extends BaseOnWrite {

        public OnWrite() {
            super(noobj(), noobj(), noobj());
        }

        @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.debug("evaluating {{y}}qless write{{/y}}: %s => %s", obj, vid);
            if (vid.hasQ(INCR)) {
                final fURI incrPattern = vid.extend(vid.qValue(INCR, fURI.class)).resolve();
                final List<String> newPath = new ArrayList<>();
                for (final String p : incrPattern.path()) {
                    if (fURI.isPattern(p))
                        newPath.add(counter.incrementAndGet() + "");
                    else
                        newPath.add(p);
                }
                return Optional.of(obj.vid(vid.removeQ(INCR).path(newPath)));
            }
            return Optional.empty();
        }
    }
}
