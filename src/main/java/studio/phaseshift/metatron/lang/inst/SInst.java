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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.BObj.InstF;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.lang.translator.JSONTranslator;
import studio.phaseshift.metatron.struct.Router;
import studio.phaseshift.metatron.ui.ProgressBar;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

public final class SInst {
    public static final fURI START_URI = fURI.of("start");
    public static final fURI EXPLAIN_URI = fURI.of("explain");
    public static final fURI BLOCK_URI = fURI.of("block");
    public static final fURI IDENTITY_URI = fURI.of("identity");
    public static final fURI MATCH_URI = fURI.of("match");
    public static final fURI PLUS_URI = fURI.of("plus");
    public static final fURI MULT_URI = fURI.of("mult");
    public static final fURI MAP_URI = fURI.of("map");
    //public static final fURI SPLIT_URI = fURI.of("split");
    public static final fURI TO_URI = fURI.of("to");
    public static final fURI FROM_URI = fURI.of("from");
    public static final fURI REF_URI = fURI.of("ref");
    public static final fURI TYPE_URI = fURI.of("type");
    public static final fURI IS_URI = fURI.of("is");
    public static final fURI EQ_URI = fURI.of("eq");
    public static final fURI NEQ_URI = fURI.of("neq");
    public static final fURI GT_URI = fURI.of("gt");
    public static final fURI GTE_URI = fURI.of("gte");
    public static final fURI LT_URI = fURI.of("lt");
    public static final fURI LTE_URI = fURI.of("lte");
    public static final fURI GET_URI = fURI.of("get");
    public static final fURI COUNT_URI = fURI.of("count");
    /// //////////////////////////////////////////////////////////
    public static final fURI JSON_URI = fURI.of("json");

    public static void load() {
        ProgressBar pg = new ProgressBar(20);
        BInst.SymbolTable.load(pg.incr(START_URI), InstF.of((lhs, args) -> args.value().get(0)));
        BInst.SymbolTable.load(pg.incr(EXPLAIN_URI), InstF.of((lhs, args) -> BObj.NoObj.of()));
        BInst.SymbolTable.load(pg.incr(BLOCK_URI), InstF.of((lhs, args) -> args.value().get(0)));
        BInst.SymbolTable.load(pg.incr(IDENTITY_URI), InstF.of(lhs -> lhs));
        BInst.SymbolTable.load(pg.incr(MAP_URI), InstF.of((lhs, args) -> args.value().get(0).apply(lhs)));
        BInst.SymbolTable.load(pg.incr(MATCH_URI), InstF.of((lhs, args) ->  SObj.Bool.of(lhs.matches(args.value().get(0)))));
        BInst.SymbolTable.load(pg.incr(PLUS_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Int(lhs.intValue() + args.value().get(0).intValue(), lhs.tid(),null);
            else if (lhs.isUri() && args.value().get(0).isUri())
                return new SObj.Uri(lhs.uriValue().extend(args.value().get(0).uriValue()), lhs.tid(),null);
            else
                throw new IllegalStateException("the operands do not support addition: %s + %s".formatted(lhs, args.value().get(0)));

        }));
        BInst.SymbolTable.load(pg.incr(MULT_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return SObj.Int.of(lhs.intValue() * args.value().get(0).intValue());
            else throw new IllegalStateException("the operands do not support multiplication");

        }));
        BInst.SymbolTable.load(pg.incr(REF_URI), InstF.of((lhs, args) -> {
            Router.global().write(lhs.uriValue(), args.value().get(0));
            return lhs;
        }));
        BInst.SymbolTable.load(pg.incr(TO_URI), InstF.of((lhs, args) -> {
            Router.global().write(args.value().get(0).uriValue(), lhs);
            return lhs;
        }));
        BInst.SymbolTable.load(pg.incr(FROM_URI), InstF.of((lhs, args) -> Router.global().read(args.value().get(0).uriValue())));
        BInst.SymbolTable.load(pg.incr(TYPE_URI), InstF.of((lhs, args) -> null == lhs.value() ? BObj.NoObj.of() : SObj.Uri.of(lhs.tid())));
        BInst.SymbolTable.load(pg.incr(IS_URI), InstF.of((lhs, args) -> args.get(0).boolValue() ? lhs : BObj.NoObj.of()));
        BInst.SymbolTable.load(pg.incr(EQ_URI), InstF.of((lhs, args) ->  SObj.Bool.of(lhs.equals(args.get(0)))));
        BInst.SymbolTable.load(pg.incr(NEQ_URI), InstF.of((lhs, args) ->  SObj.Bool.of(!lhs.equals(args.get(0)))));
        BInst.SymbolTable.load(pg.incr(GT_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() > args.value().get(0).intValue(), lhs.tid(),null);
            else
                throw new IllegalStateException("the operands do not support gt: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(LT_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() < args.value().get(0).intValue(), lhs.tid(),null);
            else
                throw new IllegalStateException("the operands do not support lt: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(GTE_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() >= args.value().get(0).intValue(), lhs.tid(),null);
            else
                throw new IllegalStateException("the operands do not support gte: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(LTE_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() <= args.value().get(0).intValue(), lhs.tid(),null);
            else
                throw new IllegalStateException("the operands do not support lte: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(GET_URI), InstF.of((lhs, args) -> {
            if (lhs.isLst())
                return lhs.lstValue().get(args.get(0).intValue().intValue());
            //else if (lhs.isRec())
            //    return lhs.recValue().get(args.get(0));
            else
                return BObj.NoObj.of();
        }));
        BInst.SymbolTable.load(pg.incr(COUNT_URI),
                InstF.of((lhs, args) -> IteratorUtil.reduce(
                        (Iterator) lhs.lstValue().get(1).iterator(),
                        (BObj.Int) lhs.lstValue().get(0),
                        (BObj.Int a, BObj.Int b) -> SObj.Int.of(a.value() + b.value()))), SObj.Int.of(0));

    }

    public static void ext() {
        ProgressBar pg = new ProgressBar(20);
        BInst.SymbolTable.load(pg.incr(JSON_URI), InstF.of((lhs, args) -> new JSONTranslator().translateString(lhs.strValue())));
    }
}
