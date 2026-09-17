package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SkillService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SystemService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.ToolService;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.SKILL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrapDocs;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The gateway of the agent's skill channel.
 *
 * <p>One owner per channel: {@code SkillFeature} owns the
 * {@code Collection<mSkill>} of skills (markdown content);
 * {@link ToolFeature} owns the {@code Collection<mTool>} of inst
 * registrations.  Contributors publish by calling
 * {@link #addSkill(mSkill)}.</p>
 *
 * <p>A skill may carry tools in its {@code tool} field.  This gateway is the
 * single composition point for that coupling: on each chat it forwards every
 * registered skill's tools to the {@code ToolFeature} gateway, which
 * registers and projects them — skill content stays here, inst registration
 * stays there.</p>
 */
public class SkillFeature extends AbstractFeature implements SkillService {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;

    @Override
    public Set<fURI> offers() {
        return Set.of(LLM_SKILL_SERVICE_TID);
    }


    /**
     * The registered skills — mSkill elements, upserted by name.
     */
    private final Map<fURI, mSkill> skillRegistry = new LinkedHashMap<>();

    public SkillFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_TOOL_SERVICE_TID);
    }

    @Override
    public Set<fURI> uses() {
        // the skill table it emits rides the system-message channel; without it, skills are
        // still forwarded as tools (degraded — no "activate_skill" prompt)
        return Set.of(LLM_SYSTEM_SERVICE_TID);
    }

    /**
     * Register a skill with this feature — the single entry point to the
     * agent's skill registry.  Upsert semantics: re-registering under the
     * same name replaces the earlier entry, so per-chat registration is
     * idempotent.
     *
     * @param skill the skill to register
     */
    public void addSkill(final mSkill skill) {
        final Obj name = skill.at(uri(NAME));
        final fURI key = name.isNoObj() ? skill.tid().extend(name.toCleanString()) : name.uriValue();
        this.skillRegistry.put(key, skill);
    }

    /**
     * The skills currently registered with this feature.
     *
     * @return the registered skills
     */
    public Lst skills() {
        return lst(new ArrayList<>(this.skillRegistry.values()));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        agent.requireService(ToolService.class).addTool(mTool.tool(docWrapDocs(instC(f("list_skills").dom(NOOBJ.zero()).rng(LST_TID), lst(),
                        (lhs, inst) -> lst(this.skillRegistry.values().stream().map(mSkill::toSkill).toList().stream().map(s -> (Obj) lst(str(s.name()), str(s.description()))).toList())),
                "no domain",
                "a lst[lst[str,str]] of skills",
                Map.of(),
                "generates a lst of available skills by name and description")));

        // --- 0. load explicit skills specified in skill feature definition (skill registry still might have some)
        this.at(SKILL).elements().forEach(s -> {
            try {
                this.addSkill(s.isUri() ? mSkill.of(fsSpace.staticObjToFile(s)) : mSkill.of(s.asRec()));
            } catch (final Exception e) {
                this.logger().warn("unable to seed skill %s (ignoring): %s", s, e.getMessage());
            }
        });

        // ── 1. forward each skill's tools to the tool gateway (the single composition point) ──
        if (agent.hasFeature(LLM_TOOL_FEATURE_TID)) {
            final ToolService toolFeature = agent.requireService(ToolService.class);
            this.skillRegistry.values().forEach(skill -> skill.tools().elements().forEach(t -> {
                try {
                    toolFeature.addTool(mTool.tool(t));
                } catch (final Exception e) {
                    this.logger().warn("unable to forward tool %s of skill %s to tool feature: %s", t, skill, e.getMessage());
                }
            }));
        }
        // ── 2. project the registry to LC4j skills (the markdown channel) ──
        final List<Skill> allSkills = this.skillRegistry.values().stream().map(mSkill::toSkill).toList();
        if (allSkills.isEmpty())
            return noobj();

        try {
            final Skills skills = new Skills.Builder().skills(allSkills).build();
            agent.requireService(ToolService.class).addToolProvider(skills.toolProvider());
            // SystemFeature owns the system-message channel (a soft `uses` collaboration —
            // absent means no skill table prompt, but skills are still forwarded as tools).
            if (agent.hasFeature(SystemFeature.class)) {
                try (final TableWidget table = new TableWidget(List.of("name", "description")).style().border(Border.continuous).applyStyle()) {
                    allSkills.forEach(s -> table.addRow(List.of(s.name(), s.description())));
                    agent.requireService(SystemService.class)
                            .addSystemMessage("""
                                              ----
                                              use activate_skill() tool to load any of the following skills:
                                              %s
                                              """.formatted(Graphitty.strip(table.format())));
                }
            }
        } catch (final Exception e) {
            throw MTronException.of("unable to setup skills: %s", e);
        }
        return noobj();
    }
}
