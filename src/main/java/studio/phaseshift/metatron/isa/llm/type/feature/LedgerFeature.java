package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;

/**
 * A persistent scratchpad the agent owns across the entire session.
 * Never cleared between chat calls.  The agent reads it via system
 * message injection (no tool needed) and writes updates via
 * {@code <<mtron:ledger>>} blocks at end of response.
 */
public class LedgerFeature extends Feature {

    private static final fURI LEDGER = res("ledger");

    private static final String INSTRUCTIONS = """
            You have a persistent ledger at <<mtron:ledger>>. \
            Update it by appending a mtron:ledger block to your response. \
            Use it to track long-running tasks, todos, or context you want \
            to carry across the entire conversation. The ledger survives \
            all chat calls — it is never cleared.""";

    public LedgerFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj skill() {
        return rec(uri(NAME), uri("ledger"),
                uri(DESC), str("Persistent agent-owned scratchpad for cross-turn task tracking"),
                uri(CONTENT), str(INSTRUCTIONS));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // Pre-populate from init config if ledger is empty and init is set
        final Obj ledger = agent.at(LEDGER);
        if (ledger.isNoObj()) {
            final Obj init = this.at(uri("init"));
            if (!init.isNoObj())
                agent.at(LEDGER, init, MUTABLE);
        }

        final Obj current = agent.at(LEDGER);
        if (!current.isNoObj())
            agent.addSystemMessage("Ledger:\n" + current);

        return noobj();
    }
}
