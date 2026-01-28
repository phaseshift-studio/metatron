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

package studio.phaseshift.metatron.isa.m.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.router.impl.MServer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.fnull;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class metaSpace extends MSpace<MServer> {

    public static final fURI META_SPACE_TID = M_ISA_TID.extend("space/meta");
    protected final fURI host;
    protected final Space cache;
    protected final List<fURI> peers = new ArrayList<>();
    protected final int selfIndex;
    protected final MServer server;

    protected static final Rec CONFIG = rec(
            uri(HOST), URI_TYPE,
            uri(PATTERN), URI_TYPE,
            uri(PEERS), lst(T(URI_TID.maybeSome())));

    public static final Type META_SPACE_TYPE = T(META_SPACE_TID,
            isa_(CONFIG), // predicate
            instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(META_SPACE_TID), //constructure
                    lst(isa_(CONFIG).tryToInst()),
                    (lhs, inst) -> {
                        final Space space = metaSpace.of(inst.arg(0).asRec(), fnull);
                        Router.global().addSpace(space);
                        return space;
                    }));


    protected metaSpace(final MServer sjvm, final Map<Obj, Obj> jvm, final fURI vid) {
        super(sjvm, jvm, META_SPACE_TID, vid);
        this.host = jvm.get(uri(HOST)).uriValue();
        this.cache = (Space) jvm.get(uri(CACHE));
        Rec c = rec(jvm);
        c.at(uri(PEERS)).asLst().elements().forEach(e -> this.peers.add(e.uriValue()));
        this.selfIndex = IteratorUtil.indexedStream(this.peers.iterator()).filter(p -> Objects.equals(p.get1().host(), this.host.host())).findFirst().map(Tuple.Pair::get0).orElse(-1);
        if (this.selfIndex == -1)
            throw MTronException.of("no cluster position found for host %s", this.host.host());
        this.server = sjvm;
    }

    public static metaSpace of(final Rec config, final fURI vid) {
        final MServer server = new MServer(config.at(HOST).uriValue());
        server.start();
        final memSpace cache = memSpace.of(config.at(PATTERN).uriValue(), fnull);
        final Map<Obj, Obj> conf = new LinkedHashMap<>(config.jvm());
        conf.put(uri(CACHE), cache);
        return new metaSpace(server, conf, vid);
    }

    @Override
    public void close() {
        this.sjvm().close();
        Router.global().removeSpace(this.vid());
        super.close();
    }

    @Override
    public Obj read(final fURI vid) {
        return studio.phaseshift.metatron.furi.Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return studio.phaseshift.metatron.furi.Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return studio.phaseshift.metatron.furi.Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
            //return obj;
            return studio.phaseshift.metatron.furi.Q.Helper.processPostWrite(this.qs(), vid, vid, obj)
                    .orElse(studio.phaseshift.metatron.furi.Q.Helper.processQlessWrite(this.qs(), vid, vid, obj)
                            .orElse(obj));
        });
    }

    @Override
    public Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader() {
        return (pattern) -> {
            final int peerIndex = fURIHasher.getNodeIndex(pattern.toString(), this.peers.size());
            if (this.selfIndex == peerIndex) {
                return this.cache.directReader().apply(pattern);
            } else {
                final fURI peer = this.peers.get(peerIndex);
                try {
                    LOG.info("reading: %s => %s", this.host, peer);
                    final Obj result = this.server.sendRecv(peer, mParser.parse("*<%s>".formatted(pattern.asBranch())));
                    return result.stream().map(x -> Tuple.Pair.with(pattern, x)).iterator();
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }

            }
        };
    }


    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {

        return (pattern, obj) -> {
            final int peerIndex = fURIHasher.getNodeIndex(pattern.toString(), this.peers.size());
            if (this.selfIndex == peerIndex) {
                return this.cache.directWriter().apply(pattern, obj);
            } else {
                final fURI peer = this.peers.get(peerIndex);
                try {
                    LOG.info("writing: %s => %s", this.host, peer);
                    final Obj result = this.server.sendRecv(peer, mParser.parse("%s -> %s".formatted(pattern, obj.toCleanString())));
                    return result;
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }

            }
        };
    }

    public static class fURIHasher {
        private static final MessageDigest sha1 = MTronException.wrap(() -> MessageDigest.getInstance("SHA-1"));

        public static int getNodeIndex(final String host, final int clusterSize) {
            try {
                byte[] hostBytes = host.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] hash = sha1.digest(hostBytes);
                int hashInt = ((hash[0] & 0xFF) << 24) |
                        ((hash[1] & 0xFF) << 16) |
                        ((hash[2] & 0xFF) << 8) |
                        (hash[3] & 0xFF);
                return Math.abs(hashInt) % clusterSize;
            } catch (final Exception e) {
                throw MTronException.of("error hashing host %s: %s", host, e);
            }
        }
    }
}

