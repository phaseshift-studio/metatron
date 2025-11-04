package studio.phaseshift.metatron.lang.mgrph.mtron;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Objs;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.lang.mtron.type.impl.MRec;

import java.util.Map;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RElement extends MRec {
    
    public RElement(final Rec element, final fURI tid, final fURI vid) {
        super(element.recValue(),tid,vid);
    }
    
}
