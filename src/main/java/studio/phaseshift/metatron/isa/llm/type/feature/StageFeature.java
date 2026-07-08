package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Records every pipeline phase as a typed stage entry in the Agent's
 * result blackboard ({@code /result/stages}).  Each lifecycle hook
 * appends a new stage record carrying the event data in flight.
 */
public class StageFeature extends Feature {

    public StageFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        agent.at(res("stages"), lst(), MUTABLE);
        appendStage(agent, "before_chat", rec(uri("userMessage"), str(agent.userMessage())));
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        appendStage(agent, "partial_response", rec(uri(TEXT), text));
    }

    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        appendStage(agent, "partial_thinking", rec(uri(TEXT), text));
    }

    @Override
    public void onPartialToolCall(final Agent agent, final Inst request) {
        appendStage(agent, "partial_tool_call", request.isNoObj() ? rec() : rec(uri("tool"), request));
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        appendStage(agent, "tool_executed", result.isNoObj() ? rec() : result.asRec());
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str response) {
        appendStage(agent, "complete_response", rec(uri(TEXT), response));
    }

    @Override
    public void onError(final Agent agent, final Fail fail) {
        appendStage(agent, "error", fail.isNoObj() ? rec() : rec(uri("fail"), fail));
    }

    private void appendStage(final Agent agent, final String phase, final Rec data) {
        final Obj stages = agent.at(res("stages"));
        final Rec entry = rec(uri("phase"), str(phase), uri("data"), data);
        agent.at(res("stages"), stages.isNoObj() ? lst(entry) : ((Lst) stages).add(entry, MUTABLE), MUTABLE);
    }
}
