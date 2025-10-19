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

package studio.phaseshift.metatron.vm;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.MInstSet;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet;
import studio.phaseshift.metatron.lang.obj.mtron.mtronRewrites;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class machInstSet extends MInstSet {

    public static final fURI MACH_TID = f("/mtron/mach");
    public static final fURI MONAD_TID = f("/mtron/mach/monad");

    public machInstSet(final fURI vid) {
        super(MACH_TID, vid);
    }

    public static machInstSet of(final fURI vid) {
        return new machInstSet(vid);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(MACH_TID), T(MONAD_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> rewrites() {
        return new mtronRewrites(mtronRewrites.REWRITE_TID, this.vid.extend("rewrite")).insts();
    }

    @Override
    public Set<Inst> insts() {
        return Set.of();
    }
}
