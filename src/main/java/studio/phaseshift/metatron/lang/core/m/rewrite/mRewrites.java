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

package studio.phaseshift.metatron.lang.core.m.rewrite;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Call;
import studio.phaseshift.metatron.lang.core.m.type.Code;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.MTRON_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.util.serial.ObjStringSerializer.prettyPrintCode;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mRewrites extends MInstSet {

    public static final fURI REWRITE_TID = MTRON_TID.extend("rewrite");
    public static final fURI REWRITE_GENERICS_TID = REWRITE_TID.extend("generics");
    public static final fURI REWRITE_TYPER_TID = REWRITE_TID.extend("typer");

    public mRewrites(final fURI tid, final fURI vid) {
        super(tid, vid);

    }

    @Override
    public Set<Inst> insts() {
        return Stream.of(
                instC(REWRITE_TYPER_TID.dom(fURI.ALL.maybeSome()).rng(fURI.ALL.maybeSome()), lst(T(fURI.ALL)), (lhs, inst) -> {
                    GraphittyLogger LOG = Graphitty.log(inst);
                    // setup global behavior around barriers, initials, and terminals
                    LOG.debug("resolving code:\n        [{{y}}PREPILED{{/y}}] %s {{g}}=>{{/g}}\n%s", lhs, prettyPrintCode(new StringBuilder(), this, 0, 7).toString());
                    Obj token = lhs.type();
                    final List<Inst> resolvedInsts = new ArrayList<>();
                    fURI dom = null;
                    fURI rng = null;
                    boolean fullResolution = true;
                    for (final Inst inst2 : inst.arg(0).<Call>as().insts()) {
                        try {
                            LOG.trace("   {{g}}=>{{/g}} resolving %s => %s", token, inst2);
                            final Inst resolvedInst2 = inst2.resolve(token);
                            if (null == dom)
                                dom = resolvedInst2.tid().query().get(fURI.DOM.toString(), fURI.class);
                            rng = resolvedInst2.tid().query().get(fURI.RNG.toString(), fURI.class);
                            resolvedInsts.add(resolvedInst2);
                            token = resolvedInst2.rng();
                            if (resolvedInst2.isInitial()) {
                                LOG.trace("  {{g}}==>{{/g}} marking {{y}}initial{{/y}} at %s", resolvedInst2);
                                token = resolvedInst2.arg(0).type();
                                //this.running().append(MMonad.of(NoObj.single(), instB));
                            } else if (resolvedInst2.isGather()) {
                                LOG.trace("  {{m}}==|{{/m}} marking {{y}}barrier{{/y}} at %s", resolvedInst2);
                            }
                            token = token.c(c -> c.mult(resolvedInst2.c()));
                        } catch (final Exception e) {
                            resolvedInsts.add(inst2);
                            LOG.debug("runtime resolution of %s required: not enough context to determine inst", null == inst2 ? "[0]" : inst2);
                            //e.printStackTrace();
                            fullResolution = false;
                        }
                    }
                    final Code resolved = inst.arg(0).jvm(resolvedInsts);
                    LOG.debug("%s code:\n        [{{g}}COMPILED{{/g}}]\n%s", fullResolution ? "{{g}}resolved{{/g}}" : "{{y}}semi-resolved{{/y}}", prettyPrintCode(new StringBuilder(), resolved, 0, 7).toString());
                    return resolved;
                }) // /+/call/A{*}
        ).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }
}
