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

package studio.phaseshift.metatron.isa.llm.ollama.type;

import io.github.ollama4j.tools.ToolFunction;
import io.github.ollama4j.tools.Tools;
import studio.phaseshift.metatron.furi.q.DocQ;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.DOM;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class OLLM {

    private static final GraphittyLogger LOG = Graphitty.log(OLLM.class);

    public static Tools.Tool mtronInstTool(final Inst inst) {
        final DocQ.Doc doc = Router.readFromSpace(inst.tid().query("doc", null))
                .orSupply(() -> DocQ.Doc.doc(inst,
                        inst.dom().tid().toString(),
                        inst.rng().tid().toString(),
                        instB(AS_INST_TID, lst(REC_TYPE)).apply(inst.args().orElse(rec0())).asRec().elements().collect(Collectors.toMap(
                                Rel::first,
                                e -> e.second().tid().toString()
                        )),
                        "<no description>"));
        LOG.info("building ollama compliant tool from mtron inst: %s => %s", inst.tid(), doc);
        Map<String, Tools.Property> instProperties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        instProperties.put("lhs", Tools.Property.builder().description(doc.at(DOM).toString()).required(!inst.dom().c().isZeroable()).type(inst.tid().dom().toString()).build());
        instProperties.putAll(inst.args().isLst() ?
                inst.args().asLst().indexedStream().collect(Collectors.toMap(
                        e -> e.first().intValue().toString(),
                        e -> Tools.Property.builder()
                                .required(!e.second().c().isZeroable())
                                .type(e.second().tid().toString())
                                .description(doc.args().at(e.first()).toString()).build())) :
                inst.args().asRec().elements().collect(Collectors.toMap(
                        e -> e.first().uriValue().toString(),
                        e -> Tools.Property.builder()
                                .required(!e.second().c().isZeroable())
                                .type(e.second().tid().toString())
                                .description(doc.args().at(e.first()).toString()).build()
                )));
        instProperties.values().forEach(p -> required.add(p.isRequired() ? "true" : "false"));

        return Tools.Tool.builder()
                .toolSpec(Tools.ToolSpec.builder()
                        .name(inst.tid().name())
                        .description(doc.description())
                        .parameters(new Tools.Parameters(instProperties, required)).build())
                .toolFunction(arguments -> {
                    final Poly<?, ?> args = inst.args().isNoObj() ? lst() : (inst.args().isLst() ?
                            lst(arguments.entrySet().stream().filter(e -> !e.getKey().equals("lhs")).map(e -> MObjFactory.single().toObjFromString(e.getValue().toString())).collect(Collectors.toList())) :
                            rec(arguments.entrySet().stream().filter(e -> !e.getKey().equals("lhs")).collect(Collectors.toMap(e -> uri(e.getKey()), e -> MObjFactory.single().toObjFromString(e.getValue().toString())))));
                    final Object result = inst
                            .args(args)
                            .apply(MObjFactory.single().toObjFromString(arguments.get("lhs").toString()));
                    LOG.info("evaluating mtron_inst tool: %s => %s => %s", arguments.get("lhs"), inst, result);
                    return result;
                })
                .isMCPTool(false)
                .type(inst.rng().tid().toString())
                .build();
    }

    public static Tools.Tool mtronEvalTool() {
        return Tools.Tool.builder()
                .toolSpec(Tools.ToolSpec.builder()
                        .name("mtron_eval")
                        .description("evaluate mtron source code and get back an obj result")
                        .parameters(new Tools.Parameters(Map.of(
                                "code", Tools.Property.builder().required(true).type("string").description("mtron sourcecode to evaluate").build()),
                                List.of("true")))
                        .build())
                .toolFunction(new ToolFunction() {
                    @Override
                    public Object apply(final Map<String, Object> arguments) {
                        LOG.info("evaluating mtron_eval tool: %s", arguments.get("code"));
                        return mParser.eval((String) arguments.get("code"));
                    }
                }).isMCPTool(false).type("obj").build();
    }
    
    /*public OLLM(final Tuple.Pair<OllamaModel, OllamaModelCard> model, final fURI tid, final fURI vid) {
        super(modelToRec(model), tid, vid);
    }
    

    public static OLLM ollm(final fURI host, final Tuple.Pair<OllamaModel, OllamaModelCard> model, final fURI tid, final fURI vid) {
        final OLLM ollm = new OLLM(model, tid, vid);
        return ollm.at(uri(HOST), uri(host), MUTABLE).as();
    }

    public String name() {
        return this.at(NAME).uriValue().toString();
    }

    public OLLM clone() {
        return this;
    }

    public OLLM clone(final Object model, fURI tid, final fURI vid) {
        return (OLLM) super.clone(model, tid, vid);
    }

    public static final ToolSpecification toolSpecification = ToolSpecification.builder()
            .name("mtron")
            .description("evaluate an metatron expression")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("code", "metatron code to evaluate")
                    .required("code")
                    .build())
            .build();

    public static class MetatronTools {

        public MetatronTools() {

        }

        @Tool("executes metatron code and returns an obj result")
        Obj evaluate(
                @P("the metatron code to evaluate") String code
        ) {
            return mParser.eval(code);
        }
    }*/
}