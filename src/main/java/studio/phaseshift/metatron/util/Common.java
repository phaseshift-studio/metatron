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

import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;

import java.io.Closeable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Common {

    private Common() {
        // do nothing
    }

    public static void close(final Object object) {
        try {
            if (object instanceof Closeable)
                ((Closeable) object).close();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static <A, B> B nullOrElse(final A object, final Supplier<B> ifNull, final Function<A, B> ifNotNull) {
        if (null == object)
            return ifNull.get();
        return ifNotNull.apply(object);
    }

    private static <K, V> Map<K, V> mapBuilder(final Supplier<Map<K, V>> supplier, final Object... args) {
        return IntStream.iterate(0, i -> i < args.length, i -> i + 2)
                .filter(i -> i + 1 < args.length)
                .boxed()
                .collect(Collectors.toMap(
                        i -> (K) args[i],
                        i -> (V) args[i + 1],
                        (a, b) -> b,
                        supplier
                ));
    }

    public static class RecCollector implements Collector<Rel, Map<Obj, Obj>, Rec> {


        @Override
        public Supplier<Map<Obj, Obj>> supplier() {
            return LinkedHashMap::new;
        }

        @Override
        public BiConsumer<Map<Obj, Obj>, Rel> accumulator() {
            return (a, b) -> a.compute(b.first(), (k, v) -> null == v ? b.second() : v.append(b.second()));
        }

        @Override
        public BinaryOperator<Map<Obj, Obj>> combiner() {
            return (a, b) -> {
                a.putAll(b);
                return a;
            };
        }

        @Override
        public Function<Map<Obj, Obj>, Rec> finisher() {
            return MRec::rec;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
    }

    public static <K, V> Map<K, V> mutableMap(final Object... args) {
        return mapBuilder(HashMap::new, args);
    }

    public static <K, V> Map<K, V> mutableOrderedMap(final Object... args) {
        return mapBuilder(LinkedHashMap::new, args);
    }
}
