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

package studio.phaseshift.metatron.isa.doc;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * docInstSet - Instruction set for document database operations
 *
 * <p>Defines types and instructions for working with MongoDB/DocumentDB through Metatron.
 *
 * <h2>Types</h2>
 * <ul>
 *   <li><b>DOC_TYPE</b> - A document (record) from a collection</li>
 *   <li><b>COLLECTION_TYPE</b> - A collection of documents</li>
 *   <li><b>DOC_SPACE_TYPE</b> - The document database space</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/doc")
public class docInstSet extends AbstractInstSet {

    public static final fURI DOC_ISA_TID = M_ISA_TID.extend("doc");
    public static final fURI INST_TID = DOC_ISA_TID.extend("inst");
    public static final fURI DOC_TID = DOC_ISA_TID.extend("document");
    public static final fURI COLLECTION_TID = DOC_ISA_TID.extend("collection");

    /**
     * Type for a single document (record with _id field)
     */
    public static final Type DOC_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(DOC_TID)
            .isaPredicate(rec(URI_TYPE, T(ALL)))
            .create();

    /**
     * Type for a collection of documents
     */
    public static final Type COLLECTION_TYPE = Type.Builder.build()
            .tid(LST_TID.maybeSome())
            .vid(COLLECTION_TID)
            .predicate(isa_(T(DOC_TID.maybeSome())).tryToInst())
            .create();

    public docInstSet() {
        super(DOC_ISA_TID, DOC_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        return Set.of(docSpace.DOC_SPACE_TYPE, DOC_TYPE, COLLECTION_TYPE);
    }
    
    @Override
    public Set<Obj> consts() {
        return Set.of(ObjSimpleJSONSerializer.single());
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                // Convert document to list (extract values)
                instC(AS_INST_TID.dom(DOC_TID).rng(LST_TID), lst(LST_TYPE),
                    (lhs, inst) -> lst(lhs.asRec().elements().map(Rel::second).toList()))
        ));
    }

    @Override
    public Set<Inst> rewrites() {
        // TODO: Add query optimization rewrites (e.g., count, aggregation)
        return new LinkedHashSet<>();
    }
}
