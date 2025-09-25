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
    default Obj apply(final Obj lhs) {
        return null == this.value() || this.value().isNoObj() ? lhs : this.value().apply(lhs);
    }

  /*  @Override
    default boolean matches(final Obj obj) {
        if (obj.tid().isZero() && this.tid().isZero())
            return true;
        if (!obj.tid().matches(this.tid()))
            return false;
        if (obj.isType())
            return true;
        final fURI base = obj.tid().basePath();
        if (!(obj instanceof Objs) && MInstSet.BASE_TYPES.contains(base)) {
            if (!((obj instanceof Bool && base.equals(BOOL_TID)) ||
                    (obj instanceof Int && base.equals(INT_TID)) ||
                    (obj instanceof Real && base.equals(REAL_TID)) ||
                    (obj instanceof Str && base.equals(STR_TID)) ||
                    (obj instanceof Uri && base.equals(URI_TID)) ||
                    (obj instanceof Lst && base.equals(LST_TID)) ||
                    (obj instanceof Rec && base.equals(REC_TID)) ||
                    (obj instanceof Rel && base.equals(REL_TID)) ||
                    (obj instanceof Inst && base.equals(INST_TID)) ||
                    (obj instanceof Code && base.equals(CODE_TID)))) {
                Graphitty.log(this)
                        .error("non-related base type %s to provided tid %s",
                                obj.getClass().getCanonicalName(),
                                obj.tid());
                return false;
            }
        } else if (obj.isCall()) {
            Obj typeObj = Router.global().read(this.tid());
            if (typeObj.isNoObj()) {
                if (!BOOTING)
                    Graphitty.log(this).warn("[{{r}}BAD{{/r}}  ] inst not in instruction set: %s", obj);
                return true;
            } else {
                Graphitty.log(this).info("[{{g}}GOOD{{/g}} ] inst in instruction set: %s", obj);
            }
            // can be an Objs of all inst permutations
            return obj.tid().rng().matches(typeObj.tid());
        } else if (!(obj instanceof Objs)) {
            final Obj typeObj = Router.global().read(base);
            if (typeObj.isNoObj())
                return false;
            //throw MTronException.of("type %s is undefined", typeObj);
        }
        return obj.tid().matches(this.tid()) && (null == this.value() || !obj.matches(this.value()));
        //throw MTronException.of("%s is not a type of %s",obj,this);
    }*/
}
