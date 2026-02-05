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

package studio.phaseshift.metatron.isa.grph.space.tp3.schema;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MInstSet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class modernSchema extends MInstSet {

    public static final fURI MODERN_SCHEMA_TID = GRPH_ISA_TID.extend("modern");
    public static final fURI PERSON_TID = MODERN_SCHEMA_TID.extend("person");
    public static final fURI SOFTWARE_TID = MODERN_SCHEMA_TID.extend("software");
    public static final fURI KNOWS_TID = MODERN_SCHEMA_TID.extend("knows");
    public static final fURI CREATED_TID = MODERN_SCHEMA_TID.extend("created");

    public static final Type PERSON_TYPE = Type.Builder.build()
            .tid(VRTX_TID)
            .vid(PERSON_TID)
            .predicate(isa_(rec(
                    uri("name"), STR_TYPE, 
                    uri("age"), INT_TYPE))).create();

    public static final Type SOFTWARE_TYPE = Type.Builder.build()
            .tid(VRTX_TID)
            .vid(SOFTWARE_TID)
            .predicate(isa_(rec(
                    uri("name"), STR_TYPE,
                    uri("lang"), STR_TYPE))).create();

    public static final Type KNOWS_TYPE = Type.Builder.build()
            .tid(EDGE_TID)
            .vid(KNOWS_TID)
            .predicate(isa_(rec(
                    uri("weight"), REAL_TYPE,
                    OUT, PERSON_TYPE,
                    IN, PERSON_TYPE))).create();

    public static final Type CREATED_TYPE = Type.Builder.build()
            .tid(EDGE_TID)
            .vid(CREATED_TID)
            .predicate(isa_(rec(
                    uri("weight"), REAL_TYPE,
                    OUT, PERSON_TYPE,
                    IN, SOFTWARE_TYPE))).create();


    protected modernSchema(final fURI vid) {
        super(MODERN_SCHEMA_TID, vid);
    }

    public static modernSchema create(final fURI vid) {
        return new modernSchema(vid);
    }

    public static modernSchema create() {
        return modernSchema.create(MODERN_SCHEMA_TID);
    }

    @Override
    public Set<Type> types() {
        return new HashSet<>(List.of(
                PERSON_TYPE,
                SOFTWARE_TYPE,
                KNOWS_TYPE,
                CREATED_TYPE));
    }
}

