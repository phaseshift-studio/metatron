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

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CardWidget extends AbstractWidget<CardWidget> {

    private static final Obj K_TITLE = uri("title");
    private static final Obj K_BODY = uri("body");

    public CardWidget(final String title, final String body) {
        super();
        this.put(K_TITLE, str(title.trim()));
        this.put(K_BODY, str(body.trim()));
    }

    @Override
    public String format() {
        final Style<CardWidget> style = this.getStyle();
        final Obj t = this.at(K_TITLE);
        final String authored = null != t && t.isStr() ? t.strValue() : "";
        final Obj b = this.at(K_BODY);
        final String body = null != b && b.isStr() ? b.strValue() : "";

        final int margins = style.leftMargin() + style.rightMargin();
        final int declared = style.width();
        // A card is never drawn wider than the width it was given — borders and margins
        // included — and it does not stretch to fill one either: a card is a badge for
        // one obj, not a pane.  Growing PAST the width is what a title longer than the
        // card used to do (see Utilities.titleClip); a body longer than it is wrapped
        // rather than left to widen the box.
        final String title = Utilities.titleClip(authored, declared, 2 + margins);
        final List<String> lines = bodyLines(body, declared > 0 ? Math.max(1, declared - 2 - margins) : 0);
        final List<String> rows = new ArrayList<>(lines.size() + 1);
        rows.add(title);
        rows.addAll(lines);
        final int width = Utilities.maxWidth(rows);

        final StringBuilder sb = new StringBuilder();
        sb.append(style.border().topLeftCorner())
                .append(style.border().topSide().repeat(width + margins))
                .append(style.border().topRightCorner())
                .append("\n");
        for (int i = 0; i < style.topMargin(); i++) {
            sb.append(style.border().leftSide())
                    .append(" ".repeat(width + margins))
                    .append(style.border().rightSide())
                    .append("\n");
        }
        sb.append(style.border().leftSide())
                .append(style.foreground())
                .append(style.background())
                .append(" ".repeat(style.leftMargin()))
                .append(title)
                .append(" ".repeat(Math.max(0, width - Highlighter.visualLength(title) + style.rightMargin())))
                .append("{{X}}")
                .append(style.border().rightSide())
                .append("\n");
        if (!style.divider().isEmpty())
            sb.append(style.border().leftSide())
                    .append(style.divider().repeat(margins + width))
                    .append("{{X}}")
                    .append(style.border().rightSide())
                    .append("\n");
        lines.forEach(line ->
                sb.append(style.border().leftSide())
                        .append(style.foreground())
                        .append(" ".repeat(style.leftMargin()))
                        .append(line).append(" ".repeat(Math.max(0, width - Highlighter.visualLength(line) + style.rightMargin())))
                        .append("{{X}}")
                        .append(style.border().rightSide())
                        .append("\n"));
        for (int i = 0; i < style.bottomMargin(); i++) {
            sb.append(style.border().leftSide())
                    .append(" ".repeat(width + margins))
                    .append(style.border().rightSide())
                    .append("\n");
        }
        sb.append(style.border().bottomLeftCorner())
                .append(style.border().bottomSide().repeat(width + margins))
                .append(style.border().bottomRightCorner());
        return sb.append("{{X}}").toString();
    }

    /**
     * The card's body as rows, wrapped to {@code room} columns when the card was
     * told to be narrower than what it holds — a line that will not fit is wrapped
     * rather than left to be clipped by whatever draws the card (the surface clips
     * an over-wide line by STRIPPING it, which takes the card's colors with it).
     * {@code room <= 0} — no declared width — leaves the body exactly as authored.
     */
    private static List<String> bodyLines(final String body, final int room) {
        final List<String> lines = new ArrayList<>(List.of(body.split("\n")));
        if (room <= 0) return lines;
        final List<String> wrapped = new ArrayList<>(lines.size());
        for (final String line : lines) wrapped.addAll(Utilities.wordWrap(line, room));
        return wrapped;
    }
}
