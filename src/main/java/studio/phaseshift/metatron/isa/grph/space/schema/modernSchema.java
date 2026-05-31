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
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/grph/schema/modern")
public class modernSchema extends AbstractInstSet {

    public static final fURI MODERN_SCHEMA_TID = GRPH_ISA_TID.extend("schema").extend("modern");
    public static final fURI PERSON_TID = MODERN_SCHEMA_TID.extend("person");
    public static final fURI SOFTWARE_TID = MODERN_SCHEMA_TID.extend("software");
    public static final fURI KNOWS_TID = MODERN_SCHEMA_TID.extend("knows");
    public static final fURI CREATED_TID = MODERN_SCHEMA_TID.extend("created");

    public static final Type MODERN_SCHEMA_TYPE = T(MODERN_SCHEMA_TID);

    public static final Type PERSON_TYPE = Type.Builder.build()
            .tid(VRTX_TID)
            .vid(PERSON_TID)
            .isaPredicate(rec(
                    uri("name"), STR_TYPE,
                    uri("age"), INT_TYPE)).create();

    public static final Type SOFTWARE_TYPE = Type.Builder.build()
            .tid(VRTX_TID)
            .vid(SOFTWARE_TID)
            .isaPredicate(rec(
                    uri("name"), STR_TYPE,
                    uri("lang"), STR_TYPE)).create();

    public static final Type KNOWS_TYPE = Type.Builder.build()
            .tid(EDGE_TID)
            .vid(KNOWS_TID)
            .isaPredicate(rec(
                    uri("weight"), REAL_TYPE)).create();

    public static final Type CREATED_TYPE = Type.Builder.build()
            .tid(EDGE_TID)
            .vid(CREATED_TID)
            .isaPredicate(rec(
                    uri("weight"), REAL_TYPE)).create();

    public modernSchema() {
        super(mutableMap(uri(PATTERN), uri(MODERN_SCHEMA_TID.extend(ALL))), INSTSET_TID, MODERN_SCHEMA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(TYPE), lst(
                        docWrap(PERSON_TYPE, "a person"),
                        docWrap(SOFTWARE_TYPE, "a software"),
                        docWrap(KNOWS_TYPE, "a knows"),
                        docWrap(CREATED_TYPE, "a created")
                )
        )));
        super.setup();
    }
}

