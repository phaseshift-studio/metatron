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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.space.stackSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.lang.sys.router.impl.MServer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.NOOBJ;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.REWRITE_INST_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.ROUTER_TID;

public interface Router extends Obj, Space {

    ThreadLocal<stackSpace> THREAD_STACK = ThreadLocal.withInitial(() -> new stackSpace(f("+/#")));

    static boolean loaded() {
        return null != BootLoader.ROUTER;
    }

    static Router global() {
        return null == BootLoader.ROUTER ? DummyRouter.single() : BootLoader.ROUTER;
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
        return Router.loaded() ? BootLoader.ROUTER.write(vid, obj) : noobj();
    }

    static Obj writeToSpace(final Obj obj) {
        return writeToSpace(obj.vid(), obj);
    }

    static stackSpace stack() {
        return THREAD_STACK.get();
    }

    default Rec spaces() {
        return this.jvm().get(uri(Tokens.SPACE)).as();
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
    
    IOStats stats();

    interface IOStats {

        IOStats incrBytesRecv(final long bytes);

        IOStats incrBytesSent(final long bytes);

        IOStats incrRunningMonads(final long runningMonads);

        IOStats incrHaltedMonads(final long haltedMonads);

        IOStats incrKilledMonads(final long killedMonads);

        IOStats incrBarrierMonads(final long barrierMonads);

        long bytesSent();

        long bytesRecv();

        void resetMonads();

        long runningMonads();

        long haltedMonads();

        long killedMonads();

        long barrierMonads();

    }

    class Helper {
        public static String routerToString(final Router router) {
            return router.tid() + "::[pattern=>#]@" + router.vid();
        }
    }

    class DummyRouter extends MRec implements Router {

        public static final DummyRouter INSTANCE = new DummyRouter();

        public static final DummyRouter single() {
            return INSTANCE;
        }

        public DummyRouter() {
            super(Map.of(), ROUTER_TID, null);
        }

        @Override
        public MServer server() {
            return null;
        }

        @Override
        public void start() {

        }

        @Override
        public Object sjvm() {
            return Map.of();
        }

        @Override
        public Obj read(fURI vid) {
            return noobj();
        }

        @Override
        public Obj write(fURI vid, Obj obj) {
            return noobj();
        }

        @Override
        public boolean hasSpaceFor(fURI vid) {
            return false;
        }

        @Override
        public void addSpace(Space space) {

        }

        @Override
        public void removeSpace(fURI vid) {

        }

        @Override
        public void registerRewrite(fURI small, fURI big) {

        }

        @Override
        public fURI rewrite(fURI furi, boolean big) {
            return NOOBJ;
        }

        @Override
        public <S extends Space> S getSpace(fURI vid) {
            return noobjSpace.single();
        }

        @Override
        public IOStats stats() {
            return new IOStats() {

                @Override
                public IOStats incrBytesRecv(long bytes) {
                    return this;
                }

                @Override
                public IOStats incrBytesSent(long bytes) {
                    return this;
                }

                @Override
                public IOStats incrRunningMonads(long runningMonads) {
                    return this;
                }

                @Override
                public IOStats incrHaltedMonads(long haltedMonads) {
                    return this;
                }

                @Override
                public IOStats incrKilledMonads(long killedMonads) {
                    return this;
                }

                @Override
                public IOStats incrBarrierMonads(long barrierMonads) {
                    return this;
                }

                @Override
                public long bytesSent() {
                    return 0;
                }

                @Override
                public long bytesRecv() {
                    return 0;
                }

                @Override
                public void resetMonads() {

                }

                @Override
                public long runningMonads() {
                    return 0;
                }

                @Override
                public long haltedMonads() {
                    return 0;
                }

                @Override
                public long killedMonads() {
                    return 0;
                }

                @Override
                public long barrierMonads() {
                    return 0;
                }
            };
        }
    }

    final class RouterType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                  
            ));
        }

    }
}
