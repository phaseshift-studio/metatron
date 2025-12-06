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

package studio.phaseshift.metatron.lang.net.clstr;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.router.impl.MConnection;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.Map;
import java.util.function.BiPredicate;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.net.clstr.clstrInstSet.MCLSTR_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class clstrSpace extends MSpace<BiPredicate<fURI, MConnection>> {

    public static final fURI CLSTR_TID = MCLSTR_TID.extend(Tokens.SPACE).extend("clstr");
    public final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type CLSTR_TYPE = T(REC_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(CLSTR_TID),
            lst(T(REC_TID, isa_(rec(uri(Tokens.PATTERN), T(URI_TID))))), (lhs, inst) -> {
                final Space space = new clstrSpace((f, c) -> true, inst.arg(0).<Rec>as().at(Tokens.PATTERN).uriValue(), inst.arg(0).vid());
                Router.global().addSpace(space);
                return space;
            }));

    public clstrSpace(final BiPredicate<fURI, MConnection> hash, final fURI pattern, final fURI vid) {
        super(hash, Map.of(uri(Tokens.PATTERN), uri(pattern)), pattern, CLSTR_TID, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        return Router.global().server().sendRecv(this.sjvm, vid, from_(vid.toUri()));
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        Router.global().server().send(this.sjvm, vid, start_(obj.vid(null)).to_(vid.toUri()));
        return obj;
    }
}
