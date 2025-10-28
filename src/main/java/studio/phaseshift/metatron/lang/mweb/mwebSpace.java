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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mweb.mwebInstSet.MWEB_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mwebSpace extends MSpace<HttpServer> {

    public static final fURI WEB_TID = MWEB_TID.extend("space/web");
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();
    private final GraphittyLogger LOG;

    public mwebSpace(final HttpServer server, final fURI pattern, final fURI vid) {
        super(server, pattern, WEB_TID, vid);
        LOG = Graphitty.log(this);
        final HttpContext context = server.createContext("/",
                exchange -> {
                    /*String response = "Hi there!";
                    exchange.sendResponseHeaders(200, response.getBytes().length);//response code and length
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();*/

                    LOG.info("connection from %s for %s", exchange.getRemoteAddress(), exchange.getRequestURI());
                    handleResponse(exchange, f(exchange.getRequestURI().toString()).path());
                });
        server.setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        LOG.info("http context loaded: %s", context.getPath());
        server.start();
    }

    public static mwebSpace of(final fURI authority, final fURI pattern, final fURI vid) {
        try {
            Graphitty.log(Router.global()).info("starting web server at %s:%d", authority.host(), authority.port());
            final HttpServer server = HttpServer.create(new InetSocketAddress(authority.host(), authority.port()), 0);
            final mwebSpace web = new mwebSpace(server, pattern, vid);

            //web.jvm().start();
            return web;
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }

    private void handleResponse(final HttpExchange exchange, final String request) throws IOException {
        LOG.info("retrieving page: %s", request);
        final OutputStream outputStream = exchange.getResponseBody();
        final StringBuilder htmlBuilder = new StringBuilder()
                .append("<html>").
                append("<body>").
                append("<h1>").
                append("metatron")
                .append(request)
                .append("</h1>")
                .append("</body>")
                .append("</html>");
        final String htmlResponse = htmlBuilder.toString();
        exchange.sendResponseHeaders(200, htmlResponse.length());
        outputStream.write(htmlResponse.getBytes());
        outputStream.flush();
        outputStream.close();
    }

    @Override
    public void close() {
        this.jvm().stop(1);
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
