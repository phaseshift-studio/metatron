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

package studio.phaseshift.metatron.isa.m.mach.instset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.instset.BasicInstSet;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.INSTSET_TID;
import static studio.phaseshift.metatron.Tokens.M_ISA_REWRITE_TID;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.ACTIVE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INACTIVE_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.NAT_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * WHAT A UNION READ DOES ACROSS LEVELS — the shadowing rule, stated once.
 * <p>
 * The tree is walked and candidates ACCUMULATE; a nearer candidate wins <b>only when it names the same thing</b>.
 * An inst's identity is its tid (its dom/rng contract); a type's is its vid. So:
 * <ul>
 *   <li><b>the same identity is shadowed</b> — a child that re-declares what the enclosing machine has answers with
 *       its own, once, and the enclosing machine is untouched;</li>
 *   <li><b>a different identity is ADDED</b> — an overloaded name keeps every distinct contract, so a frame can
 *       extend a name rather than erase it;</li>
 *   <li><b>an identity only the enclosing machine has is still answered</b> — inheritance is the same walk.</li>
 * </ul>
 * The alternative — "the first level that answers, wins" — makes a local overload erase every imported overload of
 * the same name, which is why it is not what this does.
 */
public class InstSetUnionReadTest extends AbstractMetatronTest {

    private static final fURI NAME = f("/m/inst/unionTest");
    private static final fURI INT_CONTRACT = NAME.dom(f("/m/int")).rng(f("/m/int"));
    private static final fURI REAL_CONTRACT = NAME.dom(f("/m/real")).rng(f("/m/real"));

    private static Inst contract(final fURI tid, final int arg) {
        return instC(tid, lst(jnt(arg)), (lhs, inst) -> lhs);
    }

    /**
     * THE CANONICAL READ IS {@code Machine.read} — the instset is one of ITS sources, so mtron's {@code *plus} and a
     * selector's fetch are the same question asked once. What the read must give is every contract satisfying the
     * dom/rng constraint, at the full instruction address: {@code *plus} is the whole family, {@code *plus?int<=int}
     * the int one, and the lattice case {@code *plus?nat<=nat} admits the int contract because nat's path is
     * nat -> int -> #. A caller reaching for {@code instset()} directly is an optimization of this, never the door.
     */
    @Test
    public void testMachineReadAnswersThePlusFamilyThroughTheInstset() {
        InstSet.importInstSet(f("/m/math"));

        final Obj family = Machine.read(f("plus"));
        assertFalse(family.isNoObj(), "the read answers the plus family: " + family);
        assertTrue(family.stream().count() > 1,
                "and it is the family, not a single contract: " + family);

        final Obj exact = Machine.read(f("plus").dom(f("/m/int")).rng(f("/m/int")));
        assertFalse(exact.isNoObj(), "plus?int<=int admits the int contract: " + exact);
        assertTrue(exact.stream().allMatch(o -> T(f("/m/int")).pathIncludes(o.asInst().dom())),
                "and nothing off int's path: " + exact);

        // THE LATTICE CASE: a nat query admits the int contract. nat lives at /m/math/nat — the language spells it
        // short, the read routes the short name to that address, and both spellings must ask the same question.
        final Type nat = T(f(NAT_TID.name()));
        final Obj admitted = Machine.read(f("plus").dom(f(NAT_TID.name())));
        assertFalse(admitted.isNoObj(), "plus?nat<=nat must admit a contract: " + admitted);
        assertTrue(admitted.stream().allMatch(o -> nat.pathIncludes(o.asInst().dom())),
                "every admitted contract is on nat's path (nat -> int -> #): " + admitted);
        assertEquals(admitted.stream().count(),
                Machine.read(f("plus").dom(f("/m/math/nat"))).stream().count(),
                "the short name and the full address ask the same question");
    }

    @Test
    public void testTheSameIdentityIsShadowedByTheNearerLevel() {
        final Machine parent = Machine.current();
        final BasicInstSet parentUnion = (BasicInstSet) parent.instset();
        parentUnion.write(NAME, contract(INT_CONTRACT, 1));

        final Machine child = parent.push(f("xyz"));
        try {
            final BasicInstSet childUnion = (BasicInstSet) child.instset();
            childUnion.write(NAME, contract(INT_CONTRACT, 2));

            assertEquals(1, childUnion.read(NAME).stream().count(),
                    "the same identity is one candidate, not two: " + childUnion.read(NAME));
            assertEquals(jnt(2), childUnion.read(NAME).asInst().arg(0),
                    "and the nearer level's is the one that answers");
            assertEquals(jnt(1), parentUnion.read(NAME).asInst().arg(0),
                    "the enclosing level keeps its own");
        } finally {
            child.pop();
        }
    }

    /**
     * A REWRITE IS SWITCHED OFF BY ITS SHADOW (Marko, 2026-11-10) — {@code inactive} answers a rewrite's shadow, the
     * identity on code under the rewrite's own tid, carrying the rewrite, and {@code active} answers the rewrite back. The
     * shadow is the same identity as /m's rewrite, so written into a child's own level it stands in the rewrite's place:
     * the child's pool lists it there, and the rule stops firing in the child, and only there. A noobj write at the
     * rewrite's address deletes the shadow, and /m's rewrite answers again.
     */
    @Test
    public void testARewriteIsSwitchedOffByItsShadow() {
        final Machine parent = Machine.current();
        final fURI address = M_ISA_REWRITE_TID.extend("law_absorbing");
        final Inst rule = parent.instset().rewrites().stream()
                .filter(rewrite -> rewrite.tid().basePath().equals(address)).findFirst().orElseThrow();
        final Inst shadow = Processor.Helper.apply(rule, instB(INACTIVE_INST_TID, lst())).asInst();
        assertNotSame(rule, shadow, "inactive answers a new instruction, not the rewrite");
        assertEquals(rule.tid(), shadow.tid(), "the shadow is the rewrite's own identity");
        assertEquals(rule, Inst.Helper.shadowed(shadow), "and carries the rewrite it switches off");
        assertTrue(Inst.Helper.shadowed(rule).isNoObj(), "a rewrite is no shadow");
        assertEquals(shadow, Processor.Helper.apply(shadow, instB(INACTIVE_INST_TID, lst())), "a shadow is already off");
        assertEquals(rule, Processor.Helper.apply(shadow, instB(ACTIVE_INST_TID, lst())), "active answers the rewrite back");
        assertEquals(rule, Processor.Helper.apply(rule, instB(ACTIVE_INST_TID, lst())), "and an active rewrite as it is");
        checkEquality(LOG, ObjmtronSerializer.parse("start(5).zero()"), ObjmtronSerializer.<Code>parse("5.plus(3).zero()").rewrite().tryToInst(), true);

        final Machine child = parent.push(f("rewrite_off"));
        try {
            final BasicInstSet childUnion = (BasicInstSet) child.instset();
            childUnion.write(address, shadow);
            assertEquals(List.of(shadow), childUnion.rewrites().stream().filter(rewrite -> rewrite.tid().equals(rule.tid())).toList(),
                    "the child's pool lists the shadow in the rewrite's place");
            checkEquality(LOG, ObjmtronSerializer.parse("start(5).plus(3).zero()"), ObjmtronSerializer.<Code>parse("5.plus(3).zero()").rewrite().tryToInst(), true);
            assertTrue(parent.instset().rewrites().contains(rule), "the enclosing machine keeps its rewrite");

            childUnion.write(address, noobj());
            assertEquals(List.of(rule), childUnion.rewrites().stream().filter(rewrite -> rewrite.tid().equals(rule.tid())).toList(),
                    "a noobj write deletes the shadow, and /m's rewrite answers again");
            checkEquality(LOG, ObjmtronSerializer.parse("start(5).zero()"), ObjmtronSerializer.<Code>parse("5.plus(3).zero()").rewrite().tryToInst(), true);
        } finally {
            child.pop();
        }
    }

    /**
     * THE REWRITE DOOR (Marko, 2026-11-10) — mtron reaches a machine's rewrites at {@code ~/instset/rewrite}. A read there
     * answers the effective pool, and {@code rewrite/<name>} a rewrite by its name, walked as a path or projected with
     * {@code >>}. A write there lands in the machine's own level. So a rewrite is switched off and back on in mtron, in a
     * child and only there. And a set's own path walks its rec as {@code >>} projects it. An instruction is written with
     * {@code to}, which writes the obj flowing in: an argument that is an instruction is code, applied to the lhs, so
     * {@code addr -> inst} would write what the instruction answers, never the instruction.
     */
    @Test
    public void testARewriteIsSwitchedOffThroughTheInstsetDoor() {
        final Machine parent = Machine.current();
        final Inst rule = parent.instset().rewrites().stream()
                .filter(rewrite -> "law_absorbing".equals(rewrite.tid().name())).findFirst().orElseThrow();
        assertEquals(rule, Machine.evaluate("*/m/rewrite/law_absorbing"), "a set's own path walks its rec");
        assertEquals(Machine.evaluate("*/m>>rewrite"), Machine.evaluate("*/m/rewrite"), "the path and >> read one table");

        final Machine child = parent.push(f("rewrite_door"));
        try {
            assertEquals(rule, Machine.evaluate("*~/instset/rewrite/law_absorbing"), "the door reads a rewrite of the pool by name");
            assertEquals(rule, Inst.Helper.shadowed(Machine.evaluate("*~/instset.>>rewrite/law_absorbing.inactive()").asInst()),
                    "and >> projects it, so its shadow is in reach");

            final Obj off = Machine.evaluate("*~/instset/rewrite/law_absorbing.inactive().to(~/instset/rewrite/law_absorbing)");
            assertFalse(off.isFail(), () -> "the door takes the shadow: " + off);
            assertEquals(rule, Inst.Helper.shadowed(Machine.evaluate("*~/instset/rewrite/law_absorbing").asInst()),
                    "the pool lists the rewrite as off");
            checkEquality(LOG, ObjmtronSerializer.parse("start(5).plus(3).zero()"), ObjmtronSerializer.<Code>parse("5.plus(3).zero()").rewrite().tryToInst(), true);
            assertTrue(parent.instset().rewrites().contains(rule), "the enclosing machine keeps its rewrite");

            Machine.evaluate("*~/instset/rewrite/law_absorbing.active().to(~/instset/rewrite/law_absorbing)");
            assertEquals(rule, Machine.evaluate("*~/instset/rewrite/law_absorbing"), "writing back the inherited rewrite removes the override");
            checkEquality(LOG, ObjmtronSerializer.parse("start(5).zero()"), ObjmtronSerializer.<Code>parse("5.plus(3).zero()").rewrite().tryToInst(), true);

            Machine.evaluate("*~/instset/rewrite/law_absorbing.inactive().to(~/instset/rewrite/law_absorbing)");
            Machine.evaluate("~/instset/rewrite/law_absorbing -> noobj");
            assertEquals(rule, Machine.evaluate("*~/instset/rewrite/law_absorbing"), "and so does noobj");
        } finally {
            child.pop();
        }
    }

    /**
     * Only a rewrite of the pool is switched off: plus is an instruction, so {@code inactive} refuses it, while
     * {@code active} answers an instruction that is no shadow as it is. Each row: the code % what it answers.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[plus(1)]==[inactive()]>-    % <ERROR>",
            "[plus(1)]==[active()]        % [plus(1)]",
            // the door holds rewrites by name: a value is no rewrite, and a name the pool lacks names nothing
            "~/instset/rewrite/law_absorbing -> 5      % <ERROR>",
            "~/instset/rewrite/no_such_rule -> noobj   % <ERROR>",
    }, delimiter = '%')
    public void testOnlyAPooledRewriteIsSwitchedOff(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    @Test
    public void testADifferentIdentityIsAddedNotSubstituted() {
        final Machine parent = Machine.current();
        final BasicInstSet parentUnion = (BasicInstSet) parent.instset();
        parentUnion.write(NAME, contract(INT_CONTRACT, 1));

        final Machine child = parent.push(f("xyz"));
        try {
            final BasicInstSet childUnion = (BasicInstSet) child.instset();
            childUnion.write(NAME, contract(REAL_CONTRACT, 2));

            assertEquals(2, childUnion.read(NAME).stream().count(),
                    "two contracts for one name — an overload is extended, never erased: " + childUnion.read(NAME));
            // the contract's identity is the OPERATION (dom/rng): a tid minted with MInst.ARGS_IN_TID also declares
            // its argument types, which must not make a written contract unrecognizable
            assertTrue(childUnion.read(NAME).stream().anyMatch(o -> Inst.Helper.operationTid(o.asInst().tid()).equals(REAL_CONTRACT)),
                    "the child's contract is among the candidates");
            assertTrue(childUnion.read(NAME).stream().anyMatch(o -> Inst.Helper.operationTid(o.asInst().tid()).equals(INT_CONTRACT)),
                    "and the enclosing machine's is too");
            assertEquals(1, parentUnion.read(NAME).stream().count(),
                    "the enclosing machine sees only its own — nothing leaks up");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAnIdentityOnlyTheEnclosingLevelHasIsStillAnswered() {
        final Machine parent = Machine.current();
        final BasicInstSet parentUnion = (BasicInstSet) parent.instset();
        final fURI vid = f("/m/union/onlyParent");
        parentUnion.write(vid, T(vid));

        final Machine child = parent.push(f("xyz"));
        try {
            final BasicInstSet childUnion = (BasicInstSet) child.instset();
            assertEquals(T(vid), childUnion.read(vid), "the walk answers what only the enclosing level has");
            assertTrue(childUnion.types().stream().anyMatch(t -> vid.equals(t.vid())),
                    "and enumeration walks the same chain: " + childUnion.types());
        } finally {
            child.pop();
        }
    }

    /**
     * INSTRUCTION MEMORY IS NOT DATA MEMORY. An ISA can be a member of a machine WITHOUT being one of its spaces:
     * referred by object, it answers a read even though nothing in the data index holds it. That is the distinction
     * made structural — membership no longer leans on the address index, so a space entry is free to go.
     */
    @Test
    public void testAnUnmountedInstructionSetIsStillAMemberAndAnswers() {
        final fURI isaVID = f("/m/isoDetached");
        final InstSet detached = new AbstractInstSet(mutableMap(uri(PATTERN), uri(ALL)), INSTSET_TID, isaVID) {
        };
        final fURI name = f("/m/inst/detachedOnly");
        detached.write(name, contract(name, 9));

        Machine.current().instset().refer(detached);
        assertFalse(Machine.current().memory().spaces().jvm().containsValue(detached),
                "the member ISA is NOT one of the machine's spaces");

        final Obj found = Machine.read(f("detachedOnly"));
        assertFalse(found.isNoObj(),
                "Machine.read answers a member ISA the data index never held: " + found);
        assertEquals(name, found.asInst().tid().basePath(),
                "and it is the contract that ISA holds: " + found);
    }

    /**
     * A FALLBACK READ MUST NOT CAUSE WORK. `Machine.read` asks instruction memory only after data memory has
     * missed, and it asks with the DECLARED form — so a member whose `read` materializes something is not dragged
     * into an unrelated lookup. Measured before the split: `SQLSchemaInstSet.read` populates a table's schema on
     * demand (right for a deliberate read), and a logger's TYPE lookup during a tbleSpace's own construction
     * walked into the catalog and hit a database connection that was not open yet — fourteen errors in
     * SqliteTbleSpaceTest.
     */
    @Test
    public void testTheFallbackReadAsksTheDeclaredFormOnly() {
        final AtomicInteger eager = new AtomicInteger();
        final InstSet eagerISA = new AbstractInstSet(mutableMap(uri(PATTERN), uri(ALL)), INSTSET_TID, f("/m/isoEager")) {
            @Override
            public Obj read(final fURI pattern) {
                eager.incrementAndGet();   // a DELIBERATE read is what does the work ...
                return super.read(pattern);
            }
        };
        Machine.current().instset().refer(eagerISA);

        Machine.read(f("/m/nothing/claims/this"));
        assertEquals(0, eager.get(), "the fallback must not trigger a member's eager read");

        eagerISA.read(f("/m/inst/whatever"));
        assertTrue(eager.get() > 0, "... while reading the vocabulary directly still does");
    }
}
