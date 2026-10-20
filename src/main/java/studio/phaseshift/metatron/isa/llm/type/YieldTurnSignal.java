package studio.phaseshift.metatron.isa.llm.type;

/**
 * Control-flow signal thrown by a harness-tool executor to stop the LangChain4j tool loop
 * mid-turn and yield the pending tool call back to the transport, which surfaces it as an
 * OpenAI {@code tool_calls} message.  Not an error: the tool is owned by the calling harness,
 * which executes it and re-enters with a {@code role:tool} result.
 */
public class YieldTurnSignal extends RuntimeException {

    public final String toolName;
    public final String arguments;
    public final String toolCallId;

    public YieldTurnSignal(final String toolName, final String arguments, final String toolCallId) {
        super("yield turn: " + toolName);
        this.toolName = toolName;
        this.arguments = arguments;
        this.toolCallId = toolCallId;
    }
}
