package studio.phaseshift.metatron.lang.mgrph.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;

import java.util.stream.Stream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RVertex extends RElement {

    public RVertex(final Rec vertex, final fURI tid, final fURI vid) {
        super(vertex, tid, vid);
    }

    public RVertex(final Rec vertex) {
        super(vertex, vertex.tid(), vertex.vid());
    }

    public static RVertex of(final Rec vertex) {
        return new RVertex(vertex);
    }

    public static Stream<RVertex> of(final Obj vertices) {
        return vertices.elements().map(Obj::<Rec>as).map(RVertex::new);
    }

    public Stream<REdge> edges(final Direction direction, final Obj labels) {
        return this.at(direction.name()).stream()
                .flatMap(o -> o.apply(this).stream())
                .flatMap(o -> labels.stream().flatMap(l -> o.<Rec>as().<Obj>at(l).stream()))
                .map(r -> new REdge(r.as()));
    }

    public Stream<RVertex> vertices(final Direction direction, final Obj labels) {
        return this.edges(direction, labels).flatMap(Obj::stream).map(Obj::<REdge>as).flatMap(r -> r.vertices(direction.opposite()));
    }
}
