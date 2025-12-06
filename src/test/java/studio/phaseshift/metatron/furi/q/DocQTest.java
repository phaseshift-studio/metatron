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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.InstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;

import static studio.phaseshift.metatron.Tokens.DESC;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocQTest extends MetatronTest {

    public void analyzeDocs(final InstSet instSet) {
        for (final Inst inst : instSet.insts()) {
            Obj doc = instSet.read(inst.tid().qLess().cLess().query("doc"));
            // LOG.info("HERE %s:", doc.type());
            if (doc.c().equals(cInt.ONE())) {
                LOG.warn("%s has no associated documentation %s", inst, doc.<DocQ.Doc>as().at(DESC));
            }
        }
    }
}
