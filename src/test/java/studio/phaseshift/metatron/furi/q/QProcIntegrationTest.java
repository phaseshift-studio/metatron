/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class QProcIntegrationTest extends AbstractMetatronTest {

    private Space space;
    private static final fURI BASE = f("/t/qc");

    @BeforeEach
    public void setup() {
        space = memSpace.of(f(BASE + "/#"), f("/sys/space/qc"));
        space.addQ(QCollection.constQ());
        space.addQ(QCollection.typeQ());
        space.addQ(QCollection.incrQ());
        space.addQ(QCollection.mintQ());
        space.addQ(QCollection.mimeQ());
    }

    // ================================================================
    // constQ — parameterized: blocks mutation of constant URIs
    // ================================================================

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            "/c1 % \"immutable\" % \"mutated\" % blocks overwrite after constQ",
            "/c2 % \"first\"     % \"second\"  % preserves initial value",
    }, delimiter = '%')
    void testConstQ(final String uri, final String initial, final String mutate, final String desc) {
        final fURI target = f(BASE + uri);
        final Obj initialObj = ObjmtronSerializer.parse(initial);

        // Seed initial data directly (bypass qlessWrite)
        space.directWriter().apply(target, initialObj);
        checkEquality(LOG, initialObj, space.read(target), true);

        // Register as constant (preWrite stores URI, qlessWrite blocks this write)
        space.write(f(BASE + uri + "?constq"), str("ignored"));

        // Attempt overwrite — qlessWrite returns fail
        final Obj blocked = space.write(target, ObjmtronSerializer.parse(mutate));
        assertTrue(blocked.isFail(), desc + ": mutation should be blocked");

        // Value must remain unchanged
        checkEquality(LOG, initialObj, space.read(target), true);
    }

    // ================================================================
    // typeQ — parameterized: enforces type constraints on writes
    // ================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "/t1 % int::T     % 42                  % false % 42               % int accepted under int::T",
            "/t2 % int::T     % \"hi\"              % true  % {0}              % str rejected under int::T",
            "/t3 % str::T     % \"hi\"              % false % \"hi\"           % str accepted under str::T",
            "/t4 % str::T     % 99                  % true  % {0}              % int rejected under str::T",
            "/t5 % bool::T    % true                % false % true             % bool accepted under bool::T",
            "/t6 % bool::T    % 1                   % true  % {0}              % int rejected under bool::T",
            "/t7 % bool::T    % {true,false}        % true  % {0}              % bool{2} rejected under bool::T",
            "/t8 % bool{2}::T % {true,false}        % false % {true,false}     % bool{2} accepted under bool::T",
    }, delimiter = '%')
    void testTypeQ(final String uri, final String typeConstraint, final String writeVal,
                   final boolean expectFail, final String expectedRead, final String desc) {
        final fURI target = f(BASE + uri);

        // Seed initial value directly
        space.directWriter().apply(target, jnt(0));

        // Declare type constraint (preWrite stores in typeSpace)
        space.write(f(BASE + uri + "?T"), ObjmtronSerializer.parse(typeConstraint));

        // Write data — qlessWrite checks type, accepts or rejects
        final Obj dataObj = ObjmtronSerializer.parse(writeVal);
        final Obj writeResult = space.write(target, dataObj);

        if (expectFail) {
            assertTrue(writeResult.isFail(), desc + ": write should be rejected");
        } else {
            assertFalse(writeResult.isFail(), desc + ": write should succeed");
            checkEquality(LOG, ObjmtronSerializer.parse(expectedRead), space.read(target), true);
        }
    }

    // ================================================================
    // mimeQ — parameterized: MIME serialization on postRead
    // ================================================================

    @ParameterizedTest
    @CsvSource(value = {
            //"/m1 % text/plain        % \"hello\"    % str serialized as plain text",
            //"/m2 % text/plain        % 42           % jnt serialized as plain text",
            //"/m3 % text/plain        % true         % bool serialized as plain text",
            //"/m4 % application/json  % \"hello\"    % str serialized as JSON",
            "/m5 % application/json  % 42           % jnt serialized as JSON",
            "/m6 % application/json  % true         % bool serialized as JSON",
            "/m7 % application/json  % [a=>1,b=>2]  % rec serialized as JSON",
            "/m8 % application/mtron % \"hello\"    % str serialized as mtron",
            "/m9 % application/mtron % 42           % jnt serialized as mtron",
            "/m10 % application/mtron % true        % bool serialized as mtron",
    }, delimiter = '%')
    void testMimeQ(final String uri, final String contentType, final String writeExpr,
                   final String desc) {
        final MIME.MIMEType expectedContentType = MIME.MIMEType.of(contentType);
        final Obj mtronObj = ObjmtronSerializer.parse(writeExpr);
        assertEquals(mtronObj, space.write(f(BASE + uri), mtronObj));
        assertEquals(mtronObj, space.read(f(BASE + uri)));
        assertFalse(mtronObj.isNoObj());
        final Obj result = space.read(f(BASE + uri + "?mimeq=" + contentType));
        assertNotEquals(noobj(), result, desc + ": mimeQ should produce a result");
        assertTrue(result.isStr(), desc + ": mimeQ output should be a string");
        assertNotNull(result.strValue(), desc + ": mimeQ output should have content");
        LOG.warn("result post mime conversion: %s", result);
        final Obj reparsed = expectedContentType.serializer().inputBytes(ByteBuffer.wrap(result.strValue().getBytes()));
        assertEquals(mtronObj, reparsed);
    }

    // ================================================================
    // incrQ — parameterized: auto-incrementing counter in path
    // ================================================================

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            "/i1 % /counter/#/data % first  % single-increment counter",
            "/i2 % /idx/#/v        % one    % simple counter",
            "/i3 % /a/#/data       % two    % mid-path counter",
    }, delimiter = '%')
    void testIncrQ(final String uri, final String pattern, final String value, final String desc) {
        final String expr1 = BASE + uri + "?incrq=" + pattern + " -> " + value;
        final Obj r1 = ObjmtronSerializer.parse(expr1).apply();
        assertNotNull(r1.vid(), desc + ": should have a VID");
        final String prefix = pattern.contains("/#") ? pattern.split("/#")[0] : pattern;
        assertTrue(r1.vid().toString().contains(prefix), desc + ": VID should contain counter prefix " + prefix);

        // Second write produces a different path
        final Obj r2 = ObjmtronSerializer.parse(expr1).apply();
        assertNotNull(r2.vid(), desc + ": second write should also have VID");
        assertNotEquals(r1.vid(), r2.vid(), desc + ": paths should increment");
    }

    // ================================================================
    // mintQ — standalone: UUID vid minting on write
    // ================================================================

    @Test
    public void testMintQ() {
        final fURI target = f(BASE + "/mint?mintq");
        final Obj written = Router.writeToSpace(target, str("hello"));
        assertNotEquals(noobj(), written);
        assertNotNull(written.vid());
        assertTrue(written.vid().toString().startsWith(BASE + "/mint/"),
                "VID should be under /mint/ with a minted UUID path");
    }

    // ================================================================
    // subq concurrency — standalone: concurrent writes to subscribed patterns
    // ================================================================

    @Test
    public void testSubQConcurrency() {
        space.addQ(QCollection.subq());
        final int numSubscriptions = 5;

        for (int i = 0; i < numSubscriptions; i++) {
            final String expr = BASE + "/sub" + i + "?subq -> sub::[on_recv=>>>1.to($$/callback/" + i + ")]";
            ObjmtronSerializer.parse(expr).apply();
        }

        for (int i = 0; i < numSubscriptions; i++)
            for (int j = 0; j < 3; j++)
                Router.writeToSpace(BASE + "/sub" + i, jnt(j));

        CommonUtil.sleepThread(500);

        for (int i = 0; i < numSubscriptions; i++) {
            final Obj sub = Router.readFromSpace(BASE + "/sub" + i + "?subq");
            assertNotEquals(noobj(), sub, "subscription " + i + " should still exist");
        }
    }
}
