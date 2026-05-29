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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;

/**
 * A metatron global type-checking configuration for specifying varying levels of type-safety.
 */
public enum TypeCheck {
    /**
     * require type constructor match for obj creation
     */
    type_ctor,
    /**
     * require obj type match for space write
     */
    obj_write,
    /**
     * require inst range type match post-evaluation
     */
    inst_rng,
    /**
     * require inst domain type match pre-evaluation
     */
    inst_dom,
    /**
     * require code to be fully resolved pre-evaluation
     */
    code_resolve;

    private static final Set<TypeCheck> TYPE_CHECKS = new LinkedHashSet<>(List.of(values()));

    public static void init(final Rec typer) {
        /*TYPE_CHECKS.clear();
        typer.elements().filter(r -> r.second().boolValue()).map(Rel::first).forEach(stages -> {
            TYPE_CHECKS.add(TypeCheck.valueOf(stages.uriValue().toString()));
        });*/
    }

    public boolean enabled() {
        return TYPE_CHECKS.contains(this);
    }

    public static void enable(final TypeCheck... stages) {
        TYPE_CHECKS.addAll(List.of(stages));
        /*for (final TypeCheck stage : stages) {
            Router.writeToSpace(f("/sys/typer/stage/" + stage.name()), BOOL_TRUE);
        }*/
    }

    public static void disable(final TypeCheck... stages) {
        List.of(stages).forEach(TYPE_CHECKS::remove);
        /*for (final TypeCheck stage : stages) {
            Router.writeToSpace(f("/sys/typer/stage/" + stage.name()), BOOL_FALSE);
        }*/
    }

    public static int level() {
        return TYPE_CHECKS.size();
    }

    public static Set<TypeCheck> getEnabled() {
        TypeCheck.init(Router.readFromSpace("/sys/typer/stage").asRec());
        return new LinkedHashSet<>(TYPE_CHECKS);
    }

    public static String colorLevel() {
        final int level = level();
        if (level == 5)
            return "g";
        else if (level == 4)
            return "y";
        else if (level == 3)
            return "m";
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
