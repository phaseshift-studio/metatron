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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

public abstract class MObj implements Obj, Cloneable {

    protected Object jvm;
    protected fURI tid;
    protected fURI vid;
    protected Obj parent = noobj();

    protected MObj() {
        // for non-standard constructions
    }

    public MObj(final Object jvm, final fURI tid, final fURI vid) {
        assert null != tid;
        this.self(jvm, tid.big(), vid);
        Obj.Helper.objCheckAndSave(this);
    }

    @Override
    public <O extends Obj> O parent(final Poly<?, ?> parent) {
        this.parent = parent;
        return (O) this;
    }

    @Override
    public Obj parent() {
        return this.parent;
    }

   /* protected boolean check() {
        if (!this.isInstSet() && !this.isNoObj() && !this.isType() && !this.matches(this.type()))
            throw MTronException.of("[{{r}}type error{{/r}}] %s is not a %s".formatted(this, this.type()));
        return true;
    }*/

   /* protected void save() {
        if (null != vid && !this.isType())
            Router.writeToSpace(this);
    }*/

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
        return Obj.Helper.objClone(this, jvm, tid, vid);
    }

    @Override
    public Obj take() {
        if (this.isNoObj())
            return null;
        return this;
    }

    @Override
    public <O extends Obj> O self(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = jvm;
        this.tid = tid.big();
        this.vid = vid;
        return (O) this;
    }
}
