package studio.phaseshift.metatron.isa.llm.type.feature.service;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;

import java.util.Set;

/**
 * The concept-extraction capability — distills text into concept uris in the agent's
 * concept namespace.
 */
public interface ConceptService {

    /**
     * Distill the given text into concepts, persist them in the concept namespace, and
     * return the concept uris written.
     *
     * @param agent    the agent owning the concept namespace
     * @param text     the raw text to analyze (blank yields an empty set)
     * @param blocking if true and extraction is async, block until it finishes
     * @return the set of concept uris persisted
     */
    Set<fURI> processConcepts(final Agent agent, final String text, final boolean blocking);

    /**
     * The concept namespace root for the given agent — every concept uri hangs off it.
     *
     * @param agent the agent whose root to resolve
     * @return the concept root uri
     */
    fURI root(final Agent agent);
}
