/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.grph.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.isa.m.mInstSet.MTRON_TID;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class grphSpace<S> extends MSpace<S> {

    public static final fURI GRPH_SPACE_TID = MTRON_TID.extend("grph").extend("space/grph");

    public static final Rec GRPH_CONFIG = rec(
            uri(PATTERN), URI_TYPE,
            uri(REWRITE), REL_TYPE);
    /* uri(SCHEME).maybe(), rec(
             uri(VRTX_TID), VRTX_TYPE,
             uri(EDGE_TID), EDGE_TYPE));*/
    public static final Type GRPH_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(GRPH_SPACE_TID).create();


    protected grphSpace(final S nativeSpace, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(nativeSpace, config, tid, vid);
    }

    @Override
    public void close() {
        try {
            CommonUtil.close(this.sjvm);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /*public static <S extends Space> grphSpace<S> of(final Rec config, final fURI vid) {
        return new grphSpace<>(config, vid);
    }*/
}
