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

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class IteratorUtil {
    private IteratorUtil() {
    }

    public static <S> Iterator<S> of() {
        return Collections.emptyIterator();
    }

    public static <S> Iterator<S> of(final S a) {
        return List.of(a).iterator();
    }

    public static <S> Iterator<S> of(final S a, S b) {
        return List.of(a, b).iterator();
    }

    public static <S> S index(final Iterator<S> iterator, final int index, final S noneDefault) {
        int i = 0;
        while (iterator.hasNext()) {
            S s = iterator.next();
            if (index == i++)
                return s;
        }
        return noneDefault;
    }

    public static <S extends Collection<T>, T> S fill(final Iterator<T> iterator, final S collection) {
        while (iterator.hasNext()) {
            collection.add(iterator.next());
        }

        CloseableIterator.closeIterator(iterator);
        return collection;
    }

    public static void iterate(final Iterator iterator) {
        while (iterator.hasNext()) {
            iterator.next();
        }

        CloseableIterator.closeIterator(iterator);
    }

    public static long count(final Iterator iterator) {
        long ix;
        for (ix = 0L; iterator.hasNext(); ++ix) {
            iterator.next();
        }

        CloseableIterator.closeIterator(iterator);
        return ix;
    }

    public static final long count(final Iterable iterable) {
        return count(iterable.iterator());
    }

    public static <S> List<S> list(final Iterator<S> iterator) {
        return (List) fill(iterator, new ArrayList());
    }

    public static <S> List<S> list(final Iterator<S> iterator, final Comparator comparator) {
        List<S> l = list(iterator);
        Collections.sort(l, comparator);
        return l;
    }

    public static <S> Set<S> set(final Iterator<S> iterator) {
        return (Set) fill(iterator, new HashSet());
    }

    public static <S> Iterator<S> limit(final Iterator<S> iterator, final int limit) {
        return new CloseableIterator<S>() {
            private int count = 0;

            public boolean hasNext() {
                return iterator.hasNext() && this.count < limit;
            }

            public void remove() {
                iterator.remove();
            }

            public S next() {
                if (this.count++ >= limit) {
                    throw FastNoSuchElementException.instance();


                } else {
                    return (S) iterator.next();
                }
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }
        };
    }

    public static <T> boolean allMatch(final Iterator<T> iterator, final Predicate<T> predicate) {
        try {
            while (true) {
                if (iterator.hasNext()) {
                    if (predicate.test(iterator.next())) {
                        continue;
                    }

                    boolean var6 = false;
                    return var6;
                }

                boolean var2 = true;
                return var2;
            }
        } finally {
            CloseableIterator.closeIterator(iterator);
        }
    }

    public static <T> boolean anyMatch(final Iterator<T> iterator, final Predicate<T> predicate) {
        try {
            while (true) {
                if (iterator.hasNext()) {
                    if (!predicate.test(iterator.next())) {
                        continue;
                    }

                    boolean var6 = true;
                    return var6;
                }

                boolean var2 = false;
                return var2;
            }
        } finally {
            CloseableIterator.closeIterator(iterator);
        }
    }

    public static <T> boolean noneMatch(final Iterator<T> iterator, final Predicate<T> predicate) {
        try {
            while (true) {
                if (iterator.hasNext()) {
                    if (!predicate.test(iterator.next())) {
                        continue;
                    }

                    boolean var6 = false;
                    return var6;
                }

                boolean var2 = true;
                return var2;
            }
        } finally {
            CloseableIterator.closeIterator(iterator);
        }
    }

    public static <T> Optional<T> findFirst(final Iterator<T> iterator) {
        Optional var1;
        try {
            if (!iterator.hasNext()) {
                var1 = Optional.empty();
                return var1;
            }

            var1 = Optional.ofNullable(iterator.next());
        } finally {
            CloseableIterator.closeIterator(iterator);
        }

        return var1;
    }

    public static <K, S> Map<K, S> collectMap(final Iterator<S> iterator, final Function<S, K> key) {
        return collectMap(iterator, key, Function.identity());
    }

    public static <K, S, V> Map<K, V> collectMap(final Iterator<S> iterator, final Function<S, K> key, final Function<S, V> value) {
        Map<K, V> map = new HashMap();

        while (iterator.hasNext()) {
            S obj = (S) iterator.next();
            map.put(key.apply(obj), value.apply(obj));
        }

        CloseableIterator.closeIterator(iterator);
        return map;
    }

    public static <K, S> Map<K, List<S>> groupBy(final Iterator<S> iterator, final Function<S, K> groupBy) {
        Map<K, List<S>> map = new HashMap();

        while (iterator.hasNext()) {
            S obj = (S) iterator.next();
            ((List) map.computeIfAbsent(groupBy.apply(obj), (k) -> new ArrayList())).add(obj);
        }

        CloseableIterator.closeIterator(iterator);
        return map;
    }

    public static <S> S reduce(final Iterator<S> iterator, final S identity, final BinaryOperator<S> accumulator) {
        S result;
        for (result = identity; iterator.hasNext(); result = (S) accumulator.apply(result, iterator.next())) {
        }

        CloseableIterator.closeIterator(iterator);
        return result;
    }

    public static <S> S reduce(final Iterable<S> iterable, final S identity, final BinaryOperator<S> accumulator) {
        return (S) reduce(iterable.iterator(), identity, accumulator);
    }

    public static <S, E> E reduce(final Iterator<S> iterator, final E identity, final BiFunction<E, S, E> accumulator) {
        E result;
        for (result = identity; iterator.hasNext(); result = (E) accumulator.apply(result, iterator.next())) {
        }

        CloseableIterator.closeIterator(iterator);
        return result;
    }

    public static <S, E> E reduce(final Iterable<S> iterable, final E identity, final BiFunction<E, S, E> accumulator) {
        return (E) reduce(iterable.iterator(), identity, accumulator);
    }

    public static <S> Iterator<S> consume(final Iterator<S> iterator, final Consumer<S> consumer) {
        return new CloseableIterator<S>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public void remove() {
                iterator.remove();
            }

            public S next() {
                S s = (S) iterator.next();
                consumer.accept(s);
                return s;
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }
        };
    }

    public static <S> Iterable<S> consume(final Iterable<S> iterable, final Consumer<S> consumer) {
        return () -> consume(iterable.iterator(), consumer);
    }

    public static <S, E> Iterator<E> map(final Iterator<S> iterator, final Function<S, E> function) {
        return new CloseableIterator<E>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public void remove() {
                iterator.remove();
            }

            public E next() {
                return (E) function.apply(iterator.next());
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }
        };
    }

    public static <S, E> Iterable<E> map(final Iterable<S> iterable, final Function<S, E> function) {
        return () -> map(iterable.iterator(), function);
    }

    /*public static <S, E> Iterator<E> cast(final Iterator<S> iterator) {
        return map(iterator, (s) -> s);
    }*/

    public static <S> Iterator<S> peek(final Iterator<S> iterator, final Consumer<S> function) {
        return new CloseableIterator<S>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public void remove() {
                iterator.remove();
            }

            public S next() {
                S next = (S) iterator.next();
                function.accept(next);
                return next;
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }
        };
    }

    public static <S> Iterable<S> peek(final Iterable<S> iterable, final Consumer<S> function) {
        return () -> peek(iterable.iterator(), function);
    }

    public static <S> Iterator<S> filter(final Iterator<S> iterator, final Predicate<S> predicate) {
        return new CloseableIterator<S>() {
            S nextResult = null;

            public boolean hasNext() {
                if (null != this.nextResult) {
                    return true;
                } else {
                    this.advance();
                    return null != this.nextResult;
                }
            }

            public void remove() {
                iterator.remove();
            }

            public S next() {
                Object var1;
                try {
                    if (null == this.nextResult) {
                        this.advance();
                        if (null != this.nextResult) {
                            var1 = this.nextResult;
                            return (S) var1;
                        }

                        throw FastNoSuchElementException.instance();
                    }

                    var1 = this.nextResult;
                } finally {
                    this.nextResult = (S) null;
                }

                return (S) var1;
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }

            private void advance() {
                this.nextResult = (S) null;

                while (iterator.hasNext()) {
                    S s = (S) iterator.next();
                    if (predicate.test(s)) {
                        this.nextResult = s;
                        return;
                    }
                }

            }
        };
    }

    public static <S> Iterable<S> filter(final Iterable<S> iterable, final Predicate<S> predicate) {
        return () -> filter(iterable.iterator(), predicate);
    }

    public static <S, E> Iterator<E> flatMap(final Iterator<S> iterator, final Function<S, Iterator<E>> function) {
        return new CloseableIterator<E>() {
            private Iterator<E> currentIterator = Collections.emptyIterator();

            public boolean hasNext() {
                if (this.currentIterator.hasNext()) {
                    return true;
                } else {
                    while (iterator.hasNext()) {
                        this.currentIterator = (Iterator) function.apply(iterator.next());
                        if (this.currentIterator.hasNext()) {
                            return true;
                        }
                    }

                    return false;
                }
            }

            public void remove() {
                iterator.remove();
            }

            public E next() {
                if (this.hasNext()) {
                    return (E) this.currentIterator.next();
                } else {
                    throw FastNoSuchElementException.instance();
                }
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }
        };
    }

    /*public static <S> Iterator<S> concat(final Iterator<S>... iterators) {
        MultiIterator<S> iterator = new MultiIterator();

        for (Iterator<S> itty : iterators) {
            iterator.addIterator(itty);
        }

        return iterator;
    }*/

    public static Iterator asIterator(final Object o) {
        Iterator itty;
        if (o instanceof Iterable) {
            itty = ((Iterable) o).iterator();
        } else if (o instanceof Iterator) {
            itty = (Iterator) o;
        } else if (o instanceof Object[]) {
            itty = Stream.of((Object[]) o).iterator();
        } else if (o != null && o.getClass().isArray()) {
            itty = new org.apache.commons.collections.iterators.ArrayIterator(o);
        } else if (o instanceof Stream) {
            itty = ((Stream) o).iterator();
        } else if (o instanceof Map) {
            itty = ((Map) o).entrySet().iterator();
        } else if (o instanceof Throwable) {
            itty = of(((Throwable) o).getMessage());
        } else {
            itty = of(o);
        }

        return itty;
    }

    public static List asList(final Object o) {
        return list(asIterator(o));
    }

    public static Set asSet(final Object o) {
        return set(asIterator(o));
    }

    public static <T> Stream<T> stream(final Iterator<T> iterator) {
        return (Stream) StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 1088), false).onClose(() -> CloseableIterator.closeIterator(iterator));
    }

    public static <T> Stream<T> stream(final Iterable<T> iterable) {
        return stream(iterable.iterator());
    }

    public static <T> Iterator<T> noRemove(final Iterator<T> iterator) {
        return new CloseableIterator<T>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public void remove() {
            }

            public T next() {
                return (T) iterator.next();
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }
        };
    }

    public static <T> Iterator<T> removeOnNext(final Iterator<T> iterator) {
        return new CloseableIterator<T>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public void remove() {
                iterator.remove();
            }

            public T next() {
                T object = (T) iterator.next();
                iterator.remove();
                return object;
            }

            public void close() {
                CloseableIterator.closeIterator(iterator);
            }
        };
    }

    public static class ExpandableIterator<T> implements Iterator<T> {

        public Iterator<T> baseIterator;
        public Queue<T> expansion;

        public ExpandableIterator(final Iterator<T> baseIterator) {
            this.baseIterator = baseIterator;
            this.expansion = new LinkedList<>();
        }

        public T next() {
            return this.expansion.isEmpty() ? this.baseIterator.next() : this.expansion.remove();
        }

        public boolean hasNext() {
            return !this.expansion.isEmpty() || this.baseIterator.hasNext();
        }

        public boolean push(final T t) {
            return this.expansion.add(t);
        }

        public boolean onlyHasNext() {
            if (!this.hasNext())
                return false;
            final T t = this.next();
            final boolean hasNextNext = this.hasNext();
            this.push(t);
            return !hasNextNext;
        }

        public boolean hasNextNext() {
            if (!this.hasNext())
                return false;
            final T t = this.next();
            final boolean hasNextNext = this.hasNext();
            this.push(t);
            return hasNextNext;
        }

        public static <T> ExpandableIterator<T> of(final Iterator<T> baseIterator) {
            return new ExpandableIterator<>(baseIterator);
        }

    }
}
