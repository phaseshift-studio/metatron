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

package studio.phaseshift.metatron.isa.tble.schema;

import studio.phaseshift.metatron.algebra.rewrite.CommonRewrites;

/**
 * tbleSpace-specific rewrite utilities.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class SQLRewriteUtils {

    private SQLRewriteUtils() {}

    /** SQL-style condition formatting. */
    public static final CommonRewrites.ConditionFormatter CONDITION_FORMATTER =
            new CommonRewrites.ConditionFormatter() {
                @Override
                public String equality(final String field, final String value) {
                    return field + " = " + value;
                }
                @Override
                public String comparison(final String field, final CommonRewrites.ComparisonOp op, final String value) {
                    return field + " " + op.symbol() + " " + value;
                }
                @Override
                public String exists(final String field) {
                    return field + " IS NOT NULL";
                }
                @Override
                public String escapeLiteral(final String s) {
                    return s.replace("'", "''");
                }
            };

    /** SQL-style conjunction: {@code a AND b AND c}. */
    public static final CommonRewrites.PredicateJoiner PREDICATE_JOINER =
            conds -> String.join(" AND ", conds);
}
