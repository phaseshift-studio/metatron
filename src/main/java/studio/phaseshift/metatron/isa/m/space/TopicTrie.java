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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;

/**
 * A Topic Trie (prefix tree) for efficient MQTT-style wildcard matching on fURIs.
 * <p>
 * This data structure provides:
 * <ul>
 *   <li>O(path length) for exact lookups (no wildcards)</li>
 *   <li>Efficient pattern matching for {@code +} (single-level) and {@code #} (multi-level) wildcards</li>
 *   <li>Thread-safe operations via ConcurrentHashMap</li>
 * </ul>
 * <p>
 * MQTT Wildcard Semantics:
 * <ul>
 *   <li>{@code +} - Matches exactly one path segment at that level</li>
 *   <li>{@code #} - Matches zero or more path segments (must be the last segment in a pattern)</li>
 * </ul>
 * <p>
 * Note: Multiple fURIs with the same path segments but different query params, coefficients, etc.
 * can coexist at the same trie node. The trie indexes by path, but stores full fURI→Obj mappings.
 * <p>
 * Example patterns:
 * <ul>
 *   <li>{@code /sensor/+/temperature} - Matches /sensor/kitchen/temperature, /sensor/bedroom/temperature</li>
 *   <li>{@code /sensor/#} - Matches /sensor, /sensor/kitchen, /sensor/kitchen/temperature</li>
 *   <li>{@code /+/kitchen/+} - Matches /sensor/kitchen/temperature, /actuator/kitchen/light</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TopicTrie {

    private final TrieNode root;

    public TopicTrie() {
        this.root = new TrieNode();
    }

    /**
     * A node in the topic trie.
     * Each node represents a path segment and contains:
     * - children: map of segment -> child node
     * - values: map of fURI -> Obj for all fURIs at this path (supports multiple fURIs with same path)
     */
    private static class TrieNode {
        final Map<String, TrieNode> children = new ConcurrentHashMap<>();
        // Multiple fURIs can have the same path but different query params, coefficients, etc.
        final Map<fURI, Obj> values = new ConcurrentHashMap<>();

        TrieNode getOrCreateChild(final String segment) {
            return children.computeIfAbsent(segment, k -> new TrieNode());
        }

        TrieNode getChild(final String segment) {
            return children.get(segment);
        }

        boolean hasValues() {
            return !values.isEmpty();
        }

        void putValue(final fURI furi, final Obj value) {
            this.values.put(furi, value);
        }

        Obj getValue(final fURI furi) {
            return this.values.get(furi);
        }

        Obj removeValue(final fURI furi) {
            return this.values.remove(furi);
        }
    }

    /**
     * Insert or update a value in the trie.
     *
     * @param furi the fURI key (should not contain wildcards for insertion)
     * @param obj  the value to store
     * @return the previous value, or null if none
     */
    public Obj put(final fURI furi, final Obj obj) {
        final List<String> segments = furi.segments();
        TrieNode current = root;

        for (final String segment : segments) {
            current = current.getOrCreateChild(segment);
        }

        final Obj previous = current.getValue(furi);
        current.putValue(furi, obj);
        return previous;
    }

    /**
     * Get the value for an exact fURI match (no wildcards).
     * Uses fURI.equals() semantics, so query params, coefficients, etc. must match.
     *
     * @param furi the fURI to look up
     * @return the value, or null if not found
     */
    public Obj get(final fURI furi) {
        final TrieNode node = findExactNode(furi);
        return node != null ? node.getValue(furi) : null;
    }

    /**
     * Check if the trie contains an exact match for the fURI.
     *
     * @param furi the fURI to check
     * @return true if a value exists for this exact fURI
     */
    public boolean containsKey(final fURI furi) {
        final TrieNode node = findExactNode(furi);
        return node != null && node.values.containsKey(furi);
    }

    /**
     * Remove the value for an exact fURI match.
     *
     * @param furi the fURI to remove
     * @return the removed value, or null if not found
     */
    public Obj remove(final fURI furi) {
        final TrieNode node = findExactNode(furi);
        if (node != null) {
            // Note: We don't remove empty nodes for simplicity and thread-safety
            // A background cleanup could be added if memory is a concern
            return node.removeValue(furi);
        }
        return null;
    }

    /**
     * Find all entries matching a pattern with MQTT wildcards.
     *
     * @param pattern the pattern (may contain + and # wildcards)
     * @return list of matching fURI/Obj pairs
     */
    public List<Map.Entry<fURI, Obj>> match(final fURI pattern) {
        final List<Map.Entry<fURI, Obj>> results = new ArrayList<>();
        final List<String> segments = pattern.segments();

        if (segments.isEmpty() || (segments.size() == 1 && segments.getFirst().equals("#"))) {
            // Match everything
            collectAll(root, results);
        } else {
            matchRecursive(root, segments, 0, results);
        }

        return results;
    }

    /**
     * Iterate over all entries in the trie.
     *
     * @param consumer the consumer to call for each entry
     */
    public void forEach(final BiConsumer<fURI, Obj> consumer) {
        forEachRecursive(root, consumer);
    }

    /**
     * Get all entries in the trie.
     *
     * @return set of all entries
     */
    public Set<Map.Entry<fURI, Obj>> entrySet() {
        final Set<Map.Entry<fURI, Obj>> entries = new LinkedHashSet<>();
        forEach((furi, obj) -> entries.add(new AbstractMap.SimpleEntry<>(furi, obj)));
        return entries;
    }

    /**
     * Check if the trie is empty.
     *
     * @return true if no values are stored
     */
    public boolean isEmpty() {
        return !hasAnyValue(root);
    }

    /**
     * Get the number of values in the trie.
     * Note: This is O(n) as it traverses the entire trie.
     *
     * @return the number of stored values
     */
    public int size() {
        return countValues(root);
    }

    /**
     * Clear all values from the trie.
     */
    public void clear() {
        root.children.clear();
        root.values.clear();
    }

    // ========== Private Helper Methods ==========

    private TrieNode findExactNode(final fURI furi) {
        final List<String> segments = furi.segments();
        TrieNode current = root;

        for (final String segment : segments) {
            current = current.getChild(segment);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    /**
     * Recursive pattern matching with MQTT wildcard semantics.
     *
     * @param node     current trie node
     * @param segments pattern segments
     * @param index    current segment index
     * @param results  accumulator for matching entries
     */
    private void matchRecursive(final TrieNode node, final List<String> segments,
                                final int index, final List<Map.Entry<fURI, Obj>> results) {
        if (node == null) {
            return;
        }

        // If we've consumed all pattern segments, collect values if present
        if (index >= segments.size()) {
            if (node.hasValues()) {
                node.values.forEach((furi, obj) -> results.add(new AbstractMap.SimpleEntry<>(furi, obj)));
            }
            return;
        }

        final String segment = segments.get(index);

        if (segment.equals("#")) {
            // Multi-level wildcard: match zero or more levels
            // First, match zero levels (current node's values if any)
            if (node.hasValues()) {
                node.values.forEach((furi, obj) -> results.add(new AbstractMap.SimpleEntry<>(furi, obj)));
            }
            // Then match all descendants
            collectAll(node, results);

        } else if (segment.equals("+")) {
            // Single-level wildcard: match exactly one level
            for (final TrieNode child : node.children.values()) {
                matchRecursive(child, segments, index + 1, results);
            }

        } else {
            // Exact match: follow the specific child
            final TrieNode child = node.getChild(segment);
            if (child != null) {
                matchRecursive(child, segments, index + 1, results);
            }
        }
    }

    /**
     * Collect all values from a node and all its descendants.
     * Used for # wildcard matching.
     */
    private void collectAll(final TrieNode node, final List<Map.Entry<fURI, Obj>> results) {
        if (node == null) {
            return;
        }

        for (final TrieNode child : node.children.values()) {
            if (child.hasValues()) {
                child.values.forEach((furi, obj) -> results.add(new AbstractMap.SimpleEntry<>(furi, obj)));
            }
            collectAll(child, results);
        }
    }

    private void forEachRecursive(final TrieNode node, final BiConsumer<fURI, Obj> consumer) {
        if (node == null) {
            return;
        }

        if (node.hasValues()) {
            node.values.forEach(consumer);
        }

        for (final TrieNode child : node.children.values()) {
            forEachRecursive(child, consumer);
        }
    }

    private boolean hasAnyValue(final TrieNode node) {
        if (node == null) {
            return false;
        }
        if (node.hasValues()) {
            return true;
        }
        for (final TrieNode child : node.children.values()) {
            if (hasAnyValue(child)) {
                return true;
            }
        }
        return false;
    }

    private int countValues(final TrieNode node) {
        if (node == null) {
            return 0;
        }
        int count = node.values.size();
        for (final TrieNode child : node.children.values()) {
            count += countValues(child);
        }
        return count;
    }
}
