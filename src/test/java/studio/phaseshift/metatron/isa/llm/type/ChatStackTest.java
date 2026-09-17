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

package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * The frame spine — a {@link ChatStack} writing frames flat to
 * {@code frame/s<sid>/c<cid>/d<depth>} and deriving them back on read.
 */
public class ChatStackTest extends AbstractMetatronTest {

    private static final fURI ROOT = f("/usr/test/agent");
    private static final fURI SESSION = f("/usr/test/agent/session/1");

    @BeforeAll
    public static void mountSpace() {
        InstSet.importInstSet(LLM_ISA_TID);
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    @Test
    public void testFrameSpine() {
        final ChatStack stack = new ChatStack(ROOT, SESSION, 2, 1);

        // root frame — depth 1 (the top-level recursion level), no parent
        final Frame root = stack.push(ChatFrame.chatFrame().prompt("hello"));
        assertNotNull(stack.current(), "push must address the frame");
        assertEquals(1, root.depth(), "root is depth 1");
        assertEquals(2, root.chatId(), "chat id is stamped");
        assertEquals("hello", root.prompt(), "prompt is the argument");
        assertNull(root.parentURI(), "root has no parent");

        // locals write + read (flat, same frame)
        stack.locals(f("orphans"), str("x"));
        assertTrue(stack.at(f("orphans")).isStr(), "locals write is readable");

        // child frame — depth 1, links its parent
        final fURI rootURI = stack.current();
        final Frame child = stack.push(ChatFrame.chatFrame().prompt("nested"));
        assertEquals(2, child.depth(), "child is depth 2");
        assertEquals(rootURI, child.parentURI(), "child links its parent");

        // shadow → root: the child sees the root's locals
        assertTrue(stack.at(f("orphans")).isStr(), "shadow resolves to root");

        // pop the child — complete, root survives (never deleted)
        final Frame childDone = stack.pop();
        assertTrue(childDone.isComplete(), "popped frame is complete");
        assertEquals(2, childDone.depth(), "popped the child");
        assertTrue(stack.at(f("orphans")).isStr(), "root survives the pop");

        // pop the root — complete, stack empty
        final Frame rootDone = stack.pop();
        assertTrue(rootDone.isComplete(), "root completes");
        assertNull(stack.current(), "stack is empty");
    }
}
