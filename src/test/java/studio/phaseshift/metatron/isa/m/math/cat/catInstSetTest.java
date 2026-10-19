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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.BasicCompiler;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.FixPointRewriter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catInstSetTest extends AbstractInstSetTest {

    public catInstSetTest() {
        super(catInstSet::new);
    }

    @BeforeEach
    public void installCategoryCompiler() {
        Machine.current().compiler(CatTheoryCompiler.single());
    }

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
        // the catch-all import re-adds the base /m instset, and re-adding an already-mounted instset
        // re-registers its types against a fresh space, orphaning the types derived from them (e.g.
        // cmplx, whose poly is built from real/lst) — cmplx ops then fail to resolve. re-importing
        // /m/math after the catch-all restores them.
        InstSet.importInstSet(f("/m/math"));
    }

    private static Inst plus() {
        return Machine.read(f("/m/inst/plus").rng(f("/m/int")).dom(f("/m/int"))).as();
    }

    private static Inst asUriInt() {
        return Machine.read(f("/m/inst/as").rng(f("/m/uri")).dom(f("/m/int"))).as();
    }
    
    @Test
    @Disabled
    public void testIntFamilyShipsItsAlgebra() {
        // the declarations ship with the Int registrations — read the shipped algebra back through ?catq
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int>>form", "mapper");
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int>>law", "[commutative,right_distributive,action]");
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int>>analysis>>inverse", "/m/inst/minus?rng=/m/int&dom=/m/int");
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int>>analysis>>position", "[incomparable,retract]");
        checkCodeParseApply(LOG, "*/m/inst/sum?int<=int{*}>>form", "reducer");
        checkCodeParseApply(LOG, "*/m/inst/sum?int<=int{*}>>law", "[monoidic,commutative,right_distributive]");
        checkCodeParseApply(LOG, "*/m/inst/gt?bool<=int>>form", "mapper");
        checkCodeParseApply(LOG, "*/m/inst/gt?bool<=int>>law", "[right_distributive]");
        checkCodeParseApply(LOG, "*/m/inst/minus?int<=int>>analysis>>inverse", "/m/inst/plus?rng=/m/int&dom=/m/int");
        checkCodeParseApply(LOG, "*/m/inst/neg?int<=int>>law", "[involution]");
        checkCodeParseApply(LOG, "*/m/inst/neg?int<=int>>analysis>>inverse", "/m/inst/neg?rng=/m/int&dom=/m/int");
        checkCodeParseApply(LOG, "*/m/inst/zero?int<=int>>law", "[absorbing,idempotent]");
        // the wrapped insts must still apply — regression pins for the wrap mechanics
        checkCodeParseApply(LOG, "1.plus(2)", "3");
        checkCodeParseApply(LOG, "{1,2,3}.sum()", "6");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "position % plus % [incomparable,retract]  % a self-loop retract contested by the as-casts into int",
            "position % asUriInt % [incomparable,coupling] % int and uri cast into each other, and int's other casts are disjoint from uri",
    }, delimiter = '%')
    void testPosition(final String compute, final String inst, final String expected, final String why) {
        final Inst target = "plus".equals(inst) ? plus() : asUriInt();
        checkEquality(LOG, catGraph.position(target), expectedLst(expected), true);
    }

    private static Lst expectedLst(final String csv) {
        return lst(java.util.Arrays.stream(csv.substring(1, csv.length() - 1).split(","))
                .map(String::trim).map(s -> (Obj) uri(f(s))).toList());
    }

    @Test
    public void testContestedNamesTheWitnesses() {
        final Lst witnesses = catGraph.contested(plus());
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/bool"))), bool(true), true);
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/real"))), bool(true), true);
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/uri"))), bool(true), true);
    }

    @Test
    @Disabled
    public void testOrbitSpansIntsReversibleComponent() {
        final Obj orbit = catGraph.orbit(plus());
        for (final String member : new String[]{"int", "bool", "bytes", "real", "str", "uri", "datetime"})
            checkEquality(LOG, bool(orbit.stream().map(o -> o.asRec().at(OBJ).asType()).anyMatch(o -> o.vid().name().equals(member))), bool(true), true);
        // degenerate vertices never join the orbit — only named types are vertices
        checkEquality(LOG, bool(orbit.stream().noneMatch(o -> o.type().vid().name().equals("noobj"))), bool(true), true);
        checkEquality(LOG, bool(orbit.stream().noneMatch(o -> o.type().vid().name().equals("#"))), bool(true), true);
    }

    @Test
    @Disabled
    public void testFamilyCountsTheSiblingRegistrations() {
        checkEquality(LOG, jnt(catGraph.family(plus()).lstValue().size()), jnt(13), true);
    }

    @Test
    @Disabled
    public void testCastSubgraphAuditAndImplicitCasts() {
        // the as-graph audit + implicit-cast manifestation, harvested from AsGraphTest
        final Set<catAudit.Finding> findings = catAudit.check();
        final Map<Position, Integer> kinds = new HashMap<>();
        for (final catAudit.Finding finding : findings) {
            kinds.compute(finding.kind(), (a, b) -> null == b ? 1 : b + 1);
            if (!finding.kind().equals(Position.incomparable))
                LOG.warn(finding);
        }
        LOG.warn("TYPES OF FINDINGS: %s", kinds);

        final List<Inst> implicit = catAudit.implicit();
        for (final Inst inst : implicit)
            LOG.warn("implicit: %s", inst);
    }

    /**
     * 2b. The labels checkAsGraph reports for those same sibling pairs, on a graph we control: three
     * casts into one rng (human), from three incomparable doms. 2a showed the value sets genuinely
     * overlap, so AMBIGUOUS is the semantically right answer for each pair — the question this test
     * pins down is whether the label is reached for the right reason.
     */
    @Disabled
    @ParameterizedTest
    @TestData(value = {
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/human::T[?[name=>?str::T.has('eau')]]@/m/type_test/french",
            "/m/type_test/human::T[?[name=>?str::T.has('an')]]@/m/type_test/nordic",
            "as?rng=/m/type_test/human&dom=/m/type_test/swedish(/m/type_test/human::T)@/m/type_test/as/3",
            "as?rng=/m/type_test/human&dom=/m/type_test/french(/m/type_test/human::T)@/m/type_test/as/7",
            "as?rng=/m/type_test/human&dom=/m/type_test/nordic(/m/type_test/human::T)@/m/type_test/as/17"})
    @CsvSource(value = {
            // siblingA                        % siblingB                       % label
            "/m/type_test/swedish              % /m/type_test/french            % AMBIGUOUS",
            "/m/type_test/swedish              % /m/type_test/nordic            % AMBIGUOUS",
            "/m/type_test/french               % /m/type_test/nordic            % AMBIGUOUS"},
            delimiter = '%')
    public void testTypeTestSiblingLabels(final String siblingA, final String siblingB, final String label) {
        final List<String> labels = catAudit.check().stream()
                .filter(v -> null != v.kind() && v.insts().size() > 1)
                .filter(v -> v.insts().stream().anyMatch(i -> i.tid().dom().basePath().equals(f(siblingA)))
                        && v.insts().stream().anyMatch(i -> i.tid().dom().basePath().equals(f(siblingB))))
                .map(v -> v.kind().name().toUpperCase())
                .distinct()
                .toList();
        LOG.warn("sibling pair %s / %s => %s", siblingA, siblingB, labels);
        assertTrue(labels.contains(label), siblingA + " / " + siblingB + " should be labelled " + label
                + " but was labelled " + labels);
    }

    /// //////////////////////////////////////////////////////////////
    // the data model — objects and morphisms, exercised as mtron expressions.
    // the type constructors are the lift: .as(object::T) / .as(morphism::T).

    /**
     * The vertex block: {@code obj} is the down-elevator to the source type, {@code morphed_to} is the
     * dom-side morphism stream (the OUT edges).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "noobj::T.as(object::T)>>obj                      % noobj",
            "noobj::T.as(object::T).morphed_from().count()    % 0",
            "int::T.as(object::T)>>obj                        % int::T",
            "str::T.as(object::T)>>obj                        % str::T",
            "int::T.as(object::T)>>obj                        % int::T",
            "int::T.as(object::T).morphed_to().count().gt(1)    % true",
    }, delimiter = '%')
    void testObjects(final String expr, final String expected) {
        checkCodeParseApply(LOG, expr, expected);
    }

    /**
     * The edge block: {@code form} (the n-tid coefficient shape), {@code inverse} (the opposing edge), and
     * {@code position} (where the edge sits in the graph).
     */
    @ParameterizedTest
    @CsvSource(value = {
            // form
            "|plus?int<=int(int::T).as(morphism::T)>>form    % mapper",
            "|mult?int<=int(int::T).as(morphism::T)>>form    % mapper",
            "|gt?bool<=int(int::T).as(morphism::T)>>form     % mapper",
            "|sum?int<=int{*}().as(morphism::T)>>form        % reducer",
            // inverse
            "|plus?int<=int(int::T).as(morphism::T)>>analysis>>inverse  % /m/inst/minus?rng=/m/int&dom=/m/int",
            "|mult?int<=int(int::T).as(morphism::T)>>analysis>>inverse  % /m/inst/div?rng=/m/int&dom=/m/int",
            "|minus?int<=int(int::T).as(morphism::T)>>analysis>>inverse % /m/inst/plus?rng=/m/int&dom=/m/int",
            "|div?int<=int(int::T).as(morphism::T)>>analysis>>inverse   % /m/inst/mult?rng=/m/int&dom=/m/int",
            "|neg?int<=int().as(morphism::T)>>analysis>>inverse         % /m/inst/neg?rng=/m/int&dom=/m/int",
            // position
            //  "|plus?int<=int(int::T).as(morphism::T)>>analysis>>position % [incomparable,retract]",
    }, delimiter = '%')
    @Disabled
    void testMorphisms(final String expr, final String expected) {
        checkCodeParseApply(LOG, expr, expected);
    }

    /**
     * The structural laws (the theory recs: ring/group/monoid) — not wired yet, {@code mInstSetLawTable.typeLaws} /
     * {@code catWrap(Type, …)} is still a no-op.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "|int::T.as(object::T)>>law>>ring_theory % [add=>plus?int<=int,mul=>mult?int<=int,zero=>zero?int<=int,one=>one?int<=int]",
    }, delimiter = '%')
    @Disabled
    void testObjectLaws(final String expr, final String expected) {
        checkCodeParseApply(LOG, expr, expected);
    }

    /**
     * nat vs int — the reasoner distinguishes a rig (nat: no additive inverse) from a ring (int: additive
     * group via neg). Both borrow the same plus/mult, but nat models rig_theory + an additive monoid (no
     * inv), while int models ring_theory + an additive group (inv = neg).
     */
    @ParameterizedTest
    @CsvSource(value = {
            // the theory roles hold !* (auto_from) pointers to the live operations — the graph is the refs, not a copy
            "nat::T.as(object::T)>>law>>rig         % rig_theory::[add=>!*/m/inst/plus?rng=/m/int&dom=/m/int,mul=>!*/m/inst/mult?rng=/m/int&dom=/m/int,zero=>0,one=>1]",
            "nat::T.as(object::T)>>law>>add_monoid  % monoid_theory::[op=>!*/m/inst/plus?rng=/m/int&dom=/m/int,id=>0]",
            // "nat::T.as(object::T)>>law>>add_group   % noobj",
            //  "nat::T.as(object::T)>>law>>ring        % noobj",
            "int::T.as(object::T)>>law>>ring        % ring_theory::[add=>!*/m/inst/plus?rng=/m/int&dom=/m/int,mul=>!*/m/inst/mult?rng=/m/int&dom=/m/int,zero=>0,one=>1]",
            "int::T.as(object::T)>>law>>add_group   % group_theory::[op=>!*/m/inst/plus?rng=/m/int&dom=/m/int,id=>0,inv=>!*/m/inst/neg?rng=/m/int&dom=/m/int]",
    }, delimiter = '%')
    void testNatVsInt(final String expr, final String expected) {
        checkCodeParseApply(LOG, expr, expected);
    }

    /**
     * The process laws — the {@code declared ∩ process} cell, served from {@link mInstSetLawTable}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // "*plus?int<=int.take(1).as(morphism::T)>>law   % [commutative,right_distributive,action]",
            //"|mult?int<=int(int::T).as(morphism::T)>>law     % [commutative,right_distributive,action]",
            "|minus?int<=int(int::T).as(morphism::T)>>law    % [action]",
            "|gt?bool<=int(int::T).as(morphism::T)>>law      % [right_distributive]",
            "|div?int<=int(int::T).as(morphism::T)>>law      % [action]",
            "|sum?int<=int{*}().as(morphism::T)>>law         % [monoidic,commutative,right_distributive]",
            "|prod?int<=int{*}().as(morphism::T)>>law        % [monoidic,commutative,right_distributive]",
    }, delimiter = '%')
    void testMorphismLaws(final String expr, final String expected) {
        checkCodeParseApply(LOG, expr, expected);
    }

    /**
     * The set-theoretic class — the {@code class} cell, computed conservatively from the declared laws
     * (an involution is its own inverse, hence iso; an iso is both a section and a retraction, hence mono
     * and epi; a proper non-split mono/epi is not yet detected).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "|neg?int<=int(int::T).as(morphism::T)>>class   % [endo,iso,auto,section,retraction,mono,epi]",
            "|plus?int<=int(int::T).as(morphism::T)>>class  % [endo]",
            "|div?int<=int(int::T).as(morphism::T)>>class   % [endo]",
            "|gt?bool<=int(int::T).as(morphism::T)>>class   % noobj",
            "|mean?real<=int{*}().as(morphism::T)>>class    % noobj",
    }, delimiter = '%')
    @Disabled
    void testMorphismClass(final String expr, final String expected) {
        checkCodeParseApply(LOG, expr, expected);
    }

    /**
     * The cat-instset rewrites alone, applied to a fixpoint — scoped to {@link catInstSet} so the logical
     * rules are proved without {@code /m}'s heuristic rewrites doing the work. The compiled form stays in
     * the chain's pre-resolution shape, which is what the expectations below name.
     */
    private static Code catRewrite(final Code code) {
        final AtomicReference<Code> rewrittenCode = new AtomicReference<>(code);
        int hash = code.hashCode();
        int done = 2;
        while (done != 0) {
            Machine.current().memory().spaces()
                    .elements()
                    .filter(r -> r.second() instanceof catInstSet)
                    .flatMap(r -> r.second().<InstSet>as().rewrites().stream())
                    .forEach(r -> {
                        final Obj rewritten = r.apply(rewrittenCode.get());
                        STATIC_LOG.warn(Graphitty.strip("%s: %s => %s".formatted(r, rewrittenCode.get(), rewritten)));
                        if (rewritten.isCode())
                            rewrittenCode.set(rewritten.asCode());
                    });
            if (hash == (hash = rewrittenCode.get().hashCode()))
                done--;
        }
        return rewrittenCode.get();
    }

    /**
     * {@code theory_unit_removal} — the unit law of every theory the operand's type models: {@code op(id)}
     * drops out, and a bare identity operation (the code ring's {@code id}) is the identity morphism.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // int ring — add/zero = 0, mul/one = 1
            "5.plus(0)          % start(5)          % 5",
            "5.mult(1)          % start(5)          % 5",
            "5.plus(0).mult(1)  % start(5)          % 5",
            "5.plus(0).mult(3)  % start(5).mult(3)  % 15",
            "5.plus(2).plus(0)  % start(5).plus(2)  % 7",
            "-5.mult(1)         % start(-5)         % -5",
            // str concat_monoid — op/plus, id = ""
            "\"ab\".plus(\"\")    % start(\"ab\")      % \"ab\"",
            // bool rig — add/zero = false, mul/one = true
            "true.plus(false)   % start(true)       % true",
            "true.mult(true)    % start(true)       % true",
            // code ring — the identity operation itself is the identity morphism
            "5.id()             % start(5)          % 5",
    }, delimiter = '%')
    public void testUnitRemoval(final String code, final String expected, final String expectedResult) throws Exception {
        final Code firstStage = ObjmtronSerializer.parse(code);
        // an untouched twin: firstStage is the object handed to the rewriter, and this one is what it was parsed as
        final Code pristine = ObjmtronSerializer.parse(code);
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = catRewrite(firstStage).tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        assertEquals(secondStage, compilation);
        // (1) THE REWRITER DOES NOT MUTATE ITS INPUT — measured and passing, so the suspect is NOT the fixpoint loop
        // damaging firstStage: what was parsed keeps the shape it was parsed with. The failure is in EVALUATION.
        // NOTE the expectations are the chain's PRE-RESOLUTION shape (see catRewrite's javadoc), so a COMPILED form is
        // not comparable to secondStage — a compiler probe here fires on nearly every row instead of the four that
        // fail, which is a worse signal than none. Any further probe must be a VALUE comparison, not a shape one.
        assertEquals(pristine.insts(), firstStage.insts(),
                () -> String.format("the rewriter mutated the code it was handed: parsed %s, left %s", pristine, firstStage));
        // (2) THE PARSED CODE, and (3) an untouched twin of it — both must mean expectedResult.
        // THESE ROWS PIN A GAP-TOLERANT SEQUENCER. A rewrite that removes an inst from the MIDDLE of a chain leaves
        // the survivors' POSITIONAL vids at their old indices (measured: 5.plus(3).zero() compiles to
        // [start@<0>, zero@<2>], 5.neg().neg().plus(2) to [start@<0>, plus@<3>]). Code.nextInst therefore takes the
        // next vid GREATER than the current one rather than current+1: with vid+1 the sequence ended at the gap and
        // the chain silently evaluated as its PREFIX (the seed 5 instead of the law's 0, or 5 instead of 7). The rows
        // whose rewrite removed a SUFFIX were contiguous all along and could not catch it.
        assertEquals(result, firstStage.apply(noobj()));
        assertEquals(result, pristine.apply(noobj()),
                () -> String.format("%s does not mean %s when evaluated as parsed (%s)", code, expectedResult, firstStage));
    }

    /**
     * {@code explain()} REPORTS THE COMPILED CHAIN. The int ring's unit law — catInstSet's own
     * {@code theory_unit_removal} — drops {@code plus(0)}, so the reported metadata and table must be that
     * collapsed chain, not the expression as written. This is the case the base-ISA explain rows cannot reach:
     * {@code mInstSetTest} mounts no cat laws, so nothing there collapses and the two stages coincide.
     * <p>
     * The SHIPPED schedule is installed for this one test: rewrite BEFORE resolve. This class's
     * {@link CatTheoryCompiler} resolves first, and under that order the unit law's guard — written against the
     * pre-resolution chain, where the operand type comes from the run rather than from resolved endpoints — does
     * not fire at all, so the staging is simply not observable through it.
     */
    @Test
    public void testExplainReportsTheCompiledChain() {
        Machine.current().compiler(BasicCompiler.defaults());
        checkCodeParseApply(LOG, "6.plus(0).explain()>>desc>>insts", "1");
        // the rewrite chain is WIRED: desc.rewrite is the pre-analysis stage's explanation rec (it used to be
        // hardcoded noobj, which left the compiled stage unreachable), and that rec is the chain's last
        final Obj submitted = ObjmtronSerializer.eval("6.plus(0).explain()>>desc>>rewrite");
        assertFalse(submitted.isNoObj(), () -> "desc.rewrite should carry the pre-analysis stage's rec: " + submitted);
        assertTrue(submitted.isRec(), () -> "desc.rewrite should be an explanation::T rec: " + submitted);
        assertTrue(ObjmtronSerializer.eval("6.plus(0).explain()>>desc>>rewrite>>desc>>rewrite").isNoObj(),
                "the pre-analysis rec terminates the rewrite chain");
        assertTrue(ObjmtronSerializer.eval("6.plus(0).explain()>>desc>>rewrite>>desc>>insts").asInt().intValue() > 1,
                "the pre-analysis stage still carries the chain the laws collapsed");
    }

    /**
     * {@code theory_involution} — any operation declared {@code involution} (period two) collapses an
     * adjacent pair to identity, not just the additive group's {@code neg}. The carrier comes from the
     * seed (an argless involution has no arg to derive the type from).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.neg().neg()          % start(5)          % 5",
            "5.neg().neg().plus(2)  % start(5).plus(2)  % 7",
            "5.plus(2).neg().neg()  % start(5).plus(2)  % 7",
            "5.neg()                % start(5).neg()    % -5",
            "-5.neg().neg()         % start(-5)         % -5",
            // the generalization — every declared involution, not only neg
            "\"ab\".reverse().reverse()  % start(\"ab\")  % \"ab\"",
            "true.not().not()       % start(true)       % true",
            "2.0.inv().inv()        % start(2.0)        % 2.0",
    }, delimiter = '%')
    public void testInvolutions(final String code, final String expected, final String expectedResult) throws Exception {
        final Code firstStage = ObjmtronSerializer.parse(code);
        // an untouched twin: firstStage is the object handed to the rewriter, and this one is what it was parsed as
        final Code pristine = ObjmtronSerializer.parse(code);
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = catRewrite(firstStage).tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        assertEquals(secondStage, compilation);
        // (1) THE REWRITER DOES NOT MUTATE ITS INPUT — measured and passing, so the suspect is NOT the fixpoint loop
        // damaging firstStage: what was parsed keeps the shape it was parsed with. The failure is in EVALUATION.
        // NOTE the expectations are the chain's PRE-RESOLUTION shape (see catRewrite's javadoc), so a COMPILED form is
        // not comparable to secondStage — a compiler probe here fires on nearly every row instead of the four that
        // fail, which is a worse signal than none. Any further probe must be a VALUE comparison, not a shape one.
        assertEquals(pristine.insts(), firstStage.insts(),
                () -> String.format("the rewriter mutated the code it was handed: parsed %s, left %s", pristine, firstStage));
        // (2) THE PARSED CODE, and (3) an untouched twin of it — both must mean expectedResult.
        // THESE ROWS PIN A GAP-TOLERANT SEQUENCER. A rewrite that removes an inst from the MIDDLE of a chain leaves
        // the survivors' POSITIONAL vids at their old indices (measured: 5.plus(3).zero() compiles to
        // [start@<0>, zero@<2>], 5.neg().neg().plus(2) to [start@<0>, plus@<3>]). Code.nextInst therefore takes the
        // next vid GREATER than the current one rather than current+1: with vid+1 the sequence ended at the gap and
        // the chain silently evaluated as its PREFIX (the seed 5 instead of the law's 0, or 5 instead of 7). The rows
        // whose rewrite removed a SUFFIX were contiguous all along and could not catch it.
        assertEquals(result, firstStage.apply(noobj()));
        assertEquals(result, pristine.apply(noobj()),
                () -> String.format("%s does not mean %s when evaluated as parsed (%s)", code, expectedResult, firstStage));
    }

    /**
     * {@code law_idempotent} — an adjacent pair of the same operation declared {@code idempotent} is that
     * operation ({@code f·f ↦ f}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "\"ab\".ucase().ucase()          % start(\"ab\").ucase()    % \"AB\"",
            "false.plus(true).plus(true)    % start(false).plus(true) % true",
            "2.zero().zero()                % start(2).zero()          % 0",
    }, delimiter = '%')
    public void testIdempotent(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * {@code law_absorbing} — an operation declared {@code absorbing} is a constant of the chain
     * ({@code f ∘ g = f}), so everything before it is dead code.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.plus(3).zero()    % start(5).zero()   % 0",
            "5.mult(2).zero()    % start(5).zero()   % 0",
            "5.plus(3).plus(4).zero()  % start(5).zero()  % 0",
    }, delimiter = '%')
    public void testAbsorbing(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * {@code law_monoidic} — a fold of a fold is the fold ({@code reducer}/{@code join} form, declared
     * {@code monoidic}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.sum().sum()     % start({1,2,3}).sum()    % 6",
            "{1,2,3}.prod().prod()   % start({1,2,3}).prod()   % 6",
    }, delimiter = '%')
    public void testMonoidic(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * {@code law_poset} — adjacent comparisons of an ordered type compose to the stricter single bound.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "\"m\".gt(\"a\").gt(\"z\")  % start(\"m\").gt(\"z\")  % false",
            "\"m\".lt(\"z\").lt(\"a\")  % start(\"m\").lt(\"a\")  % false",
    }, delimiter = '%')
    public void testPoset(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * {@code inverse_cancellation} — an adjacent declared inverse pair with equal operands cancels to the
     * identity (the group law {@code f·f⁻¹ = 1}, read off {@code Entry.inverse}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.plus(3).minus(3)     % start(5)   % 5",
            "6.mult(2).div(2)       % start(6)   % 6",
    }, delimiter = '%')
    public void testInverseCancellation(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * {@code form_map_unwrap} — the stream lift {@code map} is transparent on the value it wraps, so a
     * mapping of an instruction is that instruction.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.map(neg)     % start(5).neg()    % -5",
            "5.map(neg).map(neg)  % start(5)    % 5",
    }, delimiter = '%')
    public void testMapUnwrap(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * The lift's unfolding — {@code map} whose argument is an instruction ({@code map(*0)}) must unwrap for
     * the enclosing chain to resolve, which is {@code form_map_unwrap}'s job. Only the rows that require
     * that unfolding live here; a plain {@code map(+2)} resolves without the category.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4}.inst(_,+1,+2){ map(*0).plus(*1).plus(*2) }   % {6,9,12,15}",
    }, delimiter = '%')
    public void testMapUnfolding(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * {@code derivation_contraction} — folds a derived instruction's primitive composition back to the
     * derived op: {@code plus·neg ↦ minus}, {@code mult·inv ↦ div}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "6.plus(3.neg())                                % 6.minus(3)                                     % 3",
            "6.plus(3.neg())                                % start(6).minus(3)                              % 3",
            "start(6).plus(3.neg())                         % start(6).minus(3)                              % 3",
            // real — both derivations (minus = plus·neg, div = mult·inv)
            "6.0.plus(3.0.neg())                            % start(6.0).minus(3.0)                          % 3.0",
            "6.0.mult(2.0.inv())                            % start(6.0).div(2.0)                            % 3.0",
            // cmplx — both derivations over the field (the derived op is a lst::T.poly)
            "cmplx::[1.0,2.0].plus(cmplx::[3.0,4.0].neg())  % start(cmplx::[1.0,2.0]).minus(cmplx::[3.0,4.0])  % cmplx::[-2.0,-2.0]",
            "cmplx::[1.0,2.0].mult(cmplx::[1.0,1.0].inv())  % start(cmplx::[1.0,2.0]).div(cmplx::[1.0,1.0])    % cmplx::[1.5,0.5]",
    }, delimiter = '%')
    public void testDerivationContraction(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * {@code branch_collapse} — the additive like-term law on the branch, the code ring's additive op: equal
     * members of a branch collapse to the single member with the summed coefficients (x + x = 2·x),
     * first-occurrence order preserved — the additive normal form the follow-on distributivity rewrite consumes.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // a duplicate pair among three collapses; the distinct survivor keeps its position
            "1+2.branch(+2,+4,+2)  % start(1).plus(2).branch(plus{2}(2),plus(4))      % {{2}5,7}",
            // a survivor before the pair stays before it
            "1+2.branch(+4,+2,+2)  % start(1).plus(2).branch(plus(4),plus{2}(2))      % {{2}5,7}",
            // three equal members sum to the coefficient 3, and the single survivor then inlines (branch_inline)
            "1+2.branch(+2,+2,+2)  % start(1).plus(2).plus{3}(2)                     % {3}5",
            // no equal members — the law is the identity on distinct addition
            "1+2.branch(+2,+4,+8)  % start(1).plus(2).branch(plus(2),plus(4),plus(8)) % {5,7,11}",
            // like terms at different multiplicities — the coefficient is summed, not part of the identity
            "1.branch({3}10,{2}10,13)  % start(1).branch({5}10,13)  % {{5}10,13}",
            // mixed: a code pair and an inst trio each sum their own like terms, the distinct singletons
            // keep their positions, and with four members left the branch stays (no inline)
            "1.branch(+2.+2,plus{2}(2).+2,plus(2),plus{4}(2),plus(3),plus(2),mult(10))  % start(1).branch(plus{3}(2).plus(2),plus{6}(2),plus(3),mult(10))  % {{3}5,{6}3,4,10}",
    }, delimiter = '%')
    public void testBranchCollapse(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * {@code branch_collapse} on chain members — equal CODES collapse like equal single insts, but the
     * expected shape (a collapsed code carrying the summed coefficient) has no mtron sugar to parse back,
     * so pin it by structure and value instead: the branch loses its duplicate member, and the stream it
     * still produces is the one the law promises.
     */
    @Test
    public void testBranchCollapseCodeMembers() {
        final Code code = ObjmtronSerializer.parse("1.branch(2.neg(),4.neg(),2.neg())");
        final Inst branch = catRewrite(code).insts().stream()
                .filter(i -> BRANCH_INST_TID.equals(i.tid().basePath()))
                .findFirst().orElseThrow();
        // three members with a duplicate pair, collapsed to two
        assertEquals(2, branch.args().count());
        // the duplicate collapsed to the code carrying the summed coefficient at its head inst
        assertEquals(ObjmtronSerializer.parse("start(1).branch(map{2}(2).neg(),map(4).neg())").asCode().insts().get(1), branch);
        // the collapse is stream-transparent: -2 twice, -4 once
        assertEquals(ObjmtronSerializer.parse("{{2}-2,-4}"), code.apply(noobj()));
    }

    /**
     * {@code branch_inline} — the single-addend law: a branch whose members have come to one unwraps its
     * frame (a.branch(b).c |-> a.b.c), on its own for a hand-written lone branch and on what
     * {@code branch_collapse} left behind (a.branch(b,b).c |-> a.b{2}.c). Stream-transparent in every shape.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // a lone hand-written branch unwraps, no identical pair needed
            "1.branch(+2)      % start(1).plus(2)        % 3",
            // a lone chain member unwraps by its insts
            "1.branch(+2.neg()) % start(1).plus(2).neg()  % -3",
            // a non-call member unwraps as the map that emits it
            "1.branch(2)       % start(1).map(2)          % 2",
            // the collapse's single survivor is inlined with its summed coefficient
            "1.branch(+2,+2)   % start(1).plus{2}(2)      % {2}3",
            // the collapse's chain survivor is inlined with its summed coefficient at the head inst
            "1.branch(2.neg(),2.neg())  % start(1).map{2}(2).neg()  % {2}-2",
            // two distinct members stay branched
            "1.branch(+2,+4)   % start(1).branch(plus(2),plus(4))   % {3,5}",
            // an inst member and a code member that start the same are distinct — no collapse
            "1.branch(+2,+2.+2)  % start(1).branch(plus(2),plus(2).plus(2))  % {3,5}",
            // a fully-duplicate chain pair collapses, then inlines
            "1.branch(+2.+2,+2.+2)  % start(1).plus{2}(2).plus(2)  % {2}5",
            // like chains already carrying head coefficients sum their multiplicities (1 + 10)
            "1.branch(+2.+2,plus{10}(2).+2)  % start(1).plus{11}(2).plus(2)  % {11}5",
            // a fully-duplicate scalar pair inlines into the map that emits the summed term
            "1.branch(10,10)      % start(1).map({2}10)  % {2}10",
            "1.branch({2}10,{3}10)  % start(1).map({5}10)  % {5}10",
    }, delimiter = '%')
    public void testBranchInline(final String code, final String expected, final String expectedResult) throws Exception {
        rewriteAssert(code, expected, expectedResult);
    }

    /**
     * The shared rewrite assertion: the cat-only fixpoint compiles the code to {@code expected}, and the
     * code still evaluates to {@code expectedResult}.
     */
    /**
     * The two laws at a FIXPOINT driven through the rewriter itself — the rules are flat on the top-level
     * chain, so a branch living inside a member is rewritten bottom-up by the rewriter (the rules see it only
     * once it has reduced itself), and the like terms are summed in the pass after: branch(+2,
     * branch(+2, plus{3}(2))) |-> branch(+2, plus{4}(2)) |-> plus{5}(2).
     */
    @ParameterizedTest
    @CsvSource(value = {
            // the member's own branch (1 + 3) reduces to its term, which then sums with its twin (1) to 5
            "1.branch(+2,branch(+2,plus{3}(2)))    % start(1).plus{5}(2)  % {5}3",
            // two equal NESTED branches are like terms too, once each is reduced — multiplicity 2 + 2 = 4
            "1.branch(branch(+2,+2),branch(+2,+2))  % start(1).plus{4}(2)  % {4}3",
    }, delimiter = '%')
    public void testBranchFixpoint(final String code, final String expected, final String expectedResult) {
        final Code firstStage = ObjmtronSerializer.parse(code);
        final Call compilation = FixPointRewriter.single().apply(firstStage).tryToInst();
        assertEquals(ObjmtronSerializer.parse(expected), compilation);
        assertEquals(ObjmtronSerializer.parse(expectedResult), compilation.apply(noobj()));
    }

    private static void rewriteAssert(final String code, final String expected, final String expectedResult) {
        final Code firstStage = ObjmtronSerializer.parse(code);
        // an untouched twin: firstStage is the object handed to the rewriter, and this one is what it was parsed as
        final Code pristine = ObjmtronSerializer.parse(code);
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = catRewrite(firstStage).tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        assertEquals(secondStage, compilation);
        // (1) THE REWRITER DOES NOT MUTATE ITS INPUT — measured and passing, so the suspect is NOT the fixpoint loop
        // damaging firstStage: what was parsed keeps the shape it was parsed with. The failure is in EVALUATION.
        // NOTE the expectations are the chain's PRE-RESOLUTION shape (see catRewrite's javadoc), so a COMPILED form is
        // not comparable to secondStage — a compiler probe here fires on nearly every row instead of the four that
        // fail, which is a worse signal than none. Any further probe must be a VALUE comparison, not a shape one.
        assertEquals(pristine.insts(), firstStage.insts(),
                () -> String.format("the rewriter mutated the code it was handed: parsed %s, left %s", pristine, firstStage));
        // (2) THE PARSED CODE, and (3) an untouched twin of it — both must mean expectedResult.
        // THESE ROWS PIN A GAP-TOLERANT SEQUENCER. A rewrite that removes an inst from the MIDDLE of a chain leaves
        // the survivors' POSITIONAL vids at their old indices (measured: 5.plus(3).zero() compiles to
        // [start@<0>, zero@<2>], 5.neg().neg().plus(2) to [start@<0>, plus@<3>]). Code.nextInst therefore takes the
        // next vid GREATER than the current one rather than current+1: with vid+1 the sequence ended at the gap and
        // the chain silently evaluated as its PREFIX (the seed 5 instead of the law's 0, or 5 instead of 7). The rows
        // whose rewrite removed a SUFFIX were contiguous all along and could not catch it.
        assertEquals(result, firstStage.apply(noobj()));
        assertEquals(result, pristine.apply(noobj()),
                () -> String.format("%s does not mean %s when evaluated as parsed (%s)", code, expectedResult, firstStage));
    }
}
