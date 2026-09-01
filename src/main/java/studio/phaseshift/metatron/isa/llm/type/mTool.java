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
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Json.fromJson;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.furi.q.QCollection.Docs.doc;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_TID;
import static studio.phaseshift.metatron.isa.llm.parser.JsonSchemaGenerator.objToSchema;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mTool extends MRec {

    /**
     * Stashes raw Obj results before LC4j serialization, keyed by tool call id.
     * Retrieved by {@code ToolFeature.onToolExecuted} to preserve nested structure.
     */
    public static final ConcurrentHashMap<String, Obj> resultStash = new ConcurrentHashMap<>();

    public mTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Normalize any tool element — a bare inst, a docs wrapping an inst
     * (the shape {@code skill::T} recs carry in their {@code tool} field), or
     * an {@code mTool} — into a canonical {@code mTool} ready for the
     * {@code ToolFeature} registry.
     *
     * @param element the tool element to normalize
     * @return the canonical mTool
     */
    public static mTool tool(final Obj element) {
        if (element instanceof mTool mt)
            return mt;
        if (element instanceof QCollection.Docs toolDocs) {
            final Map<Obj, Obj> newJVM = new LinkedHashMap<>();
            newJVM.put(uri(INST), toolDocs.at(uri(OBJ)));
            newJVM.put(uri(NAME), uri(toolName(toolDocs.at(uri(OBJ)).tid())));
            newJVM.put(uri(DESC), toolDocs.at(uri(DESC)));
            newJVM.put(uri(ARGS), toolDocs.at(ARG));
            return new mTool(newJVM, LLM_TOOL_TID, null);
        }
        if (element instanceof Inst inst) {
            final QCollection.Docs docs = mtronInstToDocs(inst);
            final Map<Obj, Obj> jvm = new java.util.LinkedHashMap<>(docs.jvm());
            jvm.putIfAbsent(uri(INST), inst);
            jvm.putIfAbsent(uri(NAME), uri(toolName(inst.tid())));
            jvm.putIfAbsent(uri(DESC), str("a tool forwarded from a skill"));
            jvm.putIfAbsent(uri(ARGS), docs.at(ARG));
            return new mTool(jvm, LLM_TOOL_TID, inst.vid());
        }
        final Obj obj = (element.asRec().has(uri(OBJ)) ? element.asRec().atDirect(uri(OBJ)) : element);
        if (obj.isObjInst())
            return tool(mtronInstToDocs(obj.asInst()));
        if (element.isRec()) {
            return new mTool(element.jvm(), LLM_TOOL_TID, element.vid());
        }
        throw MTronException.of("unknown object can not be converted to tool: %s", element);
    }

    /**
     * Identity of this tool for registry upserts: the {@code name} field
     * when present, otherwise the flattened tid of the wrapped inst.
     *
     * @return the canonical name of this tool
     */
    public fURI name() {
        if (this.has(NAME))
            return this.at(NAME).uriValue();
        final Obj obj = this.atDirect(uri(INST));
        if (obj.isObjInst())
            return f(mTool.toolName(obj.asInst().tid()));
        return this.tid();
    }

    /**
     * The single source of truth for mapping an instruction's tid to its MCP
     * tool name: flatten the base path, dropping leading slashes and replacing
     * {@code '/'} with {@code '_'}.  e.g. {@code /m/llm/feature/chat_feature/inst/agent_chat}
     * becomes {@code m_llm_feature_chat_feature_inst_agent_chat}.
     */
    public static String toolName(final fURI tid) {
        return tid.basePath().toString().replaceAll("^/+", "").replace("/", "_");
    }

    public Tuple.Pair<ToolSpecification, ToolExecutor> toolSpecification() {
        return mtronInstToolSpecification(doc(this.at(INST).asInst()));
    }

    public static Tuple.Pair<ToolSpecification, ToolExecutor> mtronInstToolSpecification(final QCollection.Docs doc) {
        final Inst inst = doc.atDirect(uri(OBJ));
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
                .name(toolName(inst.tid()))
                .description(doc.description())
                .parameters(parameters.build());

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
            Map<String, Object> arguments = fromJson(toolExecutionRequest.arguments(), Map.class);
            final Poly<?, ?> args = inst.args().isNoObj() ? lst() : (inst.args().isLst() ?
                    lst(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).map(e -> ObjmtronSerializer.<Obj>parse(e.getValue().toString())).collect(Collectors.toList())) :
                    rec(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).collect(Collectors.toMap(e -> uri(e.getKey()), e -> ObjmtronSerializer.parse(e.getValue().toString())))));
            final Obj result = inst
                    .args(args)
                    .apply(ObjmtronSerializer.compact().read(Optional.ofNullable(arguments.get(LHS)).orElse(noobj()).toString()));
            inst.logger().debug("evaluating mtron_inst tool: %s => %s => %s", Optional.ofNullable(arguments.get(LHS)).orElse(noobj()), inst, result);
            // Stash the raw Obj so ToolFeature can embed it in the monad tree
            resultStash.put(toolExecutionRequest.id(), result);
            final String stringResult = result.toCleanString();
            return (null == stringResult || stringResult.isBlank()) ? "noobj" : stringResult; // prevents llm protocol from failing on empty or null results
        };
        return Tuple.Pair.with(toolSpecBuilder.build(), toolExecutor);
    }

    public static QCollection.Docs mtronInstToDocs(final Inst inst) {
        final Obj found = Router.readFromSpace(inst.tid().addQ(DOCQ)).stream().findFirst().orElse(noobj());
        final QCollection.Docs doc = QCollection.isNoDocs(found) ? doc(inst,
                inst.dom().tid().toString(),
                inst.rng().tid().toString(),
                instB(AS_INST_TID, lst(REC_TYPE)).apply(inst.args().orElse(rec0())).asRec().elements().collect(Collectors.toMap(
                        Rel::first,
                        e -> e.second().tid().toString()
                )),
                "<no description>") : doc(found.asRec());
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
        final Inst inst = instC(f(spec.name()).dom(ALL.maybe()).rng(STR_TID.maybeSome()), rec(argTypes), (lhs, i) -> {
            final String argsJson = i.args().isNoObj() ? "{}" :
                    new String(ObjJSONSerializer.simple().outputBytes(i.args()).array(), StandardCharsets.UTF_8);
            final String result = executor.execute(ToolExecutionRequest.builder().name(spec.name()).arguments(argsJson).build(), null);
            return str(result);
        });
        final String description = null == spec.description() || spec.description().isBlank() ? "<no description>" : spec.description();
        return doc(inst, "<no description>", "<no description>", argDescs, description);
    }
}
