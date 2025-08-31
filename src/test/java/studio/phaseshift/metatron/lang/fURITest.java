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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class fURITest {

    @Test
    public void testScheme() {
        assertEquals("http", new fURI("http://fhatos.org/b").scheme());
        assertNull(new fURI("a/b/c/d").scheme());
    }

    @Test
    public void testHostOrSegment() {
        assertEquals("fhatos.org", new fURI("http://fhatos.org/b").hostOrSegment());
        assertEquals("a", new fURI("a/b/c/d").hostOrSegment());
    }

    @Test
    public void testPrepend() {
        assertEquals(new fURI("http://fhatos.org/a/b"), new fURI("http://fhatos.org/b").prepend("a"));
        assertEquals(new fURI("http://fhatos.org/a/b/c/d"), new fURI("http://fhatos.org/d").prepend("a/b/c"));

    }

    @Test
    public void testExtend() {
        assertEquals(new fURI("http://fhatos.org/a/b"), new fURI("http://fhatos.org/a").extend("b"));
        assertEquals(new fURI("http://fhatos.org/a/b/c/d"), new fURI("http://fhatos.org/a").extend("b/c/d"));
        assertEquals(new fURI("http://fhatos.org/a/b/d"), new fURI("http://fhatos.org/a").extend("b/./d"));

    }

    @Test
    public void testIsAbsolute() {
        assertTrue(new fURI("http://fhatos.org/a").isAbsolute());
        assertTrue(new fURI("http://fhatos.org").isAbsolute());
        assertFalse(new fURI("a/b").isAbsolute());
        assertTrue(new fURI("/a/b").isAbsolute());
        assertTrue(new fURI("/a/+/b").isAbsolute());
        assertTrue(new fURI("/a/+/#").isAbsolute());
        assertFalse(new fURI("a/+/b").isAbsolute());
        assertFalse(new fURI("a/+/#").isAbsolute());
    }

    @Test
    public void testRetract() {
        assertEquals(new fURI("http://fhatos.org/"), new fURI("http://fhatos.org/a").retract(1));
        assertEquals(new fURI("http://fhatos.org/a"), new fURI("http://fhatos.org/a/b").retract(1));
        assertEquals(new fURI("http://fhatos.org/"), new fURI("http://fhatos.org/a/b").retract(2));
        assertEquals(new fURI("http://fhatos.org/"), new fURI("http://fhatos.org/a/b").retract(3));
        ///
        assertEquals(new fURI("http://fhatos.org:4500/"), new fURI("http://fhatos.org:4500/a").retract(1));
        assertEquals(new fURI("http://fhatos.org:4500/a"), new fURI("http://fhatos.org:4500/a/b").retract(1));
        assertEquals(new fURI("http://fhatos.org:4500/"), new fURI("http://fhatos.org:4500/a/b").retract(2));
        assertEquals(new fURI("http://fhatos.org:4500/"), new fURI("http://fhatos.org:4500/a/b").retract(3));
        ///
        assertEquals(new fURI("/fhatos.org/a"), new fURI("/fhatos.org/a/b").retract(1));
        assertEquals(new fURI("/fhatos.org/a"), new fURI("/fhatos.org/a/b").retract(1));
        assertEquals(new fURI("fhatos.org/a"), new fURI("fhatos.org/a/b").retract(1));
    }

    @Test
    public void testPretract() {
        assertEquals(new fURI("http://fhatos.org/"), new fURI("http://fhatos.org/a").pretract(1));
        assertEquals(new fURI("http://fhatos.org/b"), new fURI("http://fhatos.org/a/b").pretract(1));
        assertEquals(new fURI("http://fhatos.org/"), new fURI("http://fhatos.org/a/b").pretract(2));
        assertEquals(new fURI("http://fhatos.org/"), new fURI("http://fhatos.org/a/b").pretract(3));
        ///
        assertEquals(new fURI("http://fhatos.org:4500/"), new fURI("http://fhatos.org:4500/a").pretract(1));
        assertEquals(new fURI("http://fhatos.org:4500/b"), new fURI("http://fhatos.org:4500/a/b").pretract(1));
        assertEquals(new fURI("http://fhatos.org:4500/"), new fURI("http://fhatos.org:4500/a/b").pretract(2));
        assertEquals(new fURI("http://fhatos.org:4500/"), new fURI("http://fhatos.org:4500/a/b").pretract(3));
        ///
        assertEquals(new fURI("/a/b"), new fURI("/fhatos.org/a/b").pretract(1));
        assertEquals(new fURI("a/b"), new fURI("fhatos.org/a/b").pretract(1));
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
        assertTrue(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/#")));
        assertFalse(new fURI("http://fhatos.org/a/b/c").matches(new fURI("http://fhatos.org/b/#")));
        assertFalse(new fURI("b").matches(new fURI("/sys/#")));
        assertFalse(new fURI("/sys/#").matches(new fURI("b")));
        assertTrue(new fURI("b").matches(new fURI("b/#")));
        assertFalse(new fURI("b").matches(new fURI("b/c")));
        assertFalse(new fURI("b").matches(new fURI("b/c/+")));
        assertFalse(new fURI("b").matches(new fURI("b/c/#")));
        assertFalse(new fURI("b").matches(new fURI("b/+")));
        assertFalse(new fURI("a").matches(new fURI("b/c/+")));
        assertFalse(new fURI("a").matches(new fURI("b/c/#")));
        ///
        assertTrue(new fURI("/a/b/c").matches(new fURI("/a/b/+")));
        assertTrue(new fURI("/a/b/c").matches(new fURI("/a/+/c")));
        assertTrue(new fURI("/a/b/c").matches(new fURI("/a/b/#")));
        assertTrue(new fURI("/a/b/c").matches(new fURI("/a/#")));
        assertTrue(new fURI("/a/b/c").matches(new fURI("#")));
        ///
        assertTrue(new fURI("a/b/c").matches(new fURI("a/b/+")));
        assertTrue(new fURI("a/b/c").matches(new fURI("a/+/c")));
        assertTrue(new fURI("a/b/c").matches(new fURI("a/b/#")));
        assertTrue(new fURI("a/b/c").matches(new fURI("a/#")));
        assertTrue(new fURI("a/b/c").matches(new fURI("#")));
        ///
        assertFalse(new fURI("a/b/c").matches(new fURI("/a/b/+")));
        assertFalse(new fURI("a/b/c").matches(new fURI("/a/+/c")));
        assertFalse(new fURI("a/b/c").matches(new fURI("/a/b/#")));
        assertFalse(new fURI("a/b/c").matches(new fURI("/a/#")));
        assertFalse(new fURI("a/b/c").matches(new fURI("/#")));

    }

    @Test
    public void testQuery() {
        assertEquals(Map.of("a", "1", "b", "2"), fURI.of("http://meta.tron/query?a=1&b=2").query());
        assertEquals(Map.of("a", "", "b", "2"), fURI.of("http://meta.tron/query?a&b=2").query());
        assertEquals(Map.of(), fURI.of("http://meta.tron/query").query());
        assertEquals(Map.of("sub", ""), fURI.of("http://meta.tron/query?sub").query());
        //  assertEquals(fURI.of("http://meta.tron/query?a=1&b=2"), fURI.of("http://meta.tron/query").query(Map.of("a", "", "b", "2")));
    }
}
