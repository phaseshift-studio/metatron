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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds an asciicast v3 recording — the wire format the browser player reads.
 *
 * <p>This is the ONE writer: the docs pipeline ({@link MtronPreprocessor}'s
 * statements, assembled by {@link MarkdownRunner}) and the hand-authored script
 * path ({@link AsciinemaRunner}) both emit through it, so a demo means the same
 * thing however it was authored. The output is byte-identical on every machine
 * and every run (no timestamps, no environment, fixed millisecond intervals),
 * which is what makes a recording reviewable in a diff.</p>
 *
 * <p>Intervals are RELATIVE in v3 (seconds since the previous event), so the
 * builder tracks nothing but the order of {@link #statement} and
 * {@link #marker} calls.</p>
 */
public final class Asciicast {

    /** A rendered recording, with what the caller wants to report about it. */
    public record Rendered(String text, int events, double seconds) {
    }

    private record Step(String marker, String input, List<String> output, boolean prompted) {
    }

    private final int cols;
    private final int rows;
    private final List<Step> steps = new ArrayList<>();

    private String term = "xterm-256color";
    private String title;
    private String prompt = "";
    private Double idleTimeLimit;
    private double startDelay = 0.35;
    private double typingDelay = 0.06;
    private double promptDelay = 0.20;
    private double preEnterDelay = 0.12;
    private double postEnterDelay = 0.30;
    private double markerDelay = 0.20;

    private Asciicast(final int cols, final int rows) {
        this.cols = cols;
        this.rows = rows;
    }

    /** A recording at the given term size; every timing has a sane default. */
    public static Asciicast of(final int cols, final int rows) {
        return new Asciicast(cols, rows);
    }

    public Asciicast term(final String term) {
        this.term = term;
        return this;
    }

    public Asciicast title(final String title) {
        this.title = title;
        return this;
    }

    public Asciicast prompt(final String prompt) {
        this.prompt = prompt;
        return this;
    }

    public Asciicast idleTimeLimit(final Double idleTimeLimit) {
        this.idleTimeLimit = idleTimeLimit;
        return this;
    }

    public Asciicast startDelay(final double startDelay) {
        this.startDelay = startDelay;
        return this;
    }

    /** Seconds per generated keystroke ({@code 0} paints the line at once). */
    public Asciicast typingDelay(final double typingDelay) {
        this.typingDelay = typingDelay;
        return this;
    }

    public Asciicast promptDelay(final double promptDelay) {
        this.promptDelay = promptDelay;
        return this;
    }

    public Asciicast preEnterDelay(final double preEnterDelay) {
        this.preEnterDelay = preEnterDelay;
        return this;
    }

    public Asciicast postEnterDelay(final double postEnterDelay) {
        this.postEnterDelay = postEnterDelay;
        return this;
    }

    public Asciicast markerDelay(final double markerDelay) {
        this.markerDelay = markerDelay;
        return this;
    }

    /** A labelled, invisible point on the player's timeline (a chapter). */
    public Asciicast marker(final String label) {
        this.steps.add(new Step(label, null, List.of(), true));
        return this;
    }

    /**
     * One typed expression and the lines the console answers it with. The
     * keystrokes are generated: nothing here is written per character.
     */
    public Asciicast statement(final String input, final List<String> output) {
        return statement(input, output, true);
    }

    /**
     * @param prompted false for a block that suppressed the console prompt
     *                 ({@code [NO_PROMPT]}), so the recording matches the page
     */
    public Asciicast statement(final String input, final List<String> output, final boolean prompted) {
        this.steps.add(new Step(null, input, List.copyOf(output), prompted));
        return this;
    }

    /**
     * @param source the file this recording was compiled from, named in the
     *               recording's first comment line so a reader knows where to
     *               edit (the {@code .cast} itself is generated)
     */
    public Rendered render(final String source) {
        final StringBuilder out = new StringBuilder();
        out.append("{\"version\":3,\"term\":{\"cols\":").append(this.cols)
                .append(",\"rows\":").append(this.rows)
                .append(",\"type\":").append(json(this.term)).append('}');
        if (this.title != null) {
            out.append(",\"title\":").append(json(this.title));
        }
        if (this.idleTimeLimit != null) {
            out.append(",\"idle_time_limit\":").append(plain(this.idleTimeLimit));
        }
        out.append("}\n");
        out.append("# generated from ").append(source)
                .append(" — edit the source, not this recording\n");

        int events = 0;
        double seconds = 0;
        boolean first = true;
        for (final Step step : this.steps) {
            if (step.marker() != null) {
                event(out, this.markerDelay, "m", step.marker());
                seconds += this.markerDelay;
                events++;
                continue;
            }
            if (step.prompted()) {
                final double promptDelay = first ? this.startDelay : this.promptDelay;
                event(out, promptDelay, "o", this.prompt);
                seconds += promptDelay;
                events++;
            }
            first = false;

            for (int i = 0; i < step.input().length(); ) {
                final int codePoint = step.input().codePointAt(i);
                i += Character.charCount(codePoint);
                event(out, this.typingDelay, "o", new String(Character.toChars(codePoint)));
                seconds += this.typingDelay;
                events++;
            }
            event(out, this.preEnterDelay, "o", "\r\n");
            seconds += this.preEnterDelay;
            events++;

            if (!step.output().isEmpty()) {
                event(out, this.postEnterDelay, "o", String.join("\r\n", step.output()) + "\r\n");
                seconds += this.postEnterDelay;
                events++;
            }
        }
        return new Rendered(out.toString(), events, seconds);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Encoding
    // ═══════════════════════════════════════════════════════════════════

    /** One {@code [interval, code, data]} line; intervals are RELATIVE (v3). */
    private static void event(final StringBuilder out, final double interval,
                              final String code, final String data) {
        out.append('[').append(plain(interval)).append(',').append(json(code))
                .append(',').append(json(data)).append("]\n");
    }

    /**
     * A JSON string literal. Control characters become backslash-u escapes (the
     * spec asks for a valid UTF-8 JSON string), which is why a result line
     * carrying ESC sequences survives the round trip into the player.
     */
    private static String json(final String value) {
        final StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    /** A number with at most millisecond precision, trailing zeros trimmed. */
    private static String plain(final double value) {
        final String fixed = String.format(Locale.ROOT, "%.3f", value);
        final String trimmed = fixed.contains(".")
                ? fixed.replaceAll("0+$", "").replaceAll("\\.$", "")
                : fixed;
        return trimmed.isEmpty() ? "0" : trimmed;
    }
}
