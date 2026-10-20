package studio.phaseshift.metatron.isa.llm.type.openai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import studio.phaseshift.metatron.isa.llm.mToolExecutor;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.YieldTurnSignal;

/**
 * A tool executor that never evaluates locally: it records the tool call as a yield (the agent's
 * pending-yield signal) and returns a stub, so the transport can surface the call as an OpenAI
 * {@code tool_calls} message to the outer harness, which owns the tool.
 */
public class HarnessToolExecutor extends mToolExecutor {

    private final String toolName;
    private Agent agent = null;

    public HarnessToolExecutor(final String toolName) {
        super(null);
        this.toolName = toolName;
    }

    @Override
    public HarnessToolExecutor agent(final Agent agent) {
        this.agent = agent;
        return this;
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        if (null != this.agent)
            this.agent.pendingYield(new YieldTurnSignal(this.toolName, request.arguments(), request.id()));
        return "yielded to harness";
    }
}
