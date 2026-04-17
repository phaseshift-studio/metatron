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

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public final class CommonUtil {

    private static final Pattern INT_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern REAL_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)");

    private CommonUtil() {
        // do nothing
    }

    @SafeVarargs
    public static <T> List<T> arrayList(final T... elements) {
        final List<T> list = new ArrayList<>();
        Collections.addAll(list, elements);
        return list;
    }

    public static <T> Tuple.Pair<T, Long> clock(final Supplier<T> supplier) {
        final long start = System.currentTimeMillis();
        final T result = supplier.get();
        final Long stop = System.currentTimeMillis() - start;
        return Tuple.Pair.with(result, stop);
    }


    public static Tuple.Pair<Obj, Long> clock(final Obj lhs, final Obj rhs) {
        final long start = System.currentTimeMillis();
        final Obj result = lhs.apply(rhs);
        final Long stop = System.currentTimeMillis() - start;
        return Tuple.Pair.with(result, stop);
    }

    public static String getTimeStamp(final Long currentTimeInMillis) {
        final LocalDateTime time = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(null == currentTimeInMillis ? System.currentTimeMillis() : currentTimeInMillis),
                java.time.ZoneId.systemDefault());
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");
        return time.format(formatter);
    }

    public static String snakeCase(final String s) {
        return Arrays.stream(s.split("(?=[A-Z])")).map(String::toLowerCase).collect(Collectors.joining("_"));
    }


    public static void sleepThread(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            if (BootLoader.BOOTING)
                return;
            throw MTronException.of(e);
        }
    }

    public static void close(final Object object) {
        try {
            if (object instanceof Closeable)
                ((Closeable) object).close();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static int width(final String s) {
        return Arrays.stream(s.split("\n")).map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
    }


    public static <A, B> B nullOrElse(final A object, final Supplier<B> ifNull, final Function<A, B> ifNotNull) {
        if (null == object)
            return ifNull.get();
        return ifNotNull.apply(object);
    }

    public static String indent(final String s, final int spaces) {
        return Arrays.stream(s.split("\n")).map(r -> " ".repeat(spaces) + r).reduce("", (a, b) -> a + "\n" + b).substring(1);
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
        return null != s && INT_PATTERN.matcher(s).matches();
    }

    public static boolean isReal(final String s) {
        return null != s && REAL_PATTERN.matcher(s).matches();
    }

    public static <T> Supplier<T> lambda(final Supplier<T> object) {
        return object;
    }

    public static int countLines(final String str) {
        final String[] lines = str.split("\r\n|\r|\n");
        return lines.length;
    }

    public static List<String> splitOnNonQuotedSequence(final String sequence, final char split, boolean includeSplitCharacter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (char c : sequence.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == split && !inSingleQuote && !inDoubleQuote) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (includeSplitCharacter || c != split)
                current.append(c);

        }
        result.add(current.toString().trim());
        return result;
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

    public static StringBuilder readResource(final String resourcePath) {
        final InputStream inputStream = CommonUtil.class.getClassLoader().getResourceAsStream(resourcePath);
        final StringBuilder sb = new StringBuilder();
        if (inputStream != null) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (final IOException e) {
                throw MTronException.of(e);
            }
        } else {
            throw MTronException.of("resource file not found: %s", resourcePath);
        }
        return sb;
    }

    public static class RecCollector implements Collector<Rel, Map<Obj, Obj>, Rec> {

        final fURI vid;
        final fURI tid;

        public RecCollector() {
            this.vid = null;
            this.tid = REC_TID;
        }

        public RecCollector(final fURI tid, final fURI vid) {
            this.tid = tid;
            this.vid = vid;
        }

        @Override
        public Supplier<Map<Obj, Obj>> supplier() {
            return LinkedHashMap::new;
        }

        @Override
        public BiConsumer<Map<Obj, Obj>, Rel> accumulator() {
            return (a, b) -> a.compute(b.jvm().get0(), (k, v) -> b.isNoObj() ? v : (null == v ? b.jvm().get1() : v.append(b.jvm().get1())));
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
            return m -> rec(m, this.tid, this.vid);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
    }

    public static <K, V> Map<K, V> mutableMap(final Object... args) {
        return mapBuilder(LinkedHashMap::new, args);
    }

    public static <K, V> Map<K, V> immutableMap(final Object... args) {
        return Map.copyOf(mapBuilder(LinkedHashMap::new, args));
    }

    public static <K, V> Map<K, V> mutableOrderedMap(final Object... args) {
        return mapBuilder(LinkedHashMap::new, args);
    }

    public static <K, V> Map<K, V> immutableOrderedMap(final Object... args) {
        return Map.copyOf(mapBuilder(LinkedHashMap::new, args));
    }
}
