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

package studio.phaseshift.metatron.isa.grph.space.schema;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/grph/inst/schema/modern")
public class modernSchema extends AbstractInstSet {

    public static final fURI MODERN_SCHEMA_TID = grphInstSet.GRPH_INST_TID.extend("schema").extend("modern");
    public static final fURI PERSON_TID = MODERN_SCHEMA_TID.extend("person");
    public static final fURI SOFTWARE_TID = MODERN_SCHEMA_TID.extend("software");
    public static final fURI KNOWS_TID = MODERN_SCHEMA_TID.extend("knows");
    public static final fURI CREATED_TID = MODERN_SCHEMA_TID.extend("created");

    public static final Type MODERN_SCHEMA_TYPE = T(MODERN_SCHEMA_TID);

    public static final Type PERSON_TYPE = Type.Builder.build()
            .tid(grphInstSet.VRTX_TID)
            .vid(PERSON_TID)
            .isaPredicate(rec(
                    uri("name"), STR_TYPE,
                    uri("age"), INT_TYPE)).create();

    public static final Type SOFTWARE_TYPE = Type.Builder.build()
            .tid(grphInstSet.VRTX_TID)
            .vid(SOFTWARE_TID)
            .isaPredicate(rec(
                    uri("name"), STR_TYPE,
                    uri("lang"), STR_TYPE)).create();

    public static final Type KNOWS_TYPE = Type.Builder.build()
            .tid(grphInstSet.EDGE_TID)
            .vid(KNOWS_TID)
            .isaPredicate(rec(
                    uri("weight"), REAL_TYPE,
                    grphInstSet.OUT, PERSON_TYPE,
                    grphInstSet.IN, PERSON_TYPE)).create();

    public static final Type CREATED_TYPE = Type.Builder.build()
            .tid(grphInstSet.EDGE_TID)
            .vid(CREATED_TID)
            .isaPredicate(rec(
                    uri("weight"), REAL_TYPE,
                    grphInstSet.OUT, PERSON_TYPE,
                    grphInstSet.IN, SOFTWARE_TYPE)).create();


    public modernSchema() {
        this(MODERN_SCHEMA_TID);
    }

    public modernSchema(final fURI vid) {
        super(MODERN_SCHEMA_TID, vid);
    }

    @Override
    public Set<Type> types() {
        return new HashSet<>(List.of(
                docWrap(PERSON_TYPE, "a person"),
                docWrap(SOFTWARE_TYPE, "a software"),
                docWrap(KNOWS_TYPE, "a knows"),
                docWrap(CREATED_TYPE, "a created")
        ));
    }
}

