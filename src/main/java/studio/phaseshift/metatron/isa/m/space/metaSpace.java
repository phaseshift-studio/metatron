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
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.net.MServer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.SPACE_CONFIG;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class metaSpace extends AbstractSpace<MServer> {

    public static final fURI META_SPACE_TID = M_ISA_TID.extend("space/meta");
    protected final fURI host;
    protected final Space cache;
    protected final List<fURI> peers = new ArrayList<>();
    protected final int selfIndex;

    protected static final Rec META_SPACE_CONFIG = SPACE_CONFIG.plus(
            rec(uri(HOST), URI_TYPE,
                    uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                    uri(PEERS), lst(URI_TYPE.<Type>maybeSome())));

    public static final Type META_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(META_SPACE_TID)
            .constructor(instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(META_SPACE_TID), //constructor
                    lst(isa_(META_SPACE_CONFIG).tryToInst()),
                    (lhs, inst) -> metaSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();


    protected metaSpace(final MServer sjvm, final Map<Obj, Obj> jvm, final fURI vid) {
        super(sjvm, jvm, META_SPACE_TID, vid);
        this.host = this.at(uri(HOST)).uriValue();
        this.cache = memSpace.of(rec(jvm), null);
        this.at(uri(PEERS)).orElse(lst(uri(this.host))).elements().forEach(e -> this.peers.add(e.uriValue()));
        this.selfIndex = IteratorUtil.indexedStream(this.peers.iterator()).filter(p -> Objects.equals(p.get1().host(), this.host.host())).findFirst().map(Tuple.Pair::get0).orElse(-1);
        if (this.selfIndex == -1)
            throw MTronException.of("no cluster position found for host %s", this.host.host());
        this.sjvm.start();
    }

    public static metaSpace of(final Rec config, final fURI vid) {
        final MServer server = new MServer(
                config.at(HOST).uriValue(),
                config.at(PEERS).orElse(lst(config.at(HOST).asUri())).asLst().elements().map(Obj::uriValue).toList());
        return new metaSpace(server, config.jvm(), vid);
    }

    @Override
    public void close() {
        this.cache.close();
        this.sjvm().close();
        super.close();
    }

    @Override
    public Obj read(final fURI vid) {
        final fURI lowerPattern = this.rewrite(vid, false);
        final int peerIndex = fURIHasher.getNodeIndex(lowerPattern.toString(), this.peers.size());
        if (this.selfIndex == peerIndex) {
            LOG.debug("{{y}}%s{{X}} [{{c}}reading{{X}}]:  {{y}}%s {{g}}=> {{y}}%s", this.host, vid, lowerPattern);
            return this.cache.read(lowerPattern);
        } else {
            final fURI peer = this.peers.get(peerIndex);
            try {
                final Obj toSend = from_(uri(lowerPattern)).tryToInst();
                LOG.debug("{{y}}%s {{g}}=> {{y}}%s{{g}}: %s", this.host, peer, toSend);
                final Obj result = this.sjvm.sendRecv(peer, toSend);
                return result;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
        /*return studio.phaseshift.metatron.furi.Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return studio.phaseshift.metatron.furi.Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });*/
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final fURI lowerPattern = this.rewrite(vid,false);
        final int peerIndex = fURIHasher.getNodeIndex(lowerPattern.toString(), this.peers.size());
        if (this.selfIndex == peerIndex) {
            LOG.debug("{{y}}%s{{X}} [{{c}}writing{{X}}]:  {{y}}%s {{g}}=> {{y}}%s{{X}} %s", this.host, vid, lowerPattern, obj);
            return this.cache.write(lowerPattern, obj);
        } else {
            final fURI peer = this.peers.get(peerIndex);
            try {
                final Obj toSend = start_(obj).to_(uri(lowerPattern));
                LOG.debug("{{y}}%s {{g}}=> {{y}}%s{{g}}: %s", this.host, peer, toSend);
                return this.sjvm.sendRecv(peer, toSend);
            } catch (final Exception e) {
                throw MTronException.of(e);
            }

        }
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        /*return (pattern) -> {
            final fURI lowerPattern = Space.Helper.toRewrite(pattern, this.rewrite);
            final int peerIndex = fURIHasher.getNodeIndex(lowerPattern.toString(), this.peers.size());
            if (this.selfIndex == peerIndex) {
                LOG.info("{{y}}%s{{X}} [{{c}}reading{{X}}]:  {{y}}%s {{g}}=> {{y}}%s", this.host, pattern, lowerPattern);
                return Router.readFromSpace(lowerPattern).stream().map(x -> lowerPattern.isBranch() ?
                        Space.furiObj.of(Space.Helper.fromRewrite(x.asRel().first().uriValue(), this.rewrite), x.asRel().second()) :
                        Space.furiObj.of(lowerPattern, x)).iterator();
            } else {
                final fURI peer = this.peers.get(peerIndex);
                try {
                    if (pattern.pathLength() > 0) {
                        final Obj toSend = from_(uri(lowerPattern)).tryToInst();
                        LOG.info("{{y}}%s {{g}}=> {{y}}%s{{g}}: %s", this.host, peer, toSend);
                        final Obj result = this.server.sendRecv(peer, toSend);
                        return result.stream().map(x -> lowerPattern.isBranch() ?
                                Space.furiObj.of(Space.Helper.fromRewrite(x.asRel().first().uriValue(), this.rewrite), x.asRel().second()) :
                                Space.furiObj.of(lowerPattern, x)).iterator();
                    }
                    return Collections.emptyIterator();
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }

            }
        };*/
        throw new UnsupportedOperationException("directReader not supported for metaSpace");
    }


    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
       /* return (pattern, obj) -> {
            final fURI lowerPattern = Space.Helper.toRewrite(pattern, this.rewrite);
            final int peerIndex = fURIHasher.getNodeIndex(lowerPattern.toString(), this.peers.size());
            if (this.selfIndex == peerIndex) {
                LOG.info("{{y}}%s{{X}} [{{c}}writing{{X}}]:  {{y}}%s {{g}}=> {{y}}%s{{X}} %s", this.host, pattern, lowerPattern, obj);
                return Router.writeToSpace(lowerPattern, obj);
            } else {
                final fURI peer = this.peers.get(peerIndex);
                try {
                    final Obj toSend = start_(obj).to_(uri(lowerPattern)).tryToInst();
                    LOG.info("{{y}}%s {{g}}=> {{y}}%s{{g}}: %s", this.host, peer, toSend);
                    return this.server.sendRecv(peer, toSend);
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }

            }
        };*/
        throw new UnsupportedOperationException("directWriter not supported for metaSpace");
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

