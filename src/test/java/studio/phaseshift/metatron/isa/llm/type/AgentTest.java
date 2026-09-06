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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.SkipWhenPortUnavailable;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.tble.tbleSpace;

import java.io.BufferedInputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@SkipWhenPortUnavailable(value = 11434)
@Isolated
public class AgentTest extends AbstractMetatronTest {

    private static final String MODEL_NAME = "qwen3:8b";
    private static final String PROVIDER_NAME = "ollama";
    private static final String PROVIDER_HOST = "http://localhost:11434";

    private Rec fixture;
    private Agent agent;

    @BeforeEach
    public void setup() {
        InstSet.importInstSet(LLM_ISA_TID);
        fixture = buildFixture();
        agent = Agent.agent(fixture);
    }

    private static Rec buildFixture() {
        Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str(MODEL_NAME));
        final mModel model = mModel.model(rec(
                NAME, uri(MODEL_NAME),
                PROVIDER, uri("ollama"),
                PROTOCOL, uri("ollama"),
                HOST, uri(PROVIDER_HOST),
                LLM, uri(MODEL_NAME)));
        map.put(uri(FEATURE), lst(
                rec(mutableMap(uri(THINK), rec())).tid(LLM_THINK_FEATURE_TID),
                rec(mutableMap(uri(TOOL), lst(uri("/test/tool")))).tid(LLM_TOOL_FEATURE_TID),
                rec(mutableMap(uri(SKILL), lst(str("a-skill")))).tid(LLM_SKILL_FEATURE_TID),
                rec(mutableMap(uri(NOTE), lst(str("a-note")))).tid(LLM_NOTE_FEATURE_TID),
                rec(mutableMap(uri(CHAT), str("you are helpful"), uri(MODEL), model)).tid(LLM_CHAT_FEATURE_TID),
                rec(mutableMap(
                        uri(PATTERN), uri("/sys/docs/#"),
                        uri(MAX), jnt(5)
                )).tid(LLM_FEATURE_TID.extend("rag")),
                rec(mutableMap(
                        uri(SESSION), uri("/usr/test/1"),
                        uri(MEMORY), rec(mutableMap(
                                uri("mem"), lst(),
                                uri(ALGORITHM), rec(mutableMap(
                                        uri(MAX), jnt(15)
                                ))
                        )))).tid(LLM_MESSAGE_FEATURE_TID),
                rec(mutableMap(uri(MODEL), rec(mutableMap(
                        uri(LLM), uri("qwen3:8b"),
                        uri(PROTOCOL), uri(PROVIDER_NAME),
                        uri(HOST), uri(PROVIDER_HOST)), LLM_MODEL_TID, null))).tid(LLM_CHAT_FEATURE_TID)
        ));
        return rec(map, LLM_AGENT_TID, null);
    }

    // === mModel accessor tests ===

    @Test
    public void testModelFactory() {
        assertNotNull(agent);
        assertEquals(LLM_AGENT_TID, agent.tid());
        assertEquals(fixture.vid(), agent.vid());
    }

    @Test
    public void testFeaturePresent() {
        assertFalse(agent.feature(LLM_THINK_FEATURE_TID).isNoObj());
        assertFalse(agent.feature(LLM_TOOL_FEATURE_TID).isNoObj());
        assertFalse(agent.feature(LLM_SKILL_FEATURE_TID).isNoObj());
        assertFalse(agent.feature(LLM_NOTE_FEATURE_TID).isNoObj());
        assertFalse(agent.feature(LLM_CHAT_FEATURE_TID).isNoObj());
        assertFalse(agent.feature(LLM_MESSAGE_FEATURE_TID).isNoObj());
    }

    @Test
    public void testFeatureAbsent() {
        assertTrue(agent.feature(LLM_FEATURE_TID.extend("nonexistent")).isNoObj());
    }

    @Test
    public void testSkills() {
        assertFalse(agent.feature(LLM_SKILL_FEATURE_TID).isNoObj());
        LOG.debug("skills: %s", agent.feature(LLM_SKILL_FEATURE_TID));
        assertEquals(2, agent.feature(LLM_SKILL_FEATURE_TID).elements().count());
        assertEquals(1, agent.feature(LLM_SKILL_FEATURE_TID).asRec().at(SKILL).asLst().elements().count());
    }

    @Test
    public void testFeatures() {
        Lst features = agent.features();
        assertFalse(features.isNoObj());
        // features is a Lst of Feature instances
        assertFalse(features.isEmpty());
    }

    // ========================================================================
    //  Lifecycle hook dispatch tests
    // ========================================================================

    /**
     * Fixture: agent with a single ObservedTestFeature — no LLM needed.
     */
    private Agent agentWithObserver() {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("observer-agent"));
        map.put(uri(FEATURE), lst(ObservedTestFeature.observe("test-observer")));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }

    @Test
    public void testHookWiring() {
        final Agent a = agentWithObserver();
        final List<Obj> features = a.features().lstValue();
        assertEquals(1, features.size());

        // Manually dispatch onBeforeChat via JVM key — no chat() needed
        final Obj f = features.getFirst();
        assertTrue(f instanceof Feature, "observed feature should be a Feature instance: %s".formatted(f));

        final Obj hook = ((Poly) f).at(uri(ON_BEFORE_CHAT));
        assertFalse(hook.isNoObj(), "onBeforeChat hook should be registered: %s".formatted(f));

        final Obj result = hook.apply(a);
        assertTrue(result.isNoObj(), "observer onBeforeChat should return noobj (no short-circuit)");

        // Verify audit trail
        final List<Rec> trail = ObservedTestFeature.auditTrail(a);
        assertEquals(1, trail.size());
        assertEquals("onBeforeChat", trail.getFirst().at(uri("phase")).strValue());
    }

    @Test
    public void testHookDispatchOrder() {
        final Agent a = agentWithObserver();
        final List<Obj> features = a.features().lstValue();
        final Obj f = features.getFirst();

        // Dispatch hooks in the Agent's normal order
        ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);
        ((Inst) ((Poly) f).at(uri(ON_PARTIAL_RESPONSE))).args(lst(str("hello"))).apply(a);
        ((Inst) ((Poly) f).at(uri(ON_PARTIAL_THINKING))).args(lst(str("hmm"))).apply(a);
        ((Inst) ((Poly) f).at(uri(ON_TOOL_EXECUTED))).args(lst(rec())).apply(a);
        ((Inst) ((Poly) f).at(uri(ON_COMPLETE_RESPONSE))).args(lst(str("done"))).apply(a);
        ((Inst) ((Poly) f).at(uri("onError"))).args(lst(noobj())).apply(a);

        final List<Rec> trail = ObservedTestFeature.auditTrail(a);
        assertEquals(6, trail.size());
        assertEquals("onBeforeChat", trail.get(0).at(uri("phase")).strValue());
        assertEquals("onPartialResponse", trail.get(1).at(uri("phase")).strValue());
        assertEquals("onPartialThinking", trail.get(2).at(uri("phase")).strValue());
        assertEquals("onToolExecuted", trail.get(3).at(uri("phase")).strValue());
        assertEquals("onCompleteResponse", trail.get(4).at(uri("phase")).strValue());
        assertEquals("onError", trail.get(5).at(uri("phase")).strValue());
    }

    @Test
    public void testHookArgsPropagation() {
        final Agent a = agentWithObserver();
        final List<Obj> features = a.features().lstValue();
        final Obj f = features.getFirst();

        // onPartialResponse receives the partial text as arg
        ((Inst) ((Poly) f).at(uri(ON_PARTIAL_RESPONSE))).args(lst(str("partial text"))).apply(a);

        final List<Rec> trail = ObservedTestFeature.auditTrail(a);
        assertEquals(1, trail.size());
        final Obj args = trail.getFirst().at(uri("args"));
        assertFalse(args.isNoObj());
        assertEquals("partial text", args.asLst().lstValue().getFirst().strValue());
    }

    @Test
    public void testShortCircuit() {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("short-circuit-agent"));

        // Feature that short-circuits on onBeforeChat
        final AbstractFeature blocker = new AbstractFeature(new LinkedHashMap<>(), feat("blocker"), null) {
        };
        blocker.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                str("blocked-by-test")), MUTABLE);
        map.put(uri(FEATURE), lst(blocker));
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        // Simulate the Agent's onBeforeChat loop
        final List<Obj> features = a.features().lstValue();
        Obj shortCircuit = noobj();
        for (final Obj f : features) {
            final Obj result = ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);
            if (!result.isNoObj()) {
                shortCircuit = result;
                break;
            }
        }
        assertFalse(shortCircuit.isNoObj());
        assertEquals("blocked-by-test", shortCircuit.strValue());
    }

    @Test
    public void testMissingHookIsSilentNoop() {
        // A plain Feature with no hooks registered — dispatch should be a noop chain
        final AbstractFeature empty = new AbstractFeature(new LinkedHashMap<>(), feat("empty"), null) {
        };

        // noobj chain: at(key) → noobj().args(lst(...)) → noobj().apply(agent) → noobj
        final Obj hook = ((Poly) empty).at(uri(ON_BEFORE_CHAT));
        assertTrue(hook.isNoObj());
        final Obj result = ((Inst) hook).args(lst()).apply(noobj());
        assertTrue(result.isNoObj(), "noobj().args(lst()).apply(x) should be noobj");
    }

    @Test
    public void testMultipleFeaturesAllGetDispatched() {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("multi-observer-agent"));
        map.put(uri(FEATURE), lst(
                ObservedTestFeature.observe("obs-1"),
                ObservedTestFeature.observe("obs-2")
        ));
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        // Dispatch onBeforeChat — both features should fire
        for (final Obj f : a.features().lstValue())
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);

        // Each observer wrote its own audit entry
        final List<Rec> trail = ObservedTestFeature.auditTrail(a);
        assertEquals(2, trail.size());
        assertEquals("onBeforeChat", trail.get(0).at(uri("phase")).strValue());
        assertEquals("onBeforeChat", trail.get(1).at(uri("phase")).strValue());
    }

    @Test
    @Disabled("requires mock StreamingChatModel — proves the full pipeline pattern end-to-end")
    public void testFullChatLifecycleWithMockLLM() {
        // Pattern for when a mock LLM is available:
        //
        // 1. Register ObservedTestFeature + real features in agent
        // 2. Inject a mock StreamingChatModel that fires:
        //      onPartialResponse("Hello") → onPartialResponse("World") → onCompleteResponse("HelloWorld")
        // 3. Call agent.chat("test message")
        // 4. Assert result Rec has: chat=>"HelloWorld", time=>non-noobj
        // 5. Assert audit trail has: [
        //      onBeforeChat,
        //      onPartialResponse("Hello"), onPartialResponse("World"),
        //      onCompleteResponse("HelloWorld")
        //    ]
    }

    // ========================================================================
    //  System-message channel (single channel: addSystemMessage → transformer)
    // ========================================================================

    /**
     * The single system-message channel: SystemFeature owns the {@code systemMessages}
     * list; features communicate cross-feature via {@code agent.feature(SYSTEM)}.
     * No LLM needed — verify the field contract directly.
     */
    @Test
    public void testSystemMessageChannelAppends() {
        final SystemFeature system = new SystemFeature(mutableMap(), LLM_SYSTEM_FEATURE_TID, null);
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("system-channel-agent"));
        map.put(uri(FEATURE), lst(system));
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        final SystemFeature sf = a.feature(LLM_SYSTEM_FEATURE_TID).<SystemFeature>as();
        assertTrue(sf.getSystemMessages().isEmpty(), "fresh agent should have no system messages");

        sf.addSystemMessage("first");
        sf.addSystemMessage("second");

        assertEquals(2, sf.getSystemMessages().size());
        assertEquals("first", sf.getSystemMessages().get(0));
        assertEquals("second", sf.getSystemMessages().get(1));

        // The transformer appends these to the model's base prompt — the join is
        // exactly what Agent.chat() feeds to systemMessageTransformer.
        assertTrue(sf.systemMessage().contains("first\nsecond"));
    }

    /**
     * SystemFeature's onBeforeChat must be a pure no-op — it no longer writes a
     * SYSTEM_MESSAGE_TID into the message ledger (Channel B is removed).  There is
     * exactly ONE channel: addSystemMessage → transformer.
     */
    @Test
    public void testSystemFeatureOnBeforeChatIsPureNoop() {
        final SystemFeature system = new SystemFeature(mutableMap(), LLM_SYSTEM_FEATURE_TID, null);
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("system-feature-agent"));
        map.put(uri(FEATURE), lst(system));
        map.put(uri(ROOT), f("/usr/test/system/").toUri());
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        // Dispatch onBeforeChat directly — must be a silent no-op
        final Obj result = system.onBeforeChat(a);
        assertTrue(result.isNoObj(), "SystemFeature.onBeforeChat should return noobj");

        // And it must NOT have added a system message (no Channel B / ledger write).
        // The single channel is addSystemMessage; if SystemFeature wrote one, the
        // feature's pending system messages would be non-empty after onBeforeChat.
        assertTrue(a.feature(LLM_SYSTEM_FEATURE_TID).<SystemFeature>as().getSystemMessages().isEmpty(),
                "SystemFeature.onBeforeChat must not add system messages (single channel only)");
    }

    /**
     * System messages are per-chat and ephemeral: cleared in chat()'s finally.
     * LLM-backed — a real chat must clear the accumulated list afterward.
     */
    @Test
    public void testSystemMessagesClearedAfterChat() {
        // Minimal chat agent — Chat + System features only (the full buildFixture's
        // skill/note features fail on toSkill; chat needs just these two).
        // Chat feature must be a plain rec with tid(LLM_CHAT_FEATURE_TID) — matching
        // buildFixture — so the FEATURE list type-checks.
        final Rec chat = rec(mutableMap(
                uri(MODEL), rec(mutableMap(
                        uri(LLM), uri("qwen3:8b"),
                        uri(PROTOCOL), uri("ollama"),
                        uri(HOST), uri(PROVIDER_HOST)), LLM_MODEL_TID, null))).tid(LLM_CHAT_FEATURE_TID);
        final SystemFeature system = new SystemFeature(mutableMap(), LLM_SYSTEM_FEATURE_TID, null);
        final ToolFeature tool = new ToolFeature(mutableMap(uri(TOOL), lst(uri("/test/tool"))), LLM_TOOL_FEATURE_TID, null);
        final SkillFeature skill = new SkillFeature(mutableMap(uri(SKILL), lst(str("a-skill"))), LLM_SKILL_FEATURE_TID, null);
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("system-clear-agent"));
        map.put(uri(DESC), str("test system message clearing"));
        map.put(uri(FEATURE), lst(system, chat, skill, tool));
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        final SystemFeature sf = a.feature(LLM_SYSTEM_FEATURE_TID).<SystemFeature>as();
        try {
            sf.addSystemMessage("You are a test agent.");
            assertFalse(sf.getSystemMessages().isEmpty(), "system message should be pending before chat");
            a.chat("Just say ok.");
        } catch (final Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused"))
                return; // Ollama not running — skip
            throw e;
        }
        assertTrue(sf.getSystemMessages().isEmpty(),
                "systemMessages must be cleared after chat() completes (ephemeral per-chat contract)");
    }

    /**
     * SystemFeature clears its contributions on onCompleteResponse and onError —
     * the per-chat lifecycle lives on the feature, not the Agent.
     */
    @Test
    public void testSystemFeatureClearsOnCompleteAndError() {
        final SystemFeature system = new SystemFeature(mutableMap(), LLM_SYSTEM_FEATURE_TID, null);
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("system-clear-hooks-agent"));
        map.put(uri(FEATURE), lst(system));
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        // onCompleteResponse clears
        system.addSystemMessage("context");
        assertFalse(system.getSystemMessages().isEmpty(), "message pending before completion");
        system.onCompleteResponse(a, ChatResult.chatResult());
        assertTrue(system.getSystemMessages().isEmpty(), "onCompleteResponse must clear system messages");

        // onError clears (safety — a failed chat must not leak context)
        system.addSystemMessage("context");
        assertFalse(system.getSystemMessages().isEmpty(), "message pending before error");
        system.onError(a, fail(new RuntimeException("test error")));
        assertTrue(system.getSystemMessages().isEmpty(), "onError must clear system messages");
    }

    @Test
    public void testChatResultShape() {
        // Agent.chat() builds a chat_result with monos inline (chat, user, time).
        final ChatResult result = ChatResult.chatResult()
                .put("chat", str("bare response"))
                .put("user", str("test prompt"))
                .put("time", real(100.0, MATH_MILLIS_TID, null));
        assertNotNull(result, "chat_result must be a rec");
        assertEquals(LLM_CHAT_RESULT_TID, result.tid(), "chat_result must have the chat_result tid");
        assertEquals("bare response", result.at(uri(CHAT)).strValue());
        assertEquals(100.0, result.at(uri(TIME)).realValue());
    }

    @Test
    public void testChatResultRefHelper() {
        // Feature outputs are attached as !* auto_from refs, not copied.
        final ChatResult result = ChatResult.chatResult()
                .put("chat", str("response"))
                .putRef("cost", f("/usr/test/cost/1"));
        // atDirect — at() would auto-resolve the ref; we want the raw inst.
        final Obj costRef = result.atDirect(uri("cost"));
        assertFalse(costRef.isNoObj(), "ref should be attached");
        assertTrue(costRef.isInst(), "ref should be an auto_from inst");
        assertEquals(f("/usr/test/cost/1"), Obj.Helper.getAutoPointer(costRef).orElse(null),
                "ref should target the persisted cost vid");
    }

    // ========================================================================
    //  SQLite-backed memory tests
    // ========================================================================

    private static final String TEST_DB_PATH = "target/test-llm-memory.db";
    private static final String MEM_TABLE = "llm_memory";
    private static final fURI MEM_VID = f("sqlite:" + MEM_TABLE + "/1");
    private tbleSpace memSpace;

    private void initSQLiteSession() throws Exception {
        final File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();
        dbFile.getParentFile().mkdirs();

        try (final Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB_PATH);
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE " + MEM_TABLE
                    + " (id INTEGER PRIMARY KEY, agent_id VARCHAR(255) NOT NULL,"
                    + " name VARCHAR(255) DEFAULT NULL,"
                    + " algorithm TEXT DEFAULT '{}')");
            stmt.executeUpdate("INSERT INTO " + MEM_TABLE
                    + " (id, agent_id, algorithm) VALUES (1, 'test-agent', '{}')");
        }

        InstSet.importInstSet(TBLE_ISA_TID);

        this.memSpace = tbleSpace.of(
                mutableMap(
                        uri(PATTERN), uri("sqlite:#"),
                        uri(HOST), uri("sqlite:" + TEST_DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(TABLE), lst(uri(MEM_TABLE)),
                        uri(ROUTE), rec(uri("sqlite:"), uri(""))
                ),
                f("/sys/space/test_llm_mem")
        );
    }

    @AfterEach
    public void teardownSQLite() {
        if (this.memSpace != null) {
            try {
                Router.global().removeSpace(this.memSpace.vid());
            } catch (Exception ignored) {
            }
            this.memSpace.close();
            this.memSpace = null;
        }
        final File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();
    }

    @Test
    public void testSQLiteSessionTypeDetection() throws Exception {
        initSQLiteSession();

        // Read the algorithm column — should be a Rec from JSON object default
        final Obj row = Router.readFromSpace(MEM_VID);
        assertFalse(row.isNoObj(), "row should exist");
        assertTrue(row.isRec(), "row should be Rec");

        final Obj algoField = row.asRec().at(uri(ALGORITHM));
        assertFalse(algoField.isNoObj(), "algorithm field should exist");
        assertTrue(algoField.isRec(),
                "algorithm should be Rec (JSON object detected), got: " + algoField.getClass().getSimpleName());
    }

    @Test
    public void testSQLiteSessionStoreAndRetrieve() throws Exception {
        initSQLiteSession();

        // Write a memory policy row with algorithm config
        final Rec memoryRec = (Rec) rec(
                uri("agent_id"), str("test-agent"),
                uri("name"), str("test-chat"),
                uri(ALGORITHM), rec(
                        uri(MAX), jnt(20),
                        uri("message_count"), jnt(3)
                )
        ).vid(MEM_VID);

        Router.writeToSpace(MEM_VID, memoryRec);

        // Read back — verify algorithm is a Rec (JSON detection working)
        final Obj readBack = Router.readFromSpace(MEM_VID);
        assertFalse(readBack.isNoObj());
        assertTrue(readBack.isRec());

        final Obj algoField = readBack.asRec().at(uri(ALGORITHM));
        assertTrue(algoField.isRec(),
                "algorithm should be Rec (JSON detected), got: " + algoField.getClass().getSimpleName());
        assertEquals(20L, algoField.asRec().at(uri(MAX)).intValue());
        assertEquals(3L, algoField.asRec().at(uri("message_count")).intValue());

        // Delete via noobj
        Router.writeToSpace(MEM_VID, noobj());
        assertTrue(Router.readFromSpace(MEM_VID).isNoObj());
    }

    // ========================================================================
    //  agent => skill (as?skill<=agent)
    // ========================================================================

    public static ToolFeature toolFeature() {
        return new ToolFeature(mutableMap(), LLM_TOOL_FEATURE_TID, null);
    }

    public static Inst findTool(final Agent agent, final Feature feature, final String toolNameRegEx) {
        feature.onBeforeChat(agent);
        return agent.feature(LLM_TOOL_FEATURE_TID).<ToolFeature>as()
                .tools()
                .elements()
                .map(t -> t.asRec().at(uri(INST)).<Obj>as())
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(i -> Pattern.compile(toolNameRegEx).matcher(i.tid().toString()).find())
                .findFirst()
                .orElseThrow(() -> new AssertionError("bash tool not registered by BashFeature.onBeforeChat"));
    }


    @Test
    public void testAgentAsSkill() {
        final Agent a = loadAgent();
        final mSkill skill = mSkill.agentToSkill(a);
        LOG.debug("agent converted to skill:\n%s", skill);
        assertTrue(skill.testNominally(LLM_SKILL_TYPE), "result should be a skill::T");
        assertEquals("test-agent", skill.at(uri(NAME)).uriValue().toString(), "skill name = agent name");
        assertEquals("a test agent for the agent-to-skill mapping", skill.at(uri(DESC)).strValue(), "skill desc = agent desc");
        assertNull(skill.vid(), "derived skill carries a null vid");
        // assertFalse(skill.at(uri(TOOL)).isNoObj(), "features' tools should be aggregated");
        //assertEquals(6, skill.at(uri(TOOL)).asLst().elements().count(), "prev/next + messages/concepts + check_comments + chat tools");
    }

    /**
     * Evaluate the mtron agent at {@code test-agent.mtron} (co-located with this
     * class) into an {@link Agent}.
     */
    private Agent loadAgent() {
        try (BufferedInputStream bi = new BufferedInputStream(Objects.requireNonNull(AgentTest.class.getResourceAsStream("test-agent.mtron")))) {
            final Obj agentObj = ObjmtronSerializer.single().inputBytes(bi.readAllBytes());
            assertNotNull(agentObj);
            LOG.warn("agent structure: %s", agentObj);
            assertTrue(agentObj.test(LLM_AGENT_TYPE));
            return Agent.agent(agentObj.asRec());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
