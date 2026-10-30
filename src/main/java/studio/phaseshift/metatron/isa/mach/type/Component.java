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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.furi.fURI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

/**
 * ONE OF THE THINGS A MACHINE IS MADE OF — memory, network, instset, compiler, processor (and the vocabulary a
 * component may add, such as a resolver) — and the one question every component answers about itself: WHICH MACHINE
 * it belongs to.
 * <p>
 * {@link #machine()} walks {@code parent()} up to the nearest {@link Machine}, seeding through {@link Machine#mach0()}
 * when the chain holds none (a component built before a machine exists, as on a bare test rig). That walk is why a
 * component carries no back-reference and why it is meaningful in a FRAME: the answer is the level the component
 * currently stands in, not the level that constructed it.
 * <p>
 * It is its own file because it is the shared root of the component family — {@code Memory}, {@code Network},
 * {@code InstSet}, {@code Compiler} and {@code Processor} all extend it, and {@link Component.Prefix} refines it with
 * the parental name walk. Nested in {@link Machine} it made every one of them read {@code Component}.
 */
public interface Component extends Rec {

    /**
     * THE MACHINE THIS COMPONENT BELONGS TO: the nearest {@link Machine} above it in the {@code parent()} chain, or
     * {@link Machine#mach0()} when the chain names none.
     */
    default Machine machine() {
        Obj mach = this.parent();
        while (!mach.isNoObj() && !(mach instanceof Machine))
            mach = mach.parent();
        return mach instanceof Machine ? mach.as() : Machine.mach0();
    }

    /**
     * A COMPONENT THAT SPEAKS A NAMED VOCABULARY WITH PARENTAL SHADOWING — the one algorithm behind the network's peer
     * names and the ISA's scheme aliases. A name binds to a value at the level that declares it; resolution walks the
     * level chain NEAREST FIRST; a level INHERITS a name it does not declare and SHADOWS the one it does; a popped
     * frame takes only its own rows, and nothing is ever copied upward or downward.
     * <p>
     * WHAT DIFFERS between components is only the SLOT a name occupies and therefore its value — a network name is an
     * authority ({@code //ginger} => a full origin), an ISA name a scheme alias ({@code math} => a vid) — never the
     * walk. Implementing the four hooks inherits the view, and
     * {@code AbstractMetatronTest#testParentalChildPrefix(Component)} runs the contract against any
     * implementation: a new prefixed component joins by implementing this interface and adding one test line.
     */
    interface Prefix extends Component {

        /**
         * THIS LEVEL's own rows — storage, the thing registration rewrites.
         */
        Rec prefixes();

        /**
         * THE LEVEL ABOVE this one, same kind of component, or null at the top (where the walk stops).
         */
        Prefix previousLevel();

        /**
         * The SAME KIND of component on another machine — how a tool (or the contract test) looks a level in or out.
         */
        Prefix on(Machine machine);

        /**
         * DECLARE a row HERE, under this component's own policy: the ISA refuses a same-level retarget (an import that
         * silently moved would re-mean every existing spelling), while the network permits one (a denotation restated).
         * The policy is the component's; the act is the same.
         */
        Obj registerPrefix(fURI name, fURI value);

        /**
         * WITHDRAW a row HERE — the deletion side of {@link #registerPrefix(fURI, fURI)}.
         */
        Obj unregisterPrefix(fURI name);

        /**
         * RESOLVE a spelling against this component's names — {@code //ginger/a} to the service's address,
         * {@code math:cos} to a vid — the nearest declaration winning, exactly as {@link #prefixes()} is walked for a
         * lookup. A spelling this component does not speak is answered unchanged (as its coefficient-one form), so a
         * caller may align unconditionally. This is the one call every hosted or aliased address is aligned through
         * ({@link Machine.Helper#align}), which is why the contract owns it rather than each level.
         */
        fURI alignPrefix(fURI id);

        /**
         * THE ALGORITHM — the parental walk, one implementation for every prefixed component. Deliberately NOT on the
         * interface: a user declares, withdraws and lists; the walk is a question ABOUT a component, not a capability
         * of it, so it lives here (the same reason every interface has its {@code X.Helper}).
         */
        final class Helper {

            private Helper() {
                // static only
            }

            /**
             * THE VIEW: every row this level SEES, the nearest declaration winning — the table a door shows and a
             * reader enumerates, never the storage. A SNAPSHOT, not atomic across levels (registration is serialized
             * per level); a write of it back must drop the rows it merely inherits or it would copy them in.
             */
            public static Rec effectivePrefixes(final Prefix of) {
                final Map<Obj, Obj> merged = new LinkedHashMap<>();
                levels(of).forEach(rows -> rows.jvm().forEach(merged::putIfAbsent));
                return rec(merged);
            }

            /**
             * THE LOOKUP: the nearest declaration of {@code label}, else noobj. ALLOCATION-FREE, because every hosted
             * or aliased read and write is aligned through it.
             */
            public static Obj effectivePrefix(final Prefix of, final fURI label) {
                return levels(of)
                        .map(rows -> (Obj) rows.at(label))
                        .filter(Obj::isUri)
                        .findFirst()
                        .orElse(noobj());
            }

            /**
             * WHAT {@code label} MEANS <b>ABOVE THIS LEVEL</b> — the question a table WRITE asks ("is this row
             * redundant?") and a read never does.
             */
            public static Obj inheritedPrefix(final Prefix of, final fURI label) {
                final Prefix above = of.previousLevel();
                return null == above ? noobj() : effectivePrefix(above, label);
            }

            /**
             * This level and every level above it, nearest first.
             */
            private static Stream<Rec> levels(final Prefix of) {
                return Stream.<Prefix>iterate(of, Prefix::previousLevel)
                        .takeWhile(Objects::nonNull)
                        .map(Prefix::prefixes);
            }
        }
    }
}
