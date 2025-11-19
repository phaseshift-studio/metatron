package studio.phaseshift.metatron.lang.db.grph.mtron;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.db.grph.grphSpace;
import studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.Tokens.PATTERN;
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
        Router.global().put(uri("primary"), uri("/grph"));
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
            "*/g/V/+/OUT>>.count()                                                        % 6",
            "*/g/V/+/IN>>.count()                                                         % 6",
            "*/g/V/+/PROPS>>.count()                                                      % 12",
            /// ///////////////////////////////////////////////////////////////////////////////
            "*/g/V/1/OUT>>.count()                                                        % 3",
            "*/g/V/1/IN>>.count()                                                         % 0",
            "*/g/V/1/PROPS>>.count()                                                      % 2",
            "*/g/V/1.out().count()                                                        % 3",
            "*/g/V/1.outE().count()                                                       % 3",
            "*/g/V/1.in().count()                                                         % 0",
            "*/g/V/1.inE().count()                                                        % 0",
            //"*/g/V/1.-<[_.outE(),_.inE()]>-.count()                                     % 3",
            // "*/g/V/1.both().count()                                                     % 3",
            "*/g/V/1.bothE().count()                                                      % 3",
            "*/g/V/1.bothE().bothV().count()                                              % 6",
            "*/g/V/1.outE(knows).inV().count()                                            % 2",
            "*/g/V/1.outE().label()                                                       % {uri::created,uri{2}::knows}",
            "*/g/V/1.values()                                                             % {29,\"marko\"}",
            "*/g/V/1.properties()                                                         % [name=>\"marko\",age=>29]>-",
            "*/g/V/1.properties(name)                                                     % [name=>\"marko\"]>-",
            "*/g/V/1.properties(age)                                                      % [age=>29]>-",
            "*/g/V/1.values(name)                                                         % \"marko\"",
            "*/g/V/1.label()                                                              % person",
            /// ///////////////////////////////////////////////////////////////////////////////
            "*/g/V/+.label()                                                              % {person,person,person,person,software,software}",
           // "*/g/V/+.label().group([_=>_])==[_=>count()]                                  % [person=>4,software=>2]",
            /// ///////////////////////////////////////////////////////////////////////////////
            "*/g/V/4.values(name)                                                         % \"josh\"",
            "*/g/V/4.out().count()                                                        % 2",
            "*/g/V/4.outE().count()                                                       % 2",
            "*/g/V/4.in().count()                                                         % 1",
            "*/g/V/4.inE().count()                                                        % 1",
            //  "*/g/V/4.both().count()                                                       % 3",
            "*/g/V/4.bothE().count()                                                      % 3",
            "*/g/V/4.bothE().inV().count()                                                % 3",
            "*/g/V/4.bothE().outV().count()                                               % 3",
            "*/g/V/4.bothE().bothV().count()                                              % 6",
            /// ///////////////////////////////////////////////////////////////////////////////
            "*/g/V/+.out().count()                                                        % 6",
            "*/g/V/+.outE().count()                                                       % 6",
            "*/g/V/+.outE().inV().count()                                                 % 6",
            //  "*/g/V/+.-<[out()>-{,},in()>-{,}]>-.count()                                % 12",
            // "*/g/V/+.both().count()                                                       % 12",
            "*/g/V/+.bothE().count()                                                      % 12",
           // "*/g/V/+.bothE().inV().count()                                                      % 12",
            "*/g/V/+.bothE().outV().count()                                                      % 12",
           // "*/g/V/+.bothE().bothV().count()                                                      % 24",
            "*/g/V/+.out(knows).count()                                                   % 2",
            "*/g/V/+.outE(knows).inV().count()                                            % 2",
            /// ///////////////////////////////////////////////////////////////////////////////
            "*/g/V/1.out().out().values(name)                                             % {\"lop\",\"ripple\"}",
            "*/g/V/1.out().out().values(lang)                                             % str{2}::\"java\"",
            //  "*/g/V/+.out().out().values(name)                                             % {\"lop\",\"ripple\"}",
            //  "*/g/V/+.out().out().values(lang)                                             % str{2}::\"java\"",
            /// ///////////////////////////////////////////////////////////////////////////////
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
