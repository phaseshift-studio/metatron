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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Space;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MObj;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.Optional;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BaseQ extends MObj implements Q {

    protected final GraphittyLogger LOG;
    protected final Space space;
    protected OnRead onRead;
    protected OnWrite onWrite;

    public BaseQ(final Space space, final fURI queryPattern, final fURI tid) {
        super(queryPattern, tid, fURI.NULL);
        this.space = space;
        LOG = Graphitty.log(this);
    }

    @Override
    public Optional<Q.OnWrite> onWrite() {
        return Optional.ofNullable(this.onWrite);
    }

    @Override
    public Optional<Q.OnRead> onRead() {
        return Optional.ofNullable(this.onRead);
    }

    @Override
    public fURI jvm() {
        return super.jvm();
    }

    @Override
    public BaseQ clone(Object jvm, fURI tid, fURI vid) {
        return this;
    }

    @Override
    public Obj clone() {
        return this;
    }
}
