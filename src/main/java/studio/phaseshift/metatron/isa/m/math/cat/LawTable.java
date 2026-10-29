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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.Derivation;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.DERIVATION;
import static studio.phaseshift.metatron.Tokens.INV;
import static studio.phaseshift.metatron.Tokens.INVERSE;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.OP;
import static studio.phaseshift.metatron.Tokens.RHS;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.GROUP_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.GROUP_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.sum_;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.furi.q.QCollection.lawOf;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.Typer;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A per-instset law table — the declared laws of one instruction-set family, collocated in one place.
 *
 * <p>The table has two axes, matching the two blocks the category lifts:
 * <ul>
 *   <li><b>process laws</b> — the {@code law::T} labels a <em>morphism</em> obeys, keyed by the inst
 *       ({@link #lookup(fURI)}). These are proved once and registered per family; they cannot be derived.
 *       The operation's inverse is a relation, not a law, so it is a paired field on {@link Entry} rather
 *       than mixed into the law list — but it is <em>computed</em> wherever the carrier's algebra states it
 *       ({@link #derivedInverse}), the declared field being the source only where the algebra says nothing
 *       (a one-way retraction such as int's {@code div}).</li>
 *   <li><b>structural laws</b> — the algebraic theories an <em>object</em> models, keyed by the type
 *       ({@link #typeLaws(Type)}). These are the theory types ({@code ring_theory::T}, {@code group_theory::T},
 *       …) on {@code object::T}, not process laws.</li>
 * </ul>
 *
 * <p>A table is a singleton built per family: it registers its process laws in its constructor and implements
 * {@link #typeLawsUncached(Type)} for its object laws. See {@link mInstSetLawTable} for the base types'
 * instance.
 *
 * <p>Purity is declared, never inferred: an entry lists {@link Law#pure} when its instruction is a value operation,
 * and an instruction that does not — undeclared, or declared without it — is effectful, so no rule moves, drops or
 * replicates it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class LawTable {

    /**
     * A process-law row: the declared laws, the operation's inverse (null when none), and its derivation
     * (null when primitive — not definable as a composition of other instructions).
     * <p>
     * THE INVERSE IS DIRECTIONAL, AND IT HAS THREE SOURCES in this order:
     * <ol>
     *   <li><b>the carrier's algebra</b> — a group instance plus the operation's defining equation
     *       ({@code div ≡ mult ∘ inv}) gives {@code mult↔div}, and an {@link Law#involution} is its own inverse.
     *       This is the only source that can answer for an ENDOMORPHISM, where the category graph has no partner
     *       to offer: {@code position} short-circuits every endo to {@code retract}, and reads {@code coupling}
     *       only when dom and rng differ — so two endos over one carrier are indistinguishable to it;</li>
     *   <li><b>the graph's {@code coupling}</b> — an edge running back over the same two objects. A CANDIDATE pair,
     *       never a decision: the relation is symmetric, so the graph can propose a partner but cannot choose the
     *       direction, and the direction is the whole content of the claim ({@code int→str→int} holds,
     *       {@code str→int→str} does not: "007" becomes 7 becomes "7");</li>
     *   <li><b>the declared field</b> — the residue neither of the above answers, e.g. int's one-way
     *       {@code mult→div} (truncating division retracts multiplication, sound in one direction only).</li>
     * </ol>
     * {@code inverse(f) = g} claims {@code f·g = ε} only — f a section, g its retraction — and never
     * {@code g·f = ε}, which is why a lossy conversion may declare one direction and answer null for the other.
     */
    public record Entry(Lst laws, fURI inverse, Derivation derivation) {
    }

    /**
     * THE STORE'S RESIDUE of defining equations — the declarations {@code ?lawq} holds that the build-time registry
     * ({@link InstBuilder#declarations}) does not, which is the cold-boot ordering and any writer that is not
     * {@link InstBuilder}. Late-bound on purpose: the store cannot be walked until the machine is up, and the
     * registry keeps growing while the sets mount — so the snapshot records the two facts that make it stale (was
     * the machine up, and how many declarations had landed) and is retaken only when one of them changes.
     */
    private static volatile Map<fURI, Derivation> STORE_DERIVATIONS = Map.of();
    private static volatile int STORE_DERIVATIONS_AT = -1;
    private static volatile boolean STORE_DERIVATIONS_WAS_LOADED = false;

    private final Map<fURI, Entry> table = new HashMap<>();
    private final Map<fURI, Entry> resolved = new ConcurrentHashMap<>();

    private final Map<fURI, Rec> typeLawsCache = new ConcurrentHashMap<>();

    /**
     * Whether a derivation is in flight on this thread — the guard {@link #lookup} answers the declared row under.
     */
    private static final ThreadLocal<Boolean> DERIVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * The registered tables, keyed by their instset tid — {@link #typeLawsOf(Type)} dispatches a type to the
     * table of the instset that owns it (longest-prefix wins, so {@code /m/math/nat} routes to {@code /m/math}
     * rather than {@code /m}).
     */
    private static final Map<fURI, LawTable> TABLES = new LinkedHashMap<>();

    /**
     * Self-register this table under the instset whose types and insts it declares laws for.
     */
    protected LawTable(final fURI instsetTid) {
        TABLES.put(instsetTid, this);
    }

    /**
     * EVERY ROW OF EVERY REGISTERED TABLE, in registration order — keyed first by the instset whose family the
     * table belongs to, then by the row's address. This is the READ-ONLY census view, and it exists because
     * nothing else asks the question a census asks: a row is a declaration about an address, and if the
     * instruction at that address was renamed, unregistered or never registered, the row is dead weight the
     * runtime silently never consults. The answer is a copy of the outer map and unmodifiable views of the rows,
     * so a caller cannot reach in and write a declaration.
     */
    public static Map<fURI, Map<fURI, Entry>> rows() {
        final Map<fURI, Map<fURI, Entry>> rows = new LinkedHashMap<>();
        for (final Map.Entry<fURI, LawTable> table : TABLES.entrySet())
            rows.put(table.getKey(), Collections.unmodifiableMap(table.getValue().table));
        return rows;
    }

    /**
     * Register an inst's declared process laws and inverse.
     */
    protected final void entry(final fURI inst, final fURI inverse, final Law... declared) {
        this.table.put(inst, new Entry(laws(declared), inverse, null));
    }

    /**
     * Register an inst's declared process laws, inverse, and derivation.
     */
    protected final void entry(final fURI inst, final fURI inverse, final Derivation derivation, final Law... declared) {
        this.table.put(inst, new Entry(laws(declared), inverse, derivation));
    }

    /**
     * The declared process laws and inverse of the inst, or null when the inst is not in the table.
     * <p>
     * CONTRACT-LEVEL — the algebra read here is the one the contract was registered over ({@code tid.dom()}), which
     * is what a lift of that exact instruction means ({@code |mult?real<=real>>analysis>>inverse} is {@code div}).
     * An OPERAND may model less: {@link #declared(fURI, Type)} is the operand-aware read, and it is the one every
     * rewrite consults, so a nat never inherits int's additive inverse.
     * <p>
     * The inverse is not a fact re-declared per instruction: it is read off the carrier's algebra
     * ({@link #derivedInverse}) whenever that algebra states one, the stored field being the source only for a
     * carrier that states none. So {@code real}'s {@code mult}/{@code div} pair follows from the {@code field}'s
     * {@code mult_group} together with {@code div}'s own definition, while {@code int}'s {@code mult}→{@code div}
     * stays declared, int modeling a {@code mult_monoid} and no group — a truncating one-way retraction.
     */
    public final Entry lookup(final fURI instTid) {
        final Entry declared = this.row(instTid);
        if (null == declared) {
            // NO ROW: the instruction may DECLARE its law row itself (InstBuilder -> ?lawq). Declared at the inst,
            // and answered from the build-time REGISTRY -- the in-memory index over the store. The declared LAWS are
            // the instruction's; the INVERSE is still one the carrier's algebra may derive (see computed).
            final Entry fromDeclaration = declaredRow(instTid);
            return null == fromDeclaration ? null : this.computed(instTid, fromDeclaration);
        }
        // a derivation is reading the carrier's law block, which may itself consult this table: answer the
        // declaration while that read is in flight (never cache it), so the read cannot re-enter here
        if (DERIVING.get())
            return declared;
        return this.resolved.computeIfAbsent(instTid, tid -> this.computed(tid, declared));
    }

    /**
     * THE ROW OF AN ADDRESS — its own when the table declares one, else the family's UNIVERSAL row ({@code #} or a
     * generic dom) when it declares that: {@code order?dom=A{*}} answers for every carrier, which is what a fold
     * over everything must do. Without this the lift served no law for {@code order} at all -- the universal row
     * existed and was never consulted.
     */
    private Entry row(final fURI instTid) {
        if (null == instTid)
            return null;
        final Entry exact = this.table.get(instTid);
        if (null != exact)
            return exact;
        if (null == instTid.basePath())
            return null;
        for (final Map.Entry<fURI, Entry> e : this.table.entrySet())
            if (e.getKey().basePath().equals(instTid.basePath()) && isUniversal(e.getKey().dom()))
                return e.getValue();
        return null;
    }

    /**
     * THE ROW AN INSTRUCTION DECLARED BESIDE ITSELF, answered from the BUILD-TIME REGISTRY
     * ({@link InstBuilder#declaration}) — the in-memory index over what {@code InstBuilder.drain} persists to
     * {@code ?lawq}. A declaration is in hand the moment the instruction is created, so this read costs a hash
     * lookup: it never walks the registered instructions and never touches a space.
     * <p>
     * THE STORE IS THE FALLBACK, for the one answer memory cannot give: a declaration @lawq holds that the registry
     * does not. That is the cold-boot ordering where a law table is consulted before the set that declares the inst
     * is built, and any writer that is not {@link InstBuilder} (the store is the durable record, so it is also the
     * foreign writer's). The fallback is ONE read of the QUERIED address — never a walk of every instruction, which
     * is what made a lookup a per-evaluation cost.
     */
    private static Entry declaredRow(final fURI instTid) {
        final InstBuilder.Declaration registered = InstBuilder.declaration(instTid);
        if (null != registered)
            return entry(registered);
        return declaredEntry(lawOf(instTid));
    }

    /**
     * The registry's declaration as a law-table row: its process laws, its declared inverse and its defining
     * equation — the same claims {@link #declaredEntry(Obj)} reads back out of the store's rec, built from the
     * builder's staged declaration instead of from a space.
     */
    private static Entry entry(final InstBuilder.Declaration registered) {
        return new Entry(registered.laws().isEmpty() ? lst() : laws(registered.laws().toArray(Law[]::new)),
                registered.inverse(), registered.derivation());
    }

    /**
     * THE ROW AN INSTRUCTION DECLARED BESIDE ITSELF AS THE STORE HOLDS IT ({@code ?lawq}, written by
     * {@code InstBuilder.drain}): its process laws, its inverse and its defining equation, each read only when the
     * rec carries it. An undeclared facet is null (an absent laws list is the empty list, since a row with no laws
     * still declares its relations), and a rec declaring nothing is null rather than an empty claim — the table's
     * row is then the only source.
     */
    private static Entry declaredEntry(final Obj fromLawq) {
        if (!fromLawq.isRec())
            return null;
        final Rec declaration = fromLawq.asRec();
        // read as an ADDRESS, never as a value: at(...) auto-resolves and would follow the inverse edge into the
        // instruction it names instead of answering which instruction it is
        final fURI inverse = TheoryHelper.roleAddress(declaration, INVERSE);
        final Derivation derivation = declaredDerivation(declaration);
        final Obj lawLst = declaration.atDirect(uri(LAW));
        if (!lawLst.isLst() && null == inverse && null == derivation)
            return null;
        return new Entry(lawLst.isLst() ? lawLst.asLst() : lst(), inverse, derivation);
    }

    /**
     * The defining equation a declaration carries — the lhs and rhs stored as {@code code::T} — or null when the
     * rec holds none. A half-written derivation answers no derivation: it states no equation, and a rewrite run
     * off half of one would be a guess.
     */
    private static Derivation declaredDerivation(final Rec declaration) {
        final Obj derivation = declaration.atDirect(uri(DERIVATION));
        if (!derivation.isRec())
            return null;
        final Obj lhs = derivation.asRec().atDirect(uri(LHS));
        final Obj rhs = derivation.asRec().atDirect(uri(RHS));
        return lhs.isCode() && rhs.isCode() ? new Derivation(lhs.asCode(), rhs.asCode()) : null;
    }

    /**
     * The owner of a PROCESS row: the table of the instset that owns the row's CARRIER (the n-tid's dom), because
     * a row's key is an op address but its subject is a type — {@code plus?dom=/m/math/cmplx} belongs to the math
     * table although {@code plus} lives under {@code /m/inst}. The op's own longest-prefix owner can never find it,
     * which is why the math table's cmplx rows were unreachable. Falls back to the op's owner when the carrier
     * names no instset (a universal row's {@code #}/generic dom).
     */
    private static LawTable ownerOfRow(final fURI instTid) {
        if (null == instTid)
            return null;
        final LawTable carrier = null == instTid.dom() ? null : ownerOf(instTid.dom().basePath());
        return null != carrier ? carrier : ownerOf(instTid);
    }

    /**
     * The entry as the algebra answers it: the declared row, its inverse replaced when the carrier derives one.
     * The declared row is returned unchanged when the two agree, so today's declarations cost nothing.
     */
    private Entry computed(final fURI tid, final Entry declared) {
        DERIVING.set(Boolean.TRUE);
        try {
            final Type carrier = algebraOf(null == tid ? null : tid.dom());
            final fURI inverse = this.derivedInverse(tid, declared, null == carrier ? null : algebraFor(carrier, tid));
            return null == inverse || inverse.equals(declared.inverse()) ?
                    declared : new Entry(declared.laws(), inverse, declared.derivation());
        } finally {
            DERIVING.set(Boolean.FALSE);
        }
    }

    /**
     * THE INVERSE of an operation, read off THE OPERAND'S declared algebra ({@link #algebraFor} decides which type
     * that is) — a consequence, never a new fact.
     * <ul>
     *   <li>an operation declared {@link Law#involution} is its own inverse ({@code f·f = 1} is the law);</li>
     *   <li>a carrier declaring a <b>group</b> instance {@code (op, id, inv)} states the cancellation both ways:
     *       {@code op}'s inverse is the instruction DEFINED as {@code op} applied to its inverted argument —
     *       {@code div ≡ mult ∘ inv}, {@code minus ≡ plus ∘ neg} — and that instruction's inverse is {@code op};</li>
     *   <li>otherwise null: the carrier's algebra says nothing about an inverse, and the declaration is the
     *       source ({@code int}'s {@code mult}→{@code div}: truncating division is a retraction of multiplication,
     *       sound in that direction only, and no algebra states it).</li>
     * </ul>
     * Only a group is read: a {@code field} names {@code mul}/{@code inv} too, but {@code 0} has no {@code 1/0}, so a
     * field's multiplicative side is no group and licenses no cancellation of its own.
     * <p>
     * This is SOURCE 1 of the three {@link Entry} names — the algebra, and the only one that can answer for an
     * endomorphism. Source 2 (the graph's symmetric {@code coupling}, which proposes a partner but not a direction)
     * and source 3 (the declared field) are noted there.
     */
    private fURI derivedInverse(final fURI tid, final Entry entry, final Type algebra) {
        if (null == tid)
            return null;
        if (has(entry, Law.involution))
            return tid;
        // THE ALGEBRA IS THE OPERAND'S, NEVER THE CONTRACT'S: a type models exactly what it declares, so an
        // operand that declares none (or a rig where int declares a ring) derives no inverse here
        if (null == algebra || null == algebra.vid())
            return null;
        final fURI carrier = algebra.vid().basePath();
        if (null == carrier || carrier.equals(ALL.basePath()) || carrier.isGeneric())
            return null;
        for (final Obj block : TheoryHelper.instances(algebra).jvm().values()) {
            if (!block.isRec() || !GROUP_THEORY_TID.equals(block.asRec().tid().basePath()))
                continue;
            final fURI op = TheoryHelper.roleAddress(block.asRec(), OP);
            final fURI inv = TheoryHelper.roleAddress(block.asRec(), INV);
            if (null == op || null == inv)
                continue;
            if (op.basePath().equals(tid.basePath())) {
                // the group's own operation: its inverse is the instruction DEFINED as it, argument inverted
                final fURI form = this.inverseForm(tid, op, inv);
                if (null != form)
                    return form;
            } else if (defined(entry.derivation(), op, inv)) {
                // this instruction IS that definition: its inverse is the group's operation
                return this.sibling(tid, op);
            }
        }
        return null;
    }

    /**
     * Whether a derivation defines its instruction as {@code op} applied to its inverted argument — the defining
     * equation {@code div ≡ mult ∘ inv}. Read structurally, so the argument may be nested however the chain builder
     * wrote it; a derivation ending anywhere else says nothing about cancelling {@code op}.
     */
    private static boolean defined(final Derivation derivation, final fURI op, final fURI inv) {
        if (null == derivation)
            return false;
        final List<Inst> steps = derivation.rhs().insts();
        for (int i = 1; i < steps.size(); i++) {              // index 0 is the seed, never the definition
            final Inst step = steps.get(i);
            if (step.tid().basePath().equals(op.basePath()) && step.args().elements().anyMatch(arg -> holds(arg, inv.basePath())))
                return true;
        }
        return false;
    }

    /**
     * Whether an instruction holds the given op, in its arguments (nested code included).
     */
    private static boolean holds(final Obj arg, final fURI op) {
        if (arg.isCode())
            return arg.asCode().insts().stream().anyMatch(inst -> holds(inst, op));
        if (!arg.isInst())
            return false;
        return arg.asInst().tid().basePath().equals(op) || arg.asInst().args().elements().anyMatch(inner -> holds(inner, op));
    }

    /**
     * The op over the same carrier as {@code tid} — {@code mult} for {@code div}, {@code plus} for {@code minus} —
     * read as the table key that names it (its dom/rng are part of the address, so the carrier is the guard).
     */
    private fURI sibling(final fURI tid, final fURI op) {
        for (final fURI key : this.table.keySet())
            if (sameCarrier(key, tid) && key.basePath().equals(op.basePath()))
                return key;
        // the sibling may declare its laws at its own registration (?lawq) and have no row here: name its address
        // from the op with this row's endpoints, which is the address the declaration was stored under
        return null == tid.dom() || null == tid.rng() || null == op.basePath() ?
                null : op.basePath().dom(tid.dom()).rng(tid.rng());
    }

    /**
     * The instruction over {@code tid}'s carrier that is DEFINED as {@code op} applied to its inverted argument —
     * the instruction whose inverse is {@code op}. Null when nothing declares one.
     * <p>
     * BOTH PLACES A DEFINITION CAN LIVE ARE READ — the in-code rows and the equations instructions declare beside
     * themselves ({@link #declaredDerivations}). A sibling that migrated to {@code ?lawq} is still the SAME claim
     * about the same instruction, so a carrier that names the defining composition must find it there too: without
     * this, migrating {@code div}'s row would silently strip {@code mult}'s inverse ({@code mult}→{@code div} is a
     * consequence of {@code mult_group} plus {@code div ≡ mult ∘ inv}, and the equation is the half that migrated).
     */
    private fURI inverseForm(final fURI tid, final fURI op, final fURI inv) {
        for (final Map.Entry<fURI, Derivation> entry : declaredDerivations().entrySet())
            if (sameCarrier(entry.getKey(), tid) && defined(entry.getValue(), op, inv))
                return entry.getKey();
        return null;
    }

    /**
     * Whether two n-tids address an operation over the same carrier.
     */
    private static boolean sameCarrier(final fURI a, final fURI b) {
        return null != a && null != b && null != a.dom() && null != b.dom() && null != a.rng() && null != b.rng()
                && a.dom().basePath().equals(b.dom().basePath()) && a.rng().basePath().equals(b.rng().basePath());
    }

    /**
     * Whether an entry declares the given process law.
     */
    private static boolean has(final Entry entry, final Law law) {
        return entry.laws().lstValue().stream().anyMatch(l -> l.uriValue().name().equals(law.name()));
    }

    /**
     * An inst's declared entry together with the n-tid it was declared under — the address a rewrite
     * retypes an unresolved chain instruction with so the instruction's coefficient shape is legible.
     */
    public record Declared(fURI tid, Entry entry) {
    }

    /**
     * The owner table of an address — the table whose instset tid is the longest prefix. Null when none.
     */
    private static LawTable ownerOf(final fURI vid) {
        if (null == vid || null == vid.basePath())
            return null;
        final String base = vid.basePath().toString();
        LawTable owner = null;
        int best = -1;
        for (final Map.Entry<fURI, LawTable> e : TABLES.entrySet()) {
            final String prefix = e.getKey().toString();
            if ((base.equals(prefix) || base.startsWith(prefix + "/")) && prefix.length() > best) {
                owner = e.getValue();
                best = prefix.length();
            }
        }
        return owner;
    }

    /**
     * ALL LAW RESOLUTION IS STRICT: AN INSTRUCTION'S ROW FOLLOWS THE TYPE PATH, BUT A TYPE'S ALGEBRA IS ONLY WHAT
     * THAT TYPE DECLARES — SO A TYPE THAT DECLARES NO ALGEBRA TAKES PART IN NO ALGEBRAIC REWRITE.
     * <p>
     * THE ROW IS THE IMPLEMENTATION'S, AND IMPLEMENTATIONS INHERIT. Which contract runs is
     * {@link Typer.Helper#deduceTypeInst}'s answer, read off the operand's single path to the root
     * ({@code nat -> int -> #}): a {@code nat} operand gets {@code int}'s {@code plus} row — its process laws
     * ({@code commutative}, {@code monoidic}, {@code poset}, …) and its derivation. A type that implements the op
     * itself shadows its ancestor's, a sibling is never admitted, and nothing is ever guessed: no admitted contract
     * or no row for it answers null, so no law-driven rewrite can fire on the wrong type.
     * <p>
     * THE INVERSE IS THE ALGEBRA'S, AND ALGEBRAS DO NOT INHERIT. {@link #derivedInverse} reads the law block of the
     * type being operated on, never the contract's carrier — and that is the decision the whole table rests on, not
     * a convenience to relax. The policy is applied in one place, {@link #algebraFor}; its non-inheriting body is
     * active and the inheriting one is kept beside it, commented, so flipping the requirement is a comment change. {@code nat::T} exists precisely to SUBTRACT {@code int}'s {@code add_group}: same
     * {@code plus}, same representation, no additive inverse, so {@code x.plus(3).minus(3)} must not cancel on a nat
     * while it must on an int. A single {@code inv} role could not even express that subtraction, which is why
     * heredity here is not a lattice but a declaration.
     * <p>
     * THE UX CONSEQUENCE, PLAINLY: A TYPE DEFINITION IS AN ALGEBRAIC SPECIFICATION. To get cancellation, folding and
     * distribution a type must declare the theory instances it models ({@code add_group}, {@code mult_group},
     * {@code ring}, {@code field}, …) or delegate them to another type ({@code law => int} — a pointer, and still a
     * written act). A type that declares nothing is neither broken nor refused: it computes through its base type's
     * implementations, it simply licenses no law-driven rewrite — and the typer is where that becomes legible
     * ("age::T declares no algebra"). Throwaway types therefore pay nothing until they want the algebra, and a type's
     * algebra is exactly what is written on it, which is what lets this table shrink to declarations instead of
     * restating every operation's consequences.
     * <p>
     * The declared {@code inverse} is the one fact that travels with the ROW rather than the algebra: a retraction is
     * a fact about the implementation, not the structure — {@code int}'s {@code mult}→{@code div} is sound in one
     * direction because truncating division retracts multiplication, and it holds for anything running that division,
     * nat included. It answers only when the operand's algebra states no inverse of its own.
     */
    public static Declared declared(final fURI op, final Type operand) {
        final LawTable carrierOwner = null == operand || null == operand.vid() ? null : ownerOf(operand.vid());
        final LawTable owner = null != carrierOwner ? carrierOwner : ownerOf(op);
        if (null == owner || null == op.basePath())
            return null;
        final fURI base = op.basePath();
        if (null == operand || null == operand.vid())
            return owner.first(base);                  // no type to resolve against: the family's first row
        if (DERIVING.get())                            // a derivation is reading the very block we would read here
            return owner.exact(base, operand);         // (the legacy exact read, never cached, cannot re-enter)
        final Inst contract = deduce(base, operand);   // which implementation this type lands on
        if (null != contract) {
            // A ROW IS OWNED BY THE INTSET OF ITS CARRIER, not of the queried type: nat borrows int's plus, and the
            // row `plus?dom=/m/int` lives in int's table even though the operands are nats
            final LawTable rowOwner = null == contract.tid().dom() ? null : ownerOf(contract.tid().dom().basePath());
            final LawTable table = null != rowOwner ? rowOwner : owner;
            final Entry row = table.row(contract.tid());
            if (null != row)
                return new Declared(contract.tid(), table.algebraic(contract.tid(), row, operand));
            // NO ROW: the instruction declares its own law row beside it (?lawq, via InstBuilder), answered from the
            // registry. The LAWS are the instruction's, the INVERSE is still the operand's algebra -- so the pair is
            // derived, not declared.
            final Entry fromDeclaration = declaredRow(contract.tid());
            if (null != fromDeclaration)
                return new Declared(contract.tid(), table.algebraic(contract.tid(), fromDeclaration, operand));
        }
        return owner.exact(base, operand);             // the op is not in the registry: the exact row, unchanged
    }

    /**
     * The registered contract this type lands on, or null when the machine registers none (an unbooted machine
     * included) — see {@link Typer.Helper#deduceTypeInst}.
     */
    private static Inst deduce(final fURI base, final Type operand) {
        return Machine.loaded() ? Typer.Helper.deduceTypeInst(Machine.current().instset(), operand, base) : null;
    }

    /**
     * The row of the op over this exact carrier (or a universal {@code #}/generic row), as the table was written —
     * the read used where no contract can be deduced, and while a derivation is in flight.
     */
    private Declared exact(final fURI base, final Type operand) {
        final String type = operand.vid().basePath().toString();
        Declared universal = null;
        for (final Map.Entry<fURI, Entry> e : this.table.entrySet()) {
            if (!e.getKey().basePath().toString().equals(base.toString()))
                continue;
            final Entry entry = this.lookup(e.getKey());
            if (null == entry)
                continue;
            if (null == universal && isUniversal(e.getKey().dom()))
                universal = new Declared(e.getKey(), entry);
            if (e.getKey().dom().basePath().toString().equals(type) || e.getKey().rng().basePath().toString().equals(type))
                return new Declared(e.getKey(), entry);
        }
        return universal;
    }

    /**
     * The first row of a family — the answer when there is no operand type to resolve against.
     */
    private Declared first(final fURI base) {
        for (final Map.Entry<fURI, Entry> e : this.table.entrySet())
            if (e.getKey().basePath().toString().equals(base.toString())) {
                final Entry entry = this.lookup(e.getKey());
                if (null != entry)
                    return new Declared(e.getKey(), entry);
            }
        return null;
    }

    /**
     * The row as the OPERAND's algebra answers it: its inverse replaced when that algebra derives one, the declared
     * retraction kept when it does not. Strictly the operand's — see {@link #declared(fURI, Type)}.
     */
    private Entry algebraic(final fURI tid, final Entry declared, final Type operand) {
        DERIVING.set(Boolean.TRUE);
        try {
            final fURI inverse = this.derivedInverse(tid, declared, algebraFor(operand, tid));
            return null == inverse || inverse.equals(declared.inverse()) ?
                    declared : new Entry(declared.laws(), inverse, declared.derivation());
        } finally {
            DERIVING.set(Boolean.FALSE);
        }
    }

    /**
     * The type whose law block an inverse is read from — the plain (coefficient-free, non-generic) carrier named by
     * an n-tid, or null when there is none to read.
     */
    private static Type algebraOf(final fURI carrier) {
        return null == carrier || null == carrier.basePath() || carrier.basePath().equals(ALL.basePath())
                || carrier.basePath().isGeneric() ? null : T(carrier.basePath());
    }

    /**
     * WHICH TYPE'S ALGEBRA ANSWERS FOR AN OPERAND — THE ONE POLICY SWITCH. The policy is the single call on the last
     * line of the body: {@link #strict} (active), {@link #inherit}, or {@link #byContract}. All three are real,
     * compiled methods rather than commented-out bodies on purpose — a comment block holding a method is one
     * unbalanced fence away from a broken build (flipping it by hand did break this file), while a compiled
     * alternative is type-checked on every build no matter which one the switch selects.
     * <p>
     * WHAT THE CHOICE MOVES, measured 2026-10-27 with the suite under both live bodies: the observable answers of
     * {@code strict} and {@code inherit} are identical on today's table (162 tests green either way), because a
     * type that declares a block ({@code nat}'s {@code rig}) is answered by its own block in both policies, and a
     * type that declares none ({@code imperial}) is stopped earlier, at the ROW — its {@code plus?imperial} contract
     * has no row in any law table, so {@link #declared(fURI, Type)} answers null before an algebra is ever read. The
     * policy becomes load-bearing when a type has rows but no block: once delegation ({@code law => int}) lands, or
     * the row read learns to fall back to an ancestor's row. {@link #byContract} is the loosest body and the only one
     * that differs today: it hands {@code nat} int's ring, which {@code catInstSetTest#testStrictLawResolution} flags.
     *
     * @param operand  the type being operated on
     * @param contract the n-tid of the row being answered, whose dom is the implementation's carrier
     * @return the type whose law block answers for it
     */
    private static Type algebraFor(final Type operand, final fURI contract) {
        return strict(operand);
    }

    /**
     * STRICT — the operand's own declaration, and nothing else. A type models exactly what it declares, so
     * {@code nat}'s {@code rig} <em>subtracts</em> {@code int}'s {@code add_group}, and a type that declares no
     * algebra licenses no rewrite. This is the policy {@link #declared(fURI, Type)} documents.
     */
    private static Type strict(final Type operand) {
        return operand;
    }

    /**
     * INHERIT — the non-strict policy: the nearest ancestor that DECLARES a block answers, so a type that says
     * nothing borrows its base's algebra ({@code age} would get int's ring, a bare refinement of real would get
     * real's field). Convenient for throwaway types, and unsound wherever a refinement <em>narrows</em> the
     * structure: {@code nat} must subtract {@code int}'s {@code add_group}, and no walk can express a subtraction.
     * A type that does declare (nat's rig) is unaffected — the walk stops at the operand itself.
     * <p>
     * The walk has the shape of {@link Type#pathIncludes(Type)}: a base type ends it, the root is never asked, and
     * when no ancestor declares anything the operand answers (which declares nothing either, so the policies agree).
     */
    private static Type inherit(final Type operand) {
        if (null == operand || null == operand.vid())
            return operand;
        Type type = operand;
        int hops = 0;
        while (null != type && hops++ < 256) {
            if (!typeLawsOf(type).jvm().isEmpty())
                return type;
            if (type.isBaseType() || type.isRootType())
                break;
            type = type.parentType();
        }
        return operand;
    }

    /**
     * PRE-SWITCH (non-strict) — exactly the read this table made before the strict switch: the CONTRACT's own
     * carrier answers, so {@code plus?dom=/m/int} is read out of int's algebra whatever the operand is and a nat
     * operand inherits int's ring. Kept for comparison; it is the one body that changes today's answers.
     */
    private static Type byContract(final fURI contract) {
        return null == contract ? null : algebraOf(contract.dom());
    }

    /**
     * Whether a declared dom ranges over every carrier: the universal type {@code #} or a generic variable.
     */
    private static boolean isUniversal(final fURI dom) {
        return null != dom && null != dom.basePath() && (dom.basePath().equals(ALL.basePath()) || dom.basePath().isGeneric());
    }

    /**
     * A chain instruction keyed with its declared n-tid for the carrier it folds — the form {@link #combiner},
     * {@code Form.of} and every declared-law lookup read before resolution. A fold whose argument holds folds,
     * {@code group([k⇒h])}, has those keyed too, since its combiner is computed from theirs. The instruction itself
     * when nothing declares it: an undeclared op matches no law-bearing slot, fail closed.
     */
    public static Inst keyed(final Inst inst, final Type carrier) {
        final Declared declared = declared(inst.tid().basePath(), carrier);
        if (null == declared)
            return inst;
        final Inst typed = inst.tid(declared.tid());
        if (!GROUP_INST_TID.equals(typed.tid().basePath()) || 1 != typed.args().count() || !typed.arg(0).isRec())
            return typed;
        final Map<Obj, Obj> spec = new LinkedHashMap<>();
        typed.arg(0).asRec().elements().forEach(kv -> spec.put(kv.jvm().get0(),
                kv.secondDirect().isInst() ? keyed(kv.secondDirect().asInst(), carrier) : kv.secondDirect()));
        return typed.args(lst(rec(spec)));
    }

    /**
     * THE COMBINER of a fold, computed and never declared: the fold its partials recombine through, so that
     * {@code (a ⊎ b)·r = (a·r ⊎ b·r)·combiner(r)} — Thm 19 with the re-application read off the theory. A stream is a
     * bag, so this is the universal property of the free commutative monoid: a fold that distributes is
     * {@code map(g)} followed by the fold of a commutative monoid, and its partials recombine through that fold.
     * <ul>
     *   <li>a fold declaring the laws itself is its own combiner — {@code sum}, {@code prod} — and the answer is
     *       {@code keyed} itself;</li>
     *   <li>a fold DEFINED as a pointwise map into one — its declared {@link Derivation}, {@code r ↦ map(g)·f} —
     *       recombines through {@code f}, read off {@code r}'s target object (the carrier {@code f} folds), with the
     *       generics bound from {@code keyed}'s own arguments — {@code count ↦ map(1)·sum},
     *       {@code order(B) ↦ split([_])·kmerge(B)};</li>
     *   <li>a fold whose argument holds folds recombines POINTWISE through theirs — {@code group([k⇒h])}, see
     *       {@link #pointwise} — and its combiner is a chain rather than one instruction.</li>
     * </ul>
     * {@code keyed} carries its declared n-tid ({@link #keyed}). Null when the fold has none: {@code mean}
     * (magmadic), {@code str} {@code sum} (not commutative), an undeclared or non-reducing instruction.
     */
    public static Call combiner(final Inst keyed, final Law... laws) {
        final Entry entry = lawsOf(keyed.tid());
        if (null == entry || Inst.Form.reducer != Inst.Form.of(keyed))
            return null;
        if (declaresAll(entry, laws))
            return keyed;
        if (GROUP_INST_TID.equals(keyed.tid().basePath()))
            return pointwise(keyed, laws);
        if (null == entry.derivation())
            return null;
        final Inst fold = entry.derivation().fold(keyed);
        if (null == fold)
            return null;
        // the definition's last step must itself be a FOLD of the target, declaring the laws -- a mapper that
        // happens to be monoidic and commutative (bool plus) recombines nothing
        final Declared declared = declared(fold.tid().basePath(), keyed.rng());
        return null != declared && Inst.Form.reducer == Inst.Form.of(fold.tid(declared.tid())) && declaresAll(declared.entry(), laws) ? fold : null;
    }

    /**
     * THE POINTWISE COMBINER. {@code group([k⇒h])} is a homomorphism from the bag into the recs of {@code h}'s monoid,
     * keyed by {@code k}: the partials merge by key, and each key's partials fold through {@code h}'s own combiner. So
     * it is computed from that combiner, never named — {@code sum()==[_=>combiner(h)]}: the rec {@code sum} (the deep
     * merge, which appends colliding partials into a bag — a lst kept whole, so an {@code order}'s sorted runs reach
     * {@code kmerge} as runs — and reads a bulked partial as its copies), then a per-key fold. Null, so nothing shards,
     * when a value is not a fold with a combiner ({@code mean}, a constant), or when the spec's entries fold differently
     * (a key keeps no record of the entry that claimed it, and the claim falls differently in every part, so even folds
     * with one combiner would mix).
     */
    private static Call pointwise(final Inst keyed, final Law... laws) {
        if (1 != keyed.args().count() || !keyed.arg(0).isRec())
            return null;
        final List<Obj> folds = keyed.arg(0).asRec().elements().map(Rel::secondDirect).toList();
        final Obj fold = folds.isEmpty() ? null : folds.getFirst();
        if (null == fold || !fold.isInst() || !folds.stream().allMatch(f -> f.isInst() && catLiveness.same(f.asInst(), fold.asInst())))
            return null;
        final Obj perKey = perKey(fold, laws);
        return null == perKey ? null : sum_().select_(rec(id_().tryToInst(), perKey));
    }

    /**
     * The per-key fold of one spec value: its combiner, a self-combining fold emitted as the chain wrote it (its op
     * address, resolved where it runs). Null when the value is no fold with a combiner.
     */
    private static Obj perKey(final Obj value, final Law... laws) {
        if (!value.isInst())
            return null;
        final Call fold = combiner(value.asInst(), laws);
        if (null == fold)
            return null;
        return fold == value ? value.asInst().tid(value.asInst().tid().basePath()) : fold;
    }

    /**
     * The combiner under Thm 19's hypothesis — a partial recombines through a commutative monoid's fold.
     */
    public static Call combiner(final Inst keyed) {
        return combiner(keyed, Law.monoidic, Law.commutative);
    }

    /**
     * The combiner as the category names it — the declared n-tid of the edge the partials recombine through, an
     * address as {@code analysis.inverse} is one — or null when the fold has no combiner, or recombines through a
     * chain ({@link #pointwise}) that no single edge names.
     */
    public static fURI combinerOf(final Inst keyed) {
        final Call fold = combiner(keyed);
        if (null == fold || !fold.isInst())
            return null;
        if (fold == keyed)
            return keyed.tid();
        final Declared declared = declared(fold.asInst().tid().basePath(), keyed.rng());
        return null == declared ? null : declared.tid();
    }

    private static boolean declaresAll(final Entry entry, final Law... laws) {
        return Arrays.stream(laws).allMatch(law -> entry.laws().lstValue().stream().anyMatch(l -> l.uriValue().name().equals(law.name())));
    }

    /**
     * The entry of an inst, resolved from the law table of the instset that owns it — the sibling of
     * {@link #typeLawsOf(Type)}, and the oracle a rewrite consults to learn an instruction's declared
     * process laws before firing. Longest-prefix wins, so an inst address under {@code /m/math} routes to
     * the math table rather than the {@code /m} table. Null when no table declares the inst.
     */
    public static Entry lawsOf(final fURI instTid) {
        if (null == instTid)
            return null;
        final LawTable owner = ownerOfRow(instTid);
        return null == owner ? null : owner.lookup(instTid);
    }

    /**
     * Whether the inst declares the given process law — the guard a law-driven rewrite tests before firing.
     */
    public static boolean declares(final fURI instTid, final Law law) {
        final Entry entry = lawsOf(instTid);
        return null != entry && entry.laws().lstValue().stream()
                .anyMatch(l -> l.uriValue().name().equals(law.name()));
    }

    /**
     * The structural theories the type models, keyed by each theory instance's name.
     */
    public final Rec typeLaws(final Type type) {
        // keyed by the VID: the block is about the type, and two refinements of one base (nat, or a pair of
        // int-refinements) share a tid, so a tid-keyed cache would answer one's algebra for the other
        if (null == type.vid())
            return rec0();
        final Rec cached = this.typeLawsCache.get(type.vid().basePath());
        if (null != cached)
            return cached;
        // THE DECLARATION BESIDE THE TYPE WINS: an algebra written through `?lawq` (the builder's job, at
        // registration, before any lift) is the source; the in-code table is the fallback until every type
        // declares its own. WHETHER THE SPACE ANSWERED is observed here, on the read that produced the block --
        // never re-derived at the memo, where a second copy of `lawOf`'s own gate would drift from it.
        final Obj declared = lawOf(type);
        final boolean spaceAnswered = declared.isRec();
        final Rec laws = spaceAnswered && !declared.asRec().isEmpty() ? declared.asRec() : this.typeLawsUncached(type);
        if (memoizable(laws, spaceAnswered))
            this.typeLawsCache.putIfAbsent(type.vid().basePath(), laws);
        return laws;
    }

    /**
     * WHETHER AN ANSWER MAY BE MEMOIZED — a pure function of what the read OBSERVED. A non-empty block was read
     * out of a live space (and holds addresses a later boot names identically), so it is always safe to keep. An
     * EMPTY one is kept only when the type's own space answered the read with a {@code ?lawq} rec: then "declares
     * nothing" is a declaration. A BLIND read — the space was not reachable, or held no declaration yet — is a
     * fact about the machine asking, and these tables are static singletons ({@link #TABLES}) that outlive every
     * boot, so memoizing it would answer "this type declares no algebra" for the rest of the JVM's life: the
     * theory machinery ({@code TheoryHelper.instances}/{@code unit}, {@code LawTable.declared}) then finds no unit
     * and no inverse, so {@code plus(0)} never folds and {@code plus(3).minus(3)} never cancels — the two rows of
     * {@code PlanBuilderTest.testEmittedCodeIsUnchangedByRename} a peer suite left red.
     */
    static boolean memoizable(final Rec laws, final boolean spaceAnswered) {
        return !laws.jvm().isEmpty() || spaceAnswered;
    }

    /**
     * The structural theories of the type, resolved from the law table of the instset that owns the type — the
     * dispatcher that lets several sibling law tables share the category without one knowing the others.
     */
    public static Rec typeLawsOf(final Type type) {
        if (null == type.vid())
            return rec0();                      // a type with no address is listed in no table
        final String vid = type.vid().basePath().toString();
        LawTable owner = null;
        int best = -1;
        for (final Map.Entry<fURI, LawTable> e : TABLES.entrySet()) {
            final String prefix = e.getKey().toString();
            if ((vid.equals(prefix) || vid.startsWith(prefix + "/")) && prefix.length() > best) {
                owner = e.getValue();
                best = prefix.length();
            }
        }
        return null == owner ? rec0() : owner.typeLaws(type);
    }

    /**
     * ALL DERIVATIONS A REWRITE CAN APPLY — the values of {@link #declaredDerivations}, in declaration order (the
     * in-code rows first, then the equations instructions declare beside themselves).
     */
    public static List<Derivation> derivations() {
        return new ArrayList<>(declaredDerivations().values());
    }

    /**
     * EVERY defining equation, keyed by the ADDRESS it is declared at — the in-code rows of every registered table
     * united with the equations instructions declare beside themselves ({@code ?lawq}, written by {@code InstBuilder}),
     * because a definition is the instruction's claim and a row is only where it has lived so far. Without the second
     * half a migrated row would leave the language: {@code derivation_contraction} reads {@link #derivations}, and
     * {@link #inverseForm} reads it to find the sibling a carrier's group names — so a rule no row restates would
     * silently stop contracting, and an inverse no row restates would stop being served.
     * <p>
     * A definition stated BOTH ways is ONE rule, and the migration window is exactly that state. The identity is the
     * ADDRESS the definition is declared at — the row's key, or the instruction's n-tid — since that is the same
     * address in both forms; text would be a proxy, and the derived op's base path alone would collapse
     * {@code minus?int} and {@code minus?real} into one rule, which the rows keep distinct because they are
     * distinct equations over distinct carriers.
     * <p>
     * THE SECOND HALF IS READ FROM THE REGISTRY, NOT FROM THE STORE ({@link InstBuilder#declarations}): a rewrite
     * pass asks this question per evaluation, and answering it by walking every registered instruction with a space
     * read beside each made the pass cost milliseconds. The registry is the query index; the equations it holds are
     * the same claims {@code drain} persists. The STORE is still consulted — {@link #storeDerivations()} — for the
     * declarations memory cannot answer for (a foreign writer, or a table asked before the declaring set was built),
     * and that snapshot is taken lazily and reused rather than rebuilt per call.
     */
    private static Map<fURI, Derivation> declaredDerivations() {
        final Map<fURI, Derivation> out = new LinkedHashMap<>();
        for (final LawTable table : TABLES.values())
            for (final Map.Entry<fURI, Entry> row : table.table.entrySet())
                if (null != row.getValue().derivation())
                    out.putIfAbsent(row.getKey(), row.getValue().derivation());
        // DECLARED BESIDE THE INSTRUCTION, read from the registry: the address is the instruction's n-tid, which is
        // the row key the declaration replaces, so a rule written in both places lands on one entry
        InstBuilder.declarations().forEach((tid, declared) -> {
            if (null != declared.derivation())
                out.putIfAbsent(tid, declared.derivation());
        });
        // THE STORE'S RESIDUE: the declarations the registry does not hold. One snapshot, retaken only when the
        // registry grows or when it was taken before the machine was up, so a settled machine pays nothing.
        if (!STORE_DERIVATIONS_WAS_LOADED || STORE_DERIVATIONS_AT != InstBuilder.declarations().size())
            storeDerivations();
        return union(out, STORE_DERIVATIONS);
    }

    /**
     * The STORE's defining equations — {@code ?lawq}'s residue over the registry: every registered instruction the
     * registry declares nothing for gets ONE space read, and the answer is kept. Retaken only when one of its two
     * staleness facts changes ({@link #STORE_DERIVATIONS_AT}, {@link #STORE_DERIVATIONS_WAS_LOADED}): never a walk
     * per rewrite pass, and never a walk at all on a machine whose boot has settled.
     * <p>
     * The walk is why this is a snapshot and not a read: {@code instset().insts()} materializes every registered
     * instruction and each {@code lawOf} is a space lookup — 283 of each per call is the regression this file fixes.
     */
    private static void storeDerivations() {
        final boolean loaded = Machine.loaded();
        final InstSet instset = loaded ? Machine.current().instset() : null;
        final Map<fURI, Derivation> snapshot = new LinkedHashMap<>();
        if (null != instset) {
            for (final Inst inst : instset.insts()) {
                if (null == inst.tid() || null != InstBuilder.declaration(inst.tid()))
                    continue;                       // the registry already answers for this instruction
                final Obj fromLawq = lawOf(inst.tid());
                final Derivation declared = fromLawq.isRec() ? declaredDerivation(fromLawq.asRec()) : null;
                if (null != declared)
                    snapshot.put(inst.tid(), declared);
            }
        }
        STORE_DERIVATIONS = snapshot;
        STORE_DERIVATIONS_AT = InstBuilder.declarations().size();
        STORE_DERIVATIONS_WAS_LOADED = loaded;
    }

    /**
     * The union of the in-code and declared rules with the store's residue, the first two in declaration order.
     */
    private static Map<fURI, Derivation> union(final Map<fURI, Derivation> declared,
                                               final Map<fURI, Derivation> stored) {
        if (stored.isEmpty())
            return declared;
        final Map<fURI, Derivation> out = new LinkedHashMap<>(declared);
        stored.forEach(out::putIfAbsent);
        return out;
    }

    /**
     * The uncached object-law rec for the type — the theory instances the type models.
     */
    protected abstract Rec typeLawsUncached(final Type type);

    /**
     * The authoring helper: a lst of law-label uris for a declaration — {@code laws(Law.commutative, Law.right_distributive)}.
     */
    protected static Lst laws(final Law... laws) {
        return lst(Arrays.stream(laws).map(l -> (Obj) uri(l.name())).toList());
    }
}
