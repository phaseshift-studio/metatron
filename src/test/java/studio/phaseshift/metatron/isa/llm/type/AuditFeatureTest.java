package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.feature.AuditFeature;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class AuditFeatureTest extends FeatureTest {

    @Test
    public void testAuditFeatureStructure() {
        assertFeatureStructure(new AuditFeature(new LinkedHashMap<>(), feat("audit"), null) {
        });
    }

    @Test
    public void testAuditFeaturePopulatesResultBlackboard() {
        final AuditFeature audit = new AuditFeature(new LinkedHashMap<>(), feat("audit"), null) {
        };
        audit.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                audit.onBeforeChat((Agent) agent)), MUTABLE);
        audit.at(uri(ON_TOOL_EXECUTED), instLambda((agent, i) -> {
            audit.onToolExecuted((Agent) agent, i.arg(0));
            return noobj();
        }), MUTABLE);
        audit.at(uri(ON_COMPLETE_RESPONSE), instLambda((agent, i) -> {
            audit.onCompleteResponse((Agent) agent, i.arg(0).asStr());
            return noobj();
        }), MUTABLE);

        final Agent a = agentWithFeatures(audit, ObservedTestFeature.observe("audit-observer"));
        simulateLifecycle(a, "test_tool", "ok", "test response");

        final Obj table = a.at(res("audit", "table"));
        assertFalse(table.isNoObj(), "AuditFeature should generate text table");
        assertTrue(table.strValue().contains("before_chat"), "table should have before_chat");
        assertTrue(table.strValue().contains("tool_exec"), "table should have tool_exec");
        assertTrue(table.strValue().contains("complete"), "table should have complete");

        final Obj widget = a.at(res("audit", "widget"));
        assertFalse(widget.isNoObj(), "AuditFeature should generate TableWidget");

        final Map<Obj, Obj> resultMap = new LinkedHashMap<>();
        resultMap.put(uri(CHAT), a.at(res(CHAT)));
        resultMap.put(uri(TIME), a.at(res(TIME)));
        resultMap.put(uri(ERROR), a.at(res(ERROR)));
        if (!table.isNoObj())
            resultMap.put(uri("audit"), table);
        final Rec result = rec(resultMap);

        assertFalse(result.at(uri("audit")).isNoObj(), "result should include audit table");
    }
}
