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

package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.TextUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import static studio.phaseshift.metatron.Tokens.MODEL;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;

/**
 * Concept provider that asks a separate translator agent (LLM) to rewrite the text with
 * {@code <<concept:...>>} tags, then parses them.  Needs a model — without one it degrades
 * to no concepts.
 */
public final class AgentConceptFeature extends AbstractConceptFeature {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_CONCEPT_FEATURE_TID;

    private final mModel model;

    public AgentConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.model = this.has(MODEL) ? mModel.model(this.at(MODEL).asRec()) : null;
    }

    @Override
    protected Set<String> extract(final Agent agent, final String text, final boolean blocking) {
        if (null == this.model)
            return Set.of();
        final Set<String> conceptStrings = new LinkedHashSet<>();
        try {
            LOG.info("using agent to extract concepts from text length=%d", text.length());
            final CoreThread thread = CoreThread.core(instLambda((lhs, inst) -> {
                final Obj result = Agent.Helper.miniChat("concept_extractor",
                        this.model,
                        """
                        Rewrite the following text where key concepts are wrapped in <<concept:a key concept>> tags.
                        For instance, if the text is:
                           "An agent's context window can be indexed like a database."
                        It should be rewritten as:
                           "An agent's <<concept:context window>> can be <<concept:indexed>> like a <<concept:database>>.
                        
                        IMPORTANT:
                          1. Do not wrap common words nor stop words.
                          2. Do not remove spaces (e.g. context window should not be mapped to contextwindow).
                        Finally, it's better to have fewer, highly specific concepts then many general concepts.
                        Thus, if the text has no significant concepts, then simply return the text as is, no changes needed.
                        
                        The text to rewrite is:
                        
                        """ + text);
                LOG.debug("agent translation: %s", result);
                final Matcher matcher = CONCEPT_PATTERN.matcher(Str.Helper.cleanString(result));
                while (matcher.find()) {
                    final String concept = TextUtil.stripStopwords(GLOBAL_STOP_WORD_SET, CommonUtil.normalize(matcher.group(1)));
                    if (!concept.isEmpty() && conceptStrings.add(concept))
                        LOG.info("%s extracted", concept);
                }
                return noobj();
            }));
            if (blocking) thread.applyAsync().get();
            else thread.applyAsync();
        } catch (final Exception e) {
            LOG.error(e);
        }
        return conceptStrings;
    }

    @Override
    protected String systemMessage() {
        return CONCEPT_EXTRACTOR_AGENT_SYSTEM_MESSAGE;
    }

    private static final String CONCEPT_EXTRACTOR_AGENT_SYSTEM_MESSAGE =
            """
            As you respond, a separate analysis agent running behind the scenes automatically
            extracts key concepts from your output using a language model.  These concepts are
            organized into a co-location graph that connects related ideas across the conversation.
            
            When relevant historic memories are identified, they will be surfaced via the mtron
            eval tool so you can review them before continuing.
            
            You do not need to tag concepts manually — the extraction happens automatically.
            Respond naturally and the concept graph will build itself.
            """;

}
