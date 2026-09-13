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

package studio.phaseshift.metatron.isa.web.space.http;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Why a wildcard mount key mounts nothing a client can reach — pinned, so that fixing it is a deliberate change
 * rather than a silent behaviour shift.
 * <p>
 * The route key is written in <b>metatron's</b> pattern language ({@code #} multi-segment, {@code +} single
 * segment) and handed verbatim to {@code HttpServer.createContext(String)}, which matches a request path by
 * <b>literal</b> longest prefix ({@code ServerImpl.findContext}: {@code path.startsWith(ctxt)}, longest wins). So
 * {@code /people/#} registers a context whose path ends in a literal {@code #} — and the only request that can
 * reach it is one whose path literally contains that character, since an ordinary path never does ({@code #} is
 * the URI fragment delimiter and is not sent to a server at all; the {@code %23} row is that admission).
 * <p>
 * <b>The real defect is that one table is matched by two different languages.</b> {@code Space.Helper.routeFromSpace}
 * <em>applies</em> the key to the vid ({@code e.getKey().apply(vidURI)}, {@code Space.java:230}), so metatron
 * wildcards behave as wildcards on the {@code http://} address-space read path — while the serving path is
 * literal. Nothing translates between them; the key simply crosses the boundary unchanged. The consequence is
 * quiet: {@code createContext} accepts the key happily, so the mount is neither an error nor a warning — it serves
 * nothing, and a request for the subtree it was meant to cover is answered by whatever else is mounted (a 404, if
 * nothing is).
 * <p>
 * Pattern matching with longest-prefix ordering is stage 5's shared mount table
 * ({@code docs/design/webspace.md} §8.1.5, §8.1.1). Until then the working idiom is a literal prefix key — the
 * value's expressions see the whole request uri, so the pattern never has to carry the capture.
 */
@Timeout(60)
class httpWildcardMountTest extends AbstractHTTPServerIntegrationTest {

    @Override
    protected httpSpace createHTTPSpace() {
        return httpSpace.of(rec(
                uri(PATTERN), uri(f("/test/httpWildcardMount/#")),
                uri(HOST), uri(f("http://127.0.0.1:" + generatePort())),
                uri(ROUTE), rec(
                        // the working idiom: a literal prefix key
                        uri(f("/literal")), uri(f("/m/web/helper")),
                        // the idiom that reads as if it should work, and does not
                        uri(f("/people/#")), uri(f("/m/web/helper")))),
                f("/sys/space/test/httpWildcardMount"));
    }

    /**
     * The wildcard-key behaviour, asserted through the one observable a client has.
     * <p>
     * The middle row is the finding: {@code /people/34} is exactly what {@code /people/#} looks like it mounts,
     * and it is unmounted. The last row is the proof that the context exists but matches literally — it is
     * reachable only by sending a path that contains an actual {@code #}, percent-encoded, which no ordinary
     * client does ({@code #} is the fragment delimiter and never leaves the client). It also pins the second
     * defect that path exposed: the handler cache key was built from {@code mount.name()}, so a wildcard key put
     * a {@code #} into a vid and turned the cache read into a pattern read (an {@code Objs}, then
     * "not an httprec::T" far from the cause). Keys are now sanitized — see {@code httpSpace.segment}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/literal    % 200 % a literal key mounts its subtree — the idiom every live mount uses",
            "/people/34  % 404 % a wildcard key's subtree is unreachable: the context path is matched literally",
            "'/people/%23' % 200 % and only a path literally containing # reaches it (percent-encoded, since a raw # never leaves a client)",
    }, delimiter = '%')
    void testWildcardMountKey(final String path, final int status, final String desc) throws Exception {
        assertEquals(status, httpGet(path).statusCode(), desc + " — GET " + path);
    }
}
