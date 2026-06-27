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

package studio.phaseshift.metatron.isa.tble;

import studio.phaseshift.metatron.furi.fURI;

import java.util.List;

/**
 * SQL-generation utilities for key-value store ({@code kv_store}) queries.
 *
 * <p>Translates URI patterns with single-segment ({@code +}) and
 * multi-segment ({@code *}) wildcards into SQL {@code LIKE}/{@code NOT LIKE}
 * clauses that match the stored {@code furi} column format (no leading slash,
 * space prefix stripped via {@code routeFromSpace}).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class KVStoreUtil {

    private KVStoreUtil() {}

    /**
     * Translate a URI pattern to a SQL WHERE clause for {@code kv_store}.
     * Returns {@code null} when the pattern is too complex for SQL translation.
     *
     * @param pattern the URI pattern (already aligned to stored-URI format)
     * @return a SQL WHERE clause string, or {@code null}
     */
    public static String translateKVPatternToSQL(final fURI pattern) {
        return buildKVSQL(pattern.segments());
    }

    /**
     * Core translation: path segments (with {@code +} and {@code #} wildcards)
     * → SQL WHERE clause for {@code kv_store.furi}.
     */
    public static String buildKVSQL(final List<String> segs) {
        if (segs.isEmpty()) return null;

        // kv_store furi has no leading slash (routeFromSpace strips the scheme)
        final StringBuilder prefix = new StringBuilder();
        int wildStart = -1;
        int plusCount = 0;
        boolean hasStar = false;

        for (int i = 0; i < segs.size(); i++) {
            final String s = segs.get(i);
            if (isKVStar(s)) {
                if (wildStart == -1) wildStart = i;
                hasStar = true;
            } else if (isKVPlus(s)) {
                if (wildStart == -1) wildStart = i;
                plusCount++;
            } else if (wildStart == -1) {
                if (i > 0) prefix.append("/");
                prefix.append(s);
            } else {
                return null; // literal after wildcard — unsupported
            }
        }

        if (wildStart == -1) return "furi = '" + prefix + "'";

        final String safePrefix = prefix.toString().replace("'", "''");

        if (plusCount > 0 && !hasStar) {
            // Exactly N more segments
            final StringBuilder like = new StringBuilder(safePrefix);
            for (int i = 0; i < plusCount; i++) like.append("/%");
            final StringBuilder notLike = new StringBuilder(safePrefix);
            for (int i = 0; i <= plusCount; i++) notLike.append("/%");
            return "furi LIKE '" + like + "' AND furi NOT LIKE '" + notLike + "'";
        }

        if (hasStar && plusCount == 0) {
            if (prefix.length() == 0) return null; // matches everything
            return "(furi = '" + safePrefix + "' OR furi LIKE '" + safePrefix + "/%')";
        }

        // hasStar && plusCount > 0: at least N more segments
        final StringBuilder minPattern = new StringBuilder(safePrefix);
        for (int i = 0; i < plusCount; i++) minPattern.append("/%");
        return "furi LIKE '" + minPattern + "'";
    }

    private static boolean isKVPlus(final String segment) {
        return fURI.Singleton.WILD_ONE.name().equals(segment);
    }

    private static boolean isKVStar(final String segment) {
        return fURI.Singleton.ALL.name().equals(segment);
    }
}
