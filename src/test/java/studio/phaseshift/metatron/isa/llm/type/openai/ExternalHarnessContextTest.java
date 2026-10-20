package studio.phaseshift.metatron.isa.llm.type.openai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.TEXT;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.AI_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.TOOL_RESULT_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.USER_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The OpenAI transport's harness context — projecting a request's {@code tools[]} and
 * {@code messages[]} onto metatron objs.  Exercises {@link ExternalTool} (the schema
 * projection) and {@link ExternalHarnessContext} (messages/system/prompt/tools).
 */
public class ExternalHarnessContextTest extends AbstractMetatronTest {

    private static final String TOOL_JSON = """
            {"type":"function","function":{"name":"taskboard_list","description":"list tasks","parameters":{"type":"object","properties":{"workspaceId":{"type":"string","description":"the project id"},"limit":{"type":"integer"}},"required":["workspaceId"]}}}
            """;

    private static final String REQUEST_JSON = """
            {"model":"dr.stynx","messages":[
              {"role":"system","content":"you are a test agent"},
              {"role":"user","content":"call taskboard_list"}
            ],"tools":[%s]}
            """.formatted(TOOL_JSON);

    private static Obj parse(final String json) {
        return ObjJSONSerializer.web().readString(json);
    }

    @Test
    public void testToolSchemaProjection() {
        final ExternalTool tool = ExternalTool.from(parse(TOOL_JSON));
        assertNotNull(tool, "a valid function definition must project to an ExternalTool");
        final Tuple.Pair<ToolSpecification, ToolExecutor> pair = tool.toolSpecification();
        final ToolSpecification spec = pair.get0();
        assertEquals("taskboard_list", spec.name());
        assertEquals("list tasks", spec.description());
        final JsonObjectSchema params = spec.parameters();
        assertNotNull(params, "parameters must be a JsonObjectSchema");
        assertTrue(params.properties().containsKey("workspaceId"), "workspaceId missing; properties=" + params.properties().keySet());
        assertTrue(params.properties().containsKey("limit"), "limit missing; properties=" + params.properties().keySet());
        assertEquals(2, params.properties().size(), "expected exactly the two declared properties");
        assertTrue(params.required().contains("workspaceId"), "workspaceId must be required; required=" + params.required());
        assertFalse(params.required().contains("limit"), "limit must not be required; required=" + params.required());
    }

    @Test
    public void testContextProjection() {
        final ExternalHarnessContext context = ExternalHarnessContext.of(parse(REQUEST_JSON).asRec());
        // system message projected onto the system message tid
        final Rec system = context.systemMessage();
        assertNotNull(system, "the system message must project");
        assertEquals("you are a test agent", system.at(uri(TEXT)).toCleanString());
        // the last user message is the prompt
        assertEquals("call taskboard_list", context.currentPrompt());
        // messages: system + user
        assertEquals(2, context.getMessages().size());
        // tools: one harness tool
        assertEquals(1, context.getTools().size());
    }

    @Test
    public void testNullForNonFunctionElement() {
        assertNull(ExternalTool.from(parse("{\"type\":\"not_a_function\",\"function\":{}}")), "a non-function element must not project");
        assertNull(ExternalTool.from(parse("{\"type\":\"function\",\"function\":{}}")), "a function without a name must not project");
    }

    @Test
    public void testContextToolSchemaProjection() {
        final ExternalHarnessContext context = ExternalHarnessContext.of(parse(REQUEST_JSON).asRec());
        final mTool tool = context.getTools().get(0);
        assertTrue(tool instanceof ExternalTool, "the harness tool must project to an ExternalTool");
        final JsonObjectSchema params = ((ExternalTool) tool).toolSpecification().get0().parameters();
        assertTrue(params.properties().containsKey("workspaceId"), "context tool schema properties=" + params.properties().keySet());
        assertTrue(params.required().contains("workspaceId"), "context tool schema required=" + params.required());
    }

    @Test
    public void testMessageTidProjection() {
        final String req = """
                {"model":"dr.stynx","messages":[
                  {"role":"system","content":"sys"},
                  {"role":"user","content":"hi"},
                  {"role":"assistant","content":"hello"},
                  {"role":"tool","tool_call_id":"c1","name":"taskboard_list","content":"result"}
                ],"tools":[]}
                """;
        final List<Rec> messages = ExternalHarnessContext.of(parse(req).asRec()).getMessages();
        assertEquals(4, messages.size(), "every message projects onto a metatron message tid");
        assertEquals(SYSTEM_MESSAGE_TID, messages.get(0).tid(), "system → system message tid");
        assertEquals(USER_MESSAGE_TID, messages.get(1).tid(), "user → user message tid");
        assertEquals(AI_MESSAGE_TID, messages.get(2).tid(), "assistant → ai message tid");
        assertEquals(TOOL_RESULT_MESSAGE_TID, messages.get(3).tid(), "tool → tool-result message tid");
    }

    @Test
    public void testToolNameInJvm() {
        final ExternalTool tool = ExternalTool.from(parse(TOOL_JSON));
        assertEquals("taskboard_list", tool.name().toString(), "the tool name must be in the jvm so the registry upserts by name");
    }
}
