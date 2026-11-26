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

package studio.phaseshift.metatron.isa.m.db.grph;

import org.apache.tinkerpop.gremlin.jsr223.DefaultGremlinScriptEngineManager;
import org.apache.tinkerpop.gremlin.jsr223.GremlinLangScriptEngineFactory;
import org.apache.tinkerpop.gremlin.jsr223.GremlinScriptEngine;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalInterruptedException;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.rewrite.db.CommonRewrites;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.db.grph.io.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.m.db.grph.space.EdgeRec;
import studio.phaseshift.metatron.isa.m.db.grph.space.ElementRec;
import studio.phaseshift.metatron.isa.m.db.grph.space.VertexRec;
import studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.m.db.grph.space.schema.GremlinRewriteUtils;
import studio.phaseshift.metatron.isa.m.db.grph.space.schema.modernSchema;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace.*;
import static studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace.SERIALIZER;
import static studio.phaseshift.metatron.isa.m.db.grph.space.schema.modernSchema.MODERN_SCHEMA;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/db/grph")
public class grphInstSet extends AbstractInstSet {

    public static final fURI GRPH_ISA_TID = DB_ISA_TID.extend("grph");
    public static final fURI GRPH_INST_TID = GRPH_ISA_TID.extend("inst");
    public static final fURI GRPH_REWRITE_TID = GRPH_INST_TID.extend("rewrite");
    //
    public static final fURI GREMLIN_INST_TID = GRPH_INST_TID.extend("gremlin");
    public static final fURI ADDE_INST_TID = GRPH_INST_TID.extend("addE");
    public static final fURI PROPERTIES_INST_TID = GRPH_INST_TID.extend("properties");
    public static final fURI LABEL_INST_TID = GRPH_INST_TID.extend("label");
    public static final fURI VALUES_INST_TID = GRPH_INST_TID.extend("values");
    public static final fURI BOTHV_INST_TID = GRPH_INST_TID.extend("bothV");
    public static final fURI INV_INST_TID = GRPH_INST_TID.extend("inV");
    public static final fURI OUTV_INST_TID = GRPH_INST_TID.extend("outV");
    public static final fURI BOTHE_INST_TID = GRPH_INST_TID.extend("bothE");
    public static final fURI BOTH_INST_TID = GRPH_INST_TID.extend("both");
    public static final fURI INE_INST_TID = GRPH_INST_TID.extend("inE");
    public static final fURI OUTE_INST_TID = GRPH_INST_TID.extend("outE");
    public static final fURI IN_INST_TID = GRPH_INST_TID.extend("in");
    public static final fURI OUT_INST_TID = GRPH_INST_TID.extend("out");
    // the path instructions (docs/design/smooth-crumple-relations.md): smooth(T) is ~T~, crumple(T) is ~T~>
    public static final fURI SMOOTH_INST_TID = GRPH_INST_TID.extend("smooth");
    public static final fURI CRUMPLE_INST_TID = GRPH_INST_TID.extend("crumple");
    public static final String AUTO_ROUTE_STRING = "!route";
    public static final Uri BOTH = uri(Direction.BOTH.name());
    public static final Uri ID = uri("ID");
    public static final fURI IN_FURI = f(Direction.IN.name());
    public static final fURI OUT_FURI = f(Direction.OUT.name());
    public static final fURI BOTH_FURI = f(Direction.BOTH.name());
    public static final fURI LABEL_FURI = f("LABEL");
    public static final fURI ID_FURI = f("ID");
    public static final Uri LABEL = uri("LABEL");
    public static final Uri IN = uri(Direction.IN.name());
    public static final Uri OUT = uri(Direction.OUT.name());

    public grphInstSet() {
        super(mutableMap(uri(PATTERN), uri(GRPH_ISA_TID.extend(ALL))), INSTSET_TID, GRPH_ISA_TID);
    }

    /**
     * The path sugars, wraps around an edge type (docs/design/smooth-crumple-relations.md): {@code ~T~} is
     * {@code smooth(T)}, which crosses the edges of type T and keeps nothing, and {@code ~T~>} is {@code crumple(T)},
     * which crosses them and keeps the element it reaches as a point of the path. The longest sugar wins, so
     * {@code ~T~>} is never read as {@code ~T~} and a stray {@code >}.
     */
    @Override
    public Set<Sugar> sugars() {
        return new LinkedHashSet<>(List.of(
                Sugar.wrap("~", "~>", List.of(CRUMPLE_INST_TID), 1),
                Sugar.wrap("~", "~", List.of(SMOOTH_INST_TID), 1)));
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Resolve the element VID: prefer Obj VID, check ElementRec fields, unwrap Objs.
     */
    private static fURI resolveVid(final Obj lhs) {
        fURI base = lhs.vid();
        if (base == null && lhs instanceof ElementRec<?> er)
            base = er.elementVID();
        // lhs could be an Objs wrapping multiple vertices — search inside for ElementRec
        if (base == null && lhs.isObjs()) {
            base = lhs.stream()
                    .filter(o -> o instanceof ElementRec<?>)
                    .map(o -> ((ElementRec<?>) o).elementVID())
                    .findFirst().orElse(null);
        }
        return base;
    }

    /**
     * The edge labels an adjacency inst carries, EVERY ONE THE CALL NAMES (Marko, 2026-11-22: mtron has varargs, so
     * the one declared slot {@code uri{*}::T} binds them all): {@code out(knows,created)} filters by both, as the
     * pushed-down {@code out('knows','created')} does ({@link #quotedArguments}). Nothing when the slot is empty OR
     * still holds the DECLARED arg type of an unbound inst — a type is not a label. Nothing, too, when a pattern is
     * among them ({@code out(+)}): it takes every label, as the pushed-down {@code out()} does, never an edge labeled
     * {@code +}.
     */
    private static String[] edgeLabels(final Inst inst) {
        final List<String> labels = new ArrayList<>();
        for (final Obj arg : inst.args().orElse(lst()).values().flatMap(Obj::stream).toList()) {
            if (arg.isType())
                continue;
            if (arg.isUri() && arg.uriValue().hasPattern())
                return new String[0];
            if (arg.isUri())
                labels.add(arg.uriValue().name());
        }
        return labels.toArray(String[]::new);
    }

    /**
     * A label naming another space ({@code te:hot}) is routed by the space's own depth walk
     * ({@code grphSpace.traverseVertex}), not by Gremlin: such a hop keeps the address walk and declines fusion.
     */
    private static boolean isExternalLabel(final Inst inst) {
        return inst.args().orElse(lst()).values().flatMap(Obj::stream)
                .anyMatch(arg -> !arg.isType() && arg.isUri() && arg.uriValue().toString().indexOf(':') > 0);
    }

    /**
     * An address names one label, so a call naming several reads one address per label and sums what they hold.
     */
    private static Obj readPerLabel(final String[] labels, final Function<String, fURI> address) {
        return 1 == labels.length
                ? Machine.read(address.apply(labels[0]))
                : objs(Arrays.stream(labels).flatMap(label -> Machine.read(address.apply(label)).stream()));
    }

    /**
     * The live vertex recs a value holds: the rec itself, or the members of an {@link studio.phaseshift.metatron.isa.m.type.Objs}.
     */
    private static List<VertexRec> vertexRecs(final Obj lhs) {
        return lhs.stream().filter(VertexRec.class::isInstance).map(VertexRec.class::cast).toList();
    }

    /**
     * The live edge recs a value holds.
     */
    private static List<EdgeRec> edgeRecs(final Obj lhs) {
        return lhs.stream().filter(EdgeRec.class::isInstance).map(EdgeRec.class::cast).toList();
    }

    /**
     * Adjacent vertices (out/in/both) on the LIVE element: one vertex→edge→vertex walk in the graph, with
     * every endpoint keeping the {@code !route} auto-resolution {@link EdgeRec#vertex(Direction)} performs.
     * A value with no live element — data that crossed the wire, or a plain rec carrying only a vid —
     * falls back to the routed address.
     */
    private static Obj vertexTraversal(final Obj lhs, final Inst inst, final Direction direction) {
        final List<VertexRec> vertices = vertexRecs(lhs);
        if (vertices.isEmpty() || isExternalLabel(inst))
            return direction == Direction.BOTH
                    ? routeBothTraversal(lhs, inst)
                    : routeVertexTraversal(lhs, inst, direction);
        final String[] labels = edgeLabels(inst);
        return objs(switch (direction) {
            case OUT -> vertices.stream()
                    .flatMap(v -> v.edges(Direction.OUT, labels))
                    .map(e -> ((EdgeRec) e).vertex(Direction.IN));
            case IN -> vertices.stream()
                    .flatMap(v -> v.edges(Direction.IN, labels))
                    .map(e -> ((EdgeRec) e).vertex(Direction.OUT));
            case BOTH -> Stream.concat(
                    vertices.stream().flatMap(v -> v.edges(Direction.OUT, labels))
                            .map(e -> ((EdgeRec) e).vertex(Direction.IN)),
                    vertices.stream().flatMap(v -> v.edges(Direction.IN, labels))
                            .map(e -> ((EdgeRec) e).vertex(Direction.OUT)));
        });
    }

    /**
     * Adjacent edges (outE/inE/bothE) on the LIVE element.
     */
    private static Obj edgeTraversal(final Obj lhs, final Inst inst, final Direction direction) {
        final List<VertexRec> vertices = vertexRecs(lhs);
        if (vertices.isEmpty() || isExternalLabel(inst))
            return direction == Direction.BOTH
                    ? routeBothETraversal(lhs, inst)
                    : routeEdgeTraversal(lhs, inst, direction);
        final String[] labels = edgeLabels(inst);
        return objs(switch (direction) {
            case OUT -> vertices.stream().flatMap(v -> v.edges(Direction.OUT, labels));
            case IN -> vertices.stream().flatMap(v -> v.edges(Direction.IN, labels));
            case BOTH -> Stream.concat(
                    vertices.stream().flatMap(v -> v.edges(Direction.OUT, labels)),
                    vertices.stream().flatMap(v -> v.edges(Direction.IN, labels)));
        });
    }

    /**
     * WHETHER AN EDGE PASSES AN ENDPOINT STEP'S LABELS (Marko, 2026-11-22): outV(L), inV(L) and bothV(L) answer an
     * edge's endpoint only when the edge's own label is among L, and every edge passes when no label is given. So each
     * endpoint step is the converse of its edge step with the labels carried through, outE(L)⁻¹(e) = outV(L)(e): the
     * edge's tail if its label is in L, else nothing. A live edge reads its label off the graph; a value that crossed
     * the wire carries it as its type's name. L is every label the call names ({@link #edgeLabels}), the same labels
     * the pushdown hands Gremlin ({@link #quotedArguments}). Package-private: TinkerGrphSpaceTest reads a crossed
     * edge's label through it, since a value edge carries no address to walk its endpoints by.
     */
    static boolean labeled(final Obj edge, final Inst inst) {
        final String[] labels = edgeLabels(inst);
        return 0 == labels.length
                || List.of(labels).contains(edge instanceof EdgeRec live ? live.edge().label() : edge.tid().name());
    }

    /**
     * The endpoint vertex of an edge (inV/outV) on the LIVE element.
     */
    private static Obj edgeVertex(final Obj lhs, final Direction direction) {
        final List<EdgeRec> edges = edgeRecs(lhs);
        if (!edges.isEmpty())
            return objs(edges.stream().map(e -> e.vertex(direction)));
        final fURI base = resolveVid(lhs);
        return null != base
                ? Machine.read(base.extend(direction.name()))
                : lhs.asRec().at(Direction.IN == direction ? IN : OUT);
    }

    /**
     * Both endpoint vertices of an edge (bothV) on the LIVE element.
     */
    private static Obj edgeVertices(final Obj lhs) {
        final List<EdgeRec> edges = edgeRecs(lhs);
        if (!edges.isEmpty())
            return objs(edges.stream().flatMap(e -> Stream.of(e.vertex(Direction.IN), e.vertex(Direction.OUT))));
        final fURI base = resolveVid(lhs);
        return null != base
                ? objs(Stream.concat(Machine.read(base.extend("IN")).stream(), Machine.read(base.extend("OUT")).stream()))
                : objs(Stream.concat(lhs.asRec().at(IN).stream(), lhs.asRec().at(OUT).stream()));
    }

    // ── the address fallbacks: a value that carries only a vid re-reads through the routed path ──

    private static Obj routeEdgeTraversal(final Obj lhs, final Inst inst, final Direction direction) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final fURI hop = base.extend(direction.name());
        final String[] labels = edgeLabels(inst);
        return 0 == labels.length ? Machine.read(hop) : readPerLabel(labels, hop::extend);
    }

    private static Obj routeVertexTraversal(final Obj lhs, final Inst inst, final Direction direction) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final fURI hop = base.extend(direction.name());
        final String back = direction.opposite().name();
        final String[] labels = edgeLabels(inst);
        return 0 == labels.length ? Machine.read(hop.extend(back))
                : readPerLabel(labels, label -> hop.extend(label).extend(back));
    }

    private static Obj routeBothTraversal(final Obj lhs, final Inst inst) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final String[] labels = edgeLabels(inst);
        // a vertex's OUT/IN segment is its EDGES, so the OPPOSITE hop is what lands on vertices
        final String[] each = labels.length > 0 ? labels : new String[]{"+"};
        return objs(Stream.concat(
                readPerLabel(each, label -> base.extend("OUT").extend(label).extend(Tokens.IN)).stream(),
                readPerLabel(each, label -> base.extend("IN").extend(label).extend(Tokens.OUT)).stream()));
    }

    private static Obj routeBothETraversal(final Obj lhs, final Inst inst) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final String[] labels = edgeLabels(inst);
        final Obj outResult = labels.length > 0
                ? readPerLabel(labels, label -> base.extend("OUT").extend(label)) : Machine.read(base.extend("OUT"));
        final Obj inResult = labels.length > 0
                ? readPerLabel(labels, label -> base.extend("IN").extend(label)) : Machine.read(base.extend("IN"));
        if (outResult.isFail()) return outResult;
        if (inResult.isFail()) return inResult;
        return objs(Stream.concat(outResult.stream(), inResult.stream()));
    }

    protected static BiFunction<Obj, Inst, Obj> V_E_FUNCTION(final Direction direction) {
        return (lhs, inst) -> edgeTraversal(lhs, inst, direction);
    }

    public static BiFunction<Obj, Inst, Obj> V_V_FUNCTION(final Direction direction) {
        return (lhs, inst) -> vertexTraversal(lhs, inst, direction);
    }

    /// ── THE GREMLIN THEORY: mtron instruction ⇄ Gremlin step ───────────────────────────────────────────────

    /**
     * THE TABLE (a theory, beside the algebra's others): every mtron instruction that has a server-side Gremlin
     * form, bound to that step's spelling. {@code %s} is the instruction's argument list, already quoted
     * ({@code out(knows)} ⇒ {@code out('knows')}); a step with no arguments ignores it ({@code count()}).
     * <p>
     * The rewrite reads THIS table and nothing else, so a new server-side instruction is a new row here — it is
     * published as {@code gremlin_theory::T} and written to the space at {@link #GREMLIN_THEORY_VID}, so
     * {@code *<the table>} shows the vocabulary the compiler is using.
     * <p>
     * The rewrite runs before resolution, so a call still carries its written instruction NAME (this set declares
     * these names, and no other set does).
     */

    /**
     * Where the table is published in the space, so {@code *<the table>} prints the compiled vocabulary.
     */

    public static final fURI GREMLIN_THEORY_TID = GRPH_ISA_TID.extend(THEORY).extend("gremlin_theory");
    public static final Type GREMLIN_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(GREMLIN_THEORY_TID)
            .isaPredicate(rec(
                    uri("mul_monoid"), rec(mutableMap(
                                    uri(OP), auto_from_(OUT_INST_TID.dom(REC_TID).rng(REC_TID)).tryToInst(),
                                    uri(Tokens.ID), id_()),
                            MONOID_THEORY_TID, null),
                    uri(REWRITE), rec(
                            uri("out"), str("out(%s)"),
                            uri("in"), str("in(%s)"),
                            uri("both"), str("both(%s)"),
                            uri("outE"), str("outE(%s)"),
                            uri("inE"), str("inE(%s)"),
                            uri("bothE"), str("bothE(%s)"),
                            // an endpoint step filters its edge by label first: outV(L) is the edge's tail if its label is
                            // in L (Marko, 2026-11-22), and with no label the filter is dropped (gremlinSuffix)
                            uri("inV"), str("hasLabel(%s).inV()"),
                            uri("outV"), str("hasLabel(%s).outV()"),
                            uri("bothV"), str("hasLabel(%s).bothV()"),
                            // a field read ON AN ELEMENT is a values() step: (person::[name=>'marko']).>>name ⇒ values('name')
                            uri("rshift"), str("values(%s)"),
                            uri(COUNT), str("count()"))))
            .create();

    private static Obj getGremlinRewrite(final String key) {
        return GREMLIN_THEORY_TYPE.isPredicateObj().orElse(rec0()).at(REWRITE).orElse(rec0()).at(key);
    }

    /**
     * The Gremlin steps a fused run may BEGIN with: the lhs must be a graph element, not data.
     */
    private static final Set<String> ADJACENCY_STEPS =
            Set.of("out", "in", "both", "outE", "inE", "bothE", "inV", "outV", "bothV");

    /**
     * What a value is as a traversal walks it.
     */
    private enum Level {
        VERTEX, EDGE, VALUE
    }

    /**
     * A Gremlin step's typing — Gremlin's knowledge, not the mapping's: what it consumes and what it hands on.
     */
    private record StepLevel(Set<Level> in, Level out) {
    }

    private static final Map<String, StepLevel> GREMLIN_LEVELS = Map.ofEntries(
            Map.entry("out", new StepLevel(Set.of(Level.VERTEX), Level.VERTEX)),
            Map.entry("in", new StepLevel(Set.of(Level.VERTEX), Level.VERTEX)),
            Map.entry("both", new StepLevel(Set.of(Level.VERTEX), Level.VERTEX)),
            Map.entry("outE", new StepLevel(Set.of(Level.VERTEX), Level.EDGE)),
            Map.entry("inE", new StepLevel(Set.of(Level.VERTEX), Level.EDGE)),
            Map.entry("bothE", new StepLevel(Set.of(Level.VERTEX), Level.EDGE)),
            Map.entry("inV", new StepLevel(Set.of(Level.EDGE), Level.VERTEX)),
            Map.entry("outV", new StepLevel(Set.of(Level.EDGE), Level.VERTEX)),
            Map.entry("bothV", new StepLevel(Set.of(Level.EDGE), Level.VERTEX)),
            Map.entry("values", new StepLevel(Set.of(Level.VERTEX, Level.EDGE), Level.VALUE)),
            Map.entry("count", new StepLevel(Set.of(Level.VERTEX, Level.EDGE, Level.VALUE), Level.VALUE)));

    /**
     * The Gremlin step a table row spells, e.g. {@code values} for {@code values(%s)}.
     */
    private static String gremlinStep(final Inst inst) {
        // THE STEP A TEMPLATE ENDS IN types it: hasLabel(%s).inV() filters an edge and hands on a vertex, so it is inV
        final String template = getGremlinRewrite(inst.tid().name()).strValue();
        final int dot = template.lastIndexOf(").");
        final String last = dot < 0 ? template : template.substring(dot + 2);
        final int paren = last.indexOf('(');
        return paren < 0 ? last : last.substring(0, paren);
    }

    /**
     * Whether the run can be one traversal: each step must consume what the previous one handed on. Without this a
     * {@code >>a>>b} spine walk (valid in mtron: a field of a rec property) would fuse into
     * {@code values('a').values('b')}, which Gremlin cannot walk.
     */
    private static boolean isValidRun(final List<Inst> run) {
        Level level = GREMLIN_LEVELS.get(gremlinStep(run.getFirst())).in().iterator().next();
        for (final Inst inst : run) {
            final StepLevel step = GREMLIN_LEVELS.get(gremlinStep(inst));
            if (null == step || !step.in().contains(level)) return false;
            level = step.out();
        }
        return true;
    }

    /**
     * Gremlin steps that aggregate the WHOLE traversal ({@code count}). With a folded seed the run's lhs is ONE
     * element, so such a step can be pushed down with the walk; with a mapped lhs (a wildcard read hands the run
     * MANY elements) it must stay in mtron, or the count would be taken per element instead of over the union.
     */
    private static final Set<String> BARRIER_STEPS = Set.of("count");

    /**
     * A step a run can carry into Gremlin: the table has a step for it, and its span is one Gremlin says. A span is a
     * run of steps, not a count ({@code {0,2}out()} is {@code _ + out() + out().out()}), and one Gremlin step says
     * neither: the fold rendered it once and multiplied its answers by the span. So a span Gremlin cannot say BREAKS
     * THE RUN (Marko, 2026-11-13): what comes before it is pushed down, and the processor runs the span. An exact span
     * is laid out as its run before the run is found ({@link #fuseGremlinRuns}), and a ranged or open one is Gremlin's
     * {@code repeat()} ({@link #gremlinSuffix}) when Gremlin walks each of its runs as the processor does: the step
     * hands on the one level it takes (out/in/both, so no run leaves the step's domain, the zero-step run included,
     * where outE's second run would walk an edge and count takes every level), and it has no point (the k-step run
     * counts the point k times, and the call carries one coefficient). A negative span walks the step's converse, and
     * stays for the processor.
     */
    private static boolean isPushable(final Inst inst) {
        if (getGremlinRewrite(inst.tid().name()).isNoObj())
            return false;
        final cInt span = inst.tid().b();
        if (span.isOne())
            return true;
        final StepLevel level = GREMLIN_LEVELS.get(gremlinStep(inst));
        return !span.isExact() && null != span.min() && 0L <= span.min() && inst.tid().d().isOne()
                && null != level && Set.of(level.out()).equals(level.in());
    }

    /**
     * Fuse each maximal run of table instructions into ONE {@code gremlin(...)} call, so
     * {@code g:V/1.out(knows).out(created)} runs as the single traversal
     * {@code gremlin("g.V(1).out('knows').out('created')")} instead of a space read plus a walk.
     * <p>
     * A concrete ELEMENT seed ({@code from(g:V/1)}) is folded in with the run: the call then STARTS the chain
     * ({@link Tokens#NOOBJ_TID} dom, an initial instruction), and the address rides as its first argument so the
     * space is resolved at apply time. A run without a foldable seed applies to the element lhs it arrives with —
     * and stops before a {@link #BARRIER_STEPS barrier}, which mtron then evaluates over the union. External
     * ({@code te:hot}) labels are left alone: the space routes those, not Gremlin.
     */
    private static Code fuseGremlinRuns(final Code code) {
        // AN EXACT SPAN ON A GREMLIN STEP IS THAT MANY GREMLIN STEPS ({2}out() is out().out()): the code keeps the span,
        // a run Inst.Helper.runs walks, and one Gremlin step says no run, so the run is laid out where it is pushed down.
        // A ranged or open span stays on its step, spelled as Gremlin's repeat() (gremlinSuffix), and a negative span
        // stays for the processor (isPushable)
        final List<Inst> insts = code.insts().stream()
                .flatMap(inst -> inst.tid().b().isExact() && null != inst.tid().b().min() && 1L < inst.tid().b().min()
                        && !getGremlinRewrite(inst.tid().name()).isNoObj()
                        ? Collections.nCopies(inst.tid().b().min().intValue(), inst.tid(inst.tid().b(null))).stream()
                        : Stream.of(inst))
                .toList();
        final List<Inst> fused = new ArrayList<>(insts.size());
        boolean changed = false;
        for (int i = 0; i < insts.size(); i++) {
            final fURI seed = i + 1 < insts.size() ? seedAddress(insts.get(i)) : null;
            final int start = null == seed ? i : i + 1;
            // START A RUN WHERE THE TABLE HAS A STEP. The first SPLIT of this gate tested `!isNoObj()`, which
            // skipped exactly the table-backed instructions and left every chain uncompiled.
            if (start >= insts.size() || !isPushable(insts.get(start))) {
                fused.add(insts.get(i));
                continue;
            }
            int end = start + 1;
            while (end < insts.size() && isPushable(insts.get(end)))
                end++;
            // a mapped (unseeded) run leaves a barrier to mtron: see BARRIER_STEPS
            if (null == seed)
                while (end > start && BARRIER_STEPS.contains(gremlinStep(insts.get(end - 1))))
                    end--;
            if (end <= start) {
                fused.add(insts.get(i));
                continue;
            }
            final List<Inst> run = insts.subList(start, end);
            if (!ADJACENCY_STEPS.contains(run.getFirst().tid().name())
                    || run.size() < (null == seed ? 2 : 1)
                    || !isValidRun(run)
                    || run.stream().anyMatch(grphInstSet::isExternalLabel)) {
                fused.add(insts.get(i));
                continue;
            }
            fused.add(fusedGremlin(run, seed));
            changed = true;
            i = end - 1;
        }
        return changed ? code(fused) : code;
    }

    /**
     * {@code {V|E}/<id>} — the element part of a routed graph address, spelled through the space's own routes (an
     * address is {@code /g/V/1} for a path route and {@code g:V/1} for a scheme route; both are the same element).
     */
    private static DataPath elementPath(final fURI address) {
        return Machine.current().memory().getSpaceFor(address) instanceof grphSpace space
                ? DataPath.withoutDB(Space.Helper.routeFromSpace(address, space.routes()))
                : null;
    }

    /**
     * The element address a run's seed read names, when it lands in a graph space; else null. A CONCRETE address
     * ({@code V/1}) folds one element, and an element-kind WILDCARD ({@code V/+}) folds them all: {@code g:V/+}
     * compiles to {@code g.V()}, so the read and the walk are one traversal.
     */
    private static fURI seedAddress(final Inst inst) {
        if (!FROM.equals(inst.tid().name())) return null;      // @<uri> names the address, it does not read it
        final Obj arg = inst.arg(0);
        if (!arg.isUri()) return null;
        final fURI address = arg.uriValue();
        final DataPath path = elementPath(address);
        if (null == path || !path.hasCollection() || !path.hasEntry() || path.hasExtension()) return null;
        return "V".equalsIgnoreCase(path.collection()) || "E".equalsIgnoreCase(path.collection()) ? address : null;
    }

    /**
     * The single {@code gremlin(...)} call a run becomes. A folded seed makes it INITIAL — dom {@code noobj}, the
     * address as the first argument, and the complete script — so the read and the whole walk are one round trip.
     * Without a seed it maps the element lhs it is applied to. The rng follows the run's last step, and the run's
     * points ride on the call, so {@code outE{2}(k).inV()} keeps its coefficient. A step's {@code c()} is its span
     * times its point, and the script already walks the span ({@code repeat()}): the call counts the points alone.
     */
    private static Inst fusedGremlin(final List<Inst> run, final fURI seed) {
        final cInt coefficient = run.stream().map(inst -> inst.tid().d()).reduce(cInt.ONE(), cInt::mult);
        final cInt coefficiented = coefficient.isOne() ? null : coefficient;
        final String suffix = gremlinSuffix(run);
        // a mapped call takes ONE element, as its first step does: the swarm feeds it each obj, and the processor
        // carries a bulked element's count onto its walk ({2}josh walks as two joshes). A rec{*} dom made it a barrier
        // whose body walked {2}josh once
        if (null == seed)
            return instC(GREMLIN_INST_TID.dom(REC_TID).rng(terminalType(run)).c(coefficiented),
                    lst(str(suffix)),
                    (lhs, inst) -> gremlin(lhs, inst.arg(0).strValue()));
        final DataPath address = elementPath(seed);
        return instC(GREMLIN_INST_TID.dom(NOOBJ_TID).rng(terminalType(run)).c(coefficiented),
                lst(uri(seed), str(elementSeed(address) + "." + suffix)),
                (_, inst) -> Machine.current().memory().getSpaceFor(inst.arg(0).uriValue()) instanceof grphSpace space
                        ? runGremlin(space, inst.arg(1).strValue())
                        : fail(MTronException.of("a fused gremlin traversal needs a graph address, not %s", address)));
    }

    /**
     * The type a fused run hands on: element recs, a single int for count, a value stream for a projection.
     */
    private static fURI terminalType(final List<Inst> run) {
        return switch (gremlinStep(run.getLast())) {
            case "out", "in", "both", "outE", "inE", "bothE", "inV", "outV", "bothV" -> REC_TID.maybeSome();
            case "count" -> INT_TID;
            // a projection hands on property values: their types are the schema's, not the walk's
            default -> ALL.maybeSome();
        };
    }

    /**
     * The run as a Gremlin traversal suffix — the artifact {@code explain()} shows and {@code gremlin()} accepts.
     * <p>
     * A RANGED OR OPEN SPAN IS GREMLIN'S {@code repeat()}, the walk {@link Inst.Helper#runs} takes: each loop is one
     * step past the last, and the runs from the span's min on are emitted, the zero-step run by an {@code emit()}
     * before the loop, the runs from one on by an {@code emit()} after it, and those from m on by
     * {@code emit(loops().is(gte(m)))}. A max ends the loop after that many steps ({@code times(n)} hands on the last
     * run), and an open span loops until no traverser is left, where the walk halts: {@code {0,2}out()} is
     * {@code emit().repeat(out()).times(2)}, and {@code {2,}out()} is {@code repeat(out()).emit(loops().is(gte(2)))}.
     */
    private static String gremlinSuffix(final List<Inst> run) {
        final List<String> steps = new ArrayList<>(run.size());
        for (final Inst inst : run) {
            final String template = getGremlinRewrite(inst.tid().name()).strValue();
            // an endpoint step with no label filters nothing, and Gremlin's hasLabel() takes at least one label
            final String step = (template.contains("%s") ? template.formatted(quotedArguments(inst)) : template)
                    .replace("hasLabel().", "");
            final cInt span = inst.tid().b();
            steps.add(span.isOne() ? step : (0L == span.min() ? "emit()." : "") + "repeat(" + step + ")"
                    + (1L == span.min() ? ".emit()" : 1L < span.min() ? ".emit(loops().is(gte(" + span.min() + ")))" : "")
                    + (null == span.max() ? "" : ".times(" + span.max() + ")"));
        }
        return String.join(".", steps);
    }

    /**
     * An instruction's arguments as a Gremlin argument list: {@code knows,created} ⇒ {@code 'knows','created'}. A
     * pattern ({@code +}, {@code #}) takes every label or key, as the walk reads it, so the list is then empty:
     * {@code outE(+)} is Gremlin's {@code outE()}, never {@code outE('+')}, which no edge is labeled.
     */
    private static String quotedArguments(final Inst inst) {
        final List<String> arguments = new ArrayList<>();
        for (final Obj arg : inst.args().lstValue()) {
            if (arg.isUri() && arg.uriValue().hasPattern())
                return "";
            if (arg.isUri() || arg.isStr())
                arguments.add("'" + (arg.isUri() ? arg.uriValue().name() : arg.strValue()) + "'");
        }
        return String.join(",", arguments);
    }

    /// ── GREMLIN EXECUTION ──────────────────────────────────────────────────────────────────────────────────

    /**
     * One script engine per thread: a factory+manager per evaluation is pure setup cost.
     */
    private static final ThreadLocal<GremlinScriptEngine> GREMLIN_ENGINE =
            ThreadLocal.withInitial(() -> {
                final GremlinLangScriptEngineFactory factory = new GremlinLangScriptEngineFactory();
                factory.setCustomizerManager(new DefaultGremlinScriptEngineManager());
                return factory.getScriptEngine();
            });

    /**
     * Run a Gremlin expression. A script beginning with {@code g} is COMPLETE and executes on the graph space it
     * is applied to; anything else is a TRAVERSAL SUFFIX walked from the lhs element — from the live element, or
     * from the routed address when the value crossed the wire as data.
     * <p>
     * The lhs may be ONE element or MANY: a wildcard read hands the suffix an {@link studio.phaseshift.metatron.isa.m.type.Objs},
     * and {@link Obj#stream()} walks both shapes the same way, one traversal per element. Elements come back as
     * this space's recs (and a {@code !route} vertex resolves to what it points at).
     */
    private static Obj gremlin(final Obj lhs, final String expression0) {
        final String expression = expression0.trim();
        if (expression.startsWith("g.") || expression.startsWith("g[")) {
            if (!(lhs instanceof grphSpace space))
                return fail(MTronException.of("a complete gremlin script is applied to a graph space, not %s", lhs));
            return runGremlin(space, expression);
        }
        final List<Obj> walks = new ArrayList<>();
        lhs.stream().forEach(element -> walks.add(suffixWalk(element, expression)));
        for (final Obj walk : walks)
            if (walk.isFail())
                return walk;     // a member that is not a graph element is the failure, not an objs of it
        return objs(walks.stream());
    }

    /**
     * ONE element's suffix walk: its live element, else the address a data rec carries.
     */
    private static Obj suffixWalk(final Obj element, final String expression) {
        final String traversal = expression.startsWith(".") ? expression : "." + expression;
        if (element instanceof ElementRec<?> live)
            return runGremlin(live.space(),
                    elementSeed(live instanceof EdgeRec, String.valueOf(live.element().id())) + traversal);
        if (element.isRec() && null != element.vid()
                && Machine.current().memory().getSpaceFor(element.vid()) instanceof grphSpace space) {
            // data that crossed the wire: the address still names the space and the element
            final DataPath dp = elementPath(element.vid());
            if (null != dp && dp.hasCollection() && dp.hasEntry())
                return runGremlin(space, elementSeed(dp) + traversal);
        }
        return fail(MTronException.of("a gremlin suffix needs a graph element, not %s", element));
    }

    /**
     * One script, one space: the traversal engine's single door, mapping elements back to this space's recs.
     * <p>
     * AN INTERRUPT ENDS THE TRAVERSAL AS A FAIL, AND STAYS SET (2026-11-16), as the span walk answers one: an open span
     * on a cycle ({@code {*}both()}) is a {@code repeat()} that never runs dry. TinkerPop asks once a traverser and
     * clears the flag as it throws, so the flag is set again for the callers above (a mapped call is one traversal per
     * element, and the next one would loop on). A bulked traverser hands out its object once per bulk without asking, so
     * the walk asks once an element.
     */
    private static Obj runGremlin(final grphSpace space, final String script) {
        try {
            final GremlinScriptEngine engine = GREMLIN_ENGINE.get();
            engine.put("g", space.sjvm());
            final Object result = engine.eval(script);
            if (result instanceof Traversal<?, ?> traversal)
                return objs(IteratorUtils.stream(traversal.asAdmin()).map(o -> {
                    if (Thread.currentThread().isInterrupted())
                        throw new TraversalInterruptedException();
                    return o instanceof Vertex v
                            // a !route vertex is a pointer: answer what it points at, as inV/outV does
                            ? EdgeRec.dereference(space, v)
                            : o instanceof Edge e ? new EdgeRec(e, space) : MObjFactory.single().toObj(o);
                }));
            return MObjFactory.single().toObj(result);
        } catch (final TraversalInterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("gremlin traversal interrupted: %s", script);
        } catch (final Exception e) {
            return fail(e);
        }
    }

    /**
     * {@code g.V(<id>)} / {@code g.E(<id>)} — one element's seed (a numeric id is a literal, else quoted).
     */
    private static String elementSeed(final boolean edge, final String id) {
        return edge ? "g.E(" + idLiteral(id) + ")" : "g.V(" + idLiteral(id) + ")";
    }

    /**
     * {@code g.V()} / {@code g.V(<id>)} — the seed an element ADDRESS names. A wildcard entry ({@code V/+}) is ALL
     * of them, so {@code g:V/+.out(knows)} compiles to the single traversal {@code g.V().out('knows')}.
     */
    private static String elementSeed(final DataPath address) {
        final boolean edge = "E".equalsIgnoreCase(address.collection());
        final String entry = address.entry();
        return "+".equals(entry) || "#".equals(entry)
                ? (edge ? "g.E()" : "g.V()")
                : elementSeed(edge, entry);
    }

    /**
     * A TinkerPop id literal: a number bare, anything else (a UUID, a name) quoted.
     */
    private static String idLiteral(final String id) {
        return id.matches("-?\\d+") ? id : "'" + id + "'";
    }


    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setup() {
        // A PLAN READS ELEMENTS (2026-11-25), so it declines a source the space reads flat: kv_store, and a collection no
        // element carries. After two flat writes, */g/kv_scratch/+.count() walks 2 and its label count answered 0
        final BiPredicate<grphSpace, List<Inst>> elementGuard = (space, matches) -> !matches.getFirst().arg(0).isUri()
                || !space.readsFlat(space.redirect(matches.getFirst().arg(0).uriValue(), true));
        this.jvm().putAll(mutableMap(
                uri(CONSTQ), lst(ObjTP3Serializer.single(), MODERN_SCHEMA),
                uri(TYPE), lst(
                        GREMLIN_THEORY_TYPE,
                        docWrap(GRPH_SPACE_TYPE, "a space for graph traversal")
                ),
                uri(INST), lst(
                        docWrap(instC(GREMLIN_INST_TID.dom(REC_TID).rng(ALL.maybeSome()), lst(STR_TYPE),
                                        (lhs, inst) -> gremlin(lhs, inst.arg(0).strValue())),
                                "a graph space or element", "the gremlin result", Map.of(),
                                "executes a gremlin expression: a full script (\"g.V().count()\") on a graph space, or a traversal suffix (\"out('knows').values('name')\") walked from the lhs element — the form a fused run compiles to"),
                        docWrap(instC(LABEL_INST_TID.dom(REC_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL).orElse(uri(lhs.tid()))),
                                "an element", "the element label", Map.of(), "returns the lhs element label (the tid)"),
                        docWrap(instC(VALUES_INST_TID.dom(REC_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                                        inst.arg(0).isNoObj() ? lhs.asRec().at(uri("+")) : objs(inst.args().valueElements().map(key -> lhs.asRec().at(key)))),
                                "an element", "the element values", mutableMap(jnt(0), "zero or more element property labels"), "returns the lhs element arg-labeled values"),
                        InstBuilder.build()
                                .tid(INV_INST_TID)
                                .dom(REC_TID, "an edge")
                                .rng(REC_TID.maybe(), "the incoming vertex, or nothing when the edge's label is not among the arg labels")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels: the endpoint only of an edge whose label is among them")
                                .desc("returns the lhs edge head vertex, if the edge's label is among the arg labels")
                                .body((lhs, inst) -> labeled(lhs, inst) ? edgeVertex(lhs, Direction.IN) : noobj())
                                .converse(INE_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        InstBuilder.build()
                                .tid(OUTV_INST_TID)
                                .dom(REC_TID, "an edge")
                                .rng(REC_TID.maybe(), "the outgoing vertex, or nothing when the edge's label is not among the arg labels")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels: the endpoint only of an edge whose label is among them")
                                .desc("returns the lhs edge tail vertex, if the edge's label is among the arg labels")
                                .body((lhs, inst) -> labeled(lhs, inst) ? edgeVertex(lhs, Direction.OUT) : noobj())
                                .converse(OUTE_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        InstBuilder.build()
                                .tid(BOTHV_INST_TID)
                                .dom(REC_TID, "an edge")
                                // rec{*}, as out/in/both state theirs: a declared rec{2} failed the range check on the first
                                // answer (a stream of two distinct recs), order-dependently (2026-11-22)
                                .rng(REC_TID.maybeSome(), "both vertices: the edge's head and tail")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels: the endpoint only of an edge whose label is among them")
                                .desc("returns the lhs edge's head and tail vertices, if the edge's label is among the arg labels")
                                .body((lhs, inst) -> labeled(lhs, inst) ? edgeVertices(lhs) : noobj())
                                .converse(BOTHE_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                    /*    docWrap(instC(GRPH_INST_TID.extend("graph").dom(ALL.maybe()).rng(GRAPH_SPACE_TID),
                                        lst(GRAPH_CONFIG),
                                        (lhs, inst) -> grphSpace.of(inst.arg(0).asRec(), lhs.vid())),
                                "a graph space", "the graph space", Map.of(jnt(0), "the graph configuration"), "a space for graph traversal"),*/
                        InstBuilder.build()
                                .tid(OUT_INST_TID)
                                .dom(REC_TID, "a vertex")
                                .rng(REC_TID.maybeSome(), "out adjacent vertices")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels")
                                .desc("returns the lhs vertex arg-adjacent outgoing vertices")
                                .body(V_V_FUNCTION(Direction.OUT))
                                .converse(IN_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        InstBuilder.build()
                                .tid(IN_INST_TID)
                                .dom(REC_TID, "a vertex")
                                .rng(REC_TID.maybeSome(), "in adjacent vertices")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels")
                                .desc("returns the lhs vertex arg-adjacent incoming vertices")
                                .body(V_V_FUNCTION(Direction.IN))
                                .converse(OUT_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        InstBuilder.build()
                                .tid(BOTH_INST_TID)
                                .dom(REC_TID, "a vertex")
                                .rng(REC_TID.maybeSome(), "both adjacent vertices")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels")
                                .desc("returns the lhs vertex arg-adjacent incoming and outgoing vertices")
                                .body(V_V_FUNCTION(Direction.BOTH))
                                .converse(BOTH_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        InstBuilder.build()
                                .tid(OUTE_INST_TID)
                                .dom(REC_TID, "a vertex")
                                .rng(REC_TID.maybeSome(), "out adjacent edges")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels")
                                .desc("returns the lhs vertex arg-adjacent outgoing edges")
                                .body(V_E_FUNCTION(Direction.OUT))
                                .converse(OUTV_INST_TID.dom(REC_TID).rng(REC_TID.maybe()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        InstBuilder.build()
                                .tid(INE_INST_TID)
                                .dom(REC_TID, "a vertex")
                                .rng(REC_TID.maybeSome(), "in adjacent edges")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels")
                                .desc("returns the lhs vertex arg-adjacent incoming edges")
                                .body(V_E_FUNCTION(Direction.IN))
                                .converse(INV_INST_TID.dom(REC_TID).rng(REC_TID.maybe()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        InstBuilder.build()
                                .tid(BOTHE_INST_TID)
                                .dom(REC_TID, "a vertex")
                                .rng(REC_TID.maybeSome(), "both adjacent edges")
                                .argLst(T(URI_TID.maybeSome()), "zero or more edge labels")
                                .desc("returns the lhs vertex arg-adjacent incoming and outgoing edges")
                                .body(V_E_FUNCTION(Direction.BOTH))
                                .converse(BOTHV_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()).args(URI_TID.maybeSome()))
                                .law(Law.pure)
                                .create(),
                        instC(ADDE_INST_TID.dom(REC_TID).rng(REC_TID), lst(URI_TYPE, T(REC_TID.some()), T(REC_TID.maybe())), (lhs, inst) -> {
                            if (!(lhs instanceof VertexRec vr)) return noobj();
                            final Vertex outVertex = vr.vertex();
                            final grphSpace graphSpace = vr.space();
                            final GraphTraversalSource g = graphSpace.sjvm();
                            final fURI edgeLabel = inst.arg(0).uriValue().big();
                            final String label = edgeLabel.small().toString();
                            return objs(inst.arg(1).stream().map(otherV -> {
                                final Vertex inVertex;
                                if (otherV instanceof VertexRec otherVRec) {
                                    inVertex = otherVRec.vertex();
                                } else {
                                    final Optional<fURI> pointer = Obj.Helper.getPointer(otherV);
                                    if (pointer.isPresent()) {
                                        inVertex = g.addV(otherV.apply(noobj()).tid().big().toString())
                                                .property(AUTO_ROUTE_STRING, SERIALIZER.write(auto_from_(pointer.get())))
                                                .next();
                                    } else {
                                        throw MTronException.of("invalid edge vertex: %s", otherV);
                                    }
                                }
                                final Edge edge = g.V(outVertex.id()).addE(label).to(inVertex).next();
                                if (inst.arg(2).isRec()) {
                                    inst.arg(2).asRec().jvm().forEach((key, value) -> edge.property(key.uriValue().toString(), value.jvm()));
                                }
                                return new EdgeRec(edge, graphSpace).tid(edgeLabel);
                            }));
                        })),
                uri(REWRITE), lst(
                        docWrap(CommonRewrites.countRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_count"),
                                (space, dp) -> space.elements(dp.collection()).count().next(),
                                elementGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's count()-reducer for vrtx, edge and label collections"),
                        docWrap(CommonRewrites.sumColumnRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_sum"),
                                (grphSpace space, DataPath dp) -> {
                                    final String field = dp.field();
                                    if (field == null || field.isEmpty())
                                        return 0L;
                                    // nothing to sum is the sum's seed, as sql_sum_column's SUM over no row answers
                                    return space.elements(dp.collection()).values(field).sum().tryNext().orElse(0L);
                                },
                                elementGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's sum()-reducer over an element property"),
                        docWrap(CommonRewrites.skipRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_skip"),
                                (space, dp, skip) -> objs(IteratorUtil.stream(space.elements(dp.collection()).skip(skip))
                                        .map(space::elementRec)),
                                elementGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's skip()-barrier for vrtx, edge and label collections"),
                        docWrap(CommonRewrites.offsetLimitRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_skip"),
                                GRPH_REWRITE_TID.extend("gremlin_skip_limit"),
                                (space, dp, skip, limit) -> objs(IteratorUtil.stream(space.elements(dp.collection()).skip(skip).limit(limit))
                                        .map(space::elementRec))
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "fuses skip+limit into a single gremlin skip().limit()"),
                        docWrap(CommonRewrites.orderRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_order"),
                                // ONE SORTED LST, as order() answers and sql_order does: the >- after it splits the lst into
                                // its elements. A stream of vertices let >- split each vertex into its properties instead
                                (space, dp, columns) -> {
                                    final Iterator<? extends Element> ordered = null == columns || columns.isEmpty()
                                            ? space.elements(dp.collection()).order()
                                            : space.elements(dp.collection()).order().by(columns.get(0));
                                    return lst(IteratorUtil.stream(ordered)
                                            .map(e -> (Obj) space.elementRec(e))
                                            .toList());
                                },
                                elementGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's order()-by for vrtx, edge and label collections"),
                        docWrap(CommonRewrites.dedupRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_dedup"),
                                (space, dp, columns) -> {
                                    final Iterator<? extends Element> deduped = null == columns || columns.isEmpty()
                                            ? space.elements(dp.collection()).dedup()
                                            : space.elements(dp.collection()).dedup().by(columns.get(0));
                                    return objs(IteratorUtil.stream(deduped)
                                            .map(space::elementRec));
                                },
                                elementGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's dedup()-by for vrtx, edge and label collections"),
                        docWrap(CommonRewrites.limitRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_limit"),
                                (space, dp, limit) -> objs(IteratorUtil.stream(space.elements(dp.collection()).limit(limit))
                                        .map(space::elementRec)),
                                elementGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's limit()-barrier for vrtx, edge and label collections"),

                        // Optimize: from(<collection>/+).isa([field=>value]) → has(field, predicate) over the collection's elements
                        docWrap(CommonRewrites.whereRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_where").dom(GRPH_SPACE_TID).rng(REC_TID.maybeSome()),
                                (space, dp, filterClause) -> objs(IteratorUtil.stream(whereTraversal(space, dp, filterClause))
                                        .map(space::elementRec)),
                                GremlinRewriteUtils.PREDICATE_JOINER,
                                GremlinRewriteUtils.CONDITION_FORMATTER,
                                elementGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has()-filtering for vrtx, edge and label collections"),

                        // THE SHARED FUSIONS, as tble's and dcmnt's: each matches the native where by its address, never a
                        // name that merely contains it (gremlin_where_limit.count() is not a filtered count), mints a source
                        // op ({0}, not #{*}, which the processor ran as a barrier and counted twice), and queries when it
                        // runs, never while it rewrites
                        docWrap(CommonRewrites.whereCountRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_where"),
                                GRPH_REWRITE_TID.extend("gremlin_where_count"),
                                (space, dp, filterClause) -> whereTraversal(space, dp, filterClause).count().next()
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has().count() filtering-reduction for vrtx, edge and label collections"),
                        docWrap(CommonRewrites.whereLimitRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_where"),
                                GRPH_REWRITE_TID.extend("gremlin_where_limit"),
                                (space, dp, filterClause, limit) -> objs(IteratorUtil.stream(whereTraversal(space, dp, filterClause).limit(limit))
                                        .map(space::elementRec))
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has().limit() filtering-barrier for vrtx, edge and label collections"),
                        // ONE SERVER-SIDE TRAVERSAL PER RUN: a maximal chain of instructions that has a Gremlin step
                        // in GREMLIN_THEORY becomes a single gremlin(...) call (this subsumes the old outE+inV→out /
                        // inE+outV→in pair rules, and keeps the pair's label AND the run's coefficient).
                        docWrap(InstSet.Helper.rewriter(GRPH_REWRITE_TID.extend("gremlin_pushdown"),
                                        grphInstSet::fuseGremlinRuns),
                                "pre-rewrite code", "post-rewrite code", Map.of(),
                                "compiles a maximal run of Gremlin-backed instructions into one gremlin(...) call"))));
        docWrap(this, "from vertex to vertex, the edge of the metatron is traversed");
        super.setup();
        modernSchema.TYPES.forEach(type -> Machine.current().memory().write(type.vid(), type));
    }

    /**
     * THE TRAVERSAL A PUSHED-DOWN WHERE STARTS FROM: {@code has(field, predicate)} over the collection's elements, for the
     * clause the where rewrite formatted. The where op, its count and its limit read it, so each refuses an unreadable
     * clause alike.
     */
    private static GraphTraversal<? extends Element, ? extends Element> whereTraversal(final grphSpace space, final DataPath dp, final String filterClause) {
        final org.apache.tinkerpop.gremlin.process.traversal.P<?> pred = parseGremlinPredicate(filterClause);
        final String field = extractPredicateField(filterClause);
        if (pred == null || field == null)
            throw MTronException.of("unable to parse where-predicate: %s", filterClause);
        return space.elements(dp.collection()).has(field, pred);
    }

    /**
     * Parse a SQL-like WHERE clause into a Gremlin P predicate.
     * Handles: "field > value", "field = value", "field < value",
     * "field >= value", "field <= value", "field <> value".
     */
    private static org.apache.tinkerpop.gremlin.process.traversal.P<?> parseGremlinPredicate(final String whereClause) {
        if (whereClause == null || whereClause.isBlank()) return null;
        final String[] ops = {">=", "<=", "<>", ">", "<", "="};
        for (final String op : ops) {
            final int idx = whereClause.indexOf(op);
            if (idx > 0) {
                final String valueStr = whereClause.substring(idx + op.length()).trim();
                final Object value = parsePredicateValue(valueStr);
                return switch (op) {
                    case "=" -> org.apache.tinkerpop.gremlin.process.traversal.P.eq(value);
                    case ">" -> org.apache.tinkerpop.gremlin.process.traversal.P.gt((Comparable) value);
                    case "<" -> org.apache.tinkerpop.gremlin.process.traversal.P.lt((Comparable) value);
                    case ">=" -> org.apache.tinkerpop.gremlin.process.traversal.P.gte((Comparable) value);
                    case "<=" -> org.apache.tinkerpop.gremlin.process.traversal.P.lte((Comparable) value);
                    case "<>" -> org.apache.tinkerpop.gremlin.process.traversal.P.neq(value);
                    default -> null;
                };
            }
        }
        return null;
    }

    /**
     * Extract the field name from a SQL WHERE condition like "value > 5"
     */
    private static String extractPredicateField(final String whereClause) {
        if (whereClause == null || whereClause.isBlank()) return null;
        final String[] ops = {">=", "<=", "<>", ">", "<", "=", " IS NOT NULL"};
        for (final String op : ops) {
            final int idx = whereClause.indexOf(op);
            if (idx > 0) return whereClause.substring(0, idx).trim();
        }
        return null;
    }

    /**
     * Parse a value string: handle quoted strings, booleans, numbers
     */
    private static Object parsePredicateValue(final String valueStr) {
        if (valueStr.startsWith("'") && valueStr.endsWith("'"))
            return valueStr.substring(1, valueStr.length() - 1).replace("''", "'");
        if ("TRUE".equalsIgnoreCase(valueStr)) return true;
        if ("FALSE".equalsIgnoreCase(valueStr)) return false;
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(valueStr);
        } catch (NumberFormatException ignored) {
        }
        return valueStr;
    }
}
