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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.m.type.impl.MType;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * TEMPORARY diagnostic -- compare memoized vs fresh type resolution.
 */
public class TypeGraphProbeTest extends AbstractMetatronTest {

    @Test
    public void probe() {
        for (final String name : new String[]{"uri", "datetime", "int", "docs"}) {
            final fURI path = f(name);
            final Type memo = MType.T(path);
            final Obj fresh = Router.readFromSpace(path.big());
            final Type freshType = fresh.isType() ? fresh.asType() : null;
            System.out.println("[PROBE] " + name
                    + " | memo : " + (null == memo ? "null" : "vid=" + memo.vid() + " tid=" + memo.tid())
                    + " | fresh: " + (null == freshType ? "NOT-A-TYPE (" + fresh + ")" : "vid=" + freshType.vid() + " tid=" + freshType.tid())
                    + " | same=" + (null != freshType && freshType.equals(memo)));
        }
        final int size = TypeGraph.global().size();
        System.out.println("[PROBE] graph size = " + size);
        assertTrue(true);
    }
}
