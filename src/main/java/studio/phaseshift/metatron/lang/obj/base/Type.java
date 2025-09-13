package studio.phaseshift.metatron.lang.obj.base;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;

public interface Type extends Obj {

    @Override
    Type clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Obj value();
}
