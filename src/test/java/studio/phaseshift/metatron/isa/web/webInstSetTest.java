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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.STR_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.web.webHelper.isProtocolSurface;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.PROTOCOL_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
class webInstSetTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(webInstSetTest.class);

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
    }

    /**
     * Subsumption, java-side: `typeA.test(typeB)` — the same call the transport makes to ask
     * "is this target a rest::T / mcp::T surface?". It resolves through the **declared** tid chain
     * (`Type.parentType()`), never through vid paths: `fURI.test` compares *names* when neither side
     * carries a wildcard (AbstractfURI.java:321-324), so being vidded under a type implies nothing.
     * <p>
     * Asserted in java rather than as an mtron `matches()` / `sorta?()` expression on purpose: mtron
     * cannot yet express subsumption — inst resolution with a *type* lhs regresses. When it can, these
     * rows can carry expressions instead of vids.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/m/web/mcp/mcp_server  % /m/web/mcp       % true  % a server type is an mcp surface",
            "/m/web/mcp/mcp_mtron   % /m/web/mcp       % true  % the metatron-native server is an mcp surface",
            "/m/web/mcp/mcp_http    % /m/web/mcp       % true  % the http-bound mcp surface is an mcp surface",
            "/m/web/mcp/mcp_ws      % /m/web/mcp       % true  % the ws-bound mcp surface is an mcp surface",
            "/m/web/mcp/mcp_emulator_http % /m/web/mcp  % true  % the http emulator rides mcp_http's parent (2 hops)",
            "/m/web/mcp/mcp_emulator_ws   % /m/web/mcp  % true  % the ws emulator rides mcp_ws's parent (2 hops)",
            "/m/web/http/rest       % /m/web/http      % true  % rest refines the http vocabulary",
            "/m/web/http/web_http   % /m/web/http/rest % true  % the web handler is a rest surface",
            "/m/web/ws/mtron_ws     % /m/web/mtron     % true  % the ws mtron handler is an mtron surface",
            "/m/web/http/mtron_http % /m/web/mtron     % true  % the http mtron handler is an mtron surface",
            "/m/uri                 % /m/web/mcp       % false % an unrelated type is not a surface",
            "/m/rec                 % /m/web/protocol  % false % a bare rec is not a surface: protocol::T is a union, not a label",
            "/m/web/http/rest       % /m/web/protocol  % false % a union classifies *objs*; for a type, ask the declared chain",
    }, delimiter = '%')
    void testProtocolSubsumption(final String sub, final String parent,
                                 final boolean expected, final String desc) {
        final Type subType = Router.readFromSpace(f(sub)).<Type>as();
        final Type parentType = Router.readFromSpace(f(parent)).<Type>as();
        assertEquals(expected, subType.test(parentType), desc);
    }

    /**
     * The union's payoff: it classifies <b>objs</b>. A route target that is an actual surface value tests as
     * {@code protocol::T}, which a purely nominal umbrella could not decide. (For a *type* the answer comes from
     * the declared chain — see {@code webHelper.isProtocolSurface} — because a union's predicate is applied to
     * the objs on its lhs, and a {@code Type} is not a protocol value.)
     */
    @Test
    void testProtocolUnionClassifiesValues() {
        final Obj server = new mcpServer(new LinkedHashMap<>(), MCP_SERVER_TID, f("/m/web/test_protocol_probe"));
        assertEquals(true, server.test(T(PROTOCOL_TID)),
                "an mcp_server value should classify as a protocol surface: " + server.test(T(PROTOCOL_TID)));
    }

    /**
     * Every MIME type a document can be <em>typed as</em> must accept a str, because that is what a document
     * read produces: {@code fsSpace.objToFile}/{@code readFileAsObj} does
     * {@code str(bytes, mimeType.toTid(), null)} ({@code fsSpace.java:164-166}), and the read throws if the
     * document's type does not accept a str.
     * <p>
     * This is the contract test for a bug that cost a live site: {@code css::T} was declared
     * {@code tid(REC_TID)} while every sibling text MIME refines {@code str::T} — so serving any {@code .css}
     * file raised <em>"[string] is not a rec::T@/m/web/mime/css"</em> and answered 500, while {@code index.html}
     * (which links a stylesheet) kept working. A rec-typed MIME is a real possibility ({@code xsv::T} parses to
     * one), which is why the assertion is anchored on the content of the mapping rather than on a list.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "TEXT_HTML         % true  % html documents are text",
            "TEXT_CSS          % true  % css documents are text — the bug this test exists for",
            "TEXT_MARKDOWN     % true  % markdown documents are text",
            "TEXT_JAVA         % true  % java source is text",
            "APPLICATION_JSON  % true  % a json document is text, verified by its predicate",
            "APPLICATION_XML   % true  % an xml document is text",
            "APPLICATION_YAML  % true  % a yaml document is text",
            "APPLICATION_MTRON % false % null tid: the structural parse gate, never applied to raw bytes",
    }, delimiter = '%')
    void testTextMimeTypesAcceptAStr(final String mimeName, final boolean expected, final String desc) {
        final MIME.MIMEType mime = MIME.MIMEType.valueOf(mimeName);
        final fURI tid = mime.toTid();
        final boolean accepts = null != tid && Router.readFromSpace(tid).<Type>as().test(T(STR_TID));
        LOG.info("%s.toTid() = %s -> accepts a str: %s", mimeName, tid, accepts);
        assertEquals(expected, accepts, desc);
    }

    /**
     * The surfaces the web reference names, read at their vids and asked the question the reference documents:
     * for a <em>type</em>, is a protocol in its declared chain? This is the reading that verifies both that each
     * vid exists and that {@code webHelper.isProtocolSurface} answers as described (a union alone cannot: calling
     * {@code test(protocol::T)} on a type is false for every surface, which is why the chain is the answer).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/m/web/protocol    % true  % the umbrella itself",
            "/m/web/http        % true  % the http vocabulary is a surface",
            "/m/web/ws          % true  % so is the websocket vocabulary",
            "/m/web/mcp         % true  % and mcp",
            "/m/web/mtron       % true  % and the mtron-eval surface",
            "/m/web/http/rest   % true  % a rest surface (two hops: rest -> http -> protocol)",
            "/m/web/mcp/mcp_server % true % an mcp server type is a protocol surface",
            "/m/rec             % false % an ordinary type is not",
    }, delimiter = '%')
    void testProtocolSurfaceTypes(final String vid, final boolean expected, final String desc) {
        final Type type = Router.readFromSpace(f(vid)).<Type>as();
        LOG.info("%s => %s, isProtocolSurface=%s", vid, type, isProtocolSurface(type));
        assertEquals(expected, isProtocolSurface(type), desc + ": " + vid);
    }

    /**
     * The mount table's type is addressable at the vid the reference gives it (a doc reference is only useful if
     * the obj is there).
     */
    @Test
    void testRouteTypeIsRegistered() {
        final Obj routeType = Router.readFromSpace(f("/m/web/route"));
        LOG.info("/m/web/route => %s", routeType);
        assertEquals(false, routeType.isNoObj(), "the route table type should be registered at /m/web/route");
        assertEquals(true, routeType.isType(), "and it should be a type: " + routeType);
    }

    /**
     * Every document type the web reference names, read at its own vid so the claim is checkable: a document is a
     * str whoever produced it. The MIME enum maps only seven of these (there is no member for csv or xsv), so this
     * asks the type system rather than the enum.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/m/web/mime/html     % an html document",
            "/m/web/mime/markdown % a markdown document",
            "/m/web/mime/java     % java source",
            "/m/web/mime/yaml     % a yaml document",
            "/m/web/mime/xsv      % an xsv document (no MIME enum member of its own)",
            "/m/web/mime/csv      % a csv document (nor this one)",
            "/m/web/mime/json     % a json document",
            "/m/web/mime/xml      % an xml document",
            "/m/web/mime/css      % a css document",
    }, delimiter = '%')
    void testDocumentTypesAreStr(final String vid, final String desc) {
        final Type docType = Router.readFromSpace(f(vid)).<Type>as();
        LOG.info("%s => %s", vid, docType);
        assertEquals(true, docType.test(T(STR_TID)), desc + " must accept a str: " + vid);
    }

    /**
     * The same failure, one layer down, as the operation the transport actually performs — typing document bytes
     * with the MIME's type. Asserted directly so the fix is provable without a file system or a server.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "TEXT_CSS    % body{color:red}      % a css document types as its str type",
            "TEXT_HTML   % <html><body/></html> % an html document types as html::T",
            "TEXT_JAVA   % class A {}            % java source types as java::T",
    }, delimiter = '%')
    void testDocumentBytesTypeAsTheirMime(final String mimeName, final String content, final String desc) {
        final Obj typed = str(content, MIME.MIMEType.valueOf(mimeName).toTid(), null);
        LOG.info("%s typed as %s [%s]", mimeName, typed.tid(), desc);
        assertEquals(false, typed.isFail(), desc + ": " + typed);
    }
}
