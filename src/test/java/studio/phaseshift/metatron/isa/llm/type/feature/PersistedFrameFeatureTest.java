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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.Frame;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The persisted frame provider — pushes a root frame on beforeChat, pops it complete on
 * completeResponse, and leaves it in space for introspection.
 */
public class PersistedFrameFeatureTest extends AbstractMetatronTest {

    private static final fURI ROOT = f("/usr/test/agent");
    private static final fURI SESSION_VID = f("/usr/test/agent/session/1");

    @BeforeAll
    public static void mountSpace() {
        InstSet.importInstSet(LLM_ISA_TID);
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    @Test
    public void testFrameLifecyclePersists() {
        final WindowMessageFeature message = new WindowMessageFeature(
                mutableMap(uri(SESSION), uri(SESSION_VID), uri(ALGORITHM), rec(mutableMap(uri(MAX), jnt(50)))),
                LLM_WINDOW_MESSAGE_FEATURE_TID, null);
        final PersistedFrameFeature frame = new PersistedFrameFeature(
                mutableMap(uri(ROOT), uri(ROOT)), LLM_PERSISTED_FRAME_FEATURE_TID, null);

        final Agent agent = AgentFixture.builder().feature(frame, message).build();

        // drive the hooks directly (chat() needs a live model); the agent pushes/pops the frame
        message.advanceChatId(agent);
        frame.prepare(agent);   // builds the ChatStack (no push — chat() drives it)
        frame.push(ChatFrame.chatFrame().prompt("hello"));

        final fURI frameURI = frame.current();
        assertNotNull(frameURI, "push addresses the root frame");

        // locals write + read
        frame.locals(f("orphans"), str("x"));
        assertTrue(frame.at(f("orphans")).isStr(), "locals write is readable");

        // pop marks complete, does not delete
        final Frame popped = frame.pop();
        assertTrue(popped.isComplete(), "popped frame is complete");
        assertNull(frame.current(), "stack empty after pop");

        final Obj persisted = Router.readFromSpace(frameURI);
        assertTrue(persisted.isRec(), "frame persisted after pop");
        assertTrue(persisted.asRec().at(uri(STATE)).isUri(), "frame marked complete");
    }
}
