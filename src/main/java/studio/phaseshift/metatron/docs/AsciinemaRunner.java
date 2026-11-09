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

package studio.phaseshift.metatron.docs;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Compiles a terminal-session script ({@code <name>.cast.yml}) into an asciicast
 * v3 recording ({@code <name>.cast}).
 *
 * <p>The script is the artifact a person writes and reviews: one line per
 * expression, the prompt and the result. The recording is the WIRE FORMAT the
 * browser player consumes — and it is <em>generated</em>: the per-keystroke
 * output events are derived from {@code typing_delay}, so a phrase is authored
 * once, not one-hundred times. A cast emitted here is byte-identical on every
 * machine and every run (no timestamps, no environment, fixed millisecond
 * intervals), which is what makes it reviewable in a diff.</p>
 *
 * <p>This runner is for the scripts that do NOT come from a doc block (a boot
 * banner, a shell session, a demo whose pacing needs its own file). The doc
 * path lives in {@link MarkdownRunner}, which feeds the SAME {@link Asciicast}
 * writer from {@link MtronPreprocessor} statements.</p>
 *
 * <h3>Script format</h3>
 * <pre>
 * title: repl basics              # optional
 * prompt: "mtron&gt; "              # printed before each input
 * cols: 100                       # term.cols/rows in the header
 * rows: 24
 * typing_delay: 0.06              # seconds per generated keystroke (0 = instant)
 * start_delay: 0.35               # before the first prompt
 * prompt_delay: 0.20              # before every later prompt
 * pre_enter_delay: 0.12           # before the Enter newline
 * post_enter_delay: 0.30          # before the result lands
 * marker_delay: 0.20
 * idle_time_limit: 2              # playback hint (header)
 * actions:
 *   - input: "1 + 1"
 *     output: ["==&gt;2"]
 *   - marker: "second expression"
 *   - "2 * 21"                    # a bare string is an input with no output
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 * java ...AsciinemaRunner &lt;script.cast.yml|dir&gt;... [--check]
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class AsciinemaRunner {

    private static final Set<String> SCRIPT_KEYS = Set.of(
            "title", "prompt", "cols", "rows", "term", "start_delay", "typing_delay",
            "prompt_delay", "pre_enter_delay", "post_enter_delay", "marker_delay",
            "idle_time_limit", "actions");
    private static final Set<String> ACTION_KEYS = Set.of("input", "output", "marker");

    private static final String SUFFIX = ".cast.yml";
    private static final String DEFAULT_TERM = "xterm-256color";
    private static final String DEFAULT_PROMPT = "";

    private AsciinemaRunner() {
    }

    /**
     * One scripted step: an input with the lines the console answers it with,
     * or a marker (a labelled, invisible point on the player's timeline).
     */
    private record Action(String input, List<String> output, String marker) {
    }

    /**
     * A parsed script: the term, the timing, and the actions — every default
     * resolved, so rendering has no decisions left to make.
     */
    private record Script(String title, String prompt, int cols, int rows, String term,
                          double startDelay, double typingDelay, double promptDelay,
                          double preEnterDelay, double postEnterDelay, double markerDelay,
                          Double idleTimeLimit, List<Action> actions) {
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CLI
    // ═══════════════════════════════════════════════════════════════════

    public static void main(final String[] args) throws IOException {
        boolean check = false;
        final List<Path> inputs = new ArrayList<>();
        for (final String arg : args) {
            if ("--check".equals(arg)) check = true;
            else inputs.add(Path.of(arg));
        }
        if (inputs.isEmpty()) {
            System.err.println("usage: AsciinemaRunner <script.cast.yml|dir>... [--check]");
            System.exit(2);
            return;
        }

        final List<Path> scripts = new ArrayList<>();
        for (final Path input : inputs) {
            if (Files.isDirectory(input)) {
                try (final Stream<Path> walk = Files.walk(input)) {
                    walk.filter(Files::isRegularFile)
                            .filter(f -> f.getFileName().toString().endsWith(SUFFIX))
                            .sorted()
                            .forEach(scripts::add);
                }
            } else if (Files.isRegularFile(input)) {
                scripts.add(input);
            } else {
                System.err.println("no such script: " + input);
                System.exit(2);
                return;
            }
        }

        int stale = 0;
        for (final Path script : scripts) {
            final Path cast = castPath(script);
            final Asciicast.Rendered rendered = compile(parse(script), script);
            final boolean current = Files.exists(cast)
                    && Files.readString(cast, StandardCharsets.UTF_8).equals(rendered.text());
            if (current) {
                System.out.println("[cast] unchanged " + cast.getFileName());
            } else if (check) {
                System.out.println("[cast] STALE     " + cast.getFileName() + " (regenerate without --check)");
                stale++;
            } else {
                Files.writeString(cast, rendered.text(), StandardCharsets.UTF_8);
                System.out.println("[cast] wrote     " + cast.getFileName()
                        + " (" + rendered.events() + " events, " + rendered.seconds() + "s)");
            }
        }
        if (check && stale > 0) {
            System.err.println("[cast] " + stale + " stale recording(s)");
            System.exit(1);
        }
    }

    /** {@code foo.cast.yml} → {@code foo.cast}, beside it. */
    static Path castPath(final Path script) {
        final String name = script.getFileName().toString();
        if (!name.endsWith(SUFFIX)) {
            throw new IllegalArgumentException("not a " + SUFFIX + " script: " + script);
        }
        return script.resolveSibling(name.substring(0, name.length() - ".yml".length()));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Compile
    // ═══════════════════════════════════════════════════════════════════

    /** Compile one script from disk — the test entry point (no CLI, no exit). */
    static Asciicast.Rendered compile(final Path source) throws IOException {
        return compile(parse(source), source);
    }

    static Asciicast.Rendered compile(final Script script, final Path source) {
        final Asciicast cast = Asciicast.of(script.cols(), script.rows())
                .term(script.term())
                .title(script.title())
                .prompt(script.prompt())
                .idleTimeLimit(script.idleTimeLimit())
                .startDelay(script.startDelay())
                .typingDelay(script.typingDelay())
                .promptDelay(script.promptDelay())
                .preEnterDelay(script.preEnterDelay())
                .postEnterDelay(script.postEnterDelay())
                .markerDelay(script.markerDelay());
        for (final Action action : script.actions()) {
            if (action.marker() != null) cast.marker(action.marker());
            else cast.statement(action.input(), action.output());
        }
        return cast.render(source.getFileName().toString());
    }

    static Script parse(final Path source) throws IOException {
        final Object root;
        try (final Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            root = new Yaml().load(reader);
        }
        final Map<String, Object> map = map(root, source.getFileName() + ": document");
        for (final String key : map.keySet()) {
            if (!SCRIPT_KEYS.contains(key)) {
                throw new IllegalArgumentException(source.getFileName() + ": unknown key '" + key + "'");
            }
        }
        return new Script(
                string(map, "title", null),
                string(map, "prompt", DEFAULT_PROMPT),
                integer(map, "cols", 80),
                integer(map, "rows", 24),
                string(map, "term", DEFAULT_TERM),
                number(map, "start_delay", 0.35),
                number(map, "typing_delay", 0.06),
                number(map, "prompt_delay", 0.20),
                number(map, "pre_enter_delay", 0.12),
                number(map, "post_enter_delay", 0.30),
                number(map, "marker_delay", 0.20),
                map.get("idle_time_limit") == null ? null : number(map, "idle_time_limit", 0),
                actions(map.get("actions"), source));
    }

    private static List<Action> actions(final Object value, final Path source) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(source.getFileName() + ": 'actions' must be a non-empty list");
        }
        final List<Action> out = new ArrayList<>(list.size());
        int line = 0;
        for (final Object item : list) {
            line++;
            final String where = source.getFileName() + ": action " + line;
            if (item instanceof String input) {
                out.add(new Action(input, List.of(), null));
                continue;
            }
            final Map<String, Object> action = map(item, where);
            for (final String key : action.keySet()) {
                if (!ACTION_KEYS.contains(key)) {
                    throw new IllegalArgumentException(where + ": unknown key '" + key + "'");
                }
            }
            final String marker = string(action, "marker", null);
            final String input = string(action, "input", null);
            if (marker != null && input != null) {
                throw new IllegalArgumentException(where + ": 'marker' and 'input' are exclusive");
            }
            if (marker != null) {
                out.add(new Action(null, List.of(), marker));
            } else if (input != null) {
                out.add(new Action(input, strings(action.get("output")), null));
            } else {
                throw new IllegalArgumentException(where + ": needs 'input' or 'marker'");
            }
        }
        return out;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Scalars
    // ═══════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value, final String what) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(what + " must be a mapping");
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        ((Map<Object, Object>) source).forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private static String string(final Map<String, Object> map, final String key, final String absent) {
        final Object value = map.get(key);
        return value == null ? absent : String.valueOf(value);
    }

    private static double number(final Map<String, Object> map, final String key, final double absent) {
        final Object value = map.get(key);
        if (value == null) return absent;
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("'" + key + "' must be a number");
        }
        return number.doubleValue();
    }

    private static int integer(final Map<String, Object> map, final String key, final int absent) {
        final Object value = map.get(key);
        if (value == null) return absent;
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
        return number.intValue();
    }

    /** The lines under {@code output:} — a list, or one scalar line. */
    private static List<String> strings(final Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            final List<String> out = new ArrayList<>(list.size());
            for (final Object item : list) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        return List.of(String.valueOf(value));
    }
}
