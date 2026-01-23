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

package studio.phaseshift.metatron.lang.net.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.mTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class webSpaceTest extends mTest {

    public webSpaceTest() {
        webInstSet.create().vid(f("/sys/router/lang/web"));
        kvSpace.of(f("/usr/#"), fURI.fnull).vid(f("/sys/router/space/usr"));
        final webSpace web = webSpace.of(f("http://localhost:8777"), Map.of(uri("/"), uri("src/test/resources/web/")), f("http://#"), f("/usr/web"));
        Router.global().addSpace(web);
    }

    @Test
    @Disabled
    public void testIndexHTMLRedirect() {
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/index.html"));
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/"));
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777"));
    }

    @Test
    @Disabled
    public void testServerSideRecursion() {
        assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/a/b/c/text"));
        //assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/a/+/+/text"));
        assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/div/div/div/text"));
        //assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/index.html/html/body/div/+/+/text"));
        assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/html/body/a/b/c/text"));
        //assertEquals(str("a1.b1.c1.text"), Router.readFromSpace("http://localhost:8777/html/body/a/+/+/text"));
        assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/html/body/div/div/div/text"));
        //assertEquals(str("a2.b2.c2.text"), Router.readFromSpace("http://localhost:8777/html/body/div/+/+/text"));
    }

}
