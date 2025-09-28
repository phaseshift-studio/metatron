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

    public static final fURI STACKSPACE_TID = MTRON_TID.extend("space/stack");
    public static final String ARG_PREFIX = "a";

    private final GraphittyLogger LOG = Graphitty.log(this);
    private final MemSpace root;
    private final LinkedList<Map<fURI, Obj>> stack = new LinkedList<>();

    public StackSpace(final fURI pattern, final fURI vid) {
        super(pattern, STACKSPACE_TID, vid);
        this.root = new MemSpace(this.pattern, STACKSPACE_TID.extend("default"));
    }

    @Override
    public Obj read(final fURI vid) {
        LOG.trace("reading %s in %s [{{y}}root{{/y}}: %s]", vid, this.stack, this.root.pathStore);
        // if(vid.coefficientValue().isZero())
        //    return NoObj.single();
        boolean isArg = vid.toString().matches("a\\d+"); // skip first encounter of list arg variable as it's a variable to grab the variable
        for (final Map<fURI, Obj> layer : this.stack) {
            final Obj o = layer.get(vid.basePath());
            if (null != o) {
                if (isArg) isArg = false;
                else return o;
            }
        }
        return this.root.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        LOG.trace("writing %s to %s in %s [{{y}}root{{/y}}: %s]", obj, vid, this.stack, this.root.pathStore);
        this.stack.get(0).put(vid, obj);
        this.root.write(vid, obj);
        return obj;
    }

    public boolean pop() {
        final Map<fURI, Obj> frameMap = this.stack.pop();
        LOG.trace("popped frame from stack stack: %s [{{y}}depth{{/y}}: %d]", frameMap, this.stack.size());
        return null != frameMap;
    }

    public void push(final Poly frame) {
        final Map<fURI, Obj> frameMap = new LinkedHashMap<>();
        if (frame.isRec())
            frame.recValue().forEach((key, value1) -> frameMap.put(key.uriValue(), value1));
        else {
            for (int i = 0; i < frame.lstValue().size(); i++) {
                frameMap.put(fURI.of(ARG_PREFIX + i), frame.lstValue().get(i));
            }
        }
        this.stack.push(frameMap);
        LOG.trace("pushed frame to stack: %s [depth: %d]", frameMap, this.stack.size());
    }


    @Override
    public void append(final fURI addr, final Obj... obj) {

    }
}
