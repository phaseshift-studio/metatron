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

    public RVertex inVertex() {
        return this.vertices(Direction.IN).iterator().next();
    }

    public RVertex outVertex() {
        return this.vertices(Direction.OUT).iterator().next();
    }

    public String toString() {
        return "{{y}}e{{g}}[{{b}}" + this.outVertex() + "{{g}}={{b}}" + this.label() + "{{g}}=>" + this.inVertex() + "{{g}}]{{X}}";
    }

    @Override
    public REdge clone() {
        return (REdge) super.clone();
    }


}