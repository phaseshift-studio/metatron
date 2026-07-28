package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.PanelWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Selector;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Captures agent state at every lifecycle hook and produces an audit
 * trail in the result blackboard at {@code res("audit")}.  The trail
 * is a Lst of {@code [phase=>..., detail=>...]} Recs, plus both a
 * plain text table string and an interactive {@link TableWidget}
 * for terminal rendering.
 */
public class AuditFeature extends Feature {

    private static final fURI AUDIT = res("audit");
    private static final List<String> HEADERS = List.of("phase", "detail");

    public AuditFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // ── Lifecycle hooks ────────────────────────────────────────────

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.trail.clear();
        agent.at(AUDIT, noobj(), MUTABLE);
        snapshot(agent, "before_chat",
                rec(uri("features"), jnt(agent.features().lstValue().size()),
                        uri("systemMsgs"), jnt(agent.getSystemMessages().size())));
        agent.feature("audit").asRec().at(TO).apply(str("""
                                                        sys message: %s
                                                        prompt     : %s
                                                        """.formatted(agent.getSystemMessages().stream().collect(Collectors.joining()), agent.userMessage())));
        return noobj();
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
    public void onCompleteResponse(final Agent agent, final Str response) {
        snapshot(agent, "complete",
                rec(uri("chatLen"), jnt(response.strValue().length())));
        render(agent);
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

    // ── Render (table widget + text) ───────────────────────────────

    private void render(final Agent agent) {
        if (this.trail.isEmpty()) return;
        final List<Rec> rows = this.trail;

        // Persist trail as Lst
        agent.at(AUDIT, lst(rows.stream().map(r -> (Obj) r).toList()), MUTABLE);

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
        agent.at(res("audit", "table"), str(sb.toString()), MUTABLE);

        // Build interactive TableWidget
        final TableWidget table = new TableWidget(HEADERS).style().border(Border.continuous.foreground("{{y}}")).divider(Border.continuous.leftSide()).applyStyle();
        for (final Rec row : rows)
            table.addRow(List.of(
                    row.at(uri("phase")).strValue(),
                    fmt(row.at(uri("detail")))));
        agent.at(res("audit", "widget"), table, MUTABLE);
        final Selector selector = new Selector().style().attachment(table, true).pointer("{{r}}>").applyStyle()
                .onSelect((s, r, c) -> {
                    new PanelWidget(table.entry(r, c).toString(), table.row(r).toString()).run();
                });
        agent.at(res("audit", "widget"), selector, MUTABLE);
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
