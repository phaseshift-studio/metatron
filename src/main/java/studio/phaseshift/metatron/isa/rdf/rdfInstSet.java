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

package studio.phaseshift.metatron.isa.rdf;

import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.repository.util.Repositories;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.rdf.parser.ObjRDFSerializer;
import studio.phaseshift.metatron.isa.rdf.space.rdfSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.else_;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.rdf.parser.ObjRDFSerializer.OBJ_RDF_SERIALIZER_VID;
import static studio.phaseshift.metatron.isa.rdf.space.rdfSpace.RDF_SPACE_TID;
import static studio.phaseshift.metatron.isa.rdf.space.rdfSpace.SPARQL_INST_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/rdf")
public class rdfInstSet extends AbstractInstSet {

    public static final fURI RDF_ISA_TID = M_ISA_TID.extend("rdf");
    public static final fURI RDF_INST_TID = RDF_ISA_TID.extend("inst");
    public static final fURI RDF_ISA_SPACE_TID = RDF_ISA_TID.extend(SPACE);
    public static final fURI RDF_TRIPLE_TID = RDF_ISA_SPACE_TID.extend("triple");
    public static final fURI RDF_QUAD_TID = RDF_ISA_SPACE_TID.extend("quad");
    public static Type RDF_SPACE_TYPE;

    public static final Type RDF_TRIPLE_TYPE = Type.Builder.build()
            .tid(LST_TID)
            .vid(RDF_TRIPLE_TID)
            .isaPredicate(lst(URI_TYPE, URI_TYPE, URI_TYPE)).create();

    public static final Type RDF_QUAD_TYPE = Type.Builder.build()
            .tid(RDF_TRIPLE_TID)
            .vid(RDF_QUAD_TID)
            .isaPredicate(lst(URI_TYPE, URI_TYPE, URI_TYPE, URI_TYPE)).create();


    public rdfInstSet() {
        super(mutableMap(uri(PATTERN), uri(RDF_ISA_TID.extend(ALL))), INSTSET_TID, RDF_ISA_TID);
    }

    @Override
    public void setup() {
        //this.selfTID(INSTSET_TID);
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(RDF_ISA_TID.extend(ALL)),
                uri(CONST), lst(ObjRDFSerializer.single()),
                uri(TYPE), lst(
                        docWrap(RDF_TRIPLE_TYPE, "an rdf triple as a subject, predicate, object"),
                        docWrap(RDF_QUAD_TYPE, "an rdf quad as a subject, predicate, object, graph"),
                        docWrap(RDF_SPACE_TYPE =
                                Type.Builder.build()
                                        .tid(SPACE_TID)
                                        .vid(RDF_SPACE_TID)
                                        .isaPredicate(rec(
                                                uri(PATTERN), URI_TYPE,
                                                uri(HOST), URI_TYPE,
                                                uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                                                uri(ROOT).maybe(), REC_TYPE,
                                                uri(SERIALIZER).maybe(), else_(auto_from_(OBJ_RDF_SERIALIZER_VID))))
                                        //uri(SCHEMA).maybe(), InstSet.INSTSET_TYPE))
                                        .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).dom(ALL.maybe()).rng(RDF_SPACE_TID),
                                                lst(REC_TYPE),
                                                (lhs, inst) -> {
                                                    Graphitty.log(rdfSpace.class).info("rdfSpace constructor: %s", inst);
                                                    return rdfSpace.of(inst.arg(0).recValue(), inst.arg(0).vid());
                                                }))
                                        .create(), "a metatron realization of an rdf quad/triple store")),
                uri(INST), lst(Stream.of(
                        docWrap(instC(SPARQL_INST_TID.dom(RDF_SPACE_TID).rng(REC_TID.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                                    try {
                                        final rdfSpace<?> rdfSpace = lhs.as();
                                        final Obj results = objs(Repositories.tupleQuery(rdfSpace.sjvm(), inst.arg(0).strValue(), QueryResults::stream)
                                                .peek(x -> LOG.info("sparql result: %s",x))
                                                .map(r -> {
                                                    final Map<Obj, Obj> rowMap = new LinkedHashMap<>();
                                                    for (final String name : r.getBindingNames()) {
                                                        rowMap.put(uri(name), str(r.getValue(name).stringValue()));
                                                    }
                                                    return rec(rowMap);
                                                }));
                                        return results;
                                    } catch (final Exception e) {
                                        throw MTronException.of(e);
                                    }
                                }), "an rdfspace typically backed by a sparql-compliant triple/quad store",
                                "a stream of named bindings",
                                Map.of(jnt(0), "a sparql query"),
                                "query a triple/quad store in native sparql and yield an mtron mapped result set",
                                "*/sys/space/wikipedia.sparql('SELECT ?x ?y WHERE ?x foaf:knows ?y') [-- a sparql query with named bindings --]")))));
        docWrap(this,
                "expose metatron to the semantic web through rdf stores (databases) and rdf documents (rdf/json)",
                "*<wikipedia://www.wikidata.org/wiki/Q54872>.>>o");
        super.setup();
    }
}
