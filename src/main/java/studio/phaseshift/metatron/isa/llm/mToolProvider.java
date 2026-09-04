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

package studio.phaseshift.metatron.isa.llm;


import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import studio.phaseshift.metatron.isa.llm.type.mTool;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mToolProvider implements ToolProvider {

    private final Set<mTool> toolSet = new LinkedHashSet<>();
    private final Set<ToolProvider> providers = new LinkedHashSet<>();

    public void addToolProvider(final ToolProvider provider) {
        this.providers.add(provider);
    }

    public void addTool(final mTool tool) {
        this.toolSet.removeIf(t -> t.name().equals(tool.name()));
        this.toolSet.add(tool);
    }

    public Set<mTool> getTools() {
        return this.toolSet;
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        final Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        this.providers.forEach(p -> map.putAll(p.provideTools(request).tools()));
        this.toolSet.forEach(t -> {
            map.put(t.toolSpecification().get0(), t.toolSpecification().get1());
        });
        return new ToolProviderResult(map);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
