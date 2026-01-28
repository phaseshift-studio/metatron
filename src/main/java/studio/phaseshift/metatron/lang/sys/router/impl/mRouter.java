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

package studio.phaseshift.metatron.lang.sys.router.impl;

import studio.phaseshift.metatron.Registry;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.io.serial.Serializers;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.space.stackSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.lang.jre.ObjFieldReflection;
import studio.phaseshift.metatron.lang.jre.ObjReflection;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_ISA_TID;

@ObjReflection
public class mRouter extends MSpace<MServer> implements Router {

    public static final Serializers SERIALIZERS = new Serializers();

    public static final Uri PRIMARY = uri("primary");
    public static final fURI ROUTER_TID = SYS_ISA_TID.extend("router");
    private static final Set<fURI> READ_AS_NOOBJ = Set.of(fURI.ALL.maybeSome(), fURI.ALL.maybe(), fURI.ALL);
    private final GraphittyLogger LOG = Graphitty.log(this);
    @ObjFieldReflection(tid = "/m/str")
    public static final String test = "testes";
    protected final IOStats iostats = new mIOStats();

    @ObjFieldReflection
    private final Map<fURI, Set<fURI>> smallToBigRewrites = new HashMap<>();
    @ObjFieldReflection
    private final Map<fURI, fURI> bigToSmallRewrites = new HashMap<>();
    private fURI primary = M_ISA_TID;

    public mRouter(final fURI host, final fURI vid) {
        super(new MServer(host), new ConcurrentHashMap<>(Map.of(
                        uri(PATTERN), uri(ALL),
                        PRIMARY, uri(M_ISA_TID),
                        uri(Tokens.SPACE), rec(new ConcurrentHashMap<>(Map.of(uri("+/#"), new stackSpace(f("+/#"))))))),
                ROUTER_TID,
                vid);

        LOG.info("local router at %s", this.vid.toUri());
        LOG.info("available serializers: %s", lst(SERIALIZERS.getSerializers().recValue().keySet().stream().toList()));
    }


    private static Obj appendOnRead(final boolean send, final Obj base, final Obj addition) {
        return addition.isNoObj() ? base : (send ? base.append(rel(addition.vid().toUri(), addition)) : base.append(addition));
    }

    @Override
    public MServer server() {
        return this.sjvm();
    }

    public void start() {
        this.server().start();
    }

    public Rec put(final Obj key, final Obj value) {
        if (key.equals(PRIMARY))
            this.primary = value.uriValue();
        return super.put(key, value);
    }

    public synchronized void close() {
        super.close();
        this.spaces().jvm().clear();
    }

    @Override
    public IOStats stats() {
        return this.iostats;
    }

    public void registerRewrite(final fURI small, final fURI big) {
        this.smallToBigRewrites.compute(small, (k, v) -> {
            if (null == v) {
                final Set<fURI> set = new HashSet<>();
                set.add(big.basePath());
                return set;
            } else {
                v.add(big.basePath());
                return v;
            }
        });
        this.bigToSmallRewrites.put(big, small);
    }

    @Override
    public fURI rewrite(final fURI furi, final boolean big) {
        if (furi.isGeneric())
            return furi;
        fURI temp;
        if (big) {
            final Set<fURI> set = this.smallToBigRewrites.getOrDefault(furi.basePath(), Set.of(furi));
            if (set.size() > 1) {
                final Iterator<fURI> furis = set.stream().filter(f -> f.hasPrefix(this.primary)).iterator();
                temp = furis.hasNext() ? furis.next() : set.iterator().next();
            } else {
                temp = set.iterator().next();
            }
        } else {
            temp = this.bigToSmallRewrites.getOrDefault(furi.basePath(), furi);
        }
        temp = temp.c(furi.c()).queryMap(furi.queryMap());
        temp = temp.hasDom() ? temp.dom(this.rewrite(temp.dom(), big)) : temp;
        temp = temp.hasRng() ? temp.rng(this.rewrite(temp.rng(), big)) : temp;
        return temp.resolve();
    }

    @Override
    public void addSpace(final Space space) {
        this.spaces().jvm().values().stream()
                .map(Obj::<Space>as)
                .filter(s -> space.pattern().bimatches(s.pattern()))
                .findAny()
                .ifPresent(s -> {
                    LOG.error("%s and %s have overlapping address spaces: %s <=> %s", space.pattern(), s.pattern(), space, s);
                });
        this.spaces().jvm().put(null == space.vid() ? space.pattern().toUri() : space.vid().toUri(), space);
        Space.Helper.spaceOpenLog(this, space);
    }

    @Override
    public void removeSpace(final fURI vid) {
        this.spaces().elements()
                .filter(s -> Objects.equals(s.second().vid(), vid) || Objects.equals(s.first(), vid))
                .forEach(s -> {
                    try {
                        final Space space = (Space) this.spaces().jvm().remove(s.first());
                        if (null != space) {
                            Space.Helper.spaceCloseLog(this, space);
                            //space.close();
                        }
                    } catch (final Exception e) {
                        LOG.error(e);
                    }
                });
    }

    @Override
    public <S extends Space> S getSpace(final fURI match) {
        if (match.matches(fURI.NOOBJ))
            return noobjSpace.single();
        //     final fURI mvid = this.smallToBigRewrites.getOrDefault(vid,vid);
        final Optional<S> space = this.spaces().jvm().values().stream() // using jvm() for speed (given the heavy use of this method)
                .map(Obj::<Space>as)
                //.filter(s -> s.status().equals(Status.active))
                .filter(s -> match.basePath().matches(s.pattern()))
                .map(s -> (S) s)
                .findAny();
        if (space.isPresent())
            return space.get();
        else if (match.basePath().matches(f("+/#")))
            return (S) THREAD_STACK.get();
        else if (Registry.open().has(match))
            return Registry.open().load(match);
        else if (!BOOTING)
            throw MTronException.of("no active space supports pattern %s", match.toUri(false));
        else
            return noobjSpace.single();
    }

    @Override
    public Obj read(final fURI vid) {
        if (vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return noobj();
        // if (vid.hasAuthority())
        //   return this.server().sendRecv((a, b) -> a.authority().matches(b.remoteHost().authority()), vid, from_(vid.localize().toUri()).tryToInst());
        /// ///////////////////
        if (vid.isGeneric())
            return T(vid);
        final fURI local = vid;
        if (local.matches(f("+/#"))) {
            final Obj stack = Router.stack().read(local.basePath());
            if (!stack.isNoObj())
                return stack;
        }
        final Space space = this.getSpace(local);
        final Obj obj = space.read(local);
        /*if (obj.isNoObj()) { // TODO: only needed when reasoning on insts (should we gut it?)
            final fURI vidbig = vid.big();
            if (!vid.equals(vidbig))
                return this.read(vidbig);
        }*/
        return obj;

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        /*if (vid.hasAuthority()) {
            this.server().send((a, b) -> a.authority().matches(b.remoteHost().authority()), vid, start_(obj.vid(null)).to_(vid.localize().toUri()).tryToInst());
            return obj;
        }*/
        /// ///////////////
        final Space space = this.getSpace(vid);
        LOG.trace("writing %s {{g}}=>{{b}} %s{{X}} in %s", obj, vid, space);
        return space.write(vid, obj);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.spaces().jvm().values().stream().map(Obj::<Space>as).anyMatch(s -> vid.matches(s.pattern()));
    }

    @Override
    public mRouter apply(final Obj other) {
        return null;
    }

    @Override
    public Router clone() {
        Space.Helper.noCloneWarning(this);
        return this;
    }

    @Override
    public Router clone(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = jvm;
        this.tid = tid;
        this.vid = vid;
        return this;
    }

    @Override
    public String toString() {
        return Router.Helper.routerToString(this);
    }

    static class mIOStats implements IOStats {

        protected long bytesSent = 0;
        protected long bytesRecv = 0;

        @Override
        public IOStats incrBytesRecv(long bytes) {
            this.bytesRecv += bytes;
            return this;
        }

        @Override
        public IOStats incrBytesSent(long bytes) {
            this.bytesSent += bytes;
            return this;
        }

        @Override
        public long bytesSent() {
            return this.bytesSent;
        }

        @Override
        public long bytesRecv() {
            return bytesRecv;
        }
    }
}
