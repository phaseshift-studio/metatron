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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Base class for {@link DataPath}-backed spaces (document stores, SQL tables,
 * graph stores, …) that decompose a URI into {@code collection/entry/field}
 * segments and back that structured model with a flat key-value fallback
 * namespace (the {@code kv_store} collection/table, mirroring tbleSpace).
 *
 * <p>Two shared behaviors are provided:
 * <ul>
 *   <li><b>Deduced flat routing</b> ({@link #isFlatWrite}) — a write routes to
 *       the flat namespace when its first segment is a reserved flat name, or
 *       when its collection is unknown and the value is non-Rec (a mono or
 *       list, which has no document/row shape).</li>
 *   <li><b>Migration</b> ({@link #migrateFlatToStructured}) — when a Rec write
 *       creates a previously-unknown collection/table, flat entries parked under
 *       that prefix are promoted into the new structure via standard writes.  On
 *       a schema violation the offending entry is left in the flat store and the
 *       whole migration throws, so the user can repair the data manually.</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractDataPathSpace<SJVM> extends AbstractSpace<SJVM> {

    protected AbstractDataPathSpace(final SJVM sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
    }

    /**
     * A flat key-value entry: the full relative path as the key, the value
     * wrapped for round-trip fidelity.
     */
    public record FlatEntry(String key, Obj value) {
    }

    // =======================================================================
    // Storage-specific hooks (each concrete space implements)
    // =======================================================================

    /**
     * Whether the given first path segment is the reserved flat-namespace name
     * (e.g. {@code kv_store}, or tbleSpace's {@code kv}/{@code msg}).
     */
    protected abstract boolean isReservedFlatName(final String firstSegment);

    /**
     * Whether a space-relative path names a collection/table managed by this
     * space's schema (discovered or tracked).  Unknown collections fall back to
     * the flat namespace.
     */
    protected abstract boolean isStructuredCollection(final fURI relativePath);

    /**
     * Write a value into the flat namespace keyed by the full relative path.
     */
    protected abstract void writeFlat(final fURI relativePath, final Obj obj);

    /**
     * Read from the flat namespace (exact key, or wildcard scan + poly unroll).
     * {@code relativePath} is the space-relative path (may carry wildcards);
     * {@code pattern} is the full external pattern for matching/unrolling.
     */
    protected abstract Iterator<IdObj> readFlat(final fURI relativePath, final fURI pattern);

    /**
     * Write a value into the structured store at the given relative path.  A
     * standard write that enforces the collection/table schema; throws on a
     * schema violation (so the migration can preserve the un-migratable entry).
     */
    protected abstract void writeStructured(final fURI relativePath, final Obj obj);

    /**
     * Scan the flat namespace for entries whose key starts with
     * {@code collectionPrefix/}, returning their full key and value.
     */
    protected abstract Stream<FlatEntry> scanFlat(final String collectionPrefix);

    /**
     * Remove a flat entry by its full key.
     */
    protected abstract void removeFlat(final String fullKey);

    // =======================================================================
    // Shared behaviors
    // =======================================================================

    /**
     * Whether a read of {@code relativePath} belongs in the flat namespace:
     * reserved first segment, or an unknown collection.
     */
    protected boolean isFlatRead(final fURI relativePath) {
        final List<String> segs = relativePath.segments();
        return (segs != null && !segs.isEmpty() && this.isReservedFlatName(segs.getFirst()))
                || !this.isStructuredCollection(relativePath);
    }

    /**
     * Whether a write to {@code relativePath} belongs in the flat namespace:
     * reserved first segment, or an unknown collection with a non-Rec value.
     */
    protected boolean isFlatWrite(final fURI relativePath, final Obj obj) {
        final List<String> segs = relativePath.segments();
        if (segs != null && !segs.isEmpty() && this.isReservedFlatName(segs.getFirst()))
            return true;
        // Deduced flat requires 2+ segments: a 1-segment path is the collection
        // root (an auto-generated document/row id), not a flat key.
        return (segs != null && segs.size() >= 2)
                && !this.isStructuredCollection(relativePath) && !obj.isRec();
    }

    /**
     * Promote flat entries parked under {@code collectionName/} into a newly
     * created collection/table.  Each entry is written via
     * {@link #writeStructured} (a standard, schema-enforcing write).  A schema
     * violation leaves that entry in the flat store and records the failure;
     * after all entries are attempted, a single {@link MTronException} is thrown
     * listing every failure so the user can repair the data manually.
     */
    protected void migrateFlatToStructured(final String collectionName) {
        final List<String> failures = new ArrayList<>();
        this.scanFlat(collectionName).forEach(entry -> {
            final String fullKey = entry.key();
            final String rest = fullKey.substring(collectionName.length() + 1);
            if (rest.isEmpty())
                return; // 1-segment key has no document/row home
            try {
                this.writeStructured(f(collectionName + "/" + rest), entry.value());
                this.removeFlat(fullKey);
            } catch (final MTronException e) {
                failures.add(fullKey + ": " + e.getMessage());
                LOG.error("could not migrate flat entry {{b}}%s{{X}} into {{b}}%s{{X}} — kept in kv_store: %s",
                        fullKey, collectionName, e.getMessage());
            }
        });
        if (!failures.isEmpty())
            throw MTronException.of(
                    "could not migrate %d flat entry(s) into collection %s (kept in kv_store for manual repair): %s",
                    failures.size(), collectionName, failures);
    }

    /**
     * The flat namespace accepts any Obj at its root, so the document/row-level
     * Rec requirement does not apply to flat-destined writes.
     */
    @Override
    protected boolean enforceRootConstraint(final fURI vid, final Obj obj) {
        final fURI relative = Space.Helper.routeFromSpace(vid, this.routes());
        if (this.isFlatWrite(relative, obj))
            return false;
        return super.enforceRootConstraint(vid, obj);
    }
}
