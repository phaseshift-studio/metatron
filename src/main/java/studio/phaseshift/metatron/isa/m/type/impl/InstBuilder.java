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

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection.Docs;
import studio.phaseshift.metatron.isa.m.Derivation;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.Tokens.ARGS;
import static studio.phaseshift.metatron.Tokens.DERIVATION;
import static studio.phaseshift.metatron.Tokens.DESC;
import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.EXAMPLE;
import static studio.phaseshift.metatron.Tokens.FORM;
import static studio.phaseshift.metatron.Tokens.INVERSE;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.RHS;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCS_TID;
import static studio.phaseshift.metatron.furi.q.QCollection.decWrap;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BUILD AN INSTRUCTION IN ONE CHAIN — the friendly surface over {@code instC}. A single declaration states the
 * contract ({@code tid/dom/rng/argLst}), the implementation ({@code body}), its DOCS ({@code ?docq}) and its
 * process LAWS with the two relations a row still carries — its INVERSE and its DERIVATION ({@code ?decq}) —
 * instead of scattering the four across an {@code instC} call, a documentation call and a law-table row:
 *
 * <pre>{@code
 * InstBuilder.build().tid(PLUS_INST_TID, "addition")
 *         .dom(INT_TID, "the lhs integer")
 *         .rng(INT_TID, "the sum")
 *         .argLst(INT_TYPE, "the int to add")
 *         .body((lhs, inst) -> ...)
 *         .law(commutative, right_distributive, action, pure)
 *         .create();
 * }</pre>
 *
 * <p>DOCUMENTATION IS INLINE: the doc string is the second argument of the method it documents, exactly as it is
 * in {@link AbstractObjBuilder}. There is no {@code doc(...)} step and no separate documentation call, because a
 * doc kept apart from the declaration it describes drifts from it and is forgotten on the way in. The argument
 * docs are POSITIONAL — {@code argLst(int::T, "the addend", int::T, "the augend")} is legal and keeps both strings
 * in the order the arguments were declared — because an instruction's arguments are a sequence, not a set, and
 * two arguments of the same type are two distinct positions.
 *
 * <p>NOTHING IS WRITTEN HERE. An instruction is built while its instruction set is still initializing, before the
 * space that would receive it exists, so {@link #create()} composes the value and QUEUES the writes — the object at
 * its contract address, its docs at {@code ?docq}, its declaration at {@code ?decq} — and the set that owns the address
 * republishes them when it mounts. The retained staging log ({@link PendingWrites}) IS the query index a law table
 * reads: a declaration is answerable from memory the moment the instruction is created and stays answerable, so a
 * rewrite costs a hash read instead of a walk. The ONE thing kept here is the declared FORM index
 * ({@link #DECLARED_FORMS}): a form is keyed by the OPERATION's address rather than the full n-tid, because a rewrite
 * reads the form off an instruction whose tid has been RETYPED to its declared contract, and the staging log — which
 * is keyed by that full n-tid — would miss exactly the reads that matter.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class InstBuilder extends AbstractObjBuilder<InstBuilder, Inst> {

    /**
     * THE DECLARED FORMS, IN MEMORY, keyed by the OPERATION's base path. A form is a fact about the operation, not
     * about one registration: every dom/rng specialization of {@code as} is the same typer, and a rewrite reads a
     * form off an instruction whose tid has been RETYPED to its declared contract, so a map keyed by the full n-tid
     * would miss exactly the reads that matter. {@link #drain} records the facet beside the instruction in
     * {@code ?decq}, as it does the laws, inverse and derivation.
     */
    private static final Map<fURI, Inst.Form> DECLARED_FORMS = new ConcurrentHashMap<>();

    private fURI dom;
    private fURI rng;
    private Poly<?, ?> args = lst();
    private BiFunction<Obj, Inst, Obj> body;
    private String domDesc;
    private String rngDesc;
    private final List<String> argDocList = new ArrayList<>();

    /**
     * THE ARGUMENT DOCS KEYED BY THE ARGUMENT'S OWN KEY — the form a {@code rec(...)} argument list needs, where a
     * position is not the identity.
     */
    private final Map<Obj, String> keyedArgDocs = new LinkedHashMap<>();
    private Obj seed;
    private Inst.Form form;

    private InstBuilder() {
    }

    public static InstBuilder build() {
        return new InstBuilder();
    }

    /**
     * The domain — the type of the lhs the instruction is applied to — with the doc string for that domain inline.
     */
    public InstBuilder dom(final fURI dom, final String desc) {
        this.domDesc = desc;
        return this.dom(dom);
    }

    public InstBuilder dom(final fURI dom) {
        this.dom = dom;
        return this;
    }

    /**
     * The range — the type of the value the instruction answers — with the doc string for that range inline.
     */
    public InstBuilder rng(final fURI rng, final String desc) {
        this.rngDesc = desc;
        return this.rng(rng);
    }

    public InstBuilder rng(final fURI rng) {
        this.rng = rng;
        return this;
    }

    /**
     * The declared arguments with their docs inline, one doc per POSITION — so two arguments of the same type are
     * two documented positions rather than one collapsed key.
     */
    /**
     * THE ARGUMENT DOCS KEYED BY THE ARGUMENT'S OWN KEY — the form a {@code rec(...)} argument list needs, where a
     * position is not the identity (an {@code apply(lhs, args)} contract documents {@code lhs} and {@code args},
     * not "arg 0"). Merges into the doc rec's {@code args} slot beside any positional docs.
     */
    public InstBuilder argDocs(final Map<Obj, String> docs) {
        if (null != docs)
            this.keyedArgDocs.putAll(docs);
        return this;
    }

    public InstBuilder argLst(final Obj arg0, final String arg0Desc) {
        this.argDocList.add(arg0Desc);
        return this.argLst(arg0);
    }

    public InstBuilder argLst(final Obj arg0, final String arg0Desc, final Obj arg1, final String arg1Desc) {
        this.argDocList.add(arg0Desc);
        this.argDocList.add(arg1Desc);
        return this.argLst(arg0, arg1);
    }

    public InstBuilder argLst(final Obj arg0, final String arg0Desc, final Obj arg1, final String arg1Desc,
                              final Obj arg2, final String arg2Desc) {
        this.argDocList.add(arg0Desc);
        this.argDocList.add(arg1Desc);
        this.argDocList.add(arg2Desc);
        return this.argLst(arg0, arg1, arg2);
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

    /**
     * Declare the instruction's {@link Inst.Form FORM} — the one facet an n-tid coefficient shape cannot derive.
     * Today the declarable form is {@code typer}: an instruction whose subject is the OBJECT'S TYPE/IDENTITY rather
     * than its value ({@code as}, {@code ::}, {@code type()}, {@code vid()}, {@code tid()}). Such an instruction is
     * opaque to a wrapper's {@code {self} ∪ components} fan-out — {@code as} decides what the value IS, so it
     * cannot be distributed over the old basis — and the rewriter neither floats it across a coefficient change nor
     * merges two of them ({@link Inst.Form#isFloatable()} and {@link Inst.Form#isBlocked()} are both false).
     * Queued with the other declaration facets and published to {@code ?decq} by {@link #drain}.
     */
    public InstBuilder form(final Inst.Form form) {
        this.form = form;
        return this;
    }

    /**
     * shorthand for {@code form(Inst.Form.typer)} — the declared facet of an instruction that retypes the one obj.
     */
    public InstBuilder typer() {
        return this.form(Inst.Form.typer);
    }

    /**
     * Declare an ALREADY-REGISTERED operation's form by its base path — the call a registration site makes when its
     * instructions are built by {@code instC(...)} rather than through this builder (which is how the obj/inst area
     * registers {@code as}, {@code type()}, {@code vid()} and {@code tid()}). The declaration is per OPERATION, so
     * every dom/rng specialization of it is covered and a RETAINED instruction still answers {@link Inst.Form#of}
     * after a rewrite retypes its tid.
     */
    public static void declareForm(final Inst.Form form, final fURI... opTids) {
        for (final fURI opTid : opTids)
            if (null != opTid && null != opTid.basePath())
                DECLARED_FORMS.put(opTid.basePath(), form);
    }

    /**
     * The form an operation DECLARED, or null when it left its form to the n-tid shape — the hash read
     * {@link Inst.Form#of} makes before it falls back to the structural derivations.
     */
    public static Inst.Form declaredForm(final fURI instTid) {
        return null == instTid ? null : DECLARED_FORMS.get(instTid.basePath());
    }

    /**
     * The instruction — composed exactly as {@code instC} would compose it, then QUEUED: the object at its contract
     * address (the write the owning set indexes at mount), its docs at {@code ?docq} when the chain documented any
     * slot, and its claim at {@code ?decq} when it declared laws, an inverse, a derivation or a form. Nothing is
     * written to a space here: an instruction set builds its instructions during its own static initialization,
     * before a machine exists to write to.
     */
    @Override
    public Inst create() {
        if (null == this.tid)
            throw MTronException.of("tid must be set before creating an inst");
        final fURI composed = null == this.dom || null == this.rng ? this.tid : this.tid.dom(this.dom).rng(this.rng);
        // the declared ARGUMENT types complete the contract (A x B -> C) in the tid's `args` component — a no-op
        // unless MInst.ARGS_IN_TID says a tid is where the contract is carried
        final fURI keyed = MInst.keyed(composed, this.args);
        final Inst inst = null == this.seed ?
                MInst.instC(keyed, this.args, this.body) :
                MInst.instC(keyed, this.args, this.body, this.seed);
        PendingWrites.write(inst.tid(), inst);
        final Rec doc = this.docRec(inst);
        if (!doc.isEmpty())
            PendingWrites.write(PendingWrites.docq(inst.tid()), doc);
        final Rec claim = claimRec(List.copyOf(this.laws), this.inverse, this.derivation, this.form);
        if (!claim.isEmpty())
            PendingWrites.write(PendingWrites.decq(Inst.Helper.operationTid(inst.tid())), claim);
        // a FORM is a declaration too, but one made per OPERATION: every specialization of the op is the same typer
        if (null != this.form)
            DECLARED_FORMS.put(keyed.basePath(), this.form);
        return inst;
    }

    /**
     * PERSIST an inst's staged declaration to {@code ?decq} — the call an instset makes once its space is mounted,
     * and the call a caller makes when it wants the store to answer now.
     *
     * <p>THE CLAIM IS READ FROM THE RETAINED STAGING LOG, not recomposed from a registry: {@code create()} queued the
     * exact rec (laws, inverse, derivation, form) at {@code ?decq}, and that write stays staged, so the claim published
     * here is the claim the instruction made. The FORM facet is the one kept out of the log's key — it is declared per
     * OPERATION ({@link #DECLARED_FORMS}) — so it is appended when the staged rec does not already carry it.
     * {@link studio.phaseshift.metatron.furi.q.QCollection#decWrap} publishes the same rec immediately, so a rewrite
     * that drains an instruction and then consults the store does not see a hole; both writes land at the same address
     * with the same value, so the replay is a no-op rather than a second claim.
     */
    public static void drain(final Inst inst) {
        if (null == inst || null == inst.tid())
            return;
        final fURI claimAddress = PendingWrites.decq(Inst.Helper.operationTid(inst.tid()));
        final Obj staged = PendingWrites.find(claimAddress);
        final Inst.Form form = DECLARED_FORMS.get(inst.tid().basePath());
        if (null == staged && null == form)
            return;
        final Map<Obj, Obj> facets = new LinkedHashMap<>();
        if (null != staged && staged.isRec()) {
            final Rec declared = staged.asRec();
            final Obj laws = declared.atDirect(uri(LAW));
            if (!laws.isNoObj())
                facets.put(uri(LAW), laws);
            final Obj inverse = declared.atDirect(uri(INVERSE));
            if (!inverse.isNoObj())
                facets.put(uri(INVERSE), inverse);
            final Obj derivation = declared.atDirect(uri(DERIVATION));
            if (!derivation.isNoObj())
                facets.put(uri(DERIVATION), derivation);
        }
        if (null != form)
            facets.put(uri(FORM), uri(form.name()));
        final Rec claim = facets.isEmpty() ? rec0() : rec(facets);
        PendingWrites.write(claimAddress, claim);
        decWrap(inst, claim);
    }

    /**
     * THE CLAIM REC — what an instruction declares beside itself, composed ONCE so the queued write and the drained
     * write cannot disagree: the process laws it obeys, the opposing edge it declares, its defining equation and the
     * form it declared. A facet that was never declared is omitted rather than written as a null or an empty list,
     * so the rec carries exactly the claims the instruction made; a claim of nothing at all is the empty rec.
     */
    private static Rec claimRec(final List<Law> laws, final fURI inverse, final Derivation derivation,
                                final Inst.Form form) {
        final Map<Obj, Obj> facets = new LinkedHashMap<>();
        if (null != laws && !laws.isEmpty())
            facets.put(uri(LAW), lst(laws.stream().map(l -> (Obj) uri(l.name())).toList()));
        if (null != inverse)
            facets.put(uri(INVERSE), uri(inverse));
        if (null != derivation)
            facets.put(uri(DERIVATION), rec(uri(LHS), derivation.lhs(), uri(RHS), derivation.rhs()));
        if (null != form)
            facets.put(uri(FORM), uri(form.name()));
        return facets.isEmpty() ? rec0() : rec(facets);
    }

    /**
     * THE DOC REC — what the chain documented, in the one shape {@code ?docq} stores: the obj the docs describe, the
     * {@code desc}/{@code dom}/{@code rng} prose, the POSITIONAL argument docs and the worked examples. A slot the
     * chain never stated falls back to the contract it describes (the dom and rng addresses) and is otherwise
     * {@code noobj}; an undocumented chain answers the empty rec, which is what tells {@link #create()} that there
     * is no doc write to queue.
     */
    private Rec docRec(final Inst inst) {
        final String domDoc = null == this.domDesc ? (null == this.dom ? null : this.dom.toString()) : this.domDesc;
        final String rngDoc = null == this.rngDesc ? (null == this.rng ? null : this.rng.toString()) : this.rngDesc;
        final Map<Obj, Obj> keyed = new LinkedHashMap<>();
        this.keyedArgDocs.forEach((key, doc) -> keyed.put(key, str(doc)));
        final Obj argsDoc = !keyed.isEmpty()
                ? rec(keyed)
                : this.argDocList.isEmpty() ?
                noobj() :
                lst(this.argDocList.stream().map(doc -> (Obj) str(null == doc ? "" : doc)).toList());
        final List<Obj> examples = this.examples.stream().map(example -> (Obj) str(example)).toList();
        if (null == this.description && null == this.domDesc && null == this.rngDesc
                && argsDoc.isNoObj() && examples.isEmpty())
            return rec0();
        return new Docs(mutableMap(
                uri(OBJ), inst,
                uri(DOM), null == domDoc || domDoc.isBlank() ? noobj() : str(domDoc),
                uri(RNG), null == rngDoc || rngDoc.isBlank() ? noobj() : str(rngDoc),
                uri(ARGS), argsDoc,
                uri(DESC), null == this.description || this.description.isBlank() ? noobj() : str(this.description),
                uri(EXAMPLE), examples.isEmpty() ? noobj() : lst(examples)),
                DOCS_TID, null);
    }
}
