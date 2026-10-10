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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.resolver.Binder;
import studio.phaseshift.metatron.isa.m.type.resolver.Selector;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_FIRSTFIND_SELECTOR_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * FirstFindSelector — the concrete {@code firstfind_selector::T}: selection without scoring. It
 * fetches candidates from the lhs rec (by base path) and the space at that path, keeps the first
 * one that matches the call's arity and that its binder can make concrete, and returns it.
 * Selection is therefore order-dependent — declaration/read order decides, not specificity.
 *
 * The pre-scoring strategy, kept as the A/B counterpart of {@link SpecificitySelector}: it pins the
 * original first-match behavior, and being a Selector it composes with any Binder.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FirstFindSelector extends MRec implements Selector {

    private static final FirstFindSelector INSTANCE = new FirstFindSelector(mutableMap(), MACH_FIRSTFIND_SELECTOR_TID, null);

    public static FirstFindSelector single() {
        return INSTANCE;
    }

    public FirstFindSelector() {
        this(mutableMap(), MACH_FIRSTFIND_SELECTOR_TID, null);
    }

    public FirstFindSelector(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Inst apply(final Obj lhs, final Inst userInst, final Binder binder) {
        if (userInst.hasf())
            return userInst;
        if (userInst.isNoObj())
            return null;

        return this.fetchCandidates(lhs, userInst)
                .filter(Obj::isInst)
                .map(Obj::asInst)
                .filter(i -> (i.args().isEmpty() && userInst.arg(0).isNoObj()) || i.args().isRec() || i.args().count() >= userInst.args().count())
                .filter(i -> !lhs.isInst() || (i.dom().baseTypeID().equals(M_ISA_INST_TID)))
                .map(i -> binder.apply(lhs, userInst, this.hint(i, userInst)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * The first-match path's legacy hint: replace the candidate's dom/rng from the call, with no
     * cast rebind (the first-match strategy has never applied one).
     */
    private Inst hint(final Inst candidate, final Inst userInst) {
        Inst hinted = userInst.hasDom() ? candidate.dom(userInst.dom()) : candidate;
        hinted = userInst.hasRng() ? hinted.rng(userInst.rng()) : hinted;
        return hinted;
    }

    private Stream<Obj> fetchCandidates(final Obj lhs, final Inst userInst) {
        final fURI basePath = userInst.tid().basePath();

        Stream<Obj> fromLhs = Stream.empty();
        if (lhs.isRec()) {
            final Obj at = lhs.asRec().at(basePath);
            if (!at.isNoObj())
                fromLhs = at.stream();
        }

        final Stream<Obj> fromSpace = Machine.read(basePath).stream();

        return Stream.concat(fromLhs, fromSpace);
    }
}
