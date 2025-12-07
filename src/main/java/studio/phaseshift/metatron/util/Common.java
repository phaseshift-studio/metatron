/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.util;

import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;

import java.io.Closeable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Common {

    private static final Pattern INT_PATTERN = Pattern.compile("-?\\d");
    private static final Pattern REAL_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)");

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

    public static String indent(final String s, final int spaces) {
        return Arrays.stream(s.split("\n")).map(r -> " ".repeat(spaces) + r).reduce((a, b) -> a + b + "\n").orElse("").trim();
    }

    private static <K, V> Map<K, V> mapBuilder(final Supplier<Map<K, V>> supplier, final Object... args) {
        if (args.length == 1 && args[0] instanceof Map) {
            final Map<K, V> map = supplier.get();
            map.putAll((Map<K, V>) args[0]);
            return map;
        }
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

    public static Obj loop(final Obj lhs, final Function<Obj, Obj> loopFunction, final int times) {
        Obj result = lhs;
        for (int i = 0; i < times; i++) {
            result = loopFunction.apply(result);
        }
        return result;
    }

    public static boolean isInt(final String s) {
        return INT_PATTERN.matcher(s).matches();
    }

    public static boolean isReal(final String s) {
        return REAL_PATTERN.matcher(s).matches();
    }

    public static int countLines(final String str) {
        final String[] lines = str.split("\r\n|\r|\n");
        return lines.length;
    }

    public static String replaceGroups(String s, final String leftDelim, final String rightDelim,
                                       final Function<String, String> replaceFunction) {
        String ss = s;
        int start_pos = 0;
        while (true) {
            // Find the start delimiter
            start_pos = ss.indexOf(leftDelim, start_pos);
            if (start_pos == -1) {
                break; // No more delimiters found
            }
            // Find the end delimiter
            int end_pos = ss.indexOf(rightDelim, start_pos + leftDelim.length());
            if (end_pos == -1) {
                break; // No matching end delimiter found
            }
            // Extract the substring between the delimiters
            String substring = ss.substring(start_pos + leftDelim.length(), end_pos - (start_pos + leftDelim.length()));
            // Apply the replacement function
            String replacement = replaceFunction.apply(substring);
            // Replace the substring in the original string
            ss = ss.substring(0, start_pos) + replacement + ss.substring(end_pos - start_pos + rightDelim.length());
            // Update the start position to continue scanning
            start_pos = start_pos + replacement.length();
        }
        return ss;
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

    public static <K, V> Map<K, V> immutableMap(final Object... args) {
        return Map.copyOf(mapBuilder(HashMap::new, args));
    }

    public static <K, V> Map<K, V> mutableOrderedMap(final Object... args) {
        return mapBuilder(LinkedHashMap::new, args);
    }

    public static <K, V> Map<K, V> immutableOrderedMap(final Object... args) {
        return Map.copyOf(mapBuilder(LinkedHashMap::new, args));
    }
}
