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

package studio.phaseshift.metatron.algebra;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * ALGEBRA TOOL — the claimed-law oracle: does a type's declared algebra satisfy the laws someone <em>claims</em>
 * it does?
 *
 * <p>A CLAIM IS AN ARGUMENT, NEVER A STORE READ. The laws arrive as a list, are tested against known-good probe
 * operands, and answered in a {@link Report}; nothing is written back to {@code ?decq}. That is the point: a claim
 * is a hypothesis, and an unverified hypothesis must not become a declaration.
 *
 * <p>WHERE THE ROLES COME FROM — the type's declared algebra ({@code object::T>>theory}): instance name → rec of role
 * → operation. {@link TheoryHelper#instances(Type)} serves it: the theory block of the {@code ?decq} declaration the
 * base carriers write at their type, read directly through {@code Machine.read} rather than through the categorical
 * lift only the (optional) category instset builds, so the tool answers on a plain boot. Role
 * pointers are read by {@link TheoryHelper#roleAddress} ({@code atDirect} plus {@link Obj.Helper#getPointer}).
 *
 * <p>WHICH OPERATIONS ARE ANALYZED — {@link #analyzeType(Type, List)} runs {@link #analyzeTypeInst(Type, Inst, List)}
 * for every role-bearing operation the type declares and unions the reports; {@link #analyzeTypeInst(Type, Inst,
 * List)} checks the claims against that one instruction.
 *
 * <p>PROBES — the analyzers take no sample argument, so one is looked up by the type's CARRIER tid: the built-in
 * table, else a sample registered through {@link #registerSample(fURI, Obj)} (the values the type tests return
 * from {@code sample()}), else the type's OWN declared {@code zero}/{@code one} — so the tool answers for any
 * mtron type, and answers {@code uncheckable "no probe"} when the declaration names no element to try. THE SECOND
 * OPERAND IS A SECOND, DISTINCT SAMPLE, not a derivation from the first: on a free monoid no term built from ONE
 * generator can falsify {@code a·b = b·a} (every {@code a^n} commutes with every {@code a^m}), so str's
 * concatenation — the canonical non-commutative claim — would be declared to hold if only one {@code "abc"}
 * existed. A carrier with no registered partner falls back to the probe itself (a weaker answer, and the reason
 * a validator with two distinct elements registers a sample). A DEGENERATE PAIR is refused, never compared: an
 * operand that is not a value of the carrier, or two identical operands, makes any agreement between the sides
 * evidence of nothing (see {@code AlgebraTool#degenerate}). A product that happens to EQUAL one of its operands is
 * NOT such a pair: that is absorption — a property of the operation (bool's {@code true ∨ false = true},
 * {@code true ∧ false = false}) — so the law is judged over it, never refused.
 *
 * <p>VERDICTS — {@code holds} (both sides equal), {@code failed} (they differ), {@code unchecked} (a role the law
 * needs is not declared for this operation — the over-claim case) and {@code uncheckable} (the law is about a
 * DIFFERENT SHAPE than this operation, or carries no runnable equation: {@code pure}, {@code poset}, …, or its
 * probe pair is degenerate — see {@link #matches} and {@code AlgebraTool#degenerate}). {@code ok} is true iff no
 * claim failed and none was unchecked: neither an uncheckable claim nor a degenerate probe is a failure. A judged
 * claim records BOTH evaluated sides as {@code got=>[lhs-result, rhs-result]}, so a broken side is visible in the
 * report instead of inferred from one rendered string.
 * <p>APPLICABILITY IS A SIGNATURE MATCH, NEVER A LADDER — {@link #matches} compares {@link Law#wrt()} with the
 * operation's contract (dom/rng with the poly {@code A} standing for the carrier, and arity only when the
 * signature declares {@code args}). A refused claim is a category error, so it answers {@code uncheckable} with
 * the law's shape named ({@code "the law is about inst?rng=bool&dom=A&args=[A]"}), and the same rule is the
 * census a declared law is held to.
 */
public final class AlgebraTool {

    private AlgebraTool() {
    }

    /**
     * The roles a theory instance binds its operations under.
     */
    private static final List<String> OP_ROLES = List.of(OP, ADD, MUL, OR, AND, JOIN, MEET, INV, NOT);

    /**
     * THE INSTANCE NAMES THAT STATE A GROUP — the algebras an {@code involution} claim may read in its group
     * form, together with the spellings {@code declareAlgebras} gives them ({@code add_group}, {@code mul_group},
     * {@code free_groupoid}). An operation bound by a lattice, boolean or plain monoid has no group inverse, so
     * the group equation is not its law: the claim is UNCHECKABLE there, never failed.
     */
    private static final List<String> GROUP_ALGEBRAS = List.of("group", "ring", "field", "near_ring");
    /**
     * The additive roles of a theory instance.
     */
    private static final List<String> ADD_ROLES = List.of(ADD, OR, JOIN);
    /**
     * The multiplicative roles of a theory instance.
     */
    private static final List<String> MUL_ROLES = List.of(MUL, AND, MEET);

    /**
     * The operand slots the equations are rendered over — the same slots the algebra tests write a probe into
     * before dereferencing {@code *a}. They are ordinary memory cells: a call rewrites them.
     */
    private static final String A = "*a";
    private static final String B = "*b";
    private static final String D = "*d";
    private static final String Z = "*z";

    /**
     * THE PROBES — keyed by CARRIER tid (a refinement finds its base's probe: {@code nat::T} answers with
     * {@code int}'s 23), and exactly the operands the type tests return from {@code sample()}. Mutable through
     * {@link #registerSample(fURI, Obj)}: a carrier this class does not name supplies its own probe, so the tool
     * answers for ANY mtron type, not only the eight base carriers.
     */
    private static final Map<fURI, Obj> SAMPLES = samples();
    /**
     * THE SECOND OPERAND — a distinct, known-good element of the same carrier. See the class notes: a single
     * generator cannot falsify a commutativity claim on a free monoid.
     */
    private static final Map<fURI, Obj> PARTNERS = partners();

    /**
     * REGISTER A CARRIER'S PROBE — the extension point a type test uses instead of a private map: the sample an
     * equation's {@code *a} is written with. Keyed by the carrier's BASE path, the same key {@link #probe} reads,
     * so a refinement's registration serves its base too. Null and {@code noobj} are ignored (they would be a
     * probe that says nothing), which leaves the declaration's own zero/one in charge.
     */
    public static void registerSample(final fURI carrier, final Obj sample) {
        if (null == carrier || null == sample || sample.isNoObj())
            return;
        SAMPLES.put(null == carrier.basePath() ? carrier : carrier.basePath(), sample);
    }

    private static Map<fURI, Obj> samples() {
        final Map<fURI, Obj> samples = new ConcurrentHashMap<>();
        samples.put(INT_TID.basePath(), jnt(23));
        samples.put(REAL_TID.basePath(), real(23.5d));
        samples.put(STR_TID.basePath(), str("abc"));
        samples.put(BOOL_TID.basePath(), bool(true));
        samples.put(BYTES_TID.basePath(), bytes(ByteBuffer.wrap(new byte[]{1, 2, 3})));
        samples.put(URI_TID.basePath(), uri(f("/a/b")));
        samples.put(LST_TID.basePath(), lst(jnt(1), jnt(2), jnt(3)));
        samples.put(REC_TID.basePath(), MRec.rec(uri("a"), jnt(1), uri("b"), jnt(2)));
        samples.put(REL_TID.basePath(), rel(uri("a"), jnt(1)));
        return samples;
    }

    private static Map<fURI, Obj> partners() {
        final Map<fURI, Obj> partners = new LinkedHashMap<>();
        partners.put(INT_TID.basePath(), jnt(12));
        partners.put(REAL_TID.basePath(), real(12.5d));
        partners.put(STR_TID.basePath(), str("xyz"));
        partners.put(BOOL_TID.basePath(), bool(false));
        partners.put(BYTES_TID.basePath(), bytes(ByteBuffer.wrap(new byte[]{4, 5, 6})));
        partners.put(URI_TID.basePath(), uri(f("/c/d")));
        partners.put(LST_TID.basePath(), lst(jnt(4), jnt(5), jnt(6)));
        partners.put(REC_TID.basePath(), MRec.rec(uri("c"), jnt(3), uri("d"), jnt(4)));
        // rel's partner MEETS the probe a=>1 at both ends (a=>1 · 1=>a is a=>a, 1=>a · a=>1 is 1=>1), so composition
        // is tested on arrows: a partner that meets neither end composes to noobj, and every check is noobj = noobj
        partners.put(REL_TID.basePath(), rel(jnt(1), uri("a")));
        partners.put(CODE_TID.basePath(), instB(mInstSet.END_INST_TID, lst()));
        return Collections.unmodifiableMap(partners);
    }

    /* ------------------------------------------------------------------ the public analyzers */

    /**
     * Validate the CLAIMED laws against a type: every role-bearing operation the type declares is analyzed and
     * the verdicts are unioned in declaration order. A type that declares no operation leaves every claim
     * {@code unchecked} — a claim about an algebra that was never written is exactly the over-claim the tool
     * exists to expose.
     */
    public static Report analyzeType(final Type type, final List<Law> claimedLaws) {
        final List<Law> claimed = null == claimedLaws ? List.of() : claimedLaws;
        final List<Op> ops = opsOf(type);
        if (ops.isEmpty()) {
            final List<Result> results = new ArrayList<>();
            for (final Law law : claimed)
                results.add(Result.unchecked(law, OP, "the type declares no role-bearing operation"));
            return report(type, null, claimed, results);
        }
        final List<Report> reports = new ArrayList<>();
        for (final Op op : ops)
            reports.add(analyzeTypeInst(type, op.inst(), claimed));
        return merge(type, reports);
    }

    /**
     * Validate the CLAIMED laws against one instruction of the type. The instruction's role in the type's algebra
     * decides which identity and inverse a law reads; an instruction the algebra does not bind is analyzed
     * against the type's declared operations alone, with no identity of its own. A targeted analysis REFUSES a
     * degenerate probe exactly as the type-level run does: the precondition lives in {@link #check}, which both
     * analyzers reach, so a claim about one instruction can never be judged over operands that are not values of
     * the carrier or are identical.
     */
    public static Report analyzeTypeInst(final Type type, final Inst inst, final List<Law> claimedLaws) {
        final List<Law> claimed = null == claimedLaws ? List.of() : claimedLaws;
        final Op op = op(type, inst);
        return report(type,
                null == op ? null : uri(op.inst().tid()),
                claimed, check(type, op, claimed));
    }

    /**
     * Whether the type's declared algebra BINDS this operation — the same role read {@link #opsOf} makes,
     * exposed because a hand-written interpretation of an equation template needs the guard the analyzer uses
     * ("does this carrier declare the operation this row is about?"). Type-agnostic: no Java interface is asked.
     */
    public static boolean binds(final Type type, final fURI operation) {
        if (null == operation || null == operation.basePath())
            return false;
        return opsOf(type).stream().anyMatch(op -> op.address().basePath().equals(operation.basePath()));
    }

    /**
     * WHETHER THE LAW IS ABOUT THIS INSTRUCTION AT ALL — the signature match, and the decision that used to be a
     * ladder of {@code op.binary()} / "is this the group operation?" / "is this the multiplicative operation?"
     * tests spread through the equations.
     * <p>
     * A law is a statement about a SHAPE ({@link Law#wrt()}), so it applies to an instruction only when the
     * instruction's contract has that shape:
     * <ol>
     *   <li>an {@code obj}-shaped ({@code pure}) or bare-{@code inst}-shaped ({@code floatable},
     *       {@code blocked}, {@code partial}) law is about the instruction itself, so it matches every
     *       instruction — the equations then answer with their own reason, exactly as before;</li>
     *   <li>a carrier law ({@code inst?dom=…&rng=…}) matches when the instruction's {@code dom} and {@code rng}
     *       agree with the signature's, with the poly variable {@code A} standing for the carrier — one
     *       variable, so every {@code A} in the signature must land on the SAME carrier type;</li>
     *   <li>arity is read ONLY from the signature's {@code args} component: {@code args=[A]} demands exactly one
     *       argument beyond the lhs (a binary operation), while a signature with no {@code args} is
     *       ARITY-AGNOSTIC and matches any arity — an element law, a unary {@code neg}, or a fold. That is what
     *       lets an idempotent {@code zero} and an idempotent {@code or} be the same law.</li>
     * </ol>
     * Each {@code args} element is compared against the instruction's DECLARED argument type when its tid carries
     * one ({@code inst?…&args=[int]}, minted under {@code MInst.ARGS_IN_TID}) and against the type of the argument
     * VALUE otherwise — so a declared contract is judged as a contract, and an undeclared one as the only
     * evidence there is.
     * A mismatch is a CATEGORY ERROR, never a counterexample: the instruction is not the kind of thing the law
     * is about, so the verdict is {@code uncheckable} with the law's shape named, and neither confirms nor
     * refutes it.
     */
    public static boolean matches(final Law law, final Inst inst) {
        return null == law || null == inst || null == signatureMismatch(law, inst);
    }

    /**
     * THE SIGNATURE MATCH — null when the law's signature names this instruction's contract, else the one-line
     * refusal that names the law's shape. The census reads this through {@link #matches}, so the rule that
     * refuses a claim at analysis time is the same rule a declaration is held to.
     */
    private static String signatureMismatch(final Law law, final Inst inst) {
        if (null == law || null == inst || null == inst.tid())
            return null;
        // A LAW ANSWERS A SET OF SHAPES: it names this instruction's contract when ANY of them matches.
        for (final fURI signature : law.wrt())
            if (null == signatureMismatch(signature, inst))
                return null;
        return about(law);
    }

    /**
     * The signature match for ONE admissible shape of the law — null when this shape names the instruction's
     * contract, else the one-line refusal.
     */
    private static String signatureMismatch(final fURI signature, final Inst inst) {
        if (!signature.hasDom() && !signature.hasRng())
            return null;                        // `inst` (form) and `obj` (effect): about the instruction itself
        final fURI dom = inst.tid().dom();
        final fURI rng = inst.tid().rng();
        final List<fURI> args = signature.args();
        // THE DECLARED CONTRACT WINS OVER THE VALUES: a tid carrying `args` states its argument types itself
        // (MInst.ARGS_IN_TID), so the signature is compared against that declaration; an inst that declares none
        // is read through the argument VALUES it was minted with, which is the only contract it has. The test is
        // the KEY, not list emptiness: a bare `args` key declares ZERO arguments, which is a claim (and a
        // different one from "no claim"), while a missing key is no claim at all.
        final boolean declares = inst.tid().hasQ(ARGS);
        final List<fURI> declared = declares ? inst.tid().args() : List.of();
        // arity is a claim ONLY when the signature carries args: no args means "any arity"
        if (!args.isEmpty() && args.size() != (declares ? declared.size() : inst.args().count()))
            return "arity";
        final String[] carrier = {null};
        if (!agrees(dom, signature.dom(), carrier) || !agrees(rng, signature.rng(), carrier))
            return "dom/rng";
        for (int i = 0; i < args.size(); i++)
            if (!agrees(declares ? declared.get(i) : argType(inst, i), args.get(i), carrier))
                return "args";
        return null;
    }

    /**
     * Match one signature component against the instruction's component of the same slot — {@code dom} to
     * {@code dom}, {@code rng} to {@code rng}, one {@code args} element to one declared argument type.
     * <p>
     * An undeclared endpoint (the {@code #} a missing {@code dom}/{@code rng} reads as) and a GENERIC contract
     * (the {@code A}/{@code B} of a generic registration) accept anything, because they state no concrete type
     * to refuse. A signature's {@code A} BINDS the carrier through {@code carrier} and must agree with every
     * other {@code A} in the signature; an {@code A} carrying a coefficient ({@code A{*}}) additionally demands
     * an endpoint that can hold MANY copies — the fold shape is not the single-element one.
     */
    private static boolean agrees(final fURI actual, final fURI wanted, final String[] carrier) {
        if (null == actual || null == wanted)
            return true;
        final String actualName = baseName(actual);
        if (actual.isGeneric() || "#".equals(actualName) || "+".equals(actualName))
            return true;
        if (wanted.isGeneric()) {
            if (null == carrier[0])
                carrier[0] = actualName;            // ONE poly variable: the carrier the law is written over
            else if (!carrier[0].equals(actualName))
                return false;
            return wanted.c().isOne() || admitsMany(actual);
        }
        return baseName(wanted).equals(actualName);
    }

    /**
     * The type name a signature component is compared by. A law is written in the process vocabulary
     * ({@code dom=A}, {@code rng=bool}), so its names are relative, while an instruction's contract is absolute
     * ({@code /m/bool}); the base name is what both spellings share.
     */
    private static String baseName(final fURI uri) {
        if (null == uri)
            return "";
        final fURI base = uri.basePath();
        return null == base ? uri.toString() : base.name();
    }

    /**
     * Whether an endpoint can carry more than one element — the {@code A{*}} of a fold demands it, and a
     * single-element endpoint ({@code A}, {@code A{?}}) does not satisfy it.
     */
    private static boolean admitsMany(final fURI component) {
        final cInt c = component.c();
        return !c.isZero() && (null == c.max() || c.max() > 1);
    }

    /**
     * The declared type of an instruction's argument slot, as the address the signature is compared against;
     * null when the slot declares no type (which matches anything).
     */
    private static fURI argType(final Inst inst, final int index) {
        final Obj arg = inst.arg(index);
        return null == arg || arg.isNoObj() ? null : Obj.Helper.specificTypeId(arg);
    }

    /**
     * The refusal a shape mismatch answers with — the law's own signature, so a reader can see which shape was
     * expected instead of inferring it from a law name.
     */
    private static String about(final Law law) {
        return "the law is about " + law.wrt().stream().map(fURI::toString)
                .collect(java.util.stream.Collectors.joining(" or "));
    }

    /**
     * The union of per-operation reports: the verdicts concatenate, the operations are listed, and any failure or
     * unchecked claim anywhere makes the union not ok.
     */
    private static Report merge(final Type type, final List<Report> reports) {
        final List<Obj> holds = new ArrayList<>();
        final List<Obj> failed = new ArrayList<>();
        final List<Obj> unchecked = new ArrayList<>();
        final List<Obj> uncheckable = new ArrayList<>();
        final List<Obj> ops = new ArrayList<>();
        for (final Report report : reports) {
            holds.addAll(report.holds().lstValue());
            failed.addAll(report.failed().lstValue());
            unchecked.addAll(report.unchecked().lstValue());
            uncheckable.addAll(report.uncheckable().lstValue());
            if (null != report.inst())
                ops.add(report.inst());
        }
        return new Report(null == type ? null : type.vid(), ops.isEmpty() ? null : lst(ops),
                reports.isEmpty() ? lst() : reports.getFirst().claimed(),
                lst(holds), lst(failed), lst(unchecked), lst(uncheckable),
                failed.isEmpty() && unchecked.isEmpty());
    }

    /* ------------------------------------------------------------------ the report */

    /**
     * The verdict of one analyzer run, rendered by {@link #toRec()} as
     * <pre>{type=&gt;…, inst=&gt;…, claimed=&gt;[…], holds=&gt;[…], failed=&gt;[…], unchecked=&gt;[…],
     * uncheckable=&gt;[…], ok=&gt;bool}</pre>
     * where each verdict list holds one rec per law — {@code {law=&gt;"involution", role=&gt;"inv",
     * lhs=&gt;"…", rhs=&gt;"…"}} plus {@code got=&gt;[lhs-result, rhs-result]} on a judged claim and
     * {@code reason} on an unchecked/uncheckable one.
     * {@code inst} is the instruction analyzed; a type-level run carries the lst of operations it unioned.
     */
    public record Report(fURI type, Obj inst, Lst claimed, Lst holds, Lst failed, Lst unchecked,
                         Lst uncheckable, boolean ok) {

        public Obj toRec() {
            return MRec.rec(
                    uri(TYPE), null == this.type ? noobj() : uri(this.type),
                    uri(INST), null == this.inst ? noobj() : this.inst,
                    uri(CLAIMED), this.claimed,
                    uri(HOLDS), this.holds,
                    uri(FAILED), this.failed,
                    uri(UNCHECKED), this.unchecked,
                    uri(UNCHECKABLE), this.uncheckable,
                    uri(OK), bool(this.ok));
        }
    }

    private static Report report(final Type type, final Obj inst, final List<Law> claimed, final List<Result> results) {
        final List<Obj> holds = new ArrayList<>();
        final List<Obj> failed = new ArrayList<>();
        final List<Obj> unchecked = new ArrayList<>();
        final List<Obj> uncheckable = new ArrayList<>();
        for (final Result result : results) {
            switch (result.verdict()) {
                case holds -> holds.add(toRec(result));
                case failed -> failed.add(toRec(result));
                case unchecked -> unchecked.add(toRec(result));
                case uncheckable -> uncheckable.add(toRec(result));
            }
        }
        return new Report(null == type ? null : type.vid(), inst,
                lst(claimed.stream().map(law -> (Obj) uri(law.name())).toList()),
                lst(holds), lst(failed), lst(unchecked), lst(uncheckable),
                failed.isEmpty() && unchecked.isEmpty());
    }

    /* ------------------------------------------------------------------ the algebra */

    /**
     * The type's declared algebra — the theory instances it models, as {@link TheoryHelper#instances(Type)} serves
     * them off the type's {@code ?decq}.
     */
    private static Rec algebra(final Type type) {
        return null == type || !Machine.loaded() ? MRec.rec0() : TheoryHelper.instances(type);
    }

    /**
     * The VALUE a role binds — a literal element ({@code zero => 0}, {@code id => ""}, {@code one => <>}) or,
     * for an operation used as a value (the code ring's {@code one => !*id}), the operation itself. Null when the
     * role is absent or names an operation the type does not register.
     */
    private static Obj value(final Type type, final Rec block, final String... roles) {
        if (null == block)
            return null;
        for (final String role : roles) {
            final Obj holder = block.atDirect(uri(role));
            if (holder.isNoObj())
                continue;
            final Optional<fURI> pointer = Obj.Helper.getPointer(holder);
            if (pointer.isEmpty())
                return holder;
            final Inst target = contract(type, pointer.get());
            if (null != target)
                return target;
        }
        return null;
    }

    /**
     * The registered contract an op address names over this carrier — what decides an operation's arity, and
     * what tells a role holding a real operation from one holding a bare uri.
     */
    private static Inst contract(final Type type, final fURI address) {
        if (!Machine.loaded() || null == type || null == address || null == address.basePath())
            return null;
        try {
            return Typer.Helper.deduceTypeInst(type, address);
        } catch (final Throwable e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ the operations */

    /**
     * An operation of the type: its address, its registered contract, and the role/instance that binds it.
     */
    private record Op(fURI address, Inst inst, String role, String instance, Rec block, String name,
                      boolean binary) {
    }

    /**
     * Every role-bearing operation the type declares, one per op family (a {@code plus} declared by a
     * {@code ring}, an {@code add_group} and an {@code add_monoid} is ONE operation — the richest binding wins,
     * so a monoid/group instance's own {@code id} and {@code inv} are the ones a law reads).
     */
    private static List<Op> opsOf(final Type type) {
        final Map<String, Op> ops = new LinkedHashMap<>();
        for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
            if (!entry.getKey().isUri() || !entry.getValue().isRec())
                continue;
            final Rec block = entry.getValue().asRec();
            for (final String role : OP_ROLES) {
                final fURI address = TheoryHelper.roleAddress(block, role);
                if (null == address || null == address.basePath() || ops.containsKey(address.basePath().toString()))
                    continue;
                final Op op = bind(type, address);
                if (null != op)
                    ops.put(address.basePath().toString(), op);
            }
        }
        return new ArrayList<>(ops.values());
    }

    /**
     * THE RICHEST BINDING of an operation over the type — how the type's algebra says the operation is declared.
     * An {@code op} role outranks the named roles (a group/monoid instance states the operation's own identity
     * and inverse), an identity adds, an inverse adds more: int's {@code plus} is one operation declared by a
     * {@code ring}, an {@code add_group} and an {@code add_monoid}, and the group is the binding whose
     * {@code id} (0) and {@code inv} (neg) an {@code involution} claim reads. Null when the address names no
     * registered operation of the type.
     */
    private static Op bind(final Type type, final fURI address) {
        if (null == address || null == address.basePath())
            return null;
        final Inst inst = contract(type, address);
        if (null == inst)
            return null;
        Op best = null;
        int bestScore = -1;
        for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
            if (!entry.getKey().isUri() || !entry.getValue().isRec())
                continue;
            final Rec block = entry.getValue().asRec();
            for (final String role : OP_ROLES) {
                final fURI bound = TheoryHelper.roleAddress(block, role);
                if (null == bound || null == bound.basePath() || !bound.basePath().equals(address.basePath()))
                    continue;
                final Op candidate = new Op(address, inst, role, entry.getKey().uriValue().name(), block,
                        address.basePath().name(), 1 == inst.args().count());
                final int score = (OP.equals(role) ? 4 : 0)
                        + (null != identity(type, candidate) ? 2 : 0)
                        + (null != TheoryHelper.roleAddress(block, INV) ? 1 : 0);
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return null == best
                ? new Op(address, inst, OP, null, MRec.rec0(), address.basePath().name(), 1 == inst.args().count())
                : best;
    }

    /**
     * One instruction of the type bound the way {@link #opsOf} binds it.
     */
    private static Op op(final Type type, final Inst inst) {
        if (null == inst || null == inst.tid() || null == inst.tid().basePath())
            return null;
        final Op bound = bind(type, inst.tid());
        return null != bound ? bound
                : new Op(inst.tid(), inst, OP, null, MRec.rec0(), inst.tid().basePath().name(),
                1 == inst.args().count());
    }

    /* ------------------------------------------------------------------ the roles a law reads */

    /**
     * The type-level roles a law may need: the additive op, the multiplicative op, the inverse op, the zero.
     */
    private record Roles(fURI add, fURI mul, fURI inv, Obj zero) {
    }

    private static Roles roles(final Type type) {
        fURI add = null;
        fURI mul = null;
        fURI inv = null;
        Obj zero = null;
        for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
            if (!entry.getValue().isRec())
                continue;
            final Rec block = entry.getValue().asRec();
            if (null == add)
                add = first(block, ADD_ROLES);
            if (null == mul)
                mul = first(block, MUL_ROLES);
            if (null == inv)
                inv = first(block, List.of(INV, NOT));
            if (null == zero)
                zero = value(type, block, ZERO, BOTTOM);
        }
        return new Roles(add, mul, inv, zero);
    }

    private static fURI first(final Rec block, final List<String> roles) {
        for (final String role : roles) {
            final fURI address = TheoryHelper.roleAddress(block, role);
            if (null != address && null != address.basePath())
                return address;
        }
        return null;
    }

    /**
     * The identity of the operation in its OWN binding: the instance's {@code id}, or — for a named additive or
     * multiplicative role — the instance's {@code zero}/{@code one} (a ring states its identities as elements, not
     * as an {@code id} role). Null when the binding declares neither, which is what makes a {@code unit} claim on
     * a bare semigroup {@code unchecked} rather than wrong.
     */
    private static Obj identity(final Type type, final Op op) {
        final Obj direct = value(type, op.block(), ID);
        if (null != direct)
            return direct;
        if (ADD_ROLES.contains(op.role()))
            return value(type, op.block(), ZERO, BOTTOM);
        if (MUL_ROLES.contains(op.role()))
            return value(type, op.block(), ONE, TOP);
        return null;
    }

    /* ------------------------------------------------------------------ the law tests */

    /**
     * The equations a law is tested with, rendered over the probe slots — one pair (lhs, rhs) per law. The
     * SIGNATURE is consulted first ({@link #matches}): a law whose subject shape this operation does not have is
     * {@code uncheckable} with the shape named, because it is a category error and not a counterexample. Past
     * that gate a law whose required role does not resolve is {@code unchecked}; a law this version has no
     * runnable equation for is {@code uncheckable} with its own reason.
     * <p>
     * THE BINARY GUARDS BELOW ARE NO LONGER CLASSIFICATION. A signature with no {@code args} is arity-agnostic
     * on purpose (an idempotent {@code zero}, a commutative stream reduction), so an operation it matches may
     * still not be binary; the templates below state these laws between OPERANDS, so such an operation answers
     * the same {@code "the operation is not binary"} it always did — the law applies, the equation does not.
     */
    private static List<Result> test(final Type type, final Op op, final Roles roles, final Law law) {
        // THE SUBJECT SHAPE DECIDES APPLICABILITY — a law about another shape is not this operation's law
        final String mismatch = signatureMismatch(law, op.inst());
        if (null != mismatch)
            return List.of(Result.uncheckable(law, op.role(), mismatch));
        switch (law) {
            case monoidic:
                return op.binary()
                        ? List.of(evaluate(law, op.role(),
                        apply(op.name(), apply(op.name(), A, B), A),
                        apply(op.name(), A, apply(op.name(), B, A))))
                        : List.of(notBinary(law, op));
            case commutative:
                return op.binary()
                        ? List.of(evaluate(law, op.role(), apply(op.name(), A, B), apply(op.name(), B, A)))
                        : List.of(notBinary(law, op));
            case idempotent:
                // no args in the signature: the law is arity-agnostic, and the template below is its binary form
                return op.binary()
                        ? List.of(evaluate(law, op.role(), apply(op.name(), A, A), A))
                        : List.of(notBinary(law, op));
            case unit: {
                if (!op.binary())
                    return List.of(notBinary(law, op));
                final Obj identity = identity(type, op);
                if (null == identity)
                    return List.of(Result.unchecked(law, ID, "the operation declares no identity"));
                slot("d", identity);
                return List.of(evaluate(law, op.role(), apply(op.name(), A, D), A));
            }
            case absorbing: {
                if (!op.binary())
                    return List.of(notBinary(law, op));
                if (null == roles.zero())
                    return List.of(Result.unchecked(law, ZERO, "the type declares no zero"));
                slot("z", roles.zero());
                return List.of(evaluate(law, op.role(), apply(op.name(), A, Z), Z));
            }
            case involution:
                return involution(type, op, roles, law);
            case complement:
                return complement(type, op, roles, law);
            case left_distributive:
            case right_distributive:
            case semi_right_distributive:
                return distributive(op, roles, law);
            default:
                return List.of(Result.uncheckable(law, op.role(), uncheckableReason(law)));
        }
    }

    /**
     * {@code involution} is THREE claims, decided by where the operation sits in the algebra:
     * <ul>
     *   <li>(a) THE INVERSE ITSELF — the operation is the instance's {@code inv}/{@code not} role, and states
     *       {@code inv(inv(a)) = a}. This is what a Boolean algebra's {@code not} satisfies.</li>
     *   <li>(b) A GROUP OPERATION — the operation binds to a GROUP-ish instance ({@code group}, {@code ring},
     *       {@code field}, {@code near_ring}; the {@code add_group}/{@code mul_group} spellings) and has an
     *       {@code inv} role beside it, so it states {@code a·inv(a) = id}.</li>
     *   <li>(c) EVERYTHING ELSE — a lattice, Boolean, rig or monoid operation is NOT a group operation, so the
     *       group form is not its law: {@code uncheckable} with that reason, never a failure. A Boolean algebra's
     *       {@code and}/{@code or} land here, and the complement they do satisfy is {@code Law.complement}.</li>
     * </ul>
     * A role the type does not declare at all leaves the claim unchecked — the over-claim case.
     */
    private static List<Result> involution(final Type type, final Op op, final Roles roles, final Law law) {
        if (null == roles.inv())
            return List.of(Result.unchecked(law, INV, "the type declares no inverse operation"));
        final fURI bound = null != TheoryHelper.roleAddress(op.block(), INV) ? TheoryHelper.roleAddress(op.block(), INV) : TheoryHelper.roleAddress(op.block(), NOT);
        if (null != bound && null != bound.basePath() && bound.basePath().equals(op.address().basePath()))
            return List.of(evaluate(law, op.role(), call(op.name(), call(op.name(), A)), A));
        if (null == bound || !groupish(op.instance()))
            return List.of(Result.uncheckable(law, op.role(), "the operation is not a group operation"));
        if (!op.binary())
            return List.of(notBinary(law, op));   // the group form is an OPERAND equation, so it needs two
        final Obj identity = identity(type, op);
        if (null == identity)
            return List.of(Result.unchecked(law, ID, "the group operation declares no identity"));
        slot("d", identity);
        return List.of(evaluate(law, op.role(), apply(op.name(), A, call(bound.basePath().name(), A)), D));
    }

    /**
     * Does the instance that binds an operation state a GROUP? The names are the ones {@code declareAlgebras}
     * uses ({@code ring}, {@code field}, {@code add_group}, {@code mul_group}, {@code free_groupoid}) plus the
     * plain theory names {@code GROUP_ALGEBRAS} names, so a future spelling that keeps the suffix is recognized.
     */
    private static boolean groupish(final String instance) {
        if (null == instance)
            return false;
        final String name = instance.toLowerCase(Locale.ROOT);
        return GROUP_ALGEBRAS.contains(name) || name.endsWith("_group")
                || name.endsWith("_groupoid") || name.endsWith("_ring") || name.endsWith("_field");
    }

    /**
     * {@code complement} is the complemented-lattice law as an OPERAND equation: {@code a·inv(a) = inv(id)} —
     * an element combined with its complement is the OTHER bound of the lattice, so for {@code and} over a
     * Boolean algebra {@code a∧¬a = ¬⊤ = ⊥}, and for {@code or} {@code a∨¬a = ¬⊥ = ⊤}. It needs no role the
     * declaration does not already carry: the operation must be BINARY, the type must declare an inverse
     * ({@code inv}/{@code not}), and the binding must state an identity ({@code id}, else the {@code zero}/
     * {@code one} its named additive/multiplicative role states). A non-binary operation (the inverse itself)
     * carries no operand equation and is {@code uncheckable}; a missing inverse or identity is the over-claim
     * case ({@code unchecked}).
     */
    private static List<Result> complement(final Type type, final Op op, final Roles roles, final Law law) {
        if (!op.binary())
            return List.of(notBinary(law, op));
        final fURI bound = null != TheoryHelper.roleAddress(op.block(), INV) ? TheoryHelper.roleAddress(op.block(), INV) : roles.inv();
        if (null == bound || null == bound.basePath())
            return List.of(Result.unchecked(law, INV, "the type declares no inverse operation"));
        final Obj identity = identity(type, op);
        if (null == identity)
            return List.of(Result.unchecked(law, ID, "the operation declares no identity"));
        slot("d", identity);
        return List.of(evaluate(law, op.role(),
                apply(op.name(), A, call(bound.basePath().name(), A)),
                call(bound.basePath().name(), D)));
    }

    /**
     * The three distributivity laws are about the MULTIPLICATIVE operation distributing over the additive one, so
     * both roles must resolve and the analyzed operation must be the multiplicative one. {@code semi_right} is the
     * reducer's law (Thm 19): the right side re-applies the operation.
     */
    private static List<Result> distributive(final Op op, final Roles roles, final Law law) {
        if (null == roles.add())
            return List.of(Result.unchecked(law, ADD, "the type declares no additive operation"));
        if (null == roles.mul())
            return List.of(Result.unchecked(law, MUL, "the type declares no multiplicative operation"));
        if (!roles.mul().basePath().equals(op.address().basePath()))
            return List.of(Result.uncheckable(law, op.role(), "the law is about the multiplicative operation"));
        final String add = roles.add().basePath().name();
        final String times = op.name();
        final String lhs = switch (law) {
            case left_distributive -> apply(times, A, apply(add, B, A));                  // a·(b+a)
            default -> apply(times, apply(add, A, B), A);                                 // (a+b)·a
        };
        final String rhs = switch (law) {
            case left_distributive -> apply(add, apply(times, A, B), apply(times, A, A)); // a·b + a·a
            case right_distributive -> apply(add, apply(times, A, A), apply(times, B, A)); // a·a + b·a
            default -> apply(times, apply(add, apply(times, A, A), apply(times, B, A)), A); // (a·a + b·a)·a
        };
        return List.of(evaluate(law, op.role(), lhs, rhs));
    }

    private static Result notBinary(final Law law, final Op op) {
        return Result.uncheckable(law, op.role(), "the operation is not binary");
    }

    /**
     * The laws this version carries no runnable equation for, and why.
     */
    private static String uncheckableReason(final Law law) {
        return switch (law) {
            case pure -> "a declared effect, not an equation";
            case floatable -> "a stream-ring coefficient law, not an operand equation";
            case blocked -> "a declared barrier, not an equation";
            case poset -> "the type declares no order operation to compare";
            case action -> "an action needs a monoid acting on the carrier, which no role declares";
            case magmadic -> "closure only — the law demands no equation";
            case kleene -> "an unbounded iterate, not a finite operand test";
            case nilpotent -> "an unbounded iterate to a zero power, not a finite operand test";
            case partial -> "the zeroable lift of a value, not an equation";
            case prime -> "an element property, not an equation between operands";
            case zero_divisor -> "an existential over distinct elements";
            case cyclic -> "an existential over a generated subalgebra";
            default -> "no runnable test for this law";
        };
    }

    /* ------------------------------------------------------------------ evaluation */

    /**
     * THE NUMERIC TOLERANCE a law's two sides are compared within — see {@link #equal(Obj, Obj)}.
     */
    private static final double EPSILON = 1.0e-9d;

    /**
     * EQUALITY FOR A LAW'S TWO SIDES — structural equality ( ) OR, for the two numeric carriers
     * ({@code int}, {@code real}), a small RELATIVE epsilon: {@code |l-r| <= EPSILON·max(1,|l|,|r|)}.
     *
     * <p>WHY THE EPSILON EXISTS: {@code real} is IEEE-754, so a field law that is exact over the rationals need
     * not be exact over the doubles — {@code a·(1/a)} can land one ulp off 1, and a purely structural compare
     * would report a law FALSE that the carrier actually satisfies (a false failure, the worst answer an oracle
     * can give). 1e-9 is far above the last-place error of a double (2.2e-16 relative) and far below any
     * distinction an algebraic law test can draw.
     *
     * <p>WHY IT IS NOT IN {@code Obj.equals}/{@code hashCode}: approximate equality is NOT transitive (a≈b and
     * b≈c do not give a≈c), so it cannot define an equivalence relation — and {@code equals}/{@code hashCode}
     * are exactly that for hashed lookups ({@code Obj.Helper.objEquals} backs rec keys, space reads and set
     * membership). Two reals 1e-12 apart must stay two entries. The tolerance lives HERE, at the point where a
     * law is judged, and nowhere else.
     *
     * <p>Public because the hand-written interpretations of the same templates (the algebra tests) compare what
     * the tool compares — one notion of "the two sides agree".
     */
    public static boolean equal(final Obj lhs, final Obj rhs) {
        if (null == lhs || null == rhs)
            return lhs == rhs;
        if (lhs.equals(rhs))
            return true;
        final Double left = numeric(lhs);
        final Double right = numeric(rhs);
        if (null == left || null == right)
            return false;
        final double scale = Math.max(1.0d, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= EPSILON * scale;
    }

    /**
     * The double a numeric obj denotes ({@code int} or {@code real}); null for every other carrier.
     */
    private static Double numeric(final Obj obj) {
        if (null == obj)
            return null;
        try {
            if (obj.isInt())
                return obj.intValue().doubleValue();
            if (obj.isReal())
                return obj.realValue().doubleValue();
        } catch (final Throwable e) {
            return null;
        }
        return null;
    }

    /**
     * THE EVALUATION — render the equation as mtron, parse it, and apply it: the mechanism the algebra tests
     * already use ({@code ObjmtronSerializer.parse(code).apply()} over a probe written to memory). The two sides
     * are compared by {@link #equal(Obj, Obj)} — structural, with the numeric tolerance — and BOTH evaluated
     * objs are carried into the result, on a failure as well as on a hold.
     */
    private static Result evaluate(final Law law, final String role, final String lhs, final String rhs) {
        try {
            final Obj left = ObjmtronSerializer.parse(lhs).apply();
            final Obj right = ObjmtronSerializer.parse(rhs).apply();
            return equal(left, right)
                    ? Result.holds(law, role, lhs, rhs, left, right)
                    : Result.failed(law, role, lhs, rhs, left, right);
        } catch (final Throwable e) {
            return Result.uncheckable(law, role, "the equation could not be evaluated: " + message(e));
        }
    }

    private static String message(final Throwable e) {
        final String message = null == e.getMessage() ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    private static void slot(final String name, final Obj value) {
        Machine.current().memory().write(name, value);
    }

    /**
     * The operands a check runs over: {@code *a} is the probe, {@code *b} its distinct partner, {@code *d} the
     * identity a {@code unit} law reads and {@code *z} the zero an {@code absorbing} law reads.
     *
     * <p>THE PROBE PAIR IS GUARDED FIRST ({@link #degenerate}) — a law whose operands are not values of the
     * carrier or are the same element is answered {@code uncheckable} before its equation is run. Both analyzers
     * pass through here, so {@link #analyzeTypeInst(Type, Inst, List)} refuses a degenerate probe exactly as
     * {@link #analyzeType(Type, List)} does.
     */
    private static List<Result> check(final Type type, final Op op, final List<Law> claimed) {
        final List<Result> results = new ArrayList<>();
        if (null == op) {
            for (final Law law : claimed)
                results.add(Result.unchecked(law, OP, "no operation is bound"));
            return results;
        }
        final Obj probe = probe(type);
        if (null == probe) {
            for (final Law law : claimed)
                results.add(Result.uncheckable(law, op.role(), "no probe"));
            return results;
        }
        if (!Machine.loaded()) {
            for (final Law law : claimed)
                results.add(Result.uncheckable(law, op.role(), "no machine to evaluate against"));
            return results;
        }
        final Obj partner = partner(type, probe);
        slot("a", probe);
        slot("b", partner);
        final Roles roles = roles(type);
        for (final Law law : claimed) {
            final String probeReason = degenerate(type, probe, partner);
            if (null != probeReason)
                results.add(Result.uncheckable(law, op.role(), probeReason));
            else
                results.addAll(test(type, op, roles, law));
        }
        return results;
    }

    /* ------------------------------------------------------------------ the probe precondition */

    /**
     * THE DEGENERATE-PROBE PRECONDITION — the reasons a law's verdict would be evidence of nothing, or null when
     * the probe pair is sound. Checked per law, BEFORE the equation runs, and reported as {@code uncheckable}
     * (never {@code failed}): two sides that agree over operands the operation never received say nothing about
     * the carrier.
     *
     * <p>The motivating bug — {@code verify(plus?int<=int, [monoidic, commutative])} answered {@code commutative
     * FAILED} with {@code got=>'12'}, a str holding the partner operand, while {@code monoidic} still reported
     * {@code holds}. Equality of two expressions is no evidence when the operands are of the wrong type
     * ({@code "the probe is not a value of the carrier"}) or identical ({@code "the probe operands are
     * identical"}). There are exactly these two reasons, and no third: a product that equals one of its operands
     * is ABSORPTION, a property of the operation that a law is judged over (bool's {@code or} and {@code and}
     * collapse every probe to {@code true}/{@code false} and are still commutative), so it is never a broken
     * operand binding. Refusing it made {@code bool}'s {@code or} report {@code commutative} uncheckable on a
     * claim that in fact holds.
     */
    private static String degenerate(final Type type, final Obj a, final Obj b) {
        if (!ofCarrier(type, a) || !ofCarrier(type, b))
            return "the probe is not a value of the carrier";
        if (equal(a, b))
            return "the probe operands are identical";
        return null;
    }

    /**
     * Is this obj a value of the carrier? Two reads, both required: its tid sits on the carrier's path (a
     * refinement of the carrier's own address is still on that path), and the carrier's type test admits the
     * value's type — a {@code str("12")} registered as int's probe has neither, and a value that renders as a
     * different type (the {@code got=>'12'} symptom) is refused here rather than compared.
     */
    private static boolean ofCarrier(final Type type, final Obj operand) {
        if (null == type || null == type.tid() || !isValue(type, operand))
            return false;
        final fURI carrier = null == type.tid().basePath() ? type.tid() : type.tid().basePath();
        final fURI value = null == operand.tid() ? null
                : (null == operand.tid().basePath() ? operand.tid() : operand.tid().basePath());
        if (null == value)
            return false;
        return value.equals(carrier) || value.hasPrefix(carrier) || carrier.hasPrefix(value);
    }

    /**
     * The probe an operation of the type is applied to, by carrier tid. A REGISTERED sample wins; with none, the
     * type's own declaration supplies one — the literal it binds under {@code zero} (then {@code one}/{@code id})
     * — so the tool answers for ANY mtron type, not only the eight base carriers whose samples are built in. A
     * type whose declaration names neither, or whose {@code zero}/{@code one} is not a value of the type, answers
     * {@code uncheckable "no probe"}: the algebra never said what element to try. {@code code} carries no scalar
     * sample: its operand is the type's own identity morphism, else {@code id()}.
     */
    private static Obj probe(final Type type) {
        if (null == type || null == type.tid())
            return null;
        final fURI carrier = null == type.tid().basePath() ? type.tid() : type.tid().basePath();
        final Obj sample = SAMPLES.get(carrier);
        if (null != sample)
            return sample;
        if (CODE_TID.basePath().equals(carrier)) {
            for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
                if (!entry.getValue().isRec())
                    continue;
                final Obj identity = value(type, entry.getValue().asRec(), ONE, ID);
                if (null != identity)
                    return identity;
            }
            return instB(mInstSet.ID_INST_TID, lst());
        }
        return declaredProbe(type);
    }

    /**
     * THE FALLBACK PROBE — the type's declared {@code zero}/{@code bottom} (an additive carrier's 0), else its
     * {@code one}/{@code top}/{@code id} (a multiplicative carrier's 1). The role's value must BE a value of the
     * type: a role holding an operation (a {@code !*} pointer) is an operation, not a probe.
     */
    private static Obj declaredProbe(final Type type) {
        for (final List<String> roles : List.of(List.of(ZERO, BOTTOM), List.of(ONE, TOP, ID))) {
            for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
                if (!entry.getValue().isRec())
                    continue;
                final Obj candidate = value(type, entry.getValue().asRec(), roles.toArray(String[]::new));
                if (isValue(type, candidate))
                    return candidate;
            }
        }
        return null;
    }

    /**
     * Is this obj a value of the type (not an operation, not nothing, not a type)?
     */
    private static boolean isValue(final Type type, final Obj candidate) {
        if (null == candidate || candidate.isNoObj() || candidate.isInst() || candidate.isCode() || candidate.isType())
            return false;
        try {
            return type.test(candidate.type());
        } catch (final Throwable e) {
            return false;
        }
    }

    private static Obj partner(final Type type, final Obj probe) {
        if (null == type || null == type.tid())
            return probe;
        final fURI carrier = null == type.tid().basePath() ? type.tid() : type.tid().basePath();
        final Obj partner = PARTNERS.get(carrier);
        return null == partner ? probe : partner;
    }

    /**
     * {@code lhs.op(rhs)} as mtron — the chain form the algebra tests write ({@code *a.plus(*b)}).
     */
    private static String apply(final String op, final String lhs, final String rhs) {
        return lhs + "." + op + "(" + rhs + ")";
    }

    /**
     * {@code lhs.op()} as mtron — a unary operation ({@code *a.neg()}).
     */
    private static String call(final String op, final String lhs) {
        return lhs + "." + op + "()";
    }

    /* ------------------------------------------------------------------ the verdict */

    private enum Verdict {
        holds, failed, unchecked, uncheckable
    }

    /**
     * One law's verdict, with the two OBJs the equation actually evaluated to — one per side, not one rendered
     * string. Both sides are kept because a single side is not evidence: the bug this shape exists to expose
     * reported {@code "got"=>'12'} for {@code plus?int<=int}, a str holding the partner operand, and no reader
     * could tell whether the lhs had computed anything at all.
     */
    private record Result(Law law, String role, Verdict verdict, String lhs, String rhs, Obj gotL, Obj gotR,
                          String reason) {

        static Result holds(final Law law, final String role, final String lhs, final String rhs,
                            final Obj gotL, final Obj gotR) {
            return new Result(law, role, Verdict.holds, lhs, rhs, gotL, gotR, null);
        }

        static Result failed(final Law law, final String role, final String lhs, final String rhs,
                             final Obj gotL, final Obj gotR) {
            return new Result(law, role, Verdict.failed, lhs, rhs, gotL, gotR, null);
        }

        static Result unchecked(final Law law, final String role, final String reason) {
            return new Result(law, role, Verdict.unchecked, "", "", null, null, reason);
        }

        static Result uncheckable(final Law law, final String role, final String reason) {
            return new Result(law, role, Verdict.uncheckable, "", "", null, null, reason);
        }
    }

    /**
     * One law's verdict as a rec: {@code {law, role, lhs, rhs}} plus the evaluated pair {@code got=>
     * [lhs-result, rhs-result]} on a judged law, {@code reason} on an unchecked/uncheckable one. The pair is a
     * {@code lst} of the two {@code Obj}s — {@code got=>[35,35]} says both sides reached the operation, while
     * {@code got=>['12',35]} names the broken side immediately.
     */
    private static Obj toRec(final Result result) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(LAW), uri(result.law().name()));
        if (null != result.role())
            map.put(uri(ROLE), uri(result.role()));
        map.put(uri(LHS), str(result.lhs()));
        map.put(uri(RHS), str(result.rhs()));
        if (null != result.gotL() && null != result.gotR())
            map.put(uri(GOT), lst(result.gotL(), result.gotR()));
        if (null != result.reason())
            map.put(uri(REASON), str(result.reason()));
        return MRec.rec(map);
    }
}
