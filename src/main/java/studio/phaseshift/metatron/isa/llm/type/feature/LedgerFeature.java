package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A persistent scratchpad the agent owns across the entire session.
 * Never cleared between chat calls.  The agent reads it via system
 * message injection (no tool needed) and writes updates via
 * {@code <<mtron:ledger>>} blocks at end of response.
 */
public class LedgerFeature extends AbstractFeature {

    public static final String LEDGER = "ledger";

    private static final
    String INSTRUCTIONS = """
                          You have a persistent keyed ledger at %%%. Use the mtron eval tool to:
                            [-- reading --]
                              1. read entire k/v structure    : *<%%%/+/>
                              2. read a single k entry        : *<%%%/k>
                            [-- writing --]
                              3. write a single k/v entry     : <%%%/k> -> v
                              4. delete a single k/v entry    : <%%%/k> -> noobj
                              5. clear the entire structure   : <%%%/+> -> noobj
                            [-- searching --]
                              6. search the ledger            : <%%%/../search>("regex")
                            [-- archiving --]
                              7. archive the entire structure : *<%%%> >- @<%%%/../archive>
                              8. restore the last archive     : <%%%>  -> *<%%%/../archive/0>
                              9. restore the first archive    : <%%%>  -> *<%%%/../archive/-1>
                                 -- access any by their index value with negative indices supported
                              10. count the number of archives : *<%%%/../archive>.>>.count()
                          
                          Ledger keys must be a lower case string (no quotes) with no special characters.
                          Ledger values can be, e.g., 57, "a string", <http://a_uri.com>, [a,"list",12.5].
                              e.g. <%%%/abc> -> "this is an entry for abc"
                          
                          Use the ledger to track long-running tasks, todos, or context you want
                          to carry across the entire conversation. The ledger survives
                          all chat calls — it is never cleared.
                          """;

    public LedgerFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Lst skill(final Agent agent) {
        final fURI ledgerVID = this.at(LEDGER).uriValue();
        return lst(rec(uri(NAME), uri(LEDGER),
                uri(DESC), str("persistent agent-owned scratchpad for cross-turn task tracking"),
                uri(CONTENT), str(INSTRUCTIONS.replaceAll("%%%", ledgerVID.toString()))));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // Pre-populate from init config if ledger is empty and init is set
        final fURI ledgerVID = this.at(LEDGER).uriValue();
        final fURI archiveVID = ledgerVID.retract(1).extend("archive");
        Obj ledger = Router.readFromSpace(ledgerVID);
        if (ledger.isNoObj()) {
            ledger = Router.writeToSpace(ledgerVID, rec());
        }
        Obj archive = Router.readFromSpace(archiveVID);
        if (archive.isNoObj())
            Router.writeToSpace(archiveVID, lst());
        ObjmtronSerializer.parse("""
                                 <%%%/../search> ->  |inst?#{*}<=#{?}(reg=>str::T){ *<%%%>.>-.-<[<< => >>.as(str::T).=?=(regex(*reg)>-.count()?>0)]=?=(>-.count()?>0) }
                                 """.replaceAll("%%%", ledgerVID.toString())).apply();
        // Cross-feature communication: SystemFeature owns the system-message channel.
        // If the agent lacks it, this feature is debilitated — log and proceed.
        if (this.requireFeature(agent, SYSTEM))
            agent.feature(SYSTEM).<SystemFeature>as().addSystemMessage("ledger keys: " + ledger.asRec().keys().toList());
        return noobj();
    }
}
