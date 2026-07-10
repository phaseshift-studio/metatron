package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class ToolFeature extends Feature {

    public ToolFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // ToolFeature is pure config — the chest lives in its JVM.
    // AgentUtility.buildTools reads it via agent.feature(TOOL).at(uri(TOOL))

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        if (result.isRec()) {
            final Rec r = result.asRec();
            this.logger().info("tool executed: %s(%s) => %s",
                    r.at(uri(NAME)).strValue(),
                    r.at(uri(TOOL_ARGUMENTS)).strValue(),
                    r.at(uri(RESULT)).strValue());
        }
    }
}
