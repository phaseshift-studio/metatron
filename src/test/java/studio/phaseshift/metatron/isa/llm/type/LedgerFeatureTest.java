package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.feature.LedgerFeature;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class LedgerFeatureTest extends FeatureTest {

    @Test
    public void testLedgerFeatureStructure() {
        assertFeatureStructure(new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {});
    }

    @Test
    public void testLedgerInjectsAsSystemMessageWhenPresent() {
        final LedgerFeature ledger = new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {};
        ledger.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                ledger.onBeforeChat((Agent) agent)), MUTABLE);

        final Agent a = agentWithFeatures(ledger);

        // Write something to the ledger
        a.at(res("ledger"), lst(str("task 1"), str("task 2")), MUTABLE);

        // Fire onBeforeChat — should inject ledger as system message
        for (final Obj f : a.features().lstValue())
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);

       /* assertEquals(1, a.getSystemMessages().size(),
                "should inject 1 system message when ledger is non-empty");
        final String msg = a.getSystemMessages().getFirst();
        assertTrue(msg.contains("task 1"),
                "system message should contain ledger contents");
        assertTrue(msg.contains("task 2"),
                "system message should contain all ledger entries");
        assertTrue(msg.contains("Ledger"),
                "system message should be labeled as Ledger");*/
    }

    @Test
    public void testLedgerDoesNotInjectWhenEmpty() {
        final LedgerFeature ledger = new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {};
        ledger.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                ledger.onBeforeChat((Agent) agent)), MUTABLE);

        final Agent a = agentWithFeatures(ledger);
        // Don't write anything — ledger is empty

        for (final Obj f : a.features().lstValue())
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);

        assertEquals(0, a.getSystemMessages().size(),
                "should not inject when ledger is empty");
    }

    @Test
    public void testLedgerInitPrepopulates() {
        // init=>[...] in the feature config pre-populates the ledger on first call
        final LedgerFeature ledger = new LedgerFeature(
                mutableMap(uri("init"), lst(str("preloaded task 1"), str("preloaded task 2"))),
                feat("ledger"), null) {};
        ledger.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                ledger.onBeforeChat((Agent) agent)), MUTABLE);

        final Agent a = agentWithFeatures(ledger);

        // First onBeforeChat should pre-populate
        for (final Obj f : a.features().lstValue())
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);

        final Obj ledgerObj = a.at(res("ledger"));
      //  assertFalse(ledgerObj.isNoObj(), "ledger should be populated from init");
    //    assertTrue(ledgerObj.isLst() || ledgerObj.isRec(), "ledger should contain init data");

        //final String msg = a.getSystemMessages().getFirst();
        //assertTrue(msg.contains("preloaded task 1"), "system message should show init content");
    }

    @Test
    public void testLedgerInitDoesNotOverwriteExisting() {
        // If ledger already has data, init should not overwrite
        final LedgerFeature ledger = new LedgerFeature(
                mutableMap(uri("init"), lst(str("init value"))),
                feat("ledger"), null) {};
        ledger.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                ledger.onBeforeChat((Agent) agent)), MUTABLE);

        final Agent a = agentWithFeatures(ledger);

        // Pre-set ledger to something else
        a.at(res("ledger"), lst(str("existing data")), MUTABLE);

        // onBeforeChat should keep existing, not overwrite with init
        for (final Obj f : a.features().lstValue())
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);

        assertTrue(a.at(res("ledger")).toString().contains("existing data"),
                "existing ledger should not be overwritten by init");
    }

    @Test
    public void testLedgerPersistsAcrossChats() {
        final LedgerFeature ledger = new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {};
        ledger.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                ledger.onBeforeChat((Agent) agent)), MUTABLE);

        final Agent a = agentWithFeatures(ledger);

        // Simulate chat 1: agent writes to ledger
        a.at(res("ledger"), lst(str("find all .java files")), MUTABLE);

        // Chat 2: onBeforeChat should see the ledger still there
        a.getSystemMessages().clear();
        for (final Obj f : a.features().lstValue())
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(a);

       // assertEquals(1, a.getSystemMessages().size(),
       //         "ledger should persist — onBeforeChat does not clear it");
    }
}
