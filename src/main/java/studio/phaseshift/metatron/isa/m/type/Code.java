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
import studio.phaseshift.metatron.isa.m.type.impl.MMachine;
import studio.phaseshift.metatron.io.serial.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.io.serial.ObjStringSerializer.prettyPrintCode;

public interface Code extends Call {

    @Override
    Code clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Inst> jvm();

    default Inst inst(final int index) {
        return index < this.jvm().size() ? this.jvm().get(index) : NoObj.noobj();
    }

    @Override
    default boolean isResolved(final boolean nested) {
        return this.<Code>as().<Inst>elements().allMatch(x -> x.isResolved(nested));
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.apply().iterator();
    }

    @Override
    default Code resolve(final Obj lhs) {
        //if(this.insts().stream().noneMatch(x -> x.resolution().equals(Inst.Resolution.A)))
        //  return this;
        GraphittyLogger LOG = Graphitty.log(this);
        // this.code = new ExplainRewrite().rewrite(code.<Code>as());
        // process bcode inst pipeline
        //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
        // setup global behavior around barriers, initials, and terminals
        LOG.debug("resolving code:\n        [{{y}}PREPILED{{/y}}] %s {{g}}=>{{/g}}\n%s", lhs, ObjCleanStringSerializer.prettyPrintCode(this));
        Obj token = lhs.type();
        //LOG.none("%s", token.rng());
        final List<Inst> resolvedCode = new ArrayList<>();
        boolean fullResolution = true;
        for (final Inst inst : this.jvm()) {
            try {
                LOG.trace("   {{g}}=>{{/g}} resolving %s => %s", token, inst);
                final Inst resolvedInst = inst.resolve(token);
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
        LOG.debug("%s code:\n        [{{g}}COMPILED{{/g}}]\n%s", fullResolution ? "{{g}}resolved{{/g}}" : "{{y}}semi-resolved{{/y}}", ObjCleanStringSerializer.prettyPrintCode(resolved));
        return resolved;

    }

    default Inst nextInst(final Inst inst) {
        final Inst nextInst = ((Supplier<Inst>) () -> {
            if (inst.isNoObj())
                return NoObj.noobj();
            boolean found = false;
            for (final Inst i : this.jvm()) {
                if (found) return i;
                if (i == inst) found = true;
            }
            //if (found) return this.value().get(this.value().size() - 1);
            return NoObj.noobj();
        }).get();
        this.logger().trace("fetching next inst: %s => %s", inst, nextInst);
        return nextInst;
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
        return this.jvm().isEmpty() ? T(fURI.NOOBJ.zero()) : T(this.jvm().get(0).dom().tid()); // TODO: if unresolved, it's maybe.. is that good?
    }

    default Type rng() {
        return this.jvm().isEmpty() ? T(fURI.NOOBJ.zero()) : T(this.jvm().get(this.jvm().size() - 1).rng().tid());
    }

    @Override
    default Obj apply() {
        return this.apply(NoObj.noobj());
    }

    @Override
    default Obj apply(final Obj lhs) {
        final Call resolve = this.tryToInst().resolve(lhs);
        //if (!lhs.matches(resolve.dom()))
        //    throw MTronException.of("%s ({{m}}lhs{{/m}}) (%s) does not match {{m}}code domain{{/m}} (%s): %s", lhs, lhs.rng(), resolve.dom(), resolve);
        final Obj rhs = (resolve.isCode()) ? objs(MMachine.of(lhs, resolve.as()).apply(NoObj.noobj())) : resolve.apply(lhs);
        //if (!rhs.matches(call.rng()))
        //    throw MTronException.of("%s ({{m}}rhs{{/m}}) (%s) does not match {{m}}code range{{/m}} (%s): %s", rhs, rhs.rng(), call.rng(), this);
        return rhs;
    }

    // Code resolve(final Obj start);

}