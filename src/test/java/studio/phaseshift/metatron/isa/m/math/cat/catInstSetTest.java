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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
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
        checkEquality(LOG, catInstSet.position(target), expectedLst(expected), true);
    }

    private static Lst expectedLst(final String csv) {
        return lst(java.util.Arrays.stream(csv.substring(1, csv.length() - 1).split(","))
                .map(String::trim).map(s -> (Obj) uri(f(s))).toList());
    }

    @Test
    public void testContestedNamesTheWitnesses() {
        final Lst witnesses = catInstSet.contested(plus());
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/bool"))), bool(true), true);
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/real"))), bool(true), true);
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/uri"))), bool(true), true);
    }

    @Test
    @Disabled
    public void testOrbitSpansIntsReversibleComponent() {
        final Obj orbit = catInstSet.orbit(plus());
        for (final String member : new String[]{"int", "bool", "bytes", "real", "str", "uri", "datetime"})
            checkEquality(LOG, bool(orbit.stream().map(o -> o.asRec().at(OBJ).asType()).anyMatch(o -> o.vid().name().equals(member))), bool(true), true);
        // degenerate vertices never join the orbit — only named types are vertices
        checkEquality(LOG, bool(orbit.stream().noneMatch(o -> o.type().vid().name().equals("noobj"))), bool(true), true);
        checkEquality(LOG, bool(orbit.stream().noneMatch(o -> o.type().vid().name().equals("#"))), bool(true), true);
    }

    @Test
    @Disabled
    public void testFamilyCountsTheSiblingRegistrations() {
        checkEquality(LOG, jnt(catInstSet.family(plus()).lstValue().size()), jnt(13), true);
    }

    @Test
    @Disabled
    public void testCastSubgraphAuditAndImplicitCasts() {
        // the as-graph audit + implicit-cast manifestation, harvested from AsGraphTest
        final Set<catInstSet.Finding> findings = catInstSet.check();
        final Map<catInstSet.Position, Integer> kinds = new HashMap<>();
        for (final catInstSet.Finding finding : findings) {
            kinds.compute(finding.kind(), (a, b) -> null == b ? 1 : b + 1);
            if (!finding.kind().equals(catInstSet.Position.incomparable))
                LOG.warn(finding);
        }
        LOG.warn("TYPES OF FINDINGS: %s", kinds);

        final List<Inst> implicit = catInstSet.implicit();
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
        final List<String> labels = catInstSet.check().stream()
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
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = catRewrite(firstStage).tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        assertEquals(secondStage, compilation);
        assertEquals(result, firstStage.apply(noobj()));
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
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = catRewrite(firstStage).tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        assertEquals(secondStage, compilation);
        assertEquals(result, firstStage.apply(noobj()));
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
     * The shared rewrite assertion: the cat-only fixpoint compiles the code to {@code expected}, and the
     * code still evaluates to {@code expectedResult}.
     */
    private static void rewriteAssert(final String code, final String expected, final String expectedResult) {
        final Code firstStage = ObjmtronSerializer.parse(code);
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = catRewrite(firstStage).tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        assertEquals(secondStage, compilation);
        assertEquals(result, firstStage.apply(noobj()));
    }
}
