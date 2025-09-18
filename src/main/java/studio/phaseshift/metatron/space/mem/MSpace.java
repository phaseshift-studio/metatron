package studio.phaseshift.metatron.space.mem;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public abstract class MSpace implements Space {

    protected static final GraphittyLogger LOG = Graphitty.log(MSpace.class);
    protected final fURI pattern;
    protected final fURI tid;
    protected final fURI vid;
    Map<String, Object> statistics = new LinkedHashMap<>();

    public MSpace(final fURI pattern, final fURI tid, final fURI vid) {
        this.pattern = pattern;
        this.tid = tid.big();
        this.vid = vid;
        this.statistics.put("start_time", Instant.now().toString());
        if (null != this.vid && !this.vid.equals(fURI.NONE))
            Router.global().write(this.vid, this);
    }

    @Override
    public Map value() {
        return this.statistics;
    }

    @Override
    public fURI pattern() {
        return this.pattern;
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
        LOG.warn("a self reference is returned as a space can not be cloned");
        return (O) this;
    }

    @Override
    public String toString() {
        return Space.Helpers.spaceToString(this);
    }

    @Override
    public int hashCode() {
        return Space.Helpers.spaceHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Space.Helpers.spaceEquals(this,other);
    }
}
