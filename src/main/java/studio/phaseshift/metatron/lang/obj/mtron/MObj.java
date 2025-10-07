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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Objects;

import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public abstract class MObj implements Obj, Cloneable {

    protected Object value;
    protected fURI tid;
    protected fURI vid;

    public MObj(final Object value, final fURI tid, final fURI vid) {
        assert null != tid;
        this.value = value;
        this.tid = tid.big();
        this.vid = vid;
        if (!this.isType() && !this.isNoObj() && !this.matches(T(tid)))
            Graphitty.log(this).except("%s is not a %s".formatted(this, T(tid)));
        if (null != vid && null != Router.global() && !this.isType())
            Router.global().write(this.vid, this);

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
        return Helper.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Helper.objEquals(this, other);
    }

    @Override
    public String toString() {
        return Helper.objToString(this);
    }

    @Override
    public Obj clone() {
        try {
            return (Obj) super.clone();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public Obj clone(final Object newValue, final fURI newtid, final fURI newvid) {
        if (!Objects.equals(newValue, this.value) || !newtid.equals(this.tid) || !Objects.equals(newvid, this.vid)) {
            try {
                final MObj clone = (MObj) this.clone();
                clone.value = newValue;
                clone.tid = newtid;
                clone.vid = newvid;
                if (null != newvid && null != Router.global() && !this.isType())
                    Router.global().write(newvid, clone);
                return clone;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
        return this;
    }

    @Override
    public Obj take() {
        if (this.isNoObj())
            return null;
        final Obj r = this.clone();
        this.tid = fURI.NOOBJ;
        this.vid = fURI.NULL;
        return r;
    }
}
