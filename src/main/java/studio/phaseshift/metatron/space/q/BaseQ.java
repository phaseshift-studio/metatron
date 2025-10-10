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

import studio.phaseshift.metatron.lang.Q;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.Optional;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BaseQ implements Q {

    protected final fURI template;
    protected OnRead onRead;
    protected OnWrite onWrite;

    public BaseQ(final fURI template) {
        this.template = template;
    }

    @Override
    public fURI template() {
        return this.template;
    }

    @Override
    public Optional<Q.OnWrite> onWrite() {
        return Optional.ofNullable(this.onWrite);
    }

    @Override
    public Optional<Q.OnRead> onRead() {
        return Optional.ofNullable(this.onRead);
    }

    public static class OnWrite implements Q.OnWrite {

        @Override
        public Optional<Obj> preWrite(fURI source, fURI vid, Obj obj) {
            return Optional.empty();
        }

        @Override
        public Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj oldObj, final Obj newObj) {
            return Optional.empty();
        }

        @Override
        public Optional<Obj> qlessWrite(fURI source, fURI vid, Obj obj) {
            return Optional.empty();
        }
    }

    public static class OnRead implements Q.OnRead {

        @Override
        public Optional<Obj> preRead(fURI source, fURI vid) {
            return Optional.empty();
        }

        @Override
        public Optional<Obj> postRead(fURI source, fURI vid, Obj obj) {
            return Optional.empty();
        }
    }
}
