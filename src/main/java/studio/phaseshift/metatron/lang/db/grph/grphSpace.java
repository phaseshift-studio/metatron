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

package studio.phaseshift.metatron.lang.db.grph;

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.grph.parser.TP3Translator;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.MTRON_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.space.memSpace.MEM_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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
    public static final fURI GRPH_TID = MTRON_TID.extend("grph");
    public static final Rec CONFIG = rec(
            uri(PATTERN), T(URI_TID),
            uri(STORE).maybe(), T(MEM_SPACE_TID),
            uri(LOAD), T(URI_TID));
    public static final Type GRPH_TYPE = T(GRPH_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(GRPH_TID),
            lst(isa_(CONFIG).tryToInst()), (lhs, inst) -> {
                final grphSpace space = grphSpace.of(inst.arg(0).asRec(), inst.arg(0).vid());
                Router.global().addSpace(space);
                space.start();
                return space;
            }));


    public static grphSpace of(final Rec config, final fURI vid) {
        final Space inner = config.at(STORE).isNoObj() ? noobjSpace.single() :
                memSpace.of(config.at(STORE).<Rec>as().at(PATTERN).uriValue(), fURI.fnull);
        return new grphSpace(inner, config.jvm(), vid);
    }

    protected grphSpace(final Space inner, final Map<Obj, Obj> config, final fURI vid) {
        super(inner, config, GRPH_TID, vid);
    }

    public void start() {
        if (this.has(LOAD)) {
            final fURI dataset = this.at(LOAD).uriValue();
            LOG.info("translating %s into grph space", this.at(LOAD));
            final TP3Translator t = TP3Translator.Builder.of(this.pattern.retractPattern()).create();
            if (dataset.equals(f("modern")))
                t.translate(TinkerFactory.createModern());
            else if (dataset.equals(f("grateful")))
                t.translate(TinkerFactory.createGratefulDead());
            else if (dataset.equals(f("airroutes")))
                t.translate(TinkerFactory.createAirRoutes());
            else
                throw MTronException.of("unknown dataset: %s", this.at(LOAD));
        }
    }

    @Override
    public Obj read(final fURI vid) {
        return this.sjvm.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        /*if (vid.equals(this.pattern.retractPattern())) {
            return obj;
        } else {*/
        return this.sjvm.write(vid, obj);
        //}
    }


    public void clear() {
        LOG.info("clearing {{b}}%s{{X}}", this.pattern);
        this.sjvm().close();
        this.sjvm = memSpace.of(this.pattern, fURI.fnull);
    }

}
