package studio.phaseshift.metatron.lang.db.grph.type;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Objs;
import studio.phaseshift.metatron.lang.core.m.type.Rec;

import java.util.stream.Stream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class REdge extends RElement {

    protected REdge(final Obj edge) {
        super(edge);
    }

    public static REdge of(final Rec edge) {
        return edge instanceof REdge ? (REdge) edge : new REdge(edge);
    }

    public static Stream<REdge> of(final Obj edges) {
        return edges instanceof Objs ? edges.elements().map(Obj::<Rec>as).map(REdge::of) : Stream.of(REdge.of((Rec) edges));
    }

    public Stream<RVertex> vertices(final Direction direction) {
        final Stream<RVertex> out = direction.equals(Direction.OUT) || direction.equals(Direction.BOTH) ?
                this.at(Direction.OUT.name()).stream().map(o -> o.apply(this)).map(Obj::<Rec>as).map(RVertex::of) : Stream.empty();
        final Stream<RVertex> in = direction.equals(Direction.IN) || direction.equals(Direction.BOTH) ?
                this.at(Direction.IN.name()).stream().map(o -> o.apply(this)).map(Obj::<Rec>as).map(RVertex::of) : Stream.empty();
        return Stream.concat(out, in);
    }

    public RVertex inVertex() {
        return this.vertices(Direction.IN).iterator().next();
    }

    public RVertex outVertex() {
        return this.vertices(Direction.OUT).iterator().next();
    }

   /* public String toString() {
        return "{{y}}e{{g}}[{{b}}" + this.at(Direction.OUT.name()).vid() + "{{g}}={{b}}" + this.label() + "{{g}}=>" + this.inVertex().vid() + "{{g}}]{{X}}";
    }*/

    @Override
    public REdge clone() {
        return (REdge) super.clone();
    }


}