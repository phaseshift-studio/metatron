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
import studio.phaseshift.metatron.isa.tble.tbleSpace;

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

    /** The owning tbleSpace — set only on the eager empty placeholder so its first read can
     *  lazily trigger the expensive table-mapping discovery.  The populated instset (built by
     *  {@code ensureTableMapping}) has no back-reference. */
    private final tbleSpace space;

    /**
     * Create a schema instset for a SQL database.
     *
     * @param schemaVid VID must be in the {@code /m/} namespace so writes route to
     *                  memSpace rather than back into the tbleSpace
     * @param types     table Types; each VID must be under schemaVid so checkPattern()
     *                  stores them in TYPE_TABLE locally
     */
    public SQLSchemaInstSet(final fURI schemaVid, final Collection<Type> types) {
        this(schemaVid, types, null);
    }

    /** The eager empty placeholder — carries a back-reference to its tbleSpace for lazy population. */
    public SQLSchemaInstSet(final fURI schemaVid, final Collection<Type> types, final tbleSpace space) {
        super(mutableMap(
                uri(PATTERN), uri(schemaVid.extend(ALL)),
                uri(TYPE), lst(types.stream().map(t -> (Obj) t).toList())
        ), INSTSET_TID, schemaVid);
        this.space = space;
    }

    @Override
    public Obj read(final fURI pattern) {
        // An empty placeholder means the table-mapping discovery hasn't run yet: trigger it
        // through the parent (which swaps in the populated instset), then delegate — but only
        // when the walk actually produced a different instset.  If it didn't (a prior partial
        // failure early-returns, or the database has no tables), fall through to the empty
        // result rather than re-triggering forever.  Keeps the expensive catalog walk lazy
        // while making */…/instset/+ reads self-sufficient.
        if (null != this.space && this.types().isEmpty()) {
            final SQLSchemaInstSet current = this.space.schemaInstset();
            if (current != this)
                return current.read(pattern);
        }
        return super.read(pattern);
    }

    @Override
    public void setup() {
        super.setup();
    }
}
