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

package studio.phaseshift.metatron.isa.web.space.stdio.handler;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.space.stdio.StdioProtocol;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.isa.web.webInstSet;
import studio.phaseshift.metatron.util.MTronException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.isa.web.type.MIME.MIMEType.APPLICATION_JSON;
import static studio.phaseshift.metatron.isa.web.webInstSet.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The stdio MCP carrier — the third transport for a {@link mcpServer}, beside {@code mcp_wsHandler} and
 * {@code mcp_httpHandler}.
 * <p>
 * A stdio session is one process, one connection, one session: the client spawns this JVM, sends
 * {@code initialize}, and then pushes requests down the same pipe until it closes stdin (EOF), which ends the
 * VM. There is no {@code Mcp-Session-Id} — that is a Streamable-HTTP concept, and here the protocol's session
 * and the process's lifetime coincide.
 * <p>
 * The frames are newline-delimited JSON (one message per line, UTF-8, no embedded newlines), and
 * {@link mcpServer#handleMessage(String)} is the entry point rather than {@code handleMessage(Obj)}: the
 * schema-aware two-phase parse is what keeps a {@code code::T} argument from being mangled into a plain string
 * before the tool sees it.
 * <p>
 * <b>stdout belongs to the protocol</b> ({@link StdioProtocol}). Everything else — logs, boot banners, widget
 * renders — is re-pointed at stderr before the VM boots, because one stray byte on fd 1 is a corrupt session.
 * <p>
 * Following metatron's "no Java fields" principle, the carrier's state lives in its own jvm ({@code server},
 * {@code in}/{@code out}, {@code status}) so a running transport is introspectable; the input and output
 * streams are the sole fields, because a live stream is a handle rather than data.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcp_stdioHandler extends MRec {

    public static final fURI STDIO_MCP_HANDLER_TID = WEB_ISA_TID.extend(MCP).extend("mcp_stdio");

    public static final Type STDIO_MCP_HANDLER_TYPE = Type.Builder.build()
            .tid(MCP_TID)
            .vid(STDIO_MCP_HANDLER_TID)
            .isaPredicate(rec(
                    uri(HOST), T(MCP_SERVER_TID),
                    uri(IN).maybe().asUri(), webInstSet.MIME_OBJ_TYPE,
                    uri(OUT).maybe().asUri(), webInstSet.MIME_OBJ_TYPE,
                    uri(STATUS).maybe(), BOOL_TYPE))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(STDIO_MCP_HANDLER_TID),
                    lst(T(REC_TID)), (lhs, inst) -> mcp_stdioHandler.of(inst.arg(0).asRec())))
            .create();

    private static final GraphittyLogger LOG = Graphitty.log(mcp_stdioHandler.class);

    /**
     * The host that is serving this process's stdio, if one is — a stdio session is one connection by
     * construction, so a second carrier would be a second reader on the same pipe.
     */
    private static final AtomicReference<mcp_stdioHandler> SERVING = new AtomicReference<>();

    /**
     * The protocol handler this carrier carries (the composition every transport uses).
     */
    private final mcpServer mcp;

    /**
     * The input and output handles.  A live stream is not data, so it is not in the jvm; what a reader needs
     * to know *about* it is ({@code in}/{@code out} MIME types, {@code status}).
     */
    private final InputStream in;
    private final PrintStream out;

    private final Object writeLock = new Object();

    public mcp_stdioHandler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid,
                            final InputStream in, final PrintStream out) {
        super(jvm, tid, vid);
        this.mcp = (mcpServer) this.at(uri(HOST)).as();
        this.in = in;
        this.out = out;
    }

    public mcp_stdioHandler(final Rec recClone, final InputStream in, final PrintStream out) {
        this(mutableMap(recClone.jvm()), recClone.tid(), recClone.vid(), in, out);
    }

    // ========================================
    // Construction and lifecycle
    // ========================================

    /**
     * Build a carrier for the server named in {@code config} and start serving this process's stdio.  The
     * type constructor's entry point, so a boot file's {@code mcp_stdio::[host => …]} is all it takes.
     */
    public static Obj of(final Rec config) {
        return of(config, System.in, StdioProtocol.out(), true);
    }

    /**
     * Build a carrier over explicit streams.  {@code start} is false only for tests, which drive
     * {@link #handle(String)} themselves instead of handing fd 1 to a VM.
     */
    public static Obj of(final Rec config, final InputStream in, final PrintStream out, final boolean start) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(config.jvm());
        final Obj target = jvm.remove(uri(HOST));
        if (null == target || target.isNoObj())
            return fail(MTronException.of("mcp_stdio requires a host (the server to carry) — e.g. mcp_stdio::[host=>!*</sys/space/mcp/basic_server>]"));
        // the type a host is checked against lives in /m/web; without it there is no mcp_server::T to test
        // against, and a null type here used to throw out of the boot thread instead of failing (2026-09-26)
        if (null == webInstSet.MCP_SERVER_TYPE)
            return fail(MTronException.of("mcp_stdio needs /m/web imported — mcp_server::T is not registered"));
        // a route value's three shapes, the same ladder ws/http resolve: a uri that reads back to a server, a
        // server type to materialize, or a server already
        final Obj resolved = mcpServer.Helper.resolve(target);
        if (resolved.isNoObj() || !Obj.Helper.specificType(resolved).test(webInstSet.MCP_SERVER_TYPE))
            return fail(MTronException.of("mcp_stdio host is not an mcp_server: %s", target));
        jvm.put(uri(HOST), resolved);
        jvm.putIfAbsent(uri(IN), uri(config.at(IN).orElse(uri(APPLICATION_JSON.value)).uriValue()));
        jvm.putIfAbsent(uri(OUT), uri(config.at(OUT).orElse(uri(APPLICATION_JSON.value)).uriValue()));
        jvm.put(uri(STATUS), BOOL_TRUE);
        final mcp_stdioHandler handler = new mcp_stdioHandler(jvm, STDIO_MCP_HANDLER_TID, config.vid(), in, out);
        if (start)
            handler.start();
        return handler;
    }

    /**
     * Serve on a metatron virtual thread — not a bare Java thread, so the session has a thread identity the VM
     * can see ({@code /sys/thread/active}) and tool calls inherit it through {@code CURRENT_THREAD}.
     */
    public void start() {
        if (null != SERVING.get() && SERVING.get() != this)
            LOG.warn("a stdio carrier is already serving this process — %s will not be readable", this.vid());
        SERVING.set(this);
        final AbstractThread serve = virtual(instLambda((lhs, inst) -> {
            this.run();
            return noobj();
        }));
        serve.applyAsync();
    }

    /**
     * Whether a carrier is serving this process — the guard that makes mounting one from a boot file and
     * attaching one from {@code --mcp} idempotent rather than a competition for the same pipe.
     */
    public static boolean serving() {
        return null != SERVING.get();
    }

    // ========================================
    // The transport
    // ========================================

    /**
     * Read requests until the client closes stdin, then end the VM.  Blocking, and meant to own its thread.
     */
    public void run() {
        LOG.info("mcp stdio serving at {{b}}%s{{X}} — one message per line on stdin", this.vid());
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(this.in, StandardCharsets.UTF_8))) {
            String line;
            while (null != (line = reader.readLine())) {
                if (line.isBlank())
                    continue;
                try {
                    final Obj response = this.handle(line);
                    if (!response.isNoObj())
                        this.send(response);
                } catch (final Throwable e) {
                    // a bad line is the client's problem, not the session's: name it and keep reading
                    LOG.error("unable to handle stdio request: %s", null == e.getMessage() ? e.getClass().getName() : e.getMessage());
                    this.send(mcpServer.Helper.error(noobj(), -32700, "parse error: " + e.getMessage()));
                }
            }
        } catch (final IOException e) {
            LOG.error("stdio session ended on a read error: %s", e.getMessage());
        } finally {
            this.close();
            // EOF is how a client shuts a stdio server down: release the boot latch so main returns and the
            // JVM exits with the stream closed, rather than parking until something kills it
            BootLoader.close();
        }
    }

    /**
     * One line in, one response out — the whole protocol, and the seam a test drives directly.
     */
    public Obj handle(final String line) {
        return this.mcp.handleMessage(line);
    }

    /**
     * Write one JSON-RPC message: compact, single-line, flushed.  The write side is shared (a subscription's
     * code can fire on any thread), so it is serialized.
     */
    public void send(final Obj response) {
        // the simple serializer is compact by construction — no pretty printing — so a message is one line;
        // an embedded newline would end the message early and desynchronize the client
        final String json = ObjJSONSerializer.simple().write(response).toString();
        synchronized (this.writeLock) {
            this.out.print(json.replace('\n', ' ').replace('\r', ' '));
            this.out.print('\n');
            this.out.flush();
        }
    }

    /**
     * The IO this transport speaks — read from the jvm, the way {@code WebSocketObj.IO.of} reads a websocket
     * handler's, so the MIME types stay boot data rather than a Java constant.
     */
    public MIME.MIMEType[] getIO() {
        return new MIME.MIMEType[]{
                MIME.MIMEType.of(this.at(IN).orElse(uri(APPLICATION_JSON.value)).uriValue().toString()),
                MIME.MIMEType.of(this.at(OUT).orElse(uri(APPLICATION_JSON.value)).uriValue().toString())};
    }

    /**
     * Stop serving.  The protocol stream is deliberately left open (it is fd 1, not ours to close) and only
     * the state changes.
     */
    public void close() {
        this.jvm().put(uri(STATUS), BOOL_FALSE);
        if (SERVING.compareAndSet(this, null))
            LOG.info("mcp stdio session closed at {{b}}%s{{X}}", this.vid());
    }

    @Override
    public mcp_stdioHandler clone() {
        return this;
    }
}
