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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public interface Code extends Call {

    Type CODE_TYPE = Type.Builder.build().tid(CODE_TID).vid(CODE_TID).create();

    @Override
    Code clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Inst> jvm();

    default Inst inst(final int index) {
        return index < this.jvm().size() ? this.jvm().get(index) : noobj();
    }

    @Override
    default boolean isResolved(final boolean nested) {
        return this.asCode().insts().stream().allMatch(x -> x.isResolved(nested));
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.apply().iterator();
    }

    default Code rewrite() {
        final AtomicReference<Code> rewrittenCode = new AtomicReference<>(this);
        int hash = this.hashCode();
        int done = 2;
        while (done != 0) {
            Router.global().spaces()
                    .elements()
                    .filter(r -> r.second() instanceof InstSet)
                    .flatMap(r -> r.second().<InstSet>as().rewrites().stream())
                    //.peek(r -> this.logger().warn("REWRITE RULE: %s => %s [hash:%d][stage:%d]", rewrittenCode.get(), r, rewrittenCode.get().hashCode(), stage))
                    .forEach(r -> {
                        // rewrittenCode.get().insts().forEach(i -> i.args(Code.Helper.tryRewrite(i.args()).as()));
                        rewrittenCode.set(r.apply(rewrittenCode.get()).asCode());
                    });
            if (hash == (hash = rewrittenCode.get().hashCode()))
                done--;
        }
        return rewrittenCode.get();
    }
    
    /*default Code rewrite(final Code queryPlan) {
       // TODO!!! 
    }*/

    @Override
    default Code resolve(final Obj lhs) {
        //if(this.insts().stream().noneMatch(x -> x.resolution().equals(Inst.Resolution.A)))
        //  return this;
        GraphittyLogger LOG = Graphitty.log(this);
        LOG.debug("reading code:\n        [{{y}}PREPILED{{/y}}] %s {{g}}=>{{/g}}\n%s", lhs, ObjmtronSerializer.prettyPrintCode(this));
        /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        final Code rewrittenCode = this.rewrite();
        LOG.debug("rewriting code:\n        [{{y}}REWRITTEN{{/y}}] %s {{g}}=>{{/g}}\n%s", lhs, ObjmtronSerializer.prettyPrintCode(rewrittenCode));
        Obj token = lhs.isType() ? lhs : lhs.type();
        final List<Inst> resolvedCode = new ArrayList<>();
        boolean fullResolution = true;
        for (final Inst inst : rewrittenCode.insts()) {
            try {
                LOG.trace("   {{g}}=>{{/g}} resolving %s => %s", token, inst);
                final Inst resolvedInst = (inst.tid().basePath().equals(AS_INST_TID) ? inst.rng(inst.arg(0).asType()).asInst() : inst).resolve(token);

                if (!resolvedInst.hasDom()) {
                    resolvedCode.add(inst);
                    token = inst.hasRng() ? inst.rng() : token;
                } else {
                    resolvedCode.add(resolvedInst);
                    token = resolvedInst.rng();
                    if (resolvedInst.isInitial()) {
                        LOG.trace("  {{g}}==>{{/g}} marking {{y}}initial{{/y}} at %s", resolvedInst);
                        token = resolvedInst.arg(0).type();
                        //this.running().append(MMonad.of(NoObj.single(), instB));
                    } else if (resolvedInst.isGather()) {
                        // many-to-?
                        LOG.trace("  {{m}}==|{{/m}} marking {{y}}barrier{{/y}} at %s", resolvedInst);
                    }
                }
                token = token.c(c -> c.mult(resolvedInst.c()));
            } catch (final Exception e) {
                resolvedCode.add(inst);
                LOG.debug("runtime resolution of %s required: not enough context to determine inst", null == inst ? "[0]" : inst);
                //e.printStackTrace();
                fullResolution = false;
            }
        }
        final Code resolved = this.jvm(resolvedCode);
        LOG.debug("%s code:\n        [{{g}}COMPILED{{/g}}]\n%s", fullResolution ? "{{g}}resolved{{/g}}" : "{{y}}semi-resolved{{/y}}", ObjmtronSerializer.prettyPrintCode(resolved));
        return resolved;

    }

    default Inst nextInst(final Inst inst) {
        if (inst.isNoObj())
            return noobj();
        boolean found = false;
        for (final Inst i : this.jvm()) {
            if (found) return i;
            if (i == inst) found = true;
        }
        //if (found) return this.value().get(this.value().size() - 1);
        return noobj();
    }

    @Override
    default Code vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    default Code tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    @Override
    default Code jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    @Override
    default Type dom() {
        return this.jvm().isEmpty() ? T(NOOBJ) : T(this.jvm().get(0).dom().tid()); // TODO: if unresolved, it's maybe.. is that good?
    }

    default Type rng() {
        return this.jvm().isEmpty() ? T(NOOBJ) : T(this.jvm().get(this.jvm().size() - 1).rng().tid());
    }

    @Override
    default Obj apply() {
        return this.apply(noobj());
    }

    @Override
    default Obj apply(final Obj lhs) {
        final Call resolve = this.tryToInst().resolve(lhs);
        // TODO: add this as a TypeCheker check point?
        //if (!lhs.matches(resolve.dom()))
        //    throw MTronException.of("%s ({{m}}lhs{{/m}}) (%s) does not match {{m}}code domain{{/m}} (%s): %s", lhs, lhs.rng(), resolve.dom(), resolve);
        final Obj rhs = objs(resolve.isCode() ? SwarmMachine.of(lhs, resolve.as()).apply(noobj()) : resolve.apply(lhs));
        //if (!rhs.matches(call.rng()))
        //    throw MTronException.of("%s ({{m}}rhs{{/m}}) (%s) does not match {{m}}code range{{/m}} (%s): %s", rhs, rhs.rng(), call.rng(), this);
        return rhs;
    }

    public static class CodeType {

        private CodeType() {
            // do nothing
        }

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(instC(AS_INST_TID.dom(CODE_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lst(lhs.asCode().codeValue().stream().map(Obj::<Obj>as).toList()))));
        }

    }

    public static class Helper {

        public static Obj tryRewrite(final Obj obj) {
            if (obj.isCode())
                return obj.asCode().rewrite();
            if (obj.isInst())
                return obj.asInst().args(tryRewrite(obj.asInst().args()).as());
            if (obj.isLst())
                return obj.selfJVM(obj.asLst().elements().map(Code.Helper::tryRewrite).toList());
            if (obj.isRec())
                return obj.selfJVM(obj.asRec().elements().map(r -> Tuple.Pair.with(r.first(), tryRewrite(r.second()))).collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1, (a, b) -> a)));
            return obj;
        }

    }

    // Code resolve(final Obj start);

}