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

package studio.phaseshift.metatron.isa.m.type.resolver;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * Strategy interface for instruction resolution.
 * <p>
 * Given a left-hand-side object (lhs), a user instruction being resolved,
 * and a stream of candidate instructions fetched from the router,
 * the resolver selects the best matching instruction.
 * <p>
 * Different implementations can use different selection strategies:
 * <ul>
 *   <li>{@link FirstFindInstResolver} - uses first matching candidate (original behavior)</li>
 *   <li>{@link ScoringInstResolver} - scores candidates by specificity, selects best match</li>
 * </ul>
 * <p>
 * Use {@link #get()} to access the current resolver and {@link #set(InstResolver)} to change it.
 */
@FunctionalInterface
public interface InstResolver {

    /**
     * Holder for the currently active resolver instance.
     * Defaults to {@link ScoringInstResolver}.
     */
    AtomicReference<InstResolver> INSTANCE = new AtomicReference<>(new ScoringInstResolver());

    /**
     * Get the currently active resolver.
     *
     * @return the current InstResolver instance
     */
    static InstResolver get() {
        return INSTANCE.get();
    }

    /**
     * Set the active resolver implementation.
     *
     * @param resolver the new resolver to use
     * @return the previous resolver
     */
    static InstResolver set(final InstResolver resolver) {
        return INSTANCE.getAndSet(resolver);
    }

    /**
     * Resolve an instruction by selecting the best match from candidates.
     *
     * @param lhs        the left-hand-side object being operated on
     * @param userInst   the user instruction being resolved (contains args, dom/rng hints)
     * @param candidates stream of candidate instructions fetched from router
     * @return the resolved instruction with bound generics and resolved args, or null if no match
     */
    /**
     * Resolve an instruction with resolver-owned candidate fetching.
     * This is the primary resolution method. Implementations own the full pipeline:
     * candidate fetching, selection, generics binding, and argument resolution.
     */
    Inst resolveInst(Obj lhs, Inst userInst);

    /**
     * Legacy resolution method: receives pre-fetched candidates.
     * Default fetches candidates and delegates to {@link #resolveInst}.
     */
    default Inst resolve(final Obj lhs, final Inst userInst, final Stream<Obj> candidates) {
        return resolveInst(lhs, userInst);
    }

    /**
     * Resolve a full instruction chain. Default threads output-type of each
     * instruction as input-type of the next via {@link Inst#resolve(Obj)}.
     */
    default Code resolveCode(final Obj lhs, final Code code) {
        final var LOG = studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.log(this);
        Obj token = lhs.isType() ? lhs : lhs.type();
        final List<Inst> resolvedCode = new ArrayList<>();
        boolean fullResolution = true;
        int i = 0;
        for (final Inst inst : code.insts()) {
            try {
                final Inst instToResolve = inst.tid().basePath()
                        .equals(studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID)
                        ? inst.rng(inst.arg(0).asType()).asInst()
                        : inst;
                final Inst resolvedInst = instToResolve.resolve(token);
                if (!resolvedInst.hasDom()) {
                    resolvedCode.add(inst.clone().selfVID(f("" + i)).as());
                    token = inst.hasRng() ? inst.rng() : token;
                } else {
                    resolvedCode.add(resolvedInst.clone().selfVID(f("" + i)).as());
                    token = resolvedInst.rng();
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
                studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer.prettyPrintCode(resolved));
        return resolved;
    }
}
