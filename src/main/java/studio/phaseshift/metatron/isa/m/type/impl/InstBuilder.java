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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.Derivation;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.Tokens.DERIVATION;
import static studio.phaseshift.metatron.Tokens.INVERSE;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.RHS;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.furi.q.QCollection.lawWrap;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * BUILD AN INSTRUCTION IN ONE CHAIN — the friendly surface over {@code instC}. A single declaration states the
 * contract ({@code tid/dom/rng/argLst}), the implementation ({@code body}), its DOCS ({@code ?docq}) and its
 * process LAWS with the two relations a row still carries — its INVERSE and its DERIVATION ({@code ?lawq}),
 * instead of scattering the four across an {@code instC} call, a {@code docWrap} call and a law-table row:
 *
 * <pre>{@code
 * InstBuilder.build().tid(PLUS_INST_TID)
 *         .dom(INT_TID).docDom("the lhs integer")
 *         .rng(INT_TID).docRng("the sum")
 *         .argLst(INT_TYPE).argDocs(Map.of(jnt(0), "the int to add"))
 *         .body((lhs, inst) -> ...)
 *         .law(commutative, right_distributive, action, pure)
 *         .docDesc("addition")
 *         .create();
 * }</pre>
 *
 * <p>The declaration is STAGED, not written: an instruction is built before its instset is mounted (the same
 * reason {@code docWrap} soft-fails during an ISA's own setup), so the set persists it with {@link #drain} once
 * its space exists. Docq and lawq remain the stores of record — the durable form a later boot reads back — and the
 * staged map is also the QUERY INDEX a law table reads ({@link #declaration}, {@link #declarations}): a declaration
 * is answerable from memory the moment the instruction is created, so a rewrite consults it without walking the
 * registered instructions or reading the store beside each.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class InstBuilder {

    /**
     * THE DECLARATIONS, IN MEMORY, keyed by the inst's declared n-tid. This is the QUERY INDEX over {@code ?lawq}:
     * {@link #drain} persists a declaration to the store (the durable record, and the only one that survives a boot)
     * while this map answers the questions a rewrite asks per pass — so a law lookup is a hash read, never a walk of
     * every registered instruction and a space read beside each.
     */
    private static final Map<fURI, Declaration> DECLARATIONS = new ConcurrentHashMap<>();

    /**
     * The order declarations were made in. A reader that unions them ({@code LawTable.derivations}) must replay them
     * deterministically, and a hash map's iteration order is not an order — the instructions' own build order is.
     */
    private static final Queue<fURI> DECLARATION_ORDER = new ConcurrentLinkedQueue<>();

    /**
     * The ordered READ-ONLY view of {@link #DECLARATIONS}, rebuilt only when a declaration lands — so the union a
     * rewrite pass reads is one plain map, not a copy and not a hash-ordered walk.
     */
    private static volatile Map<fURI, Declaration> DECLARATION_VIEW = Map.of();
    private static volatile int DECLARATION_VIEW_SIZE = -1;

    /**
     * What an instruction declares BESIDE its docs: the process laws it obeys, the opposing edge it declares, and
     * its defining equation. Staged as one unit because a law row is one claim — a declaration carrying only an
     * inverse or only a derivation is as complete as one carrying only laws.
     */
    public record Declaration(List<Law> laws, fURI inverse, Derivation derivation) {
    }

    private fURI tid;
    private fURI dom;
    private fURI rng;
    private Poly<?, ?> args = lst();
    private BiFunction<Obj, Inst, Obj> body;
    private String description;
    private String domDesc;
    private String rngDesc;
    private Map<Obj, String> argDocs = Map.of();
    private String[] examples = {};
    private Obj seed;
    private fURI inverse;
    private Derivation derivation;
    private final List<Law> laws = new ArrayList<>();

    private InstBuilder() {
    }

    public static InstBuilder build() {
        return new InstBuilder();
    }

    public InstBuilder tid(final fURI tid) {
        this.tid = tid;
        return this;
    }

    public InstBuilder tid(final fURI tid, final String description) {
        return this.tid(tid).docDesc(description);
    }

    public InstBuilder dom(final fURI dom) {
        this.dom = dom;
        return this;
    }

    public InstBuilder rng(final fURI rng) {
        this.rng = rng;
        return this;
    }

    public InstBuilder dom(final fURI dom, final String desc) {
        return this.dom(dom).docDom(desc);
    }

    public InstBuilder rng(final fURI rng, final String desc) {
        return this.rng(rng).docRng(desc);
    }

    public InstBuilder argLst(final Obj arg0, final String arg0Desc) {
        return this.argLst(arg0).argDocs(Map.of(arg0, arg0Desc));
    }

    public InstBuilder argLst(final Obj arg0, final String arg0Desc, final Obj arg1, final String arg1Desc) {
        return this.argLst(arg0, arg1).argDocs(Map.of(arg0, arg0Desc, arg1, arg1Desc));
    }

    public InstBuilder argLst(final Obj arg0, final String arg0Desc, final Obj arg1, final String arg1Desc, final Obj arg2, final String arg2Desc) {
        return this.argLst(arg0, arg1, arg2).argDocs(Map.of(arg0, arg0Desc, arg1, arg1Desc, arg2, arg2Desc));
    }

    public InstBuilder argLst(final Obj... argTypes) {
        this.args = lst(argTypes);
        return this;
    }

    public InstBuilder args(final Poly<?, ?> args) {
        this.args = args;
        return this;
    }

    /**
     * the fold's identity ({@code sum}'s 0, {@code prod}'s 1) — passed through as the inst's seed.
     */
    public InstBuilder seed(final Obj seed) {
        this.seed = seed;
        return this;
    }

    public InstBuilder body(final BiFunction<Obj, Inst, Obj> body) {
        this.body = body;
        return this;
    }

    public InstBuilder law(final Law... laws) {
        this.laws.addAll(List.of(laws));
        return this;
    }

    /**
     * the DECLARED opposing edge ({@code inverse(f) = g} claims {@code f·g = ε} only — f a section, g its
     * retraction, never {@code g·f = ε}). Pass null for none, which is what an instruction whose carrier declares
     * no algebra states — a retraction is a fact about the implementation, not the structure.
     */
    public InstBuilder inverse(final fURI inverse) {
        this.inverse = inverse;
        return this;
    }

    /**
     * the DEFINING equation ({@code minus ≡ plus ∘ neg}) — what lets the carrier's algebra derive the inverse
     * instead of the row declaring one, and what a fold's combiner is read off.
     */
    public InstBuilder derivation(final Derivation derivation) {
        this.derivation = derivation;
        return this;
    }

    public InstBuilder docDesc(final String description) {
        this.description = description;
        return this;
    }

    public InstBuilder docDom(final String description) {
        this.domDesc = description;
        return this;
    }

    public InstBuilder docRng(final String description) {
        this.rngDesc = description;
        return this;
    }

    public InstBuilder argDocs(final Map<Obj, String> docs) {
        this.argDocs = docs;
        return this;
    }

    public InstBuilder examples(final String... examples) {
        this.examples = null == examples ? new String[]{} : examples;
        return this;
    }
    
    /**
     * The instruction. Its docs are written straight to {@code ?docq} (the space is already known when a set
     * constructs its insts) and its declaration is staged for {@link #drain}.
     */
    public Inst create() {
        if (null == this.tid)
            throw studio.phaseshift.metatron.util.MTronException.of("tid must be set before creating an inst");
        final fURI keyed = null == this.dom || null == this.rng ? this.tid : this.tid.dom(this.dom).rng(this.rng);
        final Inst inst = null == this.seed ?
                MInst.instC(keyed, this.args, this.body) :
                MInst.instC(keyed, this.args, this.body, this.seed);
        if (null != this.description)
            docWrap(inst,
                    null == this.domDesc ? this.dom.toString() : this.domDesc,
                    null == this.rngDesc ? this.rng.toString() : this.rngDesc,
                    this.argDocs, this.description, this.examples);
        // any facet is a declaration: an instruction may state only its inverse or only its defining equation
        if (!this.laws.isEmpty() || null != this.inverse || null != this.derivation) {
            DECLARATIONS.put(inst.tid(), new Declaration(List.copyOf(this.laws), this.inverse, this.derivation));
            DECLARATION_ORDER.add(inst.tid());
        }
        return inst;
    }

    /**
     * THE DECLARATION AN INSTRUCTION MADE BESIDE ITSELF, or null when it made none — a hash read, which is what
     * {@code ?lawq} cannot be: the store answers by space lookup, and asking it once per registered instruction is
     * how a law lookup became a per-evaluation cost.
     */
    public static Declaration declaration(final fURI instTid) {
        return null == instTid ? null : DECLARATIONS.get(instTid);
    }

    /**
     * EVERY DECLARATION IN THE REGISTRY, in the order the instructions were built, as an UNMODIFIABLE view — the
     * read a union over all defining equations takes. Nothing is written through it: the store stays the durable
     * record ({@link #drain}), this is the in-memory index over it. The view is rebuilt only when a declaration
     * lands, so a rewrite pass pays one map read rather than a copy.
     */
    public static Map<fURI, Declaration> declarations() {
        if (DECLARATION_VIEW_SIZE != DECLARATION_ORDER.size()) {
            final Map<fURI, Declaration> view = new LinkedHashMap<>();
            for (final fURI tid : DECLARATION_ORDER) {
                final Declaration declared = DECLARATIONS.get(tid);
                if (null != declared)
                    view.put(tid, declared);
            }
            DECLARATION_VIEW = Collections.unmodifiableMap(view);
            DECLARATION_VIEW_SIZE = DECLARATION_ORDER.size();
        }
        return DECLARATION_VIEW;
    }

    /**
     * PERSIST an inst's staged declaration to {@code ?lawq} — the call an instset makes once its space is mounted.
     * An inst that declared none is untouched, and a facet that was never declared is omitted rather than written
     * as a null or an empty list, so the rec carries exactly the claims the instruction made.
     */
    public static void drain(final Inst inst) {
        if (null == inst || null == inst.tid())
            return;
        final Declaration declared = DECLARATIONS.get(inst.tid());   // a DECLARATION, not a one-shot: each VM boot repersists it
        if (null == declared)
            return;
        final Map<Obj, Obj> facets = new LinkedHashMap<>();
        if (!declared.laws().isEmpty())
            facets.put(uri(LAW), lst(declared.laws().stream().map(l -> (Obj) uri(l.name())).toList()));
        if (null != declared.inverse())
            facets.put(uri(INVERSE), uri(declared.inverse()));
        if (null != declared.derivation())
            facets.put(uri(DERIVATION),
                    rec(uri(LHS), declared.derivation().lhs(), uri(RHS), declared.derivation().rhs()));
        lawWrap(inst, rec(facets));
    }
}
