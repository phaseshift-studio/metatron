package studio.phaseshift.metatron.isa.llm.type.feature.service;

import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Str;

/**
 * The thinking-stream capability — lets another feature append to the agent's thought channel.
 */
public interface ThinkService {

    /**
     * Append a fragment to the agent's thinking stream (surfaced to the model as thought).
     *
     * @param agent the agent whose thought channel to write
     * @param text  the thought fragment to append
     */
    void append(final Agent agent, final Str text);
}
