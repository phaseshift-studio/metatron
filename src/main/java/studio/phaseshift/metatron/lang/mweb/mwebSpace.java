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

package studio.phaseshift.metatron.lang.mweb;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.msys.Space;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.msys.msysInstSet.SPACE_TID;
import static studio.phaseshift.metatron.lang.mtron.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.mweb.mwebInstSet.PAGE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mwebSpace extends MSpace<HttpServer> {

    public static final fURI WEB_TID = SPACE_TID.extend("web");
    protected static final String ROUTE = "route";
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();

    public mwebSpace(final HttpServer server, final Map<Obj, Obj> config, final fURI pattern, final fURI vid) {
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
            LOG.info("http route attached: %s => %s", uri(context.getPath()), r.second());
        });
        Graphitty.log(Router.global()).info("starting web server at %s", this.at("host").uriValue().scheme("http").toUri());
        server.setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        LOG.info("available routes: %s", this.at(ROUTE));
        server.start();
    }

    public static mwebSpace of(final fURI host, final Map<Obj, Obj> routes, final fURI pattern, final fURI vid) {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress(host.host(), host.port()), 0);
            final Map<Obj, Obj> config = new LinkedHashMap<>();
            config.put(uri("host"), host.toUri());
            config.put(uri("pattern"), pattern.toUri());
            config.put(uri("route"), rec(routes));
            return (mwebSpace) new mwebSpace(server, config, pattern, vid).tid(WEB_TID);
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
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return (pattern) -> {
            LOG.info("retrieving %s", pattern);
            try {
                Map<fURI, Obj> partial = new LinkedHashMap<>();
                final Document doc = Jsoup.connect(pattern.asNode().toString()).ignoreContentType(true).get();
                final Obj docObj = WEB_TRANSLATOR.translate(doc).tid(PAGE_TID);
                partial.put(pattern.asNode(), docObj);
                return partial;
            } catch (final Exception e) {
                return Map.of();
            }
        };
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helper.resolveRead(this, vid, directReader());
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isNoObj()) {
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
                    }*/
                }
            });
        }
        return obj;
    }
    
   /* @Override
    public Obj apply(final Obj obj) {
        LOG.info("performing remote apply: %s", obj);
        final FutureObj<Obj> future = this.jvm().sendRecvObj(obj);
        return future;
    }*/
}
