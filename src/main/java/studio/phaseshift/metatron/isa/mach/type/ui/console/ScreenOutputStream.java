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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * An {@link java.io.OutputStream} that hands text on a line at a time.
 *
 * <p>It exists because not every writer of console text can be found and rerouted:
 * a {@code print} reaches the terminal through {@code GraphittyLogger}'s
 * {@code System.out} fallback, Logback's appender holds the stream it was started
 * with, and any library may write to stdout on its own.  Replacing the process's
 * stdout with this stream is what turns "I routed the writers I knew about" into
 * "everything the process prints arrives here" — and on a console that owns its
 * screen, arriving anywhere else means being scrolled away and lost.
 *
 * <p>Text is delivered a whole line at a time and decoded only up to the last
 * newline seen: a multi-byte character written in pieces is never decoded half
 * way, because the bytes of a character never straddle a newline.  Whatever is
 * left over waits for the next write, and {@link #flush()} delivers it as a final
 * partial line (a prompt with no newline is still a prompt).
 *
 * <p>Every method is synchronized: several threads print to one process stdout.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ScreenOutputStream extends java.io.OutputStream {

    private final Consumer<String> sink;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream(256);

    /**
     * @param sink receives text as it completes — one call per write that contains
     *             a newline, plus one per {@link #flush()}; text carries its own
     *             newlines so the screen sees the same line structure stdout did
     */
    public ScreenOutputStream(final Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public synchronized void write(final int b) {
        this.pending.write(b);
        this.drainToLastNewline();
    }

    @Override
    public synchronized void write(final byte[] bytes, final int offset, final int length) {
        this.pending.write(bytes, offset, length);
        this.drainToLastNewline();
    }

    /**
     * Deliver whatever is left, even without a newline: a caller that flushes is
     * saying "that is all there is for now".
     */
    @Override
    public synchronized void flush() {
        if (0 == this.pending.size()) return;
        final String text = this.pending.toString(StandardCharsets.UTF_8);
        this.pending.reset();
        this.sink.accept(text);
    }

    @Override
    public void close() {
        this.flush();
    }

    /** Hand on everything up to and including the last newline; keep the rest. */
    private void drainToLastNewline() {
        final byte[] bytes = this.pending.toByteArray();
        int lastNewline = -1;
        for (int i = bytes.length - 1; i >= 0; i--) {
            if ('\n' == bytes[i]) {
                lastNewline = i;
                break;
            }
        }
        if (lastNewline < 0) return;
        final String text = new String(bytes, 0, lastNewline + 1, StandardCharsets.UTF_8);
        this.pending.reset();
        this.pending.write(bytes, lastNewline + 1, bytes.length - lastNewline - 1);
        this.sink.accept(text);
    }
}
