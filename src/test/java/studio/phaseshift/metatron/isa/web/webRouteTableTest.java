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

package studio.phaseshift.metatron.isa.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.REST_TID;

/**
 * The route-table contract ({@code route::T} at {@code /m/web/route}) as a checkable function:
 * {@link webHelper#routeProblems(Rec)} reports an lst of problems, or {@code noobj} when the table is clean.
 * <p>
 * Both rules it checks come from boot defects found while characterizing the ladder: a value that cannot be
 * rendered as a uri throws <em>inside</em> the space constructor and leaves the space with no server at all
 * (every request then times out rather than failing); and an instruction-valued route must actually be a
 * {@code protocol<=uri} function. Resolvability is deliberately not checked — a web root may name a prefix
 * rather than a leaf. A {@code code} value (a fluent chain such as {@code 5.as(uri::T)}, or the production {@code *dr.as(skill::T).as(mcp_server::T)}) is accepted unchecked, because its effective rng is resolved when it is applied — there is no declared signature to test; only a single instruction's signature is checkable.
 */
class webRouteTableTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(webRouteTableTest.class);

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[/good => mcp_mtron]            % 0 % a type reference is mountable",
            "[/content => /m/web/helper]     % 0 % a uri is mountable",
            "[/bad => 5]                     % 1 % an int is not: it cannot even be rendered at boot",
            "[/good => mcp_mtron, /bad => 5] % 1 % one problem per offending entry",
            "[/root => docker:]              % 0 % a web root may name a prefix that does not read back",
            "[/conv => 5.as(uri::T)]         % 0 % a code value is accepted unchecked: its rng is resolved at application",
            "[/people/# => mcp_mtron]        % 1 % a pattern key is reported: the carrier matches its context literally, so /people/34 cannot reach it",
            "[/a+b => mcp_mtron]             % 0 % a literal plus inside a segment is not a pattern — only a whole # or + segment is",
    }, delimiter = '%')
    void testRouteTableShape(final String table, final int expected, final String desc) {
        final Obj problems = webHelper.routeProblems(ObjmtronSerializer.parse(table).asRec());
        LOG.info("routeProblems(%s) => %s", table, problems);
        assertEquals(expected, problems.isNoObj() ? 0 : problems.asLst().lstValue().size(), desc);
    }

    @Test
    void testRouteInstructionSignature() {
        // takes a uri and yields a protocol surface — mountable
        final Inst good = instC(f("route_test_good").dom(URI_TID).rng(REST_TID), lst(T(URI_TID)), (lhs, inst) -> lhs);
        // takes a uri but yields a non-protocol — not mountable
        final Inst bad = instC(f("route_test_bad").dom(URI_TID).rng(REC_TID), lst(T(URI_TID)), (lhs, inst) -> lhs);
        final Obj problems = webHelper.routeProblems(rec(uri(f("/good")), good, uri(f("/bad")), bad));
        LOG.info("routeProblems(signature) => %s", problems);
        assertEquals(1, problems.isNoObj() ? 0 : problems.asLst().lstValue().size(),
                "only the non-protocol rng should be reported: " + problems);
    }
}
