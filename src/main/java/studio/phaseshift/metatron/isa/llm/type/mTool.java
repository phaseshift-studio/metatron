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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.parser.JsonSchemaGenerator;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Json.fromJson;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.furi.q.QCollection.Docs.doc;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_TID;
import static studio.phaseshift.metatron.isa.llm.parser.JsonSchemaGenerator.objToSchema;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mTool extends MRec {

    /**
     * Stashes raw Obj results before LC4j serialization, keyed by tool call id.
     * Retrieved by {@code ToolFeature.onToolExecuted} to preserve nested structure.
     */
    public static final ConcurrentHashMap<String, Obj> resultStash = new ConcurrentHashMap<>();

    public static final Type LLM_TOOL_TYPE = docWrap(Type.Builder.build().tid(REC_TID).vid(LLM_TOOL_TID).isaPredicate(rec(
                    uri(INST), T(ALL),
                    uri(NAME), URI_TYPE,
                    uri(DESC), STR_TYPE,
                    uri(ARG).maybe(), T(ALL) /*rec(URI_TYPE, T(ALL)).maybe())*/)).create(),
            "a tool specification", "",
            Map.of(
                    uri(NAME), "tool name",
                    uri(DESC), "tool description",
                    uri(ARG).maybe(), "tool arguments"),
            "a tool function for the llm to use");

    public mTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static mTool tool(final Rec tool) {
        return new mTool(tool.jvm(), LLM_TOOL_TID, tool.vid());
    }

    public static Tuple.Pair<ToolSpecification, ToolExecutor> mtronInstToolSpecification(final QCollection.Docs doc) {
        final Inst inst = doc.at(OBJ);
        JsonObjectSchema.Builder parameters = new JsonObjectSchema.Builder();
        List<String> required = new ArrayList<>();
        if (!inst.tid().dom().isZero()) {
            parameters.addProperty(LHS, objToSchema(inst.dom(), Type.Helper.polyTypePredicateObj(inst.dom()), doc.at(DOM).orElse(str("<no description>")).strValue()));
            if (!inst.tid().dom().c().isZeroable())
                required.add(LHS);
        }
        final Poly<?, ?> instArgs = inst.args().orElse(rec0());
        if (instArgs.isRec()) {
            instArgs.asRec().elements().forEach(e -> {
                final Rel kv = e.asRel();
                final Obj desc = doc.args().at(kv.first());
                parameters.addProperty(kv.first().uriValue().basePath().toString(),
                        objToSchema(kv.second().type(), Type.Helper.polyTypePredicateObj(kv.second().type()), desc.isNoObj() ? "<no description>" : desc.strValue()));
                if (!kv.second().c().isZeroable())
                    required.add(kv.first().uriValue().basePath().toString());
            });
        } else {
            instArgs.asLst().indexedStream().forEach(r -> {
                final Obj desc = doc.args().at(r.first());
                parameters.addProperty(r.first().toString(),
                        objToSchema(r.second().type(), Type.Helper.polyTypePredicateObj(r.second().type()), desc.isNoObj() ? "<no description>" : desc.strValue()));
                if (!r.second().c().isZeroable())
                    required.add(r.first().toString());
            });
        }
        parameters.required(required);
        ToolSpecification.Builder toolSpecBuilder = ToolSpecification.builder()
                .name(inst.tid().basePath().toString().replaceAll("^/+", "").replace("/", "_"))
                .description(doc.description())
                .parameters(parameters.build());

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
            Map<String, Object> arguments = fromJson(toolExecutionRequest.arguments(), Map.class);
            final Poly<?, ?> args = inst.args().isNoObj() ? lst() : (inst.args().isLst() ?
                    lst(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).map(e -> ObjmtronSerializer.<Obj>parse(e.getValue().toString())).collect(Collectors.toList())) :
                    rec(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).collect(Collectors.toMap(e -> uri(e.getKey()), e -> ObjmtronSerializer.parse(e.getValue().toString())))));
            final Obj result = inst
                    .args(args)
                    .apply(arguments.containsKey(LHS) ? ObjmtronSerializer.compact().read(arguments.get(LHS).toString()) : noobj());
            inst.logger().debug("evaluating mtron_inst tool: %s => %s => %s", arguments.getOrDefault(LHS, noobj()), inst, result);
            // Stash the raw Obj so ToolFeature can embed it in the monad tree
            resultStash.put(toolExecutionRequest.id(), result);
            final String stringResult = ObjmtronSerializer.compact().write(result);
            return (null == stringResult || stringResult.isBlank()) ? "noobj" : stringResult; // prevents llm protocol from failing on empty or null results
        };
        return Tuple.Pair.with(toolSpecBuilder.build(), toolExecutor);
    }

    public static Rec mtronDocToTool(final QCollection.Docs doc) {
        final Inst inst = doc.at(INST);
        return rec(mutableMap(uri(INST), inst, uri(NAME), uri(inst.tid().basePath()), uri(DESC), str(doc.description()), uri(ARG), doc.args()), LLM_TOOL_TID, null);
    }


    public static QCollection.Docs mtronInstToTool(final Inst inst) {
        final QCollection.Docs doc = (QCollection.Docs) Router.readFromSpace(inst.tid().addQ(DOCQ)).stream().findFirst().orElseGet(() -> doc(inst,
                inst.dom().tid().toString(),
                inst.rng().tid().toString(),
                instB(AS_INST_TID, lst(REC_TYPE)).apply(inst.args().orElse(rec0())).asRec().elements().collect(Collectors.toMap(
                        Rel::first,
                        e -> e.second().tid().toString()
                )),
                "<no description>"));
        inst.logger().debug("building ai compliant tool from mtron inst: %s", inst.tid());
        return doc;//rec(mutableMap(uri(INST), inst, uri(NAME), uri(inst.tid()), uri(DESC), str(doc.description()), uri(ARG), doc.args()), LLM_TOOL_TID, null);
    }

    /**
     * The inverse of {@link #mtronInstToolSpecification(QCollection.Docs)}:
     * reconstruct a metatron tool {@code Docs} (and its backing inst) from a
     * LangChain4j {@link ToolSpecification} / {@link ToolExecutor} pair.  Used
     * to fold a {@code ToolProvider}'s tools into an agent-skill's {@code tool}
     * field.
     * <p>
     * The tool name becomes the inst's tid (so the forward
     * {@code basePath → _} naming round-trips), the parameter schema becomes the
     * inst's typed args via {@link JsonSchemaGenerator#schemaToType}, and the
     * executor is wrapped in the inst body.
     *
     * @param spec     the tool specification (name, description, parameters)
     * @param executor the tool executor to delegate to
     * @return a {@code Docs} carrying the inst plus its description/args
     */
    public static QCollection.Docs toolToMtronDoc(final ToolSpecification spec, final ToolExecutor executor) {
        final JsonObjectSchema params = spec.parameters();
        final Map<Obj, Obj> argTypes = new LinkedHashMap<>();
        final Map<Obj, String> argDescs = new LinkedHashMap<>();
        if (null != params) {
            params.properties().forEach((name, sub) -> {
                argTypes.put(uri(name), JsonSchemaGenerator.schemaToType(sub));
                argDescs.put(uri(name), null == sub.description() || sub.description().isBlank() ? "<no description>" : sub.description());
            });
        }
        final Inst inst = instC(f(spec.name()).rng(STR_TID.maybeSome()), rec(argTypes), (lhs, i) -> {
            final String argsJson = i.args().isNoObj() ? "{}" :
                    new String(ObjJSONSerializer.simple().outputBytes(i.args()).array(), StandardCharsets.UTF_8);
            final String result = executor.execute(ToolExecutionRequest.builder().name(spec.name()).arguments(argsJson).build(), null);
            return str(result);
        });
        final String description = null == spec.description() || spec.description().isBlank() ? "<no description>" : spec.description();
        return doc(inst, "<no description>", "<no description>", argDescs, description);
    }
}
