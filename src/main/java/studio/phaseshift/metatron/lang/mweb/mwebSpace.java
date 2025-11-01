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
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MFail.fail;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.mweb.mwebInstSet.MWEB_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mwebSpace extends MSpace<HttpServer> {

    public static final fURI WEB_TID = MWEB_TID.extend("space/web");
    protected static final String ROUTE = "route";
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();
    private final Rec routes;

    public mwebSpace(final Tuple.Pair<HttpServer, Rec> serverAndRoutes, final fURI pattern, final fURI vid) {
        super(serverAndRoutes.get0(), pattern, WEB_TID, vid);
        this.routes = serverAndRoutes.get1();
        // Router.writeToSpace(this.vid.extend(ROUTE), routes);
        this.routes.elements().forEach(r -> {
            final HttpContext context = this.jvm().createContext(r.first().uriValue().toString(),
                    exchange -> {
                        //LOG.debug("using http context %s => %s => %s", exchange.getRequestURI(), exchange.getHttpContext().getPath(), r.second());
                        final Path path = Path.of(r.second().uriValue().extend(f(exchange.getRequestURI().getPath()).removePrefix(r.first().uriValue())).toString());
                        LOG.debug("resolving context to absolute path: %s => %s", uri(exchange.getRequestURI().toString()), uri(path.toAbsolutePath().toString()));
                        final Path filePath = Files.isRegularFile(path) ? path : Path.of(path + "/index.html");
                        final String contentType = Files.probeContentType(filePath);
                        exchange.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
                        exchange.sendResponseHeaders(200, Files.size(filePath));
                        LOG.info("sending %s [%s,%d bytes] per request from %s: %s", filePath, contentType, Files.size(filePath), exchange.getRemoteAddress(), exchange.getRequestURI());
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
            LOG.info("http context loaded: %s => %s", uri(context.getPath()), r.second());
        });
        this.jvm().setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        LOG.info("http contexts: %s", this.routes);
        this.jvm().start();
    }

    public static mwebSpace of(final fURI host, final Rec routes, final fURI pattern, final fURI vid) {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress(host.host(), host.port()), 0);
            Graphitty.log(Router.global()).info("starting web server at %s:%d", host.host(), host.port());
            return new mwebSpace(Tuple.Pair.with(server, routes), pattern, vid);
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }

    public mwebSpace vid(final fURI vid) {
        if (null != vid) {
            Router.writeToSpace(vid.extend("host"), uri(jvm().getAddress().getAddress().toString()));
            Router.writeToSpace(vid.extend(ROUTE), this.routes);
        }
        return (mwebSpace) super.vid(vid);
    }

    @Override
    public void close() {
        this.jvm().stop(0);
        super.close();
    }

    @Override
    public Obj read(final fURI vid) {
        try {
            LOG.info("retrieving %s", vid);
            final Document doc = Jsoup.connect(vid.toString()).ignoreContentType(true).get();
            LOG.debug("retrieved web page: %s", doc.location());
            return WEB_TRANSLATOR.translate(doc);
        } catch (final IOException e) {
            return fail(e);
        }
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isNoObj()) {
            try {
                this.jvm().removeContext(vid.path());
                LOG.info("removing route %s", vid.path());
                Router.writeToSpace(this.vid().extend(ROUTE).extend(vid.path()), noobj());
            } catch (final IllegalArgumentException e) {
                // do nothing (no such context exists)
            }
        } else {
            LOG.info("adding new route %s => %s", vid.path(), obj);
            this.write(vid, noobj());
            Router.writeToSpace(this.vid().extend(ROUTE), rec(uri(vid.path()), obj));
            this.jvm().createContext(vid.path(), exchange -> {
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
