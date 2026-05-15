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

package studio.phaseshift.metatron.isa.web.space.http.handler;

import com.sun.net.httpserver.HttpExchange;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.HttpRec;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_SPACE_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.CONTENT_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class web_httpHandler extends HttpRec {

    public static final fURI WEB_HTTP_TID = HTTP_SPACE_TID.extend("web_http");

    public static final Type WEB_HTTP_HANDLER_TYPE = Type.Builder.build()
            .tid(HTTP_HANDLER_TID)
            .vid(WEB_HTTP_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(CONTENT_TYPE).else_(uri(Content.ContentType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(CONTENT_TYPE).else_(uri(Content.ContentType.APPLICATION_MTRON.value)),
                    uri(WEB_ROOT).maybe(), T(ALL),
                    uri(DEFAULT_PAGE).maybe(), T(ALL),
                    uri(READ_ONLY).maybe(), T(ALL)))
            .constructor(instC(WEB_HTTP_TID.extend(CTOR).dom(ALL.maybe()).rng(WEB_HTTP_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Map<Obj, Obj> config = new LinkedHashMap<>(inst.arg(0).asRec().jvm());
                config.putIfAbsent(uri(DEFAULT_PAGE), str("index.html"));
                config.putIfAbsent(uri(READ_ONLY), bool(true));
                return new web_httpHandler(config, inst.arg(0).asRec().vid());
            })).create();

    // ──────────────────────────────────────────────

    public web_httpHandler(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, WEB_HTTP_TID, vid);

        // ── ON_GET: serve objects from any Router-backed space ──
        this.jvm().put(uri(ON_GET), instC(vid.extend(ON_GET).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                final HttpExchange exchange = this.exchange;
                if (exchange == null) {
                    LOG.error("no exchange available for GET handler");
                    return noobj();
                }

                // WEB_ROOT — validated lookup (uriValue() throws if the obj isn't a Uri)
                final Obj webRootObj = this.at(uri(WEB_ROOT));
                if (webRootObj.isNoObj() || !webRootObj.isUri()) {
                    sendError(500, "WEB_ROOT not configured");
                    return noobj();
                }
                final fURI webRoot = webRootObj.uriValue();

                // Build the request URI from WEB_ROOT + exchange path (relative to mount point)
                final String mountPath = exchange.getHttpContext().getPath();
                final String fullPath = exchange.getRequestURI().getPath();
                final String relativePath = fullPath.startsWith(mountPath)
                        ? fullPath.substring(mountPath.length())
                        : fullPath;
                final String query = exchange.getRequestURI().getQuery();
                fURI requestURI = relativePath.isEmpty()
                        ? webRoot
                        : webRoot.extend(f(relativePath));
                if (query != null && !query.isEmpty())
                    requestURI = requestURI.qString(query);

                // 1 — Direct read from Router (space-agnostic: fsSpace, memSpace, etc.)
                Obj requestObj = Router.global().read(requestURI);

                // 2 — locateBaseObj: walk up the URI path to find a containing object, then navigate into it
                if (requestObj.isNoObj()) {
                    final Space space = Router.global().getSpace(requestURI);
                    if (space != null) {
                        final Space.IdObj baseObj = Space.Helper.locateBaseObj(space, requestURI, f(""));
                        if (baseObj != null) {
                            String subPath = requestURI.toString().replaceFirst(baseObj.furi().toString(), "");
                            subPath = subPath.startsWith("/") ? subPath.substring(1) : subPath;
                            if (baseObj.obj().isRec())
                                requestObj = baseObj.obj().asRec().at(subPath);
                            else if (baseObj.obj().isLst())
                                requestObj = baseObj.obj().asLst().at(subPath);
                            else
                                requestObj = baseObj.obj();
                        }
                    }
                }

                // 3 — DEFAULT_PAGE fallback (e.g. serve index.html for directory requests)
                if (requestObj.isNoObj()) {
                    final String defaultPage = this.at(uri(DEFAULT_PAGE)).orElse(str("index.html")).strValue();
                    requestObj = Router.global().read(requestURI.extend(defaultPage));
                }

                // 4 — 404 if still nothing
                if (requestObj.isNoObj()) {
                    try {
                        sendError(404, "Not Found: " + requestURI);
                    } catch (final IOException e) {
                    }
                    return noobj();
                }

                // 5 — Detect Content-Type from object type, send response
                final Content.ContentType contentType = requestURI.hasQ(OUT) ?
                        Content.ContentType.of(requestURI.q(OUT)) :
                        Content.ContentType.fromType(requestObj, Content.ContentType.TEXT_PLAIN);
                this.send(requestObj, contentType);
                return requestObj;

            } catch (final Exception e) {
                LOG.error("error handling GET: %s", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                try {
                    sendError(500, "Internal Server Error");
                } catch (final IOException ignored) {
                }
                return noobj();
            }
        }));

        // ── ON_PUT: write objects back through Router ──
        this.jvm().put(uri(ON_PUT), instC(vid.extend(ON_PUT).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                if (Boolean.TRUE.equals(this.at(uri(READ_ONLY)).orElse(bool(true)).jvm())) {
                    try {
                        sendError(403, "Read-only");
                    } catch (final IOException ignored) {
                    }
                    return noobj();
                }
                final HttpExchange exchange = this.exchange;
                final fURI webRoot = this.at(uri(WEB_ROOT)).uriValue();
                final String mountPath = exchange.getHttpContext().getPath();
                final String fullPath = exchange.getRequestURI().getPath();
                final String relativePath = fullPath.startsWith(mountPath)
                        ? fullPath.substring(mountPath.length())
                        : fullPath;
                final fURI fileURI = relativePath.isEmpty()
                        ? webRoot
                        : webRoot.extend(f(relativePath));

                final String bodyStr = readBody(exchange);
                if (!bodyStr.isEmpty()) {
                    final Content.ContentType ct = Content.ContentType.fromExtension(fileURI.name(), Content.ContentType.TEXT_PLAIN);
                    final Obj bodyObj = ct.serializer().inputBytes(ByteBuffer.wrap(bodyStr.getBytes(StandardCharsets.UTF_8)));
                    Router.writeToSpace(fileURI, bodyObj);
                    try {
                        this.exchange.sendResponseHeaders(201, -1);
                    } catch (final IOException ignored) {
                    }
                } else {
                    try {
                        sendError(400, "No body");
                    } catch (final IOException ignored) {
                    }
                }
                return noobj();
            } catch (final Exception e) {
                LOG.error("error handling PUT: %s", e.getMessage());
                try {
                    sendError(500, "Internal Server Error");
                } catch (final IOException ignored) {
                }
                return noobj();
            }
        }));

        // ── ON_ERROR: send the error ──
        this.jvm().put(uri(ON_ERROR), instC(this.vid().extend(ON_ERROR).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                this.send(lhs);
                return noobj();
            } catch (final Exception e) {
                LOG.error("error processing error: %s", lhs, e);
                return noobj();
            }
        }));

        // ── SEND: override base default for proper mtron-style send ──
        this.jvm().put(uri(SEND), instC(this.vid().extend(SEND).dom(A.maybe()).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
            try {
                this.send(inst.arg(0));
                return inst.arg(0);
            } catch (final Exception e) {
                return noobj();
            }
        }));
    }
}
