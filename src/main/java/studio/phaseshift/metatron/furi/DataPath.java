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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;

/**
 * Structural decomposition of an {@link fURI} into a four-component
 * hierarchy plus an optional extension for deeper navigation.
 * <p>
 * <b>Component model</b>
 * <pre>
 *   /db/collection/entry/field/extension...
 *    ─┬─  ───┬───  ──┬──  ──┬──  ────┬────
 *     │      │       │      │        └─ segments 4+ (sub-field navigation)
 *     │      │       │      └─ segment 3 (field / column / property)
 *     │      │       └─ segment 2 (entry / document / row / element)
 *     │      └─ segment 1 (collection / table / vertex-label)
 *     └─ segment 0 (database / graph name)
 * </pre>
 * <p>
 * <b>Two factory methods</b> — Which one you use encodes whether the
 * database name lives inside the fURI or is known out-of-band:
 * <ul>
 *   <li>{@link #of(fURI)} — all four prefix components are positionally
 *       extracted from the fURI segments.  Segment 0 is {@code db}.</li>
 *   <li>{@link #ofSpaceRelative(fURI, String)} — the fURI is
 *       space-relative (no database name segment).  {@code db} is supplied
 *       explicitly by the caller, and segment 0 is {@code collection}.</li>
 * </ul>
 * Mixing the two will silently place data in the wrong component.
 * <p>
 * <b>Wildcard cascade</b> — The recursive wildcard {@code #} cascades to
 * all descendant components.  If {@code collection} is {@code #} then
 * {@code entry} and {@code field} are also considered wildcard, even when
 * those segments are absent from the fURI.  The single-segment wildcard
 * {@code +} affects only its own position.
 * <p>
 * <b>Space provider usage</b> — DataPath does structural decomposition
 * only; it never performs database queries, ID parsing, or write
 * operations.  The space provider uses the decomposed fields to decide
 * <em>what</em> to do and implements <em>how</em> with its native
 * database API.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public record DataPath(String db, String collection, String entry, String field, fURI extension) {

    /**
     * Decompose a fully-qualified fURI into a DataPath.
     * Segment 0 → {@code db}, segment 1 → {@code collection},
     * segment 2 → {@code entry}, segment 3 → {@code field},
     * segments 4+ → {@code extension}.
     */
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

    /**
     * Decompose a space-relative fURI (no database-name segment) into a
     * DataPath.  Segment 0 → {@code collection}, segment 1 → {@code entry},
     * segment 2 → {@code field}, segments 3+ → {@code extension}.
     * The {@code db} parameter is stored as-is.
     */
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

    /**
     * Reconstruct the space-prefix URI from the populated components,
     * stopping at the first {@code null} field.  The result identifies
     * the container to query (e.g. {@code mydb/users/abc}) but does not
     * include the extension.
     */
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

    /**
     * Recursively decomposes {@link #extension()} into a new DataPath
     * using {@link #of(fURI)}.  Returns {@code null} when there is no
     * extension, allowing chained descent for paths deeper than 4 segments.
     */
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
    //
    // The recursive wildcard '#' cascades to all descendant components.
    // The single-segment wildcard '+' only affects its own position.
    // For example, a path /mydb/# produces:
    //   dbIsWildcard() = false, but all others = true (cascade from '#').

    /**
     * True when this component is {@code #} or {@code +}.
     * Cascade from ancestor components is NOT checked here.
     */
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

    /**
     * Navigate into each object in {@code objects} using {@code extension}
     * as the sub-path.  Type-dispatches: {@link Space} objects are read via
     * {@code readStream(extension)}; {@link Poly} objects via
     * {@code at(extension)}; all others via the corresponding
     * {@code rshift*} helper.  When {@code detached} is {@code true},
     * returned objects have their VID stripped.
     */
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

    /** Convenience that uses this DataPath's {@link #extension}. */
    public Stream<Obj> navigateWithin(final Stream<Obj> objects, final boolean detached) {
        return navigateWithin(objects, this.extension, detached);
    }

    // =======================================================================
    // Structural-to-URI decomposition
    // =======================================================================

    /**
     * A leaf operation decomposed from a structural Rec/Lst pattern.
     * The URI targets the exact leaf position; the value is the operation
     * to apply there (a literal for SET, an instruction for ADD/MUL/etc.).
     */
    public record StructuralLeaf(fURI uri, Obj value) {}

    /**
     * Expand a structural {@link Rec} or {@link Lst} pattern into leaf-level
     * {@code (URI, value)} pairs.  Each leaf URI extends {@code base} with
     * the structural path through Rec keys and Lst indices.
     * <p>
     * Non-{@link Poly} values and {@link Inst} leaves are emitted as-is.
     * The {@code +} prefix ({@link studio.phaseshift.metatron.isa.m.mInstSet#PLUS_INST_TID})
     * wrapping a Rec/Lst is unwrapped so its fields are decomposed into
     * individual leaf operations.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     *   expandStructural(f("mongo:users/user4"),
     *     rec("[stats=>[[events=>[[score=>plus(345)]]]]]"))
     *     → [(f("mongo:users/user4/stats/0/events/0/score"), plus(345))]
     * }</pre>
     *
     * @param base the base URI to extend
     * @param obj  the structural pattern ({@link Rec} or {@link Lst})
     * @return stream of leaf URI–value pairs
     */
    public static Stream<StructuralLeaf> expandStructural(final fURI base, final Obj obj) {
        if (obj.isInst() && obj.asInst().tid().basePath().equals(PLUS_INST_TID)
                && obj.asInst().args().count() > 0) {
            // Unwrap +[rec] / +[lst] to decompose the inner structure as per-field SETs
            final Obj inner = obj.asInst().arg(0);
            if (inner.isRec() || inner.isLst())
                return expandStructural(base, inner);
            // +345 on a scalar stays as a leaf — metatron handles the computation
            return Stream.of(new StructuralLeaf(base, obj));
        }
        if (obj.isRec()) {
            return obj.asRec().elements()
                    .flatMap(rel -> {
                        final String segment = rel.first().isUri()
                                ? rel.first().uriValue().name()
                                : rel.first().toString();
                        return expandStructural(base.extend(segment), rel.second());
                    });
        }
        if (obj.isLst()) {
            final List<Obj> elems = obj.asLst().elements().toList();
            return IntStream.range(0, elems.size())
                    .mapToObj(i -> expandStructural(base.extend(String.valueOf(i)), elems.get(i)))
                    .flatMap(s -> s);
        }
        return Stream.of(new StructuralLeaf(base, obj));
    }
}
