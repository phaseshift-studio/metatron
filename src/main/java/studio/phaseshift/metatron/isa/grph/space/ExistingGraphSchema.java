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
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.SchemaSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.grph.io.ObjTP3Serializer;

import static studio.phaseshift.metatron.isa.grph.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.VRTX_TID;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Auto-discovers vertex and edge labels, property types, and edge directions
 * from a live TinkerPop graph.  Mirrors {@code ExistingCollectionSchema} (MongoDB)
 * and {@code ExistingTableSchema} (SQL) patterns.
 */
public class ExistingGraphSchema {

    private final grphSpace space;
    private final Map</* label (lowercase) */ String, LabelMetadata> labelSchemas = new LinkedHashMap<>();
    private final int sampleSize;

    /**
     * Tracks the metatron TID of every property value written so new properties
     * can be merged into the schema InstSet without re-sampling the graph.
     * Mirrors {@code ExistingTableSchema.logicalTypes}.
     */
    private final Map<String, Map<String, Obj>> logicalTypes = new LinkedHashMap<>();

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

    public void initialize(final GraphTraversalSource g) {
        this.labelSchemas.clear();
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

    /**
     * Publish all discovered labels to the schema InstSet so that
     * {@code *g:person}, {@code *g:knows}, etc. resolve immediately after
     * dataset load.
     */
    public void publishDiscoveredLabels() {
        // Build types from all discovered labels
        final List<Type> types = new ArrayList<>();
        // Default V and E meta-collections — always present (TinkerPop convention)
        types.add(Type.Builder.build().tid(VRTX_TID).vid(f("V")).create());
        types.add(Type.Builder.build().tid(EDGE_TID).vid(f("E")).create());
        for (final LabelMetadata meta : this.labelSchemas.values()) {
            final LinkedHashMap<Obj, Obj> fields = new LinkedHashMap<>();
            for (final PropertyMetadata prop : meta.properties()) {
                final Obj typeObj = ObjTP3Serializer.javaTypeToMtronType(prop.javaType());
                if (typeObj != null)
                    fields.put(uri(prop.path()), typeObj);
            }
            if (fields.isEmpty()) continue;
            final fURI baseTid = meta.elementType() == ElementType.EDGE
                    ? EDGE_TID : VRTX_TID;
            types.add(Type.Builder.build()
                    .tid(baseTid)
                    .vid(f(meta.label()))
                    .isaPredicate(rec(fields))
                    .create());
        }
        if (types.isEmpty()) return;
        // Merge into the InstSet at SCHEMA (bootstrap if needed)
        final Obj schema = this.space.at(uri(SCHEMA));
        if (schema.isInstSet() && schema.<InstSet>as().pattern() != null) {
            for (final Type type : types)
                schema.<Space>as().write(
                        schema.<InstSet>as().pattern().retractPattern()
                                .extend(type.vid().name()), type);
        } else {
            final fURI instSetVid = this.space.vid().extend(INSTSET);
            final fURI instSetPattern = instSetVid.extend(ALL);
            final InstSet instSet = new AbstractInstSet(
                    mutableMap(
                            uri(PATTERN), uri(instSetPattern),
                            uri(TYPE), lst(types.stream().map(t -> (Obj) t).toList())
                    ),
                    INSTSET_TID, instSetVid
            ) {};
            Router.global().addSpace(instSet);
            instSet.setup();
            this.space.at(uri(SCHEMA), instSet, MUTABLE);
        }
        SchemaSpace.logSchemaChange(this.space.logger(), "dataset", "label",
                "_discovered", this.labelSchemas.keySet(), true);
    }

    // ---- entity discovery ---------------------------------------------------

    private List<String> discoverEntities(final GraphTraversalSource g) {
        final Set<String> labels = new LinkedHashSet<>();
        g.V().label().dedup().forEachRemaining(labels::add);
        g.E().label().dedup().forEachRemaining(labels::add);
        // The flat kv_store label is internal storage, not a schema type.
        labels.remove(grphSpace.KV_STORE);
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

    // ---- field type tracking -------------------------------------------------

    public Map<String, Map<String, Obj>> getLogicalTypes() {
        return this.logicalTypes;
    }

    /**
     * Record the metatron TID of a written property so the schema InstSet
     * can be updated without re-sampling the graph.
     */
    public void trackPropertyType(final String label, final String propertyName,
                                  final Obj value) {
        this.logicalTypes
                .computeIfAbsent(label.toLowerCase(), k -> new LinkedHashMap<>())
                .put(propertyName.toLowerCase(), T(value.tid()));
    }

    /**
     * Called after a vertex or edge write that may have introduced a new
     * label or new properties.  Regenerates the label's Type from tracked
     * property TIDs and writes it to the schema InstSet via the space's
     * {@code SCHEMA} key.
     */
    public void onLabelChanged(final String label, final boolean isNew) {
        final Map<String, Obj> propTypes = this.logicalTypes.get(
                label.toLowerCase());
        if (propTypes == null || propTypes.isEmpty()) return;

        // Build the Type from property TIDs: rec{*}::T[?prop1=>type1::T,...]
        final LinkedHashMap<Obj, Obj> fields = new LinkedHashMap<>();
        propTypes.forEach((propName, typeObj) ->
                fields.put(uri(propName), typeObj));

        // Determine element type (vertex vs edge) from existing metadata
        final LabelMetadata existing = this.labelSchemas.get(label.toLowerCase());
        final fURI baseTid = existing != null && existing.elementType() == ElementType.EDGE
                ? grphInstSet.EDGE_TID
                : grphInstSet.VRTX_TID;

        // vid = type name so resolveCollectionSchema(label) matches on t.vid().name()
        final Type type = Type.Builder.build()
                .tid(baseTid)           // super-type: vrtx::T or edge::T
                .vid(f(label))          // type name: "person", "knows", etc.
                .isaPredicate(rec(fields))
                .create();

        // Merge into the schema InstSet at SCHEMA (bootstrap if needed)
        final Obj schema = this.space.at(uri(SCHEMA));
        if (schema.isInstSet() && schema.<InstSet>as().pattern() != null) {
            Router.writeToSpace(
                    schema.<InstSet>as().pattern().retractPattern().extend(label),
                    type);
        } else {
            // Bootstrap: no InstSet at SCHEMA — create one seeded with this
            // type plus the default V and E meta-collections
            final fURI instSetVid = this.space.vid().extend(INSTSET);
            final fURI instSetPattern = instSetVid.extend(ALL);
            final Type vType = Type.Builder.build().tid(VRTX_TID).vid(f("V")).create();
            final Type eType = Type.Builder.build().tid(EDGE_TID).vid(f("E")).create();
            final InstSet instSet = new AbstractInstSet(
                    mutableMap(
                            uri(PATTERN), uri(instSetPattern),
                            uri(TYPE), lst(List.of((Obj) vType, (Obj) eType, (Obj) type))
                    ),
                    INSTSET_TID, instSetVid
            ) {};
            Router.global().addSpace(instSet);
            instSet.setup();
            this.space.at(uri(SCHEMA), instSet, MUTABLE);
        }
        SchemaSpace.logSchemaChange(this.space.logger(), "label", "property",
                label, propTypes.keySet(), isNew);
    }

    // ---- accessors ----------------------------------------------------------

    public Map<String, LabelMetadata> getLabelSchemas() {
        return Collections.unmodifiableMap(this.labelSchemas);
    }

    public LabelMetadata getLabelSchema(final String label) {
        return this.labelSchemas.get(label.toLowerCase());
    }
}
