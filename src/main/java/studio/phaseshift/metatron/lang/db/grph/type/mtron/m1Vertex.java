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

package studio.phaseshift.metatron.lang.db.grph.type.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.Optimizations;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.apache.tinkerpop.gremlin.structure.Direction.IN;
import static org.apache.tinkerpop.gremlin.structure.Direction.OUT;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.get_;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.parser.TP3Translator.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class m1Vertex {

    private static final Uri OUT_URI = uri(OUT.name());
    private static final Uri IN_URI = uri(IN.name());
    private static final Uri PLUS_URI = uri(f("+"));

    public static final class m1VertexType {
        public static Set<Inst> insts() {
            Optimizations.optimizedEquals.put(VERTEX_TID, (a, b) -> b.tid().basePath().equals(a.tid().basePath()) && a.vid() != null && Objects.equals(a.vid(), b.vid()));
            Optimizations.optimizedEquals.put(EDGE_TID, (a, b) ->
                    b.tid().basePath().equals(a.tid().basePath())
                            && a.<Rec>as().at(LABEL).equals(b.<Rec>as().at(LABEL))
                            && a.<Rec>as().jvm().get(uri(OUT.name())).equals(b.<Rec>as().jvm().get(uri(OUT.name())))
                            && a.<Rec>as().jvm().get(uri(IN.name())).equals(b.<Rec>as().jvm().get(uri(IN.name()))));
            return new LinkedHashSet<>(List.of(
                    instC(V_INST_TID.dom(URI_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                            inst.arg(0).isNoObj() ?
                                    Router.global().read(lhs.uriValue().extend("V/+")) :
                                    inst.args().count() == 1L ?
                                            Router.global().read(lhs.uriValue().extend("V").extend(inst.arg(0).uriValue())) :
                                            inst.args().elements().map(i -> Router.global().read(lhs.uriValue().extend("V").extend(i.uriValue()))).reduce(Obj::append).orElse(noobj())),
                    //instC(BOTH_INST_TID.dom(VERTEX_TID.maybeSome()).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> split_(lst(instB(OUT_INST_TID, lst(inst.arg(0))), instB(IN_INST_TID, lst(inst.arg(0))))).merge_().apply(lhs)),
                    //instC(BOTHE_INST_TID.dom(VERTEX_TID.maybeSome()).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> split_(lst(instB(OUTE_INST_TID, lst(inst.arg(0))), instB(INE_INST_TID, lst(inst.arg(0))))).merge_().apply(lhs)),
                    instC(OUT_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(uri(f(OUT.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue()).extend(Direction.IN.name())))),
                    instC(OUTE_INST_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(uri(f(Direction.OUT.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue())))),
                    //
                    instC(IN_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(uri(f(Direction.IN.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue()).extend(OUT.name())))),
                    instC(INE_INST_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(uri(f(Direction.IN.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue())))),
                    //
                    instC(BOTH_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(
                            lhs.asRec().at(uri(f(Direction.OUT.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue()).extend(IN.name()))),
                            lhs.asRec().at(uri(f(Direction.IN.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue()).extend(OUT.name()))))),
                    instC(BOTHE_INST_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(
                            lhs.asRec().at(uri(f(Direction.OUT.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue()))),
                            lhs.asRec().at(uri(f(Direction.IN.name()).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue()))))),
                    //
                    instC(OUTV_INST_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> get_(uri(OUT.name()))),
                    instC(INV_INST_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> get_(uri(Direction.IN.name()))),
                    instC(VALUES_INST_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> get_(uri(f(PROPS).extend(inst.arg(0).isNoObj() ? f("+") : inst.arg(0).uriValue())))),
                    instC(AS_INST_TID.dom(VERTEX_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str("{{y}}v{{g}}[{{b}}" + lhs.asRec().at(ID) + "{{g}}]")),
                    instC(AS_INST_TID.dom(EDGE_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str("{{y}}e{{g}}[][{{b}}" + lhs.asRec().at(OUT.name()).asRec().at(ID) + "=" + lhs.asRec().at(LABEL) + "=>" + lhs.asRec().at(IN.name()).asRec().at(ID) + "{{g}}]"))
                    // instC(PROPERTIES_INST_TID.dom(ALL.maybeSome()).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> get_(uri(PROPS))(inst.arg(0)).apply(lhs))
            ));
        }

    }

}
