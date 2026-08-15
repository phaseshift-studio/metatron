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
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.skills.DefaultSkill;
import dev.langchain4j.skills.DefaultSkillResource;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skill;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import studio.phaseshift.metatron.TokenMapper;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.util.Tuple;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mSkill extends MRec {

    /**
     * Metatron token → Anthropic MCP/skill vocabulary.  Metatron keeps a
     * minimal token set (e.g. {@code desc}); this maps it onto the Anthropic
     * names at the protocol boundary (e.g. {@code description}).
     */
    public static final TokenMapper ANTHROPIC_VOCAB = new TokenMapper()
            .add(LLM_SKILL_TID, DESC, "description");

    private final Skill skill;

    public mSkill(final Skill skill, final fURI tid, final fURI vid) {
        super(mutableMap(
                uri(NAME), uri(skill.name()),
                uri(DESC), str(skill.description()),
                uri(CONTENT), str(skill.content()),
                uri(RESOURCE), skill.resources().isEmpty() ? noobj() : lst(skill.resources().stream().map(r -> rec(uri(URI), uri(r.relativePath()), uri(TEXT), str(r.content())).<Obj>as()).toList())), tid, vid);
        this.skill = skill;
    }

    public mSkill(final Rec skillRec, final fURI tid, final fURI vid) {
        super(skillRec.jvm(), tid, vid);
        this.skill = null;
    }

    public Skill toSkill() {
        DefaultSkill.Builder skill = new DefaultSkill.Builder();
        if (this.has(NAME))
            skill = skill.name(this.at(NAME).uriValue().toString());
        if (this.has(DESC))
            skill = skill.description(this.at(DESC).strValue());
        if (this.has(CONTENT))
            skill = skill.content(this.at(CONTENT).strValue());
        if (this.has(RESOURCE))
            skill = skill.resources(this.at(RESOURCE).asLst().elements()
                    .map(Obj::asRec)
                    .map(e -> new DefaultSkillResource.Builder()
                            .relativePath(e.at(URI).uriValue().toString())
                            .content(e.at(TEXT).strValue())
                            .build())
                    .toList());
        if (this.has(TOOL)) {
            final Map<ToolSpecification, ToolExecutor> tools = this.at(TOOL).asLst().elements().map(i -> mTool.mtronInstToTool(i.asInst())).map(mTool::mtronInstToolSpecification).collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1));
            skill = skill.tools(tools);
        }
        if (null != this.skill && !this.skill.toolProviders().isEmpty())
            skill = skill.toolProviders();
        return skill.build();
    }

    public static mSkill of(final File skillDir) {
        return new mSkill(FileSystemSkillLoader.loadSkill(Path.of(skillDir.getAbsolutePath())), LLM_SKILL_TID, null);
    }

    public static mSkill of(final Rec skillRec) {
        return new mSkill(skillRec, LLM_SKILL_TID, skillRec.vid());
    }

    public static mSkill of(final Str skillStr) {
        final Map<String, List<String>> frontMatter = parseFrontMatter(skillStr.strValue());
        skillStr.logger().info("front matter loaded: %s", frontMatter);
        return new mSkill(Skill.builder()
                .name(frontMatter.get("name").getFirst())
                .description(frontMatter.get("description").getFirst())
                .content(extractContent(skillStr.strValue())).build(), LLM_SKILL_TID, null);
    }

    static final Parser PARSER = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .build();

    public static Map<String, List<String>> parseFrontMatter(final String markdown) {
        Node document = PARSER.parse(markdown);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        return visitor.getData();
    }

    static String extractContent(String markdown) {
        if (markdown.startsWith("---")) {
            int secondDelimiter = markdown.indexOf("\n---", 3);
            if (secondDelimiter != -1) {
                return markdown.substring(secondDelimiter + 4).trim();
            }
        }
        return markdown;
    }

    /**
     * Resolve the skills attached to every feature of an agent into a flat
     * {@code Lst<mSkill>}.  Each feature exposes skills either as mtron-native
     * recs ({@link Feature#skill(Agent)}) or as a {@code skill} field of URIs/
     * recs; a URI element is loaded from its SKILL.md directory.
     *
     * @param agent the agent whose features are traversed
     * @return a flat list of resolved skills
     */
    public static Lst skills(final Agent agent) {
        return lst(agent.features().elements()
                .flatMap(entry -> (entry instanceof Feature feat ? feat.skill(agent) : entry.asRec().at(SKILL).orElse(lst()))
                        .elements()
                        .map(s -> (Obj) (s.isUri() ? mSkill.of(fsSpace.staticObjToFile(s)) : mSkill.of(s.apply().asRec()))))
                .toList());
    }

    /**
     * The {@code as?skill<=agent} mapping: aggregate an agent's features' skills
     * into a single {@code skill::T}.  The agent's name/desc become the skill's
     * name/desc; the features' tools and resources are flattened into the
     * skill's {@code tool} and {@code resource} fields (absent when empty).
     *
     * @param agent the agent to reduce to a skill
     * @return a skill aggregating the agent's capabilities, vid-null
     */
    public static mSkill agentToSkill(final Agent agent) {
        final String nameStr = Str.Helper.cleanString(agent.at(uri(NAME)));
        final Obj desc = agent.at(uri(DESC));
        final List<Obj> tools = new ArrayList<>();
        final List<Obj> resources = new ArrayList<>();
        for (final Obj entry : agent.features().elements().toList()) {
            final Lst featureSkills = entry instanceof Feature feat ? feat.skill(agent) : entry.asRec().at(SKILL).orElse(lst());
            for (final Obj s : featureSkills.elements().toList()) {
                final mSkill skill = (s.isUri() ? mSkill.of(fsSpace.staticObjToFile(s)) : mSkill.of(s.apply().asRec()));
                tools.addAll(skill.tools().elements().toList());
                if (skill.has(RESOURCE))
                    resources.addAll(skill.at(RESOURCE).asLst().elements().toList());
                // each feature's own skill (its usage instructions) becomes a resource
                if (skill.has(CONTENT)) {
                    final Map<Obj, Obj> r = mutableMap(
                            uri(URI), uri(entry.tid()),
                            uri(NAME), str(entry.tid().name()));
                    if (skill.has(DESC))
                        r.put(uri(DESC), skill.at(uri(DESC)));
                    r.put(uri(TEXT), skill.at(uri(CONTENT)));
                    resources.add(rec(r));
                }
            }
        }
        final Map<Obj, Obj> jvm = mutableMap(
                uri(NAME), uri(nameStr),
                uri(DESC), desc.isNoObj() ? str("an agent named " + nameStr) : desc);
        if (!tools.isEmpty())
            jvm.put(uri(TOOL), lst(tools));
        if (!resources.isEmpty())
            jvm.put(uri(RESOURCE), lst(resources));
        return new mSkill(rec(jvm), LLM_SKILL_TID, null);
    }

    /**
     * The tools carried by this skill, as a {@code Lst} of tool insts.  Combines
     * the mtron-native {@code tool} field with any {@link ToolProvider} tools the
     * underlying LC4j {@link Skill} exposes (each folded into an inst via
     * {@link mTool#toolToMtronDoc}).
     *
     * @return the flattened tool insts
     */
    public Lst tools() {
        final List<Obj> tools = new ArrayList<>();
        if (this.has(TOOL))
            tools.addAll(this.at(TOOL).asLst().elements().toList());
        if (null != this.skill) {
            for (final ToolProvider provider : this.skill.toolProviders()) {
                try {
                    provider.provideTools(ToolProviderRequest.builder().build()).tools()
                            .forEach((spec, executor) -> tools.add(mTool.toolToMtronDoc(spec, executor).at(OBJ)));
                } catch (final Exception e) {
                    this.logger().warn("unable to fold tool provider %s: %s", provider, e.getMessage());
                }
            }
        }
        return lst(tools);
    }
}
