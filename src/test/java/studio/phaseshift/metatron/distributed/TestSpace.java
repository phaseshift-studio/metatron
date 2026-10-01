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

package studio.phaseshift.metatron.distributed;

import studio.phaseshift.metatron.isa.m.type.Obj;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A space definition, declared once, that every peer in the cluster mounts — modulated to that peer's address.
 * <p>
 * The value is ordinary mtron, so a space is described exactly as it would be in a boot file, with {@code $n}
 * standing for the n-th peer's uri prefix. That is what lets one string describe a whole cluster: the same
 * definition is evaluated on every peer, and {@code $1} names peer 1 everywhere it runs.
 * <pre>{@code
 * @TestSpace("memspace::[pattern => /shared/#]@/sys/space/shared")
 * @TestSpace("memspace::[pattern => $root/#, route => [$self/ => <>]]@/sys/space/each")
 * }</pre>
 * Placeholders, resolved per peer by {@link PeerCluster#modulate}:
 * <ul>
 *   <li>{@code $n} — the n-th peer's prefix ({@code <ws://localhost:43611/n>}); a trailing path folds inside
 *       the literal, so {@code $1/data} is {@code <ws://localhost:43611/n/data>}</li>
 *   <li>{@code $self} — this peer's own prefix; {@code $index}, {@code $port}, {@code $root} — its particulars</li>
 *   <li>{@code $peers} — every peer's prefix, comma-separated, for building a peer rec or lst</li>
 * </ul>
 * Spaces have to exist before the data living in them is addressed, so this is a <em>class-level</em>
 * annotation, read once in {@code @BeforeAll} and handed to {@link Helper#attach}. For data rather than
 * structure, use the existing {@code @TestData}, whose values accept {@code $n} too.
 * <p>
 * The work lives on {@link Helper} because an annotation type cannot declare a static method of its own — only
 * nested types, which is what a Java annotation offers in place of a companion object.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestSpace {

    /**
     * The space definitions, as mtron source, evaluated on every peer with {@code $} placeholders resolved for
     * that peer.
     *
     * @return one or more space definitions
     */
    String[] value();

    /**
     * The operations this annotation needs but cannot carry itself.
     */
    class Helper {

        private Helper() {
            // static only
        }

        /**
         * Collect the {@link TestSpace} definitions declared on {@code type} and its supertypes,
         * supertype-first so a subclass appends to — rather than replaces — what a parent declared.
         *
         * @param type the test class
         * @return the definitions, ready for {@link #attach}
         */
        public static String[] of(final Class<?> type) {
            final List<String> spaces = new ArrayList<>();
            for (Class<?> current = type; null != current && !Object.class.equals(current); current = current.getSuperclass()) {
                final TestSpace declared = current.getAnnotation(TestSpace.class);
                if (null != declared)
                    Collections.addAll(spaces, declared.value());
            }
            Collections.reverse(spaces);
            return spaces.toArray(String[]::new);
        }

        /**
         * Attach space definition(s) to a set of peers: each definition is modulated to a peer — so {@code $n},
         * {@code $self}, {@code $index}, {@code $port} and {@code $root} all resolve from <em>that</em> peer's
         * point of view — and then evaluated there. One definition, N machines.
         * <p>
         * It takes the peers rather than a cluster so it can be applied to any subset — one peer, or a
         * failure-injected half — and so it reads as what it is: a declaration pushed at whoever is in the list.
         * A definition that fails on a peer throws with that peer's trace, because a space that quietly did not
         * attach is a test that will fail later for the wrong reason.
         */
        public static void attach(final List<Peer> peers, final String... definitions) {
            for (final Peer peer : peers)
                for (final String definition : definitions) {
                    // `.vid()` is load-bearing, not cosmetic. A space is not a wire payload: asking a peer for
                    // the space itself gets NO answer at all — serializing it fails on the peer and the frame is
                    // dropped, which surfaces here as nothing more informative than a reply timeout. Asking for
                    // its vid returns a uri, and that the uri came back is the proof the mount happened.
                    final Obj vid = peer.evaluate(definition + ".vid()");
                    if (vid.isFail() || vid.isCaughtFail() || vid.isNoObj())
                        throw new IllegalStateException("%s failed to attach %s:%n%s%n%s"
                                .formatted(peer, definition, vid, peer.trace()));
                }
        }
    }
}
