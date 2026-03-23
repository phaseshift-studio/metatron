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

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.TriFunction;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BaseQ extends MRec implements Q {

    protected final GraphittyLogger LOG;
    protected OnRead onRead;
    protected OnWrite onWrite;
    protected final fURI queryPattern;

    public BaseQ(final Map<Obj, Obj> jvm, final fURI queryPattern, final fURI tid) {
        super(jvm, tid, null);
        this.jvm().put(uri(Tokens.PATTERN), uri(queryPattern));
        this.queryPattern = queryPattern;
        LOG = Graphitty.log(this);
        this.onRead = new BaseOnRead(this.at(PRE_READ).as(), this.at(POST_READ).as());
        this.onWrite = new BaseOnWrite(this.at(PRE_WRITE).as(), this.at(POST_WRITE).as(), this.at(QLESS_WRITE).as());

    }

    @Override
    public String toString() {
        return Q.Helper.qToString(this);
    }

    @Override
    public int hashCode() {
        return Q.Helper.qHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Q.Helper.qEquals(this, other);
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
    public BaseQ clone(final Object jvm, final fURI tid, final fURI vid) {
        final BaseQ clone = (BaseQ) this.clone();
        clone.jvm = jvm;
        clone.tid = tid;
        clone.vid = vid;
        return clone;
    }

    @Override
    public Rec clone() {
        return super.clone();
    }


    public static class BaseOnRead extends MRec implements Q.OnRead {
        public BaseOnRead(final Inst preRead, final Inst postRead) {
            super(mutableMap(uri(PRE_READ), preRead, uri(POST_READ), postRead), REC_TID, null);
        }

        public Optional<Obj> preRead(final fURI vid) {
            final Inst i = this.at(PRE_READ).as();
            if (i.isNoObj()) return Optional.empty();
            final Obj result = i.args(lst(uri(vid))).apply();
            return result.isNoObj() ? Optional.empty() : Optional.of(result);
        }

        public Optional<Obj> postRead(final fURI vid, final Obj obj) {
            final Inst i = this.at(POST_READ).as();
            if (i.isNoObj()) return Optional.empty();
            final Obj result = i.args(lst(uri(vid), obj)).apply();
            return result.isNoObj() ? Optional.empty() : Optional.of(result);
        }
    }

    public static class BaseOnWrite extends MRec implements Q.OnWrite {
        public BaseOnWrite(final Inst preWrite, final Inst postWrite, final Inst qlessWrite) {
            super(mutableMap(uri(PRE_WRITE), preWrite, uri(POST_WRITE), postWrite, uri(QLESS_WRITE), qlessWrite), REC_TID, null);
        }

        public Optional<Obj> preWrite(final fURI vid, final Obj obj) {
            final Inst i = this.at(PRE_WRITE).as();
            if (i.isNoObj()) return Optional.empty();
            final Obj result = i.args(lst(uri(vid), obj)).apply();
            return result.isNoObj() ? Optional.empty() : Optional.of(result);

        }

        public Optional<Obj> postWrite(final fURI vid, final Obj oldObj, final Obj newObj) {
            final Inst i = this.at(POST_WRITE).as();
            if (i.isNoObj()) return Optional.empty();
            final Obj result = i.args(lst(uri(vid), oldObj, newObj)).apply();
            return result.isNoObj() ? Optional.empty() : Optional.of(result);
        }

        public Optional<Obj> qlessWrite(final fURI vid, final Obj obj) {
            final Inst i = this.at(QLESS_WRITE).as();
            if (i.isNoObj()) return Optional.empty();
            final Obj result = i.args(lst(uri(vid), obj)).apply();
            return result.isNoObj() ? Optional.empty() : Optional.of(result);
        }
    }

    public static Q create(final fURI tid, final fURI pattern,
                           final Function<fURI, Obj> preRead,
                           final BiFunction<fURI, Obj, Obj> postRead,
                           final BiFunction<fURI, Obj, Obj> preWrite,
                           final TriFunction<fURI, Obj, Obj, Obj> postWrite,
                           final BiFunction<fURI, Obj, Obj> qlessWrite) {
        return new BaseQ(mutableMap(
                uri(PRE_READ), null == preRead ? noobj() : instC(INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(URI_TYPE), (_, inst) -> preRead.apply(inst.arg(0).uriValue())),
                uri(POST_READ), null == postRead ? noobj() : instC(INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(URI_TYPE,T(ALL)), (_, inst) -> postRead.apply(inst.arg(0).uriValue(), inst.arg(1))),
                uri(PRE_WRITE), null == preWrite ? noobj() : instC(INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(URI_TYPE, T(ALL)), (_, inst) -> preWrite.apply(inst.arg(0).uriValue(), inst.arg(1))),
                uri(POST_WRITE), null == postWrite ? noobj() : instC(INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(URI_TYPE, T(ALL), T(ALL)), (_, inst) -> postWrite.apply(inst.arg(0).uriValue(), inst.arg(1), inst.arg(2))),
                uri(QLESS_WRITE), null == qlessWrite ? noobj() : instC(INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(URI_TYPE, T(ALL)), (_, inst) -> qlessWrite.apply(inst.arg(0).uriValue(), inst.arg(1)))), pattern, tid);
    }
}
