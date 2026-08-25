package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.skills.Skill;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractFeature;
import studio.phaseshift.metatron.isa.llm.type.feature.IterationFeature;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tests for the {@code skill::T} type ({@link mSkill}) — construction from a
 * rec, markdown with YAML front matter, and a SKILL.md directory; the
 * mtron↔LC4j round-trip ({@link mSkill#toSkill()}) including resources and
 * tools; the parsers ({@link mSkill#parseFrontMatter}, content extraction);
 * tool folding ({@link mSkill#tools()}); the {@code agent => skill} mapping
 * ({@link mSkill#agentToSkill}); and the Anthropic vocabulary.
 *
 * <p>Cross-feature skill aggregation (across an agent's features) lives in
 * {@code SkillFeatureTest}.
 */
public class mSkillTest extends studio.phaseshift.metatron.AbstractMetatronTest {

    private static final String FRONT_MATTER_SKILL = """
                                                     ---
                                                     name: my_skill
                                                     description: a skill
                                                     ---
                                                     skill content here
                                                     """;

    private static Agent agent(final String name, final String desc, final AbstractFeature... features) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str(name));
        if (null != desc)
            map.put(uri(DESC), str(desc));
        final List<Obj> objs = new ArrayList<>();
        Collections.addAll(objs, features);
        if (!objs.isEmpty())
            map.put(uri(FEATURE), lst(objs));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }

    // ── Construction ───────────────────────────────────────────────

    @Test
    public void testOfRecPreservesFieldsAndVid() {
        final Rec src = rec(uri(NAME), uri("my_skill"), uri(DESC), str("a desc"), uri(CONTENT), str("content"));
        final mSkill skill = mSkill.of(src);
        assertEquals("my_skill", skill.at(uri(NAME)).uriValue().name(), "name from the rec");
        assertEquals("a desc", skill.at(uri(DESC)).strValue(), "desc from the rec");
        assertEquals("content", skill.at(uri(CONTENT)).strValue(), "content from the rec");
        assertEquals(src.vid(), skill.vid(), "rec vid is preserved");
    }

    @Test
    public void testOfMarkdownWithFrontMatter() {
        final mSkill skill = mSkill.of(str(FRONT_MATTER_SKILL));
        assertEquals("my_skill", skill.at(uri(NAME)).uriValue().name(), "name from front matter");
        assertEquals("a skill", skill.at(uri(DESC)).strValue(), "description from front matter");
        assertEquals("skill content here", skill.at(uri(CONTENT)).strValue(), "content is the markdown body");
    }

    @Test
    public void testOfMarkdownWithoutFrontMatter() {
        final mSkill skill = mSkill.of(str("just plain markdown"));
        assertEquals("unnamed", skill.at(uri(NAME)).uriValue().name(), "name defaults when absent");
        assertEquals("no description", skill.at(uri(DESC)).strValue(), "description defaults when absent");
        assertEquals("just plain markdown", skill.at(uri(CONTENT)).strValue(), "content is the whole markdown");
    }

    @Test
    public void testOfFromSkillDir() throws Exception {
        final Path dir = Files.createTempDirectory("mSkillTest");
        Files.writeString(dir.resolve("SKILL.md"), FRONT_MATTER_SKILL);
        final mSkill skill = mSkill.of(dir.toFile());
        assertFalse(skill.at(uri(NAME)).uriValue().toString().isBlank(), "name loads from SKILL.md");
        assertEquals("a skill", skill.at(uri(DESC)).strValue(), "description loads from front matter");
        assertTrue(skill.at(uri(CONTENT)).strValue().contains("skill content here"), "content loads from SKILL.md");
    }

    // ── Parsers ────────────────────────────────────────────────────

    @Test
    public void testParseFrontMatter() {
        final Map<String, List<String>> fm = mSkill.parseFrontMatter(FRONT_MATTER_SKILL);
        assertEquals("my_skill", fm.get("name").getFirst());
        assertEquals("a skill", fm.get("description").getFirst());
        assertTrue(mSkill.parseFrontMatter("no front matter").isEmpty(), "no front matter → empty map");
    }

    @Test
    public void testExtractContent() {
        assertEquals("body", mSkill.extractContent("---\nname: x\n---\nbody"), "strips the front-matter block");
        assertEquals("plain", mSkill.extractContent("plain"), "no front matter → unchanged");
    }

    // ── mtron → LC4j round-trip ────────────────────────────────────

    @Test
    public void testToSkillRoundTrip() {
        final Skill lc4j = mSkill.of(str(FRONT_MATTER_SKILL)).toSkill();
        assertNotNull(lc4j, "must build an LC4j Skill");
        assertEquals("my_skill", lc4j.name(), "name round-trips");
        assertEquals("a skill", lc4j.description(), "description round-trips");
        assertEquals("skill content here", lc4j.content(), "content round-trips");
    }

    @Test
    public void testToSkillWithResources() {
        final mSkill skill = mSkill.of(rec(
                uri(NAME), uri("res_skill"),
                uri(DESC), str("a resource skill"),
                uri(CONTENT), str("content"),
                uri(RESOURCE), lst(rec(uri(URI), uri("refs/guide.md"), uri(TEXT), str("guide content")))));
        final Skill lc4j = skill.toSkill();
        assertEquals(1, lc4j.resources().size(), "resources map to DefaultSkillResources");
        assertEquals("refs/guide.md", lc4j.resources().getFirst().relativePath());
        assertEquals("guide content", lc4j.resources().getFirst().content());
    }

    @Test
    public void testToSkillWithTools() {
        final IterationFeature iteration = new IterationFeature(new LinkedHashMap<>(), feat("iteration"), null) {
        };
        final mSkill skill = mSkill.of(iteration.skill(agent("test-agent", null)).asLst().at(0).asRec());
        // The tool field folds prev + next as two tool insts; LC4j wraps them all
        // in a single ToolProvider, so assert at the mSkill level.
        assertEquals(2, skill.tools().elements().count(), "the tool field folds prev + next tools");
        final Skill lc4j = skill.toSkill();
        assertNotNull(lc4j, "must build an LC4j Skill");
    }

    // ── tools() ────────────────────────────────────────────────────

    @Test
    public void testToolsFromToolField() {
        final IterationFeature iteration = new IterationFeature(new LinkedHashMap<>(), feat("iteration"), null) {
        };
        final mSkill skill = mSkill.of(iteration.skill(agent("test-agent", null)).asLst().at(0).asRec());
        assertEquals(2, skill.tools().elements().count(), "tools() flattens the tool field");
    }

    // ── agent => skill mapping (the mSkill contract) ───────────────

    @Test
    public void testAgentToSkillNameAndDesc() {
        final mSkill skill = mSkill.agentToSkill(agent("test-agent", "a test agent"));
        assertEquals("test-agent", skill.at(uri(NAME)).uriValue().name(), "skill name is the agent name");
        assertEquals("a test agent", skill.at(uri(DESC)).strValue(), "skill desc is the agent desc");
        assertTrue(skill.at(uri(TOOL)).isNoObj(), "no features → no tools");
        assertTrue(skill.at(uri(RESOURCE)).isNoObj(), "no features → no resources");
        assertNull(skill.vid(), "derived skill has a null vid");
    }

    @Test
    public void testAgentToSkillDefaultDesc() {
        final mSkill skill = mSkill.agentToSkill(agent("test-agent", null));
        assertEquals("an agent named test-agent", skill.at(uri(DESC)).strValue(), "missing desc falls back to a default");
    }

    // ── Anthropic vocabulary ───────────────────────────────────────

    @Test
    public void testAnthropicVocab() {
        assertEquals("description", mSkill.ANTHROPIC_VOCAB.to(LLM_SKILL_TID, DESC), "desc maps to description");
        assertEquals("name", mSkill.ANTHROPIC_VOCAB.to(LLM_SKILL_TID, NAME), "unmapped tokens fall back to identity");
    }
}
