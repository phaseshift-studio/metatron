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

package studio.phaseshift.metatron.isa.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_SCORING_RESOLVER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * ScoringResolver — the concrete {@code scoring_resolver::T}: the resolution stage of
 * {@code compiler::T}. It carries no config (empty rec) and owns the whole-code threading algorithm;
 * each instruction is resolved via the active {@link studio.phaseshift.metatron.isa.m.type.resolver.InstSelector}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ScoringResolver extends MRec implements Resolver {

    private static final ScoringResolver INSTANCE = new ScoringResolver(mutableMap(), MACH_SCORING_RESOLVER_TID, null);

    public static ScoringResolver single() {
        return INSTANCE;
    }

    public ScoringResolver() {
        this(mutableMap(), MACH_SCORING_RESOLVER_TID, null);
    }

    public ScoringResolver(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Code apply(final Obj code) {
        return resolveCode(noobj(), code.asCode());
    }

    /**
     * Resolve a full instruction chain — threads the output type of each instruction as the input
     * type of the next via {@code Inst.resolve(Obj)} (which delegates to the active
     * {@code InstSelector}).
     */
    public static Code resolveCode(final Obj lhs, final Code code) {
        final GraphittyLogger LOG = Graphitty.log(Resolver.class);
        Obj token = lhs.isType() ? lhs : lhs.type();
        final List<Inst> resolvedCode = new ArrayList<>();
        boolean fullResolution = true;
        int i = 0;
        for (final Inst inst : code.insts()) {
            try {
                final Inst resolvedInst = inst.resolve(token);
                if (!resolvedInst.hasDom()) {
                    resolvedCode.add(inst.clone().selfVID(f("" + i)).as());
                    token = inst.hasRng() ? inst.rng() : token;
                } else {
                    resolvedCode.add(resolvedInst.clone().selfVID(f("" + i)).as());
                    // A filter's rng is X{0,1} ("one-or-none": emit the object or ∅). Threading that
                    // {0,1} into the next inst makes a map's dom X{1,1} fail to match, but the ∅ branch
                    // can never reach the map — a filter that emits ∅ short-circuits the chain. So drop
                    // the 0 wing ({0,n} → {n,n}) for compile-time typing only, leaving rng() itself maybe
                    // so isFilter()/isPredicate()/gather-classification still read the filter cardinality.
                    token = resolvedInst.isFilter() ? resolvedInst.rng().c(resolvedInst.c().max()) : resolvedInst.rng();
                    if (resolvedInst.isGather()) {
                        LOG.trace("  {{m}}==|{{/m}} marking {{y}}barrier{{/y}} at %s", resolvedInst);
                    } else if (resolvedInst.isInitial()) {
                        LOG.trace("  {{g}}==>{{/g}} marking {{y}}initial{{/y}} at %s", resolvedInst);
                        token = resolvedInst.arg(0).isType() ? resolvedInst.arg(0) : resolvedInst.arg(0).type();
                    }
                }
                token = token.c(c -> c.mult(resolvedInst.c()));
            } catch (final Exception e) {
                resolvedCode.add(inst.clone().selfVID(f("" + i)).as());
                LOG.debug("runtime resolution of %s required", null == inst ? "[0]" : inst);
                fullResolution = false;
            }
            i++;
        }
        final Code resolved = code.jvm(resolvedCode);
        LOG.debug("%s code:\n        [{{g}}COMPILED{{/g}}]\n%s",
                fullResolution ? "{{g}}resolved{{/g}}" : "{{y}}semi-resolved{{/y}}",
                ObjmtronSerializer.prettyPrintCode(resolved));
        return resolved;
    }
}
