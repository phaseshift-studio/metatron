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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.SkipWhenPortUnavailable;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractFeature;
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

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@SkipWhenPortUnavailable(value = 11434)
public class AgentTest extends AbstractMetatronTest {

    private static final String MODEL_NAME = "test-model";
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
        map.put(uri(FEATURE), lst(
                rec(mutableMap(uri(THINK), rec())).tid(LLM_THINK_FEATURE_TID),
                rec(mutableMap(uri(TOOL), lst(uri("/test/tool")))).tid(LLM_TOOL_FEATURE_TID),
                rec(mutableMap(uri(SKILL), lst(str("a-skill")))).tid(LLM_SKILL_FEATURE_TID),
                rec(mutableMap(uri(NOTE), lst(str("a-note")))).tid(LLM_NOTE_FEATURE_TID),
                // rec(mutableMap(uri(CHAT), str("you are helpful"))).tid(LLM_CHAT_FEATURE_TID),
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
                        )))).tid(LLM_SESSION_FEATURE_TID),
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
        assertFalse(agent.feature(THINK).isNoObj());
        assertFalse(agent.feature(TOOL).isNoObj());
        assertFalse(agent.feature(SKILL).isNoObj());
        assertFalse(agent.feature(NOTE).isNoObj());
        assertFalse(agent.feature(CHAT).isNoObj());
        assertFalse(agent.feature(RAG).isNoObj());
        assertFalse(agent.feature(SESSION).isNoObj());
    }

    @Test
    public void testFeatureAbsent() {
        assertTrue(agent.feature("nonexistent").isNoObj());
    }

    @Test
    public void testSkills() {
        assertFalse(agent.feature(SKILL).isNoObj());
        assertEquals(2, agent.feature(SKILL).elements().count());
    }

    @Test
    public void testRag() {
        assertFalse(agent.feature(RAG).isNoObj());
        assertEquals(f("/sys/docs/#"), agent.feature(RAG).orElse(rec0()).at(PATTERN).uriValue());
        assertEquals(5, agent.feature(RAG).orElse(rec0()).at(MAX).intValue().intValue());
    }

    @Test
    public void testFeatures() {
        Lst features = agent.features();
        assertFalse(features.isNoObj());
        // features is a Lst of Feature instances
        assertFalse(features.isEmpty());
    }

    @Test
    public void testAddNote() {
        // Find the note feature and inspect its note list directly (no privileged addNote() on Agent)
        final Rec noteFeature = agent.feature(NOTE).orElse(rec0());
        final Obj notes = noteFeature.at(uri(NOTE));
        assertFalse(notes.isNoObj());
        assertEquals(1, notes.asLst().lstValue().size());
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

    @Test
    public void testTimeFieldInResultAssembly() {
        // Simulate what Agent.chat() does: write time to res("time"),
        // then read it back in Phase 4 result assembly.
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("time-test-agent"));
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        // Phase 3 (onCompleteResponse Lambda): write time to blackboard
        a.at(res("time"), jnt(1523), MUTABLE);

        // Verify time is readable from the blackboard
        final Obj timeFromBlackboard = a.at(res("time"));
        assertFalse(timeFromBlackboard.isNoObj(),
                "time should be stored at res(time), got noobj");

        // Phase 4: result assembly — must include time
        final Map<Obj, Obj> resultMap = new LinkedHashMap<>();
        resultMap.put(uri(CHAT), str("test chat"));
        resultMap.put(uri(TIME), a.at(res("time")));
        final Rec result = rec(resultMap);

        assertFalse(result.at(uri(TIME)).isNoObj(),
                "result should have a time field, got noobj");
    }

    @Test
    public void testResultBlackboardShapeWithoutFeatures() {
        // Bare agent with no features — result should still have chat, time, error
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("bare-agent"));
        final Agent a = Agent.agent(rec(map, LLM_AGENT_TID, null));

        a.at(res(CHAT), str("bare response"), MUTABLE);
        a.at(res(TIME), jnt(100), MUTABLE);
        a.at(res(ERROR), noobj(), MUTABLE);

        final Map<Obj, Obj> resultMap = new LinkedHashMap<>();
        resultMap.put(uri(CHAT), a.at(res(CHAT)));
        resultMap.put(uri(TIME), a.at(res(TIME)));
        resultMap.put(uri(ERROR), a.at(res(ERROR)));
        final Rec result = rec(resultMap);

        assertEquals("bare response", result.at(uri(CHAT)).strValue());
        assertEquals(100L, result.at(uri(TIME)).intValue());
        assertTrue(result.at(uri("audit")).isNoObj(), "no audit without AuditFeature");
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

    @Test
    public void testAgentAsSkill() {
        final Agent a = loadAgent();
        final mSkill skill = mSkill.agentToSkill(a);
        LOG.warn("agent converted to skill:\n%s", skill);
        assertTrue(skill.testNominally(LLM_SKILL_TYPE), "result should be a skill::T");
        assertEquals("test-agent", skill.at(uri(NAME)).uriValue().toString(), "skill name = agent name");
        assertEquals("a test agent for the agent-to-skill mapping", skill.at(uri(DESC)).strValue(), "skill desc = agent desc");
        assertNull(skill.vid(), "derived skill carries a null vid");
        assertFalse(skill.at(uri(TOOL)).isNoObj(), "features' tools should be aggregated");
        assertEquals(6, skill.at(uri(TOOL)).asLst().elements().count(), "prev/next + messages/concepts + check_comments + chat tools");
    }

    /**
     * Evaluate the mtron agent at {@code test-agent.mtron} (co-located with this
     * class) into an {@link Agent}.
     */
    private Agent loadAgent() {
        try (BufferedInputStream bi = new BufferedInputStream(Objects.requireNonNull(AgentTest.class.getResourceAsStream("test-agent.mtron")))) {
            final Obj agentObj = ObjmtronSerializer.single().inputBytes(bi.readAllBytes());
            assertNotNull(agentObj);
            assertTrue(agentObj.testNominally(LLM_AGENT_TYPE));
            return Agent.agent(agentObj.asRec());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
