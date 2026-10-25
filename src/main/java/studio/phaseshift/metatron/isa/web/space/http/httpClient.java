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

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.MIMEQ_PATTERN;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * HTTP_CLIENT — a connectionless reader and writer of the web, and a PEER once constructed: like every client, its
 * constructor indexes it into the network ({@code ~/network/peer/<key>}), and the network reaches it through the
 * instructions it declares — {@code from} as an HTTP GET and {@code to} as an HTTP POST, and NO {@code eval}: a POST
 * stores what it is sent and evaluates nothing, so this client is never a place to ship a program.
 * <pre>
 *   http_client::[host=><http://markorodriguez.com>]   [-- one site --]
 *   http_client::[host=><http://#>]                    [-- the whole web: the gateway --]
 *   http_client::[host=><https://#>]                   [-- the whole web over tls --]
 * </pre>
 * THE GATEWAY IS A CLIENT SOMEONE CONSTRUCTED. A read never dials, so an http(s) address answers only through a client a
 * boot or a program built. A wildcard host covers every authority its pattern matches, and the network prefers an
 * exact authority to it, so a site-specific client wins for its site. A machine that must not reach the web declares
 * neither wildcard.
 * <p>
 * Each override is the native itself with an http body, so a call arrives as it was written — the FULL address (with
 * authority) in arg(0), sent verbatim, its {@code ?mimeq} included: the far side renders it, which is how an mtron
 * server serves a read {@code ?mimeq=application/json}.
 */
public final class httpClient {

    public static final fURI HTTP_CLIENT_TID = WEB_ISA_TID.extend("http").extend("http_client");

    /**
     * how long an http client's status may wait for the server to answer its HEAD — the connect and the response both —
     * before it reads not live
     */
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(2);

    public static final Type HTTP_CLIENT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(HTTP_CLIENT_TID)
            .constructor(socket -> {
                final Obj host = socket.asRec().at(uri(HOST));
                final Obj from = Network.Helper.override(FROM_INST_TID, (lhs, inst) -> get(inst.arg(0).uriValue()));
                final Obj to = Network.Helper.override(TO_INST_TID, (lhs, inst) -> post(lhs, inst.arg(0).uriValue()));
                // THE STATUS — a connectionless client holds no socket to ask, so it asks the server each time it is read:
                // a bounded HEAD to the host it was built for. A wildcard host names no one server to ask, and is live.
                final Obj status = Network.Helper.statusOf(() -> host.isUri() && (host.uriValue().hasPattern() || head(host.uriValue())));
                final Obj client = rec(
                        uri(HOST), host,
                        uri(INST), rec(uri(FROM_INST_TID), from, uri(TO_INST_TID), to),
                        uri(STATUS), status);
                Network.Helper.index(client);
                return client;
            }).create();

    private httpClient() {
        // static only
    }

    /**
     * GET — the web gateway's read. The document is reified by its content type ({@code html::"..."}, {@code json::...};
     * a text/plain body takes its file extension's type when it has one). A 404 walks up the path to a containing
     * document and answers the part of it the rest of the path names, when the document is a structure.
     */
    static Obj get(final fURI address) {
        // a bare host reads its root: http://host is http://host/
        final fURI target = 0 == address.segmentLength() && address.hasHost() ? address.asBranch() : address;
        try {
            fURI running = target;
            int steps = 0;
            while (true) {
                final Connection.Response response = Jsoup.connect(running.toString()).ignoreContentType(true).ignoreHttpErrors(true).execute();
                if (404 == response.statusCode()) {
                    if (0 == running.segmentLength())
                        return noobj();
                    steps++;
                    running = running.asRelativeNode().retract(1).asAbsolute();
                    continue;
                }
                // a text/plain body takes its file extension's type: a .json file served without its content type
                MIME.MIMEType contentType = MIME.MIMEType.of(response.contentType());
                if (null == contentType || MIME.MIMEType.TEXT_PLAIN == contentType)
                    contentType = MIME.MIMEType.fromExtension(running.name(), contentType);
                final fURI contentTid = null == contentType ? null : contentType.toTid();
                final Obj document = null != contentTid ? str(response.body(), contentTid, null) : str(response.body());
                final fURI rest = target.scheme(null).host(null).tail(steps).asRelative();
                if (rest.toString().trim().isEmpty())
                    return document;
                if (document.isRec())
                    return document.asRec().at(uri(rest));
                // a document that is text has no part to answer for the rest of the path
                return document.isStr() && 0 == steps ? document : noobj();
            }
        } catch (final Exception e) {
            if (null != e.getMessage() && e.getMessage().contains("no bytes"))
                return noobj();
            return fail(e);
        }
    }

    /**
     * POST — {@code obj.to(<uri>)} writes the obj, the lhs, as the native's: the mime is the address's {@code ?mimeq} or
     * json, the body that mime's bytes. The answer is the native {@code to}'s, the obj written (its range is the lhs's —
     * the type system checks it), so a reply the far side sends back is not the write's answer; a refusal (no 2xx) is a
     * fail.
     */
    static Obj post(final Obj obj, final fURI address) {
        try {
            final MIME.MIMEType mime = Optional.ofNullable(MIME.MIMEType.of(address.q(MIMEQ_PATTERN))).orElse(MIME.MIMEType.APPLICATION_JSON);
            final HttpRequest request = HttpRequest.newBuilder()
                    .header(MIME.MIMEType.VALUE, mime.value)
                    .uri(java.net.URI.create(address.removeQ(MIMEQ_PATTERN).toString()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mime.toBytes(obj)))
                    .build();
            final HttpResponse<byte[]> response;
            try (final HttpClient client = HttpClient.newHttpClient()) {
                response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            }
            return 2 == response.statusCode() / 100
                    ? obj
                    : fail("POST %s answered %s", address, response.statusCode());
        } catch (final Exception e) {
            return fail(e);
        }
    }

    /**
     * Is a server answering at {@code host}? ANY response is a live server (a 404 still answers); a refused or timed-out
     * connection is not.
     */
    private static boolean head(final fURI host) {
        try (final HttpClient client = HttpClient.newBuilder().connectTimeout(STATUS_TIMEOUT).build()) {
            client.send(HttpRequest.newBuilder()
                            .uri(java.net.URI.create(host.toString()))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(STATUS_TIMEOUT)
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (final Exception e) {
            return false;
        }
    }
}
