package studio.phaseshift.metatron.lang.db.grph.mtron;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.db.grph.grphSpace;
import studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.Space.PATTERN;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RGraphTest extends MetatronTest {
    @BeforeAll
    public static void begin() {
        MetatronTest.begin();
        grphInstSet.create().vid(f("/mnt/lang/grph"));
        grphSpace space = new grphSpace(kvSpace.of(f("/g/#"), fURI.NULL), Map.of(
                uri(PATTERN), uri("/g/#"),
                uri("load"), uri("tinkerpop-modern")), f("/g/#"), f("/mnt/space/grph"));
        Router.global().addSpace(space);
        space.start();
       /* Router.readFromSpace(f("/grph/space/grph")).<Type>as().constructor().apply(
                rec(Map.of(
                        uri(PATTERN), uri("/g/#"), 
                        uri("load"), uri("tinkerpop-modern"),
                        uri("space"),kvSpace.of(f("/g/#"), fURI.NULL))).vid(f("/mnt/space/grph")));*/
        //grphSpace(new kvSpace(f("/g/#"), fURI.NULL), Map.of(uri(PATTERN), uri("/g/#"), uri("load"), uri("tinkerpop-modern")), f("/g/#"), f("/mnt/space/grph"));
        //Router.global().addSpace(space);
        // MGraph.of(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp"));
        // Router.writeToSpace("g", uri("/mnt/tp"));
        //kvSpace.of(f("/tp/#"), fURI.NULL).vid(f("/mnt/tp"));
    }

    @Test
    public void testBasic() {
        LOG.info(Router.readFromSpace("/mnt/space/grph/#"));
        LOG.info(Router.readFromSpace("/grph/#"));
        LOG.info(Router.readFromSpace("/g/V/#"));
    }

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/+.count()                                                              % 6",
            "*/g/V/+/OUT.count()                                                          % 6",
            /// ///////////////////////////////////////////////////////////////////////////////
            "*/g/V/1.out().count()                                                        % 3",
            "*/g/V/1.outE().count()                                                       % 3",
            "*/g/V/1.outE(knows).inV().count()                                            % 2",
            "*/g/V/1.outE().label()                                                       % {uri::created,uri{2}::knows}",
            "*/g/V/1.values()                                                             % {29,\"marko\"}",
            "*/g/V/1.properties()                                                         % [name=>\"marko\",age=>29]>-",
            "*/g/V/1.properties(name)                                                     % [name=>\"marko\"]>-",
            "*/g/V/1.properties(age)                                                      % [age=>29]>-",
            "*/g/V/1.values(name)                                                         % \"marko\"",
            /// ///////////////////////////////////////////////////////////////////////////////
            "*/g/V/+.out().count()                                                        % 6",
            "*/g/V/+.outE().count()                                                       % 6",
            "*/g/V/+.outE().inV().count()                                                 % 6",
            //"*/g/V/+.outE().bothV().count()                                               % 12",
            "*/g/V/+.out(knows).count()                                                   % 2",
            "*/g/V/+.outE(knows).inV().count()                                            % 2",
            // dummy without ending comma so it's easier to add more test cases
            "1.plus(1)                                                                    % 2"
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }


    @Test
    public void testBasicTraversals() {
        
       /* Router.writeToSpace("/tp/g", uri("/tp/g"));
        Router.writeToSpace("/tp/v1", new RVertex(rec(
                uri("OUT"), rec(
                        uri("knows"), new REdge(rec(uri("OUT"), from_(uri("/tp/v1")), uri("IN"), from_(uri("/tp/v2"))).tid(f("edge"))),
                        uri("knows"), new REdge(rec(uri("OUT"),from_(uri("/tp/v1")), uri("IN"), from_(uri("/tp/v4"))).tid(f("edge")))).tid(f("vertex")).vid(f("/tp/v1")))));
        Router.writeToSpace("/tp/v2", new RVertex(rec(
                uri("IN"), rec(
                        uri("created"), new REdge(rec(uri("OUT"), from_(uri("/tp/v1")), uri("IN"), from_(uri("/tp/v2"))).tid(f("edge")))).tid(f("vertex")).vid(f("tp/v2")))));
        Router.writeToSpace("/tp/v4", new RVertex(rec(
                uri("OUT"), rec(
                        uri("created"), new REdge(rec(uri("OUT"), from_(uri("/tp/v4")), uri("IN"), from_(uri("/tp/v3"))).tid, f("edge"), fURI.NULL),
                        uri("created"), new REdge(rec(uri("OUT"),from_(uri("/tp/v4")), uri("IN"), from_(uri("/tp/v5"))), f("edge"), fURI.NULL))), f("vertex"), f("/tp/v4")));
        Router.writeToSpace("/tp/v6", new RVertex(rec(
                uri("OUT"), rec(
                        uri("created"), new REdge(rec(uri("OUT"), from_(uri("/tp/v6")), uri("IN"), from_(uri("/tp/v3"))), f("edge"), fURI.NULL))), f("vertex"), f("/tp/v6")));*/

        //LOG.info("%s", mtronParser.eval("V(/tp/v1).outE(knows).inV()").toList());
        //LOG.info("vertex.outE() -> %s", Router.readFromSpace("/tp/V/v1").edges(Direction.OUT, uri("knows")).toList());
        //LOG.info("vertex.out() -> %s", v1.vertices(Direction.OUT, uri("knows")).toList());
    }

}
