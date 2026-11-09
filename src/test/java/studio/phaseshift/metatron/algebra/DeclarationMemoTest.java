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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.algebra.rewrite.plan.Plan;
import studio.phaseshift.metatron.algebra.rewrite.plan.PlanInterpreter;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter.PlanRewriter;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DECQ;
import static studio.phaseshift.metatron.furi.q.QCollection.DECS_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.MOD_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;

/**
 * THE ALGEBRA MEMOS FOLLOW THE STORE: a {@code ?decq} write evicts what {@link TheoryHelper#instances},
 * {@link Law.Helper#derivations()} and {@link Law.Helper#claimants} derived from the declaration it replaced, before the
 * write returns, so a declaration made at runtime is read at once. A relative type is a machine's own, so its algebra is
 * never memoized for others.
 */
public class DeclarationMemoTest extends AbstractMetatronTest {

    @Test
    public void aLawDeclaredAtRuntimeLicensesItsEquation() {
        // int mod declares pure alone, so mod·mod stays. Declared idempotent at runtime (x % m % m is x % m), the claim
        // licenses /m's rewrite table's op·op ⇝ op at once; the restored claim takes the rule away again. The snapshot reads
        // mod again and keeps every other claim: one claimant joins the law, another law's claimants stand, and the
        // restored claimants are the ones they were, in their order
        final fURI address = Inst.Helper.operationTid(MOD_INST_TID.dom(INT_TID).rng(INT_TID)).addQ(DECQ);
        final Obj original = Machine.read(address);
        final Plan idempotent = PlanRewriter.planOf("law_idempotent");
        final Code chain = ObjmtronSerializer.parse("5.mod(3).mod(3)").asCode();
        assertEquals(chain, PlanInterpreter.lower(idempotent, chain), "mod declares no idempotent law");
        final List<Inst> idempotents = Law.Helper.claimants(Law.idempotent);
        final List<Inst> involutions = Law.Helper.claimants(Law.involution);
        final Map<Obj, Obj> claim = mutableMap(original.asRec().jvm());
        claim.put(uri(LAW), ObjmtronSerializer.parse("[pure,idempotent]"));
        try {
            Machine.write(address, noobj());
            Machine.write(address, rec(claim, DECS_TID, null));
            assertEquals(ObjmtronSerializer.parse("5.mod(3)").asCode(), PlanInterpreter.lower(idempotent, chain),
                    "the declared law licenses its rule, got the claimants " + Law.Helper.claimants(Law.idempotent));
            final List<Inst> declared = Law.Helper.claimants(Law.idempotent);
            assertEquals(idempotents.size() + 1, declared.size(), "mod joins the law's claimants alone, got " + declared);
            assertTrue(declared.containsAll(idempotents), "every other claimant stays, got " + declared);
            assertEquals(involutions, Law.Helper.claimants(Law.involution), "a law the claim does not state keeps its claimants");
        } finally {
            Machine.write(address, noobj());
            Machine.write(address, original);
        }
        assertEquals(chain, PlanInterpreter.lower(idempotent, chain), "the restored claim takes the rule away");
        assertEquals(idempotents, Law.Helper.claimants(Law.idempotent), "the restored claimants are the ones they were, in their order");
    }

    @Test
    public void aClaimABuilderStagesIsReadByTheNextSnapshot() {
        // a builder stages its claim and writes nothing to ?decq, so only the staging revision tells the snapshot, and the
        // claim lands when the walk reads it again. The instruction is one the store answers without a claim, under a
        // base path no claim is staged under: nothing but the staged write can make the walk read it again
        final Set<fURI> claimed = PendingWrites.writes().stream().map(PendingWrites.Write::address)
                .filter(staged -> staged.hasQ(DECQ)).map(fURI::basePath).collect(Collectors.toSet());
        final Inst bare = Machine.current().instset().insts().stream()
                .filter(inst -> null != inst.tid() && inst.args().isEmpty() && !claimed.contains(inst.tid().basePath())
                        && !Machine.read(Inst.Helper.operationTid(inst.tid()).addQ(DECQ)).isRec())
                .findFirst().orElseThrow();
        final fURI address = Inst.Helper.operationTid(bare.tid()).addQ(DECQ);
        final List<Inst> idempotents = Law.Helper.claimants(Law.idempotent);
        try {
            PendingWrites.write(address, rec(mutableMap(uri(LAW), ObjmtronSerializer.parse("[pure,idempotent]"), uri(OBJ), bare), DECS_TID, null));
            final List<Inst> declared = Law.Helper.claimants(Law.idempotent);
            assertTrue(declared.stream().anyMatch(term -> term.tid().basePath().equals(bare.tid().basePath())),
                    "the staged claim of " + bare.tid() + " makes it a claimant, got " + declared);
        } finally {
            // the log keeps every write it staged, so the claim is withdrawn by staging noobj in its place, and noobj
            // written to ?decq clears the claim the read published
            PendingWrites.write(address, noobj());
            Machine.write(address, noobj());
        }
        assertEquals(idempotents, Law.Helper.claimants(Law.idempotent), "the withdrawn claim leaves the claimants as they were");
    }

    /**
     * A COPY of a type's declared theory, two levels down. A builder-declared theory is the live Algebra the staged
     * claim and the store share ({@link Algebra#of} answers an Algebra as itself), so it must never be handed to another
     * declaration.
     */
    private static Obj theoryOf(final fURI type) {
        final Rec theory = Machine.read(type.addQ(DECQ)).asRec().atDirect(uri(THEORY)).asRec();
        final Map<Obj, Obj> copy = new LinkedHashMap<>();
        theory.jvm().forEach((name, instance) -> copy.put(name, instance.isRec()
                ? rec(mutableMap(instance.asRec().jvm()), instance.tid(), instance.vid()) : instance));
        return rec(copy);
    }

    private static Obj declaration(final Obj theory) {
        return rec(mutableMap(uri(OBJ), str("ledgerwick"), uri(THEORY), theory), DECS_TID, null);
    }

    private static Set<Obj> instances(final fURI type) {
        return TheoryHelper.instances(T(type)).jvm().keySet();
    }

    @Test
    public void aTypesAlgebraDeclaredAtRuntimeIsReadAtOnce() {
        final fURI vellum = f("/ledgerwick/vellum");
        final Obj intTheory = theoryOf(INT_TID);
        final Obj boolTheory = theoryOf(BOOL_TID);
        assertNotEquals(intTheory.asRec().jvm().keySet(), boolTheory.asRec().jvm().keySet(), "the two algebras differ");
        try {
            Machine.write(vellum.addQ(DECQ), declaration(intTheory));
            assertEquals(intTheory.asRec().jvm().keySet(), instances(vellum), "a declared algebra is read (and memoized)");
            // an override is declared noobj first
            Machine.write(vellum.addQ(DECQ), noobj());
            Machine.write(vellum.addQ(DECQ), declaration(boolTheory));
            assertEquals(boolTheory.asRec().jvm().keySet(), instances(vellum), "the re-declaration is read, not the memo");
        } finally {
            Machine.write(vellum.addQ(DECQ), noobj());
        }
        assertEquals(intTheory.asRec().jvm().keySet(), instances(INT_TID), "int's own algebra is untouched");
    }

    @Test
    public void aDerivationWrittenAtRuntimeReachesTheRewriteList() {
        // written straight to ?decq, so no builder stages it: only the write hook can tell the snapshot. Its rhs names
        // instructions no chain contains, so the rule is inert for every other test.
        final fURI address = Inst.Helper.operationTid(MOD_INST_TID.dom(INT_TID).rng(INT_TID));
        final Code lhs = code(ObjmtronSerializer.<Call>parse("mod(#::T@b)"));
        final Code rhs = ObjmtronSerializer.parse("thornwhistle(#::T@b).quillfeather()");
        final Obj original = Machine.read(address.addQ(DECQ));
        final Map<Obj, Obj> claim = original.isRec() ? mutableMap(original.asRec().jvm()) : mutableMap(uri(OBJ), str("mod"));
        claim.put(uri(DERIVATION), rec(mutableMap(uri(LHS), lhs, uri(RHS), rhs)));
        assertFalse(Law.Helper.derivations().values().stream().anyMatch(d -> d.atDirect(uri(RHS)).toString().equals(rhs.toString())),
                "the snapshot is taken, and holds no such equation yet");
        try {
            Machine.write(address.addQ(DECQ), noobj());
            Machine.write(address.addQ(DECQ), rec(claim, DECS_TID, null));
            assertTrue(Law.Helper.derivations().values().stream().anyMatch(d -> d.atDirect(uri(RHS)).toString().equals(rhs.toString())),
                    "a derivation written at runtime feeds the rewrite list, got " + Law.Helper.derivations());
        } finally {
            Machine.write(address.addQ(DECQ), noobj());
            if (original.isRec())
                Machine.write(address.addQ(DECQ), original);
        }
    }

    @Test
    public void eachMachineReadsItsOwnRelativeTypesAlgebra() throws Exception {
        // two threads, each in a scratch machine of its own: the first declares a relative `vellum` and reads it, the
        // second declares nothing for its own `vellum`, so it must read no algebra, never the first one's
        final fURI vellum = f("vellum");
        final Obj intTheory = theoryOf(INT_TID);
        final AtomicReference<Set<Obj>> first = new AtomicReference<>();
        final AtomicReference<Set<Obj>> second = new AtomicReference<>();
        final Thread declaring = new Thread(() -> {
            Machine.current().push(f("ledger_first"));
            try {
                Machine.write(vellum.addQ(DECQ), declaration(intTheory));
                first.set(instances(vellum));
            } finally {
                Machine.current().pop();
            }
        });
        declaring.start();
        declaring.join();
        final Thread reading = new Thread(() -> {
            Machine.current().push(f("ledger_second"));
            try {
                second.set(instances(vellum));
            } finally {
                Machine.current().pop();
            }
        });
        reading.start();
        reading.join();
        assertEquals(intTheory.asRec().jvm().keySet(), first.get(), "the declaring machine reads its algebra");
        assertEquals(Set.of(), second.get(), "the other machine's `vellum` declares no algebra");
        assertEquals(intTheory.asRec().jvm().keySet(), instances(INT_TID), "int's own algebra is untouched");
    }
}
