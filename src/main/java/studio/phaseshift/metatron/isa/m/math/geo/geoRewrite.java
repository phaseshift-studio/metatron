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

package studio.phaseshift.metatron.isa.m.math.geo;

import studio.phaseshift.metatron.algebra.rewrite.RewriterBuilder;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.isa.m.mInstSet.AT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instA;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;

/**
 * The native-pushdown contract: how a space that DOES have spatial support replaces the VM's geo instructions with its
 * own operator, so the payload is filtered where it lives instead of being shipped into the VM to be filtered here.
 *
 * <h2>The two halves of the instruction set</h2>
 *
 * <p>{@link geoInstSet}'s instructions are the answer for a space with <b>no</b> spatial support: a {@code memSpace}, a
 * filesystem read, a {@code lst} of features, a rec that arrived over a wire. They are complete and correct there, and
 * they are the only implementation that exists today.
 *
 * <p>A space that <b>does</b> have spatial support — PostGIS behind {@code /m/db/tble}, MongoDB's {@code $geoWithin} and
 * {@code $near} behind {@code /m/db/dcmnt} — should never run them. Its planner can answer
 * \(\mathrm{from}(\mathit{path}).\mathrm{geo\_within}(g)\) with an indexed operator over the same predicate, which is
 * the difference between reading a collection and testing every document in it. The rewrite below is that
 * substitution, stated once and parameterized by the space: the generic geo instruction is REPLACED by the space's
 * native instruction of the same arity, keeping the query geometry as the argument it already is.
 *
 * <h2>Registering it</h2>
 *
 * <p>Nothing is registered by default, because no space declares spatial support yet — a rewrite that fires on a space
 * that cannot honour it would be a lie told to the planner. A spatial space registers its own:
 *
 * <pre>{@code
 * // in the spatial space's instset setup
 * uri(REWRITE), lst(
 *     geoRewrite.pushdown(PostgisRec.class::isInstance,
 *         geoInstSet.INSIDE_INST_TID,     POSTGIS_ST_WITHIN_TID)
 *         ...
 * )
 * }</pre>
 *
 * <p>The predicate is asked about the space that owns the code's {@code from(<uri>)} source; a {@code from} whose route
 * resolves to no space, or to a space the predicate declines, is left exactly as written. The native instruction must
 * take the same argument (the query geometry) — that is the whole contract, and it is why the geo instructions were
 * given one-geometry argument shapes rather than bespoke encodings.
 *
 * <h2>Why a rewrite and not a second implementation on the interface</h2>
 *
 * <p>A native operator is not a better Java body for the same instruction: it is a different PLAN — it changes where
 * the work happens, not only how it is computed. Spelling it as a rewrite keeps that visible in {@code explain()}
 * (the rule that fired names the native operator), and it keeps the generic implementation total: a space that loses
 * its index falls back to it by removing one registration.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class geoRewrite {

    private geoRewrite() {
        // a static provider, never an instance
    }

    /**
     * One pushdown rewrite, for a single generic geo instruction: whenever the code reads from a space the predicate
     * accepts, the instruction is replaced by {@code nativeTid} with the same arguments.
     *
     * @param nativeSupport answers whether a space can honour a spatial operator natively
     * @param genericTid    the generic instruction to replace (e.g. {@link geoInstSet#INSIDE_INST_TID})
     * @param nativeTid     the space's own operator (the same arity, one geometry argument)
     * @return the rewrite, ready for a spatial instset's {@code uri(REWRITE)} table
     */
    public static Inst pushdown(final Predicate<Space> nativeSupport, final fURI genericTid, final fURI nativeTid) {
        final fURI rewriteTid = GEO_REWRITE_TID.extend("native_" + nativeTid.name());
        return docWrap(InstSet.Helper.rewriter(rewriteTid, code -> {
                    if (!readsNativeSpace(code, nativeSupport))
                        return code;
                    return code.selfJVM(RewriterBuilder.search(code.insts())
                            .match(instA(genericTid).insts())
                            .rewrite(matched -> matched.values().stream()
                                    .map(actual -> instB(nativeTid, actual.args()))
                                    .toList())).asCode();
                }),
                "the generic " + genericTid.name() + " over a space with native spatial support",
                "the space's own " + nativeTid.name() + " over the same argument");
    }

    /**
     * The bulk form: one rewrite per {@code generic → native} entry, in the order the map iterates.
     *
     * @param nativeSupport answers whether a space can honour a spatial operator natively
     * @param nativeTids    generic instruction → the space's own operator
     * @return the rewrites, ready to be wrapped in the spatial instset's {@code uri(REWRITE)} lst
     */
    public static List<Inst> pushdown(final Predicate<Space> nativeSupport, final Map<fURI, fURI> nativeTids) {
        final List<Inst> rewrites = new ArrayList<>();
        nativeTids.forEach((generic, nativeTid) -> rewrites.add(pushdown(nativeSupport, generic, nativeTid)));
        return rewrites;
    }

    /**
     * The standard set a spatial space would map: the three predicates and the two measures, which are the operators
     * every spatial index is built to answer. A space maps only what it actually implements — an unmapped instruction
     * keeps its VM implementation, which is correct, only slower.
     */
    public static Map<fURI, fURI> standardPushdowns(final fURI instprefix) {
        final Map<fURI, fURI> pushes = new LinkedHashMap<>();
        // one containment verb: `inside` (contained-first). A backend with only ST_Contains answers it by swapping
        // the operands — that is the shape of the query, not a second instruction to map.
        pushes.put(INSIDE_INST_TID, instprefix.extend("within"));
        pushes.put(DISTANCE_INST_TID, instprefix.extend("distance"));
        // the envelope is a CAST now, so a backend that materializes a box maps the measurement it protects instead
        pushes.put(LENGTH_INST_TID, instprefix.extend("length"));
        pushes.put(AREA_INST_TID, instprefix.extend("area"));
        return pushes;
    }

    /**
     * Whether the code reads from a space the predicate accepts — the {@code from(<uri>)} / {@code at(<uri>)} source
     * at the head of the chain, resolved through the router so the question is asked of the space that would answer.
     */
    static boolean readsNativeSpace(final Code code, final Predicate<Space> nativeSupport) {
        for (final Inst inst : code.insts()) {
            // THE OPERATION KIND IS THE BASE PATH: a from/at tid may declare its argument types (`?args=[...]`, which
            // is what a parsed call or an args-encoded mint carries), and the type test would then miss it
            if (!inst.tid().basePath().equals(FROM_INST_TID) && !inst.tid().basePath().equals(AT_INST_TID))
                continue;
            final Obj source = inst.arg(0);
            if (!source.isUri())
                continue;
            final Space space = Machine.current().memory().getSpaceFor(source.uriValue());
            if (null != space && nativeSupport.test(space))
                return true;
        }
        return false;
    }
}
