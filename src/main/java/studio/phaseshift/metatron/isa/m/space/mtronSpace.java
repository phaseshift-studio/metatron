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

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.router.impl.MServer;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtronSpace extends MSpace<MServer> {
    
    public static final fURI MTRON_SPACE_TID = f("/m/space/mtron");
    protected final fURI host;
    protected final Space cache;
    protected final MServer server;

    public static final Type MTRON_SPACE_TYPE = T(MTRON_SPACE_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(MTRON_SPACE_TID), lst(isa_(rec(uri(PATTERN), T(URI_TID)/*, uri(Tokens.Q).c(cInt::maybe), T(LST_TID.maybe())*/)).tryToInst()), (lhs, inst) -> {
        final fURI pattern = inst.arg(0).asRec().at(PATTERN).uriValue();
        final Space space = mtronSpace.of(pattern,fnull);
        Router.global().addSpace(space);
        return space;
    }));


    public mtronSpace(final MServer sjvm, final Map<Obj, Obj> jvm, final fURI vid) {
        super(sjvm, jvm, jvm.get(uri(PATTERN)).uriValue(), MTRON_SPACE_TID, vid);
        this.host = jvm.get(uri(HOST)).uriValue();
        this.cache = (Space) jvm.get(uri(CACHE));
        this.server = sjvm;
    }

    public static mtronSpace of(final fURI pattern, final fURI vid) {
        final MServer server = new MServer(f(vid.host()));
        server.start();
        final memSpace cache = memSpace.of(pattern.host(BootLoader.ARGS.at(HOST).uriValue().toString()), fnull);
        return new mtronSpace(server, Map.of(uri(PATTERN), pattern.toUri(), uri(CACHE), cache), vid);
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
            //    if (pattern.equals(fURI.ALL))
            // return this.sjvm().entrySet().stream().map(kv -> Tuple.Pair.with(kv.getKey(), kv.getValue())).iterator();
            //  else {
            if (pattern.matches(this.host))
                return this.cache.directReader().apply(pattern);
            else {
                final Obj result = this.server.sendRecv(pattern, mParser.parse("*%s".formatted(pattern.asBranch())));
                return result.stream().map(x -> Tuple.Pair.with(pattern, x)).iterator();
            }
            // }
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            // if (pattern.hasPattern()) {
            //   return this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.get0(), obj));
            // } else {
            if (pattern.matches(this.host))
                return this.cache.directWriter().apply(pattern, obj);
            else {
                return this.server.sendRecv(pattern, mParser.parse("%s -> |(%s)".formatted(pattern.asBranch(), obj.toCleanString())));
            }
            // }
        };
    }
}
