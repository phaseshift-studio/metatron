/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class fURITest {

    @Test
    public void testExtend() {
        assertEquals(new fURI("http://fhatos.org/a/b"), new fURI("http://fhatos.org/a").extend("b"));
        assertEquals(new fURI("http://fhatos.org/a/b/c/d"), new fURI("http://fhatos.org/a").extend("b/c/d"));

    }

    @Test
    public void testMatches() {
        assertTrue(new fURI("http://fhatos.org/a").matches(new fURI("http://fhatos.org/a")));
        assertFalse(new fURI("http://fhatos.org/a").matches(new fURI("http://fhatos.org/a/b")));
        assertFalse(new fURI("http://fhatos.org/a/b").matches(new fURI("http://fhatos.org/a")));
        ///
        assertTrue(new fURI("http://fhatos.org/a/b").matches(new fURI("http://fhatos.org/a/+")));
        assertTrue(new fURI("http://fhatos.org/a/b").matches(new fURI("http://fhatos.org/a/#")));
        assertTrue(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/a/#")));
        assertTrue(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/a/+/c")));
        assertTrue(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/a/+/+")));
        assertTrue(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/+/+/+")));
        ///
        assertFalse(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/+/c/+")));
        assertFalse(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/+/b")));
        assertFalse(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.com/a/b/c")));
        ///
        assertTrue(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://+/a/b/c")));
        assertTrue(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://#")));


    }
}
