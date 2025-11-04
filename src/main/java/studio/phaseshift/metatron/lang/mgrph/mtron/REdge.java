package studio.phaseshift.metatron.lang.mgrph.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;

import java.util.stream.Stream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class REdge extends RElement {

    public REdge(final Rec edge, final fURI tid, final fURI vid) {
        super(edge, tid, vid);
    }

    public REdge(final Rec edge) {
        super(edge, edge.tid(), edge.vid());
    }

    public static Stream<REdge> of(final Obj edges) {
        return edges.elements().map(Obj::<Rec>as).map(REdge::new);
    }

    public Stream<RVertex> vertices(final Direction direction) {
        return this.at(direction.name()).stream().map(o -> o.apply(this)).map(Obj::<Rec>as).map(RVertex::of);
    }

}