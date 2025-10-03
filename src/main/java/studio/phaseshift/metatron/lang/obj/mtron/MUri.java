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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Uri;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.URI_TID;

public class MUri extends MObj implements Uri {

    public static Uri uri(final String s) {
        return MUri.of(s);
    }

    public static Uri uri(final fURI s) {
        return MUri.of(s);
    }

    public MUri(final fURI value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MUri(final fURI value) {
        this(value, URI_TID, fURI.NULL);
    }

    public MUri(final String value) {
        this(fURI.of(value));
    }

    @Override
    public Uri clone(final Object value, final fURI tid, final fURI vid) {
        return super.clone(value, tid, vid, (a, b, c) -> new MUri((fURI) a, b, c));
    }

    @Override
    public fURI value() {
        return (fURI) this.value;
    }

    public static Uri of(final fURI value) {
        return new MUri(value);
    }

    public static Uri of(final String value) {
        return MUri.of(fURI.of(value));
    }

    public static Uri of(final String value, final fURI tid) {
        return new MUri(fURI.of(value), tid, fURI.NULL);
    }
}


