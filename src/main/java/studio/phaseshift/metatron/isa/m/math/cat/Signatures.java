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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The scheme signatures (§11.6): data a plan cites — one scheme token to its roles. {@code dialer} is the client the
 * local machine instantiates to reach a place over that scheme, and {@link #resolves(String)} is the second conjunct of
 * the declared-liveness test (§4): a candidate dial is live only when its scheme's dialer is in the machine's ISA. No
 * dial is attempted to decide liveness — this is a declaration read, the location side of the rewrite's
 * type-part/value-residue split.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Signatures {

    private Signatures() {
        // static only
    }

    /** scheme → the dialer tid the machine instantiates to reach a place over it (the local client, not the handler). */
    private static final Map<String, String> DIALERS = Map.of(
            "ws", "/m/web/ws/ws_client",
            "http", "/m/web/http/http_client",
            // the http client's GET speaks tls as it is: without this row Network.read's auto-connect declined every
            // https:// read, which then fell through to whatever http space happened to claim the scheme
            "https", "/m/web/http/http_client",
            "loop", "/m/mach/id_client");

    /**
     * The dialer tid for a scheme, or {@code null} when the scheme is unknown.
     */
    public static String dialerOf(final String scheme) {
        return DIALERS.get(scheme);
    }

    /**
     * Whether the machine can dial over this scheme — the dialer tid resolves in its ISA. {@code false} for an unknown
     * scheme.
     */
    public static boolean resolves(final String scheme) {
        final String dialer = DIALERS.get(scheme);
        return null != dialer && !Machine.current().instset().readDeclared(f(dialer)).isNoObj();
    }
}
