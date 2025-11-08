package studio.phaseshift.metatron.lang.mgrph.mtron;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.lang.mtron.type.Rel;
import studio.phaseshift.metatron.lang.mtron.type.impl.MRec;

import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.mgrph.mtron.TP3Translator.PROPS;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RElement extends MRec {

    public RElement(final Rec element, final fURI tid, final fURI vid) {
        super(element.recValue(), tid, vid);
    }

    public Stream<Rel> properties(final Obj keys) {
        return this.has(PROPS) ? this.at(PROPS).<Rec>as().elements() : Stream.empty();
    }

    public Stream<Obj> values(final Obj keys) {
        return this.properties(keys).map(Rel::second);
    }

}
