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

package studio.phaseshift.metatron.isa.iot.space.mqtt;

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Optional;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.SUBQ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class mqttSpaceSubQ extends BaseQ {

    protected final mqttSpace space;
    protected final QProc subq = QCollection.subq();

    public mqttSpaceSubQ(final mqttSpace space) {
        super(mutableMap(uri(PATTERN), uri(SUBQ)), f(SUBQ), SUBQ_TID);
        this.space = space;
        this.onWrite = new mqttSpaceSubQ.OnWrite();
        this.onRead = this.subq.onRead().get();
    }

    public class OnWrite extends BaseOnWrite {

        public OnWrite() {
            super(noobj(), noobj(), noobj());
        }

        @Override
        public Optional<Obj> qlessWrite(final fURI vid, final Obj obj) {
            // Delegate to the SubQ qproc's qlessWrite handler, which matches
            // subscriptions against the vid and fires callbacks in virtual threads.
            // This is invoked by QProc.Helper.processQlessWrite() from both:
            //   - mqttSpace.write() for internal writes
            //   - mqttSpace's constructor-level MQTT callback for external writes
            return mqttSpaceSubQ.this.subq.onWrite().get().qlessWrite(vid, obj);
        }

        @Override
        public boolean hasQlessHandler() {
            return true;
        }

        @Override
        public Optional<Obj> preWrite(final fURI vid, final Obj obj) {
            LOG.info("evaluating {{y}}prewrite{{/y}}: %s => %s", obj, vid);
            if (vid.hasQ(SUBQ)) {
                // Delegate to the SubQ qproc's preWrite to store/remove the
                // subscription in its internal list.  The constructor-level
                // MQTT callback handles firing subscriptions for both internal
                // and external messages — no per-SubQ MQTT subscriptions needed.
                return mqttSpaceSubQ.this.subq.onWrite().get().preWrite(vid, obj);
            }
            return Optional.empty();
        }
    }
}