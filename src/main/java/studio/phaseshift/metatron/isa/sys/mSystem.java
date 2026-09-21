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

package studio.phaseshift.metatron.isa.sys;

import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * metatron's stdio: the one place input is read from and text is written to, so that a host can take
 * over the terminal without anything else having to know.
 *
 * <p>The console is one such host — while it runs it owns the tty, holds it for keys and mouse, and
 * its reader consumes whatever is typed — but it is only one: a pipe, a test, an embedding
 * application or another user interface each own input in their own way.  So input is a stream with
 * an installer rather than a call into any of them:
 *
 * <pre>{@code
 *   mSystem.in()                       // the stream every consumer reads
 *   mSystem.in(myTerminalStream)       // a host that owns the terminal hands its input over
 *   mSystem.readLine()                 // one line, whoever is providing it
 * }</pre>
 *
 * <p>By default {@link #in()} is {@code System.in}, which is right for a pipe
 * ({@code echo "3" | metatron -p -e "_ + 5"}) and for anything headless.  The console installs a
 * stream that serves the lines its own reader takes, so {@code sys:stdin}, a human chat model and
 * any other consumer read exactly what the person is typing, with no knowledge of the console
 * between them.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mSystem {

    private static volatile InputStream in = System.in;

    private mSystem() {
    }

    /** The input every consumer reads: the terminal, a pipe, or a host's own stream. */
    public static InputStream in() {
        return mSystem.in;
    }

    /**
     * Hand input over — called by whoever owns it (see the console, which installs a stream over its
     * own reader).  A null stream restores {@code System.in}.
     */
    public static void in(final InputStream stream) {
        mSystem.in = null == stream ? System.in : stream;
    }

    /**
     * The output consumers write to.  It is {@code System.out}, which the console captures into its
     * screen (see {@code Console.installStdoutCapture}); a host wanting something else replaces the
     * stream rather than calling around it.
     */
    public static PrintStream out() {
        return System.out;
    }

    /**
     * One line of input, without its line ending, or null at end of input.
     * <p>
     * Read from {@link #in()} byte by byte rather than through a buffered reader: the stream may be
     * a host's line service, and a reader that buffers ahead would take input meant for the next
     * read.  {@code \r} is dropped so a terminal in either line discipline reads the same.
     */
    public static String readLine() {
        try {
            final InputStream stream = in();
            final ByteArrayOutputStream line = new ByteArrayOutputStream();
            int c = stream.read();
            while (c >= 0 && '\n' != c) {
                if ('\r' != c) line.write(c);
                c = stream.read();
            }
            if (c < 0 && line.size() == 0) return null;
            return line.toString(StandardCharsets.UTF_8);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
