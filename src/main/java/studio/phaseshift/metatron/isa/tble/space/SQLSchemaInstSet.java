/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.tble.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Collection;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A runtime-discovered SQL schema as a minimal instset.
 *
 * Lives at {@code /m/tble/space/schema/{dbName}} — in the {@code /m/} system namespace,
 * backed by memSpace so it never routes back into the tbleSpace's data pattern.
 *
 * Uses the setup() model (jvm map pre-loaded with types) so that
 * AbstractInstSet does not call types() during construction before the
 * field is initialized.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SQLSchemaInstSet extends AbstractInstSet {

    /**
     * Create a schema instset for a SQL database.
     *
     * @param schemaVid VID must be in the {@code /m/} namespace so writes route to
     *                  memSpace rather than back into the tbleSpace
     * @param types     table Types; each VID must be under schemaVid so checkPattern()
     *                  stores them in TYPE_TABLE locally
     */
    public SQLSchemaInstSet(final fURI schemaVid, final Collection<Type> types) {
        super(mutableMap(
                uri(PATTERN), uri(schemaVid.extend(ALL)),
                uri(TYPE), lst(types.stream().map(t -> (Obj) t).toList())
        ), INSTSET_TID, schemaVid);
    }

    @Override
    public void setup() {
        super.setup();
    }
}
