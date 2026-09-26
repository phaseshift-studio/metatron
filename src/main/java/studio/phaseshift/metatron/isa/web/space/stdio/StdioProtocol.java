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

package studio.phaseshift.metatron.isa.web.space.stdio;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * The process's stdio, claimed for the MCP protocol.
 * <p>
 * On a stdio MCP session <b>fd 1 is the wire</b>: every byte written to stdout is read by the client as a
 * JSON-RPC message, so a single stray banner, log line or progress frame corrupts the session — and it does so
 * invisibly, because the client reports it as a protocol error rather than as noise.
 * <p>
 * metatron writes to stdout from many places that have nothing to do with the protocol: the boot banner and
 * every {@code print(...)} in a boot file (through {@link GraphittyLogger}'s {@code System.out} fallback), the
 * {@code print} instruction, {@code mSystem.out()}, {@code CommonUtil.Spinner}, and whatever a widget
 * {@code display()} renders. Rather than chase them, this class takes stdout the way a host does: the protocol
 * stream is claimed privately over {@link FileDescriptor#out} and {@code System.out} is re-pointed at stderr,
 * so everything that is not the protocol lands on the diagnostic channel the client shows as server logs.
 * <p>
 * The console solved the same problem in the other direction ({@code Console.installStdoutCapture} takes stdout
 * for its screen); this is the inverse discipline for a protocol host.
 * <p>
 * One detail is not optional: logback's {@link ConsoleAppender} resolves its stream <em>once</em>, in
 * {@code start()}, so a {@code System.setOut} performed afterwards does not move it — and the config shipped
 * inside the uber-jar ({@code src/main/resources/logback.xml}) has no {@code <target>}, i.e. it holds
 * {@code System.out}. So the appenders are re-pointed explicitly here, and the whole call is wrapped: a
 * diagnostic aid that can break the server it is watching is worse than none.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class StdioProtocol {

    private static final GraphittyLogger LOG = Graphitty.log(StdioProtocol.class);

    /**
     * The protocol stream — fd 1, claimed. Written only by the stdio carrier, never closed.
     */
    private static PrintStream PROTOCOL_OUT;

    private static volatile boolean installed = false;

    private StdioProtocol() {
        // do nothing
    }

    /**
     * Claim stdout for the protocol and hand {@code System.out} to stderr.  Idempotent, and safe to call
     * before the VM boots (which is when it must be called — a boot banner printed first is a corrupt
     * session).
     */
    public static synchronized void install() {
        if (installed)
            return;
        // fd 1 goes to the protocol; the System.out *object* is what everything else holds, so it is what
        // gets moved.  The error stream is left alone: it is the diagnostic channel the client surfaces.
        PROTOCOL_OUT = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        repointConsoleAppenders();
        installed = true;
        LOG.debug("stdio protocol installed — fd 1 is the mcp wire, stdout is stderr");
    }

    /**
     * The protocol stream; installs on first use, so a boot file that mounts {@code mcp_stdio} without the
     * {@code --mcp} flag still gets a correct wire.
     */
    public static synchronized PrintStream out() {
        install();
        return PROTOCOL_OUT;
    }

    public static boolean installed() {
        return installed;
    }

    /**
     * Move every logback console appender to stderr.  Initializing the logger context here is deliberate: it
     * makes the re-point deterministic rather than dependent on whether something else logged first.
     */
    private static void repointConsoleAppenders() {
        try {
            final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (!(factory instanceof LoggerContext context))
                return;
            context.getLoggerList().forEach(logger ->
                    logger.iteratorForAppenders().forEachRemaining(appender -> {
                        if (appender instanceof ConsoleAppender<?> console)
                            console.setOutputStream(System.err);
                    }));
        } catch (final Throwable e) {
            // never fail the boot over a log stream
            System.err.println("stdio: unable to repoint logback console appenders: " + e);
        }
    }
}
