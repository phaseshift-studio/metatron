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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Theory resolution — reading a type's declared algebraic structures (the theory instances under
 * {@code object.theory}) in a theory-generic way, instead of hard-coding one type's names.
 *
 * <p>A type's law block is a rec of <em>named theory instances</em> (e.g. {@code ring}, {@code add_group},
 * {@code field}, {@code boolean}), each an instance of a theory type ({@code ring_theory :: T},
 * {@code group_theory :: T}, …) with its operations named by <em>role</em> ({@code add}, {@code mul},
 * {@code op}, {@code zero}, {@code one}, {@code inv}, …). These helpers navigate that two-level structure:</p>
 *
 * <pre>
 *   type  →  law  →  instance(name)  →  role(role)  →  inst | value
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TheoryHelper extends MRec {

    /**
     * The instance name a type's law block uses for the additive group (op, id, inv).
     */
    public static final String ADD_GROUP = "add_group";

    public TheoryHelper(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static TheoryHelper from(final Rec rec) {
        return rec instanceof TheoryHelper ? (TheoryHelper) rec : new TheoryHelper(rec.jvm(), rec.tid(), rec.vid());
    }

    /**
     * The instance name an algebraic theory TID maps to in a law block — {@code ring_theory :: T → ring}.
     */
    public static String nameOf(final fURI theoryTID) {
        return theoryTID.name().replace("_theory", "");
    }

    /**
     * Legacy alias for {@link #nameOf} — the law-block key a theory TID resolves to.
     */
    public static fURI tidToKey(final fURI theoryTID) {
        return f(nameOf(theoryTID));
    }

    /**
     * The rec of a named theory instance from a type's law block — the empty rec when the type models it not.
     */
    public static Rec instance(final Type type, final String name) {
        return instances(type).at(f(name)).orElse(rec0());
    }

    public static Rec instance(final Type type, final fURI theoryTID) {
        return instance(type, nameOf(theoryTID));
    }

    /**
     * The value of a role (a {@code zero}, {@code one}, {@code id}, …) — noobj when the type models it not.
     */
    public static Obj element(final Type type, final String name, final String role) {
        final Obj obj = instance(type, name).at(f(role));
        return obj.isNoObj() ? noobj() : obj;
    }

    public static Obj element(final Type type, final fURI theoryTID, final String role) {
        return element(type, nameOf(theoryTID), role);
    }

    /**
     * The inst of a role (an {@code op}, {@code add}, {@code mul}, …) — falling back to the generic {@code op} —
     * noobj when the type models it not.
     */
    public static Inst inst(final Type type, final String name, final String role) {
        final Obj obj = instance(type, name).at(f(role));
        if (!obj.isNoObj())
            return obj.asInst();
        if (role.equals(OP))
            return noobj().asInst();
        final Obj op = instance(type, name).at(f(OP));
        return op.isNoObj() ? noobj().asInst() : op.asInst();
    }

    public static Inst inst(final Type type, final fURI theoryTID, final String role) {
        return inst(type, nameOf(theoryTID), role);
    }

    /**
     * The inst of {@code opRole} applied to the value of {@code idRole} — the unit pattern, e.g. a type's
     * {@code add} applied to its {@code zero}.
     */
    public static Inst unit(final Type type, final String name, final String opRole, final String idRole) {
        return inst(type, name, opRole).args(lst(element(type, name, idRole)));
    }

    public static Inst unit(final Type type, final fURI theoryTID, final String opRole, final String idRole) {
        return unit(type, nameOf(theoryTID), opRole, idRole);
    }

    /**
     * The named theory instances of a type — the {@code object::T.theory} rec, keyed by the user's name for
     * each instance (e.g. {@code ring}, {@code add_group}, {@code boolean}). Empty when the type models no
     * algebraic structure. This is the enumeration a law-driven rewrite walks.
     */
    public static Algebra instances(final Type type) {
        // THE DECLARED ALGEBRA, READ LEANLY: the theory facet of the type's ?decq (one Machine.read), not the categorical
        // lift (an object::T with its lazy incidence) that only the category instset builds. A type with no address, or
        // one that declares nothing, answers the empty algebra — never a fail, so a law query declines rather than crashing.
        if (null == type.vid())
            return Algebra.empty();
        final fURI key = type.vid().basePath();
        final Algebra cached = ALGEBRAS.get(key);
        if (null != cached)
            return cached;
        final Obj declared = Machine.read(type.vid().addQ(QCollection.DECQ));
        final boolean spaceAnswered = declared.isRec();
        final Obj theory = spaceAnswered ? declared.asRec().atDirect(uri(THEORY)) : noobj();
        final Algebra algebra = theory.isRec() ? Algebra.of(theory.asRec()) : Algebra.empty();
        if (memoizable(algebra, spaceAnswered))
            ALGEBRAS.putIfAbsent(key, algebra);
        return algebra;
    }

    /**
     * THE ALGEBRA MEMO, keyed by the type's VID base path (two refinements of one base share a tid, so a tid key would
     * answer one's algebra for the other). INTERIM: type-edit subscriptions will replace it.
     */
    private static final Map<fURI, Algebra> ALGEBRAS = new ConcurrentHashMap<>();

    /**
     * A BOOT IS A FRESH WORLD — the memoized algebras hold role pointers into the spaces of the boot that read them, so a
     * second VM in the same JVM must not answer them ({@code BootLoader.resetMemos}, before any set mounts).
     */
    public static void reset() {
        ALGEBRAS.clear();
    }

    /**
     * WHETHER AN ANSWER MAY BE MEMOIZED — a pure function of what the read OBSERVED: a non-empty algebra always; an empty
     * one only when the type's space answered with a declaration ("declares nothing" is then a fact). A BLIND read — no
     * space reachable, or no declaration yet — is a fact about the machine asking, and memoizing it would answer "this
     * type declares no algebra" for the rest of the JVM's life.
     */
    public static boolean memoizable(final Rec laws, final boolean spaceAnswered) {
        return !laws.jvm().isEmpty() || spaceAnswered;
    }

    /**
     * The (op, identity) role pairs a theory can witness: {@code add}/{@code zero}, {@code mul}/{@code one},
     * {@code op}/{@code id}, {@code or}/{@code zero}, {@code and}/{@code one}, {@code join}/{@code bottom},
     * {@code meet}/{@code top}. Each pair names the unit pattern {@code op(id)} the theory licenses.
     */
    public static final List<String[]> UNIT_ROLES = List.of(
            new String[]{ADD, ZERO}, new String[]{MUL, ONE}, new String[]{OP, ID},
            new String[]{OR, ZERO}, new String[]{AND, ONE}, new String[]{JOIN, BOTTOM}, new String[]{MEET, TOP});

    /**
     * The identity roles that may hold an <em>operation</em> rather than a literal ({@code one}, {@code id}), e.g.
     * the code ring's {@code id}. Such an identity is the identity morphism only when its operation is composition
     * — see {@link #compositionIdentity} — since the code ring's additive group also names an {@code id}: end.
     */
    public static final List<String> IDENTITY_ROLES = List.of(ONE, ID);

    /**
     * The unit pattern of an instance's role pair — the inst {@code op} applied to the instance's identity
     * element, e.g. {@code add(zero)} for a ring. {@code noobj} when the instance does not declare both roles.
     * The law roles hold {@code !*} pointers, so applying {@code args} to the pointer builds the pattern the
     * shipped rewrites test with {@link Inst#test(Obj)}; the pointer is transparent.
     */
    public static Inst unit(final Rec instance, final String opRole, final String idRole) {
        final Obj id = instance.at(f(idRole));
        final Obj op = instance.at(f(opRole));
        if (id.isNoObj() || !op.isInst())
            return noobj().asInst();
        return op.asInst().args(lst(id));
    }

    /**
     * The op address of an instance's identity <em>operation</em>, or null when the role holds a literal
     * (e.g. {@code zero => 0}) rather than an operation (code's {@code one => !*id}). A {@code !*} pointer
     * names its target in its own argument, so the address is read without resolving — the target may be an
     * as-yet-unresolved registration.
     */
    public static fURI identityAddress(final Rec instance, final String idRole) {
        // the raw rec value: `at()` dereferences an auto (`!*`) role, and a pointer whose target is not
        // registered yet reads back noobj — which is exactly the code ring's `one => !*id`
        Obj id = instance.jvm().get(uri(idRole));
        if (null == id)
            id = instance.at(f(idRole));
        if (null == id || id.isNoObj())
            return null;
        if (id.isUri())
            return id.uriValue().basePath();
        if (!id.isInst())
            return null;
        final Inst inst = id.asInst();
        if (!inst.args().isEmpty() && inst.arg(0).isUri())
            return inst.arg(0).uriValue().basePath();
        return inst.tid().basePath();
    }

    /**
     * The op address of a type's declared additive structure — the law-indexed witness: the first theory
     * instance of {@code object[T].theory} that carries an additive role — the {@code add} role of a ring/field/rig,
     * or the {@code op} role of an {@code add_*} monoid/group. The role is read with {@code atDirect} (no call
     * evaluation; {@code rec.has} reports false on law keys that still resolve, so the noobj answer is the
     * presence test), and the auto pointer is unwrapped with {@code getPointer} without resolving it. For the
     * code ring this answers the branch address, for the int ring the plus address; a type declaring no additive
     * structure answers null, which degrades the additive normalizers — and their liveness — to the identity.
     */
    public static fURI additiveWitness(final Type type) {
        fURI add = null;
        for (final Map.Entry<Obj, Obj> instance : instances(type).jvm().entrySet()) {
            if (!instance.getValue().isRec())
                continue;
            final Rec block = instance.getValue().asRec();
            final String name = instance.getKey().isUri() ? instance.getKey().uriValue().name() : null;
            if (null != name && name.startsWith("add_")) {
                final fURI op = roleAddress(block, OP);
                if (null != op)
                    return op;  // the additive group's n-ary op (branch) — the form the normalizers fold
            } else if (null == add)
                add = roleAddress(block, ADD);
        }
        return add;
    }

    /**
     * The op address of the identity MORPHISM — the identity of chain composition, read off the type's declared
     * algebra: its multiplicative op is the {@code mul} role ({@code compose} for the code ring), and a theory
     * instance whose operation is that op contributes its operation-valued identity ({@code ring.one},
     * {@code mult_monoid.id}: both {@code id}). The identity of any OTHER operation is no identity morphism: the
     * additive group's {@code id} is end, the identity of branch, which under composition is the ZERO. Null when
     * the type declares no multiplicative structure, so no bare op is taken for the identity.
     */
    public static fURI compositionIdentity(final Type type) {
        final Collection<Obj> blocks = instances(type).jvm().values();
        final fURI compose = blocks.stream()
                .filter(Obj::isRec)
                .map(block -> roleAddress(block.asRec(), MUL))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (null == compose)
            return null;
        for (final Obj block : blocks) {
            if (!block.isRec())
                continue;
            for (final String[] roles : UNIT_ROLES) {
                final fURI op = IDENTITY_ROLES.contains(roles[1]) ? roleAddress(block.asRec(), roles[0]) : null;
                if (null != op && compose.basePath().equals(op.basePath())) {
                    final fURI identity = identityAddress(block.asRec(), roles[1]);
                    if (null != identity)
                        return identity;
                }
            }
        }
        return null;
    }

    /**
     * The op address a law block's role binds, or null when the block or the role is absent, or the role binds a
     * literal: the value arrives as a {@code !*} auto pointer naming its target in its own argument, so the address
     * is read without resolving — the target may be an as-yet-unresolved registration.
     */
    static fURI roleAddress(final Rec block, final String role) {
        if (null == block)
            return null;
        final Obj holder = block.atDirect(uri(role));
        if (holder.isNoObj())
            return null;
        if (holder.isUri())
            return holder.uriValue();
        return Obj.Helper.getPointer(holder).orElse(null);
    }

    // ---- stable, algebra-specific conveniences (used by the shipped rewrites) -----------------------------------------

    public static Obj zeroElement(final fURI theoryTID, final Type type) {
        return element(type, theoryTID, ZERO);
    }

    public static Obj oneElement(final fURI theoryTID, final Type type) {
        return element(type, theoryTID, ONE);
    }

    public static Inst plusInst(final fURI theoryTID, final Type type) {
        return inst(type, theoryTID, ADD);
    }

    public static Inst plusZeroInst(final fURI theoryTID, final Type type) {
        return unit(type, theoryTID, ADD, ZERO);
    }

    public static Inst multOneInst(final fURI theoryTID, final Type type) {
        return unit(type, theoryTID, MUL, ONE);
    }

    /**
     * The involution op of a type's additive group — instance {@link #ADD_GROUP}, role {@code inv}
     * ({@code neg} for int / real; the coefficient −1 unapply for code).
     */
    public static Inst invInst(final Type type) {
        final Obj obj = instance(type, ADD_GROUP).at(f(INV));
        return obj.isNoObj() ? noobj().asInst() : obj.asInst();
    }
}
