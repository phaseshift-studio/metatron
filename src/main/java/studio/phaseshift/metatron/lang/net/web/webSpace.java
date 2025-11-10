/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.net.web;

import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.mtronInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.ai.llm.type.impl.Audio.AUDIO_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mtronFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.mtronInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.mtronInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.net.web.webInstSet.MWEB_TID;
import static studio.phaseshift.metatron.lang.net.web.webInstSet.PAGE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class webSpace extends MSpace<HttpServer> {

    public static final fURI WEB_TID = MWEB_TID.extend("space").extend("web");
    protected static final String ROUTE = "route";
    protected static final Type WEB_TYPE = T(WEB_TID, null, instC(mtronInstSet.INST_TID.dom(ALL.maybe()).rng(WEB_TID), lst(T(REC_TID, isa_(rec(uri(PATTERN), T(URI_TID), uri(HOST), T(URI_TID), uri(ROUTE), T(REC_TID))))), (lhs, inst) -> {
        final fURI pattern = inst.arg(0).<Rec>as().at(PATTERN).uriValue();
        final fURI host = inst.arg(0).<Rec>as().at(HOST).uriValue();
        final Rec route = inst.arg(0).<Rec>as().at(ROUTE);
        final webSpace space = webSpace.of(host, route.jvm(), pattern, inst.arg(0).vid());
        Router.global().addSpace(space);
        return space;
    }));
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();
    private static final JSONTranslator JSON_TRANSLATOR = new JSONTranslator();

    public webSpace(final HttpServer server, final Map<Obj, Obj> config, final fURI pattern, final fURI vid) {
        super(server, config, pattern, WEB_TID, vid);
        // Router.writeToSpace(this.vid.extend(ROUTE), routes);
        this.at(ROUTE).orElse(rec()).elements().forEach(r -> {
            final HttpContext context = server.createContext(r.first().uriValue().toString(),
                    exchange -> {
                        //LOG.debug("using http context %s => %s => %s", exchange.getRequestURI(), exchange.getHttpContext().getPath(), r.second());
                        final Path path = Path.of(r.second().uriValue().extend(f(exchange.getRequestURI().getPath()).removePrefix(r.first().uriValue())).toString());
                        LOG.debug("resolving context to absolute path: %s => %s", uri(exchange.getRequestURI().toString()), uri(path.toAbsolutePath().toString()));
                        final Path filePath = Files.isRegularFile(path) ? path : Path.of(path + "/index.html");
                        final String contentType = Files.probeContentType(filePath);
                        exchange.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
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
                        }
                    });
            LOG.info("http route attached: %s", rel(uri(context.getPath()), r.second()));
        });
        LOG.info("starting web server at %s", this.at("host").uriValue().scheme("http").toUri());
        server.setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        LOG.info("available routes: %s", this.at(ROUTE));
        server.start();
    }

    public static webSpace of(final fURI host, final Map<Obj, Obj> routes, final fURI pattern, final fURI vid) {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress(host.host(), host.port()), 0);
            final Map<Obj, Obj> config = new LinkedHashMap<>();
            config.put(uri(HOST), host.toUri());
            config.put(uri(PATTERN), pattern.toUri());
            config.put(uri(ROUTE), rec(routes));
            return new webSpace(server, config, pattern, vid).tid(WEB_TID);
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void close() {
        this.sjvm().stop(0);
        super.close();
    }

    @Override
    public webSpace tid(final fURI tid) {
        return (webSpace) super.tid(tid);
    }

    @Override
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return (pattern) -> {
            LOG.debug("retrieving %s", pattern);
            try {
                final Map<fURI, Obj> partial = new LinkedHashMap<>();
                final Connection.Response response = Jsoup.connect(pattern.asNode().toString()).ignoreContentType(true).execute();
                final Obj docObj = (null != response.contentType() && response.contentType().equals("application/json")) ?
                        JSON_TRANSLATOR.translateString(response.body()) :
                        WEB_TRANSLATOR.translate(response.parse()).tid(PAGE_TID);
                partial.put(pattern.asNode(), docObj);
                return partial;
            } catch (final Exception e) {
                if (e.getMessage().contains("no bytes"))
                    return Map.of();
                throw MTronException.of(e);
            }
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            LOG.debug("writing %s", pattern);
            try (AutoCloseable client = (AutoCloseable) HttpClient.newHttpClient()) {
                final JsonElement json = JSON_TRANSLATOR.translate(obj);
                final HttpRequest request = HttpRequest.newBuilder()
                        .header("Content-Type", "application/json")
                        .uri(URI.create(pattern.toString()))
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();
                final HttpResponse<byte[]> response = ((HttpClient) client).send(request, HttpResponse.BodyHandlers.ofByteArray());
                LOG.info("%s", response.headers().firstValue("Content-Type"));
                final Optional<String> contentType = response.headers().firstValue("Content-Type");
                if (contentType.isPresent()) {
                    if (contentType.get().startsWith("audio/")) {
                        return rec(uri("location"), bytes(ByteBuffer.wrap(response.body()))).tid(AUDIO_TID);
                    } else if (contentType.get().equals("application/json")) {
                        return JSON_TRANSLATOR.translateString(new String(response.body()));
                    }
                }
                return jnt(response.statusCode());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helper.resolveRead(this, vid, directReader());
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        //Space.Helper.resolveWrite(this, vid, obj, directWriter(), directReader());
        return directWriter().apply(vid, obj);
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
