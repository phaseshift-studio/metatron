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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.STYLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_PROGRESS_TABLE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A {@link TableWidget} that formats progress rec rows into bars.
 * <p>
 * Headers: {@code [layer, progress, %]}.  Each progress rec has
 * {@code {text, percent}}; rows are keyed by column 0 for upserts.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ProgressTableWidget extends TableWidget {

    private static final int BAR_WIDTH = 35;

    public ProgressTableWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.headers.clear();
        this.headers.addAll(List.of("layer", "progress", " ", " "));
        if (this.has(STYLE)) {
            Style.from(this.at(STYLE), this);
        }
    }

    public ProgressTableWidget() {
        this(mutableMap(), UI_PROGRESS_TABLE_TID, null);
    }

    /**
     * Add/update a row from a progress rec, keyed by {@code text}.
     */
    public void addProgressRow(final Rec rec) {
        final String barBody = this.getStyle().textBody().isEmpty() ? "=" : this.getStyle().textBody();
        final String barHead = this.getStyle().pointer().isEmpty() ? ">" : this.getStyle().pointer();

        final Obj labelObj = rec.at(uri("text")).isNoObj()
                ? rec.at(uri("layer")) : rec.at(uri("text"));
        final String label = labelObj.isNoObj() ? ""
                : labelObj.isStr() ? labelObj.strValue()
                : labelObj.isUri() ? labelObj.uriValue().name() : "";
        final Obj pctObj = rec.at(uri("percent"));
        final double pct = pctObj.isNoObj() ? -1.0
                : pctObj.isReal() ? pctObj.realValue()
                : pctObj.isInt() ? pctObj.intValue().doubleValue() : -1.0;

        if (pct < 0) return; // skip layers with no progress data

        final int filled = (int) (BAR_WIDTH * pct / 100.0);
        final String bar = "[" + barBody.repeat(Math.max(0, Math.min(filled, BAR_WIDTH)))
                + (pct < 100 ? barHead : "")
                + " ".repeat(Math.max(0, BAR_WIDTH - filled - (pct < 100 ? 1 : 0))) + "]";
        final String pctStr = String.format("%3.0f%%", pct);

        super.addRow(List.<Object>of(label, bar, pctStr + " ".repeat(4 - pctStr.length()), pct >= 100 ? "{{g}}✓" : " "), 0);
    }
}
