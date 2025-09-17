package studio.phaseshift.metatron.space.mem;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Poly;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class StackSpace extends MObj implements Space {

    private static final GraphittyLogger LOG = Graphitty.log(StackSpace.class);
    private final fURI pattern;
    private final MemSpace root;

    public StackSpace(final fURI pattern, final fURI vid) {
        super(new LinkedList<>(), fURI.of("/mtron/space/stack"), vid);
        this.pattern = pattern;
        this.root = new MemSpace(this.pattern,fURI.of("/mtron/space/stack/root"));
    }


    @Override
    public LinkedList<Map<fURI, Obj>> value() {
        return (LinkedList<Map<fURI, Obj>>) this.value;
    }

    @Override
    public fURI pattern() {
        return this.pattern;
    }

    @Override
    public Obj read(final fURI vid) {
        LOG.trace("searching for %s in %s [%s]", vid, this.value, this.root.store);
        for (final Map<fURI, Obj> layer : this.value()) {
            final Obj o = layer.get(vid.basePath());
            if (null != o)
                return o;
        }
        return this.root.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        this.value().get(0).put(vid, obj);
        this.root.write(vid,obj);
        return obj;
    }

    public boolean pop() {
        final Map<fURI, Obj> frameMap = this.value().pop();
        LOG.trace("popped from frame stack: %s [depth: %d]", frameMap, this.value().size());
        return null != frameMap;
    }

    public void push(final Poly frame) {
        final Map<fURI, Obj> frameMap = new LinkedHashMap<>();
        if (frame.isRec())
            frame.recValue().forEach((key, value1) -> frameMap.put(key.uriValue(), value1));
        else {
            for (int i = 0; i < frame.lstValue().size(); i++) {
                frameMap.put(fURI.of("arg" + i), frame.lstValue().get(i));
            }
        }
        this.value().push(frameMap);
        LOG.trace("pushed to frame stack: %s [depth: %d]", frameMap, this.value().size());
    }


    @Override
    public void append(final fURI addr, final Obj... obj) {

    }

    @Override
    public fURI tid() {
        return this.tid;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public <O extends Obj> O clone(Object value, fURI tid, fURI vid) {
        return (O) this;
    }

    @Override
    public int hashCode() {
        return ObjUtil.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return ObjUtil.objEquals(this,other);
    }

    @Override
    public String toString() {
        return ObjUtil.objToString(this);
    }
}
