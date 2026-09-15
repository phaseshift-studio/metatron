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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.SSE_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.SSE_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.STREAM_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

public class SseStreamTest extends AbstractMetatronTest {

    @BeforeEach
    public void importWeb() {
        InstSet.importInstSet(WEB_ISA_TID);
    }

    private static String text(final ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void testDataFrame() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SseStream sse = new SseStream(out);
        sse.send("hello");
        sse.close();
        assertEquals("data: hello\n\n", text(out), "a bare data event is a data line + blank line");
    }

    @Test
    public void testEventAndDataFrame() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SseStream sse = new SseStream(out);
        sse.send("message", "{\"ping\":true}");
        sse.close();
        assertEquals("event: message\ndata: {\"ping\":true}\n\n", text(out), "an event name precedes the data field");
    }

    @Test
    public void testMultilineDataSplitsFields() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SseStream sse = new SseStream(out);
        sse.send("line one\nline two");
        sse.close();
        assertEquals("data: line one\ndata: line two\n\n", text(out), "multi-line payloads become repeated data fields");
    }

    @Test
    public void testCommentHeartbeat() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SseStream sse = new SseStream(out);
        sse.comment("ping");
        sse.close();
        assertEquals(": ping\n\n", text(out), "a comment is a colon line the client ignores");
    }

    @Test
    public void testDoneMarker() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SseStream sse = new SseStream(out);
        sse.done();
        sse.close();
        assertEquals("data: [DONE]\n\n", text(out), "done() emits the openai-style terminal event");
    }

    @Test
    public void testCloseIsIdempotent() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SseStream sse = new SseStream(out);
        sse.send("one");
        sse.close();
        sse.close();
        assertTrue(sse.isClosed(), "close() should be idempotent");
        assertEquals("data: one\n\n", text(out), "closing twice should not emit twice");
    }

    @Test
    public void testSendAfterCloseThrows() throws Exception {
        final SseStream sse = new SseStream(new ByteArrayOutputStream());
        sse.close();
        assertThrows(java.io.IOException.class, () -> sse.send("late"), "writing to a closed stream should fail");
    }

    @Test
    public void testSendObjFramesJson() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SseStream sse = new SseStream(out);
        final Obj obj = rec(uri("greeting"), str("hi"));
        sse.send(obj);
        sse.close();
        final String frame = text(out);
        assertTrue(frame.startsWith("data: "), "an obj event should carry a data field: " + frame);
        assertTrue(frame.endsWith("\n\n"), "an event should end with a blank line: " + frame);
        assertTrue(frame.contains("greeting"), "the obj should be json-serialized into the payload: " + frame);
    }

    @Test
    public void testEventStreamMimeMapsToSseSurface() {
        assertEquals(MIME.MIMEType.TEXT_EVENT_STREAM, MIME.MIMEType.of("text/event-stream"), "the wire type should resolve");
        assertEquals(SSE_TID, MIME.MIMEType.TEXT_EVENT_STREAM.toTid(), "text/event-stream should map to sse::T");
    }

    @Test
    public void testSseSurfaceRefinesStream() {
        assertNotNull(SSE_TYPE, "sse::T should be registered by webInstSet");
        assertEquals(STREAM_TID, SSE_TYPE.tid(), "sse::T should refine stream::T");
        assertEquals(SSE_TID, SSE_TYPE.vid(), "sse::T should live at /m/web/sse");
    }
}
