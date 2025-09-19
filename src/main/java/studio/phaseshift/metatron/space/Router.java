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

    class Helpers {
        public static String routerToString(final Router router) {
            return Graphitty.string("{{b}}" + router.tid() + "{{g}}::[{{c}}global{{g}}]@{{b}}" + router.vid() + "{{X}}");
        }
    }

    ThreadLocal<StackSpace> INST_STACK = ThreadLocal.withInitial(() -> new StackSpace(fURI.of("+"), Router.global().tid().extend("stack")));

    static Router global() {
        return BootLoader.ROUTER;
    }

    static StackSpace stack() {
        return INST_STACK.get();
    }

    Obj read(final fURI vid);

    default Obj read(final String vid) {
        return this.read(fURI.of(vid));
    }


    default <O extends Obj> O read(final String vid, final Class<O> oClass) {
        try {
            return oClass.getConstructor(Obj.class).newInstance(this.read(fURI.of(vid)));
        } catch (final Exception e) {
            throw Graphitty.log(this).except(e);
        }
    }

    Obj write(final fURI vid, final Obj obj);

    default Obj write(final String vid, final Obj obj) {
        return this.write(fURI.of(vid), obj);
    }

    boolean hasSpaceFor(final fURI vid);

    void registerSpace(final Space space);

    void registerRewrite(final fURI small, final fURI big);

    fURI rewrite(final fURI furi, final boolean big);

    <S extends Space> S getSpace(final fURI vid);

    default String toString(final Palette palette) {
        return Graphitty.string("!b%s!g:[!yrouter!g]!!".formatted(this.tid().toString()));
    }
}
