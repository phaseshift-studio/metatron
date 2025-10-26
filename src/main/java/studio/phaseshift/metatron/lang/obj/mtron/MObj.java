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
import studio.phaseshift.metatron.util.MTronException;

import java.util.Objects;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.FAIL_TID;

public abstract class MObj implements Obj, Cloneable {

    protected Object jvm;
    protected fURI tid;
    protected fURI vid;

    protected MObj() {
        // for non-standard constructions
    }

    public MObj(final Object jvm, final fURI tid, final fURI vid) {
        assert null != tid;
        this.jvm = jvm;
        this.tid = tid.big();
        this.vid = vid;
        if (this.check())
            this.save();
    }

    protected boolean check() {
        if (!this.isNoObj() && !this.isType() && !this.matches(this.type())) {
            this.tid = FAIL_TID;
            this.vid = fURI.NULL;
            this.jvm = MTronException.of("[{{r}}type error{{/r}}] %s is not a %s".formatted(this, this.type()));
            return false;
        }
        return true;
    }

    protected void save() {
        if (null != vid && null != Router.global() && !this.isType())
            Router.global().write(this.vid, this);
    }

    @Override
    public <J> J jvm() {
        return (J) this.jvm;
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

    public <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid) {
        if (!Objects.equals(jvm, this.jvm) || !tid.equals(this.tid) || !Objects.equals(vid, this.vid)) {
            try {
                final MObj clone = (MObj) this.clone();
                clone.jvm = jvm;
                clone.tid = tid;
                clone.vid = vid;
                clone.check();
                clone.save();
                return (O) clone;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
        return (O) this;
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
