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

package studio.phaseshift.metatron.isa.m.type.reflect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Class-level annotation that supplies a default mtron type identity (TID)
 * for a JRec-backed class.  When a {@link JRecElement} annotation omits
 * {@code dom} or {@code rng}, the fallback resolver checks this annotation
 * and uses its {@code tid} value.
 *
 * Example:
 * <pre>{@code
 *   @JRecType(tid = "/m/sys/ui/widget/table")
 *   public class Table extends AbstractWidget<Table> {
 *       @JRecElement(key = "addRow")  // dom/rng -> class TID
 *       public Table addRow(List<Object> entries) { ... }
 *   }
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JRecType {
    String tid();
}
