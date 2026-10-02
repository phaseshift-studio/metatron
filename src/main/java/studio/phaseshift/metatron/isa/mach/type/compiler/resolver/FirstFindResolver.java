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

package studio.phaseshift.metatron.isa.mach.type.compiler.resolver;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.resolver.InstSelector;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_FIRSTFIND_RESOLVER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * FirstFindResolver — the concrete {@code firstfind_resolver::T}: a {@link Resolver} sibling to
 * {@link ScoringResolver} that pins the original first-match selection strategy. It carries no
 * config (empty rec) and owns its own per-instruction selection — {@link #resolveInst} filters
 * candidates and returns the first match, order-dependent (no specificity scoring) — so a chain
 * resolved through this stage is resolved that way without touching the active global
 * {@link InstSelector}.
 * <p>
 * Preserved for backward compatibility and A/B testing against the {@link ScoringResolver}
 * strategy.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FirstFindResolver extends MRec implements Resolver, InstSelector {

    private static final FirstFindResolver INSTANCE = new FirstFindResolver(mutableMap(), MACH_FIRSTFIND_RESOLVER_TID, null);

    public static FirstFindResolver single() {
        return INSTANCE;
    }

    public FirstFindResolver() {
        this(mutableMap(), MACH_FIRSTFIND_RESOLVER_TID, null);
    }

    public FirstFindResolver(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Code apply(final Obj code) {
        return resolveCode(noobj(), code.asCode());
    }

    /**
     * Resolve a full instruction chain — threads the output type of each inst as the input type of
     * the next via {@link Resolver.Helper#resolveCode} — with this resolver's pinned first-match
     * selection ({@link #resolveInst}), independent of the active global {@link InstSelector}.
     */
    public static Code resolveCode(final Obj lhs, final Code code) {
        return Resolver.Helper.resolveCode(lhs, code, single());
    }

    @Override
    public Inst resolveInst(final Obj lhs, final Inst userInst) {
        if (userInst.hasf())
            return userInst;
        if (userInst.isNoObj())
            return null;

        final Stream<Obj> candidates = fetchCandidates(lhs, userInst);

        return candidates
                .filter(Obj::isInst)
                .map(Obj::asInst)
                .filter(i -> (i.args().isEmpty() && userInst.arg(0).isNoObj()) || i.args().isRec() || i.args().count() >= userInst.args().count())
                .filter(i -> !lhs.isInst() || (i.dom().baseTypeID().equals(M_ISA_INST_TID)))
                .map(i -> userInst.hasDom() ? i.dom(userInst.dom()) : i)
                .map(i -> userInst.hasRng() ? i.rng(userInst.rng()) : i)
                .map(i -> lhs.isInst() ? i : Inst.Helper.bindGenerics(lhs, i, userInst))
                .filter(Objects::nonNull)
                .filter(i -> lhs.isInst() || lhs.test(i.dom()))
                .map(i -> {
                    final Poly<?, ?> resolvedArgs = Inst.Helper.resolveArgs(userInst, i, lhs);
                    if (null == resolvedArgs)
                        return null;
                    return i.args(resolvedArgs);
                })
                .filter(Objects::nonNull)
                .map(i -> i.isInitial() ? i.rng(i.arg(0).type()) : i)
                .map(i -> i.c(userInst.c()))
                .findFirst()
                .orElse(null);
    }

    private Stream<Obj> fetchCandidates(final Obj lhs, final Inst userInst) {
        final fURI basePath = userInst.tid().basePath();

        Stream<Obj> fromLhs = Stream.empty();
        if (lhs.isRec()) {
            final Obj at = lhs.asRec().at(basePath);
            if (!at.isNoObj())
                fromLhs = at.stream();
        }

        final Stream<Obj> fromSpace = Machine.readFromSpace(basePath).stream();

        return Stream.concat(fromLhs, fromSpace);
    }
}