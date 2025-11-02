package studio.phaseshift.metatron.lang.msys;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.lang.mtron.type.impl.MInstSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class msysInstSet extends MInstSet {

    public static final fURI MSYS_TID = f("/msys");
    public static final fURI ROUTER_TID = MSYS_TID.extend("router");
    public static final fURI SPACE_TID = MSYS_TID.extend("space");

    public msysInstSet(final fURI vid) {
        super(MSYS_TID, vid);
    }

    public static msysInstSet create() {
        return new msysInstSet(fURI.NULL);
    }

    @Override
    public Set<Type> types() {
        return Set.of(T(ROUTER_TID), T(SPACE_TID));
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of());
    }
}
