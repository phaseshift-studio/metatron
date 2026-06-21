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

import studio.phaseshift.metatron.furi.fURI;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TokenMapper {

    private final Map<fURI, Map<String, String>> toMapper = new LinkedHashMap<>();
    private final Map<fURI, Map<String, String>> fromMapper = new LinkedHashMap<>();

    /**
     * Register a bidirectional token mapping within a context (e.g., a message type TID).
     * The context scopes the mapping so the same metatron token can resolve to different
     * external names depending on the API boundary.
     *
     * @param context the boundary context (e.g., a message TID like TOOL_RESULT_MESSAGE_TID)
     * @param from    the metatron token (e.g., {@code Tokens.NAME})
     * @param to      the external field name (e.g., {@code "toolName"})
     * @return this mapper, for chaining
     */
    public TokenMapper add(final fURI context, final String from, final String to) {
        final Map<String, String> toCtx = this.toMapper.computeIfAbsent(context, k -> new LinkedHashMap<>());
        final Map<String, String> fromCtx = this.fromMapper.computeIfAbsent(context, k -> new LinkedHashMap<>());
        // Remove stale reverse mapping if this from token is being overridden
        final String oldTo = toCtx.put(from, to);
        if (oldTo != null)
            fromCtx.remove(oldTo);
        fromCtx.put(to, from);
        return this;
    }

    /**
     * Resolve a metatron token to its external name in the given context.
     * Returns {@link Optional#empty()} when no mapping is registered —
     * callers should fall back to identity (the token is its own external name).
     */
    public Optional<String> getTo(final fURI context, final String from) {
        final Map<String, String> ctx = this.toMapper.get(context);
        return ctx != null ? Optional.ofNullable(ctx.get(from)) : Optional.empty();
    }

    /**
     * Resolve an external field name back to its metatron token in the given context.
     * Returns {@link Optional#empty()} when no mapping is registered —
     * callers should fall back to identity (the external name is also the token).
     */
    public Optional<String> getFrom(final fURI context, final String to) {
        final Map<String, String> ctx = this.fromMapper.get(context);
        return ctx != null ? Optional.ofNullable(ctx.get(to)) : Optional.empty();
    }

    /**
     * Convenience: resolve a metatron token → external name, with identity fallback.
     * Equivalent to {@code getTo(context, from).orElse(from)}.
     */
    public String to(final fURI context, final String from) {
        return getTo(context, from).orElse(from);
    }

    /**
     * Convenience: resolve an external name → metatron token, with identity fallback.
     * Equivalent to {@code getFrom(context, to).orElse(to)}.
     */
    public String from(final fURI context, final String to) {
        return getFrom(context, to).orElse(to);
    }
}
