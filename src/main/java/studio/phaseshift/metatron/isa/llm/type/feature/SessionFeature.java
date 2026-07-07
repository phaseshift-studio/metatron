package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

public class SessionFeature extends Feature {

    private MessageWindowChatMemory chatMemory;

    public SessionFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // Session is stored in this feature's own config (key => resolved session Rec)
        final Obj sessionObj = this.at(uri(SESSION));
        if (sessionObj.isNoObj()) return noobj();
        final Rec session = sessionObj.asRec();
        if (session.isNoObj()) return noobj();
        try {
            final fURI sessionVID = session.vid();
            if (sessionVID == null) return noobj();
            final Space space = Router.global().getSpaceFor(sessionVID);
            final SpaceChatSessionStore store = new SpaceChatSessionStore(agent, space);
            final int max = session.at(ALGORITHM).asRec().at(MAX).orElse(jnt(15)).intValue().intValue();
            this.chatMemory = MessageWindowChatMemory.builder()
                    .alwaysKeepSystemMessageFirst(true)
                    .maxMessages(max)
                    .id(sessionVID)
                    .chatMemoryStore(store)
                    .build();
        } catch (final Exception e) {
            throw MTronException.of("unable to setup session: %s", e);
        }
        return noobj();
    }

    public MessageWindowChatMemory chatMemory() { return this.chatMemory; }
}
