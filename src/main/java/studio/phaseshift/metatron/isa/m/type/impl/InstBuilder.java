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
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.furi.q.QCollection.lawWrap;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * BUILD AN INSTRUCTION IN ONE CHAIN — the friendly surface over {@code instC}. A single declaration states the
 * contract ({@code tid/dom/rng/argLst}), the implementation ({@code body}), its DOCS ({@code ?docq}) and its
 * process LAWS ({@code ?lawq}), instead of scattering the four across an {@code instC} call, a {@code docWrap}
 * call and a law-table row:
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
 * <p>The laws are STAGED, not written: an instruction is built before its instset is mounted (the same reason
 * {@code docWrap} soft-fails during an ISA's own setup), so the set persists them with {@link #drain} once its
 * space exists. Docq and lawq remain the stores of record — this builder is the authoring surface over them.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class InstBuilder {

    /**
     * staged process laws, keyed by the inst's declared n-tid — drained by the instset, never read for serving.
     */
    private static final Map<fURI, List<Law>> LAWS = new ConcurrentHashMap<>();

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
     * constructs its insts) and its laws are staged for {@link #drain}.
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
        if (!this.laws.isEmpty())
            LAWS.put(inst.tid(), List.copyOf(this.laws));
        return inst;
    }

    /**
     * PERSIST an inst's staged laws to {@code ?lawq} — the call an instset makes once its space is mounted.
     * An inst that declared none is untouched.
     */
    public static void drain(final Inst inst) {
        if (null == inst || null == inst.tid())
            return;
        final List<Law> laws = LAWS.get(inst.tid());   // a DECLARATION, not a one-shot: each VM boot repersists it
        if (null == laws || laws.isEmpty())
            return;
        lawWrap(inst, rec(uri(LAW), lst(laws.stream().map(l -> (Obj) uri(l.name())).toList())));
    }
}
