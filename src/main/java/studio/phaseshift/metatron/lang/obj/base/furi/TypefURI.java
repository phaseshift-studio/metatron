package studio.phaseshift.metatron.lang.obj.base.furi;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.Router;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.NOOBJ_TID;

public final class TypefURI {

    public static final fURI DOM = fURI.of("dom");
    public static final fURI RNG = fURI.of("rng");

    private TypefURI() {
        // do nothing
    }

    public static fURI dom(final fURI furi) {
        return furi.queryValue(DOM,fURI.class,NOOBJ_TID);
    }

    public static fURI rng(final fURI furi) {
        return furi.queryValue(RNG,fURI.class,NOOBJ_TID);
    }

    public static fURI orNone(final fURI furi) {
        return null == furi ? NOOBJ_TID : furi;
    }

    public static Obj instDom(final Inst inst) {
        return Router.global().read(TypefURI.dom(inst.tid()));
    }

    public static Obj instRng(final Inst inst) {
        return Router.global().read(TypefURI.rng(inst.tid()));
    }

    public static boolean match(final fURI furi, final Inst inst) {
        return rng(furi).matches(dom(inst.tid()));
    }
}
