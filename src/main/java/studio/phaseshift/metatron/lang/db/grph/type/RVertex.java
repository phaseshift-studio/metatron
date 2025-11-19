package studio.phaseshift.metatron.lang.db.grph.type;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.sys.router.Router;

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
        return vertex instanceof RVertex ? (RVertex) vertex : new RVertex(vertex.vid(null)).vid(vertex.vid()).as();
    }

    public static Stream<RVertex> of(final Obj vertices) {
        return vertices.elements().map(Obj::<Rec>as).map(RVertex::of);
    }

    public String toString() {
        return "{{b}}v{{g}}" + (this.tid().cV().isOne() ? "" : ("{{{y}}" + this.tid().c() + "{{g}}}")) + "[{{y}}" + this.vid() + "{{g}}]{{X}}";
    }

    public Stream<REdge> edges(final Direction direction, final Lst labels) {
        final boolean emptyLabels = labels.elements().noneMatch(e -> !e.isNoObj());
        final Stream<REdge> inE = direction.equals(Direction.IN) || direction.equals(Direction.BOTH) ?
                this.at(Direction.IN.name()).elements()
                        .map(r -> ((Rel) r).second())
                        .flatMap(Obj::stream)
                        .filter(o -> emptyLabels || labels.elements().anyMatch(u -> o.<Rec>as().at(LABEL).uriValue().matches(u.uriValue())))
                        .map(r -> REdge.of(r.as())) : Stream.of();
        final Stream<REdge> outE = direction.equals(Direction.OUT) || direction.equals(Direction.BOTH) ?
                this.at(Direction.OUT.name()).elements()
                        .map(r -> ((Rel) r).second())
                        .flatMap(Obj::stream)
                        .filter(o -> emptyLabels || labels.elements().anyMatch(u -> o.<Rec>as().at(LABEL).uriValue().matches(u.uriValue())))
                        .map(r -> REdge.of(r.as())) : Stream.of();
        return Stream.concat(inE, outE);
    }

    public Stream<RVertex> vertices(final Direction direction, final Lst labels) {
        return this.edges(direction, labels)
                .flatMap(Obj::stream)
                .map(Obj::<REdge>as)
                .flatMap(e -> e.vertices(direction.opposite()).map(Obj::as));
    }

    @Override
    public RVertex clone() {
        return (RVertex) super.clone();
    }
}
