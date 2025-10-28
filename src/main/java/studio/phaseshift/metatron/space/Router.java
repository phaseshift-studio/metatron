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

package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.NoObj;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.router.net.MServer;
import studio.phaseshift.metatron.space.stack.StackSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;

public interface Router extends Obj, Space, Closeable {

    ThreadLocal<StackSpace> INST_STACK = ThreadLocal.withInitial(() -> new StackSpace(fURI.of("+/#"), Router.global().vid().extend("stack")));

    static boolean loaded() {
        return null != BootLoader.ROUTER;
    }

    static Router global() {
        return BootLoader.ROUTER;
    }

    static Obj readFromSpace(final fURI vid) {
        return Router.loaded() ? BootLoader.ROUTER.read(vid) : NoObj.single();
    }
    
    static Obj writeToSpace(final fURI vid, final Obj obj) {
        return Router.loaded() ? BootLoader.ROUTER.write(vid,obj) : NoObj.single();
    }

    static StackSpace stack() {
        return INST_STACK.get();
    }

    MServer server();

    void start();

    Obj read(final fURI vid);

    default Obj read(final String vid) {
        return this.read(fURI.of(vid));
    }

    @Override
    default fURI pattern() {
        return fURI.ALL;
    }

    default <O extends Obj> O read(final String vid, final Class<O> oClass) {
        try {
            return oClass.getConstructor(Obj.class).newInstance(this.read(fURI.of(vid)));
        } catch (final Exception e) {
            throw Graphitty.log(this).except(e);
        }
    }

    Obj write(final fURI vid, final Obj obj);

    default Obj write(final Obj obj) {
        if (null == obj.vid()) {
            throw MTronException.of("direct obj writing requires obj already be in space");
        }
        return this.write(obj.vid(), obj);
    }

    default Obj write(final String vid, final Obj obj) {
        return this.write(fURI.of(vid), obj);
    }

    default Obj[] write(final Object... vidObj) {
        int count = (int) ((double) vidObj.length / 2.0d);
        final Obj[] result = new Obj[count];
        for (int i = 0; i < vidObj.length; i = i + 2) {
            result[--count] = this.write(fURI.of(vidObj[i]), (Obj) vidObj[i + 1]);
        }
        return result;
    }

    boolean hasSpaceFor(final fURI vid);

    void addSpace(final Space space);

    void removeSpace(final fURI vid);

    void registerRewrite(final fURI small, final fURI big);

    fURI rewrite(final fURI furi, final boolean big);

    <S extends Space> S getSpace(final fURI vid);

    @Override
    Iterable<Space> jvm();

    @Override
    default void close() {
        this.jvm().forEach(s -> this.removeSpace(s.vid()));
    }

    class Helpers {
        public static String routerToString(final Router router) {
            return Graphitty.string("{{b}}" + router.tid() + "{{g}}::[{{c}}global{{g}}]@{{b}}" + router.vid() + "{{X}}");
        }
    }
}
