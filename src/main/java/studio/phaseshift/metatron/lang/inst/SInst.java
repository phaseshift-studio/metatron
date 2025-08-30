/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.inst;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.ui.ProgressBar;

public final class SInst {
    private static final Logger LOG = LoggerFactory.getLogger(SInst.class);
    public static final fURI START_URI = new fURI("start");
    public static final fURI APPLY_URI = new fURI("apply");
    public static final fURI PLUS_URI = new fURI("plus");
    public static final fURI MULT_URI = new fURI("mult");

    public static void load() {
        ProgressBar pg = new ProgressBar(4);
        BInst.SymbolTable.load(START_URI, (lhs, args) -> args.value().get(0));
        pg.incr(START_URI.toString());
        BInst.SymbolTable.load(APPLY_URI, (lhs, args) -> args.value().get(0).apply(lhs));
        pg.incr(APPLY_URI.toString());
        BInst.SymbolTable.load(PLUS_URI, (lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Int(lhs.intValue() + args.value().get(0).intValue(), lhs.type());
            else if (lhs.isUri() && args.value().get(0).isUri())
                return new SObj.Uri(lhs.uriValue().extend(args.value().get(0).uriValue()), lhs.type());
            else
                throw new IllegalStateException("the operands do not support addition: %s + %s".formatted(lhs, args.value().get(0)));

        });
        pg.incr(PLUS_URI.toString());
        BInst.SymbolTable.load(MULT_URI, (lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return SObj.Int.of(lhs.intValue() * args.value().get(0).intValue());
            else
                throw new IllegalStateException("the operands do not support multiplication");

        });
        pg.incr(MULT_URI.toString());
    }
}
