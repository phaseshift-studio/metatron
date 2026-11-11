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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.algebra.TheoryHelper;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.AlgebraTool;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mTypes;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DECQ;
import static studio.phaseshift.metatron.isa.m.mTypes.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE LAW CENSUS — four facts about the algebra declarations, machine-checked, because each has silently broken
 * this week: a row of the retired LawTable whose address stopped being an instruction ({@code order?int}), an
 * algebra whose role pointers name instructions that are no longer registered (code's
 * {@code neg}/{@code end}/{@code id}), identities that stopped naming anything (bool's {@code zero}/{@code one}), and
 * a claim that was never about its instruction at all (a law whose subject SHAPE the instruction's contract does not
 * have). None of them produced an error: a row nothing consults, a role that resolves to {@code noobj}, and a
 * category error are all silent, so the failure mode is a law that quietly stops holding. This class is the census
 * that turns each into a checked fact.
 *
 * <p>THE FOUR FACTS
 * <ol>
 *   <li><b>every row of the retired LawTable was reachable</b> — a row's address was a registered instruction
 *       ({@code live}), an operation the lift still admitted over that carrier ({@code view}), or an
 *       {@code orphan}. The table is gone, and no test in this class checks this fact today;</li>
 *   <li><b>every declared algebra validates</b> — a carrier's declared theory instances are turned into the law
 *       claims their theories entail ({@code mTypes.axiomsOf}), and
 *       {@link AlgebraTool#analyzeType(Type, List)} must accept every claim for every operation the carrier binds.
 *       A carrier that declares no instance at all is REPORTED, never failed ({@code rel} declares none);</li>
 *   <li><b>every role points at something real</b> — each role value that is an address or a pointer must name a
 *       registered instruction, or one a registration of the same operation covers over a carrier on the role's own
 *       path, or one the lift admits. This is the assertion that catches code's {@code neg}/{@code end}/{@code id},
 *       bool's {@code zero}/{@code one} and an {@code order?int} that lost its registration;</li>
 *   <li><b>every declared law matches its instruction's contract</b> — an instruction that declares a process law
 *       in {@code ?decq} must be the KIND of thing the law is about:
 *       {@link AlgebraTool#matches(Law, Inst)} compares {@link Law#wrt()} with the instruction's own dom/rng and,
 *       only when the signature carries {@code args}, its arity. A law about a binary relation declared on an endo
 *       op is a category error, and the census names it as one — it never silently stops applying.</li>
 * </ol>
 *
 * <p>NOTHING IS FIXED HERE, AND NOTHING IS WEAKENED: a census that fails must be answered by restoring the lost
 * registration or by writing the exception down in {@link #ALLOWLIST}, never by relaxing the check.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LawCensusTest extends AbstractMetatronTest {

    /**
     * KNOWN EXCEPTIONS — EACH ONE NAMES AN ADDRESS AND THE ISSUE THAT JUSTIFIES TOLERATING IT, written as
     * {@code "<address> — <issue>"}. An entry is a public apology, not a switch: it stays visible in this source
     * file, so every reader sees why the algebra, the declared law or the role is exempt, and removing an entry is the
     * day the underlying issue got fixed. The first two entries are ANALYZER limits rather than lost registrations,
     * and neither may be "fixed" by renaming a declaration —
     * naming the registered addresses for code's additive group instead breaks 48 cases of
     * {@code catInstSetTest} (measured), because those addresses are deliberate declaration-level aliases
     * ({@code branch} has only a generic registration, {@code end}'s rng is the zero coefficient {@code {0}}).
     */
    private static final Set<String> ALLOWLIST = Set.of(
            // the code ring's and additive group's operations cannot be run by the analyzer: the probe it chooses
            // for a code is the identity INSTRUCTION, and every equation template then applies that instruction
            // ("unable to determine inst function"), so the claims fail on evaluation rather than on the algebra
            "/m/code — the analyzer probes a code with an instruction and then applies it, so none of the code "
                    + "ring/group equations can be evaluated; the declared algebra is not disproved, only untestable",
            // the machine declares a groupoid of address morphisms: its operation is uri's mult, and no operation
            // whose dom is uri lies on the machine type's own path, so analyzeType binds no role-bearing operation
            "/m/mach/machine — its free_groupoid binds mult over /m/uri, which the machine type's path never "
                    + "includes, so analyzeType binds no operation and every claim comes back unchecked",
            // mean is a REDUCTION, not an endo or a binary operation: it maps a stream of ints to a real, so no
            // signature in the six names its map, and its commutativity/magmadic claims are about the reduction
            // (order-independence of the fold) rather than about an operation of the carrier. Removing this entry
            // needs a shape for a carrier-changing fold, not a looser match.
            // the addresses render rng before dom — the canonical query order (inst?rng<=dom(args)), pinned by
            // catInstSetTest's inverse rows — so the entry must begin with that exact text
            "/m/inst/mean?rng=/m/real&dom=/m/int{*} — mean reduces a stream of ints to a real, so neither the "
                    + "arity-agnostic endo shape (rng != dom) nor the binary shape (zero arguments) is its contract; "
                    + "the commutative/magmadic claims are about the reduction, and no signature names that yet",
            // mean over reals is at least an endo, so only its magmadic claim (spelled binary) has no subject
            "/m/inst/mean?rng=/m/real&dom=/m/real{*} — mean is a zero-argument reduction, so the binary magmadic "
                    + "shape does not name it (its commutative claim matches the arity-agnostic endo shape)");

    /**
     * The catch-all import mounts every set, so every declaration the census reads is answerable. {@code /m/math} is
     * re-imported afterwards for the same reason {@code catInstSetTest} does it: the catch-all re-adds the base
     * {@code /m} set and re-registers its types against a fresh space.
     */
    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
        InstSet.importInstSet(f("/m/math"));
    }

    // each test's own machine is named apart: a class's methods run in parallel in one fork
    private static final AtomicLong MACHINES = new AtomicLong();

    /**
     * EVERY TEST RUNS IN A MACHINE OF ITS OWN (Marko, 2026-11-11: push, run, pop). The census drives
     * {@link AlgebraTool}, which writes its probes to {@code a} and {@code b} in the current machine, and a fork's
     * classes share its root. The imports above stay the root's: a machine pushed per test reads them through its
     * parent.
     */
    @BeforeEach
    protected void pushOwnMachine() {
        Machine.current().push(f("law_census_" + MACHINES.incrementAndGet()));
    }

    @AfterEach
    protected void popOwnMachine() {
        Machine.current().pop();
    }

    /**
     * ASSERTION 2 — EVERY DECLARED ALGEBRA VALIDATES. For each carrier that declares theory instances, the union
     * of the law batteries those theories entail is claimed of the carrier and
     * {@link AlgebraTool#analyzeType(Type, List)} must accept it. A carrier that declares nothing is reported and
     * skipped — it makes no claim to validate — and today {@code rel} is the one expected to be in that list.
     * <p>
     * A carrier whose algebra the analyzer cannot run at all is exempted through {@link #ALLOWLIST}, never through a
     * silent skip: the entry names the carrier and the issue, and the run logs every carrier it exempted.
     */
    @Test
    public void testEveryDeclaredAlgebraValidates() {
        final List<String> failures = new ArrayList<>();
        final List<String> empty = new ArrayList<>();
        final List<String> exempt = new ArrayList<>();
        final List<String> unbattered = new ArrayList<>();
        for (final Type carrier : carriers()) {
            final Rec algebra = TheoryHelper.instances(carrier);
            if (algebra.jvm().isEmpty()) {
                empty.add(carrier.namedType());
                continue;
            }
            final List<Law> claims = new ArrayList<>();
            for (final Obj instance : algebra.jvm().values()) {
                if (!instance.isRec() || null == instance.asRec().tid())
                    continue;
                final List<Law> battery = mTypes.axiomsOf(instance.asRec().tid().basePath());
                if (battery.isEmpty())
                    unbattered.add(carrier.namedType() + "." + instance.asRec().tid().basePath());
                for (final Law law : battery)
                    if (!claims.contains(law))
                        claims.add(law);
            }
            final fURI carrierVID = carrier.vid();
            if (null != carrierVID && null != carrierVID.basePath() && allowed(carrierVID.basePath().toString())) {
                exempt.add(carrier.namedType());
                continue;
            }
            final AlgebraTool.Report report = AlgebraTool.analyzeType(carrier, claims);
            if (!report.ok())
                failures.add(carrier.namedType() + " declares " + algebra + " and so claims " + claims
                        + ", but the declared algebra does not validate: " + report.toRec());
        }
        LOG.warn("algebra census: carriers with no declared instances (reported, not failed): %s", empty);
        LOG.warn("algebra census: carriers exempted by the ALLOWLIST (issue named there): %s", exempt);
        LOG.warn("algebra census: instances whose theory declares no law battery (reported, not failed): %s", unbattered);
        assertTrue(failures.isEmpty(),
                () -> "carriers whose declared algebra does not validate:\n  " + String.join("\n  ", failures));
    }

    /**
     * ASSERTION 3 — EVERY ROLE POINTS AT SOMETHING REAL. A role holding an address or a pointer names an
     * instruction, and that instruction must be registered (its tid is the address) or liftable
     * ({@code deduceTypeInst} admits it over the address's own carrier — a role names the operation's contract, so
     * the address's dom is the carrier the lift is asked over, not the enclosing algebra's), or covered by a
     * registration of the same operation over a carrier on that address's path ({@link #covered} — the family read a
     * role pointer takes, which is why code's {@code end} role is answered by {@code end?rng=/m/code{0}&dom=/m/code}
     * and its {@code branch} role by the generic {@code branch?dom=A}). A role that binds a
     * literal element ({@code zero => 0}, {@code id => ""}) or a type address ({@code objects => /m/mach/machine})
     * is not an instruction pointer and is skipped. This is the assertion that catches code's
     * {@code neg}/{@code end}/{@code id} and bool's {@code zero}/{@code one}: when a registration is lost the role
     * keeps the address and silently resolves to {@code noobj}.
     */
    @Test
    public void testEveryRolePointsAtSomethingReal() {
        final List<Inst> insts = List.copyOf(Machine.current().instset().insts());
        final List<String> dangling = new ArrayList<>();
        int checked = 0;
        for (final Type carrier : carriers()) {
            final Rec algebra = TheoryHelper.instances(carrier);
            for (final Map.Entry<Obj, Obj> instance : algebra.jvm().entrySet()) {
                if (!instance.getKey().isUri() || !instance.getValue().isRec())
                    continue;
                final String name = carrier.namedType() + "." + instance.getKey().uriValue().name();
                final Rec block = instance.getValue().asRec();
                for (final Map.Entry<Obj, Obj> role : block.jvm().entrySet()) {
                    final Optional<fURI> pointer = Obj.Helper.getPointer(role.getValue());
                    if (pointer.isEmpty())
                        continue;                              // a literal or a type address, not an instruction
                    checked++;
                    final fURI address = pointer.get();
                    if (live(insts, address) || covered(insts, address) || admits(address) || allowed(address.toString()))
                        continue;
                    dangling.add(name + "." + (role.getKey().isUri() ? role.getKey().uriValue().name() : role.getKey())
                            + " => " + address + " (no instruction is registered for that operation over that carrier, and the lift admits none)");
                }
            }
        }
        assertTrue(0 < checked,
                "the census found no role address at all: the declared algebras were read without their pointers, "
                        + "so the assertion would be vacuous");
        assertTrue(dangling.isEmpty(), () -> """
                algebra roles that point at nothing:
                  %s""".formatted(String.join("\n  ", dangling)));
    }

    /**
     * ASSERTION 4 — EVERY DECLARED LAW IS ABOUT ITS INSTRUCTION'S CONTRACT. An instruction's declared process laws
     * (its {@code ?decq} claim) are claims about the KIND of operation it is, so every one of
     * them must pass the same signature match the analyzer applies before it renders an equation:
     * {@link AlgebraTool#matches(Law, Inst)} compares {@link Law#wrt()} with the instruction's {@code dom}/{@code rng}
     * — one poly variable {@code A}, so every occurrence must land on the same carrier — and its arity, but ONLY
     * when the signature declares an {@code args} component (no {@code args} is the arity-agnostic spelling and
     * matches any arity).
     * <p>
     * THE FAILURE IS THE POINT: a claim that does not match is a CATEGORY ERROR, not a counterexample, so the message
     * prints every offending pair as {@code law @ inst_address — the law is about <shape>}. That is what makes a law
     * quietly written on the wrong kind of operation a checked fact instead of a claim nothing ever consults. Known
     * exceptions live in {@link #ALLOWLIST} with the issue that justifies them, never in a looser match.
     */
    @Test
    public void testEveryDeclaredLawMatchesItsInstruction() {
        final List<Inst> insts = List.copyOf(Machine.current().instset().insts());
        final List<String> mismatched = new ArrayList<>();
        final List<String> exempt = new ArrayList<>();
        int checked = 0;
        for (final Inst inst : insts) {
            if (null == inst || null == inst.tid())
                continue;
            final String address = inst.tid().toString();
            for (final Law law : declaredLaws(inst)) {
                checked++;
                if (AlgebraTool.matches(law, inst))
                    continue;
                final String row = law.name() + " @ " + inst.tid() + " — the law is about " + law.wrt();
                if (allowed(address))
                    exempt.add(row);
                else
                    mismatched.add(row);
            }
        }
        LOG.warn("law census: %d declared law(s) checked, %d exempted by the ALLOWLIST", checked, exempt.size());
        LOG.warn("law census: declared laws exempted (issue named in the ALLOWLIST): %s", exempt);
        assertTrue(0 < checked,
                "the census found no declared law at all: ?decq and the law tables answered nothing, "
                        + "so the assertion would be vacuous");
        assertTrue(mismatched.isEmpty(), () -> """
                instructions whose declared law is about a different shape than their contract:
                  %s""".formatted(String.join("\n  ", mismatched)));
    }

    /**
     * THE MEMO DECISION IS MADE FROM WHAT THE READ OBSERVED. {@link TheoryHelper#memoizable(Rec, boolean)} is a pure
     * function of the block a law read produced and whether the type's own space answered it, so the rule is pinned
     * here rather than through a boot/close integration test (which would have to tear the shared VM down inside a
     * test method). A non-empty block is always safe to memoize. An empty one is safe only when a {@code ?decq} rec
     * actually came back; a BLIND read — the owning space was not reachable, or held no declaration yet — must
     * answer its caller and never enter the memo, because {@code TheoryHelper}'s memo is static and only a boot
     * clears it ({@code BootLoader.resetMemos}), so memoizing it would answer "this type declares no algebra" for the
     * rest of that boot. Each row: the block the read produced % how the read went % whether it is memoizable.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "empty     % blind                   % false",
            "empty     % settled, space answered % true",
            "non-empty % blind                   % true",
    }, delimiter = '%')
    public void testTypeLawsAreMemoizedOnlyFromAnObservedAnswer(final String block, final String read, final boolean memoized) {
        final Rec laws = block.equals("empty") ? rec0() : rec(uri("ring"), uri("ring_theory"));
        final boolean spaceAnswered = read.equals("settled, space answered");
        assertEquals(memoized, TheoryHelper.memoizable(laws, spaceAnswered),
                "the memo decision for a " + block + " block from a " + read + " read");
    }

    /// ------------------------------------------------------------------ the census reads

    /**
     * THE CARRIERS THE ALGEBRA ASSERTIONS WALK — every base type that declares instances ({@code mTypes.declareAlgebras}),
     * plus {@code rel} (which declares none today: reported, not failed) and the machine frame type, whose
     * {@code free_groupoid} is declared at {@code /m/mach/machine}.
     */
    private static List<Type> carriers() {
        return List.of(INT_TYPE, REAL_TYPE, BOOL_TYPE, STR_TYPE, BYTES_TYPE, URI_TYPE, CODE_TYPE,
                LST_TYPE, REC_TYPE, REL_TYPE, T(f("/m/mach/machine")));
    }

    /**
     * The address IS a registered instruction's tid: the declaration addresses a live implementation.
     */
    private static boolean live(final List<Inst> insts, final fURI address) {
        return null != address && insts.stream().anyMatch(inst -> address.equals(inst.tid()));
    }

    /**
     * Does a registration of the role's OPERATION cover the role's own carrier? This is the family read a role
     * pointer takes, with the admission rule a family read applies: the same op family, over a carrier on the
     * address's path to the root. It is deliberately NOT a bare operation-name match — a lost {@code zero?bool} is
     * not answered by {@code zero?int}, because {@code bool}'s path never includes {@code int}, while code's
     * {@code end} role IS answered by the registered {@code end?rng=/m/code{0}&dom=/m/code} (same carrier,
     * coefficient-only difference) and its {@code branch} role by the generic {@code branch?dom=A} (a generic dom
     * is on every path).
     */
    private static boolean covered(final List<Inst> insts, final fURI address) {
        if (null == address || null == address.basePath())
            return false;
        final Type carrier = address.hasDom() ? T(address.dom()) : null;
        return insts.stream().anyMatch(inst -> null != inst.tid() && null != inst.tid().basePath()
                && inst.tid().basePath().bimatches(address.basePath().asNode())
                && (null == carrier || carrier.pathIncludes(inst.dom())));
    }

    /**
     * Does an instruction registration answer this address? Asked over the ADDRESS'S OWN carrier (its {@code dom}),
     * because a role names one operation's contract over one carrier: a role naming {@code int}'s {@code plus} is
     * answered by the int contract even when it is asked of a {@code nat}, and the machine's groupoid role names a
     * {@code uri} operation the {@code machine} type's own path never includes.
     */
    private static boolean admits(final fURI address) {
        if (null == address || null == address.basePath() || !Machine.loaded())
            return false;
        final Type carrier = address.hasDom() ? T(address.dom()) : null;
        try {
            return null != Typer.Helper.deduceTypeInst(Machine.current().instset(), carrier, address);
        } catch (final Throwable e) {
            return false;                                       // an unparseable address answers no instruction
        }
    }

    /**
     * THE LAWS AN INSTRUCTION DECLARES — its {@code ?decq} rec (where {@code InstBuilder.create} stages the claims
     * an instruction makes about itself), read raw and as {@link Law.Helper#lawsOf(fURI)} answers it, unioned in
     * declaration order and deduplicated. Both are reads of a DECLARATION, never of a tentative claim: the two reads
     * may state the same law, and the census must report each claim once.
     */
    private static List<Law> declaredLaws(final Inst inst) {
        final List<Law> laws = new ArrayList<>();
        final Obj fromDecq = Machine.read(Inst.Helper.operationTid(inst.tid()).addQ(DECQ));
        if (fromDecq.isRec())
            addLaws(laws, fromDecq.asRec().atDirect(uri(LAW)));
        addLaws(laws, Law.Helper.lawsOf(inst.tid()).atDirect(uri(LAW)));
        return laws;
    }

    /**
     * Add the law names a declared {@code law => [..]} lst holds, ignoring names outside the vocabulary. A slot that
     * names the LAW TYPE's address ({@code uri(LAW_TID)}, the {@code law::T} form) means "any law" and expands to the
     * whole vocabulary, so a type-valued slot can never silently disable the census.
     */
    private static void addLaws(final List<Law> laws, final Obj declared) {
        if (null == declared || declared.isNoObj())
            return;
        if (declared.isUri() && Law.LAW_TID.equals(declared.uriValue())) {
            for (final Law law : Law.values())
                if (!laws.contains(law))
                    laws.add(law);
            return;
        }
        if (!declared.isLst())
            return;
        for (final Obj obj : declared.lstValue()) {
            if (!obj.isUri())
                continue;
            final Law law = lawNamed(obj.uriValue().name());
            if (null != law && !laws.contains(law))
                laws.add(law);
        }
    }

    /**
     * The {@link Law} a declared name denotes, or null when the claim names something outside the vocabulary.
     */
    private static Law lawNamed(final String name) {
        for (final Law law : Law.values())
            if (law.name().equals(name))
                return law;
        return null;
    }

    /**
     * Is this address (or carrier) written down as a known exception? An entry must begin with its address.
     */
    private static boolean allowed(final String address) {
        return null != address && ALLOWLIST.stream().anyMatch(entry -> entry.startsWith(address));
    }
}
