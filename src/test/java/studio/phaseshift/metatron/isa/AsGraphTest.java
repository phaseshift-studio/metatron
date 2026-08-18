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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Exercises the {@code as} instruction graph — both the explicit ambiguity checker
 * ({@link Inst.Helper#checkAsGraph()}) and the manifested implicit ancestor casts
 * ({@link Inst.Helper#implicitAsGraph()}).
 */
public class AsGraphTest extends AbstractObjTest {

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
    }

    @Test
    public void testAsValidation() {
        final Set<Inst.Helper.Violation> violations = Inst.Helper.checkAsGraph();
        final Map<Inst.Helper.Violation.Type, Integer> types = new HashMap<>();
        for (final Inst.Helper.Violation v : violations) {
            types.compute(v.type(), (a, b) -> null == b ? 1 : b + 1);
            if (!v.type().equals(Inst.Helper.Violation.Type.INCOMPARABLE))
                LOG.warn(v);
        }
        LOG.warn("TYPES OF VIOLATIONS: %s", types);

        final List<Inst> implicit = Inst.Helper.implicitAsGraph();
        for (final Inst inst : implicit) {
            LOG.warn("implicit: %s", inst);
        }
    }
}
