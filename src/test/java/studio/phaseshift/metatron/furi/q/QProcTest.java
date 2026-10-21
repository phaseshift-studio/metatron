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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Addressable;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;

import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/*
 * The q-proc test harness: it is a test of a SOURCE, not of a space. A processor is declared on whatever
 * {@link Addressable} the test names and the layer is driven through it, so the same cases run against a space
 * (memSpace, miot, mqtt) and against a source that is not a place at all.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface QProcTest {

    /**
     * the source under test — a space, a network, or any other addressable that can declare q-procs. A space
     * test answers it by overriding {@link #getSpace()}; a source that is not a place overrides this instead.
     */
    default Addressable getSource() {
        return this.getSpace();
    }

    /**
     * the space under test, for the cases that need a space's own affordances (pattern, direct writer)
     *
     * @throws MTronException when the source under test is not a space
     */
    default Space getSpace() {
        final Addressable source = this.getSource();
        if (source instanceof Space space)
            return space;
        throw MTronException.of("%s is not a space: this case must address its source as an Addressable", source.pattern());
    }

    /**
     * Declare a processor by its tid, so a case can then use its {@code ?q}.
     *
     * @param tid the processor type to declare
     * @return the source, for chaining
     */
    default Addressable attachQ(final fURI tid) {
        final Addressable source = this.getSource();
        if (source.qs().lstValue().stream().noneMatch(x -> x.tid().equals(tid))) {
            // a source need not be an Obj (see Addressable.isResolved), but the ones that can be handed a test
            // harness's expressions are, and those are the ones worth naming in the log
            if (source instanceof Obj obj)
                obj.logger().warn("manually adding %s to %s", tid.name(), obj.vidOrTid());
            source.addQ(rec(new LinkedHashMap<Obj, Obj>(), tid, null).as());
        }
        return source;
    }

    String make(final String expression);
}
