/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.space.q;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class PubSubQ implements Q {

    // <source,pattern,callback>
    final List<Triplet<fURI, fURI, Code>> subscriptions = new ArrayList<>();

    @Override
    public String query() {
        return "sub";
    }

    @Override
    public void onQLessWrite(final fURI source, final fURI target, final Obj original) {
        this.subscriptions.stream().filter(t -> target.matches(t.get1())).forEach(t -> {
            t.get2().apply(original);
        });
    }

    /*@Override
    public void onPostWrite(final fURI source, final fURI target, final BObj.Obj original, final BObj.Obj replacement) {

    }*/

    @Override
    public Obj onPreRead(fURI source, fURI target) {
        return Q.super.onPreRead(source, target);
    }

    @Override
    public Obj onPostRead(fURI source, fURI target) {
        return Q.super.onPostRead(source, target);
    }

}
