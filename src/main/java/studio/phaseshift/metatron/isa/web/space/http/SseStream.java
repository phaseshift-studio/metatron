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

package studio.phaseshift.metatron.isa.web.space.http;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A server-sent-events (SSE) response channel — the streaming analog of
 * {@link HttpRec#send(Obj)}.
 * <p>
 * Whereas {@code send} writes one fixed-length body and closes, an {@code SseStream}
 * owns the chunked response body for the lifetime of the stream: each event is
 * framed ({@code data:}/\opt{@code event:} lines + a blank line) and flushed
 * immediately, so a client sees it before the stream ends.
 * <p>
 * Writes are {@code synchronized} — the handler thread that opened the stream is
 * the normal writer, but a producing thread (a {@code ?subq} subscription, a
 * future LLM token stream) may also push events, so the channel must be safe to
 * hand across that boundary. The primitive itself is pull/single-writer; a
 * bounded queue and backpressure for many producers is deliberately out of scope.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class SseStream implements Closeable {

    private final OutputStream out;
    private ObjJSONSerializer json;
    private boolean closed = false;

    public SseStream(final OutputStream out) {
        this.out = out;
    }

    /**
     * Whether this stream has been closed (or the client has hung up).
     */
    public synchronized boolean isClosed() {
        return this.closed;
    }

    /**
     * Send a single {@code data:} event (no {@code event:} name).
     */
    public synchronized void send(final String data) throws IOException {
        this.send(null, data);
    }

    /**
     * Send an event with an explicit {@code event:} name and a {@code data:} payload.
     * A multi-line payload is split into repeated {@code data:} fields (SSE spec).
     */
    public synchronized void send(final String event, final String data) throws IOException {
        this.ensureOpen();
        final StringBuilder frame = new StringBuilder();
        if (null != event && !event.isBlank())
            frame.append("event: ").append(event).append('\n');
        for (final String line : data.split("\n", -1))
            frame.append("data: ").append(line).append('\n');
        frame.append('\n');
        this.write(frame.toString());
    }

    /**
     * Send an event whose payload is the JSON serialization of a metatron obj.
     */
    public synchronized void send(final Obj obj) throws IOException {
        this.send(null, this.json().write(obj).toString());
    }

    /**
     * Send a {@code :} comment line — a keep-alive/heartbeat that a client ignores.
     */
    public synchronized void comment(final String text) throws IOException {
        this.ensureOpen();
        this.write(": " + text + "\n\n");
    }

    /**
     * Emit the terminal {@code data: [DONE]} event — the OpenAI chat-completions
     * convention. A generic SSE stream simply {@link #close() closes} without one.
     */
    public synchronized void done() throws IOException {
        this.send(null, "[DONE]");
    }

    @Override
    public synchronized void close() {
        if (this.closed)
            return;
        this.closed = true;
        try {
            this.out.flush();
        } catch (final IOException ignored) {
            // the client is gone — nothing to flush
        }
        try {
            this.out.close();
        } catch (final IOException ignored) {
            // closing is best-effort
        }
    }

    private void ensureOpen() throws IOException {
        if (this.closed)
            throw new IOException("sse stream is closed");
    }

    private void write(final String frame) throws IOException {
        this.out.write(frame.getBytes(StandardCharsets.UTF_8));
        this.out.flush();
    }

    private ObjJSONSerializer json() {
        if (null == this.json)
            this.json = ObjJSONSerializer.simple();
        return this.json;
    }
}
