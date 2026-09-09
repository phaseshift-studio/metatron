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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.web.webInstSet.HTML_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.MARKDOWN_TID;

/*
 * The .as() markdown ⇄ html instructions of webInstSet. After the
 * docs-pipeline generalization these route through HTMLMarkdownSerializer
 * (flexmark with tables/strikethrough/tasklists/autolink — the same engine the
 * site-html pass uses) instead of the bare flexmark parser ObjMarkdownSerializer
 * used before. A dereferenced .md file is a markdown *rec* (fsSpace MIME-reads
 * via ObjMarkdownSerializer) — NOT a str — so the instructions must normalize
 * non-str inputs (rec → canonical document text) before converting.
 *
 * e.g.  *<mfs:docs/skills/ide/SKILL.md>.as(markdown::T).as(html::T).to(<mfs:test.html>)
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MarkdownHtmlAsInstTest extends AbstractMetatronTest {

    @BeforeAll
    public static void importWebTypes() {
        InstSet.importInstSet(f("/m/web"));
    }

    @SuppressWarnings("unchecked")
    private static <O extends Obj> O eval(final String mtronExpr) {
        return (O) ObjmtronSerializer.singleNoClip().parse(mtronExpr).apply();
    }

    @Test
    public void testMarkdownAsHtmlUsesGeneralizedEngine() {
        // str-tagged markdown (mtron's .as(markdown::T) re-escapes '['/'|'/'~'
        // in raw string literals, so rows stay clear of those characters).
        final Obj heading = eval("\"# Operator docs\".as(markdown::T).as(html::T)");
        assertTrue(heading.isStr(), "md→html must yield a str");
        assertEquals(HTML_TID, heading.tid(), "md→html must tag html::T");
        assertTrue(heading.strValue().contains("<h1>Operator docs</h1>"),
                "md→html must render an h1: " + heading.strValue());

        final Obj link = eval("\"visit https://x.dev\".as(markdown::T).as(html::T)");
        assertTrue(link.strValue().contains("href=\"https://x.dev\""),
                "autolink extension must wrap the bare url: " + link.strValue());
    }

    @Test
    public void testMarkdownRecAsHtmlUsesGeneralizedEngine() {
        // a dereferenced .md file is a markdown *rec* (fsSpace MIME-read) — the
        // shape *<mfs:docs/skills/ide/SKILL.md> yields — so the md→html inst
        // must normalize non-str inputs (rec → canonical markdown text) before
        // rendering, exactly like the old ObjMarkdownSerializer.write(obj)
        // branching did. Without it the renderer receives flattened garbage.
        final Obj html = eval("[type=>doc,out=>[[type=>head,level=>1,out=>[[type=>text,content=>'Hi']]]]].as(markdown::T).as(html::T)");
        assertTrue(html.isStr(), "rec markdown→html must yield a str: " + html);
        assertEquals(HTML_TID, html.tid(), "rec markdown→html must tag html::T");
        assertTrue(html.strValue().contains("<h1>Hi</h1>"),
                "rec markdown must render an h1: " + html.strValue());
    }

    @Test
    public void testHtmlAsMarkdownReverse() {
        final Obj markdown = eval("\"<h1>Hello</h1>\".as(html::T).as(markdown::T)");
        assertTrue(markdown.isStr(), "html→md must yield a str");
        assertEquals(MARKDOWN_TID, markdown.tid(), "html→md must tag markdown::T");
        assertTrue(markdown.strValue().contains("# Hello"),
                "h1 must convert to an atx heading: " + markdown.strValue());

        final Obj table = eval("\"<table><tr><th>a</th></tr><tr><td>1</td></tr></table>\".as(html::T).as(markdown::T)");
        assertTrue(table.strValue().contains("| a |"),
                "html table must convert to a gfm table: " + table.strValue());
    }
}
