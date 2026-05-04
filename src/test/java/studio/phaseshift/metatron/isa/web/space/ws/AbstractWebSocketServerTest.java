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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Abstract base for unit-testing WSServer implementations.
 * Subclasses only need to provide {@link #createServer(fURI)}.
 * The server instance is available as {@link #server} in all tests.
 */
public abstract class AbstractWebSocketServerTest extends AbstractMetatronTest {

    protected Space testSpace;
    protected WebSocketRec server;

    /**
     * Override to change the test space pattern. Default: /test/#
     */
    protected fURI testSpacePattern() {
        return f("/test/#");
    }

    protected fURI createTestVid() {
        return f("/test/" + getClass().getSimpleName() + "/" + System.nanoTime());
    }

    /**
     * The only abstract method — produce a server instance for the given vid.
     */
    protected abstract WebSocketRec createServer(fURI vid);

    /**
     * Look up the registered Type for this server from the Router.
     */
    protected Type serverType() {
        return Router.global().read(server.vid()).type();
    }

    @BeforeEach
    public void setupTestSpace() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.testSpace = wsSpace.of(mutableMap(
                uri(PATTERN),uri(testSpacePattern()),
                uri(HOST),uri("ws://localhost:" + generatePort()),
                uri(ROUTE),rec()), f("/sys/space/test"));
        this.server = createServer(createTestVid());
    }

    @AfterEach
    public void teardownTestSpace() {
        if (this.testSpace != null) {
            Router.global().removeSpace(this.testSpace.vid());
            this.testSpace.close();
            this.testSpace = null;
        }
        this.server = null;
    }

    // =========================================================
    // Type-level tests
    // =========================================================

    @Test
    public void testServerTypeIsDefined() {
        final Type type = serverType();
        assertNotNull(type, "server type should not be null");
        assertNotNull(type.tid(), "server type should have a tid");
        assertNotNull(type.vid(), "server type should have a vid");
    }

    @Test
    public void testServerTypeVidMatchesTid() {
        // The type looked up at server.tid() should have vid == server.tid()
        assertEquals(server.tid(), serverType().vid(),
                "type vid should equal the server's tid");
    }

    @Test
    public void testServerTypeHasPredicate() {
        assertTrue(serverType().hasPredicate(), "type predicate should not be noobj");
    }

    @Test
    public void testServerTypeHasConstructor() {
        final Type type = serverType();
        assertNotNull(type.constructor(), "type should have a constructor");
        assertTrue(type.hasConstructor(), "constructor should not be noobj");
    }

    // =========================================================
    // Instance-level tests
    // =========================================================

    @Test
    public void testServerIsWSServer() {
        assertInstanceOf(WebSocketObj.class, server, "server should implement WSServer");
    }

    @Test
    public void testServerHasVid() {
        assertNotNull(server.vid(), "server should have a vid");
    }

    @Test
    public void testServerHasIOSerializers() {
        assertNotNull(server.getIO(), "server should have IO serializers");
        assertNotNull(server.getIO().input(), "should have an input serializer");
        assertNotNull(server.getIO().output(), "should have an output serializer");
    }

    // =========================================================
    // Handler registration
    // =========================================================
    
    @Test
    public void testHasOnMessage() {
        assertFalse(server.at(uri(ON_MESSAGE)).isNoObj(), "missing ON_MESSAGE");
    }
    
    // =========================================================
    // Handler invocation (no real WebSocket needed)
    // =========================================================

    @Test
    public void testOnOpenInvocation() {
        assertNotNull(server.at(uri(ON_OPEN)).apply(uri("/test/resource")));
    }

    @Test
    public void testOnCloseInvocation() {
        assertNotNull(server.at(uri(ON_CLOSE)).apply(
                rec(uri(CODE), jnt(1000), uri(REASON), str("normal closure"))));
    }

    @Test
    public void testOnErrorInvocation() {
        assertNotNull(server.at(uri(ON_ERROR)).apply(
                fail(new RuntimeException("test error"))));
    }

    // =========================================================
    // Parameterized ON_MESSAGE tests
    // Subclasses override provideMessageTestCases() (static) to supply their own cases.
    // =========================================================

    protected static Stream<Arguments> provideMessageTestCases() {
        return Stream.of(
                Arguments.of("passthrough-str", str("hello"), str("hello"))
        );
    }

    @ParameterizedTest(name = "[{index}] ON_MESSAGE: {0}")
    @MethodSource("provideMessageTestCases")
    public void testOnMessageHandler(final String description, final Obj input, final Obj expected) {
        final Obj result = server.at(uri(ON_MESSAGE)).apply(input);
        assertNotNull(result, "ON_MESSAGE should return a result for: " + description);
        assertOnMessageResult(description, input, expected, result);
    }

    /**
     * Override to customise ON_MESSAGE assertions. Default: equality check.
     */
    protected void assertOnMessageResult(final String desc, final Obj input, final Obj expected, final Obj actual) {
        assertEquals(expected, actual, "ON_MESSAGE mismatch for: " + desc);
    }

    // =========================================================
    // Lifecycle smoke test
    // =========================================================

    @Test
    public void testLifecycleFlow() {
        assertNotEquals(noobj(), server.at(uri(ON_MESSAGE)).apply(str("test message")));
    }
}
