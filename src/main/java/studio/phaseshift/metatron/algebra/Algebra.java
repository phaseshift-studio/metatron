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

package studio.phaseshift.metatron.algebra;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Algebra — the STRUCTURAL THEORIES a type models: the law block {@code LawTable.typeLaws(Type)} answers, read as
 * Java instead of as a rec.
 *
 * <p>The block is a rec keyed by theory-instance name ({@code ring}, {@code add_group}, {@code semilattice}, …), each
 * value the instance's declaration — the roles it binds, {@code ring => [zero => 0, one => 1]}. mtron already has
 * that value, and what it lacks is a way to ask a question of it without rec-at ceremony
 * ({@code laws.at(uri("ring")).orElse(rec0()).at(uri("zero"))}). This class is that way: its methods say what the
 * caller means while the value underneath stays the rec the language, the type system and the serializer already
 * understand. An Algebra IS a rec — {@link #toRec()} answers {@code this} — so nothing is translated, wrapped or
 * cached, and the view can be handed to any door a rec can.
 *
 * <p>THE VIEW NEVER ALIASES THE STORE. {@link #of(Rec)} copies the law block's map, so mutating the view (or one of
 * its {@link #with} children) can never write back into the block a law table answered, which other types, other
 * threads and the table's own cache are reading. The class is read-only by design — no setter, no
 * {@code this.at(key, value, MUTABLE)}; a change is a copy.
 *
 * <p>NOTE: the structural laws are theory instances on {@code object::T}, not {@link Law process laws}; the two axes
 * are named in {@link Law}'s own documentation.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Algebra extends MRec {

    public Algebra(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The algebra a rec declares — the Java face of a type's law block, and the only door a caller needs: an Algebra
     * that comes back in is answered as itself, a rec that declares nothing is answered as {@link #empty()}, and a
     * real block is answered over a COPY so the view may be mutated freely without the source block noticing.
     */
    public static Algebra of(final Rec rec) {
        if (null == rec)
            return empty();
        if (rec instanceof Algebra algebra)
            return algebra;
        if (rec.isNoObj() || rec.isEmpty())
            return empty();
        return new Algebra(new LinkedHashMap<>(rec.jvm()), rec.tid(), rec.vid());
    }

    /**
     * The algebra of a type that models no theory — a rec with nothing in it, so a caller that has not been told which
     * type it is looking at still reads an honest answer rather than a null it must test for.
     */
    public static Algebra empty() {
        return new Algebra(new LinkedHashMap<>(), REC_TID, null);
    }

    /**
     * The theory instances this algebra declares, in the rec's own order — the spell order the declaration was written
     * in, which is what an author reading the block back expects, not a sort the block never promised.
     */
    public List<fURI> theories() {
        return this.jvm().keySet().stream().map(Obj::uriValue).toList();
    }

    /**
     * The declaration of one theory instance — the roles it binds, e.g. a ring's {@code zero} and {@code one} — or
     * rec0() when this algebra does not model that theory. The names are the block's keys, so a name the algebra does
     * not declare answers nothing rather than an empty declaration, which would read as "it models this and says
     * nothing about it".
     */
    public Rec theory(final fURI name) {
        if (null == name)
            return rec0();
        final Obj declaration = this.at(name);
        return declaration.isRec() ? declaration.asRec() : rec0();
    }

    /**
     * The same declaration, named as language text — the spelling a reader has in hand from a type definition or the
     * documentation, rather than the address the block happens to key it by. The text is turned into the address it
     * names ({@code uri(...).uriValue()}), never into the {@code uri} value that wraps one: a block is keyed by
     * addresses, which is why {@link #theories()} answers addresses too.
     */
    public Rec theory(final String name) {
        return null == name ? rec0() : this.theory(uri(name).uriValue());
    }

    /**
     * Whether this algebra declares the theory at all — the question a caller asks before reading a declaration, and
     * the one a rewrite asks before promising a law.
     */
    public boolean models(final fURI theory) {
        // THE KEY IS THE CLAIM: an absent theory answers {@code rec0()}, which is an EMPTY REC and not
        // {@code noobj}, so presence is asked of the block itself rather than of the answer's shape
        return null != theory && this.at(theory).isRec();
    }

    /**
     * The same algebra with one theory instance replaced (or added) — COPY-ON-WRITE, so the receiver is a value and not
     * a mutable table: a caller that extends an algebra never edits the one a type, a cache or another thread is
     * reading. The result carries the receiver's tid and vid, so a child of a law block is still a law block. An
     * unnamed theory is refused here rather than written under a null key, exactly as {@link #theory(fURI)} and
     * {@link #models(fURI)} answer nothing for one.
     */
    public Algebra with(final fURI theory, final Rec declaration) {
        if (null == theory)
            throw MTronException.of("an algebra theory must be named");
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(this.jvm());
        jvm.put(uri(theory), declaration);
        return new Algebra(jvm, this.tid(), this.vid());
    }

    /**
     * The mtron face — this algebra as the rec it already is. There is no translation here and there must never be
     * one: an {@code Algebra} introspected, serialized, selected into or walked by the language has to answer exactly
     * what the law block it views answers.
     */
    public Rec toRec() {
        return this;
    }
}
