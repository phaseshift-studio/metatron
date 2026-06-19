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
    public void testTitleSurvivesRoundTrip() {
        // Bug: title was stored as str but writeHeadElement checked isRec(), so title was lost
        final String html = "<html><head><title>abc</title></head><body></body></html>";
        final Obj rec = ObjHTMLSerializer.parse(html);

        // Verify title is parsed correctly
        assertEquals(str("abc"), rec.asRec().at(uri("html/head/title")));

        // Write back and verify title survives
        final Document doc = serializer.write(rec);
        final String regeneratedHtml = doc.outerHtml();

        // Parse regenerated and verify title is still present
        final Obj rec2 = ObjHTMLSerializer.parse(regeneratedHtml);
        assertEquals(str("abc"), rec2.asRec().at(uri("html/head/title")));
    }

    /**
     * Full round-trip: HTML → mtron → HTML → mtron.
     * Verifies both HTML idempotency (two serializations produce identical output)
     * and mtron structural fidelity (key paths survive both trips).
     */
    @Test
    public void testFullRoundTripComplexHTML() {
        final String originalHtml = """
                <html>
                  <head>
                    <title>My Test Page</title>
                    <script type="text/javascript">console.log('hello');</script>
                    <meta charset="utf-8">
                  </head>
                  <body>
                    <h1>Welcome</h1>
                    <p>This is a <strong>bold</strong> statement.</p>
                    <a href="https://example.com">Visit Example</a>
                    <ul>
                      <li>Item A</li>
                      <li>Item B</li>
                    </ul>
                    <div id="footer">
                      <img src="/logo.png" alt="Logo">
                      <span>Footer text</span>
                    </div>
                  </body>
                </html>
                """;

        // Phase 1: HTML₁ → mtron₁
        final Obj mtron1 = ObjHTMLSerializer.parse(originalHtml);
        assertTrue(mtron1.isRec());
        final Obj htmlObj1 = mtron1.asRec().at(uri(HTML));
        assertFalse(htmlObj1.isNoObj());

        // Verify key paths in mtron₁ (using rec-key paths only, no list indices)
        assertEquals(str("My Test Page"), mtron1.asRec().at(uri("html/head/title")));

        // Phase 2: mtron₁ → HTML₂
        final Document doc2 = serializer.write(mtron1);
        final String html2 = doc2.outerHtml();

        // Phase 3: HTML₂ → mtron₂
        final Obj mtron2 = ObjHTMLSerializer.parse(html2);
        assertTrue(mtron2.isRec());

        // Phase 4: mtron₂ → HTML₃
        final Document doc3 = serializer.write(mtron2);
        final String html3 = doc3.outerHtml();

        // === Assert HTML idempotency: HTML₂ == HTML₃ ===
        assertEquals(html2, html3, "serializer must be idempotent: second write produces identical HTML");

        // === Assert mtron structural fidelity across round-trip ===

        // Title survived
        assertEquals(str("My Test Page"), mtron2.asRec().at(uri("html/head/title")));

        // Head: title + OUT children (script, meta)
        final Obj head1 = findChildByTag(htmlObj1, HEAD);
        assertFalse(head1.isNoObj());
        final Obj headOut1 = head1.asRec().at(uri(OUT));
        assertFalse(headOut1.isNoObj(), "head must have OUT children (script, meta)");
        final int headChildCount1 = headOut1.asLst().elements().toList().size();

        final Obj htmlObj2 = mtron2.asRec().at(uri(HTML));
        final Obj head2 = findChildByTag(htmlObj2, HEAD);
        assertFalse(head2.isNoObj());
        final Obj headOut2 = head2.asRec().at(uri(OUT));
        assertFalse(headOut2.isNoObj(), "head OUT children must survive round-trip");
        assertEquals(headChildCount1, headOut2.asLst().elements().toList().size(),
                "head must have same number of children after round-trip");

        // Body: OUT children survived
        final Obj body1 = findChildByTag(htmlObj1, BODY);
        assertFalse(body1.isNoObj());
        final Obj bodyOut1 = body1.asRec().at(uri(OUT));
        final int bodyChildCount1 = bodyOut1.asLst().elements().toList().size();

        final Obj body2 = findChildByTag(htmlObj2, BODY);
        assertFalse(body2.isNoObj());
        final Obj bodyOut2 = body2.asRec().at(uri(OUT));
        assertFalse(bodyOut2.isNoObj(), "body must have OUT children");
        assertEquals(bodyChildCount1, bodyOut2.asLst().elements().toList().size(),
                "body must have same number of children after round-trip");

        // h1 survived with text
        final Obj h1_1 = findChildByTag(body1, "h1");
        final Obj h1_2 = findChildByTag(body2, "h1");
        assertFalse(h1_1.isNoObj());
        assertFalse(h1_2.isNoObj());
        assertEquals((Object) h1_1.asRec().at(uri(TEXT)), (Object) h1_2.asRec().at(uri(TEXT)));

        // a href survived
        final Obj a1 = findChildByTag(body1, "a");
        final Obj a2 = findChildByTag(body2, "a");
        assertFalse(a1.isNoObj());
        assertFalse(a2.isNoObj());
        final Obj a1Href = a1.asRec().at(uri(HREF));
        final Obj a2Href = a2.asRec().at(uri(HREF));
        assertFalse(a1Href.isNoObj());
        assertFalse(a2Href.isNoObj());
        // href stored as URI by the serializer (see readElement src/href handling)
        assertTrue(a1Href.isUri());
        assertTrue(a2Href.isUri());
        assertEquals(a1Href.uriValue().toString(), a2Href.uriValue().toString());
        assertEquals((Object) a1.asRec().at(uri(TEXT)), (Object) a2.asRec().at(uri(TEXT)));

        // ul with li children survived
        final Obj ul1 = findChildByTag(body1, "ul");
        final Obj ul2 = findChildByTag(body2, "ul");
        assertFalse(ul1.isNoObj());
        assertFalse(ul2.isNoObj());
        assertEquals(ul1.asRec().at(uri(OUT)).asLst().elements().toList().size(),
                ul2.asRec().at(uri(OUT)).asLst().elements().toList().size());

        // div#footer survived with id attribute and children
        final Obj footer1 = findChildByTag(body1, "div");
        final Obj footer2 = findChildByTag(body2, "div");
        assertFalse(footer1.isNoObj());
        assertFalse(footer2.isNoObj());
        assertEquals(str("footer"), footer1.asRec().at(uri("id")));
        assertEquals(str("footer"), footer2.asRec().at(uri("id")));
        assertEquals(footer1.asRec().at(uri(OUT)).asLst().elements().toList().size(),
                footer2.asRec().at(uri(OUT)).asLst().elements().toList().size());

        // img survived with src and alt
        final Obj img1 = findChildByTag(footer1, "img");
        final Obj img2 = findChildByTag(footer2, "img");
        assertFalse(img1.isNoObj());
        assertFalse(img2.isNoObj());
        assertFalse(img1.asRec().at(uri(SRC)).isNoObj());
        assertFalse(img2.asRec().at(uri(SRC)).isNoObj());
        assertEquals(str("Logo"), img1.asRec().at(uri("alt")));
        assertEquals(str("Logo"), img2.asRec().at(uri("alt")));

        // span survived
        final Obj span1 = findChildByTag(footer1, "span");
        final Obj span2 = findChildByTag(footer2, "span");
        assertFalse(span1.isNoObj());
        assertFalse(span2.isNoObj());
        assertEquals((Object) span1.asRec().at(uri(TEXT)), (Object) span2.asRec().at(uri(TEXT)));
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
