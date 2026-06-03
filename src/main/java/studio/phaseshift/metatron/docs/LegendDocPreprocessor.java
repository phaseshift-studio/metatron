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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans an adoc file for {@code [discrete]} section headings, auto-generates
 * {@code [[sec-*]]} anchors for headings that lack them, and injects a
 * {@code [.legend-index]} sidebar block — a compact, floating table-of-contents
 * box — after the first {@code ==} heading.
 *
 * <p>Runs BEFORE {@link MtronDocPreprocessor} so it operates on raw adoc.</p>
 *
 * <h3>Heading detection</h3>
 * Matches lines of the form:
 * <pre>
 * [discrete]
 * [[optional-anchor]]
 * === Section Title
 * </pre>
 * The first {@code ==} heading is treated as the document title and excluded
 * from the legend.
 *
 * <h3>Anchor generation</h3>
 * If a heading already carries a {@code [[anchor]]} it is reused.
 * Otherwise an ID is derived from the heading text:
 * lowercased, non-alphanumerics stripped, spaces → hyphens, prefixed with
 * {@code sec-}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class LegendDocPreprocessor {

    /**
     * Matches a discrete heading with an optional inline anchor.
     * Group 1 = existing anchor id (nullable),
     * Group 2 = heading level (=+),
     * Group 3 = heading text.
     */
    private static final Pattern HEADING = Pattern.compile(
            "\\[discrete]\\R(?:\\[\\[([^\\]]+)\\]\\]\\R)?(=+)\\s+(.+)");

    /** Detects an existing legend block (AsciiDoc or HTML) — skip if present. */
    private static final Pattern EXISTING_LEGEND = Pattern.compile(
            "legend-index");

    /**
     * Process an adoc string: add anchors, inject legend.
     *
     * @param adocText raw adoc source
     * @return adoc with legend index and anchors
     */
    public String process(final String adocText) {
        // ── 0. Skip if a legend block already exists ──────────────────
        if (EXISTING_LEGEND.matcher(adocText).find()) {
            return adocText;
        }

        // ── 1. Collect headings ───────────────────────────────────────
        final List<Heading> headings = new ArrayList<>();
        final Matcher collectMatcher = HEADING.matcher(adocText);
        while (collectMatcher.find()) {
            final String existingAnchor = collectMatcher.group(1);
            final String level = collectMatcher.group(2);
            final String text = collectMatcher.group(3).strip();
            final String anchor = existingAnchor != null
                    ? existingAnchor
                    : generateAnchorId(text);
            headings.add(new Heading(anchor, text, level,
                    existingAnchor == null));
        }

        if (headings.isEmpty()) return adocText;

        // ── 2. Build legend as raw HTML passthrough block ──────────────
        // Skip the first == (document title); include everything else.
        final StringBuilder legend = new StringBuilder();
        legend.append("\n++++\n<div class=\"legend-index\">\n<ul>\n");
        boolean skippedTitle = false;
        for (final Heading h : headings) {
            if (!skippedTitle && h.level.equals("==")) {
                skippedTitle = true;
                continue;
            }
            legend.append("<li><a href=\"#").append(h.anchor)
                    .append("\" onclick=\"var t=document.getElementById('")
                    .append(h.anchor)
                    .append("');if(t){var c=t.closest('.collapse');if(c&&!c.classList.contains('show')){var bs=bootstrap.Collapse.getInstance(c)||new bootstrap.Collapse(c,{toggle:false});bs.show();c.addEventListener('shown.bs.collapse',function f(){c.removeEventListener('shown.bs.collapse',f);t.scrollIntoView({behavior:'smooth',block:'start'});});}else{t.scrollIntoView({behavior:'smooth',block:'start'});}}return false;\">")
                    .append(h.text).append("</a></li>\n");
        }
        legend.append("</ul>\n</div>\n++++\n");

        // ── 3. Single-pass rewrite via appendReplacement (no offset bugs) ──
        final Matcher rewriteMatcher = HEADING.matcher(adocText);
        final StringBuilder result = new StringBuilder();
        int headingIdx = 0;
        boolean legendInjected = false;

        while (rewriteMatcher.find()) {
            final Heading h = headings.get(headingIdx++);

            // Reconstruct the heading block
            final StringBuilder replacement = new StringBuilder();
            replacement.append("[discrete]\n[[").append(h.anchor).append("]]\n")
                    .append(h.level).append(' ').append(h.text);

            // Inject legend after the first == heading
            if (!legendInjected && h.level.equals("==")) {
                replacement.append('\n').append(legend);
                legendInjected = true;
            }

            rewriteMatcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacement.toString()));
        }
        rewriteMatcher.appendTail(result);

        return result.toString();
    }

    /**
     * Derive a clean anchor ID from heading text.
     * {@code "type predicates (pred)" → "sec-type-predicates-pred"}
     */
    static String generateAnchorId(final String text) {
        return "sec-" + text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")   // strip non-alnum except space/hyphen
                .replaceAll("\\s+", "-")           // spaces → hyphens
                .replaceAll("-{2,}", "-")          // collapse runs
                .replaceAll("(^-|-$)", "");        // trim leading/trailing hyphens
    }

    // ── Internal data class ──────────────────────────────────────────

    private record Heading(String anchor, String text, String level,
                           boolean needsAnchor) {}
}
