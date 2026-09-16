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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The single source of truth for the website's page chrome. Every docs pipeline
 * — {@link MarkdownRunner} (skills), {@link InstSetDocGenerator} (instset), and
 * {@link AsciiDocRunner} (index/start/tractatus) — calls {@link #header} and
 * {@link #footer} with its page's depth, title, and any extra {@code <head>}
 * content, and receives the same look-and-feel header/footer in return.
 *
 * <p>The templates live in {@code docs/website/includes/header.html} and
 * {@code footer.html}. {@code header.html} carries two replacement tokens:
 * {@code {{TITLE}}} (the page {@code <title>}) and {@code {{EXTRA_HEAD}}}
 * (page-specific {@code <head>} content, e.g. an extra stylesheet). Relative
 * asset URLs ({@code images/}, {@code css/}, {@code js/}, {@code highlight/},
 * {@code lib/}) and the top-level page links ({@code index.html},
 * {@code tractatus.html}, {@code start.html}, {@code ./instset/}, {@code ./skills/},
 * {@code ./articles/}, {@code ./console/}) are depth-rewritten so they resolve from
 * a nested page: {@code depth} is the relative path up to the website root
 * ({@code ""}, {@code ".."}, {@code "../.."}, …).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class SiteChrome {

    private static final Path INCLUDES_PATH = Path.of("docs/website/includes");

    private static final String TITLE_TOKEN = "{{TITLE}}";
    private static final String EXTRA_HEAD_TOKEN = "{{EXTRA_HEAD}}";

    /**
     * Relative asset {@code href}/{@code src} that must resolve from a nested page:
     * {@code css/}, {@code images/}, {@code js/}, {@code highlight/}, {@code lib/}
     * (with an optional leading {@code ./}).
     */
    private static final Pattern DOC_LINK_PAT = Pattern.compile("(href|src)=\"(?:\\./)?(images|css|lib|highlight|js)/");

    private SiteChrome() {
    }

    /**
     * The website header: the shared {@code header.html} with its asset/page links
     * depth-rewritten and the {@code {{TITLE}}} / {@code {{EXTRA_HEAD}}} tokens
     * substituted.
     *
     * @param depth     relative path up to the website root ({@code ""} for a
     *                  top-level page, {@code ".."}, {@code "../.."}, …)
     * @param title     the page title (already HTML-escaped by the caller)
     * @param extraHead page-specific {@code <head>} content, or {@code null}/{@code ""}
     */
    public static String header(final String depth, final String title, final String extraHead) {
        return rewrite(read("header.html"), depth)
                .replace(TITLE_TOKEN, title)
                .replace(EXTRA_HEAD_TOKEN, extraHead == null ? "" : extraHead);
    }

    /**
     * The website footer: the shared {@code footer.html} with its asset/page links
     * depth-rewritten.
     */
    public static String footer(final String depth) {
        return rewrite(read("footer.html"), depth);
    }

    /**
     * Depth-rewrite the relative asset and top-level page links in {@code content}.
     * An empty depth leaves them relative (unchanged); otherwise each is prefixed
     * with {@code depth + "/"}.
     */
    private static String rewrite(final String content, final String depth) {
        final String pre = depth == null || depth.isEmpty() ? "" : depth + "/";
        return DOC_LINK_PAT.matcher(content).replaceAll("$1=\"" + pre + "$2/")
                .replace("href=\"index.html\"", "href=\"" + pre + "index.html\"")
                .replace("href=\"tractatus.html\"", "href=\"" + pre + "tractatus.html\"")
                .replace("href=\"start.html\"", "href=\"" + pre + "start.html\"")
                .replace("href=\"./instset/", "href=\"" + pre + "instset/")
                .replace("href=\"./skills/", "href=\"" + pre + "skills/")
                .replace("href=\"./articles/", "href=\"" + pre + "articles/")
                .replace("href=\"./console/", "href=\"" + pre + "console/")
                .replace("location.href='./articles/", "location.href='" + pre + "articles/")
                .replace("location.href='tractatus.html'", "location.href='" + pre + "tractatus.html'")
                .replace("location.href='index.html'", "location.href='" + pre + "index.html'")
                .replace("location.href='./instset/", "location.href='" + pre + "instset/")
                .replace("location.href='./skills/", "location.href='" + pre + "skills/");
    }

    private static String read(final String name) {
        try {
            return Files.readString(INCLUDES_PATH.resolve(name));
        } catch (final IOException e) {
            return "";
        }
    }
}
