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

package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;

/**
 * Message provider that aggregates by a message-count window ({@code MessageWindowChatMemory}).
 * {@code algorithm => [max => N]} is the message ceiling.
 */
public final class WindowMessageFeature extends AbstractMessageFeature {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_WINDOW_MESSAGE_FEATURE_TID;

    public WindowMessageFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    protected ChatMemory buildMemory(final fURI sessionID, final int max, final SpaceChatSessionStore store) {
        return MessageWindowChatMemory.builder()
                .alwaysKeepSystemMessageFirst(true)
                .maxMessages(max)
                .id(sessionID)
                .chatMemoryStore(store)
                .build();
    }

    @Override
    protected String algorithmName() {
        return "message_window";
    }
}
