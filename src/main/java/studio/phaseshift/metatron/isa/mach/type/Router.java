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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.stackSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.router.NoObjRouter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.SPACE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Router extends Space {

    enum Reference {
        ATTACHED,
        DETACHED;

        public boolean isDetached() {
            return this == DETACHED;
        }

        public boolean isAttached() {
            return this == ATTACHED;
        }
    }

    fURI STACK_PATTERN = f("+/#");
    ThreadLocal<stackSpace> THREAD_STACK = ThreadLocal.withInitial(() -> new stackSpace(STACK_PATTERN));

    static boolean loaded() {
        return null != BootLoader.ROUTER;
    }

    static Router global() {
        return null == BootLoader.ROUTER ? NoObjRouter.single() : BootLoader.ROUTER;
    }

    static Stream<Obj> streamFromSpace(final fURI vid, final Reference type) {
        if (!Router.loaded())
            return Stream.empty();
        final Stream<IdObj> stream = BootLoader.ROUTER.readStream(vid);
        if (vid.isBranch())
            return stream.map(pair -> rel(uri(pair.furi()), type == Reference.ATTACHED
                    ? pair.obj().vid(pair.furi()) : pair.obj().vid(null)));
        return stream.map(pair -> type == Reference.ATTACHED
                ? pair.obj().vid(pair.furi()) : pair.obj().vid(null));
    }

    static Obj readFromSpace(final fURI vid) {
        return Router.loaded() ? BootLoader.ROUTER.read(vid) : noobj();
    }

    static Obj readFromSpace(final String vid) {
        return Router.readFromSpace(f(vid));
    }

    static Obj writeToSpace(final fURI vid, final Obj obj) {
        return Router.loaded() ? BootLoader.ROUTER.write(vid, obj) : noobj();
    }

    static Obj writeToSpace(final String vid, final Obj obj) {
        return writeToSpace(f(vid), obj);
    }

    static Obj writeToSpace(final Obj obj) {
        return writeToSpace(obj.vid(), obj);
    }

    static stackSpace stack() {
        return THREAD_STACK.get();
    }

    default Rec spaces() {
        return this.at(uri(SPACE)).as();
    }

    void start();

    Obj read(final fURI vid);

    default Obj read(final String vid) {
        return this.read(f(vid));
    }

    @Override
    default fURI pattern() {
        return ALL;
    }

    Obj write(final fURI vid, final Obj obj);

    default Obj write(final String vid, final Obj obj) {
        return this.write(f(vid), obj);
    }

    default Obj[] write(final Object... vidObj) {
        int count = (int) ((double) vidObj.length / 2.0d);
        final Obj[] result = new Obj[count];
        for (int i = 0; i < vidObj.length; i = i + 2) {
            result[--count] = this.write(f(vidObj[i].toString()), (Obj) vidObj[i + 1]);
        }
        return result;
    }

    boolean hasSpaceFor(final fURI vid);

    void addSpace(final Space space);

    void removeSpace(final fURI vid);

    <SPACE extends Space> SPACE getSpace(final fURI pattern);

    void registerRedirect(final fURI small, final fURI big);

    void unregisterRedirect(final fURI small, final fURI big);

    void registerPrefix(final fURI prefix, final fURI vid);

    fURI redirect(final fURI furi, final boolean big);

    <SPACE extends Space> SPACE getSpaceFor(final fURI vid);

    class Helper {
        public static String routerToString(final Router router) {
            return router.tid() + "::[pattern=>#]@" + router.vid();
        }
    }

    final class RouterType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(

            ));
        }

    }
}
