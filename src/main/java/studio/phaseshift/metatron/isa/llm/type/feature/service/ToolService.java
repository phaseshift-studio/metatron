package studio.phaseshift.metatron.isa.llm.type.feature.service;

import dev.langchain4j.service.tool.ToolProvider;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Lst;

/**
 * The tool capability — the agent's single tool registry.  A feature providing this
 * service owns the collection of registered tools and their LC4j projection.
 */
public interface ToolService {

    /**
     * Register a tool with the agent's tool channel.
     *
     * @param tool the tool to register
     */
    void addTool(mTool tool);

    /**
     * The registered tools.
     *
     * @return the tool list
     */
    Lst tools();

    /**
     * The LC4j projection of the registered tools, used to wire the agent's model.
     *
     * @return the tool provider
     */
    ToolProvider getToolProvider();

    /**
     * Register a raw LC4j tool provider (used when a provider is built outside the feature).
     *
     * @param toolProvider the provider to register
     */
    void addToolProvider(ToolProvider toolProvider);
}
