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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.mInstSet.AlgebraTool;
import studio.phaseshift.metatron.isa.m.mTypes;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.Typer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.lawOf;
import static studio.phaseshift.metatron.isa.m.mTypes.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.mTypes.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * THE LAW CENSUS — three facts about the algebra declarations, machine-checked, because each has silently broken
 * this week: a row whose address stopped being an instruction ({@code order?int}), an algebra whose role pointers
 * name instructions that are no longer registered (code's {@code neg}/{@code end}/{@code id}), and identities that
 * stopped naming anything (bool's {@code zero}/{@code one}). None of them produced an error: a row nothing
 * consults and a role that resolves to {@code noobj} are both silent, so the failure mode is a law that quietly
 * stops holding. This class is the census that turns each into a checked fact.
 *
 * <p>THE THREE FACTS
 * <ol>
 *   <li><b>every law-table row is reachable</b> — the row's address is either a registered instruction
 *       ({@code live}), or an operation the lift still admits over that carrier ({@code view}: the implementation
 *       lives elsewhere but {@link Typer.Helper#deduceTypeInst} resolves it, or a registration shares the row's
 *       op family), or it is an {@code orphan} — a declaration about an address nothing answers for;</li>
 *   <li><b>every declared algebra validates</b> — a carrier's declared theory instances are turned into the law
 *       claims their theories entail ({@code mTypes.axiomsOf}), and
 *       {@link AlgebraTool#analyzeType(Type, List)} must accept every claim for every operation the carrier binds.
 *       A carrier that declares no instance at all is REPORTED, never failed ({@code rel} declares none);</li>
 *   <li><b>every role points at something real</b> — each role value that is an address or a pointer must name a
 *       registered instruction, or one a registration of the same operation covers over a carrier on the role's own
 *       path, or one the lift admits. This is the assertion that catches code's {@code neg}/{@code end}/{@code id},
 *       bool's {@code zero}/{@code one} and an {@code order?int} that lost its registration.</li>
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
     * file, so every reader sees why the algebra, the row or the role is exempt, and removing an entry is the day
     * the underlying issue got fixed. The two entries below are the ones today's census produced; both are
     * ANALYZER limits rather than lost registrations, and neither may be "fixed" by renaming a declaration —
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
                    + "includes, so analyzeType binds no operation and every claim comes back unchecked");

    /**
     * The category is the set that populates {@link LawTable#rows()} — its {@code setup()} self-registers the
     * per-instset tables — so the catch-all import is what makes the law table exist at all. {@code /m/math} is
     * re-imported afterwards for the same reason {@code catInstSetTest} does it: the catch-all re-adds the base
     * {@code /m} set and re-registers its types against a fresh space.
     */
    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
        InstSet.importInstSet(f("/m/math"));
    }

    /**
     * ASSERTION 1 — EVERY DECLARED ROW IS REACHABLE FROM AN ADDRESS. Each row of each registered law table is
     * classified as {@code live} (an instruction's tid IS the row key), {@code view} (no exact instruction, but the
     * operation is still liftable: {@code deduceTypeInst} admits it, or a registration shares its op family) or
     * {@code orphan} (neither). An orphan is a declaration about an address nothing answers for — the
     * {@code order?int} failure mode — and the failure message prints all three lists so a human can act on the
     * orphans without reproducing the census by hand.
     */
    @Test
    public void testEveryLawTableRowIsReachable() {
        final Map<fURI, Map<fURI, LawTable.Entry>> tables = LawTable.rows();
        assertFalse(tables.isEmpty(),
                "no law table is registered: the category never mounted, so the census would be vacuous");

        final List<Inst> insts = List.copyOf(Machine.current().instset().insts());
        final List<String> live = new ArrayList<>();
        final List<String> view = new ArrayList<>();
        final List<String> orphan = new ArrayList<>();
        for (final Map.Entry<fURI, Map<fURI, LawTable.Entry>> table : tables.entrySet()) {
            for (final fURI address : table.getValue().keySet()) {
                final String row = table.getKey() + " table, row " + address;
                if (live(insts, address))
                    live.add(row);
                else if (view(insts, address))
                    view.add(row);
                else if (!allowed(address.toString()))
                    orphan.add(row);
            }
        }
        LOG.warn("law census: %d live, %d view, %d orphan row(s) across %d table(s)",
                live.size(), view.size(), orphan.size(), tables.size());
        LOG.warn("law census: rows reachable only through the lift (view): %s", view);
        assertTrue(orphan.isEmpty(), () -> """
                law-table rows no address answers for (lost registrations):
                  %s
                live (%d):
                  %s
                view (%d):
                  %s
                orphan (%d):
                  %s""".formatted(
                String.join("\n  ", orphan),
                live.size(), String.join("\n  ", live),
                view.size(), String.join("\n  ", view),
                orphan.size(), String.join("\n  ", orphan)));
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
            final Rec algebra = algebra(carrier);
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
     * role pointer takes, which is why code's {@code end} role is answered by {@code end?dom=/m/code&rng=/m/code{0}}
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
            final Rec algebra = algebra(carrier);
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
     * The type's declared algebra — the same read {@link AlgebraTool} makes: the law table's served
     * {@code ?lawq} declaration first, the direct declaration when no table answers for the type.
     */
    private static Rec algebra(final Type type) {
        final Rec declared = LawTable.typeLawsOf(type);
        if (!declared.jvm().isEmpty())
            return declared;
        final Obj declaration = lawOf(type);
        return declaration.isRec() ? declaration.asRec() : rec0();
    }

    /** The row key IS a registered instruction's tid: the declaration addresses a live implementation. */
    private static boolean live(final List<Inst> insts, final fURI address) {
        return null != address && insts.stream().anyMatch(inst -> address.equals(inst.tid()));
    }

    /**
     * No exact instruction, but the address is still answerable: either the lift resolves the operation over the
     * address's carrier ({@link #admits}), or a registration shares the row's op family (the row states a
     * different endpoint spelling than the implementation — a coefficient, a generic — and the family is what a
     * rewrite reads).
     */
    private static boolean view(final List<Inst> insts, final fURI address) {
        if (null == address)
            return false;
        final fURI base = address.basePath();
        if (null != base && insts.stream().anyMatch(inst -> null != inst.tid() && base.equals(inst.tid().basePath())))
            return true;
        return admits(address);
    }

    /**
     * Does a registration of the role's OPERATION cover the role's own carrier? This is the family read a role
     * pointer takes, with the admission rule a family read applies: the same op family, over a carrier on the
     * address's path to the root. It is deliberately NOT a bare operation-name match — a lost {@code zero?bool} is
     * not answered by {@code zero?int}, because {@code bool}'s path never includes {@code int}, while code's
     * {@code end} role IS answered by the registered {@code end?dom=/m/code&rng=/m/code{0}} (same carrier,
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
     * because a row or a role names one operation's contract over one carrier: {@code int}'s {@code plus} row is
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

    /** Is this address (or carrier) written down as a known exception? An entry must begin with its address. */
    private static boolean allowed(final String address) {
        return null != address && ALLOWLIST.stream().anyMatch(entry -> entry.startsWith(address));
    }
}
