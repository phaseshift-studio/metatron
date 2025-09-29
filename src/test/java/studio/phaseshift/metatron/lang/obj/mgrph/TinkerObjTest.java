package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.obj.InstSet;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.mtron.mtronFluent;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mgrph.mgrphFluent.StartLess.g;

public class TinkerObjTest extends MetatronTest {

    @Test
    public void testTinkerObj() {
        final MGraph graph = new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp"));
        Router.global().addSpace(graph);
        final GrphInstSet grphInstSet = new GrphInstSet(f("/grph/#"), f("/mnt/grph"));
        Router.global().addSpace(grphInstSet);
        grphInstSet.load();
        final mgrphFluent f = g(graph).V().out();


        Graphitty.log(this).info("graph: %s", graph);
        Graphitty.log(this).info("traversal: %s", f);
        f.forEach(v -> Graphitty.out(System.out, "%s", v));
    }
}
