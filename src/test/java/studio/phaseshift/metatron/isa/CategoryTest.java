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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.CatQ;
import studio.phaseshift.metatron.algebra.Category;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The category-graph engine: every inst is a morphism — outV its dom, inV its rng — and
 * {@link Category} computes its position labels, contest witnesses, reversible-core orbit, and family, while
 * {@link CatQ#catWrap} composes the morphism block — declared values plus lazy compute pointers — into the
 * q-proc's store, docq-style.
 */
@Disabled("boot loader forward reference issues to fix first")
public class CategoryTest extends AbstractObjTest {

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
    }

    private static Inst plus() {
        return Router.readFromSpace(f("/m/inst/plus").rng(f("/m/int")).dom(f("/m/int"))).as();
    }

    private static Inst asUriInt() {
        return Router.readFromSpace(f("/m/inst/as").rng(f("/m/uri")).dom(f("/m/int"))).as();
    }

    @Test
    public void testIntFamilyShipsItsAlgebra() {
        // the declarations ship with the Int registrations — read the shipped algebra back through ?catq
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int&catq>>form", "mapper");
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int&catq>>law", "[commutative,right_distributive,action]");
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int&catq>>inverse", "/m/inst/minus?rng=/m/int&dom=/m/int");
        checkCodeParseApply(LOG, "*/m/inst/plus?int<=int&catq>>position", "[incomparable,retract]");
        checkCodeParseApply(LOG, "*/m/inst/sum?int<=int{*}&catq>>form", "reducer");
        checkCodeParseApply(LOG, "*/m/inst/sum?int<=int{*}&catq>>law", "[monoidic,commutative,right_distributive]");
        checkCodeParseApply(LOG, "*/m/inst/gt?bool<=int&catq>>form", "mapper");
        checkCodeParseApply(LOG, "*/m/inst/gt?bool<=int&catq>>law", "[right_distributive]");
        checkCodeParseApply(LOG, "*/m/inst/minus?int<=int&catq>>inverse", "/m/inst/plus?rng=/m/int&dom=/m/int");
        checkCodeParseApply(LOG, "*/m/inst/neg?int<=int&catq>>law", "[involution]");
        checkCodeParseApply(LOG, "*/m/inst/neg?int<=int&catq>>inverse", "/m/inst/neg?rng=/m/int&dom=/m/int");
        checkCodeParseApply(LOG, "*/m/inst/zero?int<=int&catq>>law", "[absorbing,idempotent]");
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
        checkEquality(LOG, Category.position(target), expectedLst(expected), true);
    }

    private static Lst expectedLst(final String csv) {
        return lst(java.util.Arrays.stream(csv.substring(1, csv.length() - 1).split(","))
                .map(String::trim).map(s -> (Obj) uri(f(s))).toList());
    }

    @Test
    public void testContestedNamesTheWitnesses() {
        final Lst witnesses = Category.contested(plus());
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/bool"))), bool(true), true);
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/real"))), bool(true), true);
        checkEquality(LOG, bool(witnesses.lstValue().stream().anyMatch(w -> w.uriValue().toString().contains("dom=/m/uri"))), bool(true), true);
    }

    @Test
    public void testOrbitSpansIntsReversibleComponent() {
        final Lst orbit = Category.orbit(plus());
        for (final String member : new String[]{"int", "bool", "bytes", "real", "str", "uri", "datetime"})
            checkEquality(LOG, bool(orbit.lstValue().stream().anyMatch(o -> o.uriValue().name().equals(member))), bool(true), true);
        // degenerate vertices never join the orbit — only named types are vertices
        checkEquality(LOG, bool(orbit.lstValue().stream().noneMatch(o -> o.uriValue().name().equals("noobj"))), bool(true), true);
        checkEquality(LOG, bool(orbit.lstValue().stream().noneMatch(o -> o.uriValue().name().equals("#"))), bool(true), true);
    }

    @Test
    public void testFamilyCountsTheSiblingRegistrations() {
        checkEquality(LOG, jnt(Category.family(plus()).lstValue().size()), jnt(13), true);
    }

    @Test
    public void testCatWrapStoresAndReadsBack() {
        // identical to the shipped declaration, so the test is order-independent against the family test
        CatQ.catWrap(plus(), Map.of(
                uri(Tokens.LAW), Category.laws(Category.Law.commutative, Category.Law.right_distributive, Category.Law.action),
                uri(Tokens.INVERSE), uri(Router.readFromSpace(f("/m/inst/minus").rng(f("/m/int")).dom(f("/m/int"))).tid())));
        // the read through ?catq serves the stored block
        final Obj cat = Router.readFromSpace(plus().tid().addQ("catq"));
        checkEquality(LOG, bool(cat.isNoObj()), bool(false), true);
        final Rec block = cat.asRec();
        checkEquality(LOG, block.at(uri(Tokens.FORM)), uri("mapper"), true);
        checkEquality(LOG, block.at(uri(Tokens.LAW)), Category.laws(Category.Law.commutative, Category.Law.right_distributive, Category.Law.action), true);
        // the position cell is materialized by ?catq's preRead — the compute pointer resolved on the way out
        checkEquality(LOG, block.at(uri(Tokens.POSITION)), lst(uri("incomparable"), uri("retract")), true);
    }

    @Test
    public void testCastSubgraphAuditAndImplicitCasts() {
        // the as-graph audit + implicit-cast manifestation, harvested from AsGraphTest
        final Set<Category.Finding> findings = Category.check();
        final Map<Category.Position, Integer> kinds = new HashMap<>();
        for (final Category.Finding finding : findings) {
            kinds.compute(finding.kind(), (a, b) -> null == b ? 1 : b + 1);
            if (!finding.kind().equals(Category.Position.incomparable))
                LOG.warn(finding);
        }
        LOG.warn("TYPES OF FINDINGS: %s", kinds);

        final List<Inst> implicit = Category.implicit();
        for (final Inst inst : implicit)
            LOG.warn("implicit: %s", inst);
    }
}
