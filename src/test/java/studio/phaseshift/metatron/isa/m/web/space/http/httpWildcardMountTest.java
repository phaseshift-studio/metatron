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

import org.junit.jupiter.api.Timeout;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A wildcard mount key mounts the paths it describes — pinned, because it once did not.
 * <p>
 * The route key is written in <b>metatron's</b> pattern language ({@code #} multi-segment, {@code +} single segment).
 * The httpspace carrier handed it verbatim to {@code HttpServer.createContext(String)}, which matches a request path
 * by <b>literal</b> longest prefix, so {@code /people/#} registered a context that only a path literally containing
 * {@code #} could reach, and {@code /people/34} answered 404. The http_server mounts one root context and routes every
 * request itself ({@code Listener.route}): the most specific key that describes the request's path answers, in the same
 * pattern language the key is written in, so a wildcard key now mounts exactly its subtree.
 */
@Timeout(60)
class httpWildcardMountTest extends AbstractHTTPServerIntegrationTest {

    @Override
    protected Listener createHTTPServer() {
        // what the wildcard mount serves: a memspace over /wild/#, holding a person beneath the mount's root
        memSpace.of(rec(uri(PATTERN), uri(f("/wild/#"))), f("/sys/space/test/wild"));
        Machine.write(f("/wild/people/34"), rec(uri("name"), str("Ada")));
        return httpServer.of(rec(
                uri(ORIGIN), uri(f("http://127.0.0.1:" + generatePort())),
                uri(ROUTE), rec(
                        // a literal prefix key mounts its subtree
                        uri(f("/literal")), uri(f("/m/web/helper")),
                        // and a wildcard key the paths it describes: /people/34 is /wild/people/34
                        uri(f("/people/#")), uri(f("/wild/people")))));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/literal    % 200 % a literal key mounts its subtree",
            "/people/34  % 200 % a wildcard key mounts the paths it describes: /people/# serves /people/34 from its root",
            "/elsewhere  % 404 % a path no key describes is no route",
    }, delimiter = '%')
    void testWildcardMountKey(final String path, final int status, final String desc) throws Exception {
        assertEquals(status, httpGet(path).statusCode(), desc + " — GET " + path);
    }
}
