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

package studio.phaseshift.metatron.isa.m.db.vec.space;

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Parser;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.EMBED;
import static studio.phaseshift.metatron.Tokens.MODEL;
import static studio.phaseshift.metatron.Tokens.M_ISA_INST_TID;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.EMBEDQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.EMBEDQ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * vecSpace-specific {@code embedQ}.
 * <p>
 * Uses {@link VectorDBClient} to store and retrieve embeddings indexed by
 * source VID + model name, replacing the generic {@code QCollection.embedQ()}
 * fallback when registered on a {@link vecSpace}.
 */
public class vecEmbedQ extends BaseQ {

    /**
     * @param space the space whose embeddings this reads
     * @param model the reference to the model the space embeds with, as its embedq definition writes it
     *              (embedq::[model=>!*<location>]), kept unresolved; noobj when it names none, and then the space's
     *              config function embeds
     */
    public vecEmbedQ(final vecSpace space, final Obj model) {
        super(buildJvm(space, model), EMBEDQ_PATTERN, EMBEDQ_TID);
    }

    private static Map<Obj, Obj> buildJvm(final vecSpace space, final Obj model) {
        return mutableMap(
                uri(PATTERN), uri(EMBEDQ_PATTERN),
                uri(MODEL), model,
                uri(PRE_READ), instC(
                        M_ISA_INST_TID
                                .dom(ALL.maybe())
                                .rng(ALL.maybeSome()),
                        lst(mInstSet.URI_TYPE),
                        (lhs, inst) -> {
                            final fURI vid = inst.arg(0).uriValue();
                            final fURI sourceVid = vid.qLess();
                            // Route through space to get collection + entry
                            final fURI routed = Space.Helper.routeFromSpace(sourceVid, space.routes());
                            final DataPath dp = DataPath.withoutDB(routed);
                            if (!dp.hasCollection() || !dp.hasEntry())
                                return noobj();
                            final String collectionName = dp.collection();
                            final String docId = vecSpace.documentId(routed);
                            try {
                                final VectorDBClient.CollectionData coll = space.sjvm().getCollection(collectionName);
                                final VectorDBClient.GetResult result = space.sjvm().get(coll.id(), List.of(docId));
                                return result.entities().isEmpty() ? noobj() : result.entities().getFirst().embedding();
                            } catch (final Exception e) {
                                space.logger().debug("embedQ preRead failed for %s: %s", sourceVid, e.getMessage());
                                return noobj();
                            }
                        }),
                uri(POST_READ), noobj(),
                uri(PRE_WRITE), noobj(),
                uri(POST_WRITE), noobj(),
                uri(QLESS_WRITE), noobj()
        );
    }

    /**
     * the embedding of doc by the model this embedq references. The reference is resolved at each embedding (reading
     * embedq>>model), so the model is whatever is at its location then: a model written there takes the place of the
     * last without a remount, and the vectors an earlier model embedded stay as they were (Marko, 2026-11-26). Nothing
     * is held here. The model is cast to model::T, which builds nothing for an obj already typed model::T and builds a
     * model from a spec, again at every embedding, so the caller builds the model at the location. A build that fails
     * answers its fail, and a reference that reads nothing answers a fail naming it. Both calls go through the machine
     * (the instruction set defining model::T and embed already depends on this one), so they resolve as mtron resolves
     */
    public Obj embed(final Obj doc) {
        final Obj named = this.at(uri(MODEL));
        if (named.isNoObj())
            return fail("the embedq's model, %s, reads no model", this.atDirect(uri(MODEL)));
        final Obj model = Machine.evaluate(named, Parser.Helper.toCode(Machine.parse("as(model::T)")));
        if (model.isFail())
            return model;
        // the document reaches embed as it is: an inst computes a value argument against its lhs, which expands a str's
        // or a uri's templates (running them as mtron), while a call's answer is kept as it is
        return Machine.evaluate(model, instB(f(EMBED), lst(instLambda((lhs, inst) -> doc))).asCode());
    }
}
