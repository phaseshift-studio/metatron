package studio.phaseshift.metatron.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Tuple {

    protected final List elements;

    protected Tuple(final List elements) {
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

    public static class Pair<A, B> extends Tuple {

        private Pair(List elements) {
            super(elements);
        }

        public static <A, B> Pair<A, B> with(final A a, final B b) {
            final ArrayList list = new ArrayList();
            list.add(a);
            list.add(b);
            return new Pair<>(list);
        }

        public A getValue0() {
            return (A) this.elements.get(0);
        }

        public B getValue1() {
            return (B) this.elements.get(1);
        }
    }

    public static class Triplet<A, B, C> extends Pair<A, B> {

        private Triplet(List elements) {
            super(elements);
        }

        public static <A, B, C> Triplet<A, B, C> with(final A a, final B b, final C c) {
            final ArrayList list = new ArrayList();
            list.add(a);
            list.add(b);
            list.add(c);
            return new Triplet<>(list);
        }

        public C getValue2() {
            return (C) this.elements.get(2);
        }
    }

    public static class Quartet<A, B, C, D> extends Triplet<A, B, C> {

        private Quartet(final List elements) {
            super(elements);
        }

        public static <A, B, C, D> Quartet<A, B, C, D> with(final A a, final B b, final C c, final D d) {
            final ArrayList list = new ArrayList();
            list.add(a);
            list.add(b);
            list.add(c);
            list.add(d);
            return new Quartet<>(list);
        }

        public D getValue3() {
            return (D) this.elements.get(3);
        }
    }
}
