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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The STATEFUL sibling of {@code @CsvSource}. Each entry is a script LINE, run in order by
 * {@link StatefulCSVExtension}, not an independent row — so a machine can be pushed/popped mid-script and every
 * observation is checked as it happens. This is what a plain {@code @CsvSource} cannot express: mutation.
 * <p>
 * Line forms (see {@code AbstractMetatronTest#script(String...)}):
 * <pre>
 *   [STATE] &lt;mtron&gt;            mutate machine state (push, pop, write, …)
 *   // &lt;comment&gt;               ignored
 *   &lt;code&gt; % &lt;expected&gt;        checkCodeParseApply(code, expected)
 *   &lt;lhs&gt; % &lt;rhs&gt; % &lt;equals&gt;   checkEquality(eval(lhs), eval(rhs), equals)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StatefulCSVSource {

    /**
     * The script lines, evaluated in order.
     */
    String[] value();
}
