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

package studio.phaseshift.metatron.isa.m.space;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.furi.q.SubQTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.PERSIST;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class memSpaceTest extends AbstractSpaceTest implements SubQTest {

    public memSpaceTest() {
        super(() -> {
            if (RANDOM.nextInt(10) < 20) {
                Graphitty.log(memSpaceTest.class).debug("deleting persisted memspace data at {{y}}/tmp/memspace-test.mtron{{X}}");
                final File file = new File("/tmp/memspace-test.mtron");
                if (file.exists())
                    file.delete();
            }
            final Space space = memSpace.of(rec(uri(PATTERN), uri("/t/#"), uri(PERSIST), uri("/tmp/memspace-test.mtron")), f("/sys/space/mem"));
            space.addQ(QCollection.subq());
            return space;
        });
    }

    @Test
    public void testPersistence() {
        File file = new File("/tmp/memspace-test.mtron");
        assert !file.exists() || file.delete();
        final memSpace space = memSpace.of(rec(
                uri(PERSIST), uri("/tmp/memspace-test.mtron"),
                uri(PATTERN), uri("/tt/#")), f("/sys/space/mem_persist_1"));
        final Map<fURI, Obj> data = generateRandomData(space.pattern().retractPattern(), 10);
        data.forEach(Router::writeToSpace);
        data.forEach((k, v) -> assertEquals(v, Router.readFromSpace(k)));
        space.close();
        final memSpace space2 = memSpace.of(rec(
                uri(PERSIST), uri("/tmp/memspace-test.mtron"),
                uri(PATTERN), uri("/tt/#")), f("/sys/space/mem_persist_2"));
        data.forEach((k, v) -> assertEquals(v, Router.readFromSpace(k)));
        space2.close();
    }
}
