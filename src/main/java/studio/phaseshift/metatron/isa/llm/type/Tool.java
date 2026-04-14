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

package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Json.fromJson;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.furi.q.QCollection.Docs.doc;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_TID;
import static studio.phaseshift.metatron.isa.llm.type.Model.Helper.objToSchema;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Tool extends MRec {

    public static final Type LLM_TOOL_TYPE = docWrap(Type.Builder.build().tid(REC_TID).vid(LLM_TOOL_TID).isaPredicate(rec(
                    uri(INST), T(ALL),
                    uri(NAME), URI_TYPE,
                    uri(DESC), STR_TYPE,
                    uri(ARG).maybe(), rec(URI_TYPE, T(ALL)).maybe())).create(),
            "a tool specification", "",
            Map.of(
                    uri(NAME), "tool name",
                    uri(DESC), "tool description",
                    uri(ARG).maybe(), "tool arguments"),
            "a tool function for the llm to use",
            "*eval.as(tool::T)   [-- see as?tool<=inst() --]");

    public Tool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Tool tool(final Rec tool) {
        return new Tool(tool.jvm(), LLM_TOOL_TID, tool.vid());
    }

    public static Tuple.Pair<ToolSpecification, ToolExecutor> mtronInstToolSpecification(final Rec tool) {
        final Inst inst = tool.at(INST);
        final QCollection.Docs doc = doc(Router.global().read(inst.tid().addQ(DOCQ)).as());
        JsonObjectSchema.Builder parameters = new JsonObjectSchema.Builder();
        List<String> required = new ArrayList<>();
        parameters.addProperty(LHS, objToSchema(inst.dom(), Type.Helper.polyTypePredicateObj(inst.dom()), doc.at(DOM).orElse(str("<no description>")).strValue()));
        if (!inst.tid().dom().c().isZeroable())
            required.add(LHS);
        final boolean recArgs = doc.args().isRec();
        final AtomicInteger counter = new AtomicInteger(0);
        doc.args().elements().forEach(e -> {
            final Rel kv = recArgs ? e.asRel() : rel(uri(ARG + counter.getAndIncrement()), e);
            parameters.addProperty(
                    kv.first().toString(),
                    objToSchema(kv.second().type(), Type.Helper.polyTypePredicateObj(kv.second().type()), kv.second().orElse(str("<no description>")).strValue()));
            if (!kv.second().c().isZeroable())
                required.add(kv.first().toString());
        });
        parameters.required(required);
        ToolSpecification.Builder toolSpecBuilder = ToolSpecification.builder()
                .name(inst.tid().basePath().toString().replaceAll("^/+", "").replace("/", "_"))
                .description(doc.description())
                .parameters(parameters.build());

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
            Map<String, Object> arguments = fromJson(toolExecutionRequest.arguments(), Map.class);
            final Poly<?, ?> args = inst.args().isNoObj() ? lst() : (inst.args().isLst() ?
                    lst(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).map(e -> MObjFactory.single().toObjFromString(e.getValue().toString())).collect(Collectors.toList())) :
                    rec(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).collect(Collectors.toMap(e -> uri(e.getKey()), e -> MObjFactory.single().toObjFromString(e.getValue().toString())))));
            final Object result = inst
                    .args(args)
                    .apply(arguments.containsKey(LHS) ? MObjFactory.single().toObjFromString(arguments.get(LHS).toString()) : noobj());
            inst.logger().info("evaluating mtron_inst tool: %s => %s => %s", arguments.get(LHS), inst, result);
            return result.toString();
        };
        return Tuple.Pair.with(toolSpecBuilder.build(), toolExecutor);
    }

    public static Rec mtronDocToTool(final QCollection.Docs doc) {
        final Inst inst = doc.at(INST);
        return rec(Map.of(uri(INST), inst, uri(NAME), uri(inst.tid()), uri(DESC), str(doc.description()), uri(ARG), doc.args()), LLM_TOOL_TID, null);
    }


    public static Rec mtronInstToTool(final Inst inst) {
        final QCollection.Docs doc = Router.readFromSpace(inst.tid().addQ(DOCQ))
                .orSupply(() -> doc(inst,
                        inst.dom().tid().toString(),
                        inst.rng().tid().toString(),
                        instB(AS_INST_TID, lst(REC_TYPE)).apply(inst.args().orElse(rec0())).asRec().elements().collect(Collectors.toMap(
                                Rel::first,
                                e -> e.second().tid().toString()
                        )),
                        "<no description>"));
        inst.logger().info("building ai compliant tool from mtron inst: %s => %s", inst.tid(), doc);
        return rec(Map.of(uri(INST), inst, uri(NAME), uri(inst.tid()), uri(DESC), str(doc.description()), uri(ARG), doc.args()), LLM_TOOL_TID, null);
    }
}
