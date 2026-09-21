package studio.phaseshift.metatron.isa.llm.type.feature.service;

import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.Rec;

/**
 * The chat capability — the model/response state of the agent's current turn.
 */
public interface ChatService {

    /**
     * The current turn's model/response rec, where the streaming response accumulates.
     *
     * @return the last-message rec (empty when the turn has not produced one yet)
     */
    Rec lastMessage();

    mModel model();
}
