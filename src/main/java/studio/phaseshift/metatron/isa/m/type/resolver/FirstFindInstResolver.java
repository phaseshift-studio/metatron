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

package studio.phaseshift.metatron.isa.m.type.resolver;

import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;

import java.util.Objects;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;

/**
 * Original instruction resolver that uses {@code findFirst()} selection.
 * <p>
 * This resolver filters candidates and returns the first one that matches all criteria.
 * The order of candidates depends on insertion order in the InstSet, which can lead
 * to non-deterministic behavior when multiple instructions with the same name exist.
 * <p>
 * This is preserved for backward compatibility and A/B testing against newer resolvers.
 */
public class FirstFindInstResolver implements InstResolver {

    @Override
    public Inst resolve(final Obj lhs, final Inst userInst, final Stream<Obj> candidates) {
        return candidates
                .filter(Obj::isInst)
                .map(Obj::asInst)
                .filter(i -> (i.args().isEmpty() && userInst.arg(0).isNoObj()) || i.args().isRec() || i.args().count() >= userInst.args().count())
                .filter(i -> !lhs.isInst() || (i.dom().baseType().equals(M_ISA_INST_TID)))
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
}
