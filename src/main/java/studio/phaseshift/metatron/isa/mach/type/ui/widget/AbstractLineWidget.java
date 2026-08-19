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

import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractLineWidget<W extends AbstractLineWidget<W>> extends AbstractWidget<W> {

    private static final Obj K_KEY = uri("key");
    private static final Obj K_ON_KEY = uri("on_key");

    public AbstractLineWidget() {
        super();
        // A line widget is always a single row tall.
        this.style.height(1);
    }

    public AbstractLineWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.style.height(1);
        this.readStyle();
    }

    /**
     * A line widget is always one row tall; any height configured on the
     * style is overridden to 1.
     */
    @Override
    public W style(final Style<W> style) {
        super.style(style);
        this.style.height(1);
        return (W) this;
    }

    protected void readStyle() {
        final Obj s = this.at(uri("style"));
        if (s != null && s.isRec()) {
            final Style<W> st = Style.from(s.as());
            st.stylable = (W) this;
            this.style(st);
        }
    }

    /**
     * The keyboard combo (e.g. {@code alt_m}) that triggers this line widget,
     * as a raw string.  Empty when no combo is configured.
     */
    public String key() {
        final Obj k = this.at(K_KEY);
        if (null == k || k.isNoObj()) return "";
        return k.isStr() ? k.strValue() : k.isUri() ? k.uriValue().name() : "";
    }

    /**
     * The instruction to apply when {@link #key()} is pressed.
     */
    public Obj onKey() {
        return this.at(K_ON_KEY);
    }

    /**
     * A line widget renders as a single row of text (no embedded newlines).
     */
    @Override
    public abstract String format();
}
