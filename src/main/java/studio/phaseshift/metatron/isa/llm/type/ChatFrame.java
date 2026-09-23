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
import studio.phaseshift.metatron.isa.llm.WatermarkUtil;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Uri;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_RESULT_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The chat-specific frame — {@link Frame} plus the chat result fields ({@code chat},
 * {@code user}, {@code time}, {@code thinking}).  It is both the activation record while the
 * turn runs and the answer when it is popped back up — one type, not two.
 *
 * <p>Wears the {@code chat_result::T} tid, so {@code ChatFrame} is the {@code chat_result::T}
 * of the existing {@code chat} contract: the {@code ChatFrame} Java class is retired in favor
 * of this type, and the result-address methods ({@link #put}, {@link #putRef}, {@link #watermark},
 * {@link #watermarks}) live here so features attach their outputs exactly as before.
 */
public class ChatFrame extends Frame {

    public ChatFrame(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, LLM_CHAT_RESULT_TID, vid);
    }

    public static ChatFrame chatFrame() {
        return new ChatFrame(mutableMap(uri(RUNTIME), real(0.0, MATH_MILLIS_TID, null), uri(TIME), mathInstSet.nowDatetime()), null);
    }

    /**
     * Covariant {@link Frame#prompt} so the fluent chain stays a {@code ChatFrame}.
     */
    @Override
    public ChatFrame prompt(final String prompt) {
        super.prompt(prompt);
        return this;
    }

    public String chat() {
        final Obj c = this.at(uri(CHAT));
        return c.isStr() ? c.strValue() : null;
    }

    public String user() {
        final Obj u = this.at(uri(USER));
        return u.isStr() ? u.strValue() : null;
    }

    public Uri time() {
        return this.at(uri(TIME));
    }

    public Real runtime() {
        return this.at(uri(RUNTIME));
    }

    public Obj thinking() {
        return this.at(uri(THINKING));
    }

    // ── result-address methods (absorbed from the retired ChatFrame) ──

    /**
     * The in-band markers this response carried, in the model's own order, as the
     * {@code watermark} lst.  Empty when the response carried none.
     */
    public Lst watermarks() {
        final Obj watermarks = this.at(uri(WATERMARK));
        return watermarks.isLst() ? watermarks.asLst() : lst0();
    }

    /**
     * The argument rec the model addressed to {@code key} — the deferred call's argument,
     * resolved across all four cases a caller has to care about: absent ({@code noobj}),
     * decoded, empty body (an empty rec, not noobj), and undecodable ({@code noobj}).
     */
    public Obj watermark(final String key) {
        final Lst watermarks = this.watermarks();
        if (!WatermarkUtil.has(watermarks, key))
            return noobj();
        // a body that did not decode is reported back to the model separately —
        // there is no argument to act on, so it must not read as a zero-arg call
        if (WatermarkUtil.failed(watermarks, key).isRec())
            return noobj();
        final Obj body = WatermarkUtil.get(watermarks, key);
        return body.isNoObj() ? rec0() : body;
    }

    /**
     * Embed a value directly into the result — use for mono values
     * (str, real, int, bool, uri, fail, ...).
     */
    public ChatFrame put(final String key, final Obj value) {
        this.at(uri(key), value, MUTABLE);
        return this;
    }

    /**
     * Store a {@code !*} auto_from_ reference to a space obj's vid.
     * A no-op for null or empty vids.
     */
    public ChatFrame putRef(final String key, final fURI vid) {
        if (null != vid && !vid.isEmpty())
            this.at(uri(key), auto_from_(vid).tryToInst(), MUTABLE);
        return this;
    }

    /**
     * Ref a persisted obj by its vid when it has one (recs/lsts written to
     * space); embed the value inline otherwise.
     */
    public ChatFrame putRef(final String key, final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (null != obj.vid() && !obj.vid().isEmpty())
            return this.putRef(key, obj.vid());
        return this.put(key, obj);
    }
}
