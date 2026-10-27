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

package studio.phaseshift.metatron.isa.mach.type.memory;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.ObjectMap;
import studio.phaseshift.metatron.isa.m.type.reflect.StoreRec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE NAME INDEX AS AN OBJ — a <b>projection</b> of memory's routing table, exposed at {@code <memory>/redirect}.
 * <p>
 * It is <b>not</b> the table the resolver reads. The truth is {@code BasicMemory}'s forward relation in RAW {@code fURI}
 * keys, because {@code big()} is asked from inside {@code MObj}'s constructor ({@code tid.big()}): a table keyed by
 * {@code Obj} builds an {@code MUri} to look itself up, that constructor asks {@code big()} again, and the lookup
 * overflows the stack in the shape of a recursion. So the hot path never reaches this rec.
 * <p>
 * What reaches it is a language read ({@code *</memory/redirect>}), which is cold by definition. The {@code Obj}-keyed
 * rows are built <b>then</b> — {@link #project()}, once per table version — and handed to the {@link StoreRec} store,
 * which is what makes {@code at(uri("count"))} a plain map get afterwards. Building it here, on a dereference, is also
 * what keeps a rec out of the machine's own bindings: the index carries <b>no vid</b>, so nothing it constructs can be
 * saved back into the address it names.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
final class NameIndexRec extends StoreRec {

    /**
     * THE TRUTH this rec projects: {@code name => the paths it denotes}, raw keys. Read only when a dereference needs
     * the {@code Obj}-keyed rows; never on the resolver's path.
     */
    private final ObjectMap<fURI, Set<fURI>> forward;

    /**
     * THE TABLE'S VERSION, so the projection is rebuilt exactly when what it projects has changed — never on a read
     * that would see the same rows, and never stale after a registration.
     */
    private final LongSupplier version;

    /**
     * the version the rows in the store were built from; {@code -1} before the first projection
     */
    private volatile long projected = -1L;

    NameIndexRec(final ObjectMap<fURI, Set<fURI>> forward, final LongSupplier version) {
        super(mutableMap(), REC_TID, null);
        this.forward = forward;
        this.version = version;
    }

    // ======================== the cold build ========================

    /**
     * BUILD THE ROWS THE TABLE HANDED US. Called on the way into any read, and a no-op unless the table moved since the
     * last build: the fast path is a volatile comparison, the slow path copies the forward relation into {@code Obj}
     * keys and swaps the store.
     */
    private void project() {
        // the superclass constructor reads through jvm() before our fields are assigned: nothing to project yet
        if (null == this.forward)
            return;
        if (this.projected == this.version.getAsLong())
            return;
        synchronized (this) {
            final long current = this.version.getAsLong();
            if (this.projected == current)
                return;
            final Map<Obj, Obj> rows = new LinkedHashMap<>();
            this.forward.rawMap().forEach((name, paths) -> {
                if (null == paths || paths.isEmpty())
                    return;
                rows.put(uri(name.toString()), objs(paths.stream().map(p -> (Obj) uri(p.toString())).toArray(Obj[]::new)));
            });
            this.jvm(rows);
            this.projected = current;
        }
    }

    // ======================== every read projects first ========================

    @Override
    public <O extends Obj> O at(final Obj key) {
        this.project();
        return super.at(key);
    }

    @Override
    public <O extends Obj> O atDirect(final Obj key) {
        this.project();
        return super.atDirect(key);
    }

    /**
     * the store accessor, projected first: {@code table.jvm().keySet()} is how a caller enumerates the names
     */
    @Override
    public Map<Obj, Obj> jvm() {
        this.project();
        return super.jvm();
    }
}
