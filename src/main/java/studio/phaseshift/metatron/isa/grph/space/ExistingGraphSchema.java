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

package studio.phaseshift.metatron.isa.grph.space;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.furi.fURI;

import java.util.*;

import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * Auto-discovers vertex and edge labels, property types, and edge directions
 * from a live TinkerPop graph.  Mirrors {@code ExistingCollectionSchema} (MongoDB)
 * and {@code ExistingTableSchema} (SQL) patterns.
 */
public class ExistingGraphSchema {

    private final grphSpace space;
    private final Map</* label (lowercase) */ String, LabelMetadata> labelSchemas = new LinkedHashMap<>();
    private final int sampleSize;

    // ---- records ------------------------------------------------------------

    public enum ElementType { VERTEX, EDGE }

    public record LabelMetadata(String dbName, String label, ElementType elementType,
                                 List<PropertyMetadata> properties,
                                 List<EdgeDirectionMetadata> edgeDirections) {
    }

    public record PropertyMetadata(String path, Class<?> javaType, double probability) {
    }

    public record EdgeDirectionMetadata(Direction direction, String toLabel) {
    }

    // ---- construction -------------------------------------------------------

    public ExistingGraphSchema(final grphSpace space, final int sampleSize) {
        this.space = space;
        this.sampleSize = sampleSize;
    }

    public ExistingGraphSchema(final grphSpace space) {
        this(space, 100);
    }

    // ---- main entry point ---------------------------------------------------

    public void initialize(final Graph graph) {
        this.labelSchemas.clear();
        final GraphTraversalSource g = graph.traversal();
        for (final String label : discoverEntities(g)) {
            // Determine element type: check if any vertex has this label
            final ElementType type = g.V().hasLabel(label).hasNext()
                    ? ElementType.VERTEX : ElementType.EDGE;
            final List<PropertyMetadata> props = inferPropertyTypes(g, label, type);
            final List<EdgeDirectionMetadata> dirs = type == ElementType.EDGE
                    ? discoverReferences(g, label) : List.of();
            this.labelSchemas.put(label.toLowerCase(),
                    new LabelMetadata("graph", label, type, props, dirs));
            this.space.logger().debug("discovered label: %s (%s) with %d properties, %d directions",
                    label, type, props.size(), dirs.size());
        }
        this.space.logger().info("discovered {{b}}%d{{X}} labels: %s",
                this.labelSchemas.size(), this.labelSchemas.keySet());
    }

    // ---- entity discovery ---------------------------------------------------

    private List<String> discoverEntities(final GraphTraversalSource g) {
        final Set<String> labels = new LinkedHashSet<>();
        g.V().label().dedup().forEachRemaining(labels::add);
        g.E().label().dedup().forEachRemaining(labels::add);
        this.space.logger().debug("discovered {{b}}%d{{X}} entity labels", labels.size());
        return new ArrayList<>(labels);
    }

    // ---- property type inference --------------------------------------------

    private List<PropertyMetadata> inferPropertyTypes(final GraphTraversalSource g,
                                                       final String label,
                                                       final ElementType type) {
        final Map<String, Map<Class<?>, Integer>> typeCounts = new LinkedHashMap<>();
        int elementCount = 0;
        final Iterator<? extends Element> elements = type == ElementType.VERTEX
                ? g.V().hasLabel(label).limit(this.sampleSize)
                : g.E().hasLabel(label).limit(this.sampleSize);
        while (elements.hasNext()) {
            final Element e = elements.next();
            e.properties().forEachRemaining(p -> {
                typeCounts.computeIfAbsent(p.key(), k -> new LinkedHashMap<>())
                        .merge(inferPropertyClass(p.value()), 1, Integer::sum);
            });
            elementCount++;
        }
        if (elementCount == 0)
            return List.of();
        return buildPropertyMetadata(typeCounts, elementCount);
    }

    private static Class<?> inferPropertyClass(final Object value) {
        if (value == null) return Void.class;
        if (value instanceof String) return String.class;
        if (value instanceof Integer) return Integer.class;
        if (value instanceof Long) return Long.class;
        if (value instanceof Double || value instanceof Float) return Double.class;
        if (value instanceof Boolean) return Boolean.class;
        if (value instanceof List || value.getClass().isArray()) return List.class;
        return String.class;
    }

    private List<PropertyMetadata> buildPropertyMetadata(
            final Map<String, Map<Class<?>, Integer>> counts, final int elementCount) {
        final List<PropertyMetadata> fields = new ArrayList<>();
        for (final var entry : counts.entrySet()) {
            final String path = entry.getKey();
            final Map<Class<?>, Integer> typeCounts = entry.getValue();
            Class<?> dominantType = Void.class;
            int maxCount = 0;
            int totalCount = 0;
            for (final var tc : typeCounts.entrySet()) {
                totalCount += tc.getValue();
                if (tc.getValue() > maxCount) {
                    maxCount = tc.getValue();
                    dominantType = tc.getKey();
                }
            }
            final double probability = elementCount > 0 ? (double) totalCount / elementCount : 0.0;
            fields.add(new PropertyMetadata(path, dominantType, probability));
        }
        return fields;
    }

    // ---- reference / edge direction detection -------------------------------

    private List<EdgeDirectionMetadata> discoverReferences(final GraphTraversalSource g,
                                                            final String edgeLabel) {
        final Map<String, Integer> outLabels = new LinkedHashMap<>();
        final Map<String, Integer> inLabels = new LinkedHashMap<>();
        final Iterator<Edge> edges = g.E().hasLabel(edgeLabel).limit(this.sampleSize);
        while (edges.hasNext()) {
            final Edge e = edges.next();
            outLabels.merge(e.outVertex().label(), 1, Integer::sum);
            inLabels.merge(e.inVertex().label(), 1, Integer::sum);
        }
        final List<EdgeDirectionMetadata> dirs = new ArrayList<>();
        outLabels.forEach((label, count) ->
                dirs.add(new EdgeDirectionMetadata(Direction.OUT, label)));
        inLabels.forEach((label, count) ->
                dirs.add(new EdgeDirectionMetadata(Direction.IN, label)));
        return dirs;
    }

    // ---- mtron type mapping -------------------------------------------------

    public static fURI toMtronType(final Class<?> javaType) {
        if (javaType == String.class) return str("").tid().basePath();
        if (javaType == Integer.class || javaType == Long.class) return jnt(0).tid().basePath();
        if (javaType == Double.class || javaType == Float.class) return real(0.0).tid().basePath();
        if (javaType == Boolean.class) return bool(false).tid().basePath();
        return str("").tid().basePath();
    }

    // ---- accessors ----------------------------------------------------------

    public Map<String, LabelMetadata> getLabelSchemas() {
        return Collections.unmodifiableMap(this.labelSchemas);
    }

    public LabelMetadata getLabelSchema(final String label) {
        return this.labelSchemas.get(label.toLowerCase());
    }
}
