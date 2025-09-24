package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.monoid.Monoid;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Set;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.*;

public interface Type extends Obj {

    @Override
    Type clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Obj value();

    @Override
    default Obj apply(final Obj obj) {
      return this;
    }

    @Override
    default boolean matches(final Obj obj) {
        if(obj.tid().coefficientValue().isZero() && this.tid().coefficientValue().isZero())
            return true;
        if(!obj.tid().matches(this.tid()))
            return false;
        if (obj.isType())
            return true;
        final fURI base = obj.tid().basePath();
        final Set<fURI> BASE_TYPES = Set.of(
                BOOL_TID, INT_TID, REAL_TID,
                STR_TID, URI_TID, REL_TID,
                LST_TID, REC_TID, INST_TID,
                CODE_TID, OBJS_TID, NOOBJ_TID);
        if (!(obj instanceof Objs) && BASE_TYPES.contains(base)) {
            if (!((obj instanceof Bool && base.equals(BOOL_TID)) ||
                    (obj instanceof Int && base.equals(INT_TID)) ||
                    (obj instanceof Real && base.equals(REAL_TID)) ||
                    (obj instanceof Str && base.equals(STR_TID)) ||
                    (obj instanceof Uri && base.equals(URI_TID)) ||
                    (obj instanceof Lst && base.equals(LST_TID)) ||
                    (obj instanceof Rec && base.equals(REC_TID)) ||
                    (obj instanceof Rel && base.equals(REL_TID)) ||
                    (obj instanceof Inst && base.equals(INST_TID)) ||
                    //(obj instanceof NoObj && base.equals(fURI.NONE)) ||
                    (obj instanceof Code && base.equals(CODE_TID)) ||
                    (obj instanceof Objs && base.equals(OBJS_TID)))) {
                Graphitty.log(this).error("non-related base type %s to provided tid %s", obj.getClass().getCanonicalName(), obj.tid());
                return false;
            }
        } else if (obj.isCall()) {
            Obj typeObj = Router.global().read(this.tid());
            //System.out.println(Graphitty.string("HERE %s %s %s %s -- %s", this,obj,this.tid(),obj.tid(),typeObj));
            if (typeObj.isNoObj()) {
                if (!BOOTING)
                    Graphitty.log(this).warn("[{{r}}BAD{{/r}}  ] inst not in instruction set: %s", obj);
                return true;
            } else {
                Graphitty.log(this).info("[{{g}}GOOD{{/g}} ] inst in instruction set: %s",obj);
            }
            //System.out.println(Graphitty.string("%s",typeObj.tid()));
            // can be an Objs of all inst permutations
            return this.tid().basePath().matches(typeObj.tid().basePath());
        } else if (!(obj instanceof Inst) && !(obj instanceof Objs) && !this.tid().hasPattern() && !obj.tid().hasPattern() && !(obj instanceof Monad) && !(obj instanceof Monoid) && !(obj instanceof Log) && !(obj instanceof Space) && !(obj instanceof Router) && !(obj instanceof InstSet) && base.segments().size() > 1 && !base.equals(f("obj")) && !base.equals(fURI.NONE) && !base.equals(f("noobj")) && !base.head(2).equals(f("/mtron/inst"))) {
            Obj typeObj = Router.global().read(base);
            if (typeObj.isNoObj())
                return false;
            //throw MTronException.of("type %s is undefined", typeObj);
        }
        if (base.matches(this.tid().basePath()) && obj.tid().coefficientValue().within(this.tid().coefficientValue()))
            return null == this.value() || !obj.matches(this.value());
        return false;
        //throw MTronException.of("%s is not a type of %s",obj,this);
    }
}
