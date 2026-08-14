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

package studio.phaseshift.metatron.isa.llm;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore.WRITTEN_KEY;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Fluent builder for message {@link Rec} objects written to the
 * chronological message ledger.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Build and return a Rec without writing:
 * Rec msg = MessageBuilder.build(USER_MESSAGE_TID)
 *     .text("hello")
 *     .session(sessionVID)
 *     .create();
 *
 * // Build and write in one call:
 * MessageBuilder.build(SYSTEM_MESSAGE_TID)
 *     .text("you are a helpful assistant")
 *     .session(sessionVID)
 *     .create(writePath);
 * }</pre>
 */
public class MessageBuilder {

    private final fURI tid;
    private final Map<Obj, Obj> map;

    private MessageBuilder(final fURI tid) {
        this.tid = tid;
        this.map = mutableMap();
    }

    /**
     * Start building a message with the given type TID.
     */
    public static MessageBuilder build(final fURI tid) {
        return new MessageBuilder(tid);
    }

    /**
     * Set the {@code text} field.
     */
    public MessageBuilder text(final String text) {
        if (text != null && !text.isBlank())
            this.map.put(uri(TEXT), str(text));
        return this;
    }

    /**
     * Set the {@code contents} field.
     */
    public MessageBuilder contents(final String contents) {
        if (contents != null && !contents.isBlank())
            this.map.put(uri(CONTENTS), str(contents));
        return this;
    }

    /**
     * Set the {@code time} field to the current instant as a {@code datetime::T} URI.
     */
    public MessageBuilder time() {
        this.map.put(uri(TIME), mathInstSet.nowDatetime());
        return this;
    }

    /**
     * Set the {@code session} field.
     */
    public MessageBuilder session(final fURI sessionVID) {
        if (sessionVID != null)
            this.map.put(uri(SESSION), uri(sessionVID));
        return this;
    }

    /**
     * Set the {@code depth} field (recursion depth: 0=top-level, 1+=nested).
     */
    public MessageBuilder depth(final int depth) {
        this.map.put(uri(DEPTH), jnt(depth));
        return this;
    }


    public MessageBuilder written() {
        this.map.put(uri(WRITTEN_KEY), BOOL_TRUE);
        return this;
    }

    /**
     * Set the {@code chat_id} field (monotonic execution counter per session).
     */
    public MessageBuilder chatId(final int chatId) {
        this.map.put(uri(CHAT_ID), jnt(chatId));
        return this;
    }

    /**
     * Set an arbitrary field.
     */
    public MessageBuilder put(final String key, final Obj value) {
        if (key != null && value != null)
            this.map.put(uri(key), value);
        return this;
    }

    /**
     * Build the message Rec without writing it to a space.
     */
    public Rec create() {
        return rec(this.map, this.tid, null);
    }

    /**
     * Build the message Rec and write it to the given path.
     *
     * @return the written Rec (with VID assigned by the space)
     */
    public Rec create(final fURI writePath) {
        return Router.writeToSpace(writePath, this.create()).as();
    }
}
