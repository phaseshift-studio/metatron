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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The signature axis of a law: every {@link Law} constant answers the fURI signature of the thing it is a law about
 * ({@link Law#wrt()}) — "with respect to".
 * <p>
 * A law is a statement about a <em>shape</em> of operation on a set, so the signature is the shape and nothing else:
 * a binary operation on a carrier declares one operand type, an operation on the carrier with no operand types
 * declared declares none — that spelling is ARITY-AGNOSTIC, so it covers an element law and an operation at any
 * arity — a relation answers a {@code bool}, a fold consumes many copies, and an effect or a form law has no
 * operation on a carrier to point at.
 * <p>
 * This is a pure enum test — no VM boots, because nothing here leaves the fURI layer. The two properties that matter
 * are that the table is COMPLETE (no constant is left without a signature) and that every spelling is one the parser
 * accepts and renders back byte for byte, the {@code args=[...]} component included: these uris are meant to be
 * compared against an instruction's contract, so a spelling the parser cannot round trip would be a claim nobody could
 * read.
 */
public class LawTest {

    /**
     * The whole table, one row per constant — the audit list. Each expectation is the signature verbatim, so a
     * reclassification is a deliberate edit to a readable line rather than a silent change of behavior.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            // a binary operation on the carrier — A × A → A: the laws that read an operand equation
            "action                 % inst?dom=A&rng=A&args=[A]",
            "complement             % inst?dom=A&rng=A&args=[A]",
            "cyclic                 % inst?dom=A&rng=A&args=[A]",
            "left_distributive      % inst?dom=A&rng=A&args=[A]",
            "magmadic               % inst?dom=A&rng=A&args=[A]",
            "nilpotent              % inst?dom=A&rng=A&args=[A]",
            "right_distributive     % inst?dom=A&rng=A&args=[A]",
            "zero_divisor           % inst?dom=A&rng=A&args=[A]",
            // the carrier to itself, the arity stated only by `args` — an ELEMENT law, or a law whose claims
            // span arities: no `args` key at all is the arity-agnostic spelling
            "absorbing              % inst?dom=A&rng=A",
            "commutative            % inst?dom=A&rng=A",
            "idempotent             % inst?dom=A&rng=A",
            "involution             % inst?dom=A&rng=A",
            "monoidic               % inst?dom=A&rng=A",
            "prime                  % inst?dom=A&rng=A",
            "unit                   % inst?dom=A&rng=A",
            // a relation on the carrier — A × A → bool
            "poset                  % inst?dom=A&rng=bool&args=[A]",
            // a fold — A{*} → A: the reducer's distributivity, and the closure it iterates
            "semi_right_distributive% inst?dom=A{*}&rng=A",
            "kleene                 % inst?dom=A{*}&rng=A",
            // a form law — about an instruction's form, not an operation on a carrier
            "floatable              % inst",
            "blocked                % inst",
            "partial                % inst",
            // an effect — no carrier at all
            "pure                   % obj",
    })
    void lawAnswersItsSignature(final Law law, final String expected) {
        final fURI signature = law.wrt();
        assertNotNull(signature, law + " answers no signature");
        assertEquals(expected, signature.toString());
        assertEquals(expected, f(expected).toString());   // the table itself is parseable, spelled as written
        // the args component, which is where the operand types live, survives the parser
        final List<String> expectedArgs = expected.contains("args=[") ? List.of("A") : List.<String>of();
        assertEquals(expectedArgs, argText(signature.args()));
        assertEquals(argText(signature.args()), argText(f(signature.toString()).args()));
    }

    /**
     * The table is COMPLETE, and every entry obeys the canonical spelling rules: the carrier is the poly variable
     * {@code A} (never {@code #}, which would flip {@link fURI#hasPattern()} for a query value), no {@code args} key is
     * written when there are no arguments, and a uri carrying arguments writes exactly one {@code args=[...]} key.
     */
    @Test
    void everyLawAnswersASignature() {
        for (final Law law : Law.values()) {
            final fURI signature = law.wrt();
            assertNotNull(signature, law + " answers no signature");
            final String rendered = signature.toString();
            assertFalse(rendered.contains("#"), law + " spells its carrier with #, which flips hasPattern()");
            assertFalse(signature.hasPattern(), law + " should declare no wildcard: " + rendered);
            assertFalse(rendered.contains("args=[]"), law + " writes an empty args component: " + rendered);
            assertFalse(rendered.contains("args=]"), law + " writes a malformed args component: " + rendered);
            assertEquals(signature.hasArgs(), rendered.contains("args=["),
                    law + " carries args off the table: " + rendered);
            assertEquals(signature.hasArgs() ? 1 : 0, countArgsKeys(rendered),
                    law + " writes the wrong number of args keys: " + rendered);
            // the parser is the judge: parse, render, parse again -- every form must be stable
            final fURI parsed = f(rendered);
            assertEquals(rendered, parsed.toString(), law + " does not render back as written: " + rendered);
            assertEquals(rendered, f(parsed.toString()).toString(),
                    law + " is not stable across a second parse: " + rendered);
            assertEquals(argText(signature.args()), argText(parsed.args()),
                    law + " loses its argument types: " + rendered);
        }
    }

    /**
     * A fold is spelled with the many-copies coefficient on its DOMAIN and none on its range — the one place a
     * signature states how many of the carrier arrive, so it is asserted apart from the table's arity cases.
     */
    @Test
    void aFoldConsumesManyAndAnswersOne() {
        assertEquals("A{*}", Law.semi_right_distributive.wrt().dom().toString());
        assertEquals("A", Law.semi_right_distributive.wrt().rng().toString());
        // the copies ARE the coefficient, so the argument list is empty
        assertEquals(List.<String>of(), argText(Law.semi_right_distributive.wrt().args()));
        assertEquals("A{*}", Law.kleene.wrt().dom().toString());
        assertEquals("A", Law.kleene.wrt().rng().toString());
    }

    private static List<String> argText(final List<fURI> args) {
        return args.stream().map(fURI::toString).toList();
    }

    private static int countArgsKeys(final String furi) {
        int count = 0;
        for (final String component : furi.substring(furi.indexOf('?') + 1).split("&"))
            if (component.startsWith("args="))
                count++;
        return count;
    }
}
