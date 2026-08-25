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

import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_RESULT_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The aggregate result of an {@link Agent#chat(String)} interaction — the
 * {@code chat_result::T} rec written to the chat feature's root space when a
 * chat completes.
 *
 * <p>Created by {@code Agent.chat()} and passed to every feature's
 * {@code onCompleteResponse} hook so features can attach their outputs.
 * The chat_result is deliberately address-first: feature-produced objs are
 * stored as {@code !*} auto_from_ references to their durable locations in
 * the uri space, while mono values (str, real, int, bool, ...) are embedded
 * inline.  This keeps the agent stateless — the chat_result is a thin
 * pointer-map over the distributed feature outputs, not a copy.
 *
 * <p>Agent owns the lifecycle: it builds the rec, dispatches the hook, and
 * writes the result to space via {@code ChatFeature.persist} (the chat
 * feature owns the {@code root} the result is stored at).  Features only
 * ever mutate the rec — the machinery stays out of the feature hooks.
 */
public class ChatResult extends MRec {

    public ChatResult(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(jvm), tid, vid);
    }

    public static ChatResult chatResult() {
        return new ChatResult(new LinkedHashMap<>(), LLM_CHAT_RESULT_TID, null);
    }

    /**
     * Embed a value directly into the result — use for mono values
     * (str, real, int, bool, uri, fail, ...).
     */
    public ChatResult put(final String key, final Obj value) {
        this.at(uri(key), value, MUTABLE);
        return this;
    }

    /**
     * Store a {@code !*} auto_from_ reference to a space obj's vid.
     * A no-op for null or empty vids.
     */
    public ChatResult putRef(final String key, final fURI vid) {
        if (null != vid && !vid.isEmpty())
            this.at(uri(key), auto_from_(vid).tryToInst(), MUTABLE);
        return this;
    }

    /**
     * Ref a persisted obj by its vid when it has one (recs/lsts written to
     * space); embed the value inline otherwise (monos carry no vid).
     */
    public ChatResult putRef(final String key, final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (null != obj.vid() && !obj.vid().isEmpty())
            return this.putRef(key, obj.vid());
        return this.put(key, obj);
    }
}
