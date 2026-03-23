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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.parser.ObjHTMLSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
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
import java.util.*;
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
                                final fURI requestURI = r.second().uriValue().extend(f(exchange.getRequestURI().getPath())).qString(exchange.getRequestURI().getQuery());
                                LOG.info("requesting: %s", requestURI);
                                final File base = Space.Helper.locateBaseFile(requestURI, INDEX_HTML);
                                // final Path filePath = null == base ? null : base.toPath(); //Files.isRegularFile(path) ? path : Path.of(path + "/" + INDEX_HTML);
                                if (null != base) {
                                    final Path absolutePath = base.toPath().toAbsolutePath();
                                    final Path relativePath = base.toPath();
                                    LOG.info("resolving context to request=>relative=>absolute path: %s => %s => %s", uri(exchange.getRequestURI().toString()), uri(relativePath.toString()), uri(absolutePath.toString()));
                                    // fURI toRemove = f(filePath.toString());
                                    final fURI pretractedURI = f(requestURI.toString().replace(INDEX_HTML,"").replace(relativePath.toString().replace(INDEX_HTML, ""), "")).asRelative(); //.removeSubpath(f(base.toPath().toString())).asRelative();
                                    LOG.info("remaining steps in request uri: %s", pretractedURI);
                                    if (pretractedURI.segmentLength() == 0) {
                                        // send the full html document
                                        final ContentType contentType = ContentType.of(requestURI.hasQ("content_type") ? requestURI.qValue("content_type", String.class) : Files.probeContentType(absolutePath));
                                        LOG.info("sending with content-type: %s", contentType.value);
                                        sendResponse(contentType, absolutePath.toFile(), exchange);
                                    } else {
                                        // send a subset of larger html document
                                        final ContentType contentType = ContentType.of(requestURI.hasQ("content_type") ? requestURI.qValue("content_type", String.class) : ContentType.APPLICATION_MTRON.value);
                                        LOG.info("sending with content-type: %s", contentType.value);
                                        sendResponse(contentType, ByteBuffer.wrap(
                                                new ObjByteBufferSerializer().write(HTML_SERIALIZER.read(
                                                        Jsoup.parse(absolutePath)).asRec().at(pretractedURI)).array()), exchange);
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
                                /// ////////////////////////////////////////////////////////////////////////////////////
                                /// ////////////////////////////////////////////////////////////////////////////////////
                                /// ////////////////////////////////////////////////////////////////////////////////////
                            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                                LOG.info("POST request received for %s", exchange.getRequestURI());
                                final String post = new BufferedReader(new InputStreamReader(exchange.getRequestBody())).lines().reduce("", (a, b) -> a + b + "\n");
                                final ContentType contentType = ContentType.of(exchange.getRequestHeaders().containsKey(ContentType.VALUE) ?
                                        exchange.getRequestHeaders().get(ContentType.VALUE).getFirst() :
                                        ContentType.APPLICATION_MTRON.value);
                                final File file = Space.Helper.locateBaseFile(r.second().uriValue().extend(f(exchange.getRequestURI().getPath())), INDEX_HTML);
                                if (file == null) {
                                    if (contentType.isMtron()) {
                                        final fURI writePattern = r.second().uriValue().extend(f(exchange.getRequestURI().getPath()));
                                        Router.writeToSpace(writePattern, mParser.parse(post));
                                    } else {
                                        File newFile = new File(r.second().uriValue().extend(f(exchange.getRequestURI().getPath())).toString());
                                        FileWriter writer = new FileWriter(newFile);
                                        writer.write(post);
                                        writer.close();
                                        String response = "<html><body><h1>404 Not Found</h1></body></html>";
                                        sendResponse(ContentType.TEXT_HTML, ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)), exchange);
                                    }
                                } else {
                                    try {
                                        String fileContent = Files.readString(file.toPath());
                                        final Rec existingObj = HTML_SERIALIZER.readRec(Jsoup.parse(fileContent));
                                        final Obj extendingObj;
                                        if (contentType.isMtron())
                                            extendingObj = mParser.parse(post);
                                        else if (contentType.isJson())
                                            extendingObj = JSON_TRANSLATOR.parse(post);
                                        else if (contentType.isHtml() || contentType.isXml())
                                            extendingObj = HTML_SERIALIZER.read(Jsoup.parse(fileContent));
                                        else if (contentType.isPlain())
                                            extendingObj = str(post);
                                        else
                                            throw MTronException.of("unsupported content-type: %s", contentType);
                                        final fURI reference = f(exchange.getRequestURI().getPath()).removePrefix(f(file.toPath().toString()));
                                        LOG.info("remaining: %s", reference);
                                        existingObj.at(reference, extendingObj, MUTABLE);
                                        LOG.info("existing obj: %s", existingObj);
                                        // final Path newPath = Paths.get(file.toPath().toString() + "-temp.html");
                                        Files.writeString(file.toPath(), HTML_SERIALIZER.writeRec(existingObj).toString());
                                        exchange.getResponseHeaders().set(ContentType.VALUE, ContentType.TEXT_HTML.value);
                                        sendResponse(ContentType.TEXT_PLAIN, ByteBuffer.wrap(
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
    
    private void sendResponse(final ContentType contentType, final File file, final HttpExchange exchange) throws
            IOException {
        sendResponse(contentType, ByteBuffer.wrap(Files.readAllBytes(file.toPath())), exchange);
    }

    private void sendResponse(final ContentType contentType, final ByteBuffer bytes, final HttpExchange exchange) throws
            IOException {
        exchange.getResponseHeaders().set(ContentType.VALUE, contentType.value);
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
                        runningPattern = runningPattern.asRelativeNode().retract(1);
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
                        final Uri key = uri(pattern.scheme(null).host(null).tail(steps).asRelative());
                        LOG.debug("page found -- searching for %s in %s", key, runningPattern);
                        final Obj subDocObj = key.uriValue().toString().isEmpty() ? docObj : docObj.asRec().at(key);
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
}
