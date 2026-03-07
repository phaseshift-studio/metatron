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
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjHTMLSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class httpSpace extends AbstractSpace<HttpServer> {

    public enum ContentType {
        APPLICATION_JSON("application/json"),
        APPLICATION_LD_JSON("application/ld+json"),
        MEDIA("media/"),
        MEDIA_MPEG("media/mpeg"),
        APPLICATION_OCTET_STREAM("application/octet-stream"),
        APPLICATION_ATOM_XML("application/atom+xml"),
        APPLICATION_XML("application/xml"),
        APPLICATION_MTRON("application/mtron"),
        TEXT_HTML("text/html"),
        TEXT_PLAIN("text/plain"),
        APPLICATION_XHTML_XML("application/xhtml+xml");
        final String value;

        ContentType(final String value) {
            this.value = value;
        }

        public static ContentType of(final String contentType) {
            return null == contentType ? TEXT_PLAIN : Arrays.stream(ContentType.values()).filter(ct -> (contentType.contains(ct.value))).findAny().orElse(TEXT_PLAIN);
        }

        public boolean isJson() {
            return this.equals(APPLICATION_JSON) || this.equals(APPLICATION_LD_JSON);
        }

        public boolean isHtml() {
            return this.equals(TEXT_HTML);
        }

        public boolean isMtron() {
            return this.equals(APPLICATION_MTRON);
        }

        public boolean isXml() {
            return this.equals(APPLICATION_ATOM_XML) || this.equals(APPLICATION_XHTML_XML) || this.equals(APPLICATION_XML);
        }

        public boolean isAudio() {
            return List.of(MEDIA, MEDIA_MPEG).contains(this);
        }

        public boolean isBinary() {
            return this.equals(APPLICATION_OCTET_STREAM);
        }

        public boolean isPlain() {
            return this.equals(TEXT_PLAIN);
        }

        public static final String VALUE = "Content-Type";
    }

    public static final String INDEX_HTML = "index.html";
    public static final fURI HTTP_SPACE_TID = WEB_ISA_TID.extend("space/http");
    public static final Rec CONFIG = rec(uri(Tokens.PATTERN), T(URI_TID), uri(HOST), T(URI_TID), uri(ROUTE), T(REC_TID));
    public static final Type HTTP_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(HTTP_SPACE_TID)
            .constructor(instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(HTTP_SPACE_TID),
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
                                final fURI requestURI = r.second().uriValue().extend(f(exchange.getRequestURI().getPath()));
                                final File base = locateBaseFile(requestURI);
                                final Path filePath = null == base ? null : base.toPath(); //Files.isRegularFile(path) ? path : Path.of(path + "/" + INDEX_HTML);
                                if (null != base) {
                                    LOG.debug("resolving context to absolute path: %s => %s", uri(exchange.getRequestURI().toString()), uri(filePath.toAbsolutePath().toString()));
                                    // fURI toRemove = f(filePath.toString());
                                    final fURI pretractedURI = f(exchange.getRequestURI().getPath()).removeSubpath(f(INDEX_HTML)).asRelative();
                                    LOG.info("remaining steps in request uri: %s", pretractedURI);
                                    if (pretractedURI.pathLength() == 0) {
                                        // send the full html document
                                        final String contentType = exchange.getRequestURI().getPath().endsWith("mtron") ?
                                                ContentType.APPLICATION_MTRON.value :
                                                Files.probeContentType(filePath);
                                        exchange.getResponseHeaders().set(ContentType.VALUE, contentType == null ? ContentType.APPLICATION_OCTET_STREAM.value : contentType);
                                        try (final InputStream input = Files.newInputStream(filePath)) {
                                            sendResponse(ByteBuffer.wrap(input.readAllBytes()), exchange);
                                        }
                                    } else {
                                        // send a subset of larger html document
                                        final String contentType = ContentType.APPLICATION_MTRON.value;
                                        exchange.getResponseHeaders().set(ContentType.VALUE,
                                                contentType == null ? ContentType.APPLICATION_OCTET_STREAM.value : contentType);
                                        sendResponse(ByteBuffer.wrap(
                                                new ObjByteBufferSerializer().write(HTML_SERIALIZER.read(
                                                        Jsoup.parse(filePath)).asRec().at(pretractedURI)).array()), exchange);
                                    }
                                } else {
                                    String response = "<html><body><h1>404 Not Found</h1></body></html>";
                                    exchange.getResponseHeaders().set(ContentType.VALUE, ContentType.TEXT_HTML.value);
                                    exchange.sendResponseHeaders(404, response.length());
                                    try (final OutputStream os = exchange.getResponseBody()) {
                                        os.write(response.getBytes());
                                        os.flush();
                                    }
                                }
                            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                                LOG.info("POST request received for %s", exchange.getRequestURI());
                                final String post = new BufferedReader(new InputStreamReader(exchange.getRequestBody())).lines().reduce("", (a, b) -> a + b + "\n");
                                final Obj obj = mParser.parse(post);
                                LOG.info("POST request body: %s", obj);
                                final File file = locateBaseFile(r.second().uriValue().extend(f(exchange.getRequestURI().getPath())));
                                if (file == null) {
                                    String response = "<html><body><h1>404 Not Found</h1></body></html>";
                                    exchange.getResponseHeaders().set(ContentType.VALUE, ContentType.TEXT_HTML.value);
                                    exchange.sendResponseHeaders(404, response.length());
                                    try (final OutputStream os = exchange.getResponseBody()) {
                                        os.write(response.getBytes());
                                        os.flush();
                                    }
                                } else {
                                    try {
                                        String fileContent = Files.readString(file.toPath());
                                        Rec htmlObj = ObjHTMLSerializer.parse(fileContent).asRec();
                                        final fURI reference = f(exchange.getRequestURI().getPath()).removePrefix(f(file.toPath().toString()));
                                        LOG.info("remaining: %s", reference);
                                        htmlObj.at(reference, obj);
                                        LOG.info("htmlObj: %s", htmlObj);
                                        final Path newPath = Paths.get(file.toPath().toString() + "-temp.html");
                                        Files.writeString(newPath, HTML_SERIALIZER.writeRec(htmlObj).toString());
                                        exchange.getResponseHeaders().set(ContentType.VALUE, ContentType.TEXT_HTML.value);
                                        sendResponse(ByteBuffer.wrap(
                                                new ObjByteBufferSerializer().write(HTML_SERIALIZER.read(
                                                        Jsoup.parse(newPath)).asRec().at(reference)).array()), exchange);

                                    } catch (final Exception e) {
                                        throw MTronException.of(e);
                                    }
                                }

                                this.directWriter().apply(r.second().uriValue().extend(f(exchange.getRequestURI().getPath())), mParser.parse(post));
                                try (final OutputStream os = exchange.getResponseBody()) {
                                    os.write(post.getBytes());
                                    os.flush();
                                }
                            } else {
                                //LOG.debug("using http context %s => %s => %s", exchange.getRequestURI(), exchange.getHttpContext().getPath(), r.second());
                                /*final String contentType = exchange.getRequestURI().getPath().endsWith("mtron") ? ContentType.APPLICATION_MTRON.value : Files.probeContentType(filePath);
                                LOG.debug("content-type: %s", contentType);
                                exchange.getResponseHeaders().set(ContentType.VALUE, contentType == null ? ContentType.APPLICATION_OCTET_STREAM.value : contentType);
                                exchange.sendResponseHeaders(200, Files.size(filePath));
                                LOG.debug("sending %s [%s,%d bytes] per request from %s: %s", filePath, contentType, Files.size(filePath), exchange.getRemoteAddress(), exchange.getRequestURI());
                                try (final InputStream is = Files.newInputStream(filePath);
                                     final OutputStream os = exchange.getResponseBody()) {
                                    byte[] buffer = new byte[8192]; // 8KB buffer
                                    int bytesRead;
                                    while ((bytesRead = is.read(buffer)) != -1) {
                                        os.write(buffer, 0, bytesRead);
                                        os.flush();
                                    }
                                }*/
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

    private void sendResponse(final ByteBuffer bytes, final HttpExchange exchange) throws IOException {
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

    private File locateBaseFile(final fURI vid) {
        fURI temp = vid.asNode();
        while (true) {
            Path path = Path.of(temp.toString());
            LOG.info("checking %s", path);
            if (path.toFile().exists() && path.toFile().isFile())
                return path.toFile();
            path = Path.of(temp.extend(INDEX_HTML).toString());
            LOG.info("checking %s", path);
            if (path.toFile().exists() && path.toFile().isFile())
                return path.toFile();
            temp = temp.retract().asNode();
            if (temp.pathLength() == 0)
                return null;
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
    public Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader() {
        return (pattern) -> {
            LOG.debug("retrieving %s", pattern);
            try {
                fURI runningPattern = pattern.clone();
                int steps = 0;
                while (true) {
                    LOG.debug("fetching %s", runningPattern.toString());
                    final Connection.Response response = Jsoup.connect(runningPattern.toString()).ignoreContentType(true).ignoreHttpErrors(true).execute();
                    LOG.debug("code: %d (%s)", response.statusCode(), runningPattern);
                    if (response.statusCode() == 404) {
                        if (runningPattern.pathLength() == 0)
                            return IteratorUtil.of();
                        steps++;
                        runningPattern = runningPattern.retract();
                    } else {
                        final ContentType contentType = ContentType.of(response.contentType());
                        LOG.debug("content-type: %s => %s", response.contentType(), contentType);
                        final Obj docObj = contentType.isMtron() ?
                                mParser.parse(response.body()) :
                                (contentType.isHtml() ?
                                        HTML_SERIALIZER.read(response.parse()) :
                                        (contentType.isJson() ?
                                                JSON_TRANSLATOR.parse(response.body()) :
                                                (contentType.isXml() ?
                                                        HTML_SERIALIZER.read(response.parse()) :
                                                        str(response.body()))));
                        final Uri key = uri(pattern.scheme(null).authority(null).tail(steps).asRelative());
                        LOG.debug("page found -- searching for %s in %s", key, runningPattern);
                        final Obj subDocObj = key.uriValue().toString().isEmpty() ? docObj : docObj.asRec().at(key);
                        return subDocObj.isNoObj() ? IteratorUtil.of() : IteratorUtil.of(Tuple.Pair.with(pattern, subDocObj));
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
                        .header(ContentType.VALUE, ContentType.APPLICATION_JSON.value)
                        .uri(URI.create(pattern.toString()))
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();
                final HttpResponse<byte[]> response = ((HttpClient) client).send(request, HttpResponse.BodyHandlers.ofByteArray());
                LOG.debug("%s", response.headers().firstValue(ContentType.VALUE));
                final Optional<String> contentType = response.headers().firstValue(ContentType.VALUE);
                if (contentType.isPresent()) {
                    if (contentType.get().equals(ContentType.APPLICATION_JSON.value))
                        return JSON_TRANSLATOR.parse(new String(response.body()));
                    else if (contentType.get().equals(ContentType.TEXT_HTML.value))
                        return HTML_SERIALIZER.read(Jsoup.parse(new String(response.body())));
                    else if (contentType.get().equals(ContentType.APPLICATION_MTRON.value))
                        return mParser.parse(new String(response.body()));
                }
                return jnt(response.statusCode());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    @Override
    public Obj read(final fURI vid) {
        return Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
        //return Space.Helper.resolveRead(this, vid, directReader());
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        //Space.Helper.resolveWrite(this, vid, obj, directWriter(), directReader());
        return this.directWriter().apply(vid, obj);
    }
        /*if (obj.isNoObj()) {
            try {
                this.sjvm().removeContext(vid.path());
                LOG.info("removing route %s", vid.path());
                Router.writeToSpace(this.vid().extend(ROUTE).extend(vid.path()), noobj());
            } catch (final IllegalArgumentException e) {
                // do nothing (no such context exists)
            }
        } else {
            LOG.info("adding new route %s => %s", vid.path(), obj);
            this.write(vid, noobj());
            Router.writeToSpace(this.vid().extend(ROUTE), rec(uri(vid.path()), obj));
            this.sjvm().createContext(vid.path(), exchange -> {
                String contentType = "text/plain";
                exchange.getResponseHeaders().set("Content-Type", contentType == null ? "text/plain" : contentType);
                final String result = Graphitty.strip(obj.apply(uri(exchange.getRequestURI().toString())).toString());
                exchange.sendResponseHeaders(200, result.length());
                LOG.info("sending %s [%s,%d bytes] per request from %s: %s", result, contentType, result.length(), exchange.getRemoteAddress(), exchange.getRequestURI());
                try (//final InputStream is = new ByteArrayInputStream(result.getBytes());
                     final OutputStream os = exchange.getResponseBody()) {
                    os.write(result.getBytes());
                    os.flush();
                    /*byte[] buffer = new byte[8192]; // 8KB buffer
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        os.flush();
                    }
                }
            });
        }
        return obj;*/
    
   /* @Override
    public Obj apply(final Obj obj) {
        LOG.info("performing remote apply: %s", obj);
        final FutureObj<Obj> future = this.jvm().sendRecvObj(obj);
        return future;
    }*/
}
