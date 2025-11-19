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
 */

package studio.phaseshift.metatron.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public abstract class Tuple implements Iterable<Object> {

    protected final List<Object> elements;

    protected Tuple(final List<Object> elements) {
        this.elements = elements;
    }

    public boolean equals(final Object other) {
        if (!(other instanceof Tuple) || this.elements.size() != ((Tuple) other).elements.size())
            return false;
        for (int i = 0; i < this.elements.size(); i++) {
            if (!Objects.equals(this.elements.get(i), ((Tuple) other).elements.get(i)))
                return false;
        }
        return true;
    }

    public int hashCode() {
        return Objects.hash(this.elements);
    }

    public String toString() {
        return this.elements.toString();
    }

    public Iterator<Object> iterator() {
        return this.elements.iterator();
    }

    public <T> T project(final int i) {
        if (i >= this.elements.size())
            throw MTronException.of("projection larger than tuple: %d < %d", this.elements.size(), i);
        return (T) this.elements.get(i);
    }

    public abstract Tuple inject(final int i, final Object object);


    public static class Pair<A, B> extends Tuple {

        private Pair(List<Object> elements) {
            super(elements);
        }

        public static <A, B> Pair<A, B> with(final A a, final B b) {
            final ArrayList<Object> list = new ArrayList<>();
            list.add(a);
            list.add(b);
            return new Pair<>(list);
        }

        public A get0() {
            return (A) this.elements.get(0);
        }

        public B get1() {
            return (B) this.elements.get(1);
        }

        public Pair<A, B> inject(final int i, final Object object) {
            final Pair<A, B> pair = new Pair<>(new ArrayList<>(this.elements));
            pair.elements.set(i, object);
            return pair;
        }
    }

    public static class Triplet<A, B, C> extends Pair<A, B> {

        private Triplet(List<Object> elements) {
            super(elements);
        }

        public static <A, B, C> Triplet<A, B, C> with(final A a, final B b, final C c) {
            final ArrayList<Object> list = new ArrayList<>();
            list.add(a);
            list.add(b);
            list.add(c);
            return new Triplet<>(list);
        }

        public C get2() {
            return (C) this.elements.get(2);
        }

        public Triplet<A, B, C> inject(final int i, final Object object) {
            final Triplet<A, B, C> triplet = new Triplet<>(new ArrayList<>(this.elements));
            triplet.elements.set(i, object);
            return triplet;
        }
    }

    public static class Quartet<A, B, C, D> extends Triplet<A, B, C> {

        private Quartet(final List<Object> elements) {
            super(elements);
        }

        public static <A, B, C, D> Quartet<A, B, C, D> with(final A a, final B b, final C c, final D d) {
            final ArrayList<Object> list = new ArrayList<>();
            list.add(a);
            list.add(b);
            list.add(c);
            list.add(d);
            return new Quartet<>(list);
        }

        public D get3() {
            return (D) this.elements.get(3);
        }

        public Quartet<A, B, C, D> inject(final int i, final Object object) {
            final Quartet<A, B, C, D> quartet = new Quartet<>(new ArrayList<>(this.elements));
            quartet.elements.set(i, object);
            return quartet;
        }
    }

    public static class Quintet<A, B, C, D,E> extends Quartet<A, B, C,D> {

        private Quintet(final List<Object> elements) {
            super(elements);
        }

        public static <A, B, C, D,E> Quintet<A, B, C, D,E> with(final A a, final B b, final C c, final D d, final E e) {
            final ArrayList<Object> list = new ArrayList<>();
            list.add(a);
            list.add(b);
            list.add(c);
            list.add(d);
            list.add(e);
            return new Quintet<>(list);
        }

        public E get4() {
            return (E) this.elements.get(4);
        }

        public Quintet<A, B, C, D,E> inject(final int i, final Object object) {
            final Quintet<A, B, C, D,E> quintet = new Quintet<>(new ArrayList<>(this.elements));
            quintet.elements.set(i, object);
            return quintet;
        }
    }
}
