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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet.Law;
import studio.phaseshift.metatron.isa.m.type.Lst;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.Law.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.laws;

/**
 * The declared process laws of the base types' operations, in one table.
 *
 * <p>Laws split two ways: by <i>provenance</i> — {@code syntactic} (read off the coefficient),
 * {@code declared} (proved once, registered per family), {@code semantic} (apply-and-test) — and by
 * <i>kind</i> — {@code structure} (the whole algebra, riding on {@code object::T} as theory recs) vs
 * {@code process} (a single operation, riding on {@code morphism::T} as labels). Only the
 * {@code declared ∩ process} cell cannot be derived, so only it lives here. The operation's inverse is a
 * relation, not a law, so it is a paired field on the entry rather than mixed into the law list.
 *
 * <p>The base types' laws never (or rarely) change, so a single table beats declarations scattered across
 * the type classes.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class CatLawTable {

    private CatLawTable() {
    }

    /**
     * A table row: the declared process laws, and the operation's inverse (null when none).
     */
    public record Entry(Lst laws, fURI inverse) {
    }

    private static final Map<fURI, Entry> TABLE = new HashMap<>();

    static {
        // int — the ring (add/mul) plus its order and reductions
        entry(PLUS_INST_TID.dom(INT_TID).rng(INT_TID), MINUS_INST_TID.dom(INT_TID).rng(INT_TID), commutative, right_distributive, action);
        entry(MULT_INST_TID.dom(INT_TID).rng(INT_TID), DIV_INST_TID.dom(INT_TID).rng(INT_TID), commutative, right_distributive, action);
        entry(MINUS_INST_TID.dom(INT_TID).rng(INT_TID), PLUS_INST_TID.dom(INT_TID).rng(INT_TID), action);
        entry(DIV_INST_TID.dom(INT_TID).rng(INT_TID), MULT_INST_TID.dom(INT_TID).rng(INT_TID));
        entry(NEG_INST_TID.dom(INT_TID).rng(INT_TID), NEG_INST_TID.dom(INT_TID).rng(INT_TID));
        entry(GT_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(GTE_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(LT_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(LTE_INST_TID.dom(INT_TID).rng(BOOL_TID), null, right_distributive);
        entry(SUM_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), null, monoidic, commutative, right_distributive);
        entry(PROD_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), null, monoidic, commutative, right_distributive);
        // TODO: real, str, rec, bool, bytes, uri, lst — their declared process laws
    }

    /**
     * The declared process laws and inverse of the inst, or null when the inst is not in the table.
     */
    public static Entry lookup(final fURI instTid) {
        return TABLE.get(instTid);
    }

    private static void entry(final fURI inst, final fURI inverse, final Law... declared) {
        TABLE.put(inst, new Entry(laws(declared), inverse));
    }
}
