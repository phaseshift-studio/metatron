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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.ARGS;
import static studio.phaseshift.metatron.Tokens.INST_CTOR_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class MInst extends MObj implements Inst {

    /**
     * WHEN TRUE, an instruction's DECLARED ARGUMENT TYPES are encoded into its tid as the {@code args} query
     * component ({@code inst?rng=int&dom=int&args=[int,rec]}), completing the contract ({@code A x B -> C}) the
     * resolver, binder and laws read.
     * <p>
     * OFF BY DEFAULT: the encoding happens where an inst is MINTED, so it must be on before an inst set registers; a
     * flag flipped later only affects insts minted afterwards. Off keeps every tid rendering byte-identically (the
     * {@code fURI.args(List)} contract: an empty list carries no key at all).
     */
    public static volatile boolean ARGS_IN_TID = Boolean.getBoolean("mtron.inst.args-in-tid");

    /**
     * the {@code args} query key: an operation's DECLARED argument types. A key with a value lists them in order
     * ({@code args=[str,uri]}); a BARE key declares ZERO arguments; no key is no argument claim at all.
     */
    public static final String ARGS_KEY = ARGS;

    public MInst(final Triplet<Poly, Inst.f, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? Tokens.M_ISA_INST_TID : tid, vid);
        // T(INST_TID, this.tid(), null, null);
        //if (this.tid().name().equals("inst"))
        //    this.selfTID(this.tid().dom(ALL.maybe()).rng(ALL.maybeSome()));
    }


    @Override
    public Inst clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Triplet<Poly, Inst.f, Obj> jvm() {
        return (Triplet<Poly, Inst.f, Obj>) this.jvm;
    }

    @Override
    public Inst c(final cInt c) {
        return (Inst) super.c(c);
    }

    public static Inst instCtor(final fURI rngTID, final Function<Obj, Obj> construct) {
        return instCtor(T(rngTID), construct);
    }

    public static Inst instCtor(final Type rngType, final Function<Obj, Obj> construct) {
        return instC(INST_CTOR_TID.dom(ALL.maybe()).rng(rngType.vid()), lst(rngType), (lhs, inst) -> construct.apply(inst.arg(0)));
    }

    /**
     * {@code tid} carrying the declared argument types of {@code args} when {@link #ARGS_IN_TID} — else {@code tid}.
     * <p>
     * THE ONE FUNNEL every minting site takes, so the flag has a single meaning: an instruction's contract is
     * {@code A x B -> C}, and the {@code args} query component is where the {@code B} lives. The declared argument
     * types are read from the inst's argument Poly — a {@code Type} contributes its {@code vid}, any other obj its
     * specific type id — and the resulting list is written through {@link fURI#args(List)}, which omits the key
     * entirely for an empty list.
     * <p>
     * A NAMED (rec-shaped) argument list is OUT OF SCOPE: the {@code args} component is an ordered list of types, so
     * a rec-shaped declaration keeps its tid unchanged rather than being flattened into a positional claim it does
     * not make. An argument whose type cannot be read (a null or {@code noobj} slot) is NO CLAIM either: the tid is
     * left alone rather than advertising a partial arity.
     * <p>
     * The flag is tested FIRST, before any obj is asked anything, because {@code instC} is called from inst-set
     * CLASS INITIALIZERS — minting a type at class-init time can re-enter the type system.
     *
     * @param tid  the tid composed by the caller from dom and rng
     * @param args the declared arguments, as the inst's argument Poly
     * @return {@code tid} carrying the declared argument types, or {@code tid} itself when there is nothing to say
     */
    public static fURI keyed(final fURI tid, final Poly args) {
        return ARGS_IN_TID ? signature(tid, args) : tid;
    }

    /**
     * THE SIGNATURE ITSELF — {@code tid} carrying the declared argument types of {@code args}, with no flag: the rule
     * the mint-time funnel ({@link #keyed}) and the explicit {@code Inst.Helper.fullSignature} both apply, so an
     * instruction's contract is written one way whether it is minted with the encoding on or completed later.
     *
     * @param tid  the tid composed by the caller from dom and rng
     * @param args the declared arguments, as the inst's argument Poly
     * @return {@code tid} carrying the declared argument types, or {@code tid} itself when there is nothing to say
     */
    public static fURI signature(final fURI tid, final Poly args) {
        if (null == tid || null == args || args.isNoObj())
            return tid;
        final List<Obj> slots;
        if (args.isLst())
            slots = args.lstValue();
        else if (args.isRec())
            // NAMES ARE NOT REPRESENTABLE — the `args` component is an ordered list of TYPES — so a named declaration
            // is encoded positionally, in INSERTION order: make_person(first=>?str::T, age=>int::T) => args=[str{?},int].
            // The names stay on the inst's argument Poly; the contract keeps what it can state.
            slots = List.copyOf(args.recValue().values());
        else
            return tid;
        final List<fURI> typeIds = new ArrayList<>(slots.size());
        for (final Obj arg : slots) {
            if (null == arg || arg.isNoObj())
                return tid;
            final fURI typeId = arg.isType() ? arg.asType().vid() : Obj.Helper.specificTypeId(arg);
            // AN ELEMENT MUST BE A PLAIN ADDRESS: a type id that itself carries `args` cannot be written inside the
            // comma/bracket text of the outer list (the codec would split it or close the list early), so such a
            // declaration is NO CLAIM rather than an unparseable one.
            if (null == typeId || typeId.hasArgs())
                return tid;
            typeIds.add(typeId);
        }
        // A KNOWN ZERO IS A CLAIM: an empty declared argument list says "this operation takes no arguments", which
        // is NOT the same as "no argument claim at all" (no key). The BARE `args` key carries the former — a qparam
        // needs no value — and `fURI.args(List)` cannot express it, because an empty list REMOVES the key.
        return typeIds.isEmpty() ? tid.addQ(ARGS) : tid.args(typeIds);
    }

    public static Inst instA(final fURI tid) {
        return new MInst(Triplet.with(null, null, NoObj.noobj()), tid, null);
    }

    public static Inst instB(final fURI tid, final Poly args) {
        final fURI keyed = keyed(tid, args);
        return new MInst(Triplet.with(args, null, NoObj.noobj()), keyed, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final BiFunction<Obj, Inst, Obj> f) {
        final fURI keyed = keyed(tid, args);
        return new MInst(Triplet.with(args, Inst.f.of(f), NoObj.noobj()), keyed, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final Function<Obj, Obj> f) {
        final fURI keyed = keyed(tid, args);
        return new MInst(Triplet.with(args, Inst.f.of(f), NoObj.noobj()), keyed, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final BiFunction<Obj, Inst, Obj> f, final Obj seed) {
        final fURI keyed = keyed(tid, args);
        return new MInst(Triplet.with(args, Inst.f.of(f), seed), keyed, null);
    }

    public static Inst instC(final fURI tid, final Poly args, final String code) {
        final fURI keyed = keyed(tid, args);
        return new MInst(Triplet.with(args, Inst.f.of(code), NoObj.noobj()), keyed, null);
    }

    public static Inst instLambda(final BiFunction<Obj, Inst, Obj> f) {
        return instLambda(lst(), f);
    }

    public static Inst instLambda(final Lst args, final BiFunction<Obj, Inst, Obj> f) {
        return instC(Tokens.LAMBDA_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), args, f);
    }

    public static Inst instLambda(final String name, final BiFunction<Obj, Inst, Obj> f) {
        return instC(fURI.Singleton.f(Tokens.LAMBDA + name), lst(), f);
    }

    public static Inst instLambda(final String name, final fURI dom, final fURI rng, final BiFunction<Obj, Inst, Obj> f) {
        return instC(fURI.Singleton.f(Tokens.LAMBDA + name).dom(dom).rng(rng), lst(), f);
    }


    public static Inst instLambda(final fURI dom, final fURI rng, final BiFunction<Obj, Inst, Obj> f) {
        return instC(Tokens.LAMBDA_TID.dom(dom).rng(rng), lst(), f);
    }

    public static Inst instLambda(final fURI dom, final fURI rng, final Poly args, final BiFunction<Obj, Inst, Obj> f) {
        return instC(Tokens.LAMBDA_TID.dom(dom).rng(rng), args, f);
    }

    public static Inst instLambda(final fURI furi, final Poly args, final BiFunction<Obj, Inst, Obj> f) {
        return instC(fURI.Singleton.f(Tokens.LAMBDA + furi.toString()), args, f);
    }

    /**
     * A lambda whose domain is the lhs alone — the {@code inst} argument is simply ignored, so an
     * arm that does not need the instruction itself is written {@code in -> ...} instead of
     * {@code (in, ignored) -> ...}.
     */
    public static Inst instLambda(final Function<Obj, Obj> f) {
        return instC(Tokens.LAMBDA_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL.maybeSome())), f);
    }

    public static Inst instLambda(final Lst args, final Function<Obj, Obj> f) {
        return instC(Tokens.LAMBDA_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), args, f);
    }

    public static Inst instLambda(final fURI dom, final fURI rng, final Function<Obj, Obj> f) {
        return instC(Tokens.LAMBDA_TID.dom(dom).rng(rng), lst(T(ALL.maybeSome())), f);
    }

    @Override
    public int hashCode() {
        return this.isNoObj() ? Obj.Helper.objHashCode(this) : Objects.hash(withoutArgs(this.tid), this.vid);
    }

    /**
     * A tid with its {@code args} component removed — the OPERATION's address, which is what two instructions are
     * compared by (their declared argument types are the contract, carried by the args poly). Allocation-free when
     * the tid declares no arguments, which is every tid minted with {@code ARGS_IN_TID} off.
     */
    private static fURI withoutArgs(final fURI tid) {
        return Inst.Helper.operationTid(tid);
    }

    @Override
    public String toShortString() {
        if (this.tid().basePath().equals(AUTO_FROM_INST_TID))
            return "!*" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(AUTO_AT_INST_TID) && this.arg(1).isNoObj())
            return "!@" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(AUTO_INST_TID))
            return "!" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(FROM_INST_TID))
            return "*" + this.arg(0).toShortString();
        if (this.tid().basePath().equals(ISA_INST_TID))
            return "?" + this.arg(0).toShortString();
        final String internal = this.args().elements()
                .map(Obj::toShortString)
                .reduce("", (a, b) -> a + b + ",");
        return this.tid().qLess().small() +
                ((!this.tid().rng().equals(ALL) && !this.tid().dom().equals(ALL)) ? "?" + this.tid().rng().small() + "<=" + this.tid.dom().small() : "") +
                "(" + (internal.isEmpty() ? "" : internal.substring(0, internal.length() - 1)) + ")" +
                ((null == this.f() || this.f().isLambda()) ? "" : "{" + this.f() + "}");
    }


    @Override
    public boolean equals(final Object other) {
        // a zero-coefficient inst is the one zero, as noobj (itself an Inst) already says from its side
        if (this.isNoObj() && other instanceof Obj otherObj && otherObj.isNoObj())
            return true;
        // AN INSTRUCTION'S IDENTITY IS THE OPERATION, NOT THE CONTRACT'S SPELLING: the tid's `args` component is
        // the declared argument TYPES (a rendering of what the args poly already carries), so it is compared
        // transparently and SYMMETRICALLY — the previous rule stripped it only when `this.args()` was set, which made
        // equality depend on which side asked. The contract itself is compared by `args()` below.
        return (other instanceof Inst otherInst) &&
                Objects.equals(withoutArgs(this.tid), withoutArgs(otherInst.tid())) &&
                Objects.equals(this.args(), otherInst.args()) &&
                Objects.equals(this.f(), otherInst.f()) &&
                Objects.equals(this.vid, otherInst.vid());
        /*Objects.equals(this.value,((Obj) other).value())*/
    }
}