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

import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A metatron global tracing configuration for surfacing internal Java diagnostics.
 */
public enum Tracer {

    /**
     * Render the Java stack trace on {@code fail()} objects.
     */
    stack;

    private static final Set<Tracer> ACTIVE = new LinkedHashSet<>(List.of(values()));

    public static void init(final Rec config) {
        ACTIVE.clear();
        Stream.of(Tracer.values()).filter(t -> config.at(uri(t.name())).orElse(BOOL_FALSE).boolValue()).forEachOrdered(ACTIVE::add);
    }

    public boolean enabled() {
        return ACTIVE.contains(this);
    }

    public static void enable(final Tracer... stages) {
        ACTIVE.addAll(List.of(stages));
        for (final Tracer stage : stages)
            Router.writeToSpace(f("/sys/tracer/stage").extend(stage.name()), BOOL_TRUE);
    }

    public static void disable(final Tracer... stages) {
        List.of(stages).forEach(ACTIVE::remove);
        for (final Tracer stage : stages)
            Router.writeToSpace(f("/sys/tracer/stage").extend(stage.name()), BOOL_FALSE);
    }

    public static Set<Tracer> getEnabled() {
        return new LinkedHashSet<>(ACTIVE);
    }
}
