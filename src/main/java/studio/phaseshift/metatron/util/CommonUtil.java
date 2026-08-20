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

package studio.phaseshift.metatron.util;

import org.apache.lucene.search.spell.LevenshteinDistance;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
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
            // AutoCloseable covers both java.io.Closeable and java.sql.Connection
            // (which extends AutoCloseable via Wrapper, not via java.io.Closeable)
            if (object instanceof AutoCloseable)
                ((AutoCloseable) object).close();
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

    public static String removeQuotes(final String quotedString) {
        String unquotedString = quotedString.trim();
        while (unquotedString.startsWith("\"") || unquotedString.startsWith("'")) {
            unquotedString = unquotedString.substring(1);
        }
        while (unquotedString.endsWith("\"") || unquotedString.endsWith("'")) {
            unquotedString = unquotedString.substring(0, unquotedString.length() - 1);
        }
        return unquotedString;
    }

    public static String normalize(String concept) {
        if (concept == null || concept.isBlank()) return "";
        concept = concept.trim().toLowerCase();
        // Basic singularization
        if (concept.endsWith("ies") && concept.length() > 3) {
            concept = concept.substring(0, concept.length() - 3) + "y";
        } else if (concept.endsWith("es") && concept.length() > 2) {
            // Handle cases like "indexes" -> "index", "buses" -> "bus"
            // If it ends in "ses", "xes", "ches", "shes", we strip "es"
            if (concept.endsWith("ses") || concept.endsWith("xes") || concept.endsWith("ches") || concept.endsWith("shes")) {
                concept = concept.substring(0, concept.length() - 2);
            } else {
                // Default to stripping "s" if it ends in "es" but not one of the above? 
                // e.g., "apples" -> "apple" (ends in es)
                concept = concept.substring(0, concept.length() - 1);
            }
        } else if (concept.endsWith("s") && !concept.endsWith("ss") && concept.length() > 1) {
            concept = concept.substring(0, concept.length() - 1);
        }
        return concept.replace(' ', '_').replace("'", "").replace("\"", "").replace("&", "and");
    }

    // =========================================================================
    // Spell correction
    // =========================================================================

    /**
     * Lucene Levenshtein distance — returns a normalized similarity score
     * between 0.0 (completely different) and 1.0 (identical).
     */
    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();

    /**
     * Common English words loaded lazily from {@code dictionary_en.txt}
     * on the classpath.  One word per line; lines starting with {@code #}
     * are comments.  Compatible with SCOWL-generated word lists — drop a
     * SCOWL {@code wl.txt} (size 60 or larger) at this resource path to
     * replace the built-in dictionary.
     */
    private static Set<String> COMMON_WORDS = null;

    private static synchronized Set<String> commonWords() {
        if (COMMON_WORDS == null) {
            final InputStream in = CommonUtil.class.getResourceAsStream("dictionary_en.txt");
            if (in == null) {
                throw MTronException.of(
                        "dictionary_en.txt not found on classpath — "
                                + "ensure src/main/resources is on the classpath and "
                                + "dictionary_en.txt is included in the build");
            }
            final Set<String> words = new HashSet<>();
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String word = line.trim().toLowerCase();
                    if (!word.isEmpty() && !word.startsWith("#") && word.length() >= 4) {
                        words.add(word);
                    }
                }
            } catch (final IOException e) {
                throw MTronException.of("failed to read dictionary_en.txt: %s", e.getMessage());
            }
            COMMON_WORDS = Collections.unmodifiableSet(words);
            System.err.println("INFO: loaded " + COMMON_WORDS.size() + " words from dictionary_en.txt");
        }
        return COMMON_WORDS;
    }

    /**
     * Correct the spelling of a word against the built-in dictionary of
     * common English words loaded from {@code dictionary_en.txt}.
     * Returns the closest match within an edit-distance threshold, or the
     * original word if no correction is found.
     * <p>
     * Words shorter than 4 characters are returned unchanged — they are
     * too short for reliable correction.
     *
     * @param word the word to check
     * @return the corrected word, or the original if no correction is needed
     */
    public static String correctSpelling(final String word) {
        return correctSpelling(word, commonWords());
    }

    /**
     * Correct the spelling of a word against a provided dictionary.
     * Returns the closest dictionary entry within an edit-distance threshold,
     * or the original word if no close match is found.
     * <p>
     * Words shorter than 4 characters or already present in the dictionary
     * are returned unchanged.
     *
     * @param word       the word to check
     * @param dictionary the set of correctly-spelled words to match against
     * @return the corrected word, or the original if no correction is needed
     */
    public static String correctSpelling(final String word, final Set<String> dictionary) {
        if (word == null || word.isBlank() || dictionary == null || dictionary.isEmpty())
            return word;

        final String normalized = word.toLowerCase().trim();

        // Very short words are too prone to false positives
        if (normalized.length() < 4)
            return word;

        // Already in dictionary — no correction needed
        if (dictionary.contains(normalized))
            return word;

        String bestMatch = null;
        float bestScore = 0.0f;

        for (final String candidate : dictionary) {
            if (candidate.length() < 4) continue; // skip short dictionary entries too
            final float score = LEVENSHTEIN.getDistance(normalized, candidate.toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
                if (score >= 1.0f) break; // exact match, can't do better
            }
        }

        // Threshold: require at least 75% similarity.
        // For words of length 4, a single edit gives 0.75 — we accept that.
        // For longer words, even two edits still clears 0.75.
        if (bestMatch != null && bestScore >= 0.75f) {
            return bestMatch;
        }
        return word;
    }

    /**
     * Split a sequence on occurrences of {@code split} that occur outside of
     * quoted spans.  A quoted span opens with a run of N quote characters
     * (single, triple, quadruple, ... {@code 's or "}s) and closes only on a
     * run of the SAME quote character at least as long as the opening run —
     * other quote characters, and shorter runs of the same character, are
     * literal span content.  Split characters inside spans pass through
     * untouched and are preserved in the fragments.
     */
    public static List<String> splitOnNonQuotedSequence(final String sequence, final char split, boolean includeSplitCharacter) {
        final List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // Open span state: length of the delimiter run (0 = outside any span)
        // and which quote character the span was opened with.
        int quoteCount = 0;
        char quoteChar = 0;
        boolean escaped = false;

        final int length = sequence.length();
        for (int i = 0; i < length; i++) {
            final char c = sequence.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                current.append(c);
                continue;
            }
            if (c == '\'' || c == '"') {
                // Measure the full run of this quote character
                int run = 0;
                while (i + run < length && sequence.charAt(i + run) == c)
                    run++;
                if (quoteCount == 0) {
                    // No open span. An even run (2, 4, ...) is a sequence of
                    // self-contained empty-string literals ("", """") — it does
                    // not open a span. Odd runs open one: 1 = '...'/"...",
                    // 3+ = triple-quoted """...""".
                    if (run % 2 != 0) {
                        quoteCount = run;
                        quoteChar = c;
                    }
                } else if (quoteChar == c && run >= quoteCount) {
                    // Closing run of the same character at least as long as the opener
                    quoteCount = 0;
                    quoteChar = 0;
                }
                // Append the whole run verbatim — the splitter preserves text
                current.append(sequence, i, i + run);
                i += run - 1;
                continue;
            }
            if (c == split && quoteCount == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
                if (includeSplitCharacter)
                    current.append(c);
                continue;
            }
            current.append(c);
        }
        result.add(current.toString().trim());
        return result;
    }

    public static fURI mintUUID(final fURI baseURI) {
        final String uuid = UUID.randomUUID().toString().toLowerCase();
        return null == baseURI ? f(uuid) : baseURI.extend(uuid);
    }

    public static fURI mintShortUUID(final fURI baseURI, boolean retryIfCollision) {
        fURI shortId;
        do {
            final UUID uuid = UUID.randomUUID();
            shortId = baseURI.extend(Long.toHexString(uuid.getMostSignificantBits())
                    .substring(0, 8) // Take first 8 hex chars of the MSB
            );
        } while (retryIfCollision && !Router.readFromSpace(shortId).isNoObj());
        return shortId;
    }

    private static final String[] STOP_WORDS = {
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't", "as", "at",
            "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can't", "cannot", "could", "couldn't",
            "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
            "each",
            "few", "for", "from", "further",
            "had", "hadn't", "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him", "himself", "his", "how", "how's",
            "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself",
            "let's",
            "me", "more", "most", "mustn't", "my", "myself",
            "no", "nor", "not",
            "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own",
            "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such",
            "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too",
            "under", "until", "up",
            "very",
            "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't",
            "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves",
            // Extended list including less common forms and variations
            "able", "according", "accordingly", "across", "actually", "afterwards", "ain", "allow", "allows", "almost", "alone", "along", "already", "also", "although", "always", "among", "amongst", "another", "anybody", "anyhow", "anyone", "anything", "anyway", "anyways", "anywhere", "apart", "appear", "appreciate", "appropriate", "around", "aside", "ask", "asking", "associated", "available", "away", "awfully",
            "back", "became", "become", "becomes", "becoming", "beforehand", "behind", "believe", "beside", "besides", "best", "better", "beyond", "brief",
            "came", "cause", "causes", "certain", "certainly", "changes", "clearly", "co", "com", "come", "comes", "concerning", "consequently", "consider", "considering", "contain", "containing", "contains", "corresponding", "course", "currently",
            "definitely", "described", "despite", "did", "different", "done", "downwards", "due",
            "edu", "eg", "eight", "either", "else", "elsewhere", "enough", "entirely", "especially", "et", "etc", "even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "ex", "exactly", "example", "except",
            "far", "ff", "fifth", "first", "five", "followed", "following", "follows", "former", "formerly", "forth", "four", "front", "full", "furthermore",
            "get", "gets", "getting", "given", "gives", "go", "goes", "going", "gone", "got", "gotten", "greetings",
            "happens", "hardly", "hence", "help", "hereafter", "hereby", "herein", "hereupon", "hi", "hopefully", "howbeit", "however",
            "ie", "ignored", "immediate", "inasmuch", "inc", "indeed", "indicate", "indicated", "indicates", "inner", "insofar", "instead", "inward",
            "just",
            "keep", "keeps", "kept", "know", "known", "knows",
            "last", "lately", "later", "latter", "latterly", "least", "less", "lest", "let", "like", "liked", "likely", "little", "look", "looking", "looks", "ltd",
            "mainly", "many", "may", "maybe", "mean", "meanwhile", "merely", "might", "moreover", "mostly", "much", "must",
            "name", "namely", "nd", "near", "nearly", "necessary", "need", "needs", "neither", "never", "nevertheless", "new", "next", "nine", "nobody", "non", "none", "noone", "normally", "nothing", "novel", "nowhere",
            "obviously", "oh", "ok", "okay", "old", "onto", "others", "otherwise", "outside", "overall", "own",
            "particular", "particularly", "per", "perhaps", "placed", "please", "plus", "possible", "presumably", "probably", "provides",
            "que", "quite", "qv",
            "rather", "rd", "re", "really", "reasonably", "regarding", "regardless", "regards", "relatively", "respectively", "right",
            "said", "saw", "say", "saying", "says", "second", "secondly", "see", "seeing", "seem", "seemed", "seeming", "seems", "seen", "self", "selves", "sensible", "sent", "serious", "seriously", "seven", "several", "shall", "side", "since", "six", "slightly", "somebody", "somehow", "someone", "something", "sometime", "sometimes", "somewhat", "somewhere", "soon", "sorry", "specified", "specify", "specifying", "state", "states", "still", "sub", "sure",
            "take", "taken", "tell", "tends", "th", "thank", "thanks", "thanx", "third", "thorough", "thoroughly", "thought", "thoughts", "thru", "thus", "together", "took", "toward", "towards", "tried", "tries", "truly", "try", "trying", "twice", "two",
            "un", "underneath", "unfortunately", "unless", "unlikely", "unto", "upon", "use", "used", "useful", "uses", "using", "usually",
            "value", "various", "via", "viz", "vs",
            "want", "wants", "way", "welcome", "went", "well", "whatever", "whence", "whenever", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "whilst", "whither", "whoever", "whole", "whose", "willing", "wish", "wonder", "wrote",
            "yes", "yet",
            "zero"
    };

    private static final Set<String> STOP_WORDS_SET = new HashSet<>(Arrays.asList(STOP_WORDS));

    public static String stripStopwords(final String text) {
        final String[] words = text.split("\\s+");
        final StringBuilder filteredText = new StringBuilder();
        for (String word : words) {
            // Clean punctuation and normalize case
            String cleanWord = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (!STOP_WORDS_SET.contains(cleanWord)) {
                filteredText.append(cleanWord).append(" ");
            }
        }
        return filteredText.toString().trim();
    }

    public static String clipString(final String string, final int maxLength, final boolean ellipses) {
        if (string.length() < maxLength)
            return string;
        return string.substring(0, maxLength) + (ellipses ? "..." : "");
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

    public static String readResource(final Class<?> rootClass, final String resourcePath, String... replacements) {
        String s = readResource(rootClass, resourcePath).toString();
        for (int i = 0; i < replacements.length; i += 2)
            s = s.replace(replacements[i], replacements[i + 1]);
        return s;
    }

    public static StringBuilder readResource(final Class<?> rootClass, final String resourcePath) {
        final InputStream inputStream = rootClass.getResourceAsStream(resourcePath);
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

    public static void copyDirectory(final Path from, final Path to) {
        try (final Stream<Path> fileWalk = Files.walk(from)) {
            fileWalk.forEach(sourcePath -> {
                try {
                    // Resolve the relative path in the destination
                    Path targetPath = to.resolve(from.relativize(sourcePath));
                    // Create parent directories if they don't exist
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (final IOException e) {
                    throw MTronException.of(e);
                }
            });
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    public static void deleteDirectory(final Path dir) {
        try (final Stream<Path> fileWalk = Files.walk(dir)) {
            fileWalk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (final IOException e) {
                            throw MTronException.of(e);
                        }
                    });
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }

    public static final String HEADER_SEPARATOR = "####################";
    public static final String HEADER_FILE = "./conf/ansi_headers.txt";

    public static String getHeader(final String headerFile, final String headerName, final boolean applyGraphitty) {
        try {
            final Map<String, String> headers = new HashMap<>();
            StringBuilder current = new StringBuilder();
            final BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(headerFile)));
            String headerTitle = null;
            while (input.ready()) {
                final String line = input.readLine().stripTrailing();
                if (line.startsWith(HEADER_SEPARATOR) && line.endsWith(HEADER_SEPARATOR)) {
                    if (null != headerTitle && !current.isEmpty()) {
                        headers.put(headerTitle, current.toString());
                    }
                    current = new StringBuilder();
                    headerTitle = line.replace(HEADER_SEPARATOR, "").trim();
                } else {
                    current.append(line).append("\n");
                }
            }
            input.close();
            if (!current.isEmpty())
                headers.put(headerTitle, current.toString());
            final String fetchHeaderTitle = null == headerName || headerName.isBlank() ?
                    new ArrayList<>(headers.keySet()).get(new Random().nextInt(headers.size())) : headerName;
            final String fetchHeader = headers.get(fetchHeaderTitle);
            if (null == fetchHeader)
                throw MTronException.of("<unknown header: " + fetchHeaderTitle + ">");
            return applyGraphitty ? Graphitty.string(fetchHeader) : fetchHeader.toString();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static class LstCollector implements Collector<Obj, List<Obj>, Lst> {

        final fURI vid;
        final fURI tid;

        public LstCollector() {
            this.vid = null;
            this.tid = LST_TID;
        }

        public LstCollector(final fURI tid, final fURI vid) {
            this.tid = tid;
            this.vid = vid;
        }

        @Override
        public Supplier<List<Obj>> supplier() {
            return ArrayList::new;
        }

        @Override
        public BiConsumer<List<Obj>, Obj> accumulator() {
            return List::add;
        }

        @Override
        public BinaryOperator<List<Obj>> combiner() {
            return (a, b) -> {
                a.addAll(b);
                return a;
            };
        }

        @Override
        public Function<List<Obj>, Lst> finisher() {
            return m -> lst(m, this.tid, this.vid);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
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

    public static <V> List<V> mutableList(final List<V> list) {
        return new ArrayList<>(list);
    }

    public static <V> List<V> mutableList(final V... args) {
        return mutableList(List.of(args));
    }


    public static <K, V> Map<K, V> mutableMap(final Map<K, V> map) {
        return new LinkedHashMap<>(map);
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

    /* ================================================================
     * Spinner
     * ================================================================ */

    /**
     * A terminal spinner that animates while blocking work runs on another thread.
     * <pre>{@code
     *   final var spinner = CommonUtil.spinner("loading...");
     *   try {
     *       ... blocking work ...
     *   } finally {
     *       spinner.stop();
     *   }
     * }</pre>
     */
    public static Spinner spinner(final String message, final boolean rainbow) {
        return new Spinner(message, null, rainbow);
    }

    public static final class Spinner implements AutoCloseable {
        private static final Map<String, String[]> FRAMES = Map.of(
                "level", new String[]{"▁", "▃", "▄", "▅", "▆", "▇", "█", "▇", "▆", "▅", "▄", "▃"},
                "braille1", new String[]{"⢎⡰", "⢎⡡", "⢎⡑", "⢎⠱", "⠎⡱", "⢊⡱", "⢌⡱", "⢆⡱"},
                "braille2", new String[]{"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"},
                "braille3", new String[]{"⠋", "⠙", "⠹", "⠸", "⢰", "⣰", "⣠", "⣄", "⣆", "⡆", "⠇", "⠏"},
                "kitt", new String[]{"▉", "▊", "▋", "▌", "▍", "▎", "▏", "▎", "▍", "▌", "▋", "▊", "▉"},
                "bounce1", new String[]{".", "_", "-", "'", "-", "_"},
                "bounce2", new String[]{".", "o", "O", "°", "O", "o"},
                "triangle", new String[]{"◣", "◤", "◥", "◢"});

        private static final long INTERVAL_MS = 120;
        private String message;

        private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        private final Thread thread;

        private Spinner(final String message, final String frameStyle, final boolean rainbow) {
            this.message = message;
            final String[] frame = null == frameStyle || "random".equals(frameStyle) ?
                    FRAMES.values()
                            .stream()
                            .skip(new Random().nextInt(FRAMES.size()))
                            .findFirst()
                            .orElse(FRAMES.get("line")) : FRAMES.get(frameStyle);
            this.thread = new Thread(() -> {
                int idx = 0;
                String currentFrame = "";
                while (running.get()) {
                    currentFrame = frame[idx++ % frame.length];
                    System.out.print("\r" + (rainbow ? Graphitty.string(Graphitty.sillyPrint(currentFrame, rainbow, false)) : currentFrame) + " " + this.message);
                    //System.out.flush();
                    try {
                        Thread.sleep(INTERVAL_MS);
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
                System.out.print(Graphitty.string("{{-X-}}") + "\r"); // overwrite with spaces, then return to col 0
                System.out.flush();
            }, "spinner for [" + CommonUtil.clipString(message, 20, false) + "]");
            this.thread.setDaemon(true);
            //  this.thread.start();
            ThreadExecutor.instance().execute(this.thread);
        }

        /**
         * Stop the animation and wait for the line to clear.
         */
        public void stop() {
            if (!running.getAndSet(false))
                return;
            this.thread.interrupt();
            try {
                this.thread.join(200);
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Convenience for try-with-resources.
         */
        @Override
        public void close() {
            stop();
        }

        public void setMessage(final String format, final Object... args) {
            this.message = format.formatted(args);
        }
    }

    /* ================================================================
     * Tree walking
     * ================================================================ */

    /**
     * A node visited during a {@link #treeConsumer} walk.
     *
     * @param uri        the full URI of this node
     * @param name       the last segment of the URI (display name)
     * @param obj        the object at this URI, or {@link NoObj#noobj()} if none
     * @param depth      0 = root
     * @param isLast     true if this node is the last child of its parent
     * @param childCount number of immediate children (0 = leaf)
     */
    public record TreeEntry(fURI uri, String name, Obj obj, int depth,
                            boolean isLast, int childCount) {
        /**
         * @return true if this node has children to expand.
         */
        public boolean hasChildren() {
            return childCount > 0;
        }
    }

    /**
     * Walk a metatron space tree depth-first.
     * <p>
     * Starting from {@code root}, each node's children are discovered by reading
     * the branch URI {@code uri.extend("+/")}, which returns {@link Rel Rel}
     * objects mapping each child URI to its value.
     * <p>
     * The consumer receives nodes in DFS pre-order so that a {@code TreeWidget}
     * can reconstruct the visual tree structure from the sequence of
     * {@link TreeEntry#isLast} flags and {@link TreeEntry#depth} changes.
     *
     * @param root     the root URI to start from
     * @param maxDepth maximum levels to descend (0 = root only)
     * @param consumer receives each node as it is visited
     */
    public static void treeConsumer(final fURI root, final int maxDepth,
                                    final Consumer<TreeEntry> consumer) {
        treeConsumer(root, maxDepth, Set.of(), consumer);
    }

    /**
     * Walk a metatron space tree depth-first with per-branch expansion.
     * <p>
     * Nodes in {@code forceExpand} have their children read even when
     * {@code depth >= maxDepth}, enabling selective deepening of specific
     * branches without expanding the entire tree.
     *
     * @param root        the root URI to start from
     * @param maxDepth    maximum levels to descend (0 = root only)
     * @param forceExpand URIs whose children should always be read
     * @param consumer    receives each node as it is visited
     */
    public static void treeConsumer(final fURI root, final int maxDepth,
                                    final Set<fURI> forceExpand,
                                    final Consumer<TreeEntry> consumer) {
        _treeWalk(root, maxDepth, forceExpand, 0, true, consumer);
    }

    private static void _treeWalk(final fURI uri, final int maxDepth, final int depth,
                                  final boolean isLast, final Consumer<TreeEntry> consumer) {
        _treeWalk(uri, maxDepth, Set.of(), depth, isLast, consumer);
    }

    private static void _treeWalk(final fURI uri, final int maxDepth,
                                  final Set<fURI> forceExpand,
                                  final int depth, final boolean isLast,
                                  final Consumer<TreeEntry> consumer) {
        final Obj obj = Router.readFromSpace(uri);
        // Directories carry the trailing / (a branch); the display name is the last real
        // segment, so strip the branch marker before naming (keep the branch uri for navigation).
        final String name = uri.asNode().name();

        final java.util.List<fURI> childUris = new java.util.ArrayList<>();
        if (depth < maxDepth || forceExpand.contains(uri)) {
            // Read direct children via +/ on the specific parent URI.
            // Each space implements +/ to return the immediate children
            // of the given node (e.g. local:a/+/ → a1, a2).  This is the
            // universal "list children" query pattern.
            Router.readFromSpace(uri.extend("+/")).stream()
                    .filter(o -> !o.isNoObj())
                    .forEach(o -> {
                        final Rel rel = o.asRel();
                        childUris.add(rel.first().uriValue());
                    });
            childUris.sort(java.util.Comparator.comparing(f -> f.asNode().name()));
        }

        consumer.accept(new TreeEntry(uri, name, obj, depth, isLast, childUris.size()));

        for (int i = 0; i < childUris.size(); i++) {
            _treeWalk(childUris.get(i), maxDepth, forceExpand, depth + 1,
                    i == childUris.size() - 1, consumer);
        }
    }

}
