package studio.phaseshift.metatron.lang.db.grph.type;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;

import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.LABEL;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RVertex extends RElement {
    
    public RVertex(final Obj vertex) {
        super(vertex);
    }

    public static RVertex of(final Rec vertex) {
        return new RVertex(vertex);
    }

    public static Stream<RVertex> of(final Obj vertices) {
        return vertices.elements().map(Obj::<Rec>as).map(RVertex::new);
    }
    
    public String toString() {
        return "{{y}}v{{g}}[{{b}}" + this.vid() + "{{g}}]{{X}}"; 
    }
    
    /*@Override
    public void drop() {
        this.edges(Direction.OUT,lst()).map(e -> {
            e.vertices(Direction.IN).forEach(adj -> {
                adj.at(f(Direction.IN.name()).extend("/+/+").toUri()) {
                    
                }
            });
        })
    }*/


    public Stream<REdge> edges(final Direction direction, final Lst labels) {
        final boolean emptyLabels = labels.elements().noneMatch(e -> !e.isNoObj());
        return this.at(direction.name()).elements()
                .map(r -> ((Rel) r).second())
                .flatMap(Obj::stream)
                .flatMap(o -> o.apply(this).<Rec>as().stream())
                .filter(o -> emptyLabels || labels.elements().anyMatch(u -> o.<Rec>as().at(LABEL).uriValue().matches(u.uriValue())))
                .map(r -> new REdge(r.as()));
    }

    public Stream<RVertex> vertices(final Direction direction, final Lst labels) {
        return this.edges(direction, labels).flatMap(Obj::stream).map(Obj::<REdge>as).flatMap(r -> r.vertices(direction.opposite()));
    }

    @Override
    public RVertex clone() {
        return (RVertex) super.clone();
    }
}
