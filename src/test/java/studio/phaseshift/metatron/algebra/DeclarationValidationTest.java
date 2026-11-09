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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.DECQ;
import static studio.phaseshift.metatron.furi.q.QCollection.DECS_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.MOD_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.THEORY_FORM_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A DECLARATION IS ADMITTED ONLY WHEN IT IS SOUND ({@code Law.Helper.validate}, Phase 3): a {@code ?decq} write whose
 * laws are not all {@code law::T} (the union of the vocabulary's labels), or whose derivation cannot run both ways, is
 * refused with a fail before the store holds it, and the declaration already there stays. Each row rewrites the
 * {@code law} and {@code derivation} facets of int {@code mod}'s real declaration and restores it afterwards; the
 * derivations name instructions no chain contains, so no other test can see them fire.
 */
public class DeclarationValidationTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // a law is a law::T, the union of the declared labels (Marko, 2026-11-08)
            "[commutative,pure]  %                     %                                  % true",
            "[not_a_law]         %                     %                                  % false",
            "[pure,'pure']       %                     %                                  % false",
            // law::T's own address claims every law; a lone label is no lst of laws
            "</m/law>            %                     %                                  % true",
            "pure                %                     %                                  % false",
            // a derivation runs both ways: its lhs ends in the instruction it defines and its sides declare the same names
            "                    % mod(#::T@b)         % siftveil(#::T@b).quarrymark()    % true",
            "                    % mod()               % siftveil().quarrymark()          % true",
            "                    % mod(#::T@b)         % siftveil(#::T@c).quarrymark()    % false",
            "                    % mod(#::T@b)         % siftveil().quarrymark()          % false",
            "                    % siftveil(#::T@b)    % mod(#::T@b)                      % false",
            // half an equation is no equation
            "                    % mod(#::T@b)         %                                  % false",
    }, delimiter = '%')
    void testADeclarationIsAdmittedOnlyWhenSound(final String laws, final String lhs, final String rhs, final boolean admitted) {
        final fURI address = Inst.Helper.operationTid(MOD_INST_TID.dom(INT_TID).rng(INT_TID)).addQ(DECQ);
        final Obj original = Machine.read(address);
        assertTrue(original.isRec(), "int mod declares its laws, got " + original);
        final Map<Obj, Obj> claim = mutableMap(original.asRec().jvm());
        claim.remove(uri(DERIVATION));
        if (null != laws)
            claim.put(uri(LAW), ObjmtronSerializer.parse(laws));
        if (null != lhs || null != rhs)
            claim.put(uri(DERIVATION), rec(mutableMap(
                    uri(LHS), null == lhs ? noobj() : code(ObjmtronSerializer.<Call>parse(lhs)),
                    uri(RHS), null == rhs ? noobj() : code(ObjmtronSerializer.<Call>parse(rhs)))));
        final Obj declaration = rec(claim, DECS_TID, null);
        try {
            final Obj written = Machine.write(address, declaration);
            if (admitted) {
                assertFalse(written.isFail(), "a sound declaration is admitted, got " + written);
                assertEquals(declaration, Machine.read(address), "and the store holds it");
            } else {
                assertTrue(written.isFail(), "an unsound declaration is refused with a fail, got " + written);
                assertEquals(original, Machine.read(address), "and the declaration already there stays");
            }
        } finally {
            Machine.write(address, noobj());
            Machine.write(address, original);
        }
    }

    /**
     * A THEORY FORM IS ONE OF theory_form::T's (Marko, 2026-11-08): a theory with several op roles is keyed by its own
     * name, a one-op theory by the instruction family its op binds, {@code add} for plus and {@code mul} for mult. The
     * type is read back by its address, so a row also proves it is registered there.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ring                    % true",
            "near_ring               % true",
            "add_monoid              % true",
            "mul_group               % true",
            "add_commutative_monoid  % true",
            "free_groupoid           % true",
            // a one-op theory's bare name says nothing of the instruction its op binds
            "monoid                  % false",
            // mult is the instruction; mul is the theory's role
            "mult_monoid             % false",
            // what plus does on a carrier is the instruction's business, not the theory's
            "concat_monoid           % false",
            "merge_monoid            % false",
            // a str is no uri
            "'ring'                  % false",
    }, delimiter = '%', quoteCharacter = '~')
    void testATheoryFormIsOneOfTheUnions(final String form, final boolean admitted) {
        final Type forms = T(THEORY_FORM_TID);
        assertTrue(forms.toString().contains("union("), "theory_form::T states its forms as a union, got " + forms);
        assertEquals(admitted, !forms.apply(ObjmtronSerializer.parse(form)).isNoObj(), form + " as a theory_form::T");
    }

    /**
     * A DECLARATION'S THEORIES ARE KEYED BY theory_form::T: each row writes str's own declaration back with its monoid
     * re-keyed under the row's keys. One key outside the union refuses the whole declaration, and the one already there
     * stays; a theory block that is no rec is refused too.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[add_monoid]                % true",
            "[mul_monoid]                % true",
            "[add_monoid,ring]           % true",
            "[mult_monoid]               % false",
            "[concat_monoid]             % false",
            "[add_monoid,concat_monoid]  % false",
            "['add_monoid']              % false",
            // the theories are a rec keyed by form: a lone form is none
            "add_monoid                  % false",
    }, delimiter = '%', quoteCharacter = '~')
    void testADeclarationsTheoriesAreKeyedByForm(final String forms, final boolean admitted) {
        final fURI address = STR_TID.addQ(DECQ);
        final Obj original = Machine.read(address);
        assertTrue(original.isRec(), "str declares its algebra, got " + original);
        final Obj monoid = original.asRec().atDirect(uri(THEORY)).asRec().atDirect(uri("add_monoid"));
        assertTrue(monoid.isRec(), "str models an add_monoid, got " + original);
        final Obj keys = ObjmtronSerializer.parse(forms);
        final Map<Obj, Obj> theories = new LinkedHashMap<>();
        if (keys.isLst())
            keys.lstValue().forEach(key -> theories.put(key, monoid));
        final Map<Obj, Obj> claim = mutableMap(original.asRec().jvm());
        claim.put(uri(THEORY), keys.isLst() ? rec(theories) : keys);
        final Obj declaration = rec(claim, DECS_TID, null);
        try {
            final Obj written = Machine.write(address, declaration);
            if (admitted) {
                assertFalse(written.isFail(), "a declaration keyed by forms is admitted, got " + written);
                assertEquals(declaration, Machine.read(address), "and the store holds it");
            } else {
                assertTrue(written.isFail(), "a declaration keyed by anything else is refused with a fail, got " + written);
                assertEquals(original, Machine.read(address), "and the declaration already there stays");
            }
        } finally {
            Machine.write(address, noobj());
            Machine.write(address, original);
        }
    }
}
