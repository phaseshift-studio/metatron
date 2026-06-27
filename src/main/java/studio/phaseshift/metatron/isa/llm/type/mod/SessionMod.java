/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.llm.type.mod;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.mAgent;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.ALGORITHM;
import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SessionMod implements Mod {

    @Override
    public void apply(final mModel model, final AiServices<mAgent> services) {
        if (!model.session().isNoObj()) {
            try {
                final fURI sessionVID = model.session().vid();
                if (sessionVID == null) {
                    model.logger().warn("llm session has no vid (ignoring): %s", model.session());
                } else {
                    final Space space = Router.global().getSpaceFor(sessionVID);
                    final SpaceChatSessionStore store = new SpaceChatSessionStore(space);
                    final MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                            .alwaysKeepSystemMessageFirst(true)
                            .maxMessages(model.session().at(ALGORITHM).asRec().at(MAX).orElse(jnt(15)).intValue().intValue())
                            .id(sessionVID)
                            .chatMemoryStore(store)
                            .build();
                    services.chatMemory(chatMemory)
                            .storeRetrievedContentInChatMemory(true);
                    // Save so mergeSystemMessages() can mirror system messages
                    //model.sessionStore = store;
                }
            } catch (Exception e) {
                throw MTronException.of("unable to setup session: %s", e);
            }
        }
    }
}
