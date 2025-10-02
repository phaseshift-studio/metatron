package studio.phaseshift.metatron.lang.obj.mgrph;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mgrph.mgrphFluent.StartLess.g;

public class TinkerObjTest extends MetatronTest {

    @Test
    public void testTinkerObj() {
      /*  final MGraph graph = new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp"));
        Router.global().addSpace(graph);
        final GrphInstSet grphInstSet = new GrphInstSet(f("/grph/#"), f("/mnt/grph"));
        Router.global().addSpace(grphInstSet);
        grphInstSet.load();*/
        final MGraph graph = Router.global().read(f("/mnt/tp")).as();
        //final mgrphFluent f = g(graph).V().out().count();
      //  System.out.println(f.iterator().next());
       // g(graph).V().out().out().stream().forEach(v -> System.out.println(v.tid().coefficientValue()));

        Graphitty.log(this).info("graph: %s", graph);
       // Graphitty.log(this).info("traversal: %s", f);
      //  f.forEach(v -> Graphitty.out(System.out, "%s", v));
    }
}
