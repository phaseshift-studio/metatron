package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.LinkedHashMap;

public class NullSpace implements Space {


    private final fURI vid;
    public NullSpace(final fURI vid) {
        this.vid = vid;
    }

    @Override
    public Object value() {
        return new LinkedHashMap<>();
    }

    @Override
    public fURI pattern() {
        return fURI.of("#");
    }

    @Override
    public Obj read(fURI vid) {
        return NoObj.single();
    }

    @Override
    public Obj write(fURI vid, Obj obj) {
        return obj;
    }

    @Override
    public void append(fURI addr, Obj... obj) {

    }

    @Override
    public fURI tid() {
        return fURI.of("#");
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public <O extends Obj> O clone(Object value, fURI tid, fURI vid) {
        return (O)this;
    }

    @Override
    public String toString() {
        return "nullspace";
    }
}
