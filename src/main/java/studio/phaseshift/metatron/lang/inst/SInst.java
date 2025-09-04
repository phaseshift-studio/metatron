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

public final class SInst {
    public static final fURI START_URI = new fURI("start");
    public static final fURI EXPLAIN_URI = new fURI("explain");
    public static final fURI BLOCK_URI = new fURI("block");
    public static final fURI IDENTITY_URI = new fURI("identity");
    public static final fURI MATCH_URI = new fURI("match");
    public static final fURI PLUS_URI = new fURI("plus");
    public static final fURI MULT_URI = new fURI("mult");
    public static final fURI MAP_URI = new fURI("map");
    //public static final fURI SPLIT_URI = new fURI("split");
    public static final fURI TO_URI = new fURI("to");
    public static final fURI FROM_URI = new fURI("from");
    public static final fURI REF_URI = new fURI("ref");
    public static final fURI TYPE_URI = new fURI("type");
    public static final fURI IS_URI = new fURI("is");
    public static final fURI EQ_URI = new fURI("eq");
    public static final fURI NEQ_URI = new fURI("neq");
    public static final fURI GT_URI = new fURI("gt");
    public static final fURI GTE_URI = new fURI("gte");
    public static final fURI LT_URI = new fURI("lt");
    public static final fURI LTE_URI = new fURI("lte");
    public static final fURI GET_URI = new fURI("get");
    /// //////////////////////////////////////////////////////////
    public static final fURI JSON_URI = new fURI("json");

    public static void load() {
        ProgressBar pg = new ProgressBar(20);
        BInst.SymbolTable.load(pg.incr(START_URI), InstF.of((lhs, args) -> args.value().get(0)));
        BInst.SymbolTable.load(pg.incr(EXPLAIN_URI), InstF.of((lhs, args) -> BObj.NoObj.of()));
        BInst.SymbolTable.load(pg.incr(BLOCK_URI), InstF.of((lhs, args) -> args.value().get(0)));
        BInst.SymbolTable.load(pg.incr(IDENTITY_URI), InstF.of(lhs -> lhs));
        BInst.SymbolTable.load(pg.incr(MAP_URI), InstF.of((lhs, args) -> args.value().get(0).apply(lhs)));
        BInst.SymbolTable.load(pg.incr(MATCH_URI), InstF.of((lhs, args) -> new SObj.Bool(lhs.matches(args.value().get(0)))));
        BInst.SymbolTable.load(pg.incr(PLUS_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Int(lhs.intValue() + args.value().get(0).intValue(), lhs.tid());
            else if (lhs.isUri() && args.value().get(0).isUri())
                return new SObj.Uri(lhs.uriValue().extend(args.value().get(0).uriValue()), lhs.tid());
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
        BInst.SymbolTable.load(pg.incr(TYPE_URI), InstF.of((lhs, args) -> null == lhs.tid() ? BObj.NoObj.of() : SObj.Uri.of(lhs.tid())));
        BInst.SymbolTable.load(pg.incr(IS_URI), InstF.of((lhs, args) -> args.get(0).boolValue() ? lhs : BObj.NoObj.of()));
        BInst.SymbolTable.load(pg.incr(EQ_URI), InstF.of((lhs, args) -> new SObj.Bool(lhs.equals(args.get(0)))));
        BInst.SymbolTable.load(pg.incr(NEQ_URI), InstF.of((lhs, args) -> new SObj.Bool(!lhs.equals(args.get(0)))));
        BInst.SymbolTable.load(pg.incr(GT_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() > args.value().get(0).intValue(), lhs.tid());
            else
                throw new IllegalStateException("the operands do not support gt: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(LT_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() < args.value().get(0).intValue(), lhs.tid());
            else
                throw new IllegalStateException("the operands do not support lt: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(GTE_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() >= args.value().get(0).intValue(), lhs.tid());
            else
                throw new IllegalStateException("the operands do not support gte: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(LTE_URI), InstF.of((lhs, args) -> {
            if (lhs.isInt() && args.value().get(0).isInt())
                return new SObj.Bool(lhs.intValue() <= args.value().get(0).intValue(), lhs.tid());
            else
                throw new IllegalStateException("the operands do not support lte: %s + %s".formatted(lhs, args.value().get(0)));
        }));
        BInst.SymbolTable.load(pg.incr(GET_URI), InstF.of((lhs, args) -> {
            if (lhs.isLst())
                return lhs.lstValue().get(args.get(0).intValue().intValue());
            else if (lhs.isRec())
                return lhs.recValue().get(args.get(0));
            else
                return BObj.NoObj.of();
        }));
    }

    public static void ext() {
        ProgressBar pg = new ProgressBar(20);
        BInst.SymbolTable.load(pg.incr(JSON_URI), InstF.of((lhs, args) -> new JSONTranslator().translateString(lhs.strValue())));
    }
}
