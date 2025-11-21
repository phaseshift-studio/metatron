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

package studio.phaseshift.metatron.lang.db.grph;

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.db.grph.type.TP3Translator;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.util.noobjSpace;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.kv.kvSpace.KV_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class grphSpace extends MSpace<Space> {

    /**
     * /root/v/+                       => vertices
     * /root/v/{id}                    => vertex by id
     * /root/v/{id}/outE               => vertex outgoing edges
     * /root/v/{id}/outE/{label}       => vertex outgoing labeled edges
     * /root/v/{id}/out/{id2}          => vertex outgoing adjacent vertices
     * /root/v/{id}/vp/{key}           => vertex property by key
     * /root/v/{id}/vp/{key}/{key2}    => vertex property property by key
     */
    public static final fURI GRPH_TID = f("/grph/space/grph");
    public static final Type GRPH_TYPE = T(GRPH_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(GRPH_TID),
            lst(isa_(rec(uri(PATTERN), T(URI_TID),
                    uri(SPACE).c(cInt.MAYBE()), T(KV_TID),
                    uri(LOAD), T(URI_TID)))
                    .tryToInst()), (lhs, inst) -> {
                final fURI pattern = inst.arg(0).<Rec>as().at(PATTERN).uriValue();
                final fURI dataset = inst.arg(0).<Rec>as().at(LOAD).uriValue();
                final Obj inner = inst.arg(0).<Rec>as().at(SPACE);
                final grphSpace space = new grphSpace(inner.isNoObj() ? noobjSpace.single() : 
                        new kvSpace(inner.<Rec>as().at(PATTERN).uriValue(), fURI.NULL), Map.of(uri(PATTERN), uri(pattern), uri(LOAD), uri(dataset)), pattern, inst.arg(0).vid());
                Router.global().addSpace(space);
                space.start();
                return space;
            }));

    public grphSpace(final Space inner, final Map<Obj, Obj> config, final fURI pattern, final fURI vid) {
        super(inner, config, pattern, GRPH_TID, vid);
    }

    public void start() {
        if (this.has("load")) {
            LOG.info("translating %s into grph space", uri("tinkerpop-modern"));
            final TP3Translator t = TP3Translator.Builder.of(this.pattern.retractPattern()).create();
            t.translate(TinkerFactory.createModern());
        }
    }

    @Override
    public Obj read(final fURI vid) {
        return this.sjvm.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (vid.equals(this.pattern.retractPattern())) {

            return obj;
        } else {
            return this.sjvm.write(vid, obj);
        }
    }

}
