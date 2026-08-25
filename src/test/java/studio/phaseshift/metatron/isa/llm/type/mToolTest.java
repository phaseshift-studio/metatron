package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.Docs.doc;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tests for the {@code tool::T} type ({@link mTool}) — {@link mTool#toolName}
 * tid flattening, the mtron↔LC4j tool conversions in both directions
 * ({@link mTool#mtronInstToolSpecification}, {@link mTool#mtronInstToTool},
 * {@link mTool#toolToMtronDoc}, {@link mTool#mtronDocToTool}), the generated
 * executor (evaluation + {@link mTool#resultStash}), and the tool::T shape.
 */
public class mToolTest extends studio.phaseshift.metatron.AbstractMetatronTest {

    private static final fURI TOOL_TID = f("/m/test/tool");

    /**
     * A tool inst: optional str dom (lhs), one named {@code query} str arg.
     */
    private static QCollection.Docs testToolDoc() {
        final Inst inst = instC(TOOL_TID.dom(STR_TID.maybe()).rng(STR_TID),
                rec(uri("query"), STR_TYPE),
                (lhs, i) -> str("result:" + i.arg(0)));
        return doc(inst, "<dom>", "<rng>", Map.of(uri("query"), "the query string"), "a test tool");
    }

    // ── toolName tid flattening ────────────────────────────────────

    @Test
    public void testToolNameFlattensTid() {
        assertEquals("m_llm_feature_chat_feature_inst_agent_chat",
                mTool.toolName(LLM_CHAT_FEATURE_TID.extend(INST).extend("agent_chat")),
                "base path is flattened with leading slash stripped");
        assertEquals("m_test_tool", mTool.toolName(TOOL_TID));
        assertEquals("simple", mTool.toolName(f("/simple")));
    }

    // ── mtron inst → Docs ──────────────────────────────────────────

    @Test
    public void testMtronInstToToolBuildsDefaultDocs() {
        final Inst inst = instC(f("/m/test/undocumented").dom(ALL.maybe()).rng(STR_TID.maybeSome()), lst(STR_TYPE),
                (lhs, i) -> str("ok"));
        final QCollection.Docs docs = mTool.mtronInstToTool(inst);
        assertNotNull(docs, "an inst without docq docs still yields a Docs");
        assertEquals(inst, docs.at(OBJ), "the Docs carries the inst");
        assertEquals("<no description>", docs.description(), "undocumented inst gets a default description");
    }

    // ── mtron Docs → LC4j ToolSpecification ────────────────────────

    @Test
    public void testToolSpecificationNameDescAndParameters() {
        final ToolSpecification spec = mTool.mtronInstToolSpecification(testToolDoc()).get0();
        assertEquals("m_test_tool", spec.name(), "name is the flattened tid");
        assertEquals("a test tool", spec.description(), "description from the doc");
        assertNotNull(spec.parameters(), "args produce a parameter schema");
        assertTrue(spec.parameters().properties().containsKey("query"), "named arg becomes a property");
        assertTrue(spec.parameters().properties().containsKey("lhs"), "the inst dom becomes the lhs property");
        assertEquals(List.of("query"), spec.parameters().required(), "required args are marked; optional dom is not");
    }

    @Test
    public void testToolExecutorEvaluatesInstAndStashes() throws Exception {
        final Tuple.Pair<ToolSpecification, ToolExecutor> pair = mTool.mtronInstToolSpecification(testToolDoc());
        final String result = pair.get1().execute(ToolExecutionRequest.builder()
                .name("m_test_tool")
                .arguments("{\"query\":\"hello\"}")
                .id("test-call-1")
                .build(), null);
        assertNotNull(result, "executor must return a string");
        assertFalse(result.isBlank(), "executor result must not be blank");
        assertTrue(result.contains("result:"), "executor evaluates the mtron inst body");
        assertTrue(mTool.resultStash.values().stream().anyMatch(o -> o.toString().contains("result:")),
                "the raw Obj result is stashed for ToolFeature");
    }

    // ── Docs → tool::T rec ─────────────────────────────────────────

    @Test
    public void testMtronDocToToolRec() {
        final Rec toolRec = mTool.mtronDocToTool(testToolDoc());
        assertEquals(LLM_TOOL_TID, toolRec.tid(), "the rec is a tool::T");
        assertEquals("/m/test/tool", toolRec.at(uri(NAME)).uriValue().toString(), "name is the inst's base path");
        assertEquals("a test tool", toolRec.at(uri(DESC)).strValue(), "description from the doc");
        assertFalse(toolRec.at(uri(INST)).isNoObj(), "the inst is carried on the rec");
    }

    // ── LC4j tool → mtron Docs (and back) ──────────────────────────

    @Test
    public void testToolToMtronDocRoundTrip() {
        final ToolSpecification spec = ToolSpecification.builder()
                .name("m_round_trip")
                .description("a round trip tool")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query")
                        .required(List.of("query"))
                        .build())
                .build();
        final QCollection.Docs docs = mTool.toolToMtronDoc(spec, (req, memoryId) -> "executed");
        final ToolSpecification back = mTool.mtronInstToolSpecification(docs).get0();
        assertEquals("m_round_trip", back.name(), "tool name round-trips through toolToMtronDoc");
        assertEquals("a round trip tool", back.description(), "description round-trips");
        assertTrue(back.parameters().properties().containsKey("query"), "the parameter schema survives the round-trip");
    }

    @Test
    public void testToolToMtronDocDelegatesToExecutor() {
        final ToolSpecification spec = ToolSpecification.builder().name("m_delegate").build();
        final AtomicReference<String> called = new AtomicReference<>();
        final ToolExecutor executor = (req, memoryId) -> {
            called.set(req.name());
            return "executed";
        };
        final Inst inst = mTool.toolToMtronDoc(spec, executor).at(OBJ);
        final Obj result = inst.args(rec(uri("query"), str("hello"))).apply(noobj());
        assertEquals("executed", result.strValue(), "the mtron inst body delegates to the LC4j executor");
        assertEquals("m_delegate", called.get(), "the executor is invoked with the tool name");
    }

    // ── tool(Rec) factory ──────────────────────────────────────────

    @Test
    public void testToolFactory() {
        // tool::T requires inst + name + desc
        final Rec r = rec(uri(INST), instLambda((lhs, i) -> noobj()), uri(NAME), uri("foo"), uri(DESC), str("d"));
        final mTool tool = mTool.tool(r);
        assertEquals(LLM_TOOL_TID, tool.tid(), "tool factory stamps the tool tid");
        assertEquals(r.vid(), tool.vid(), "rec vid is preserved");
    }

    @Test
    public void testToolTypeShape() {
        // objCheckAndSave validates a rec against tool::T at construction
        final Map<Obj, Obj> valid = new LinkedHashMap<>();
        valid.put(uri(INST), instLambda((lhs, i) -> noobj()));
        valid.put(uri(NAME), uri("n"));
        valid.put(uri(DESC), str("d"));
        assertDoesNotThrow(() -> rec(valid, LLM_TOOL_TID, null),
                "an inst/name/desc rec satisfies the tool::T type");
        final Map<Obj, Obj> noInst = new LinkedHashMap<>();
        noInst.put(uri(NAME), uri("n"));
        noInst.put(uri(DESC), str("d"));
        assertThrows(Exception.class, () -> rec(noInst, LLM_TOOL_TID, null),
                "inst is required by tool::T");
    }
}
