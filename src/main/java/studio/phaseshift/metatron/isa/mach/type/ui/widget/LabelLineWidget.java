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
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;

import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LabelLineWidget extends AbstractLineWidget<LabelLineWidget> {

    private static final Obj K_BODY = uri("body");

    public LabelLineWidget() {
        super();
    }

    public LabelLineWidget(final String body) {
        this();
        this.body(body);
    }

    public LabelLineWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public LabelLineWidget body(final String body) {
        jvmWrite(K_BODY, str(null != body ? body : ""));
        return this;
    }

    public String body() {
        final Obj b = this.at(K_BODY);
        return null != b && b.isStr() ? b.strValue() : "";
    }

    @Override
    public String format() {
        return this.style.foreground() + this.style.background() + this.body() + Widget.X;
    }
}
