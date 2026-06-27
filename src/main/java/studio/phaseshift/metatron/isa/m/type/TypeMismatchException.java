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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.util.MTronException;

/**
 * Thrown by {@link Obj.Helper#objTypeCheck(Obj)} when an instance fails to
 * satisfy its declared type.  Carries the original instance and type so that
 * interactive consoles can offer a table-based diff widget.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeMismatchException extends MTronException {

    private final Obj instance;
    private final Type type;

    public TypeMismatchException(final Obj instance, final Type type,
                                 final String format, final Object... args) {
        super(args.length == 0 ? format : format.formatted(args), null, true);
        this.instance = instance;
        this.type = type;
    }

    public Obj instance() {
        return instance;
    }

    public Type type() {
        return type;
    }
}
