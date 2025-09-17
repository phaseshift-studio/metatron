package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NullSpace implements Space, InstSet {


    private final fURI vid;
    public NullSpace(final fURI vid) {
        this.vid = vid;
    }

    @Override
    public Map value() {
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
    public Inst resolve(final Obj lhs, final Inst instAorB) {
        return NoObj.single();
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
    public NullSpace clone(Object value, fURI tid, fURI vid) {
        return this;
    }

    @Override
    public String toString() {
        return "nullspace";
    }
}
