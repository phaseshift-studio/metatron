package studio.phaseshift.metatron.lang.net.clstr;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.net.web.JSONTranslator;
import studio.phaseshift.metatron.lang.net.web.WebTranslator;
import studio.phaseshift.metatron.lang.net.web.webInstSet;
import studio.phaseshift.metatron.lang.net.web.webSpace;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class clstrInstSet extends MInstSet {

    public static final fURI MCLSTR_TID = f("/clstr");

    public clstrInstSet(final fURI vid) {
        super(MCLSTR_TID, vid);
    }

    public static clstrInstSet create() {
        return new clstrInstSet(fURI.NULL);
    }

    @Override
    public Set<Type> types() {
        return Set.of(clstrSpace.CLSTR_TYPE);
    }
}