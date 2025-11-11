package studio.phaseshift.metatron.lang.core.m.inst;

import studio.phaseshift.metatron.furi.q.DocQ;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.mInstSet.CHOOSE_TID;
import static studio.phaseshift.metatron.lang.core.m.mInstSet.IS_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mDocs {

    private mDocs() {
        // do nothing
    }

    public static void attach(final Space space) {
        final DocQ docs = new DocQ();
        space.qs().register(docs);
        List.of(
                        DocQ.Doc.doc(Router.readFromSpace(CHOOSE_TID).as(), "any obj", "the split as an objs", Map.of(jnt(0), "the branches"), "a branching inst"),
                        DocQ.Doc.doc(Router.readFromSpace(IS_TID).as(), "any obj", "the lhs obj if arg is true", Map.of(jnt(0), "filter lhs if false"), "filters the lhs obj"))
                .forEach(doc -> {
                    docs.logger().info("documenting %s", doc.at("inst"));
                    docs.docSpace.put(doc.at("inst").tid(), doc);
                });

    }
}
