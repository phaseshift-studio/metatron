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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import studio.phaseshift.metatron.SkipInheritedTests;
import studio.phaseshift.metatron.SkipInheritedTestsExtension;
import studio.phaseshift.metatron.TestTag;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ExtendWith(SkipInheritedTestsExtension.class)
@SkipInheritedTests(tags = {
        TestTag.CRUD,        // Skip all CRUD tests
        TestTag.BOUNDARY,    // Skip all boundary value tests
        TestTag.TYPE,        // Skip all type preservation tests
        TestTag.NESTED,      // Skip all nested structure tests
        TestTag.LIST,        // Skip all list handling tests
        TestTag.SPECIAL      // Skip all special value tests
}, include = {
        //   "testMonoReadWrite"  // Include this CRUD test even though CRUD tag is skipped
})
public class httpSpaceTest extends AbstractSpaceTest {
    private static final String BASE_URL = "http://localhost:" + generatePort();

    /**
     * Isolated copy of test resources — writes land here, not in src/test/resources/.
     */
    private static final Path WEB_DATA_DIR = Path.of("target", "test-web-data");

    private static httpSpace staticHttpSpace;

    @BeforeAll
    public static void setupSpaces() {
        InstSet.importInstSet(WEB_ISA_TID);
        fsSpace.of(FileSystems.getDefault(), rec(
                uri(QPROC), lst(QCollection.mimeQ()),
                uri(PATTERN), uri("local:#"),
                uri(ROUTE), rec(uri("local:"), uri(WEB_DATA_DIR + "/"))), f("/sys/space/fs"));
        staticHttpSpace = httpSpace.of(rec(
                uri(QPROC), lst(QCollection.mimeQ()),
                uri(HOST), uri(BASE_URL),
                uri(PATTERN), uri("http://#"),
                uri(ROUTE), rec(uri("/"), uri("local:web"))), f("/sys/space/web"));
    }

    @BeforeEach
    public void copyWebData() throws Exception {
        final File src = new File("src/test/resources/web");
        final File dest = new File(WEB_DATA_DIR.toFile(), "web");
        if (dest.exists())
            CommonUtil.deleteDirectory(dest.toPath());
        dest.mkdirs();
        CommonUtil.copyDirectory(src.toPath(), dest.toPath());
    }

    public httpSpaceTest() {
        super(f(BASE_URL), () -> staticHttpSpace);
    }

    @Override
    @AfterEach
    protected void stop() {
        // Don't close the static httpSpace between tests — it's shared
    }

    @Override
    public void testPolyReadWrite(final String writeExpression, final String mutationExpression, final String readExpression, final String expectedExpression) {
        LOG.warn("testPolyReadWrite (skipped): %s => %s => %s => %s", writeExpression, mutationExpression, readExpression, expectedExpression);
    }

    @Override
    public fURI getTestDataUriPrefix() {
        return f(BASE_URL + "/test/");
    }


    @Test
    public void testResourceAccess() {
        // Read from fsSpace with ?mimeq=application/x-mtron to get rec::T DOM tree.
        // The rec structure from Jsoup.parse() may differ slightly from the old
        // round-tripped HTML path; we verify the key text nodes are reachable.
        final String src = "local:web/index.html?mimeq=application/x-mtron";
        final Rec base = Router.readFromSpace(src).asRec();
        assertNotNull(base, "index.html rec should not be null");
        assertFalse(base.at(f("html")).isNoObj(), "rec should have html key");
        // Verify the text content is somewhere in the rec tree
        final String baseStr = base.toString();
        assertTrue(baseStr.contains("a1.b1.c1.text"), "should contain a1.b1.c1.text");
        assertTrue(baseStr.contains("a2.b2.c2.text"), "should contain a2.b2.c2.text");
    }

    @Test
    public void testJsonResourceFields() {
        // Read from fsSpace with ?mimeq=application/x-mtron to get rec::T
        final Rec base = Router.readFromSpace("local:web/test.json?mimeq=application/x-mtron").asRec();
        assertNotNull(base, "test.json should be a rec");
        assertEquals((Obj) ObjmtronSerializer.parse("world"), base.at(f("hello")), "hello");
        assertEquals((Obj) ObjmtronSerializer.parse("42"), base.at(f("number")), "number");
        assertEquals((Obj) ObjmtronSerializer.parse("true"), base.at(f("active")), "active");
        assertTrue(base.at(f("nothing")).isNoObj(), "nothing");
        assertEquals((Obj) ObjmtronSerializer.parse("<2024-06-01T12:00:00Z>"), base.at(f("meta/created")), "meta/created");
        assertEquals((Obj) ObjmtronSerializer.parse("99.5"), base.at(f("meta/details/score")), "meta/details/score");
        assertEquals((Obj) ObjmtronSerializer.parse("false"), base.at(f("meta/details/valid")), "meta/details/valid");
        assertEquals((Obj) ObjmtronSerializer.parse("1"), base.at(f("items/0")), "items/0");
        assertEquals((Obj) ObjmtronSerializer.parse("two"), base.at(f("items/1")), "items/1");
        assertEquals((Obj) ObjmtronSerializer.parse("false"), base.at(f("items/2")), "items/2");
        // items/3 is out-of-bounds; returns noobj (not a fail in this path)
        assertTrue(base.at(f("items/3")).isNoObj(), "items/3 should be noobj");
        assertEquals((Obj) ObjmtronSerializer.parse("value"), base.at(f("items/4/deep")), "items/4/deep");
        assertEquals((Obj) ObjmtronSerializer.parse("alpha"), base.at(f("meta/tags/0")), "meta/tags/0");
        assertEquals((Obj) ObjmtronSerializer.parse("beta"), base.at(f("meta/tags/1")), "meta/tags/1");
    }

    @Test
    @Override
    public void testMonoRootlessReadWrites() {
        // do nothing
    }


    @Test
    @Override
    public void testMonoUpdate() {
        // do nothing
    }

    @Test
    public void testIndexHTMLRedirect() {
        // Default read returns typed html::"..."
        final Obj idx = Router.readFromSpace(BASE_URL + "/index.html");
        assertNotEquals(noobj(), idx);
        assertTrue(idx.test(HTML_TYPE), "should be html::T");
        assertTrue(idx.isStr(), "html::T refines str::T");
        assertEquals(HTML_TID, idx.tid().basePath(), "TID should be HTML_TID");

        final Obj root = Router.readFromSpace(BASE_URL + "/");
        assertNotEquals(noobj(), root);

        final Obj bare = Router.readFromSpace(BASE_URL);
        assertNotEquals(noobj(), bare);
    }

    @Test
    public void testServerSideRecursion() {
        // Default httpSpace read returns typed html::"..."
        assertNotEquals(noobj(), Router.readFromSpace(BASE_URL + "/#/"));
        assertNotEquals(noobj(), Router.readFromSpace(BASE_URL + "/index.html"));
        assertTrue(Router.readFromSpace(BASE_URL + "/index.html").test(HTML_TYPE));
        assertEquals(HTML_TID, Router.readFromSpace(BASE_URL + "/index.html").tid().basePath());
        assertEquals(HTML_TID, Router.readFromSpace(BASE_URL).tid().basePath());
        assertTrue(Router.readFromSpace(BASE_URL).test(HTML_TYPE));

        // Server-side recursion: use fsSpace directly with ?mimeq=application/x-mtron
        // to get rec::T DOM tree and verify text nodes are reachable
        final Rec idxMRec = Router.readFromSpace("local:web/index.html?mimeq=application/x-mtron").asRec();
        assertNotNull(idxMRec);
        final String idxStr = idxMRec.toString();
        assertTrue(idxStr.contains("a1.b1.c1.text"), "rec should contain a1.b1.c1.text");
        assertTrue(idxStr.contains("a2.b2.c2.text"), "rec should contain a2.b2.c2.text");
    }

    @Test
    public void testFsSpaceDirectRead() {
        final Obj direct = Router.readFromSpace("local:web/test.txt");
        assertNotEquals(noobj(), direct, "direct fsSpace read should not be noobj");
        assertTrue(direct.isStr(), "test.txt should be a string, got: " + direct.tid());
    }

    @Test
    public void testFsSpaceDirectReadTypedStr() {
        // .html files default to typed html::T (str refinement)
        final Obj direct = Router.readFromSpace("local:web/index.html");
        assertNotEquals(noobj(), direct, "direct fsSpace read should not be noobj");
        assertTrue(direct.isStr(), "html should be a str, got: " + direct.tid());
        assertTrue(direct.test(HTML_TYPE), "should be html::T");
        assertEquals(HTML_TID, direct.tid().basePath());
    }
}
