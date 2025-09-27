package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;

public interface Type extends Obj {

    @Override
    Type clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Obj value();

    @Override
    default Type dom() {
        return this;
    }

    @Override
    default Type rng() {
        return this;
    }

    @Override
    default Obj apply(final Obj obj) {
        if (null == this.value())
            return obj;
        else if (this.value().isCall()) {
            return obj.matches(this.value().apply(obj)) ? obj : NoObj.single();
        } else {
            return obj.matches(this.value()) ? obj : NoObj.single();
        }
    }
}
