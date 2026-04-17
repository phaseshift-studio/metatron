/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.tinkerpop.shaded.kryo.io.ByteBufferInputStream;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.parser.ObjHTMLSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class httpSpace extends AbstractSpace<HttpServer> {

    public static final String INDEX_HTML = "index.html";
    public static final fURI HTTP_SPACE_TID = WEB_ISA_TID.extend("space/httpspace");
    public static final Rec CONFIG = rec(uri(Tokens.PATTERN), T(URI_TID), uri(HOST), T(URI_TID), uri(ROUTE), T(REC_TID));
    public static final Type HTTP_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(HTTP_SPACE_TID)
            .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(HTTP_SPACE_TID),
                    lst(T(REC_TID, isa_(CONFIG))), (lhs, inst) -> httpSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();
    private static final ObjHTMLSerializer HTML_SERIALIZER = new ObjHTMLSerializer();
    private static final ObjJSONSerializer JSON_TRANSLATOR = new ObjJSONSerializer();

    protected httpSpace(final HttpServer server, final Map<Obj, Obj> config, final fURI vid) {
        super(server, config, HTTP_SPACE_TID, vid);
        // Router.writeToSpace(this.vid.extend(ROUTE), routes);
        try {
            this.at(ROUTE).orElse(rec()).elements().forEach(r -> {
                final HttpContext context = server.createContext(r.first().uriValue().toString(),
                        exchange -> {
                            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                                // Strip the route prefix from the request path to get the relative path
                                final String routePrefix = r.first().uriValue().toString();
                                String requestPath = exchange.getRequestURI().getPath();
                                if (requestPath.startsWith(routePrefix) && !routePrefix.equals("/")) {
                                    requestPath = requestPath.substring(routePrefix.length());
                                }
                                final fURI requestURI = r.second().uriValue().extend(f(requestPath)).qString(exchange.getRequestURI().getQuery());
                                LOG.debug("requesting: %s", requestURI);
                                final File base = Space.Helper.locateBaseFile(requestURI, INDEX_HTML);
                                // final Path filePath = null == base ? null : base.toPath(); //Files.isRegularFile(path) ? path : Path.of(path + "/" + INDEX_HTML);
                                if (null != base) {
                                    final Path absolutePath = base.toPath().toAbsolutePath();
                                    final Path relativePath = base.toPath();
                                    LOG.debug("resolving context to request=>relative=>absolute path: %s => %s => %s", uri(exchange.getRequestURI().toString()), uri(relativePath.toString()), uri(absolutePath.toString()));
                                    // fURI toRemove = f(filePath.toString());
                                    final fURI pretractedURI = f(requestURI.toString().replace(INDEX_HTML, "").replace(relativePath.toString().replace(INDEX_HTML, ""), "")).asRelative(); //.removeSubpath(f(base.toPath().toString())).asRelative();
                                    LOG.debug("remaining steps in request uri: %s", pretractedURI);
                                    if (pretractedURI.segmentLength() == 0) {
                                        // send the full html document
                                        // Use query param if specified, otherwise try Files.probeContentType, fallback to extension-based detection
                                        final Content.ContentType contentType;
                                        if (requestURI.hasQ("content_type")) {
                                            contentType = Content.ContentType.of(requestURI.qValue("content_type", String.class));
                                        } else {
                                            final String probed = Files.probeContentType(absolutePath);
                                            final Content.ContentType probedType = Content.ContentType.of(probed);
                                            // If probeContentType returned null or fell back to TEXT_PLAIN, use extension-based detection
                                            contentType = (probed == null || probedType == Content.ContentType.TEXT_PLAIN)
                                                    ? Content.ContentType.fromExtension(absolutePath.toString())
                                                    : probedType;
                                        }
                                        LOG.debug("sending with content-type: %s", contentType.value);
                                        sendResponse(contentType, absolutePath.toFile(), exchange);
                                    } else {
                                        // send a subset of larger html document
                                        final Content.ContentType contentType = Content.ContentType.of(requestURI.hasQ("content_type") ? requestURI.qValue("content_type", String.class) : Content.ContentType.APPLICATION_MTRON.value);
                                        LOG.debug("sending with content-type: %s", contentType.value);
                                        sendResponse(contentType, ByteBuffer.wrap(
                                                new ObjByteBufferSerializer().write(HTML_SERIALIZER.read(
                                                        Jsoup.parse(absolutePath)).asRec().at(pretractedURI)).array()), exchange);
                                    }
                                } else {
                                    String response = "<html><body><h1>404 Not Found</h1></body></html>";
                                    exchange.getResponseHeaders().set(Content.ContentType.VALUE, Content.ContentType.TEXT_HTML.value);
                                    exchange.sendResponseHeaders(404, response.length());
                                    try (final OutputStream os = exchange.getResponseBody()) {
                                        os.write(response.getBytes());
                                        os.flush();
                                    }
                                }
                                /// ////////////////////////////////////////////////////////////////////////////////////
                                /// ////////////////////////////////////////////////////////////////////////////////////
                                /// ////////////////////////////////////////////////////////////////////////////////////
                            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                                LOG.debug("POST request received for %s", exchange.getRequestURI());
                                // Strip the route prefix from the request path to get the relative path
                                final String postRoutePrefix = r.first().uriValue().toString();
                                String postRequestPath = exchange.getRequestURI().getPath();
                                if (postRequestPath.startsWith(postRoutePrefix) && !postRoutePrefix.equals("/")) {
                                    postRequestPath = postRequestPath.substring(postRoutePrefix.length());
                                }
                                final String post = new BufferedReader(new InputStreamReader(exchange.getRequestBody())).lines().reduce("", (a, b) -> a + b + "\n");
                                final Content.ContentType contentType = Content.ContentType.of(exchange.getRequestHeaders().containsKey(Content.ContentType.VALUE) ?
                                        exchange.getRequestHeaders().get(Content.ContentType.VALUE).getFirst() :
                                        Content.ContentType.APPLICATION_MTRON.value);
                                final File file = Space.Helper.locateBaseFile(r.second().uriValue().extend(f(postRequestPath)), INDEX_HTML);
                                if (file == null) {
                                    if (contentType.isMtron()) {
                                        final fURI writePattern = r.second().uriValue().extend(f(postRequestPath));
                                        Router.writeToSpace(writePattern, ObjmtronSerializer.parse(post));
                                    } else {
                                        File newFile = new File(r.second().uriValue().extend(f(postRequestPath)).toString());
                                        FileWriter writer = new FileWriter(newFile);
                                        writer.write(post);
                                        writer.close();
                                        String response = "<html><body><h1>404 Not Found</h1></body></html>";
                                        sendResponse(Content.ContentType.TEXT_HTML, ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)), exchange);
                                    }
                                } else {
                                    try {
                                        String fileContent = Files.readString(file.toPath());
                                        final Rec existingObj = HTML_SERIALIZER.readRec(Jsoup.parse(fileContent));
                                        final Obj extendingObj = contentType.toObj(post.getBytes());
                                        final fURI reference = f(exchange.getRequestURI().getPath()).removePrefix(f(file.toPath().toString()));
                                        LOG.debug("remaining: %s", reference);
                                        existingObj.at(reference, extendingObj, MUTABLE);
                                        LOG.debug("existing obj: %s", existingObj);
                                        // final Path newPath = Paths.get(file.toPath().toString() + "-temp.html");
                                        Files.writeString(file.toPath(), HTML_SERIALIZER.writeRec(existingObj).toString());
                                        exchange.getResponseHeaders().set(Content.ContentType.VALUE, Content.ContentType.TEXT_HTML.value);
                                        sendResponse(Content.ContentType.TEXT_PLAIN, ByteBuffer.wrap(
                                                new ObjByteBufferSerializer().write(HTML_SERIALIZER.read(
                                                        Jsoup.parse(file.toPath())).asRec().at(reference)).array()), exchange);

                                    } catch (final Exception e) {
                                        throw MTronException.of(e);
                                    }
                                }
                            }
                        });
                LOG.debug("http route attached: %s", rel(uri(context.getPath()), r.second()));
            });
            LOG.info("starting web server at %s", this.at(HOST).uriValue().scheme(Tokens.HTTP).toUri());
            server.setExecutor(BootLoader.getExecutor());
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
            LOG.info("available routes: %s", this.at(ROUTE));
            server.start();
        } catch (final Exception e) {
            LOG.error(MTronException.of(e));
            LOG.warn("%s server not started", this);
        }
    }

    private void sendResponse(final Content.ContentType contentType, final File file, final HttpExchange exchange) throws
            IOException {
        sendResponse(contentType, ByteBuffer.wrap(Files.readAllBytes(file.toPath())), exchange);
    }

    private void sendResponse(final Content.ContentType contentType, final ByteBuffer bytes, final HttpExchange exchange) throws
            IOException {
        exchange.getResponseHeaders().set(Content.ContentType.VALUE, contentType.value);
        exchange.sendResponseHeaders(200, bytes.remaining());
        try (final InputStream is = new ByteBufferInputStream(bytes);
             final OutputStream os = exchange.getResponseBody()) {
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                os.flush();
            }
        } catch (final Exception e) {
            exchange.sendResponseHeaders(500, 0);
            throw MTronException.of(e);
        }
    }

    public static httpSpace of(final Rec config, final fURI vid) {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress(config.at(HOST).uriValue().host(), config.at(HOST).uriValue().port()), 0);
            server.setExecutor(BootLoader.getExecutor());
            return new httpSpace(server, config.jvm(), vid);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void close() {
        this.sjvm().stop(0);
        super.close();
    }

    @Override
    public httpSpace tid(final fURI tid) {
        return (httpSpace) super.tid(tid);
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            LOG.debug("retrieving %s", pattern);
            try {
                fURI runningPattern = pattern;
                int steps = 0;
                while (true) {
                    LOG.debug("fetching %s", runningPattern.toString());
                    final Connection.Response response = Jsoup.connect(runningPattern.toString()).ignoreContentType(true).ignoreHttpErrors(true).execute();
                    LOG.debug("code: %d (%s)", response.statusCode(), runningPattern);
                    if (response.statusCode() == 404) {
                        if (runningPattern.segmentLength() == 0)
                            return IteratorUtil.of();
                        steps++;
                        runningPattern = runningPattern.asRelativeNode().retract(1).asAbsolute();
                    } else {
                        final Content.ContentType contentType = Content.ContentType.of(response.contentType());
                        LOG.debug("content-type: %s => %s", response.contentType(), contentType);
                        final Obj docObj = contentType.toObj(response.body());
                        final Uri key = uri(pattern.scheme(null).host(null).tail(steps).asRelative());
                        LOG.debug("page found -- searching for %s in %s", key, runningPattern);
                        final Obj subDocObj = key.uriValue().toString().trim().isEmpty() ? docObj : docObj.asRec().at(key);
                        return subDocObj.isNoObj() ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(pattern, subDocObj));
                    }
                }
            } catch (final Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("no bytes"))
                    return IteratorUtil.of();
                throw MTronException.of(e);
            }
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            LOG.debug("writing %s", pattern);
            try (final AutoCloseable client = (AutoCloseable) HttpClient.newHttpClient()) { // a true jvm bug!
                final JsonElement json = JSON_TRANSLATOR.write(obj);
                final HttpRequest request = HttpRequest.newBuilder()
                        .header(Content.ContentType.VALUE, Content.ContentType.APPLICATION_JSON.value)
                        .uri(URI.create(pattern.toString()))
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();
                final HttpResponse<byte[]> response = ((HttpClient) client).send(request, HttpResponse.BodyHandlers.ofByteArray());
                LOG.debug("%s", response.headers().firstValue(Content.ContentType.VALUE));
                final Optional<String> contentType = response.headers().firstValue(Content.ContentType.VALUE);
                if (contentType.isPresent()) {
                    return Content.ContentType.of(contentType.get()).toObj(response.body());
                }
                return jnt(response.statusCode());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }
}
