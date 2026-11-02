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

package studio.phaseshift.metatron.algebra.rewrite;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE DECISION MODEL — the distribution decision as three axes (a {@link PlaceSet}, a {@link Hop}, a
 * {@link Partition}), with the {@link Placement.Shape topology enum} kept as a derivation
 * ({@code docs/design/distributed-qparams-implementation.md} §1, chunk C3).
 *
 * <p>The one claim this class exists to prove is that the axes are a <em>refactor</em> of the enum, not a guess beside
 * it: {@link Placement#obligations(PlaceSet, Hop, Partition)} reproduces {@link Placement.Shape#obligations()} for
 * every member the axes carry, and the members they coarsen are named rather than forced.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DecisionModelTest extends AbstractInstSetTest {

    public DecisionModelTest() {
        super(catInstSet::new);
    }

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
        InstSet.importInstSet(f("/m/math"));
    }

    @BeforeEach
    public void installCategoryCompiler() {
        Machine.current().compiler(CatTheoryCompiler.single());
    }

    /**
     * The nine members of the recorded enum, built through the factories a plan uses today — the input side of the
     * migration.
     */
    private static Placement legacy(final String shape) {
        return switch (shape) {
            case "local" -> Placement.local(Span.at());
            case "peer" -> Placement.peer(f("ws://ginger.local:8555"), Span.at());
            case "peers" -> Placement.peers(Span.at());
            case "space" -> Placement.space(f("space://durable/store"), Span.at());
            case "tree" -> Placement.tree(2, Span.at());
            case "shuffle" -> Placement.shuffle(Span.at());
            case "chain" -> Placement.chain(f("loop://localhost:8555"), Span.at());
            case "star" -> Placement.star(f("//ginger:8555"), Span.at());
            case "none" -> Placement.none(Span.at());
            default -> throw new IllegalArgumentException(shape);
        };
    }

    private static Set<Law> laws(final String rendered) {
        if (null == rendered || rendered.isBlank())
            return Set.of();
        return java.util.Arrays.stream(rendered.split(","))
                .map(String::trim)
                .map(Law::valueOf)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String rendered(final Set<Law> laws) {
        return laws.stream().map(Law::name).sorted().collect(Collectors.joining(","));
    }

    /**
     * THE AXES EACH RECORDED MEMBER MIGRATES TO — the design's own "what it really is" table, read as data. Each row:
     * the shape % the place set's designators % the hop height % the hop arity % the partition kind % the laws the
     * decision demands. Every row also asserts the decision's obligations are the ones the enum declares, which is the
     * refactor's whole acceptance.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "local   % local                       % 0 % ** % stream % ",
            "none    %                             % 0 % ** % stream % ",
            "peer    % ws://ginger.local:8555       % 0 % ** % stream % commutative,monoidic",
            "peers   % peer                        % 0 % ** % stream % commutative,monoidic",
            "space   % space://durable/store        % 0 % ** % stream % ",
            "tree    % peer                        % 2 % ** % stream % commutative,monoidic",
            "shuffle % peer                        % 1 % ** % key    % monoidic",
            "chain   % loop://localhost:8555        % 0 % 1  % stream % monoidic",
            "star    % //ginger:8555                % 0 % ** % stream % commutative,monoidic",
    }, delimiter = '%')
    public void testTheAxesOfTheRecordedShape(final String shape, final String places, final int height,
                                              final String arity, final String split, final String derivedLaws) {
        final Placement placement = legacy(shape);
        assertEquals(null == places ? "" : places, String.join(",", placement.place().names()), "place(" + shape + ")");
        assertEquals(height, placement.hopAxis().height(), "hop height(" + shape + ")");
        assertEquals(arity, placement.hopAxis().arity().toString(), "hop arity(" + shape + ")");
        assertEquals(split, placement.split().kind().name().toLowerCase(), "split(" + shape + ")");
        // THE REFACTOR: the decision's laws are read off the axes, and they are the enum's declared set
        assertEquals(laws(derivedLaws), placement.obligations(), "obligations(" + shape + ")");
        assertEquals(Placement.Shape.valueOf(shape.toUpperCase()).obligations(), placement.obligations(),
                "the axes reproduce obligations(" + shape + ")");
        assertEquals(Placement.Shape.valueOf(shape.toUpperCase()), placement.shape(), "the recorded token(" + shape + ")");
    }

    /**
     * THE DERIVATION PROVED AGAINST THE ENUM — all nine members, read straight off the axes
     * ({@link Placement#obligations(PlaceSet, Hop, Partition)}) and compared one for one. Each row: the shape % the
     * laws its axes derive.
     *
     * <p>Eight rows derive their declared set exactly. The ninth is the recorded exception: <b>{@code space}</b>. A
     * landing ships a durable result and recombines nothing at home, so it demands nothing of the cut — but the axes
     * cannot say "landing" (the design defers SPACE past them), and an address at no depth derives the fan-out set.
     * Its declared {@code {}} therefore stands, and no fourth axis is invented to force the equality.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "local   % ",
            "none    % ",
            "peer    % commutative,monoidic",
            "peers   % commutative,monoidic",
            "space   % commutative,monoidic",
            "tree    % commutative,monoidic",
            "shuffle % monoidic",
            "chain   % monoidic",
            "star    % commutative,monoidic",
    }, delimiter = '%')
    public void testTheDerivationAgainstTheEnum(final String shape, final String derivedLaws) {
        final Placement placement = legacy(shape);
        final Set<Law> derived = Placement.obligations(placement.place(), placement.hopAxis(), placement.split());
        assertEquals(laws(derivedLaws), derived, "derived obligations(" + shape + ")");
        final Set<Law> declared = Placement.Shape.valueOf(shape.toUpperCase()).obligations();
        if ("space".equals(shape))
            assertNotEquals(declared, derived,
                    "SPACE is the recorded exception: a landing is not a fan-out, and the axes cannot say so");
        else
            assertEquals(declared, derived, "the derivation reproduces obligations(" + shape + ")");
    }

    /**
     * THE SHAPE PROJECTION — an axes-only rec (no recorded token) answers the enum member its axes stand for. Each row:
     * the member the axes were migrated from % the member they project back to. Eight project exactly; the two
     * coarsenings are recorded here rather than hidden:
     *
     * <ul>
     *   <li>{@code star} projects as {@code peer} — one named address at no depth is both a one-slave shard and a move
     *       fan-out, and what separates them (the cut, and the fact that a move has none) is not in the placement;</li>
     *   <li>{@code space} projects as {@code peer} too — a landing has no axis spelling yet, so a rec that states the
     *       axes alone does not say it (the recorded token does).</li>
     * </ul>
     */
    @ParameterizedTest
    @CsvSource(value = {
            "local   % local",
            "none    % none",
            "peer    % peer",
            "peers   % peers",
            "space   % peer",
            "tree    % tree",
            "shuffle % shuffle",
            "chain   % chain",
            "star    % peer",
    }, delimiter = '%')
    public void testTheShapeAnAxesOnlyRecProjects(final String shape, final String projected) {
        final Placement axes = axesOnly(legacy(shape));
        assertEquals(Placement.Shape.valueOf(projected.toUpperCase()), axes.shape(), "the projection of " + shape);
        // the rest of the decision reads back off the axes alone: the address, the depth, the partition
        final Placement recorded = legacy(shape);
        assertEquals(recorded.target(), axes.target(), "target(" + shape + ")");
        assertEquals(recorded.hop(), axes.hop(), "hop(" + shape + ")");
        assertEquals(recorded.split(), axes.split(), "split(" + shape + ")");
    }

    /**
     * A placement's decision as the axes alone — the same three keys with the recorded enum token removed, which is
     * what {@link Placement#of(Rec)} leaves when a plan speaks the axes (C9's spelling).
     */
    private static Placement axesOnly(final Placement placement) {
        return Placement.of(rec(
                uri(PLACE), placement.place().toObj(),
                uri(HOP), placement.hopAxis().toObj(),
                uri(SPLIT), placement.split().toObj()));
    }

    /**
     * THE LEGACY REC → AXES → THE SAME ANSWERS. Each row: a rec written in the old encoding % the shape it records %
     * the address it names % its hop budget. {@link Placement#of(Rec)} migrates the rec into the axes, and the shape,
     * the target and the hop budget answer what they answered before the axes existed — including the pre-singular
     * {@code hops} spelling.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "'[shape=><local>]'                                                    % local   %     % 0",
            "'[shape=><none>]'                                                     % none    %     % 0",
            "'[shape=><peer>,target=><ws://ginger.local:8555>]'                    % peer    % ws://ginger.local:8555 % 0",
            "'[shape=><peers>]'                                                    % peers   %     % 0",
            "'[shape=><space>,target=><space://durable/store>]'                    % space   % space://durable/store % 0",
            "'[shape=><tree>,hops=>2]'                                             % tree    %     % 2",
            "'[shape=><shuffle>]'                                                 % shuffle %     % 1",
            "'[shape=><chain>,target=><loop://localhost:8555>]'                    % chain   % loop://localhost:8555 % 0",
            "'[shape=><star>,target=><//ginger:8555>]'                             % star    % //ginger:8555 % 0",
    }, delimiter = '%')
    public void testTheLegacyRecMigratesToTheAxes(final String source, final String shape, final String target, final int hop) {
        final Placement placement = Placement.of(ObjmtronSerializer.parse(source).asRec());
        assertEquals(Placement.Shape.valueOf(shape.toUpperCase()), placement.shape(), "shape of " + source);
        assertEquals(null == target ? null : f(target), placement.target(), "target of " + source);
        assertEquals(hop, placement.hop(), "hop of " + source);
        // the migration is idempotent: the rec now speaks the axes, and re-reading it answers the same decision
        final Placement reread = Placement.of(placement);
        assertEquals(placement.place(), reread.place(), "place of " + source);
        assertEquals(placement.hopAxis(), reread.hopAxis(), "hop axis of " + source);
        assertEquals(placement.split(), reread.split(), "split of " + source);
        assertEquals(placement.shape(), reread.shape(), "shape of " + source);
    }

    /**
     * THE LOCAL WARNING — {@code local} has two meanings and they stay apart. As the <em>whole</em> place set it is the
     * origin's tail decision: {@code merge().<combiner>.<tail>} runs at home, nothing is shipped, and the cut is
     * re-applied in place, so the decision demands no law. As <em>one designator of a fan-out</em> it is the master
     * taking a share, and the decision still ships — it demands the off-machine merge's laws. Collapsing the two would
     * silently turn the plan's gather into a lane.
     */
    @Test
    public void testLocalKeepsItsTwoMeanings() {
        // (a) the origin's tail: the place set is local alone, spans AT/POST (law_shard's first placement)
        final Placement tail = Placement.of(ObjmtronSerializer.parse(
                "[shape=><local>,span=>[[span=><at>],[span=><after>]]]").asRec());
        assertEquals(List.of("local"), tail.place().names(), "the tail's place set is local alone");
        assertFalse(tail.place().distributes(), "the tail ships nothing");
        assertEquals(Set.of(), tail.obligations(), "nothing is regrouped off-machine, so the cut demands no law");
        assertNull(tail.target(), "the tail names no address");
        // (b) the home place in a fan-out: local is one designator of a set that ships
        final PlaceSet fanOut = PlaceSet.of(ObjmtronSerializer.parse("[local=>1,peer=>1]"));
        assertEquals(List.of("local", "peer"), fanOut.names(), "the master is a place among the places");
        assertTrue(fanOut.distributes(), "the set ships (peer is in it)");
        assertEquals(Set.of(Law.monoidic, Law.commutative),
                Placement.obligations(fanOut, Hop.tree(1), Partition.stream()),
                "a fan-out that includes the master still recombines off-machine");
        // the same designator, two decisions: the place set is what separates them
        assertNotEquals(tail.place(), fanOut, "the tail's decision is not the fan-out's");
    }

    /**
     * THE PLACE SET'S FIVE FORMS — one designator, the uniform set, the weighted set, a counted designator, the
     * wildcard — each read from the value a {@code place} key holds, plus the write/read round trip. Each row: the
     * written value % the designators as written % whether the set ships.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "peer                    % peer         % true",
            "local                   % local        % false",
            "#                       % #            % true",
            "//abc:45                % //abc:45     % true",
            "ws://ginger:8555        % ws://ginger:8555 % true",
            "[local,peer]            % local,peer   % true",
            "[local=>4,//abc:45=>2]  % local,//abc:45 % true",
            "[{2}//abc:45,peer]      % //abc:45,peer % true",
    }, delimiter = '%')
    public void testThePlaceSetForms(final String written, final String names, final boolean distributes) {
        final PlaceSet places = PlaceSet.of(ObjmtronSerializer.parse(written));
        assertEquals(List.of(names.split(",")), places.names(), "the designators of " + written);
        assertEquals(distributes, places.distributes(), "distributes(" + written + ")");
        // the canonical spelling reads back to the same set
        final PlaceSet reread = PlaceSet.of(PlaceSet.of(places.toObj()).toObj());
        assertEquals(places, reread, "the place set round-trips through its own spelling: " + written);
    }

    /**
     * THE DEMANDS ARE THE COEFFICIENTS — a designator's demand survives both spellings (the {@code lst}'s counted
     * designator and the {@code rec}'s weight), and an exclusion is a zero demand the {@code rec} spelling is the only
     * one that can carry.
     */
    @Test
    public void testThePlaceDemands() {
        final PlaceSet counted = PlaceSet.of(ObjmtronSerializer.parse("[{2}//abc:45,peer]"));
        assertEquals(cInt.of(2L), counted.designators().getFirst().demand(), "a counted designator asks for two");
        assertEquals(cInt.ONE(), counted.designators().get(1).demand(), "an uncounted one asks for one");
        final PlaceSet weighted = PlaceSet.of(ObjmtronSerializer.parse("[local=>4,//abc:45=>2]"));
        assertEquals(cInt.of(4L), weighted.designators().getFirst().demand(), "a rec's weight is the demand");
        final PlaceSet exclusion = PlaceSet.of(ObjmtronSerializer.parse("[local=>0,peer=>1]"));
        assertEquals(cInt.ZERO(), exclusion.designators().getFirst().demand(), "an omission stated as a zero demand");
        assertEquals(exclusion, PlaceSet.of(exclusion.toObj()), "the weights spelling round-trips");
    }

    /**
     * THE DEPTH AXIS — a counted int whose value is the height and whose coefficient is the arity, plus the pair
     * {@code [height, arity]} for the two arities mtron's coefficient grammar cannot render. Each row: the written
     * value % the height % the arity % whether it is sequential.
     *
     * <p><b>A bare int is TODAY, not one child:</b> {@code ?hop=n} means "height n, the receiver's local out-degree"
     * in every plan and in the design (§10), so it reads as {@code cInt.ANY()}. A chain states arity one in the pair
     * form ({@code [2,1]}) — {@code {1}2} normalizes to {@code 2}, so the coefficient cannot carry it — and the pair
     * also spells a zero arity, which would be {@code noobj} as a coefficient.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "4        % 4 % **  % false",
            "{2}4     % 4 % 2   % false",
            "{**}1    % 1 % **  % false",
            "'{2,4}4' % 4 % 2,4 % false",
            "[2,1]    % 2 % 1   % true",
            "[0,0]    % 0 % 0   % false",
    }, delimiter = '%')
    public void testTheHopAxis(final String written, final int height, final String arity, final boolean sequential) {
        final Hop hop = Hop.of(ObjmtronSerializer.parse(written));
        assertEquals(height, hop.height(), "the height of " + written);
        assertEquals(arity, hop.arity().toString(), "the arity of " + written);
        assertEquals(sequential, hop.sequential(), "sequential(" + written + ")");
        assertEquals(hop, Hop.of(hop.toObj()), "the hop round-trips through its own spelling: " + written);
    }

    /**
     * THE PARTITION AXIS — a counted uri: the name is the kind, the coefficient the part count. Each row: the written
     * value % the kind % the part count.
     *
     * <p><b>A bare kind is that kind's OWN default, never "one":</b> {@code stream} is as many parts as lanes — the
     * design's default and today's emitter — and so is every other stream-partitioning kind; {@code copy} is the one
     * exception, because the whole seed on every lane IS its meaning. A bare spelling therefore cannot silently turn
     * a default into a broadcast (which would bypass {@code copy}'s idempotence gate in C7).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "stream      % stream % **",
            "{**}stream  % stream % **",
            "{2}block    % block  % 2",
            "'{2,4}stream' % stream % 2,4",
            "key         % key    % **",
            "index       % index  % **",
            "copy        % copy   % 1",
    }, delimiter = '%')
    public void testThePartitionAxis(final String written, final String kind, final String parts) {
        final Partition split = Partition.of(ObjmtronSerializer.parse(written));
        assertEquals(kind, split.kind().name().toLowerCase(), "the kind of " + written);
        assertEquals(parts, split.parts().toString(), "the part count of " + written);
        assertEquals(split, Partition.of(split.toObj()), "the partition round-trips through its own spelling: " + written);
    }

    /**
     * THE FIVE CLAUSES OF THE OBLIGATIONS RULE, one row each — including the two the reviewer's traps live in.
     * Each row: the place set % the hop height % the hop arity % the split % the laws the decision demands.
     *
     * <ul>
     *   <li><b>the no-regroup clause is about ORDER, not about leaving home</b>: {@code [local]} is the local fold
     *       (nothing is regrouped ⇒ nothing demanded), but the design's peerless-lanes case — four concurrent home
     *       lanes, {@code [local{4}]} — is gathered after the fact, and a stream split permutes the shares, so k > 1
     *       demands {@code monoidic} and {@code commutative} exactly as a fan-out does. (The row spells it
     *       {@code [{4}local]} / {@code [local=>4]}: a TRAILING coefficient on a path-only designator is not read by
     *       the object grammar — measured — while the leading and weighted spellings are.)</li>
     *   <li><b>a keyed split</b> folds per bucket: {@code monoidic} alone;</li>
     *   <li><b>a sequential hop</b> (arity one) preserves order: {@code monoidic} alone;</li>
     *   <li><b>everything else</b> that regroups demands both;</li>
     *   <li><b>{@code index} is derived like a fan-out</b> (it ships no data, but the code ships to the range holders
     *       and their partials still recombine at home through the same combiner), and {@code owner}/{@code copy} with
     *       it — the gate fails closed.</li>
     * </ul>
     */
    @ParameterizedTest
    @CsvSource(value = {
            "peer          % 1 % ** % stream % commutative,monoidic",
            "peer          % 1 % 2  % stream % commutative,monoidic",
            "peer          % 2 % 1  % stream % monoidic",
            "peer          % 1 % ** % key    % monoidic",
            "peer          % 1 % ** % index  % commutative,monoidic",
            "peer          % 1 % ** % owner  % commutative,monoidic",
            "peer          % 1 % ** % copy   % commutative,monoidic",
            "#             % 1 % ** % stream % commutative,monoidic",
            "local         % 0 % ** % stream %",
            "[{4}local]    % 0 % ** % stream % commutative,monoidic",
            "[{4}local]    % 0 % ** % key    % monoidic",
            "[local=>4]    % 0 % ** % stream % commutative,monoidic",
            "'[local=>1,peer=>1]' % 0 % ** % stream % commutative,monoidic",
            "[]            % 0 % ** % stream %",
    }, delimiter = '%')
    public void testTheObligationClauses(final String places, final int height, final String arity,
                                         final String split, final String derivedLaws) {
        final PlaceSet place = PlaceSet.of(ObjmtronSerializer.parse(places));
        final Hop hop = Hop.of(height, cInt.of(arity));
        final Partition partition = Partition.of(ObjmtronSerializer.parse(split));
        assertEquals(laws(derivedLaws), Placement.obligations(place, hop, partition),
                "obligations(" + places + ", " + height + "{" + arity + "}, " + split + ")");
    }

    /**
     * A MALFORMED AXIS IS REFUSED, NEVER GUESSED — an unknown designator, a hop that is neither a counted int nor a
     * two-number pair, an unknown partition kind, and the one count the grammar cannot state: a single part on a
     * stream-family kind ({@code {1}stream} normalizes to {@code stream}, the default), which is the {@code copy}
     * kind's value and is refused by name rather than silently read back as "as many parts as lanes".
     */
    @Test
    public void testAMalformedAxisIsRefused() {
        assertThrows(MTronException.class, () -> PlaceSet.of(ObjmtronSerializer.parse("5")),
                "a place set is not a number");
        assertThrows(MTronException.class, () -> PlaceSet.of(ObjmtronSerializer.parse("abc")),
                "a designator with no host, scheme or value token names no place form");
        assertThrows(MTronException.class, () -> PlaceSet.of(ObjmtronSerializer.parse("[local,5]")),
                "a place set's members are designators");
        assertThrows(MTronException.class, () -> Hop.of(ObjmtronSerializer.parse("stream")),
                "a hop is a counted int or a pair");
        assertThrows(MTronException.class, () -> Hop.of(ObjmtronSerializer.parse("[1,2,3]")),
                "a hop pair is exactly [height, arity]");
        assertThrows(MTronException.class, () -> Partition.of(ObjmtronSerializer.parse("2")),
                "a split is a counted uri");
        assertThrows(MTronException.class, () -> Partition.of(ObjmtronSerializer.parse("{**}streams")),
                "an unknown partition kind is refused");
        assertThrows(MTronException.class, () -> Partition.stream(cInt.ONE()).toObj(),
                "a stated one part is the copy kind: the coefficient cannot state it");
    }

    /**
     * THE AXES OUTRANK THE RECORDED TOKEN (the reviewer's fifth item): a rec that states both cannot answer a
     * {@code shape()} its axes contradict — the old token-first order allowed {@code shape()==TREE} beside
     * {@code obligations()=={}}, and {@code PlanInterpreter} reads the first while {@code create()} reads the second.
     * The token answers only where the projection coarsens ({@code peer}/{@code star}/{@code space}); everywhere else
     * the axes decide, so shape and obligations always come from the same decision.
     */
    @Test
    public void testTheAxesOutrankTheRecordedToken() {
        // the token says tree, the axes say "the home place alone": the axes win, and the laws agree with the shape
        final Placement contradictory = Placement.of(ObjmtronSerializer.parse(
                "[shape=><tree>,place=>[local],hop=>0,split=>stream]").asRec());
        assertEquals(Placement.Shape.LOCAL, contradictory.shape(), "the axes decide, not the stale token");
        assertEquals(Set.of(), contradictory.obligations(), "shape and obligations cannot disagree");
        // and the one place the token still answers: one named address at no depth, where the projection is ambiguous
        final Placement star = Placement.of(ObjmtronSerializer.parse(
                "[shape=><star>,place=>[//ginger:8555],hop=>0,split=>stream]").asRec());
        assertEquals(Placement.Shape.STAR, star.shape(), "the move fan-out is not distinguishable from a peer by axes");
        assertEquals(Placement.Shape.PEER, Placement.of(ObjmtronSerializer.parse(
                        "[place=>[//ginger:8555],hop=>0,split=>stream]").asRec()).shape(),
                "... so the projection alone answers the peer");
    }

    /**
     * THE AXES ARE A VALID {@code plan::T} — with COUNTED values, which is the point: a plain {@code <#>} rejects a
     * counted mono ({@code {2}4}, {@code {2}block}, {@code {**}stream} are all {@code /m/int{..}} / {@code /m/uri{..}}),
     * so the predicate's axis types carry {@code cInt.ANY()}. This row is what pins that widening.
     */
    @Test
    public void testTheAxesArePlanT() {
        final Plan plan = PlanBuilder.plan("axes_plan_t")
                .cut(Cut.reducer(Law.monoidic, Law.commutative))
                .place(PlaceSet.peer()).hop(2, cInt.of(2L)).split(Partition.of(Partition.Kind.BLOCK, cInt.of(2L)))
                .where(Placement.peers(Span.seed().partition()))
                .create();
        assertTrue(plan.test(catInstSet.PLAN_TYPE), "counted axes are still a plan::T: " + plan);
        // the hop rides the pair form (arity two IS a coefficient, so this one is the counted spelling) and the split
        // its coefficient; both are counted objects, which is exactly what the widening admits
        assertEquals(2, plan.where().getFirst().hopAxis().height());
        assertEquals(Partition.Kind.BLOCK, plan.where().getFirst().split().kind());
    }
}
