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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.HttpRec;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.webInstSet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.MIMEQ_PATTERN;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.update_;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class web_httpHandler extends HttpRec {

    public static final fURI WEB_HTTP_TID = WEB_ISA_TID.extend("http").extend("web_http");

    public static final Type WEB_HTTP_HANDLER_TYPE = Type.Builder.build()
            .tid(HTTP_HANDLER_TID)
            .vid(WEB_HTTP_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).else_(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).else_(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(WEB_ROOT).maybe(), T(ALL),
                    uri(DEFAULT_PAGE).maybe(), T(ALL),
                    uri(READ_ONLY).maybe(), T(ALL)))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(WEB_HTTP_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Map<Obj, Obj> config = new LinkedHashMap<>(inst.arg(0).asRec().jvm());
                config.putIfAbsent(uri(DEFAULT_PAGE), str("index.html"));
                config.putIfAbsent(uri(READ_ONLY), bool(false));
                return new web_httpHandler(config, inst.arg(0).asRec().vid());
            })).create();

    // ──────────────────────────────────────────────

    public web_httpHandler(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, WEB_HTTP_TID, vid);

        // ── ON_GET: serve objects from any Router-backed space ──
        this.jvm().put(uri(ON_GET), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                final HttpExchange exchange = this.exchange();
                if (exchange == null) {
                    // exchange is null during type-checking of the isaPredicate
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

                // Walk up the URI to find a path segment with a known file extension
                // for content-type detection (e.g. /test.json/number → test.json → application/json)
                fURI contentTypeHint = requestURI;
                while (contentTypeHint.segmentLength() > 0
                        && MIME.MIMEType.fromExtension(contentTypeHint.name(), null) == null) {
                    contentTypeHint = contentTypeHint.retract(1);
                }

                // 1 — Direct read from Router (space-agnostic: fsSpace, memSpace, etc.)
                Obj requestObj = Router.readFromSpace(requestURI.qprocLess());

                // 1.5 — When the request URI looks like a directory (no file extension),
                // try the DEFAULT_PAGE.  This handles / → local:web where fsSpace returns
                // a directory listing (rec or lst) rather than a DIR_TID URI.
                if (!requestObj.isNoObj() && !requestURI.name().contains(".")) {
                    final String defaultPage = this.at(uri(DEFAULT_PAGE)).orElse(str("index.html")).strValue();
                    final Obj defaultObj = Router.readFromSpace(requestURI.extend(defaultPage));
                    if (!defaultObj.isNoObj()) {
                        requestObj = defaultObj;
                    }
                }

                // 2 — locateBaseObj: walk up the URI path to find a containing object, then navigate into it
                boolean foundBase = false;
                if (requestObj.isNoObj()) {
                    final Space space = Router.global().getSpaceFor(requestURI);
                    if (space != null) {
                        final Space.IdObj baseObj = Space.Helper.locateBaseObj(space, requestURI, f(""));
                        // Only accept navigable base objects (rec/lst) — directories
                        // and other non-content types can't be used for field-level access.
                        if (baseObj != null && (baseObj.obj().isRec() || baseObj.obj().isLst())) {
                            foundBase = true;
                            String subPath = requestURI.toString().replaceFirst(baseObj.furi().toString(), "");
                            subPath = subPath.startsWith("/") ? subPath.substring(1) : subPath;
                            if (baseObj.obj().isRec())
                                requestObj = baseObj.obj().asRec().at(subPath);
                            else
                                requestObj = baseObj.obj().asLst().at(subPath);
                            // Sub-path navigation failed (e.g. base was a directory listing,
                            // not a content document) — allow DEFAULT_PAGE / 404 fallthrough
                            if (requestObj.isNoObj())
                                foundBase = false;
                        }
                    }
                }

                // 3 — DEFAULT_PAGE fallback (skip when a base document was found — the noobj
                //     represents a null field value or missing key within that document)
                //     Also applies when the path resolves to a directory (DIR_TID).
                if (isNoobjOrDir(requestObj) && !foundBase) {
                    final String defaultPage = this.at(uri(DEFAULT_PAGE)).orElse(str("index.html")).strValue();
                    requestObj = Router.global().read(requestURI.extend(defaultPage));
                }

                // 4 — 404 if still nothing (skip when a base document was found — see above)
                if (isNoobjOrDir(requestObj) && !foundBase) {
                    try {
                        sendError(404, "Not Found: " + requestURI);
                    } catch (final IOException e) {
                        LOG.warn("unable to send 404 response: %s", e.getMessage());
                    }
                    return noobj();
                }

                // 5 — Detect Content-Type: query param > object type > file extension > text/plain
                // Structured rec values use type-specific serializers (HTML, JSON).
                // Leaf values (str, jnt, bool, noobj, etc.) use APPLICATION_MTRON
                // so type information is preserved through the serialization round-trip.
                // fromType checks the obj's TID (works for both rec and typed str)
                final MIME.MIMEType contentType = requestURI.hasQ(MIMEQ_PATTERN) ?
                        MIME.MIMEType.of(requestURI.q(MIMEQ_PATTERN)) :
                        requestURI.hasQ(OUT) ?
                                MIME.MIMEType.of(requestURI.q(OUT)) :
                                MIME.MIMEType.fromType(requestObj,
                                        MIME.MIMEType.fromExtension(contentTypeHint.name(),
                                                requestObj.isRec() ? MIME.MIMEType.TEXT_PLAIN : MIME.MIMEType.APPLICATION_MTRON));
                this.send(requestObj, contentType);
                LOG.debug("served %s [contentType=%s, objTid=%s]", requestURI, contentType.value, requestObj.tid());
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

        // ── Write verbs (verbs = read / replace / update / unlink · actions = qprocs) ──
        //
        //   PUT    — replace the address with the request body      (201)
        //   POST   — alias of PUT: metatron's `->` idiom sends POST          (201)
        //   PATCH  — the update verb: apply the `>>=` update algebra
        //            to the existing object (body = delta)             (200)
        //   DELETE — unlink the address (write noobj)                 (204)
        //
        // Create/append stays a client-side address choice (`.../_?incrq`),
        // never a verb secret.  The request body was consumed — exactly
        // once — by HttpRec.buildRequest() (run by dispatchToMtron before
        // these handlers), deserialized with the handler's IN serializer
        // and delivered as request.body in the inst's lhs; the exchange's
        // request stream is one-shot and must never be re-read here
        // (a second read throws java.io.IOException("Stream is closed")).
        this.jvm().put(uri(ON_PUT), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)),
                (lhs, inst) -> writeValue(lhs)));

        this.jvm().put(uri(ON_POST), this.jvm().get(uri(ON_PUT)));

        this.jvm().put(uri(ON_PATCH), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)),
                (lhs, inst) -> updateValue(lhs)));

        this.jvm().put(uri(ON_DELETE), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)),
                (lhs, inst) -> deleteValue()));

        // ── ON_ERROR: send the error ──
        this.jvm().put(uri(ON_ERROR), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                this.send(lhs);
                return noobj();
            } catch (final Exception e) {
                LOG.error("error processing error: %s", lhs, e);
                return noobj();
            }
        }));

        // ── SEND: override base default for proper mtron-style send ──
        this.jvm().put(uri(SEND), instC(M_ISA_INST_TID.dom(A.maybe()).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
            try {
                this.send(inst.arg(0));
                return inst.arg(0);
            } catch (final Exception e) {
                return noobj();
            }
        }));

    }


    // ──────────────────────────────────────────────
    // Shared write-verb plumbing
    // ──────────────────────────────────────────────

    /** PUT / POST — replace the address with the request body. 201. */
    private Obj writeValue(final Obj lhs) {
        Obj value = lhs.isRec() ? lhs.asRec().at(uri(BODY)) : noobj();
        if (readOnlyGate()) {
            return noobj();
        }
        final HttpExchange exchange = this.exchange();
        if (exchange == null) {
            // exchange is null during type-checking of the isaPredicate
            return noobj();
        }
        if (value.isNoObj()) {
            sendErrorQuiet(400, "No body");
            return noobj();
        }
        // A body the IN serializer couldn't parse arrives as a raw string —
        // give it the mtron data grammar a parse (mtron's `->` write idiom
        // sends mtron-rendered bodies); a body the mtron parser rejects is
        // genuine text and stays a string.
        if (value.isStr()) {
            try {
                value = ObjmtronSerializer.parse(value.strValue());
            } catch (final Exception ignored) {
                // keep the raw string — text bodies are legitimate
            }
        }
        try {
            Router.writeToSpace(resolveFileURI(exchange), value);
            sendStatus(exchange, 201);
        } catch (final Exception e) {
            LOG.error("error handling write: %s", e.getMessage());
            sendErrorQuiet(500, "Internal Server Error");
        }
        return noobj();
    }

    /**
     * PATCH — the update verb.  The delta body is a mtron *expression* (the
     * {@code >>=} algebra: overlay, {@code +N} numeric add, {@code +[v]} set
     * promotion, {@code none} delete), not IN-serializer data — the data
     * parsers have no reading for the operators and silently mangle them
     * ({@code +[d=>100]} becomes the bare uri {@code <+>}).  Read it raw here
     * — the one-shot request stream is consumed exactly once — and hand it to
     * the ON_PATCH handler, which parses it with the full mtron parser.
     */
    @Override
    protected void doPatch(final HttpExchange exchange) throws IOException {
        final String bodyStr = readBody(exchange);
        if (bodyStr.isEmpty()) {
            sendError(400, "No body");
            return;
        }
        final Obj handler = this.at(uri(ON_PATCH));
        if (handler.isNoObj()) {
            sendError(405, "PATCH not supported");
            return;
        }
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(METHOD), str(exchange.getRequestMethod()));
        map.put(uri(URI), uri(exchange.getRequestURI().toString()));
        map.put(uri(BODY), str(bodyStr));
        handler.apply(rec(map));
    }

    /**
     * PUT / POST / PATCH: the mtron {@code >>=} update algebra applied to the
     * existing object at the address (body = delta).  A plain value delta
     * replaces the address wholesale; a structured delta merges recursively.
     *  200.
     */
    private Obj updateValue(final Obj lhs) {
        Obj delta = lhs.isRec() ? lhs.asRec().at(uri(BODY)) : noobj();
        if (readOnlyGate()) {
            return noobj();
        }
        final HttpExchange exchange = this.exchange();
        if (exchange == null) {
            // exchange is null during type-checking of the isaPredicate
            return noobj();
        }
        if (delta.isNoObj()) {
            sendErrorQuiet(400, "No body");
            return noobj();
        }
        // A delta that arrived as a raw string (the IN serializer had no
        // reading for it) is given the mtron update algebra a parse.
        if (delta.isStr()) {
            try {
                delta = ObjmtronSerializer.parse(delta.strValue());
            } catch (final Exception e) {
                sendErrorQuiet(400, "Unparseable delta: " + e.getMessage());
                return noobj();
            }
        }
        final fURI fileURI = resolveFileURI(exchange);
        try {
            final Obj base = Router.readFromSpace(fileURI);
            if (base.isNoObj()) {
                sendErrorQuiet(404, "Not Found: " + fileURI);
                return noobj();
            }
            final Obj updated = update_(delta).apply(base); // a >>= delta
            Router.writeToSpace(fileURI, updated);
            sendStatus(exchange, 200);
        } catch (final Exception e) {
            LOG.error("error handling update: %s", e.getMessage());
            sendErrorQuiet(500, "Internal Server Error");
        }
        return noobj();
    }

    /** DELETE — unlink the address (metatron's clear idiom: write noobj).  204. */
    private Obj deleteValue() {
        if (readOnlyGate()) {
            return noobj();
        }
        final HttpExchange exchange = this.exchange();
        if (exchange == null) {
            // exchange is null during type-checking of the isaPredicate
            return noobj();
        }
        try {
            Router.writeToSpace(resolveFileURI(exchange), noobj());
            sendStatus(exchange, 204);
        } catch (final Exception e) {
            LOG.error("error handling delete: %s", e.getMessage());
            sendErrorQuiet(500, "Internal Server Error");
        }
        return noobj();
    }

    /** The read_only gate — send 403 when set.  Returns true when handled. */
    private boolean readOnlyGate() {
        if (!Boolean.TRUE.equals(this.at(uri(READ_ONLY)).orElse(bool(false)).jvm())) {
            return false;
        }
        sendErrorQuiet(403, "Read-only");
        return true;
    }

    /** Mount-relative request path → space URI under web_root. */
    private fURI resolveFileURI(final HttpExchange exchange) {
        final fURI webRoot = this.at(uri(WEB_ROOT)).uriValue();
        final String mountPath = exchange.getHttpContext().getPath();
        final String fullPath = exchange.getRequestURI().getPath();
        final String relativePath = fullPath.startsWith(mountPath)
                ? fullPath.substring(mountPath.length())
                : fullPath;
        return relativePath.isEmpty()
                ? webRoot
                : webRoot.extend(f(relativePath));
    }

    private void sendStatus(final HttpExchange exchange, final int status) {
        try {
            exchange.sendResponseHeaders(status, -1);
        } catch (final IOException e) {
            LOG.warn("unable to send response: %s", e.getMessage());
        }
    }

    private void sendErrorQuiet(final int status, final String message) {
        try {
            sendError(status, message);
        } catch (final IOException e) {
            LOG.warn("unable to send error response: %s", e.getMessage());
        }
    }

    /**
     * Checks whether an object is noobj or a directory — used by DEFAULT_PAGE fallback.
     * A directory derefs to its own uri (no content), so a uri result means "not content."
     */
    private static boolean isNoobjOrDir(final Obj requestObj) {
        return requestObj.isNoObj() || requestObj.isUri();
    }
}
