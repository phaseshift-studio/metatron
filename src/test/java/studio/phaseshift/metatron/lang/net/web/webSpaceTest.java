package studio.phaseshift.metatron.lang.net.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.mtronInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.mach.machInstSet;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.db.vec.vecInstSet;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.router.impl.MRouter;
import studio.phaseshift.metatron.space.SpaceTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class webSpaceTest {

    @BeforeAll
    public static void setup() {
        BootLoader.load(rec(uri("mode"), uri("testing")));
        mtronInstSet.create().vid(f("/mnt/lang/m"));
        webInstSet.create().vid(f("/mnt/lang/web"));
        kvSpace.of(f("/usr/#"), fURI.NULL).vid(f("/mnt/usr"));
        final webSpace web = webSpace.of(f("http://localhost:8777"), Map.of(uri("/"), uri("src/test/resources/web/")), f("http://#"), f("/usr/web"));
        Router.global().addSpace(web);
    }

    @AfterAll
    public static void shutdown() {
        Router.global().close();
        BootLoader.close();
    }

    @Test
    public void testIndexHTMLRedirect() {
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/index.html"));
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777/"));
        assertNotEquals(noobj(), Router.readFromSpace("http://localhost:8777"));
    }

    @Test
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
