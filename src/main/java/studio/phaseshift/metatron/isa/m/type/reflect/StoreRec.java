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

package studio.phaseshift.metatron.isa.m.type.reflect;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.autoToggle;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A rec whose MAP IS THE STORE — the rec an index uses when it is read far more often than it is written, and whose
 * contents are therefore not worth copying on the way in or checking on the way out.
 * <p>
 * Two rules, and everything else follows from them:
 * <ul>
 *   <li><b>one read</b> — {@link #at(Obj)} on a resolved single-segment uri is {@code jvm().get(key)}: no resolve, no
 *       fresh key, no copy, no type-check. That is the 80% of accesses, and it is the reason this class exists.</li>
 *   <li><b>one write</b> — a write takes the lock, copies the map, applies the change, and <b>swaps the reference</b>
 *       ({@code volatile}), then hands the new map to the owner's commit hook. Readers never block and never see a
 *       half-built table; they simply keep reading the old one until the swap. Slow, deliberate, and exactly as
 *       expensive as the table is large.</li>
 * </ul>
 * <p>
 * WHAT IT DELIBERATELY SKIPS, and why that is sound here rather than merely fast:
 * <ul>
 *   <li>{@link Rec.Helper#cleanMap} — the noobj sweep. A write of {@code noobj} is a {@code remove} instead, which is
 *       the same close by its proper name.</li>
 *   <li>{@code Obj.Helper.objCheckAndSave} — the whole-rec type check plus the incidental space write. The check is a
 *       registry read that MATERIALIZES the rec's type and tests every entry; on a large table it is a rebuild, not a
 *       check. Integrity moves to the writer: the owner validates what it puts in, once, at the swap.</li>
 *   <li>{@code tid.big()} on every {@code self} — a redirect lookup per field assignment, which for a routing table
 *       would be the table asking itself.</li>
 * </ul>
 * So a StoreRec is a component's private index that happens to be an obj: readable at its address like any other rec,
 * written only through the deliberate path, and never paying for machinery it does not use.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StoreRec extends MRec {

    /**
     * THE PUBLISHED MAP. Volatile because the write path swaps it wholesale: a reader either sees the map before the
     * swap or the map after it, and no reader ever observes a half-applied batch.
     */
    private volatile Map<Obj, Obj> store;

    /**
     * writers serialize here; readers never touch it
     */
    private final Object writeLock = new Object();

    /**
     * the owner's save — called ONCE per batch with the map that has already been published, so an index kept beside
     * this one (a reverse lookup, a hot-path copy) is rebuilt exactly when the table changes, never per entry
     */
    private final Consumer<Map<Obj, Obj>> commit;

    public StoreRec() {
        this(mutableMap(), REC_TID, null, null);
    }

    public StoreRec(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        this(jvm, tid, vid, null);
    }

    public StoreRec(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid,
                    final Consumer<Map<Obj, Obj>> commit) {
        super(jvm, tid, vid);
        this.store = jvm;
        this.commit = commit;
    }

    // ======================== one read ========================

    @Override
    public Map<Obj, Obj> jvm() {
        // SUPER RUNS FIRST, and MRec's constructor reads through this accessor before our field is assigned -- so the
        // view falls back to the parent's field until the store is installed. Without this a StoreRec cannot be
        // constructed at all: the superclass asks for the map it is in the middle of building.
        return null == this.store ? (Map<Obj, Obj>) super.jvm() : this.store;
    }

    /**
     * A single-segment, already-resolved uri is a direct map read — the whole point of the class. Every other shape
     * (a branch, a wildcard, {@code ..}, a q-carrying key, a non-uri key) goes to the ordinary rec read, which knows
     * what those mean.
     */
    @Override
    public <O extends Obj> O at(final Obj key) {
        if (key.isUri()) {
            final fURI k = key.uriValue();
            if (!k.hasQ() && !k.hasPattern() && !k.isBranch() && k.segmentLength() == 1) {
                final Obj found = this.store.get(uri(k.asNode()));
                // APPLY ON ACCESS: a stored value may be an `auto_` inst (the network's status, a place's derived
                // edges), and reading it IS invoking it. autoToggle is the slow path's exact form -- autoResolve with
                // the holding rec as context, then re-parent -- so the fast path cannot drift from it.
                if (null != found)
                    return (O) autoToggle(this, found, true);
            }
        }
        return Rec.Helper.atToggle(this, key, true);
    }

    @Override
    public <O extends Obj> O atDirect(final Obj key) {
        return Rec.Helper.atToggle(this, key, false);
    }

    // ======================== one write ========================

    /**
     * RAW SET: assign the map in place. NOT {@code self()} — that routes through {@link MRec#self}, which sweeps the
     * whole map with {@code cleanMap} and recomputes {@code tid.big()} (a redirect lookup, i.e. for a routing table the
     * table asking itself). A store's map is the store; there is nothing to launder and nothing to save.
     */
    @Override
    public Rec jvm(final Object jvm) {
        if (null != jvm)
            this.store = (Map<Obj, Obj>) jvm;
        return this;
    }

    /**
     * THE DELIBERATE WRITE, and the only mutation a reader can observe: lock, copy, apply, swap, commit. A single-
     * segment uri key is applied directly to the copy; anything else is refused rather than guessed at, because a
     * store's keys are names — a nested path or a wildcard in one is a caller who wanted a different structure.
     */
    @Override
    public Rec at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        if (Poly.IMMUTABLE == operation)
            return super.at(key, value, operation);
        if (!key.isUri() || key.uriValue().hasQ() || key.uriValue().hasPattern() || key.uriValue().isBranch()
                || key.uriValue().resolve().segmentLength() != 1)
            throw MTronException.of("a store rewrites single-segment names, not %s: rewrite the table wholesale instead", key);
        synchronized (this.writeLock) {
            final Map<Obj, Obj> next = new LinkedHashMap<>(this.store);
            final Obj name = uri(key.uriValue().resolve().asNode());
            if (value.isNoObj())
                next.remove(name);
            else
                next.put(name, value.parent(this));
            this.store = next;
            if (null != this.commit)
                this.commit.accept(next);
            return this;
        }
    }
}
