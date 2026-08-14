package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class SystemFeature extends AbstractFeature {

    public SystemFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static String generateSystemMessage(final Agent agent) {
        return (String.join("\n", agent.getSystemMessages()) + "\n" + agent.at(DESC).orElse(str("")).strValue()).trim();
    }

    // Key used to store the last-written text in this feature's JVM
    private static final String LAST = "last_system_text";

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final String systemMessage = generateSystemMessage(agent);
        if (!systemMessage.isBlank() && agent.hasFeature(SESSION)) {
            // Only write if the system message changed since last chat
            final String lastText = this.at(uri(LAST)).orElse(str("")).strValue();
            if (!systemMessage.equals(lastText)) {
                final fURI sessionVID = agent.feature(SESSION).asRec().at(SESSION).uriValue();
                try {
                    MessageBuilder.build(SYSTEM_MESSAGE_TID)
                            .text(systemMessage)
                            .time()
                            .session(sessionVID)
                            .depth(agent.chatDepth())
                            .chatId(agent.chatId())
                            .create(agent.at(ROOT).uriValue().extend(MESSAGE).extend("_").addQ(INCRQ));
                    this.at(uri(LAST), str(systemMessage), MUTABLE);
                } catch (final Exception e) {
                    this.logger().warn("system message write failed (non-blocking): %s", e.getMessage());
                }
            }
        }
        return noobj();
    }
}
