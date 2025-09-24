/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Objects;

import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public class MObj implements Obj {

    protected final Object value;
    protected final fURI tid;
    protected final fURI vid;

    public MObj(final Object value, final fURI tid, final fURI vid) {
        assert null != tid;
        this.value = value;
        this.tid = tid.big();
        this.vid = vid;
        if (!this.isType() && !this.isNoObj() && T(tid).apply(this).isNoObj())
            Graphitty.log(this).except("%s is not a %s".formatted(this, T(tid)));
        if (null != Router.global() && !this.isType() && null != this.vid && !this.vid.equals(fURI.NONE)) {
            Router.global().write(this.vid, this);
        }
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
    public <O extends Obj> O clone(final Object value, final fURI tid, final fURI vid) {
        return (O) this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value, this.tid, this.vid);
    }

    @Override
    public boolean equals(final Object other) {
        if (this.isNoObj())
            return other instanceof Obj && ((Obj) other).isNoObj();
        return this.getClass().isAssignableFrom(other.getClass()) &&
                (Objects.equals(this.tid, ((Obj) other).tid()) &&
                        Objects.equals(this.vid, ((Obj) other).vid()) &&
                        Objects.equals(this.value, ((Obj) other).value()));
    }

    @Override
    public String toString() {
        return Graphitty.string(this);
    }

    private static final Obj SINGLE = new MObj(new Object(), fURI.of("obj"), fURI.NULL);

    public static Obj single() {
        return SINGLE;
    }
}
