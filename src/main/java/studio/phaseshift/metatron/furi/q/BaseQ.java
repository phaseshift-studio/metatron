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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static studio.phaseshift.metatron.lang.Space.PATTERN;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BaseQ extends MRec implements Q {

    protected final GraphittyLogger LOG;
    protected OnRead onRead;
    protected OnWrite onWrite;
    protected final fURI queryPattern;

    public BaseQ(final Map<Obj, Obj> jvm, final fURI queryPattern, final fURI tid) {
        super(jvm, tid, fURI.NULL);
        this.queryPattern = queryPattern;
        LOG = Graphitty.log(this);
    }

    @Override
    public fURI pattern() {
        return this.queryPattern;
    }

    @Override
    public Optional<Q.OnWrite> onWrite() {
        return Optional.ofNullable(this.onWrite);
    }

    @Override
    public Optional<Q.OnRead> onRead() {
        return Optional.ofNullable(this.onRead);
    }

    @Override
    public BaseQ clone(Object jvm, fURI tid, fURI vid) {
        this.jvm = jvm;
        return this;
    }

    @Override
    public Rec clone() {
        return this;
    }


    public static class BaseOnRead extends MRec implements Q.OnRead {
        public BaseOnRead(final Inst preRead, final Inst postRead) {
            super(mutableMap(uri(PRE_READ), preRead, uri(POST_READ), postRead),REC_TID,fURI.NULL);
        }

        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            final Inst i = this.at(uri(PRE_READ)).as();
            if (i.isNoObj()) return Optional.empty();
            final Obj result = i.apply(lst(uri(source), uri(vid)));
            return Optional.of(result);

        }

        public Optional<Obj> postRead(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }
    }

    public static class BaseOnWrite extends MRec implements Q.OnWrite {
        public BaseOnWrite(final Inst preWrite, final Inst postWrite, final Inst qlessWrite) {
            super(mutableMap(uri(PRE_WRITE), preWrite, uri(POST_WRITE), postWrite, uri(QLESS_WRITE), qlessWrite),REC_TID,fURI.NULL);
        }

        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            final Inst i = this.at(uri(PRE_WRITE)).as();
            if (i.isNoObj()) return Optional.empty();
            final Obj result = i.apply(lst(uri(source), uri(vid), obj));
            return Optional.of(result);

        }

        public Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj oldObj, final Obj newObj) {
            return Optional.empty();
        }

        public Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }
    }
}
