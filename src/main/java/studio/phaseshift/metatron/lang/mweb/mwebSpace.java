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

import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.mweb.mwebInstSet.MWEB_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mwebSpace extends MSpace<HttpServer> {

    public static final fURI WEB_TID = MWEB_TID.extend("space/web");
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();
    private final Rec routes;

    public mwebSpace(final Tuple.Pair<HttpServer, Rec> serverAndRoutes, final fURI pattern, final fURI vid) {
        super(serverAndRoutes.get0(), pattern, WEB_TID, vid);
        (this.routes = serverAndRoutes.get1()).elements().forEach(r -> {
            final HttpContext context = this.jvm().createContext(r.first().uriValue().toString(),
                    exchange -> {
                        final Path path = Path.of(r.second().uriValue().extend(exchange.getRequestURI().getPath()).toString());
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
        this.jvm().start();
    }

    public static mwebSpace of(final fURI host, final Rec routes, final fURI pattern, final fURI vid) {
        try {
            Graphitty.log(Router.global()).info("starting web server at %s:%d", host.host(), host.port());
            final HttpServer server = HttpServer.create(new InetSocketAddress(host.host(), host.port()), 0);
            return new mwebSpace(Tuple.Pair.with(server, routes), pattern, vid);
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }

    public mwebSpace vid(final fURI vid) {
        if (null != vid) {
            Router.writeToSpace(vid.extend("host"), uri(jvm().getAddress().getAddress().toString()));
            Router.writeToSpace(vid.extend("route"), this.routes);
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
            throw MTronException.of(e);
        }
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return obj;
    }

   /* @Override
    public Obj apply(final Obj obj) {
        LOG.info("performing remote apply: %s", obj);
        final FutureObj<Obj> future = this.jvm().sendRecvObj(obj);
        return future;
    }*/
}
