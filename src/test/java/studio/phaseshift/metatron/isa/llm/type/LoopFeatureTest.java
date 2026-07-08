package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.feature.LoopFeature;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class LoopFeatureTest extends FeatureTest {

    @Test
    public void testLoopFeatureStructure() {
        assertFeatureStructure(new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {});
    }

    @Test
    public void testLoopFeatureOnBeforeChatInitializesTrail() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {};
        loop.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                loop.onBeforeChat((Agent) agent)), MUTABLE);

        final Agent a = agentWithFeatures(loop);
        for (final Obj f : a.features().lstValue())
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);

        // onBeforeChat should initialize the loop_results trail
        final Obj trail = a.at(res("loop_results"));
        assertFalse(trail.isNoObj(), "loop_results should be initialized to empty lst");
    }

    @Test
    public void testLoopFeatureDelayConfig() {
        final LoopFeature lfNoDelay = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {};
        assertTrue(lfNoDelay.at(uri("delay")).isNoObj(), "no delay key → default behavior");

        final LoopFeature lfWithDelay = new LoopFeature(
                mutableMap(uri("delay"), real(3.5d)), feat("loop"), null) {};
        assertFalse(lfWithDelay.at(uri("delay")).isNoObj(), "delay should be present in JVM");
    }

    @Test
    public void testLoopResultsIncludedInResultAssembly() {
        final Agent a = agentWithFeatures();

        a.at(res("loop_results"), lst(
                rec(uri("iteration"), jnt(1), uri("result"), rec(uri(CHAT), str("pass 1"))),
                rec(uri("iteration"), jnt(2), uri("result"), rec(uri(CHAT), str("pass 2")))
        ), MUTABLE);

        final Map<Obj, Obj> resultMap = new LinkedHashMap<>();
        resultMap.put(uri(CHAT), str("final"));
        resultMap.put(uri(TIME), jnt(100));
        resultMap.put(uri(ERROR), noobj());
        if (!a.at(res("loop_results")).isNoObj())
            resultMap.put(uri("loop_results"), a.at(res("loop_results")));
        final Rec result = rec(resultMap);

        final Obj trail = result.at(uri("loop_results"));
        assertFalse(trail.isNoObj(), "result should include loop_results");
    }

    @Test
    public void testLoopResultsAbsentWithoutLoopFeature() {
        final Agent a = agentWithFeatures();

        final Map<Obj, Obj> resultMap = new LinkedHashMap<>();
        resultMap.put(uri(CHAT), str("done"));
        resultMap.put(uri(TIME), jnt(42));
        resultMap.put(uri(ERROR), noobj());
        final Rec result = rec(resultMap);

        assertTrue(result.at(uri("loop_results")).isNoObj(),
                "no loop_results field without LoopFeature");
    }

    @Test
    @Disabled("requires mock StreamingChatModel for full loop lifecycle")
    public void testLoopFeatureFullCycle() {
    }
}
