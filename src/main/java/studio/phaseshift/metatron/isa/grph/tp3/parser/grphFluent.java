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

package studio.phaseshift.metatron.isa.grph.tp3.parser;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class grphFluent extends mFluent<grphFluent> {

    // ========================================
    // Constructors and Core Methods
    // ========================================

    public grphFluent() {
        this(new ArrayList<>(), grphInstSet.GRPH_INST_TID, null);
    }

    protected grphFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    // ========================================
    // Graph Traversal Operations - Vertex to Edge
    // ========================================

    public grphFluent outE_(final Obj... obj) {
        return this.addInst(instB(OUTE_INST_TID, lst(obj)));
    }

    public grphFluent inE_(final Obj... obj) {
        return this.addInst(instB(INE_INST_TID, lst(obj)));
    }

    public grphFluent bothE_(final Obj... obj) {
        return this.addInst(instB(BOTHE_INST_TID, lst(obj)));
    }

    // ========================================
    // Graph Traversal Operations - Vertex to Vertex
    // ========================================

    public grphFluent out_(final Obj... obj) {
        return this.addInst(instB(OUT_INST_TID, lst(obj)));
    }

    public grphFluent in_(final Obj... obj) {
        return this.addInst(instB(IN_INST_TID, lst(obj)));
    }

    public grphFluent both_(final Obj... obj) {
        return this.addInst(instB(BOTH_INST_TID, lst(obj)));
    }

    // ========================================
    // Graph Traversal Operations - Edge to Vertex
    // ========================================

    public grphFluent outV_(final Obj... obj) {
        return this.addInst(instB(OUTV_INST_TID, lst(obj)));
    }

    public grphFluent inV_(final Obj... obj) {
        return this.addInst(instB(INV_INST_TID, lst(obj)));
    }

    public grphFluent bothV_(final Obj... obj) {
        return this.addInst(instB(BOTHV_INST_TID, lst(obj)));
    }

    // ========================================
    // Graph Element Operations
    // ========================================

    public grphFluent values_(final Obj... obj) {
        return this.addInst(instB(VALUES_INST_TID, lst(obj)));
    }

    public grphFluent properties_(final Obj... obj) {
        return this.addInst(instB(PROPERTIES_INST_TID, lst(obj)));
    }

    @Override
    public grphFluent clone(final Object jvm, final fURI tid, final fURI vid) {
        return (grphFluent) super.clone(jvm, tid, vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        // ========================================
        // Core Methods
        // ========================================

        public static grphFluent inst_(final Inst inst) {
            return new grphFluent().addInst(inst);
        }

        // ========================================
        // Graph Traversal Operations - Vertex to Edge
        // ========================================

        public static grphFluent outE_(final Obj... obj) {
            return new grphFluent().outE_(obj);
        }

        public static grphFluent inE_(final Obj... obj) {
            return new grphFluent().inE_(obj);
        }

        public static grphFluent bothE_(final Obj... obj) {
            return new grphFluent().bothE_(obj);
        }

        // ========================================
        // Graph Traversal Operations - Vertex to Vertex
        // ========================================

        public static grphFluent out_(final Obj... obj) {
            return new grphFluent().out_(obj);
        }

        public static grphFluent in_(final Obj... obj) {
            return new grphFluent().in_(obj);
        }

        public static grphFluent both_(final Obj... obj) {
            return new grphFluent().both_(obj);
        }

        // ========================================
        // Graph Traversal Operations - Edge to Vertex
        // ========================================

        public static grphFluent outV_(final Obj... obj) {
            return new grphFluent().outV_(obj);
        }

        public static grphFluent inV_(final Obj... obj) {
            return new grphFluent().inV_(obj);
        }

        public static grphFluent bothV_(final Obj... obj) {
            return new grphFluent().bothV_(obj);
        }

        // ========================================
        // Graph Element Operations
        // ========================================

        public static grphFluent values_(final Obj... obj) {
            return new grphFluent().values_(obj);
        }

        public static grphFluent properties_(final Obj... obj) {
            return new grphFluent().properties_(obj);
        }
    }
}
