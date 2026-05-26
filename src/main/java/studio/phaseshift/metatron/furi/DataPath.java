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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.List;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public record DataPath(String db, String collection, String entry, String field, fURI extension) {

    public static DataPath of(final fURI vid) {
        final fURI fprops;
        if (vid.pathLength() > 4) {
            final List<String> props = vid.segments().subList(4, vid.segmentLength());
            fprops = fURI.of(null, null, -1, props, vid.c(), null, vid.qMap(), vid.templates());
        } else {
            fprops = null;
        }
        return new DataPath(vid.segments(0, null), vid.segments(1, null), vid.segments(2, null), vid.segments(3, null), fprops);
    }

    public static DataPath ofSpaceRelative(final fURI vid, final String db) {
        final fURI fprops;
        if (vid.pathLength() > 3) {
            final List<String> props = vid.segments().subList(3, vid.segmentLength());
            fprops = fURI.of(null, null, -1, props, vid.c(), null, vid.qMap(), vid.templates());
        } else {
            fprops = null;
        }
        return new DataPath(db, vid.segments(0, null), vid.segments(1, null), vid.segments(2, null), fprops);
    }

    public fURI spaceURI() {
        fURI path = f("");
        if (this.hasDb()) path = path.extend(this.db);
        else return path;
        if (this.hasCollection()) path = path.extend(this.collection);
        else return path;
        if (this.hasEntry()) path = path.extend(this.entry);
        else return path;
        if (this.hasField()) path = path.extend(this.field);
        return path;
    }

    /**
     * Build a fully-qualified VID from a space's base pattern
     * by stripping the pattern wildcard and extending with the path
     * components of this DataPath.  Does not include {@link #db} —
     * the space pattern already accounts for the database context.
     */
    public fURI vid(final fURI spacePattern) {
        fURI result = spacePattern.retractPattern();
        if (this.hasCollection()) result = result.extend(this.collection);
        if (this.hasEntry()) result = result.extend(this.entry);
        if (this.hasField()) result = result.extend(this.field);
        return result;
    }

    public DataPath extendedDataPath() {
        return null == this.extension ? null : DataPath.of(this.extension);
    }

    // --- has* accessors ---

    public boolean hasDb() {
        return null != this.db;
    }

    public boolean hasCollection() {
        return null != this.collection;
    }

    public boolean hasEntry() {
        return null != this.entry;
    }

    public boolean hasField() {
        return null != this.field;
    }

    public boolean hasExtension() {
        return null != this.extension;
    }

    /**
     * The full field path from {@link #field()} through {@link #extension()},
     * joined with {@code .} for use in dot-notation field access (MongoDB, etc.).
     * Returns {@code null} when there is no field.
     */
    public String fieldPathStr() {
        if (null == this.field)
            return null;
        if (null == this.extension)
            return this.field;
        return this.field + "." + String.join(".", this.extension.segments());
    }

    // --- wildcard inspection ---

    public boolean dbIsWildcard() {
        return isWildcard(this.db);
    }

    public boolean collectionIsWildcard() {
        return isWildcard(this.collection) || isAllWildcard(this.db);
    }

    public boolean entryIsWildcard() {
        return isWildcard(this.entry) || isAllWildcard(this.db) || isAllWildcard(this.collection);
    }

    public boolean fieldIsWildcard() {
        return isWildcard(this.field) || isAllWildcard(this.db) || isAllWildcard(this.collection) || isAllWildcard(this.entry);
    }

    public boolean extensionIsWildcard() {
        return (null != this.extension && this.extension.hasPattern())
                || isAllWildcard(this.db) || isAllWildcard(this.collection) || isAllWildcard(this.entry) || isAllWildcard(this.field);
    }

    private static boolean isWildcard(final String segment) {
        return fURI.Singleton.ALL.name().equals(segment) || fURI.Singleton.WILD_ONE.name().equals(segment);
    }

    private static boolean isAllWildcard(final String segment) {
        return fURI.Singleton.ALL.name().equals(segment);
    }

    // --- extension navigation ---

    public static Stream<Obj> navigateWithin(final Stream<Obj> objects, final fURI extension, final boolean detached) {
        if (null == extension)
            return objects;
        return objects.flatMap(o -> {
            if (o.isSpace()) {
                return o.<Space>as().readStream(extension).map(io -> detached ? io.obj().vid(null) : io.obj().selfVID(io.furi()));
            } else if (o.isPoly()) {
                return o.<Poly<?, ?>>as().at(extension).stream();
            } else if (o.isRec())
                return Rec.Helper.rshiftRec(o.asRec(), extension.toUri()).stream();
            else if (o.isLst())
                return Lst.Helper.rshiftLst(o.asLst(), extension.toUri()).stream();
            else if (o.isUri())
                return Uri.Helper.rshiftUri(o.asUri(), extension.toUri()).stream();
            else if (o.isRel())
                return Rel.Helper.rshiftRel(o.asRel(), extension.toUri()).stream();
            else return Stream.empty();
        });
    }

    public Stream<Obj> navigateWithin(final Stream<Obj> objects, final boolean detached) {
        return navigateWithin(objects, this.extension, detached);
    }
}
