/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.furi.form;

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SAPPCQfURI extends SAPXCQfURI {

    protected final List<String> poly;
    protected Poly<?, ?> parsedPoly = null;

    public SAPPCQfURI(final String scheme, final String host, final int port, final List<String> path, final List<String> poly, final C<?, ?> coefficient, final Map<String, String> query) {
        super(scheme, host, port, path, coefficient, query);
        this.poly = null == poly ? List.of() : poly;
    }

    @Override
    public List<String> poly() {
        return this.poly;
    }

    public Poly<?, ?> polyParsed() {
        if (null != this.parsedPoly)
            return this.parsedPoly;
        if (!this.hasPoly())
            return this.parsedPoly = null;
        final List<String> poly = this.poly();
        if (poly.size() == 1) {
            if (poly.getFirst().trim().equals(","))
                return this.parsedPoly = lst0();
            else if (poly.getFirst().trim().equals("=>"))
                return this.parsedPoly = rec0();
        }
        if (poly.getFirst().contains("=>")) {
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            for (final String s : poly) {
                final String[] kv = s.split("=>");
                if (kv.length != 2)
                    throw MTronException.of("invalid rec type poly %s", s);
                map.put(uri(f(kv[0].trim()).big()), T(f(kv[1].trim()).big()));
            }
            return this.parsedPoly = rec(map);
        } else {
            final List<Obj> list = new ArrayList<>();
            for (final String s : poly) {
                list.add(T(f(s.trim()).big()));
            }
            return this.parsedPoly = lst(list);
        }
    }

}
