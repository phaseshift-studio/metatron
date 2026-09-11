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
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.REQUIRED;
import static studio.phaseshift.metatron.Tokens.STAGE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LambdaFeature extends AbstractFeature {

    public LambdaFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    private void computeLambda(final fURI stage, final Agent agent, Obj arg) {
        this.at(STAGE).orElse(rec()).at(stage).orElse(lst()).elements().forEach(lambda -> {
            final Obj result;
            try {
                if (lambda.isInst()) {
                    if (null != arg)
                        result = lambda.asInst().apply(arg);
                    else
                        result = lambda.asInst().apply(agent);
                } else
                    result = lambda.apply(null == arg ? agent : arg);
                if (!stage.equals(f("on_error")) && result.isFail()) {
                    throw result.asFail().asException();
                }
            } catch (final Exception e) {
                if (stage.equals(f("on_error")))
                    LOG.error("an error occurred during on_error: %s", e);
                else throw e;
            }
        });
    }

    @Override
    public Set<fURI> requires() {
        return this.at(REQUIRED).elements().map(Obj::uriValue).collect(Collectors.toSet());
    }


    @Override
    public void onAgentCtor(final Agent agent) {
        this.computeLambda(f("on_agent_ctor"), agent, null);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.computeLambda(f("on_before_chat"), agent, null);
        return noobj();
    }

    // ── Streaming (observation) ──────────────────────────────────

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        this.computeLambda(f("on_partial_response"), agent, text);
    }

    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        this.computeLambda(f("on_partial_thinking"), agent, text);
    }

    @Override
    public void onPartialToolCall(final Agent agent, final Inst request) {
        this.computeLambda(f("on_partial_tool_call"), agent, request);
    }

    // ── Tool execution ───────────────────────────────────────────

    @Override
    public void beforeToolExecution(final Agent agent, final Inst request) {
        this.computeLambda(f("before_tool_execution"), agent, request);
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        this.computeLambda(f("on_tool_executed"), agent, result);
    }

    @Override
    public void onToolResult(final Agent agent, final Inst tool, final Obj result) {
        // this.computeLambda(f("on_tool_result"), agent, tool, result);
    }

    // ── Completion ───────────────────────────────────────────────

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        this.computeLambda(f("on_complete_response"), agent, result);
    }

    // ── Error ────────────────────────────────────────────────────

    @Override
    public void onError(final Agent agent, final Fail fail) {
        this.computeLambda(f("on_fail"), agent, fail);
    }
}
