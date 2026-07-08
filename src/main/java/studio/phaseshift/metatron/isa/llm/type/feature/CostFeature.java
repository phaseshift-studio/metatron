package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tracks LLM cost across the chat lifecycle.  During {@code onBeforeChat}
 * it scans features for model configs and looks up provider pricing.
 * Streaming hooks accumulate cost deltas, and {@code onCompleteResponse}
 * writes the final cost rec to the Agent's result blackboard.
 */
public class CostFeature extends Feature {

    private double inboundCostPerToken = 0.0;
    private double outboundCostPerToken = 0.0;
    private double accumulatedCost = 0.0;

    public CostFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // TODO: scan feature list for models, query provider pricing APIs
        // For now, read cost from the ChatFeature's model config
        final Obj chatFeat = agent.feature(CHAT);
        final Obj chatModel = chatFeat.isNoObj() ? noobj() : ((Poly) chatFeat).at(uri(MODEL));
        if (!chatModel.isNoObj()) {
            final Rec model = chatModel.asRec();
            final Obj costConfig = model.at(uri(COST));
            if (!costConfig.isNoObj()) {
                final Rec costRec = costConfig.asRec();
                this.inboundCostPerToken = costRec.at(uri("in")).orElse(real(0d)).realValue();
                this.outboundCostPerToken = costRec.at(uri("out")).orElse(real(0d)).realValue();
            }
        }
        this.accumulatedCost = 0.0;
        agent.at(res(COST), rec(), MUTABLE);
        return noobj();
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        // Accumulate cost based on tool execution bytes
        if (result.isRec()) {
            final Rec r = result.asRec();
            final String toolResult = r.at(uri(RESULT)).orElse(noobj()).strValue();
            if (!toolResult.isBlank())
                this.accumulatedCost += toolResult.getBytes().length * this.inboundCostPerToken;
        }
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str response) {
        final double responseCost = response.strValue().getBytes().length * this.outboundCostPerToken;
        this.accumulatedCost += responseCost;

        final Rec costRec = rec(
                uri("inbound"), real(this.accumulatedCost * this.inboundCostPerToken),
                uri("outbound"), real(responseCost),
                uri("total"), real(this.accumulatedCost)
        );
        agent.at(res(COST), costRec, MUTABLE);
    }

    @Override
    public void onError(final Agent agent, final Fail fail) {
        // Finalize cost even on error
        final Rec costRec = rec(
                uri("total"), real(this.accumulatedCost)
        );
        agent.at(res(COST), costRec, MUTABLE);
    }
}
