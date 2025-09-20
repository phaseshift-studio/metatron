package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.util.MTronException;

public interface Type extends Obj {

    @Override
    Type clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Obj value();

    @Override
    default Obj apply(final Obj obj) {
        if (obj.tid().basePath().matches(this.tid().basePath()) && obj.tid().coefficientValue().within(this.tid().coefficientValue()))
            if(null == this.value() || !obj.matches(this.value()))
                return obj;
        return NoObj.single();
        //throw MTronException.of("%s is not a type of %s",obj,this);
    }

}
