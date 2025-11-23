package studio.phaseshift.metatron.lang.db.grph.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.core.m.type.facade.FRec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;

import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.LABEL;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.PROPS;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RElement extends FRec {

    public RElement(final Obj element) {
        super((Rec) element);
    }

    public static RElement of(final Rec obj) {
        return (obj.tid().basePath().equals(EDGE_TID)) ? REdge.of(obj) : RVertex.of(obj);
    }

    public RElement property(final fURI key, final Obj value) {
        this.put(uri(PROPS), this.at(uri(PROPS)).orSupply(MRec::rec).put(uri(key), value), MUTABLE);
        return this;
    }

    public Stream<Rel> properties(final Obj keys) {
        boolean emptyKeys = keys.elements().noneMatch(e -> !e.isNoObj());
        return this.has(PROPS) ?
                this.at(PROPS).<Rec>as()
                        .elements()
                        .filter(o -> emptyKeys || keys.elements().anyMatch(u -> o.<Rel>as().first().uriValue().matches(u.uriValue()))) :
                Stream.empty();
    }

    public fURI label() {
        return this.at(LABEL).uriValue();
    }

    public Object id() {
        return this.vid();
    }

    public Stream<Obj> values(final Obj keys) {
        return this.properties(keys).map(Rel::second);
    }

    // abstract public void drop();

    @Override
    public RElement clone() {
        return (RElement) super.clone();
    }
}
