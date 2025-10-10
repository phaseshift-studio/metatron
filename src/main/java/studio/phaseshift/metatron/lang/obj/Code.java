/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.MTonoid;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonad;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonoid;
import studio.phaseshift.metatron.lang.obj.mtron.MCode;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.ui.ObjStringSerializer.prettyPrintCode;

public interface Code extends Call {

    @Override
    Code clone(final Object value, final fURI tid, final fURI vid);

    @Override
    List<Inst> value();

    default Inst inst(final int index) {
        return index < this.value().size() ? this.value().get(index) : NoObj.single();
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.apply().iterator();
    }

    @Override
    default Code resolve(final Obj lhs) {
        GraphittyLogger LOG = Graphitty.log(this);
        // this.code = new ExplainRewrite().rewrite(code.<Code>as());
        // process bcode inst pipeline
        //this.code = Rewriter({Rewriter::by(), Rewriter::explain()}).apply(this.code);
        // setup global behavior around barriers, initials, and terminals
        LOG.debug("resolving code:\n        [{{y}}PREPILED{{/y}}] %s {{g}}=>{{/g}}\n%s", lhs, prettyPrintCode(new StringBuilder(), this, 0, 7).toString());
        Obj token = lhs.type();
        //LOG.none("%s", token.rng());
        final List<Inst> resolvedCode = new ArrayList<>();
        fURI dom = null;
        fURI rng = null;
        boolean fullResolution = true;
        for (final Inst inst : this.value()) {
            try {
                LOG.trace("   {{g}}=>{{/g}} resolving %s => %s", token, inst);
                final Inst resolvedInst = inst.resolve(token);
                /*if(!resolvedCode.isEmpty()) {
                  final Inst instA = resolvedCode.remove(resolvedCode.size() - 1);
                  resolvedCode.add(instA.tid(instA.tid().rng(instB.tid().dom())));
                }*/
                if (null == dom)
                    dom = resolvedInst.tid().query().get(fURI.DOM.toString(), fURI.class);
                rng = resolvedInst.tid().query().get(fURI.RNG.toString(), fURI.class);
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
                token = token.c(c -> c.mult(resolvedInst.c()));
            } catch (final Exception e) {
                resolvedCode.add(inst);
                LOG.debug("runtime resolution of %s required: not enough context to determine inst", null == inst ? "[0]" : inst);
                //e.printStackTrace();
                fullResolution = false;
            }
        }
        final Code resolved = this.value(resolvedCode);
        LOG.debug("%s code:\n        [{{g}}COMPILED{{/g}}]\n%s", fullResolution ? "{{g}}resolved{{/g}}" : "{{y}}semi-resolved{{/y}}", prettyPrintCode(new StringBuilder(), resolved, 0, 7).toString());
        return resolved;

    }

    default Inst nextInst(final Inst inst) {
        final Inst nextInst = ((Supplier<Inst>) () -> {
            if (inst.isNoObj())
                return NoObj.single();
            boolean found = false;
            for (final Inst i : this.value()) {
                if (found) return i;
                if (i == inst) found = true;
            }
            //if (found) return this.value().get(this.value().size() - 1);
            return NoObj.single();
        }).get();
        this.logger().trace("fetching next inst: %s => %s", inst, nextInst);
        return nextInst;
    }

    @Override
    default Code vid(final fURI newVid) {
        return (Code) Call.super.vid(newVid);
    }

    @Override
    default Code tid(final fURI newTid) {
        return (Code) Call.super.tid(newTid);
    }

    @Override
    default Code value(final Object newValue) {
        return Call.super.value(newValue);
    }

    @Override
    default Type dom() {
        return this.value().isEmpty() ? T(fURI.NOOBJ.zero()) : T(this.value().get(0).dom().tid().maybeSome()); // TODO: if unresolved, it's maybe.. is that good?
    }

    default Type rng() {
        return this.value().isEmpty() ? T(fURI.NOOBJ.zero()) : T(this.value().get(this.value().size() - 1).rng().tid().maybeSome());
    }

    @Override
    default Obj apply() {
        return this.apply(NoObj.single());
    }

    @Override
    default Obj apply(final Obj lhs) {
        if (!lhs.matches(this.dom()))
            throw MTronException.of("%s ({{m}}lhs{{/m}}) (%s) does not match {{m}}code domain{{/m}} (%s): %s", lhs, lhs.rng(), this.dom(), this);
        final Code resolve = this.resolve(lhs);
        final MTonoid monoid =  MMonoid.of(lhs, resolve);
        final Obj rhs = objs(monoid.apply(NoObj.single()));
        if (!rhs.matches(monoid.rng()))
            throw MTronException.of("%s ({{m}}rhs{{/m}}) (%s) does not match {{m}}code range{{/m}} (%s): %s", rhs, rhs.rng(), this.rng(), this);
        return rhs;
    }

    // Code resolve(final Obj start);

}