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

package studio.phaseshift.metatron.isa.web.parser;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.jekyll.front.matter.JekyllFrontMatterExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_SERIALIZER_TID;

/**
 * The first {@link AbstractSerializer} whose two sides are <em>not</em>
 * {@code Obj}: a pure document-format pair mapping markdown text ⇄ html text —
 * {@code Serializer<String, String>} with {@code A = markdown} (the canonical
 * authored doc; "markdown is the source of truth") and {@code B = html} (the
 * derived rendering). It generalizes the docs-pipeline renderer that used to
 * live in {@code docs/SkillHtmlRenderer} (now removed) to <em>any</em> markdown
 * document, in both directions.
 *
 * <p><strong>write(markdown) → html.</strong> Flexmark renders the markdown with
 * the tables, GFM strikethrough, GFM task-list, autolink and jekyll-front-matter
 * extensions (a leading YAML {@code ---…---} block is consumed as front matter,
 * not rendered). This is
 * the same engine and options the skill-doc website renderer used, so its
 * character-fidelity guarantee is inherited: multi-character operators such as
 * {@code =>}, {@code -<}, {@code >=}, {@code <=} and {@code >>=} pass through
 * verbatim — flexmark only HTML-escapes them when required and never
 * typographically "compresses" them into single Unicode look-alikes
 * ({@code ≥}, {@code ≤}, {@code ⇒}, …). A regression test pins this down.
 *
 * <p><strong>read(html) → markdown.</strong> Flexmark's official html→md
 * converter ({@link FlexmarkHtmlConverter}) runs on the same flexmark markdown
 * model as the write side, so both directions agree on what markdown is. Note
 * the conversion is lossy by nature (html carries layout that markdown has no
 * syntax for); it targets html that itself was written as markdown, e.g.
 * scraped skill pages.
 *
 * <p>Like every serializer, instances are metatron {@code Rec}s typed
 * {@code /m/web/serializer/html_markdown}. mtron reaches the pair through the
 * {@code .as()} instructions of webInstSet
 * ({@code markdown::T ⇄ html::T}); space/CONST registration is still pending.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class HTMLMarkdownSerializer extends AbstractSerializer<String, String> {

    public static final fURI HTML_MARKDOWN_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("html_markdown");
    public static final fURI HTML_MARKDOWN_SERIALIZER_VID = HTML_MARKDOWN_SERIALIZER_TID;

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;
    private static final FlexmarkHtmlConverter HTML_TO_MD;
    private static final HTMLMarkdownSerializer INSTANCE = new HTMLMarkdownSerializer();

    static {
        final MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create(),
                JekyllFrontMatterExtension.create()));
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
        // html → markdown: ATX headings (#, ##, ...), '-'-bulleted lists, and a
        // '---' thematic break keep the output in the house style the md side
        // produces (the docs write ATX + '-' bullets + '---' rules).
        final MutableDataSet htmlOptions = new MutableDataSet();
        htmlOptions.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);
        htmlOptions.set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '-');
        htmlOptions.set(FlexmarkHtmlConverter.THEMATIC_BREAK, "---");
        HTML_TO_MD = FlexmarkHtmlConverter.builder(htmlOptions).build();
    }

    public static HTMLMarkdownSerializer single() {
        return INSTANCE;
    }

    public HTMLMarkdownSerializer() {
        super(HTML_MARKDOWN_SERIALIZER_TID, HTML_MARKDOWN_SERIALIZER_VID);
    }

    /**
     * Render a markdown document (A) into an html fragment (B).
     */
    @Override
    public String write(final String markdown) throws MTronException {
        if (null == markdown || markdown.isEmpty()) return "";
        return RENDERER.render(PARSER.parse(markdown));
    }

    /**
     * Convert an html fragment (B) back into a markdown document (A).
     */
    @Override
    public String read(final String html) throws MTronException {
        if (null == html || html.isEmpty()) return "";
        return HTML_TO_MD.convert(html);
    }

    /**
     * Markdown → html convenience (stateless; delegates to {@link #single()}).
     */
    public static String toHTML(final String markdown) {
        return single().write(markdown);
    }

    /**
     * Html → markdown convenience (stateless; delegates to {@link #single()}).
     */
    public static String toMarkdown(final String html) {
        return single().read(html);
    }

    @Override
    public fURI vid() {
        return HTML_MARKDOWN_SERIALIZER_VID;
    }
}
