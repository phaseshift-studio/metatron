/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC 
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

package studio.phaseshift.metatron.lang.mgrph;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.mgrph.tp.MGraph;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;

import static studio.phaseshift.metatron.furi.fURI.f;

public class TinkerObjTest extends MetatronTest {

    @Test
    public void testTinkerObj() {
      /*  final MGraph graph = new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp"));
        Router.global().addSpace(graph);
        final GrphInstSet grphInstSet = new GrphInstSet(f("/grph/#"), f("/mnt/grph"));
        Router.global().addSpace(grphInstSet);
        grphInstSet.load();*/
        final MGraph graph = Router.global().read(f("/mnt/tp")).as();
        //final mgrphFluent f = g(graph).V().out().count();
        //  System.out.println(f.iterator().next());
        // g(graph).V().out().out().stream().forEach(v -> System.out.println(v.tid().coefficientValue()));

        Graphitty.log(this).info("graph: %s", graph);
        // Graphitty.log(this).info("traversal: %s", f);
        //  f.forEach(v -> Graphitty.out(System.out, "%s", v));
    }
}
