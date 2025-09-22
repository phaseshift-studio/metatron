package studio.phaseshift.metatron.space.mem;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class MSpace implements Space {

    protected final fURI pattern;
    protected final fURI tid;
    protected final fURI vid;
    private final Map<String, Object> statistics = new LinkedHashMap<>();

    public MSpace(final fURI pattern, final fURI tid, final fURI vid) {
        this.pattern = pattern;
        this.tid = tid.big();
        this.vid = vid;
        this.statistics.put("start_time", Instant.now().toString());
        if (null != this.vid && !this.vid.equals(fURI.NONE)) {
            Router.global().write(this.vid, this);
        }
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
    public String toString() {
        return Space.Helpers.spaceToString(this);
    }

    @Override
    public int hashCode() {
        return Space.Helpers.spaceHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Space.Helpers.spaceEquals(this, other);
    }

    @Override
    public void close() {

    }
}
