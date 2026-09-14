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
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A rec whose state is <b>only</b> its own map — and, when it is anchored (has a
 * {@link #vid()}), the space that map is stored in.
 *
 * <p>This is the base the widgets are built on, and it exists because
 * {@link JRec} offers three homes for state (a Java field, the local map, and the
 * space) with no rule about which wins; a widget built on it can hold the new
 * value and the old one at the same time, depending on which accessor a caller
 * happened to use. A {@code SpaceRec} has exactly two rules:
 *
 * <ul>
 *   <li><b>One read path</b> — {@link #read()} returns the anchored truth: the
 *   space's copy when there is a vid, the local map otherwise. {@link #get(Obj)}
 *   and its typed friends read through it and <em>normalize</em> what they find
 *   (a rec read may answer a key multi-valued), so no caller has to defend itself
 *   against the shape of a read.</li>
 *   <li><b>One write path</b> — {@link #put(Obj, Obj)} goes through
 *   {@code at(key, value, MUTABLE)}, the single policy that both installs the
 *   value in the rec and saves it to the space when the rec is anchored. A write
 *   can never land in one place and leave the other stale.</li>
 * </ul>
 *
 * <p>There is deliberately no reflection here: a subclass holds collaborators,
 * per-render scratch, and the identity of the rec it renders — never the rec's
 * data. Anything that should survive a re-hydration (a fresh instance built from
 * the same anchored rec) lives in the rec because that is the only thing that
 * survives it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class SpaceRec<T extends SpaceRec<T>> extends MRec {

    protected SpaceRec(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The anchored truth: the space's copy of this rec when it has a {@link #vid()},
     * the local map otherwise.  Read once per pass and pass the result down —
     * a render should not hit the space per field, per line, or per call.
     */
    protected Map<Obj, Obj> read() {
        if (null == this.vid()) return this.jvm();
        try {
            final Obj fresh = Router.global().read(this.vid());
            return fresh.isRec() ? fresh.jvm() : this.jvm();
        } catch (final Exception e) {
            return this.jvm();   // space unavailable (boot, headless, store gone)
        }
    }

    /**
     * The one write path: install {@code value} under {@code key} in this rec and,
     * when it is anchored, in the space.  Other fields are never clobbered.
     */
    protected void put(final Obj key, final Obj value) {
        this.at(key, value, MUTABLE);
    }

    /**
     * The same write path with an explicit policy — {@link Poly#MUTABLE} to install
     * a value (what {@link #put(Obj, Obj)} does), {@link Poly#IMMUTABLE} to keep a
     * clone.  Inst latching goes through here so every write in a widget looks the
     * same.
     */
    protected void put(final Obj key, final Obj value,
                       final java.util.function.BiFunction<studio.phaseshift.metatron.isa.m.type.Poly<?, ?>, Object, studio.phaseshift.metatron.isa.m.type.Poly<?, ?>> operation) {
        this.at(key, value, operation);
    }

    /**
     * The value under {@code key}: the rec's own map first, then a name-equivalent
     * key (a store round-trip need not preserve key identity), and null when the
     * key is absent.
     *
     * <p>The value is returned as it is stored — a key may legitimately hold a
     * <em>list</em> of values (a widget body is a list of lines).  The multi-value
     * surprise lives in {@code Rec.at(key)} (which walks paths and unions field,
     * method and map answers), not in a plain map read; unwrapping here would
     * silently drop all but the first line.
     */
    protected Obj get(final Map<Obj, Obj> fields, final Obj key) {
        if (null == fields) return null;
        final Obj direct = fields.get(key);
        if (null != direct && !direct.isNoObj()) return direct;
        for (final Map.Entry<Obj, Obj> entry : fields.entrySet())
            if (entry.getKey().isUri() && key.isUri()
                    && entry.getKey().uriValue().equals(key.uriValue())) {
                final Obj value = entry.getValue();
                return (null == value || value.isNoObj()) ? null : value;
            }
        return null;
    }

    protected Obj get(final Obj key) {
        return this.get(this.read(), key);
    }

    /** A {@code str} under {@code key}, or "" when absent. */
    protected String getStr(final Map<Obj, Obj> fields, final Obj key) {
        final Obj o = this.get(fields, key);
        return null != o && o.isStr() ? o.strValue() : "";
    }

    protected String getStr(final Obj key) {
        return this.getStr(this.read(), key);
    }

    /** A {@code bool} under {@code key}, or {@code fallback} when absent/not a bool. */
    protected boolean getBool(final Map<Obj, Obj> fields, final Obj key, final boolean fallback) {
        final Obj o = this.get(fields, key);
        return null != o && o.isBool() ? o.boolValue() : fallback;
    }

    /** An {@code int} under {@code key}, or {@code fallback} when absent/not an int. */
    protected int getInt(final Map<Obj, Obj> fields, final Obj key, final int fallback) {
        final Obj o = this.get(fields, key);
        return null != o && o.isInt() ? o.asInt().intValue().intValue() : fallback;
    }

    /**
     * Text lines under {@code key}, across the shapes a body legitimately has:
     *
     * <ul>
     *   <li>a {@code str::T} — one value — is split on newlines (a literal
     *   {@code \n} counts as a break); this is what a writer produces
     *   ({@code body => 'a\nb'}) and what the accumulate idiom grows
     *   ({@code body => +*a} concatenates);</li>
     *   <li>a multiplicity of strs, {@code str{*}::T} — many values — contributes
     *   one line per value;</li>
     *   <li>a {@code lst[str]::T} — ONE value that is a list — contributes one
     *   line per member; this is the shape Java-side construction produces.</li>
     * </ul>
     *
     * <p>Note the middle and last are different types, not spellings of each
     * other: {@code str{2}::T} is two strs, {@code lst[str]::T} is one list.  A
     * widget type that declares {@code body => str{*}} therefore rejects
     * {@code ['one','two']} on write, correctly.
     */
    protected List<String> getLines(final Map<Obj, Obj> fields, final Obj key) {
        final List<String> lines = new ArrayList<>();
        final Obj body = this.get(fields, key);
        if (null == body || body.isNoObj()) return lines;
        if (body.isStr())
            java.util.Arrays.asList(body.strValue().replace("\\n", "\n").split("\n", -1)).forEach(lines::add);
        else
            forEachValue(body, o -> {
                if (o.isStr()) lines.add(o.strValue());
            });
        return lines;
    }

    /**
     * Walk the values under a key: a {@code lst} contributes its members, anything
     * else contributes itself (one value) or its members (a multiplicity).
     *
     * <p>{@code stream()} alone is not enough, and this is the trap it sets: over a
     * {@code Lst} it yields the <em>list</em>, so a caller that meant to walk members
     * silently gets one value — a whole row read as a single cell, a body read as no
     * lines at all.  {@code getLines} and the table both need the same rule, so it
     * lives here once.
     */
    protected static void forEachValue(final Obj obj, final java.util.function.Consumer<Obj> consumer) {
        if (null == obj || obj.isNoObj()) return;
        if (obj.isLst()) obj.lstValue().forEach(consumer);
        else obj.stream().forEach(consumer);
    }

    protected List<String> getLines(final Obj key) {
        return this.getLines(this.read(), key);
    }
}
