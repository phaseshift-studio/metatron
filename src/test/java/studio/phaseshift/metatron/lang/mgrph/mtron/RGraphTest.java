package studio.phaseshift.metatron.lang.mgrph.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mgrph.mgrphInstSet;
import studio.phaseshift.metatron.lang.mgrph.tp.MGraph;
import studio.phaseshift.metatron.lang.mkv.mkvSpace;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.mtron.mtronParser;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.from_;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RGraphTest extends MetatronTest {
    @BeforeAll
    public static void begin() {
        MetatronTest.begin();
        mgrphInstSet.create().vid(f("/mnt/lang/mgraph"));
       // MGraph.of(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp"));
       // Router.writeToSpace("g", uri("/mnt/tp"));
        mkvSpace.of(f("/tp/#")).vid(f("/mnt/tp"));
    }


    @Test
    public void testVertexEdge() {
        Router.writeToSpace("/tp/g", uri("/tp/g"));
        Router.writeToSpace("/tp/v1", new RVertex(rec(
                uri("OUT"), rec(
                        uri("knows"), new REdge(rec(uri("OUT"), from_(uri("/tp/v1")), uri("IN"), from_(uri("/tp/v2"))), f("edge"), fURI.NULL),
                        uri("knows"), new REdge(rec(uri("OUT"),from_(uri("/tp/v1")), uri("IN"), from_(uri("/tp/v4"))), f("edge"), fURI.NULL))), f("vertex"), f("/tp/v1")));
        Router.writeToSpace("/tp/v2", new RVertex(rec(
                uri("IN"), rec(
                        uri("created"), new REdge(rec(uri("OUT"), from_(uri("/tp/v1")), uri("IN"), from_(uri("/tp/v2"))), f("edge"), fURI.NULL))), f("vertex"), f("tp/v2")));
        Router.writeToSpace("/tp/v4", new RVertex(rec(
                uri("OUT"), rec(
                        uri("created"), new REdge(rec(uri("OUT"), from_(uri("/tp/v4")), uri("IN"), from_(uri("/tp/v3"))), f("edge"), fURI.NULL),
                        uri("created"), new REdge(rec(uri("OUT"),from_(uri("/tp/v4")), uri("IN"), from_(uri("/tp/v5"))), f("edge"), fURI.NULL))), f("vertex"), f("/tp/v4")));
        Router.writeToSpace("/tp/v6", new RVertex(rec(
                uri("OUT"), rec(
                        uri("created"), new REdge(rec(uri("OUT"), from_(uri("/tp/v6")), uri("IN"), from_(uri("/tp/v3"))), f("edge"), fURI.NULL))), f("vertex"), f("/tp/v6")));
       
        //LOG.info("%s", mtronParser.eval("V(/tp/v1).outE(knows).inV()").toList());
        //LOG.info("vertex.outE() -> %s", Router.readFromSpace("/tp/V/v1").edges(Direction.OUT, uri("knows")).toList());
        //LOG.info("vertex.out() -> %s", v1.vertices(Direction.OUT, uri("knows")).toList());
    }

}
