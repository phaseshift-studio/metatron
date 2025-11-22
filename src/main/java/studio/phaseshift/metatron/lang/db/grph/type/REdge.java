package studio.phaseshift.metatron.lang.db.grph.type;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Objs;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Uri;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.lang.db.grph.type.mtp3.mGraph;

import java.util.Arrays;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.auto;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.LABEL;
import static studio.phaseshift.metatron.lang.db.grph.type.mtp3.mGraph.PROPS;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class REdge extends RElement {

    protected REdge(final Obj edge) {
        super(edge);
    }

    public static REdge of(final Rec edge) {
        return edge instanceof REdge ? (REdge) edge : new REdge(edge.vid(null)).vid(edge.vid()).as();
    }

    public static REdge of(final String label, final fURI outVertex, final fURI inVertex, final Object... keyValues) {
        return REdge.of(rec(uri(LABEL), uri(label), uri(Direction.OUT.name()), auto(outVertex), uri(Direction.IN.name()), auto(inVertex)));
    }

    public static Stream<REdge> of(final Obj edges) {
        return edges instanceof Objs ? edges.elements().map(Obj::<Rec>as).map(REdge::of) : Stream.of(REdge.of((Rec) edges));
    }

    public Stream<RVertex> vertices(final Direction direction) {
        final Stream<RVertex> out = direction.equals(Direction.OUT) || direction.equals(Direction.BOTH) ?
                this.at(Direction.OUT.name()).stream().map(Obj::<Rec>as).map(RVertex::of) : Stream.empty();
        final Stream<RVertex> in = direction.equals(Direction.IN) || direction.equals(Direction.BOTH) ?
                this.at(Direction.IN.name()).stream().map(Obj::<Rec>as).map(RVertex::of) : Stream.empty();
        return Stream.concat(out, in);
    }

    public String toString() {
        return "{{b}}e{{g}}" + (this.tid().cV().isOne() ? "" : ("{{{y}}" + this.tid().c() + "{{g}}}")) + "[{{b}}" + this.jvm().get(uri(Direction.OUT.name())) + "{{g}}={{b}}" + this.label() + "{{g}}=>" + this.jvm().get(uri(Direction.IN.name())) + "{{g}}]{{X}}";
    }

    @Override
    public REdge clone() {
        return (REdge) super.clone();
    }


}