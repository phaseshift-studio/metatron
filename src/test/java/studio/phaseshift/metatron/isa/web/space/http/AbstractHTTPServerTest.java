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

package studio.phaseshift.metatron.isa.web.space.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Abstract base for unit-testing HttpRec implementations.
 * Subclasses only need to provide {@link #createHandler(fURI)}.
 * The handler instance is available as {@link #handler} in all tests.
 */
public abstract class AbstractHTTPServerTest extends AbstractMetatronTest {

    protected Space testSpace;
    protected HttpRec handler;

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
     * The only abstract method — produce an HttpRec handler instance for the given vid.
     */
    protected abstract HttpRec createHandler(fURI vid);

    /**
     * Look up the registered Type for this handler from the Router.
     */
    protected Type handlerType() {
        return Router.global().read(handler.vid()).type();
    }

    @BeforeEach
    public void setupTestSpace() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.testSpace = memSpace.of(rec(
                uri(PATTERN), uri(testSpacePattern()),
                uri(ROUTE), rec()
        ), f("/sys/space/test/http/" + getClass().getSimpleName()));
        this.handler = createHandler(createTestVid());
    }

    @AfterEach
    public void teardownTestSpace() {
        if (this.testSpace != null) {
            Router.global().removeSpace(this.testSpace.vid());
            this.testSpace.close();
            this.testSpace = null;
        }
        this.handler = null;
    }

    // =========================================================
    // Type-level tests
    // =========================================================

    @Test
    public void testHandlerTypeIsDefined() {
        final Type type = handlerType();
        assertNotNull(type, "handler type should not be null");
        assertNotNull(type.tid(), "handler type should have a tid");
        assertNotNull(type.vid(), "handler type should have a vid");
    }

    @Test
    public void testHandlerTypeVidMatchesTid() {
        assertEquals(handler.tid(), handlerType().vid(),
                "type vid should equal the handler's tid");
    }

    @Test
    public void testHandlerTypeHasPredicate() {
        assertTrue(handlerType().hasPredicate(), "type predicate should not be noobj");
    }

    @Test
    public void testHandlerTypeHasConstructor() {
        final Type type = handlerType();
        assertNotNull(type.constructor(), "type should have a constructor");
        assertTrue(type.hasConstructor(), "constructor should not be noobj");
    }

    // =========================================================
    // Instance-level tests
    // =========================================================

    @Test
    public void testHandlerIsHttpRec() {
        assertInstanceOf(HttpRec.class, handler, "handler should be an HttpRec");
    }

    @Test
    public void testHandlerHasVid() {
        assertNotNull(handler.vid(), "handler should have a vid");
    }

    @Test
    public void testHandlerHasHttpIO() {
        assertNotNull(handler.getHttpIO(), "handler should have HttpIO");
        assertNotNull(handler.getHttpIO().input(), "should have an input type");
        assertNotNull(handler.getHttpIO().output(), "should have an output type");
    }
}
