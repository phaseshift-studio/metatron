package studio.phaseshift.metatron.lang.core.m.type.facade;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.MTronException;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FObj<O extends Obj> implements Obj {

    protected fURI vid = null;

    protected O base;

    public FObj(final O base) {
        this.base = base;
    }

    public O base() {
        return this.base;
    }

    @Override
    public <J> J jvm() {
        return this.base.jvm();
    }

    @Override
    public fURI tid() {
        return this.base.tid();
    }

    /*@Override
    public Obj tid(final fURI tid) {
        return this.base.tid(tid);
    }*/

    @Override
    public Obj vid(final fURI vid) {
        this.vid = vid;
        if (null != this.vid && !this.isType())
            Router.writeToSpace(this.vid, this);
        return this;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public FObj<O> clone() {
        try {
            final FObj<O> clone = (FObj<O>) super.clone();
            clone.base = (O) this.base.clone();
            return clone;
        } catch (final CloneNotSupportedException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid) {
        try {
            final FObj<O> clone = (FObj<O>) super.clone();
            clone.base = this.base.clone(jvm, this.base.tid(), null);
            clone.self(clone.base, tid, null);
            return clone.vid(vid).as();
            //   return (O) clone;
        } catch (final CloneNotSupportedException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public int hashCode() {
        return Obj.Helper.objHashCode(this);
    }

    @Override
    public String toString() {
        return Obj.Helper.objToString(this);
    }

    @Override
    public boolean equals(final Object rhs) {
        return Obj.Helper.objEquals(this, rhs);
    }

    @Override
    public FObj<O> self(final Object jvm, final fURI tid, final fURI vid) {
        this.base = jvm instanceof Obj ? (O) jvm : this.base.jvm(jvm);
        this.vid = vid;
        this.base.self(this.base.jvm(), tid, fURI.fnull);
        return this;
    }


}
