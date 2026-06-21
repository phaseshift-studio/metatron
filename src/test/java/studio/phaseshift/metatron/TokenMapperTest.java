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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Tests for {@link TokenMapper} — context-scoped bidirectional token mapping
 * with identity fallback.
 */
@DisplayName("TokenMapper")
public class TokenMapperTest {

    // -- shared contexts --
    private static final fURI CTX_A = f("/ctx/a");
    private static final fURI CTX_B = f("/ctx/b");
    private static final fURI CTX_C = f("/ctx/c");

    // -- helpers --
    private static TokenMapper mapper() {
        return new TokenMapper();
    }

    private static TokenMapper sample() {
        return new TokenMapper()
                .add(CTX_A, "name", "fullName")
                .add(CTX_A, "age", "years")
                .add(CTX_B, "name", "toolName")
                .add(CTX_B, "args", "arguments");
    }

    ///////////////////////////////////////////////////////////////////////////
    // add + getTo / getFrom
    ///////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("add / getTo / getFrom")
    class AddAndGet {

        @ParameterizedTest(name = "[{index}] ctx={0} from={1} -> to={2}  (reverse: {2} -> {1})")
        @CsvSource(value = {
            "/ctx/a % name % fullName  % /ctx/a fullName query maps back",
            "/ctx/a % age  % years    % /ctx/a years maps back to age",
            "/ctx/b % name % toolName % /ctx/b toolName maps back to name",
            "/ctx/b % args % arguments % /ctx/b arguments maps back to args",
        }, delimiter = '%')
        void testAddThenGetBothDirections(String ctxPath, String from, String to, String description) {
            final fURI ctx = f(ctxPath);
            final TokenMapper m = new TokenMapper().add(ctx, from, to);

            assertEquals(Optional.of(to), m.getTo(ctx, from), "getTo");
            assertEquals(Optional.of(from), m.getFrom(ctx, to), "getFrom");
        }

        @Test
        @DisplayName("chained add returns same mapper")
        void testChaining() {
            final TokenMapper m = new TokenMapper()
                    .add(CTX_A, "a", "A")
                    .add(CTX_B, "b", "B");

            assertEquals(Optional.of("A"), m.getTo(CTX_A, "a"));
            assertEquals(Optional.of("B"), m.getTo(CTX_B, "b"));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Identity fallback (no mapping registered)
    ///////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("identity fallback")
    class IdentityFallback {

        @ParameterizedTest(name = "[{index}] ctx={0} token={1} -> empty ({2})")
        @CsvSource(value = {
            "/ctx/a % color  % unknown token in known context",
            "/ctx/b % age    % token mapped in ctx_a but not in ctx_b",
            "/ctx/x % name   % unknown context",
        }, delimiter = '%')
        void testGetToReturnsEmpty(String ctxPath, String from, String description) {
            final TokenMapper m = sample();
            final fURI ctx = f(ctxPath);
            assertEquals(Optional.empty(), m.getTo(ctx, from), "getTo for '" + from + "' in " + ctx + " should be empty");
        }

        @ParameterizedTest(name = "[{index}] ctx={0} external={1} -> empty ({2})")
        @CsvSource(value = {
            "/ctx/a % toolName % external from ctx_b not present in ctx_a",
            "/ctx/b % fullName  % external from ctx_a not present in ctx_b",
            "/ctx/x % name      % unknown context",
        }, delimiter = '%')
        void testGetFromReturnsEmpty(String ctxPath, String external, String description) {
            final TokenMapper m = sample();
            final fURI ctx = f(ctxPath);
            assertEquals(Optional.empty(), m.getFrom(ctx, external), "getFrom for '" + external + "' in " + ctx + " should be empty");
        }

        @ParameterizedTest(name = "[{index}] to({0}, {1}) -> {2} ({3})")
        @CsvSource(value = {
            "/ctx/a % name     % fullName  % mapped",
            "/ctx/a % color    % color     % identity: token returned as-is",
            "/ctx/a % toolName % toolName  % identity: external name returned as-is",
            "/ctx/x % name     % name      % unknown context: identity fallback",
        }, delimiter = '%')
        void testTo_method(String ctxPath, String token, String expected, String description) {
            final TokenMapper m = sample();
            final fURI ctx = f(ctxPath);
            assertEquals(expected, m.to(ctx, token));
        }

        @ParameterizedTest(name = "[{index}] from({0}, {1}) -> {2} ({3})")
        @CsvSource(value = {
            "/ctx/a % fullName  % name       % mapped back",
            "/ctx/a % color     % color      % identity: unknown external returned as-is",
            "/ctx/b % toolName  % name       % mapped back (different context)",
            "/ctx/b % arguments % args       % mapped back",
            "/ctx/x % toolName  % toolName   % unknown context: identity fallback",
        }, delimiter = '%')
        void testFrom_method(String ctxPath, String external, String expected, String description) {
            final TokenMapper m = sample();
            final fURI ctx = f(ctxPath);
            assertEquals(expected, m.from(ctx, external));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Same token, different mappings per context (the whole point)
    ///////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("context-scoped semantics")
    class ContextScoped {

        /**
         * The defining use case: "name" maps to "fullName" in context A
         * but to "toolName" in context B.  Same token, different API boundary.
         */
        @ParameterizedTest(name = "[{index}] {0}: name -> {1} ({2})")
        @CsvSource(value = {
            "/ctx/a % fullName  % context A: name is a person's full name",
            "/ctx/b % toolName  % context B: name refers to a tool",
        }, delimiter = '%')
        void testNameMeansDifferentThings(String ctxPath, String expected, String description) {
            final TokenMapper m = sample();
            final fURI ctx = f(ctxPath);
            assertEquals(expected, m.to(ctx, "name"));
            assertEquals("name", m.from(ctx, expected));
        }

        @Test
        @DisplayName("context isolation: mapping in one context does not affect another")
        void testContextIsolation() {
            final TokenMapper m = sample();
            assertEquals(Optional.of("fullName"), m.getTo(CTX_A, "name"));
            assertEquals(Optional.of("toolName"), m.getTo(CTX_B, "name"));
            assertEquals(Optional.empty(), m.getTo(CTX_C, "name"));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Override / last-write-wins
    ///////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("last-write-wins override")
    class Override {

        @Test
        @DisplayName("re-adding same context+from overrides previous mapping")
        void testOverride() {
            final TokenMapper m = new TokenMapper()
                    .add(CTX_A, "name", "fullName")
                    .add(CTX_A, "name", "displayName");

            assertEquals(Optional.of("displayName"), m.getTo(CTX_A, "name"));
            assertEquals(Optional.of("name"), m.getFrom(CTX_A, "displayName"));
            assertEquals(Optional.empty(), m.getFrom(CTX_A, "fullName"));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Empty / no-registrations
    ///////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("empty mapper")
    class Empty {

        @Test
        @DisplayName("fresh mapper returns Optional.empty() for all lookups")
        void testEmptyMapper() {
            final TokenMapper m = mapper();
            assertEquals(Optional.empty(), m.getTo(CTX_A, "anything"));
            assertEquals(Optional.empty(), m.getFrom(CTX_A, "anything"));
        }

        @Test
        @DisplayName("to/from with empty mapper return identity")
        void testEmptyMapperIdentity() {
            final TokenMapper m = mapper();
            assertEquals("anything", m.to(CTX_A, "anything"));
            assertEquals("anything", m.from(CTX_A, "anything"));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Round-trip
    ///////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("round-trip")
    class RoundTrip {

        @ParameterizedTest(name = "[{index}] {0}: to->from round-trip ({1})")
        @CsvSource(value = {
            "/ctx/a % name % fullName  % person context",
            "/ctx/b % name % toolName  % tool context",
            "/ctx/b % args % arguments % params context",
            "/ctx/a % age  % years     % age context",
        }, delimiter = '%')
        void testToFromRoundTrip(String ctxPath, String token, String expectedExternal, String description) {
            final TokenMapper m = sample();
            final fURI ctx = f(ctxPath);
            final String external = m.to(ctx, token);
            assertEquals(expectedExternal, external);
            assertEquals(token, m.from(ctx, external));
        }

        @ParameterizedTest(name = "[{index}] {0}: from->to round-trip ({1})")
        @CsvSource(value = {
            "/ctx/a % fullName  % name  % person context",
            "/ctx/b % toolName  % name  % tool context",
            "/ctx/b % arguments % args  % params context",
            "/ctx/a % years     % age   % age context",
        }, delimiter = '%')
        void testFromToRoundTrip(String ctxPath, String external, String expectedToken, String description) {
            final TokenMapper m = sample();
            final fURI ctx = f(ctxPath);
            final String token = m.from(ctx, external);
            assertEquals(expectedToken, token);
            assertEquals(external, m.to(ctx, token));
        }
    }
}
