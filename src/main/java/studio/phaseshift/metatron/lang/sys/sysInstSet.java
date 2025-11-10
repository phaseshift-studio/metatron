package studio.phaseshift.metatron.lang.sys;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.net.remote.remoteSpace;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class sysInstSet extends MInstSet {

    public static final fURI MSYS_TID = f("/sys");
    public static final fURI ROUTER_TID = MSYS_TID.extend("router");
    public static final fURI SPACE_TID = MSYS_TID.extend("space");
    public static final fURI Q_TID = SPACE_TID.extend("q");

    public sysInstSet(final fURI vid) {
        super(MSYS_TID, vid);
    }

    public static sysInstSet create() {
        return new sysInstSet(fURI.NULL);
    }

    public static sysInstSet create(final fURI vid) {
        return new sysInstSet(vid);
    }

    @Override
    public Set<Type> types() {
        return Set.of(T(ROUTER_TID), T(SPACE_TID), remoteSpace.REMOTE_TYPE);
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of());
    }
}
