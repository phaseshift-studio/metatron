package studio.phaseshift.metatron.isa.llm.type.feature.service;

/**
 * The system-message channel — the single writer of the agent's system context, with
 * per-chat contributions from other features.
 */
public interface SystemService {

    /**
     * Append a feature's contribution to the agent's system context for the next turn.
     *
     * @param text the contribution to add
     */
    void addSystemMessage(final String text);

    /**
     * The assembled system message — every contribution joined for the current turn.
     *
     * @return the full system message
     */
    String systemMessage();

    /**
     * Drop every accumulated contribution.
     */
    void clearSystemMessages();

    /**
     * The individual per-chat contributions (unjoined).
     *
     * @return the raw contribution list
     */
    java.util.List<String> getSystemMessages();
}
