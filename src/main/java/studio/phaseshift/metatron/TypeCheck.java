/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum TypeCheck {
    type_cons,
    obj_write,
    inst_rng,
    inst_dom;

    private static final Set<TypeCheck> TYPE_CHECKS = new LinkedHashSet<>(List.of(values()));

    public boolean enabled() {
        return TYPE_CHECKS.contains(this);
    }

    public static void enable(final TypeCheck... stage) {
        TYPE_CHECKS.addAll(List.of(stage));
    }

    public static void disable(final TypeCheck... stage) {
        List.of(stage).forEach(TYPE_CHECKS::remove);
    }

    public static int level() {
        return TYPE_CHECKS.size();
    }
    
    public static Set<TypeCheck> getEnabled() {
        return new LinkedHashSet<>(TYPE_CHECKS);
    }

    public static String colorLevel() {
        final int level = level();
        if (level == 4)
            return "g";
        else if (level == 3)
            return "y";
        else if (level == 2)
            return "r";
        else if (level == 1)
            return "k";
        else if (level == 0)
            return "w";
        else
            throw MTronException.of("invalid type check level: %d", level);
    }

    public static boolean check(final TypeCheck stage) {
        return TYPE_CHECKS.contains(stage);
    }
}
