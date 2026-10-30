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

package studio.phaseshift.metatron.isa.m.web.space.http;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Unit characterization of {@code httpServer.RouteLane#resolve} — all four lanes, called directly.
 * <p>
 * This is what naming the ladder bought: the {@code NONE} lane cannot be reached through a route table at all
 * (a route value that would reach it is no uri, type or instruction, and the table check reports it), so before the
 * extraction it was untestable dead code. Called as a function it is covered like any other branch.
 * <p>
 * No server is needed: this test exercises resolution only, and never issues a request.
 */
class httpRouteResolutionTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(httpRouteResolutionTest.class);

    @BeforeAll
    public static void importEverything() {
        InstSet.importInstSet(f("#"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "mcp_mtron     % HANDLER % an mcp_server type is materialized into an instance, then wrapped",
            "mtron_http    % HANDLER % a handler type with a constructor keeps the type lane (no materialization)",
            "/m/web/helper % WEB     % a uri whose read yields a non-type is served as a web root",
            "5             % NONE    % a value that reaches no lane — unreachable through a route table",
    }, delimiter = '%')
    void testResolveRoute(final String value, final String expectedKind, final String desc) {
        final Obj input = ObjmtronSerializer.parse(value);
        final httpServer.RouteLane lane = httpServer.RouteLane.resolve(input, null);
        LOG.info("resolveRoute(%s) => %s", value, lane.kind());
        assertEquals(expectedKind, lane.kind().name(), desc);
    }

    /**
     * Resolving a route value <em>against a request</em> — the two templated spellings, and the distinction the
     * templating turns on: a templated value has seen the request path, so the uri it produces <em>is</em> the
     * address ({@code address}), while an untemplated uri names a root the mount still extends ({@code webRoot}).
     * Exactly one of the two is ever set, which is what tells the handler whether to append the request path.
     * <p>
     * Note {@code /mount/helper}: {@code path/2} is the third element because the path is 0-indexed with an empty
     * leading element ({@code <>,mount,<helper>}) — so the positional idiom counts from the uri root, not from
     * the mount.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "</m/web/${name()}>              % /mount/helper % WEB % /m/web/helper % -              % templated: name() takes the request's tail segment",
            "</m/web/${as(rec::T).>>path/2}> % /mount/helper % WEB % /m/web/helper % -              % templated: path/2 takes the same segment positionally",
            "</m/web/helper>                 % /mount/helper % WEB % -              % /m/web/helper % untemplated: a prefix root, extended by the request path",
    }, delimiter = '%')
    void testResolveRouteAgainstRequest(final String value, final String requestUri, final String expectedKind,
                                        final String expectedAddress, final String expectedWebRoot, final String desc) {
        final httpServer.RouteLane lane = httpServer.RouteLane.resolve(ObjmtronSerializer.parse(value), f(requestUri));
        LOG.info("resolveRoute(%s, %s) => %s [address=%s webRoot=%s]", value, requestUri, lane.kind(),
                lane.address(), lane.webRoot());
        assertEquals(expectedKind, lane.kind().name(), desc);
        assertEquals("-".equals(expectedAddress) ? null : expectedAddress,
                null == lane.address() ? null : lane.address().toString(), desc);
        assertEquals("-".equals(expectedWebRoot) ? null : expectedWebRoot,
                null == lane.webRoot() ? null : lane.webRoot().toString(), desc);
    }
}
