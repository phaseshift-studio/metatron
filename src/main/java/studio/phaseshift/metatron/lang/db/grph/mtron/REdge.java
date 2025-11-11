package studio.phaseshift.metatron.lang.db.grph.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;

import java.util.stream.Stream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class REdge extends RElement {
    
    public REdge(final Obj edge) {
        super(edge);
    }

    public static Stream<REdge> of(final Obj edges) {
        return edges.elements().map(Obj::<Rec>as).map(REdge::new);
    }

    public Stream<RVertex> vertices(final Direction direction) {
        return this.at(direction.name()).stream().map(o -> o.apply(this)).map(Obj::<Rec>as).map(RVertex::of);
    }

    @Override
    public REdge clone() {
        return (REdge) super.clone();
    }

}