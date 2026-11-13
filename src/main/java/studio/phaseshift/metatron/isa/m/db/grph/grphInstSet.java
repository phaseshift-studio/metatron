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
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.rewrite.RewriterBuilder;
import studio.phaseshift.metatron.algebra.rewrite.db.CommonRewrites;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
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
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace.*;
import static studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace.SERIALIZER;
import static studio.phaseshift.metatron.isa.m.db.grph.space.schema.modernSchema.MODERN_SCHEMA_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
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
     * The edge labels an adjacency inst carries: a concrete uri for a labelled call ({@code out(knows)}),
     * nothing when the slot is empty OR still holds the DECLARED arg type ({@code uri{*}::T}) of an
     * unbound inst — a type is not a label.
     */
    private static String[] edgeLabels(final Inst inst) {
        final Obj arg = inst.arg(0);
        return arg.isUri() ? new String[]{arg.uriValue().name()} : new String[0];
    }

    /**
     * A label naming another space ({@code te:hot}) is routed by the space's own depth walk
     * ({@code grphSpace.traverseVertex}), not by Gremlin: such a hop keeps the address walk and declines fusion.
     */
    private static boolean isExternalLabel(final Inst inst) {
        final Obj arg = inst.arg(0);
        return arg.isUri() && arg.uriValue().toString().indexOf(':') > 0;
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
        fURI path = base.extend(direction.name());
        final String[] labels = edgeLabels(inst);
        if (labels.length > 0)
            path = path.extend(labels[0]);
        return Machine.read(path);
    }

    private static Obj routeVertexTraversal(final Obj lhs, final Inst inst, final Direction direction) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        fURI path = base.extend(direction.name());
        final String[] labels = edgeLabels(inst);
        if (labels.length > 0)
            path = path.extend(labels[0]);
        path = path.extend(direction.opposite().name());
        return Machine.read(path);
    }

    private static Obj routeBothTraversal(final Obj lhs, final Inst inst) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final String[] labels = edgeLabels(inst);
        // a vertex's OUT/IN segment is its EDGES, so the OPPOSITE hop is what lands on vertices
        final String label = labels.length > 0 ? labels[0] : "+";
        return objs(Stream.concat(
                Machine.read(base.extend("OUT").extend(label).extend(Tokens.IN)).stream(),
                Machine.read(base.extend("IN").extend(label).extend(Tokens.OUT)).stream()));
    }

    private static Obj routeBothETraversal(final Obj lhs, final Inst inst) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final String[] labels = edgeLabels(inst);
        final Obj outResult = Machine.read(labels.length > 0
                ? base.extend("OUT").extend(labels[0]) : base.extend("OUT"));
        final Obj inResult = Machine.read(labels.length > 0
                ? base.extend("IN").extend(labels[0]) : base.extend("IN"));
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
                            uri("inV"), str("inV()"),
                            uri("outV"), str("outV()"),
                            uri("bothV"), str("bothV()"),
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
        final String template = getGremlinRewrite(inst.tid().name()).strValue();
        final int paren = template.indexOf('(');
        return paren < 0 ? template : template.substring(0, paren);
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
     * A step a run can carry into Gremlin: the table has a step for it, and it has NO SPAN. A span is a run of steps,
     * not a count ({@code {0,2}out()} is {@code _ + out() + out().out()}), and one Gremlin step says neither: the fold
     * rendered it once and multiplied its answers by the span. So a spanned step BREAKS THE RUN (Marko, 2026-11-13):
     * what comes before it is pushed down, and the processor runs the span. Until a span renders as Gremlin's
     * {@code repeat()} or {@code union()}.
     */
    private static boolean isPushable(final Inst inst) {
        return inst.tid().b().isOne() && !getGremlinRewrite(inst.tid().name()).isNoObj();
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
        final List<Inst> insts = code.insts();
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
     * multiplicity rides on the call, so {@code outE{2}(k).inV()} keeps its coefficient.
     */
    private static Inst fusedGremlin(final List<Inst> run, final fURI seed) {
        final cInt coefficient = run.stream().map(Inst::c).reduce(cInt.ONE(), cInt::mult);
        final cInt coefficiented = coefficient.isOne() ? null : coefficient;
        final String suffix = gremlinSuffix(run);
        if (null == seed)
            return instC(GREMLIN_INST_TID.dom(REC_TID.maybeSome()).rng(terminalType(run)).c(coefficiented),
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
     */
    private static String gremlinSuffix(final List<Inst> run) {
        final List<String> steps = new ArrayList<>(run.size());
        for (final Inst inst : run) {
            final String template = getGremlinRewrite(inst.tid().name()).strValue();
            steps.add(template.contains("%s") ? template.formatted(quotedArguments(inst)) : template);
        }
        return String.join(".", steps);
    }

    /**
     * An instruction's arguments as a Gremlin argument list: {@code knows,created} ⇒ {@code 'knows','created'}.
     */
    private static String quotedArguments(final Inst inst) {
        final List<String> arguments = new ArrayList<>();
        for (final Obj arg : inst.args().lstValue())
            if (arg.isUri() || arg.isStr())
                arguments.add("'" + (arg.isUri() ? arg.uriValue().name() : arg.strValue()) + "'");
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
            if (walk.isFail()) return walk;     // a member that is not a graph element is the failure, not an objs of it
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
     */
    private static Obj runGremlin(final grphSpace space, final String script) {
        try {
            final GremlinScriptEngine engine = GREMLIN_ENGINE.get();
            engine.put("g", space.sjvm());
            final Object result = engine.eval(script);
            if (result instanceof Traversal<?, ?> traversal)
                return objs(IteratorUtils.stream(traversal.asAdmin()).map(o -> o instanceof Vertex v
                        // a !route vertex is a pointer: answer what it points at, as inV/outV does
                        ? EdgeRec.dereference(space, v)
                        : o instanceof Edge e ? new EdgeRec(e, space) : MObjFactory.single().toObj(o)));
            return MObjFactory.single().toObj(result);
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

    /** A TinkerPop id literal: a number bare, anything else (a UUID, a name) quoted. */
    private static String idLiteral(final String id) {
        return id.matches("-?\\d+") ? id : "'" + id + "'";
    }


    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(CONSTQ), lst(ObjTP3Serializer.single()),
                uri(TYPE), lst(
                        GREMLIN_THEORY_TYPE,
                        docWrap(GRPH_SPACE_TYPE, "a space for graph traversal"),
                        docWrap(MODERN_SCHEMA_TYPE, "a schema for the modern graph dataset")
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
                        docWrap(instC(INV_INST_TID.dom(REC_TID).rng(REC_TID), lst(), (lhs, inst) -> edgeVertex(lhs, Direction.IN)),
                                "an edge", "the incoming vertex", Map.of(), "returns the lhs edge head vertex"),
                        docWrap(instC(OUTV_INST_TID.dom(REC_TID).rng(REC_TID), lst(), (lhs, inst) -> edgeVertex(lhs, Direction.OUT)),
                                "an edge", "the outgoing vertex", Map.of(), "returns the lhs edge tail vertex"),
                        docWrap(instC(BOTHV_INST_TID.dom(REC_TID).rng(REC_TID.c(cInt.of(2))), lst(), (lhs, inst) -> edgeVertices(lhs)),
                                "an edge", "both vertices", Map.of(), "returns the lhs edge's head and tail vertices"),
                    /*    docWrap(instC(GRPH_INST_TID.extend("graph").dom(ALL.maybe()).rng(GRAPH_SPACE_TID),
                                        lst(GRAPH_CONFIG),
                                        (lhs, inst) -> grphSpace.of(inst.arg(0).asRec(), lhs.vid())),
                                "a graph space", "the graph space", Map.of(jnt(0), "the graph configuration"), "a space for graph traversal"),*/
                        docWrap(InstBuilder.declareConverse(instC(OUT_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.OUT)), IN_INST_TID),
                                "a vertex", "out adjacent vertices", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing vertices"),
                        docWrap(InstBuilder.declareConverse(instC(IN_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.IN)), OUT_INST_TID),
                                "a vertex", "in adjacent vertices", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming vertices"),
                        docWrap(InstBuilder.declareConverse(instC(BOTH_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.BOTH)), BOTH_INST_TID),
                                "a vertex", "both adjacent vertices", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing vertices"),
                        docWrap(instC(OUTE_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.OUT)),
                                "a vertex", "out adjacent edges", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing edges"),
                        docWrap(instC(INE_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.IN)),
                                "a vertex", "in adjacent edges", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming edges"),
                        docWrap(instC(BOTHE_INST_TID.dom(REC_TID).rng(REC_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.BOTH)),
                                "a vertex", "both adjacent edges", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing edges"),
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
                                (space, dp) -> "V".equals(dp.collection())
                                        ? space.sjvm().V().count().next()
                                        : space.sjvm().E().count().next()
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's count()-reducer for vrtx/edge collections"),
                        docWrap(CommonRewrites.sumColumnRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_sum"),
                                (grphSpace space, DataPath dp) -> {
                                    final String field = dp.field();
                                    if (field == null || field.isEmpty())
                                        return 0L;
                                    return space.sjvm().V().values(field).sum().next();
                                },
                                null
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's sum()-reducer over a vertex property"),
                        docWrap(CommonRewrites.skipRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_skip"),
                                (space, dp, skip) -> {
                                    final Iterator<? extends Element> elements = "V".equals(dp.collection())
                                            ? space.sjvm().V().skip(skip)
                                            : space.sjvm().E().skip(skip);
                                    return objs(IteratorUtil.stream(elements).map(e -> (e instanceof Vertex v) ? new VertexRec(v, space) : new EdgeRec((Edge) e, space)));
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's skip()-barrier for vrtx/edge collections"),
                        docWrap(CommonRewrites.offsetLimitRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_skip"),
                                GRPH_REWRITE_TID.extend("gremlin_skip_limit"),
                                (space, dp, skip, limit) -> {
                                    final Iterator<? extends Element> elements = "V".equals(dp.collection())
                                            ? space.sjvm().V().skip(skip).limit(limit)
                                            : space.sjvm().E().skip(skip).limit(limit);
                                    return objs(IteratorUtil.stream(elements).map(e -> (e instanceof Vertex v) ? new VertexRec(v, space) : new EdgeRec((Edge) e, space)));
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "fuses skip+limit into a single gremlin skip().limit()"),
                        docWrap(CommonRewrites.orderRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_order"),
                                (space, dp, columns) -> {
                                    final GraphTraversalSource g = space.sjvm();
                                    if (columns == null || columns.isEmpty())
                                        return objs(IteratorUtil.stream(g.V().order()).map(v -> new VertexRec((Vertex) v, space)));
                                    return objs(IteratorUtil.stream(g.V().order().by(columns.get(0))).map(v -> new VertexRec((Vertex) v, space)));
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's order()-by for vrtx collections"),
                        docWrap(CommonRewrites.dedupRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_dedup"),
                                (space, dp, columns) -> {
                                    final GraphTraversalSource g = space.sjvm();
                                    if (columns == null || columns.isEmpty())
                                        return objs(IteratorUtil.stream(g.V().dedup()).map(v -> new VertexRec((Vertex) v, space)));
                                    return objs(IteratorUtil.stream(g.V().dedup().by(columns.get(0))).map(v -> new VertexRec((Vertex) v, space)));
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's dedup()-by for vrtx collections"),
                        docWrap(CommonRewrites.limitRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_limit"),
                                (space, dp, limit) -> {
                                    final Iterator<? extends Element> elements = "V".equals(dp.collection())
                                            ? space.sjvm().V().limit(limit)
                                            : space.sjvm().E().limit(limit);
                                    return objs(IteratorUtil.stream(elements).map(e -> (e instanceof Vertex v) ? new VertexRec(v, space) : new EdgeRec((Edge) e, space)));
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's limit()-barrier for vrtx/edge collections"),

                        // Optimize: from(V/+).isa([field=>value]) → g.V().has(field, predicate)
                        docWrap(CommonRewrites.whereRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_where").dom(GRPH_SPACE_TID).rng(REC_TID.maybeSome()),
                                (space, dp, filterClause) -> {
                                    if (!"V".equals(dp.collection()))
                                        throw MTronException.of("where-rewrite only supports the vertex (V) collection: %s", dp.collection());
                                    final org.apache.tinkerpop.gremlin.process.traversal.P<?> pred = parseGremlinPredicate(filterClause);
                                    final String field = extractPredicateField(filterClause);
                                    if (pred == null || field == null)
                                        throw MTronException.of("unable to parse where-predicate: %s", filterClause);
                                    return objs(IteratorUtil.stream(space.sjvm().V().has(field, pred)).map(v -> new VertexRec(v, space)));
                                }, GremlinRewriteUtils.PREDICATE_JOINER,
                                GremlinRewriteUtils.CONDITION_FORMATTER
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has()-filtering for vrtx collections"),

                        // Optimize: gremlin_where.count() → g.V().has(field, pred).count().next()
                        // Uses direct rewriter to match by TID substring (bypasses ?dom/?rng query-param mismatch)
                        docWrap(
                                InstSet.Helper.rewriter(
                                        GRPH_REWRITE_TID.extend("gremlin_where_count"),
                                        code -> code.selfJVM(
                                                RewriterBuilder.search(code.insts())
                                                        .match(List.of(instB(ALL, lst()), instB(COUNT_INST_TID, lst())))
                                                        .rewrite(map -> {
                                                            final java.util.List<Inst> matchList = new java.util.ArrayList<>(map.values());
                                                            final Inst whereInst = matchList.get(0);
                                                            // only compose if the first instruction is a gremlin_where
                                                            if (!whereInst.tid().toString().contains("gremlin_where"))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final Obj args = whereInst.args();
                                                            if (!args.isLst() || args.asLst().count() < 2)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final fURI furi = args.asLst().at(0).asUri().uriValue();
                                                            final String filterClause = args.asLst().at(1).asStr().jvm();
                                                            final studio.phaseshift.metatron.isa.Space space = Machine.current().memory().getSpaceFor(furi);
                                                            if (!(space instanceof grphSpace gs))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            //final DataPath dp = DataPath.withoutDB(furi);
                                                            final org.apache.tinkerpop.gremlin.process.traversal.P<?> pred = parseGremlinPredicate(filterClause);
                                                            final String field = extractPredicateField(filterClause);
                                                            if (pred == null || field == null)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final long count = gs.sjvm().V().has(field, pred).count().next();
                                                            return List.of(instC(
                                                                    GRPH_REWRITE_TID.extend("gremlin_where_count").dom(ALL_STAR).rng(INT_TID),
                                                                    lst(uri(furi), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(filterClause)),
                                                                    (lhs, inst) -> jnt(count)));
                                                        })
                                        ).asCode()),
                                "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has().count() filtering-reduction for vrtx collections"),

                        // Optimize: gremlin_where.take(n) → g.V().has(field, pred).limit(n)
                        docWrap(
                                InstSet.Helper.rewriter(
                                        GRPH_REWRITE_TID.extend("gremlin_where_limit"),
                                        code -> code.selfJVM(
                                                RewriterBuilder.search(code.insts())
                                                        .match(List.of(instB(ALL, lst()), instB(TAKE_INST_TID, lst())))
                                                        .rewrite(map -> {
                                                            final java.util.List<Inst> matchList = new java.util.ArrayList<>(map.values());
                                                            final Inst whereInst = matchList.get(0);
                                                            final Inst takeInst = matchList.get(1);
                                                            if (!whereInst.tid().toString().contains("gremlin_where"))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final Obj args = whereInst.args();
                                                            if (!args.isLst() || args.asLst().count() < 2)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final fURI furi = args.asLst().at(0).asUri().uriValue();
                                                            final String filterClause = args.asLst().at(1).asStr().jvm();
                                                            final long limit = takeInst.arg(0).asInt().jvm();
                                                            final studio.phaseshift.metatron.isa.Space space = Machine.current().memory().getSpaceFor(furi);
                                                            if (!(space instanceof grphSpace gs))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final DataPath dp = DataPath.withoutDB(furi);
                                                            final org.apache.tinkerpop.gremlin.process.traversal.P<?> pred = parseGremlinPredicate(filterClause);
                                                            final String field = extractPredicateField(filterClause);
                                                            if (pred == null || field == null)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            return List.of(instC(
                                                                    GRPH_REWRITE_TID.extend("gremlin_where_limit").dom(ALL_STAR).rng(ALL_STAR),
                                                                    lst(uri(furi), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(filterClause), jnt(limit)),
                                                                    (lhs, inst) -> objs(IteratorUtil.stream(
                                                                            gs.sjvm().V().has(field, pred).limit(limit)).map(v -> new VertexRec((Vertex) v, gs)))));
                                                        })
                                        ).asCode()),
                                "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has().limit() filtering-barrier for vrtx collections"),
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
