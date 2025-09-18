package studio.phaseshift.metatron.space.mem;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Poly;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class StackSpace extends MSpace {

    private static final GraphittyLogger LOG = Graphitty.log(StackSpace.class);
    private final MemSpace root;
    private final LinkedList<Map<fURI, Obj>> stack = new LinkedList<>();

    public StackSpace(final fURI pattern, final fURI vid) {
        super(pattern, fURI.of("/mtron/space/stack"), vid);
        this.root = new MemSpace(this.pattern, fURI.of("/mtron/space/stack/root"));
    }

    @Override
    public Obj read(final fURI vid) {
        LOG.trace("searching for %s in %s [%s]", vid, this.stack, this.root.store);
        //if(vid.coefficientValue().isZero())
        //    return NoObj.single();
        for (final Map<fURI, Obj> layer : this.stack) {
            final Obj o = layer.get(vid.basePath());
            if (null != o)
                return o;
        }
        return this.root.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        this.stack.get(0).put(vid, obj);
        this.root.write(vid, obj);
        return obj;
    }

    public boolean pop() {
        final Map<fURI, Obj> frameMap = this.stack.pop();
        LOG.trace("popped from frame stack: %s [depth: %d]", frameMap, this.stack.size());
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
        this.stack.push(frameMap);
        LOG.trace("pushed to frame stack: %s [depth: %d]", frameMap, this.stack.size());
    }


    @Override
    public void append(final fURI addr, final Obj... obj) {

    }
}
