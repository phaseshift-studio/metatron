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

package studio.phaseshift.metatron.space.remote;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.obj.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MStr.str;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.start;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RemoteSpaceTest extends MetatronTest {

    /* @AfterAll
    public static void end() {
        BootLoader.close();
    }*/

    @Test
    public void testRemote() {
        RemoteSpace remote = new RemoteSpace(f("ws://chibi.local:8887/usr/#"), f("/mnt/ws/chibi.local/8887/usr"));
        assertEquals(str("hello world"), start(str("hello world")).to_(uri("/usr/a")).apply());
        //final Obj obj = str("hello world").vid(f("ws://chibi.local:8887/usr/a"));
        final Obj a = remote.read("ws://chibi.local:8887/usr/a");
        assertEquals(str("hello world"), a);
        remote.close();
    }


}
