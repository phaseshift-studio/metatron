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

package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Captures agent state at every lifecycle hook and produces an audit trail.
 * The trail is a Lst of {@code [phase=>..., detail=>...]} Recs persisted to the
 * feature's {@code root} space and attached to the {@code chat_result::T} as a
 * reference.  A plain-text table rendering of the trail is stored alongside it.
 * <p>
 * Absorbed from the deleted StageFeature: the streaming hooks now count partial
 * response/thinking events, recorded in the final snapshot rather than as
 * per-chunk rows — the trail stays phase-granular.
 */
public class AuditFeature extends AbstractFeature {

    /** Counts of streamed events (from StageFeature) — recorded, not per-chunk rows. */
    private int partialResponses;
    private int partialThinkings;

    public AuditFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // ── Lifecycle hooks ────────────────────────────────────────────

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.trail.clear();
        this.partialResponses = 0;
        this.partialThinkings = 0;
        snapshot(agent, "before_chat",
                rec(uri("features"), jnt(agent.features().lstValue().size()),
                        uri("systemMsgs"), jnt(agent.hasFeature(SYSTEM) ? agent.feature(SYSTEM).<SystemFeature>as().getSystemMessages().size() : 0),
                        uri("userMessage"), str(null == agent.userMessage() ? "" : agent.userMessage())));
        agent.feature(AUDIT).asRec().at(TO).apply(str("""
                                                      {{_}}{{g}}system{{/g}}{{/_}}: %s
                                                      {{_}}{{g}}prompt{{/g}}{{/_}}: %s
                                                      """.formatted(agent.hasFeature(SYSTEM) ? agent.feature(SYSTEM).<SystemFeature>as().getSystemMessages().stream().collect(Collectors.joining()) : "", agent.userMessage())));
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        this.partialResponses++;
    }

    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        this.partialThinkings++;
    }

    @Override
    public void onPartialToolCall(final Agent agent, final Inst request) {
        snapshot(agent, "tool_call",
                rec(uri("tool"), request));
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        snapshot(agent, "tool_exec",
                result.isNoObj() ? rec() : result.asRec());
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        final Obj chatObj = result.at(uri(CHAT));
        final int chatLen = chatObj.isStr() ? chatObj.strValue().length() : 0;
        snapshot(agent, "complete",
                rec(uri("chatLen"), jnt(chatLen),
                        uri("partialResponses"), jnt(this.partialResponses),
                        uri("partialThinkings"), jnt(this.partialThinkings)));
        render(agent, result);
    }

    @Override
    public void onError(final Agent agent, final Fail fail) {
        snapshot(agent, "error",
                fail.isNoObj() ? rec() : rec(uri("message"), fail));
    }

    // ── Snapshot ───────────────────────────────────────────────────

    private final List<Rec> trail = new java.util.ArrayList<>();

    private void snapshot(final Agent agent, final String phase, final Obj detail) {
        this.trail.add(rec(uri("phase"), str(phase), uri("detail"), detail));
    }

    // ── Render (persist trail + terminal table) ────────────────────

    private void render(final Agent agent, final ChatResult result) {
        if (this.trail.isEmpty()) return;
        final List<Rec> rows = this.trail;

        // Build plain text table
        final StringBuilder sb = new StringBuilder();
        final int[] widths = {16, 48};
        for (final Rec row : rows) {
            final String detail = fmt(row.at(uri("detail")));
            if (detail.length() > widths[1]) widths[1] = Math.min(detail.length(), 64);
        }

        sb.append("\n").append('┌').append(repeat('─', widths[0]))
                .append('┬').append(repeat('─', widths[1])).append('┐').append('\n');
        sb.append('│').append(pad("phase", widths[0]))
                .append('│').append(pad("detail", widths[1])).append('│').append('\n');
        sb.append('├').append(repeat('─', widths[0]))
                .append('┼').append(repeat('─', widths[1])).append('┤').append('\n');
        for (final Rec row : rows) {
            final String phase = row.at(uri("phase")).strValue();
            final String detail = fmt(row.at(uri("detail")));
            sb.append('│').append(pad(phase, widths[0]))
                    .append('│').append(pad(fmtTrunc(detail, widths[1]), widths[1]))
                    .append('│').append('\n');
        }
        sb.append('└').append(repeat('─', widths[0]))
                .append('┴').append(repeat('─', widths[1])).append('┘').append("\n");

        // Persist the trail (+ its table rendering) to the feature's root and
        // attach a reference on the chat_result.
        final Obj root = this.at(ROOT);
        if (!root.isNoObj()) {
            try {
                final Map<Obj, Obj> row = new LinkedHashMap<>();
                row.put(uri("trail"), lst(rows.stream().map(r -> (Obj) r).toList()));
                row.put(uri("table"), str(sb.toString()));
                final Obj written = Router.writeToSpace(root.uriValue().extend("_").addQ(INCRQ), rec(row, null, null));
                result.putRef("audit", written);
            } catch (final Exception e) {
                LOG.warn("failed to persist audit trail: %s", e.getMessage());
            }
        }
    }

    // ── Formatting helpers ─────────────────────────────────────────

    private static String fmt(final Obj detail) {
        if (detail.isNoObj()) return "-";
        if (detail.isRec()) {
            final Rec r = detail.asRec();
            if (r.has(uri(NAME)) && r.has(uri(RESULT)))
                return r.at(uri(NAME)).strValue() + " → " + trunc(r.at(uri(RESULT)).strValue(), 32);
            if (r.has(uri("tool")))
                return r.at(uri("tool")).toString();
            if (r.has(uri("features"))) {
                final StringBuilder b = new StringBuilder("features: " + r.at(uri("features")).intValue());
                if (r.has(uri("systemMsgs")))
                    b.append(", system msgs: ").append(r.at(uri("systemMsgs")).intValue());
                return b.toString();
            }
            if (r.has(uri("chatLen")))
                return r.at(uri("chatLen")).intValue() + " chars";
            return r.toString();
        }
        return detail.toString();
    }

    private static String pad(final String s, final int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + repeat(' ', width - s.length());
    }

    private static String trunc(final String s, final int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + '…';
    }

    private static String fmtTrunc(final String s, final int max) {
        return trunc(s, max);
    }

    private static String repeat(final char c, final int n) {
        final StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
