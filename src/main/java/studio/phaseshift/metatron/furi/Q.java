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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;

import java.util.Optional;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public interface Q extends Rec {

    fURI ON_WRITE = f("on_write");
    fURI PRE_WRITE = f("pre_write");
    fURI POST_WRITE = f("post_write");
    fURI QLESS_WRITE = f("qless_write");
    fURI ON_READ = f("on_read");
    fURI PRE_READ = f("pre_read");
    fURI POST_READ = f("post_read");
    Type Q_TYPE = T(f("/sys/space/q"));/*, null, instC(mtronInstSet.INST_TID.dom(ALL.maybe()).rng(f("/sys/space/q")),
            lst(T(REC_TID, isa_(rec(uri(PATTERN), T(URI_TID),
                    uri(ON_WRITE), rec(uri(PRE_WRITE), T(INST_TID).c(cInt::maybe), uri(POST_WRITE), T(INST_TID).c(cInt::maybe), uri(QLESS_WRITE), T(INST_TID).c(cInt::maybe)),
                    uri(ON_READ), rec(uri(PRE_READ), T(INST_TID).c(cInt::maybe), uri(POST_READ).c(cInt::maybe)))))), (lhs, inst) -> {
                return lhs;
            }));*/


    fURI pattern();
    
    Optional<OnWrite> onWrite();

    Optional<OnRead> onRead();

    interface OnWrite {
        default Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }

        default Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj oldObj, final Obj newObj) {
            return Optional.empty();
        }

        default Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }
    }

    interface OnRead {
        default Optional<Obj> preRead(final fURI source, final fURI vid) {
            return Optional.empty();
        }

        default Optional<Obj> postRead(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }
    }

}
