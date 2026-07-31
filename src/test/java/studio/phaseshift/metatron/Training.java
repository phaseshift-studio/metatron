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
import java.util.ArrayList;
import java.util.List;

 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Training {

    record Run(String desc, String mapDesc, int lhs, int rhs) {
        public static List<Run> runs(final Training training) {
            final List<Run> runs = new ArrayList<>();
            if (training.map1()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[0], training.map1()[0], training.map1()[1]));
            }
            if (training.map2()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[1], training.map2()[0], training.map2()[1]));
            }
            if (training.map3()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[2], training.map3()[0], training.map3()[1]));
            }
            return runs;
        }
    }







}
