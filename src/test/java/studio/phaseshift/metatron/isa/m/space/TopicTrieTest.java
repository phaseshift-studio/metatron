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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * Tests for the TopicTrie MQTT-style wildcard matching data structure.
 */
public class TopicTrieTest extends AbstractMetatronTest {

    private TopicTrie trie;

    @BeforeEach
    void setUp() {
        trie = new TopicTrie();
    }

    // ========== Basic Operations ==========

    @Test
    void testPutAndGet() {
        final fURI key = f("/sensor/kitchen/temperature");
        final Obj value = jnt(22);

        assertNull(trie.get(key));
        trie.put(key, value);
        assertEquals(value, trie.get(key));
    }

    @Test
    void testPutOverwrite() {
        final fURI key = f("/sensor/kitchen/temperature");
        final Obj value1 = jnt(22);
        final Obj value2 = jnt(25);

        trie.put(key, value1);
        assertEquals(value1, trie.get(key));

        final Obj previous = trie.put(key, value2);
        assertEquals(value1, previous);
        assertEquals(value2, trie.get(key));
    }

    @Test
    void testContainsKey() {
        final fURI key = f("/sensor/kitchen/temperature");
        assertFalse(trie.containsKey(key));

        trie.put(key, jnt(22));
        assertTrue(trie.containsKey(key));
    }

    @Test
    void testRemove() {
        final fURI key = f("/sensor/kitchen/temperature");
        final Obj value = jnt(22);

        trie.put(key, value);
        assertTrue(trie.containsKey(key));

        final Obj removed = trie.remove(key);
        assertEquals(value, removed);
        assertFalse(trie.containsKey(key));
        assertNull(trie.get(key));
    }

    @Test
    void testRemoveNonExistent() {
        assertNull(trie.remove(f("/nonexistent/path")));
    }

    @Test
    void testIsEmpty() {
        assertTrue(trie.isEmpty());

        trie.put(f("/sensor/temp"), jnt(22));
        assertFalse(trie.isEmpty());

        trie.remove(f("/sensor/temp"));
        assertTrue(trie.isEmpty());
    }

    @Test
    void testSize() {
        assertEquals(0, trie.size());

        trie.put(f("/a"), jnt(1));
        assertEquals(1, trie.size());

        trie.put(f("/b"), jnt(2));
        assertEquals(2, trie.size());

        trie.put(f("/a/b"), jnt(3));
        assertEquals(3, trie.size());

        trie.remove(f("/a"));
        assertEquals(2, trie.size());
    }

    @Test
    void testClear() {
        trie.put(f("/a"), jnt(1));
        trie.put(f("/b"), jnt(2));
        trie.put(f("/a/b/c"), jnt(3));

        assertFalse(trie.isEmpty());
        trie.clear();
        assertTrue(trie.isEmpty());
        assertEquals(0, trie.size());
    }

    // ========== Single-Level Wildcard (+) ==========

    @Test
    void testSingleLevelWildcard() {
        // Setup: /sensor/{room}/temperature for multiple rooms
        trie.put(f("/sensor/kitchen/temperature"), jnt(22));
        trie.put(f("/sensor/bedroom/temperature"), jnt(20));
        trie.put(f("/sensor/bathroom/temperature"), jnt(24));
        trie.put(f("/sensor/kitchen/humidity"), jnt(45));

        // Query: /sensor/+/temperature
        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/sensor/+/temperature"));

        assertEquals(3, results.size());
        final Set<String> furis = results.stream()
                .map(e -> e.getKey().toString())
                .collect(Collectors.toSet());

        assertTrue(furis.contains("/sensor/kitchen/temperature"));
        assertTrue(furis.contains("/sensor/bedroom/temperature"));
        assertTrue(furis.contains("/sensor/bathroom/temperature"));
        assertFalse(furis.contains("/sensor/kitchen/humidity"));
    }

    @Test
    void testSingleLevelWildcardAtStart() {
        trie.put(f("/sensor/kitchen/temperature"), jnt(22));
        trie.put(f("/actuator/kitchen/light"), jnt(1));
        trie.put(f("/config/kitchen/settings"), str("auto"));

        // Query: /+/kitchen/+
        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/+/kitchen/+"));

        assertEquals(3, results.size());
    }

    @Test
    void testSingleLevelWildcardNoMatch() {
        trie.put(f("/sensor/kitchen/temperature"), jnt(22));

        // Query for different depth - should not match
        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/sensor/+/+/extra"));

        assertTrue(results.isEmpty());
    }

    // ========== Multi-Level Wildcard (#) ==========

    @Test
    void testMultiLevelWildcardAtEnd() {
        trie.put(f("/sensor/kitchen"), jnt(1));
        trie.put(f("/sensor/kitchen/temperature"), jnt(22));
        trie.put(f("/sensor/kitchen/temperature/raw"), jnt(2200));
        trie.put(f("/sensor/bedroom/temperature"), jnt(20));
        trie.put(f("/actuator/kitchen/light"), jnt(1));

        // Query: /sensor/kitchen/#
        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/sensor/kitchen/#"));

        assertEquals(3, results.size());
        final Set<String> furis = results.stream()
                .map(e -> e.getKey().toString())
                .collect(Collectors.toSet());

        assertTrue(furis.contains("/sensor/kitchen"));
        assertTrue(furis.contains("/sensor/kitchen/temperature"));
        assertTrue(furis.contains("/sensor/kitchen/temperature/raw"));
        assertFalse(furis.contains("/sensor/bedroom/temperature"));
    }

    @Test
    void testMultiLevelWildcardAlone() {
        trie.put(f("/a"), jnt(1));
        trie.put(f("/b"), jnt(2));
        trie.put(f("/a/b"), jnt(3));
        trie.put(f("/a/b/c"), jnt(4));

        // Query: # (match everything)
        final List<Map.Entry<fURI, Obj>> results = trie.match(f("#"));

        assertEquals(4, results.size());
    }

    @Test
    void testMultiLevelWildcardTopLevel() {
        trie.put(f("/sensor/temp"), jnt(22));
        trie.put(f("/sensor/humidity"), jnt(45));
        trie.put(f("/actuator/light"), jnt(1));

        // Query: /sensor/#
        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/sensor/#"));

        assertEquals(2, results.size());
        final Set<String> furis = results.stream()
                .map(e -> e.getKey().toString())
                .collect(Collectors.toSet());

        assertTrue(furis.contains("/sensor/temp"));
        assertTrue(furis.contains("/sensor/humidity"));
    }

    // ========== Combined Wildcards ==========

    @Test
    void testCombinedWildcards() {
        trie.put(f("/sensor/kitchen/temperature"), jnt(22));
        trie.put(f("/sensor/kitchen/temperature/raw"), jnt(2200));
        trie.put(f("/sensor/bedroom/temperature"), jnt(20));
        trie.put(f("/sensor/bedroom/temperature/calibrated"), jnt(19));
        trie.put(f("/sensor/kitchen/humidity"), jnt(45));

        // Query: /sensor/+/temperature/#
        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/sensor/+/temperature/#"));

        assertEquals(4, results.size());
        final Set<String> furis = results.stream()
                .map(e -> e.getKey().toString())
                .collect(Collectors.toSet());

        assertTrue(furis.contains("/sensor/kitchen/temperature"));
        assertTrue(furis.contains("/sensor/kitchen/temperature/raw"));
        assertTrue(furis.contains("/sensor/bedroom/temperature"));
        assertTrue(furis.contains("/sensor/bedroom/temperature/calibrated"));
        assertFalse(furis.contains("/sensor/kitchen/humidity"));
    }

    // ========== Edge Cases ==========

    @Test
    void testRelativePaths() {
        trie.put(f("sensor/temp"), jnt(22));
        trie.put(f("sensor/humidity"), jnt(45));

        assertEquals(jnt(22), trie.get(f("sensor/temp")));

        final List<Map.Entry<fURI, Obj>> results = trie.match(f("sensor/+"));
        assertEquals(2, results.size());
    }

    @Test
    void testDeepPaths() {
        trie.put(f("/a/b/c/d/e/f/g"), jnt(1));
        trie.put(f("/a/b/c/d/e/f/h"), jnt(2));

        assertEquals(jnt(1), trie.get(f("/a/b/c/d/e/f/g")));

        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/a/b/c/d/e/f/+"));
        assertEquals(2, results.size());
    }

    @Test
    void testSingleSegmentPath() {
        trie.put(f("/root"), jnt(1));
        trie.put(f("/other"), jnt(2));

        assertEquals(jnt(1), trie.get(f("/root")));

        final List<Map.Entry<fURI, Obj>> results = trie.match(f("/+"));
        assertEquals(2, results.size());
    }

    @Test
    void testEntrySet() {
        trie.put(f("/a"), jnt(1));
        trie.put(f("/b"), jnt(2));
        trie.put(f("/a/b"), jnt(3));

        final Set<Map.Entry<fURI, Obj>> entries = trie.entrySet();
        assertEquals(3, entries.size());
    }

    @Test
    void testForEach() {
        trie.put(f("/a"), jnt(1));
        trie.put(f("/b"), jnt(2));
        trie.put(f("/a/b"), jnt(3));

        final int[] count = {0};
        trie.forEach((furi, obj) -> count[0]++);

        assertEquals(3, count[0]);
    }

    // ========== Thread Safety (basic) ==========

    @Test
    void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 10;
        final int opsPerThread = 100;
        final Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    final fURI key = f("/thread/" + threadId + "/item/" + j);
                    trie.put(key, jnt(j));
                    trie.get(key);
                }
            });
        }

        for (final Thread t : threads) {
            t.start();
        }
        for (final Thread t : threads) {
            t.join();
        }

        // Verify all items are present
        assertEquals(numThreads * opsPerThread, trie.size());
    }
}
