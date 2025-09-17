/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.mem.StackSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.Palette;

public interface Router extends Obj {

    ThreadLocal<StackSpace> INST_STACK =   ThreadLocal.withInitial(() -> new StackSpace(fURI.of("+"),Router.global().tid().extend("/stack")));

    static Router global() {
        return BootLoader.ROUTER;
    }

    static StackSpace stack() {
        return INST_STACK.get();
    }

    Obj read(final fURI vid);

    Obj write(final fURI vid, final Obj obj);

    boolean hasSpaceFor(final fURI vid);

    void registerSpace(final Space space);

    void registerRewrite(final fURI small, final fURI big);

    fURI rewrite(final fURI furi, final boolean big);

    <S extends Space> S getSpace(final fURI vid);

    default String toString(final Palette palette) {
        return Graphitty.string("!b%s!g:[!yrouter!g]!!".formatted(this.tid().toString()));
    }
}
