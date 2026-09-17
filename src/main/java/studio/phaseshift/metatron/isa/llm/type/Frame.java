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

package studio.phaseshift.metatron.isa.llm.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_FRAME_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The generic "method frame" — an activation record for one function-like evaluation.
 * The spine: identity ({@code session}, {@code chat_id}, {@code depth}), the return address
 * ({@code parent}), the {@code run}/{@code complete} state, the argument ({@code prompt}),
 * and an open locals extension.
 *
 * <p>Address-first: a frame is written flat to its URI
 * {@code frame/s<sid>/c<cid>/d<depth>} and the nested tree is derived on read.  A Java field
 * that is transient per-iteration scratch becomes a locals key — this is the field-state sweep.
 */
public class Frame extends MRec {

    /** The run state — the frame is mid-evaluation. */
    public static final String RUN = "run";
    /** The complete state — the frame has returned. */
    public static final String COMPLETE = "complete";

    public Frame(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(jvm), tid, vid);
    }

    public static Frame frame() {
        return new Frame(new LinkedHashMap<>(), LLM_FRAME_TID, null);
    }

    // ── identity ────────────────────────────────────────────────────

    public fURI session() {
        final Obj s = this.at(uri(SESSION));
        return s.isUri() ? s.uriValue() : null;
    }

    public int chatId() {
        return this.at(uri(CHAT_ID)).orElse(jnt(0)).intValue().intValue();
    }

    public int depth() {
        return this.at(uri(DEPTH)).orElse(jnt(0)).intValue().intValue();
    }

    /** The caller frame's URI — the return address (null for the root frame). */
    public fURI parentURI() {
        final Obj p = this.at(uri(PARENT));
        return p.isUri() ? p.uriValue() : null;
    }

    // ── state ───────────────────────────────────────────────────────

    public boolean isRun() {
        return this.at(uri(STATE)).isUri() && RUN.equals(this.at(uri(STATE)).uriValue().name());
    }

    public boolean isComplete() {
        return this.at(uri(STATE)).isUri() && COMPLETE.equals(this.at(uri(STATE)).uriValue().name());
    }

    public Frame complete() {
        this.at(uri(STATE), uri(COMPLETE), MUTABLE);
        return this;
    }

    // ── argument ────────────────────────────────────────────────────

    public String prompt() {
        final Obj p = this.at(uri(PROMPT));
        return p.isStr() ? p.strValue() : null;
    }

    // ── identity writes (the ChatStack stamps these on push) ────────

    public Frame session(final fURI session) {
        this.at(uri(SESSION), uri(session), MUTABLE);
        return this;
    }

    public Frame chatId(final int chatId) {
        this.at(uri(CHAT_ID), jnt(chatId), MUTABLE);
        return this;
    }

    public Frame depth(final int depth) {
        this.at(uri(DEPTH), jnt(depth), MUTABLE);
        return this;
    }

    public Frame parentURI(final fURI parent) {
        this.at(uri(PARENT), uri(parent), MUTABLE);
        return this;
    }

    public Frame prompt(final String prompt) {
        this.at(uri(PROMPT), str(prompt), MUTABLE);
        return this;
    }
}
