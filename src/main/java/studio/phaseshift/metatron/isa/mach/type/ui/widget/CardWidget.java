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

import java.util.Arrays;

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
        final String title = null != t && t.isStr() ? t.strValue() : "";
        final Obj b = this.at(K_BODY);
        final String body = null != b && b.isStr() ? b.strValue() : "";
        final StringBuilder sb = new StringBuilder();
        final int width = Utilities.maxWidth(Arrays.asList(title, body));
        sb.append(style.border().topLeftCorner())
                .append(style.border().topSide().repeat(width + style.leftMargin() + style.rightMargin()))
                .append(style.border().topRightCorner())
                .append("\n");
        for (int i = 0; i < style.topMargin(); i++) {
            sb.append(style.border().leftSide())
                    .append(" ".repeat(width + style.leftMargin() + style.rightMargin()))
                    .append(style.border().rightSide())
                    .append("\n");
        }
        sb.append(style.border().leftSide())
                .append(style.foreground())
                .append(style.background())
                .append(" ".repeat(style.leftMargin()))
                .append(title)
                .append(" ".repeat(width - Highlighter.visualLength(title) + style.rightMargin()))
                .append("{{X}}")
                .append(style.border().rightSide())
                .append("\n");
        if (!style.divider().isEmpty())
            sb.append(style.border().leftSide())
                    .append(style.divider().repeat(style.leftMargin() + width + style.rightMargin()))
                    .append("{{X}}")
                    .append(style.border().rightSide())
                    .append("\n");
        Arrays.stream(body.split("\n")).forEach(line ->
                sb.append(style.border().leftSide())
                        .append(style.foreground())
                        .append(" ".repeat(style.leftMargin()))
                        .append(line).append(" ".repeat(width - Highlighter.visualLength(line) + style.rightMargin()))
                        .append("{{X}}")
                        .append(style.border().rightSide())
                        .append("\n"));
        for (int i = 0; i < style.bottomMargin(); i++) {
            sb.append(style.border().leftSide())
                    .append(" ".repeat(width + style.leftMargin() + style.rightMargin()))
                    .append(style.border().rightSide())
                    .append("\n");
        }
        sb.append(style.border().bottomLeftCorner())
                .append(style.border().bottomSide().repeat(width + style.leftMargin() + style.rightMargin()))
                .append(style.border().bottomRightCorner());
        return sb.append("{{X}}").toString();
    }
}
