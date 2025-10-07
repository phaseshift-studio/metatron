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

package studio.phaseshift.metatron.lang.obj.mtron;

import org.apache.tinkerpop.gremlin.util.function.TriFunction;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Objects;

import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public abstract class MObj implements Obj {

    protected final Object value;
    protected final fURI tid;
    protected final fURI vid;

    public MObj(final Object value, final fURI tid, final fURI vid) {
        assert null != tid;
        this.value = value;
        this.tid = tid.big();
        this.vid = vid;
        if (!this.isType() && !this.isNoObj() && !this.matches(T(tid)))
            Graphitty.log(this).except("%s is not a %s".formatted(this, T(tid)));
    }

    @Override
    public Object value() {
        return this.value;
    }

    @Override
    public fURI tid() {
        return this.tid;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public int hashCode() {
        return ObjUtil.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return ObjUtil.objEquals(this, other);
    }

    @Override
    public String toString() {
        return ObjUtil.objToString(this);
    }

    public <O extends Obj> O clone(final Object newValue, final fURI newtid, final fURI newvid, final TriFunction<Object, fURI, fURI, O> constructor) {
        if (!Objects.equals(newValue, this.value) || !newtid.equals(this.tid) || !Objects.equals(newvid, this.vid)) {
            try {
                final O clone = constructor.apply(newValue, newtid, newvid);
                if (null != newvid && null != Router.global() && !this.isType())
                    Router.global().write(newvid, clone);
                return clone;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
        return (O) this;
    }
}
