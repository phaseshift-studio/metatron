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

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjHTMLSerializerTest extends AbstractSerializerTest<Document> {

    private final ObjHTMLSerializer serializer = ObjHTMLSerializer.single();

    public ObjHTMLSerializerTest() {
        super(new ObjHTMLSerializer());
    }

    @Override
    public void testSerializeDeserializeObj(final String objString) {
        // do nothing
    }


    /**
     * Helper method to find a child element by tag name in the hybrid structure.
     * For singular elements (head, body), they are direct keys and return recs.
     * Title is a direct key under head but returns a string.
     * For other elements, they are in the children list with a tag field.
     */
    private Obj findChildByTag(Obj parent, String tagName) {
        if (!parent.isRec()) return noobj();

        // Check if it's a direct key (head, body, title)
        if (tagName.equals(HEAD) || tagName.equals(BODY) || tagName.equals(TITLE)) {
            final Obj directChild = parent.asRec().at(uri(tagName));
            if (!directChild.isNoObj()) {
                return directChild;
            }
        }

        // Otherwise, search in children list
        final Obj children = parent.asRec().at(uri(OUT));
        if (children.isNoObj() || !children.isLst()) return noobj();

        for (Obj child : children.asLst().elements().toList()) {
            if (child.isRec() && child.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals(tagName)) {
                return child;
            }
        }
        return noobj();
    }

    @Test
    public void testWebPageParsing() {
        final ObjHTMLSerializer t = new ObjHTMLSerializer();
        final Rec page = (Rec) t.translatePage(new File("./docs/website/images/ansi/metatron-character.html"));
        LOG.info("%s", page);
        LOG.info("%s", t.write(page));
    }

    @Test
    public void testSimpleHTML() {
        final String html = "<html><body><h1>Hello World</h1></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        assertTrue(rec.isRec());
        // Root has html as a key: [html => [...]]
        final Obj htmlObj = rec.asRec().at(uri(HTML));
        assertFalse(htmlObj.isNoObj());
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
    }

    @Test
    public void testHTMLWithAttributes() {
        final String html = "<html><body><div id=\"test\" class=\"container\">Content</div></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);
        assertTrue(rec.isRec());
        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj div = findChildByTag(body, "div");
        assertFalse(div.isNoObj());
        assertEquals(str("test"), div.asRec().at(uri("id")));
        assertEquals(str("container"), div.asRec().at(uri("class")));
    }

    @Test
    public void testHTMLWithText() {
        final String html = "<html><body><p>This is a paragraph.</p></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        assertTrue(rec.isRec());
        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj p = findChildByTag(body, "p");
        assertFalse(p.isNoObj());
        assertEquals(str("This is a paragraph."), p.asRec().at(uri("text")));
    }

    @Test
    public void testHTMLWithLink() {
        final String html = "<html><body><a href=\"https://example.com\">Link</a></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj a = findChildByTag(body, "a");
        assertFalse(a.isNoObj());
        assertTrue(a.asRec().at(uri("href")).isUri() || a.asRec().at(uri("href")).strValue().equals("https://example.com"));
        assertEquals(str("Link"), a.asRec().at(uri("text")));
    }

    @Test
    public void testHTMLWithImage() {
        final String html = "<html><body><img src=\"/image.png\" alt=\"Test Image\"></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj img = findChildByTag(body, "img");
        assertFalse(img.isNoObj());
        assertTrue(img.asRec().at(uri("src")).isUri() || img.asRec().at(uri("src")).strValue().equals("/image.png"));
        assertEquals(str("Test Image"), img.asRec().at(uri("alt")));
    }

    @Test
    public void testNestedHTML() {
        final String html = "<html><body><div><p><span>Nested</span></p></div></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj div = findChildByTag(body, "div");
        assertFalse(div.isNoObj());
        final Obj p = findChildByTag(div, "p");
        assertFalse(p.isNoObj());
        final Obj span = findChildByTag(p, "span");
        assertFalse(span.isNoObj());
        assertEquals(str("Nested"), span.asRec().at(uri("text")));
    }

    @Test
    public void testHTMLList() {
        final String html = "<html><body><ul><li>Item 1</li><li>Item 2</li></ul></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj ul = findChildByTag(body, "ul");
        assertFalse(ul.isNoObj());
    }

    @Test
    public void testRoundTripSimple() {
        final String html = "<html><body><h1>Test</h1></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);
        final Document doc = serializer.write(rec);
        final Obj rec2 = serializer.read(doc);

        assertNotNull(doc);
        assertTrue(rec2.isRec());
    }

    @Test
    public void testRoundTripWithAttributes() {
        final String html = "<html><body><div id=\"main\" class=\"container\">Content</div></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);
        final Document doc = serializer.write(rec);
        final Obj rec2 = serializer.read(doc);

        final Obj htmlObj = rec2.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj div = findChildByTag(body, "div");
        assertFalse(div.isNoObj());
        assertEquals(str("main"), div.asRec().at(uri("id")));
        assertEquals(str("container"), div.asRec().at(uri("class")));
    }

    @Test
    public void testRoundTripWithText() {
        final String html = "<html><body><p>Hello World</p></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);
        final Document doc = serializer.write(rec);
        final Obj rec2 = serializer.read(doc);

        final Obj htmlObj = rec2.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj p = findChildByTag(body, "p");
        assertFalse(p.isNoObj());
        assertEquals(str("Hello World"), p.asRec().at(uri("text")));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<html><body><h1>Heading 1</h1></body></html>|h1|Heading 1",
            "<html><body><h2>Heading 2</h2></body></html>|h2|Heading 2",
            "<html><body><p>Paragraph</p></body></html>|p|Paragraph",
            "<html><body><span>Span</span></body></html>|span|Span",
            "<html><body><div>Division</div></body></html>|div|Division"
    }, delimiter = '|')
    public void testHTMLElements(String html, String tagName, String expectedText) {
        final Obj rec = ObjHTMLSerializer.parse(html);
        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj element = findChildByTag(body, tagName);
        assertFalse(element.isNoObj());
        assertEquals(str(expectedText), element.asRec().at(uri("text")));
    }

    @Test
    public void testEmptyHTML() {
        final String html = "<html></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);
        assertTrue(rec.isRec());
    }

    @Test
    public void testHTMLWithMultipleChildren() {
        final String html = "<html><body><div><p>First</p><p>Second</p><p>Third</p></div></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj div = findChildByTag(body, "div");
        assertFalse(div.isNoObj());
    }

    @Test
    public void testHTMLTable() {
        final String html = "<html><body><table><tr><td>Cell 1</td><td>Cell 2</td></tr></table></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj table = findChildByTag(body, "table");
        assertFalse(table.isNoObj());
    }

    @Test
    public void testHTMLForm() {
        final String html = "<html><body><form action=\"/submit\" method=\"post\"><input type=\"text\" name=\"username\"></form></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        final Obj htmlObj = rec.asRec().at(uri(HTML));
        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());
        final Obj form = findChildByTag(body, "form");
        assertFalse(form.isNoObj());
        assertEquals(str("/submit"), form.asRec().at(uri("action")));
        assertEquals(str("post"), form.asRec().at(uri("method")));
    }

    @Test
    public void testComplexHTML() {
        final String html = """
                            <html>
                            <head><title>Test Page</title></head>
                            <body>
                                <div id="header">
                                    <h1>Welcome</h1>
                                </div>
                                <div id="content">
                                    <p>This is a test.</p>
                                    <a href="https://example.com">Link</a>
                                </div>
                            </body>
                            </html>
                            """;

        final Obj rec = ObjHTMLSerializer.parse(html);
        assertTrue(rec.isRec());

        // Root has html as a key: [html => [...]]
        final Obj htmlObj = rec.asRec().at(uri(HTML));
        assertFalse(htmlObj.isNoObj());

        final Obj head = findChildByTag(htmlObj, HEAD);
        assertFalse(head.isNoObj());

        final Obj body = findChildByTag(htmlObj, BODY);
        assertFalse(body.isNoObj());

        final Obj title = findChildByTag(head.asRec(), TITLE);
        assertNotNull(title);
        assertEquals(str("Test Page"), title);
    }

    @Test
    public void testRoundTripHTMLString() {
        final String originalHtml = "<html><head><title>Test</title></head><body><h1>Hello</h1><p>This is <strong>bold</strong> text.</p></body></html>";
        // Parse HTML string to Rec
        final Obj htmlRec = ObjHTMLSerializer.parse(originalHtml);
        assertEquals(str("Test"), htmlRec.asRec().at(uri("html/head/title")));
        // Convert back to Document
        final Document doc = serializer.write(htmlRec);
        // Get HTML string
        final String regeneratedHtml = doc.outerHtml();

        // Parse both to compare structure (not exact string match due to formatting)
        final Obj originalRec = ObjHTMLSerializer.parse(originalHtml);
        final Obj regeneratedRec = ObjHTMLSerializer.parse(regeneratedHtml);

        // Verify both have the same structure
        final Obj originalHtmlObj = originalRec.asRec().at(uri(HTML));
        final Obj regeneratedHtmlObj = regeneratedRec.asRec().at(uri(HTML));
        assertFalse(originalHtmlObj.isNoObj());
        assertFalse(regeneratedHtmlObj.isNoObj());

        // Verify body exists in both
        final Obj originalBody = findChildByTag(originalHtmlObj, BODY);
        final Obj regeneratedBody = findChildByTag(regeneratedHtmlObj, BODY);
        assertFalse(originalBody.isNoObj());
        assertFalse(regeneratedBody.isNoObj());

        // Verify h1 exists in both
        final Obj originalH1 = findChildByTag(originalBody, "h1");
        final Obj regeneratedH1 = findChildByTag(regeneratedBody, "h1");
        assertFalse(originalH1.isNoObj());
        assertFalse(regeneratedH1.isNoObj());
        assertEquals(str("Hello"), originalH1.asRec().at(uri("text")));
        assertEquals(str("Hello"), regeneratedH1.asRec().at(uri("text")));
    }
}
