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

import studio.phaseshift.metatron.furi.fURI;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JRecElement {
    String key();

    String dom() default "#{?}";

    String rng() default "#{*}";
    
    Mimic mimic() default Mimic.FIELD;

    String typecast() default "";

    public static enum Mimic {
        FIELD, METHOD
    }
    
    class Helper {
        public static fURI getDom(JRecElement annotation) {
            return "#{?}".equals(annotation.dom()) ? null : f(annotation.dom());
        }

        public static fURI getRng(JRecElement annotation) {
            return "#{*}".equals(annotation.rng()) ? null : f(annotation.rng());
        }
    }
}
