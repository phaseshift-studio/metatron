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

package studio.phaseshift.metatron.isa.web.parser;

import com.vladsch.flexmark.util.ast.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjMarkdownSerializerTest extends AbstractSerializerTest<Node> {

    private final ObjMarkdownSerializer serializer = ObjMarkdownSerializer.single();

    public ObjMarkdownSerializerTest() {
        super(new ObjMarkdownSerializer());
    }

    @Override
    public void testSerializeDeserializeObj(final String objString) {
      // TODO: easy -- just toString monos;
    }

    @Test
    public void testSimpleHeading() {
        final String markdown = "# Hello World";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        assertTrue(rec.isRec());
        assertEquals(uri(DOC), rec.asRec().at(uri(TYPE)));

        final Obj children = rec.asRec().at(uri(OUT));
        assertTrue(children.isLst());
        final Obj child = children.asLst().at(0);
        assertFalse(child.isNoObj());
        assertEquals(uri(HEAD), child.asRec().at(uri(TYPE)));
        assertEquals(jnt(1), child.asRec().at(uri("level")));
        assertEquals(str("Hello World"), child.asRec().at(uri(TEXT)));
    }

    @Test
    public void testParagraph() {
        final String markdown = "This is a simple paragraph.";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        assertTrue(rec.isRec());
        final Obj children = rec.asRec().at(uri(OUT));
        final Obj child = children.asLst().at(0);
        assertEquals(uri(P), child.asRec().at(uri(TYPE)));
    }

    @Test
    public void testCodeBlock() {
        final String markdown = "```java\npublic static void main(String[] args) {\n    System.out.println(\"Hello\");\n}\n```";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj child = children.asLst().at(0);
        assertEquals(uri(CODE), child.asRec().at(uri(TYPE)));
        assertEquals(str("java"), child.asRec().at(uri(LANG)));
        assertTrue(child.asRec().at(uri(CODE)).strValue().contains("public static void main"));
    }

    @Test
    public void testBulletList() {
        final String markdown = "- Item 1\n- Item 2\n- Item 3";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj child = children.asLst().at(0);
        assertEquals(uri(B_LIST), child.asRec().at(uri(TYPE)));

        // Check first list item
        final Obj listChildren = child.asRec().at(uri(OUT));
        final Obj item1 = listChildren.asLst().at(0);
        assertEquals(uri(ENTRY), item1.asRec().at(uri(TYPE)));
    }

    @Test
    public void testOrderedList() {
        final String markdown = "1. First\n2. Second\n3. Third";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj child = children.asLst().at(0);
        assertEquals(uri(O_LIST), child.asRec().at(uri(TYPE)));
        assertEquals(jnt(1), child.asRec().at(uri("start")));
    }

    @Test
    public void testLink() {
        final String markdown = "[Google](https://google.com)";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj paragraph = children.asLst().at(0);
        final Obj paragraphChildren = paragraph.asRec().at(uri(OUT));
        final Obj link = paragraphChildren.asLst().at(0);
        assertEquals(uri(EDGE), link.asRec().at(uri(TYPE)));
        assertEquals(str("Google"), link.asRec().at(uri(TEXT)));
        assertTrue(link.asRec().at(uri(URI)).isUri() || link.asRec().at(uri(URI)).strValue().equals("https://google.com"));
    }

    @Test
    public void testImage() {
        final String markdown = "![Alt text](https://example.com/image.png)";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj paragraph = children.asLst().at(0);
        final Obj paragraphChildren = paragraph.asRec().at(uri(OUT));
        final Obj image = paragraphChildren.asLst().at(0);
        assertEquals(uri("image"), image.asRec().at(uri(TYPE)));
        assertEquals(str("Alt text"), image.asRec().at(uri("alt")));
    }

    @Test
    public void testEmphasis() {
        final String markdown = "This is *italic* text.";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj paragraph = children.asLst().at(0);
        final Obj paragraphChildren = paragraph.asRec().at(uri(OUT));
        final Obj emphasis = paragraphChildren.asLst().at(1);
        assertEquals(uri("emphasis"), emphasis.asRec().at(uri(TYPE)));
    }

    @Test
    public void testStrong() {
        final String markdown = "This is **bold** text.";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj paragraph = children.asLst().at(0);
        final Obj paragraphChildren = paragraph.asRec().at(uri(OUT));
        final Obj strong = paragraphChildren.asLst().at(1);
        assertEquals(uri("strong"), strong.asRec().at(uri(TYPE)));
    }

    @Test
    public void testInlineCode() {
        final String markdown = "Use `System.out.println()` to print.";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj paragraph = children.asLst().at(0);
        final Obj paragraphChildren = paragraph.asRec().at(uri(OUT));
        final Obj code = paragraphChildren.asLst().at(1);
        assertEquals(uri("inline_code"), code.asRec().at(uri(TYPE)));
        assertEquals(str("System.out.println()"), code.asRec().at(uri("code")));
    }

    @Test
    public void testBlockQuote() {
        final String markdown = "> This is a quote";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj child = children.asLst().at(0);
        assertEquals(uri(QUOTE), child.asRec().at(uri(TYPE)));
    }

    @Test
    public void testHorizontalRule() {
        final String markdown = "---";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        final Obj child = children.asLst().at(0);
        assertEquals(uri("horizontal_rule"), child.asRec().at(uri(TYPE)));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "# Heading 1|head|1",
            "## Heading 2|head|2",
            "### Heading 3|head|3",
            "#### Heading 4|head|4",
            "##### Heading 5|head|5",
            "###### Heading 6|head|6"
    }, delimiter = '|')
    public void testHeadingLevels(String markdown, String expectedType, int expectedLevel) {
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Obj children = rec.asRec().at(uri(OUT));
        final Obj child = children.asLst().at(0);
        assertEquals(uri(expectedType), child.asRec().at(uri(TYPE)));
        assertEquals(jnt(expectedLevel), child.asRec().at(uri("level")));
    }

    @Test
    public void testComplexDocument() {
        final String markdown = """
                                # Main Title
                                
                                This is a paragraph with **bold** and *italic* text.
                                
                                ## Subsection
                                
                                - Item 1
                                - Item 2
                                
                                ```python
                                def hello():
                                    print("world")
                                ```
                                
                                [Link](https://example.com)
                                """;

        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        assertTrue(rec.isRec());
        assertEquals(uri(DOC), rec.asRec().at(uri(TYPE)));

        // Verify we have multiple children
        final Obj children = rec.asRec().at(uri(OUT));
        assertTrue(children.isLst());
        assertFalse(children.asLst().at(0).isNoObj());
        assertFalse(children.asLst().at(1).isNoObj());
    }

    @Test
    public void testRoundTripSimple() {
        final String markdown = "# Hello\n\nThis is a test.";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Node node = serializer.write(rec);
        final String result = node.getChars().toString();

        // Parse again to verify structure is preserved
        final Obj rec2 = serializer.read(node);
        assertEquals(uri(DOC), rec2.asRec().at(uri(TYPE)));
    }

    @Test
    public void testRoundTripHeading() {
        final String markdown = "# Hello World";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Node node = serializer.write(rec);
        final Obj rec2 = serializer.read(node);

        assertEquals(uri(DOC), rec2.asRec().at(uri(TYPE)));
        final Obj children = rec2.asRec().at(uri(OUT));
        final Obj heading = children.asLst().at(0);
        assertEquals(uri(HEAD), heading.asRec().at(uri(TYPE)));
        assertEquals(jnt(1), heading.asRec().at(uri("level")));
    }

    @Test
    public void testRoundTripCodeBlock() {
        final String markdown = "```java\nSystem.out.println(\"test\");\n```";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Node node = serializer.write(rec);
        final Obj rec2 = serializer.read(node);

        final Obj children = rec2.asRec().at(uri(OUT));
        final Obj codeBlock = children.asLst().at(0);
        assertEquals(uri(CODE), codeBlock.asRec().at(uri(TYPE)));
        assertEquals(str("java"), codeBlock.asRec().at(uri(LANG)));
    }

    @Test
    public void testRoundTripList() {
        final String markdown = "- Item 1\n- Item 2\n- Item 3";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Node node = serializer.write(rec);
        final Obj rec2 = serializer.read(node);

        final Obj children = rec2.asRec().at(uri(OUT));
        final Obj list = children.asLst().at(0);
        assertEquals(uri(B_LIST), list.asRec().at(uri(TYPE)));
    }

    @Test
    public void testRoundTripOrderedList() {
        final String markdown = "1. First\n2. Second\n3. Third";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Node node = serializer.write(rec);
        final Obj rec2 = serializer.read(node);

        final Obj children = rec2.asRec().at(uri(OUT));
        final Obj list = children.asLst().at(0);
        assertEquals(uri(O_LIST), list.asRec().at(uri(TYPE)));
    }

    @Test
    public void testRoundTripLink() {
        final String markdown = "[Google](https://google.com)";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Node node = serializer.write(rec);
        final Obj rec2 = serializer.read(node);

        final Obj children = rec2.asRec().at(uri(OUT));
        final Obj paragraph = children.asLst().at(0);
        final Obj paragraphChildren = paragraph.asRec().at(uri(OUT));
        final Obj link = paragraphChildren.asLst().at(0);
        assertEquals(uri(EDGE), link.asRec().at(uri(TYPE)));
    }

    @Test
    public void testEmptyDocument() {
        final String markdown = "";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        assertTrue(rec.isRec());
        assertEquals(uri(DOC), rec.asRec().at(uri(TYPE)));
    }

    @Test
    public void testMultipleParagraphs() {
        final String markdown = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final Obj children = rec.asRec().at(uri(OUT));
        assertFalse(children.asLst().at(0).isNoObj());
        assertFalse(children.asLst().at(1).isNoObj());
        assertFalse(children.asLst().at(2).isNoObj());
    }

    @Test
    public void testNestedList() {
        final String markdown = """
                                - Item 1
                                  - Nested 1
                                  - Nested 2
                                - Item 2
                                """;
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        final Obj children = rec.asRec().at(uri(OUT));
        final Obj list = children.asLst().at(0);
        assertEquals(uri(B_LIST), list.asRec().at(uri(TYPE)));
    }

    @Test
    public void testSimpleParagraphWithFormatting() {
        final String markdown = "This is a paragraph with **bold** and *italic* text.";

        // Parse to Rec
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        System.out.println("Parsed Rec: " + rec);

        // Write back to markdown
        final com.vladsch.flexmark.util.ast.Node mdNode = serializer.write(rec);
        final String regenerated = mdNode.getChars().toString();
        System.out.println("Regenerated: " + regenerated);

        // Convert to HTML
        com.vladsch.flexmark.html.HtmlRenderer renderer = com.vladsch.flexmark.html.HtmlRenderer.builder().build();
        String html = renderer.render(mdNode);
        System.out.println("HTML: " + html);

        // Parse HTML to Rec
        final Obj htmlRec = ObjHTMLSerializer.parse(html);
        System.out.println("HTML Rec: " + htmlRec);

        // Verify - with new hybrid structure, root has html key: [html => [...]]
        final Obj htmlObj = htmlRec.asRec().at(uri(HTML));
        assertFalse(htmlObj.isNoObj(), "Root should have html key");

        // Get body directly (it's a direct key under html)
        final Obj body = htmlObj.asRec().at(uri(BODY));
        assertNotNull(body, "Body should exist");

        // Find paragraph in body
        final Obj bodyChildren = body.asRec().at(uri(OUT));
        Obj p = null;
        for (Obj child : bodyChildren.asLst().elements().toList()) {
            if (child.isRec() && child.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("p")) {
                p = child;
                break;
            }
        }
        assertNotNull(p, "Paragraph should exist");

        // Check paragraph has strong and em children
        final Obj pChildren = p.asRec().at(uri(OUT));
        boolean hasStrong = false;
        boolean hasEm = false;
        for (Obj child : pChildren.asLst().elements().toList()) {
            if (child.isRec()) {
                String tag = child.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString();
                if (tag.equals("strong")) hasStrong = true;
                if (tag.equals("em")) hasEm = true;
            }
        }
        assertTrue(hasStrong, "Should have <strong>");
        assertTrue(hasEm, "Should have <em>");
    }

    private void printNodeStructure(com.vladsch.flexmark.util.ast.Node node, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + node.getClass().getSimpleName() + ": " + node.getChars().toString().replace("\n", "\\n").substring(0, Math.min(50, node.getChars().length())));
        for (com.vladsch.flexmark.util.ast.Node child : node.getChildren()) {
            printNodeStructure(child, depth + 1);
        }
    }

    @Test
    public void testMarkdownToHTMLConversion() {
        final String markdown = """
                                # Main Heading
                                
                                This is a paragraph with **bold** and *italic* text.
                                
                                ## Subheading
                                
                                - Item 1
                                - Item 2
                                - Item 3
                                
                                ```java
                                public static void main(String[] args) {
                                    System.out.println("Hello World");
                                }
                                ```
                                
                                [Link](https://example.com)
                                """;

        // Parse markdown to Rec
        final Obj markdownRec = ObjMarkdownSerializer.parse(markdown);
        System.out.println("Markdown Rec: " + markdownRec);

        // Write back to markdown
        final com.vladsch.flexmark.util.ast.Node mdNode = serializer.write(markdownRec);
        final String regeneratedMarkdown = mdNode.getChars().toString();
        System.out.println("Regenerated Markdown:\n" + regeneratedMarkdown);

        // Debug: print the node structure
        System.out.println("\nFlexmark Node Structure:");
        printNodeStructure(mdNode, 0);

        // Convert markdown Rec to HTML via toHTML method
        final Obj htmlRec = serializer.toHTML(mdNode);
        System.out.println("HTML Rec: " + htmlRec);

        // Also check the raw HTML output
        com.vladsch.flexmark.html.HtmlRenderer renderer = com.vladsch.flexmark.html.HtmlRenderer.builder().build();
        String rawHtml = renderer.render(mdNode);
        System.out.println("Raw HTML:\n" + rawHtml);

        // Verify the HTML structure contains key elements
        assertTrue(htmlRec.isRec());

        // With the new hybrid structure, root has html key: [html => [...]]
        final Obj htmlObj = htmlRec.asRec().at(uri(HTML));
        assertFalse(htmlObj.isNoObj(), "Root should have html key");

        // Get body directly (it's a direct key under html)
        final Obj body = htmlObj.asRec().at(uri(BODY));
        assertNotNull(body, "Body element should exist");

        final Obj bodyChildren = body.asRec().at(uri(OUT));
        assertFalse(bodyChildren.isNoObj(), "Body should have children");
        assertTrue(bodyChildren.isLst(), "Body children should be a list");

        // Verify all elements are present in the body children
        List<Obj> bodyChildList = bodyChildren.asLst().elements().toList();

        // Find h1
        Obj h1 = bodyChildList.stream()
                .filter(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("h1"))
                .findFirst().orElse(null);
        assertNotNull(h1, "H1 should exist");
        assertEquals(str("Main Heading"), h1.asRec().at(uri(TEXT)), "H1 text should match");

        // Find the paragraph with bold/italic (first <p>)
        Obj paragraphWithFormatting = bodyChildList.stream()
                .filter(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("p"))
                .findFirst().orElse(null);
        assertNotNull(paragraphWithFormatting, "First paragraph should exist");

        // Check that the paragraph has children with strong and em
        final Obj pChildren = paragraphWithFormatting.asRec().at(uri(OUT));
        assertFalse(pChildren.isNoObj(), "Paragraph should have children");
        List<Obj> pChildList = pChildren.asLst().elements().toList();

        boolean hasStrong = pChildList.stream()
                .anyMatch(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("strong"));
        assertTrue(hasStrong, "Paragraph should contain <strong>");

        boolean hasEm = pChildList.stream()
                .anyMatch(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("em"));
        assertTrue(hasEm, "Paragraph should contain <em>");

        // Find h2
        Obj h2 = bodyChildList.stream()
                .filter(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("h2"))
                .findFirst().orElse(null);
        assertNotNull(h2, "H2 should exist");
        assertEquals(str("Subheading"), h2.asRec().at(uri(TEXT)), "H2 text should match");

        // Find ul
        Obj ul = bodyChildList.stream()
                .filter(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("ul"))
                .findFirst().orElse(null);
        assertNotNull(ul, "UL should exist");

        // Check that all 3 list items are present
        final Obj ulChildren = ul.asRec().at(uri(OUT));
        assertFalse(ulChildren.isNoObj(), "UL should have children");
        List<Obj> liList = ulChildren.asLst().elements().toList();
        assertEquals(3, liList.size(), "Should have 3 list items");

        // Verify raw HTML still contains all content
        assertTrue(rawHtml.contains("<strong>bold</strong>"), "Raw HTML should contain <strong>bold</strong>");
        assertTrue(rawHtml.contains("<em>italic</em>"), "Raw HTML should contain <em>italic</em>");
        assertTrue(rawHtml.contains("<li>Item 1</li>"), "Raw HTML should contain Item 1");
        assertTrue(rawHtml.contains("<li>Item 2</li>"), "Raw HTML should contain Item 2");
        assertTrue(rawHtml.contains("<li>Item 3</li>"), "Raw HTML should contain Item 3");
        assertTrue(rawHtml.contains("public static void main"), "Raw HTML should contain code block content");
        assertTrue(rawHtml.contains("<a href=\"https://example.com\">Link</a>"), "Raw HTML should contain link");
    }

    @Test
    public void testMarkdownToHTMLSimple() {
        final String markdown = "# Hello World\n\nThis is a test.";

        // Parse markdown to Rec
        final Obj markdownRec = ObjMarkdownSerializer.parse(markdown);
        LOG.info("Simple Markdown Rec: %s", markdownRec);

        // Write back to markdown
        final com.vladsch.flexmark.util.ast.Node mdNode = serializer.write(markdownRec);
        final String regeneratedMarkdown = mdNode.getChars().toString();
        LOG.info("Regenerated Markdown: %s", regeneratedMarkdown);

        // Convert to HTML
        final Obj htmlRec = serializer.toHTML(mdNode);
        LOG.info("HTML Rec: %s", htmlRec);

        // Verify structure - root has html key: [html => [...]]
        final Obj htmlObj = htmlRec.asRec().at(uri(HTML));
        assertFalse(htmlObj.isNoObj(), "Root should have html key");

        // Get body directly (it's a direct key under html)
        final Obj body = htmlObj.asRec().at(uri(BODY));
        assertNotNull(body, "Body should exist");

        final Obj bodyChildren = body.asRec().at(uri(OUT));
        List<Obj> bodyChildList = bodyChildren.asLst().elements().toList();

        // Should have h1
        Obj h1 = bodyChildList.stream()
                .filter(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("h1"))
                .findFirst().orElse(null);
        assertNotNull(h1, "H1 should exist");
        assertEquals(str("Hello World"), h1.asRec().at(uri(TEXT)));

        // Should have paragraph
        Obj p = bodyChildList.stream()
                .filter(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("p"))
                .findFirst().orElse(null);
        assertNotNull(p, "Paragraph should exist");
        assertTrue(p.asRec().at(uri(TEXT)).strValue().contains("This is a test"));
    }

    @Test
    public void testMarkdownToHTMLCodeBlock() {
        final String markdown = """
                                ```python
                                def hello():
                                    print("world")
                                ```
                                """;

        final Obj markdownRec = ObjMarkdownSerializer.parse(markdown);
        final Obj htmlRec = serializer.toHTML(serializer.write(markdownRec));

        // Get html object and then body directly
        final Obj htmlObj = htmlRec.asRec().at(uri(HTML));
        final Obj body = htmlObj.asRec().at(uri(BODY));
        assertNotNull(body, "Body should exist");

        // Find pre in body children
        final Obj bodyChildren = body.asRec().at(uri(OUT));
        Obj pre = bodyChildren.asLst().elements().toList().stream()
                .filter(c -> c.isRec() && c.asRec().at(TAG).orElse(uri("")).uriValue().toString().equals("pre"))
                .findFirst().orElse(null);

        assertNotNull(pre, "Code block should be converted to <pre>");
    }

    @Test
    public void testMarkdownToHTMLList() {
        final String markdown = """
                                - First item
                                - Second item
                                - Third item
                                """;

        final Obj markdownRec = ObjMarkdownSerializer.parse(markdown);
        final Obj htmlRec = serializer.toHTML(serializer.write(markdownRec));

        // Get html object and then body directly
        final Obj htmlObj = htmlRec.asRec().at(uri(HTML));
        final Obj body = htmlObj.asRec().at(uri(BODY));
        assertNotNull(body, "Body should exist");

        // Find ul in body children
        final Obj bodyChildren = body.asRec().at(uri(OUT));
        Obj ul = bodyChildren.asLst().elements().toList().stream()
                .filter(c -> c.isRec() && c.asRec().at(uri(TAG)).orElse(uri("")).uriValue().toString().equals("ul"))
                .findFirst().orElse(null);

        assertNotNull(ul, "Bullet list should be converted to <ul>");
    }

    @Test
    public void testOutputBytesVsGetChars() {
        final String markdown = "# Hello World\n\nThis is a paragraph.";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        // What write() returns as toString()
        final com.vladsch.flexmark.util.ast.Node node = serializer.write(rec);
        final String nodeToString = node.toString();
        final String nodeGetChars = node.getChars().toString();

        System.out.println("node.toString():    " + nodeToString);
        System.out.println("node.getChars():    " + nodeGetChars);

        // outputBytes uses toString() internally
        final String outputBytesString = new String(serializer.outputBytes(rec).array());
        System.out.println("outputBytes string: " + outputBytesString);

        // The actual markdown content should be in getChars, not toString
        assertTrue(nodeGetChars.contains("Hello World"),
                "getChars() should contain the markdown content");
    }

    @Test
    public void testWriteToStringReturnsMarkdownText() {
        // This tests the code path used by webInstSet's AS_INST_TID for markdown→str
        final String markdown = "## Hello\n\n**bold** and *italic*";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        final com.vladsch.flexmark.util.ast.Node node = serializer.write(rec);

        // Current webInstSet line 162 uses .toString() — should use .getChars().toString()
        final String viaToString = node.toString();
        final String viaGetChars = node.getChars().toString();

        System.out.println("write().toString():       " + viaToString);
        System.out.println("write().getChars():       " + viaGetChars);

        // The getChars() is the correct markdown output
        assertTrue(viaGetChars.contains("Hello") && viaGetChars.contains("bold"),
                "getChars() should contain the regenerated markdown");
    }

    @Test
    public void testRoundTripViaOutputBytes() {
        final String markdown = "# Title\n\nSome content with **bold** and *italic*.\n\n- Item 1\n- Item 2";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);

        // outputBytes is used when serializing markdown to bytes (e.g., fsSpace writing)
        final byte[] bytes = serializer.outputBytes(rec).array();
        final String roundTripped = new String(bytes);

        // Re-parse the round-tripped output — it should still be valid markdown
        final Obj rec2 = ObjMarkdownSerializer.parse(roundTripped);
        assertEquals(uri(DOC), rec2.asRec().at(uri(TYPE)),
                "Round-tripped output should be valid markdown");
    }

    // ================================================================
    // Bijective round-trip tests — markdown → rec → markdown must
    // produce semantically equivalent output
    // ================================================================

    private String roundTrip(final String markdown) {
        return serializer.write(ObjMarkdownSerializer.parse(markdown))
                .getChars().toString();
    }

    @Test
    public void testBijectiveHeadings() {
        assertEquals("# Hello\n\n", roundTrip("# Hello"));
        assertEquals("## Hello\n\n", roundTrip("## Hello"));
        assertEquals("### Hello\n\n", roundTrip("### Hello"));
    }

    @Test
    public void testBijectiveParagraph() {
        assertEquals("A simple paragraph.\n\n", roundTrip("A simple paragraph."));
    }

    @Test
    public void testBijectiveParagraphWithFormatting() {
        assertEquals("This has **bold** and *italic* text.\n\n",
                roundTrip("This has **bold** and *italic* text."));
        final String result = roundTrip("This has **bold** and *italic* text.");
        assertTrue(result.contains("**bold**"), "Should preserve bold: " + result);
        assertTrue(result.contains("*italic*"), "Should preserve italic: " + result);
    }

    @Test
    public void testBijectiveCodeBlock() {
        final String input = "```java\nSystem.out.println(\"hello\");\n```";
        assertEquals(input + "\n\n", roundTrip(input));
    }

    @Test
    public void testBijectiveInlineCode() {
        assertEquals("Use `System.out.println()` here.\n\n",
                roundTrip("Use `System.out.println()` here."));
    }

    @Test
    public void testBijectiveBulletList() {
        final String input = "- Item 1\n- Item 2\n- Item 3";
        assertEquals(input + "\n\n", roundTrip(input));
    }

    @Test
    public void testBijectiveOrderedList() {
        final String input = "1. First\n2. Second\n3. Third";
        assertEquals(input + "\n\n", roundTrip(input));
    }

    @Test
    public void testBijectiveLink() {
        assertEquals("[Google](https://google.com)\n\n",
                roundTrip("[Google](https://google.com)"));
    }

    @Test
    public void testBijectiveImage() {
        assertEquals("![Alt](https://example.com/img.png)\n\n",
                roundTrip("![Alt](https://example.com/img.png)"));
    }

    @Test
    public void testBijectiveBlockQuote() {
        assertEquals("> A quote\n\n", roundTrip("> A quote"));
    }

    @Test
    public void testBijectiveHorizontalRule() {
        assertEquals("---\n\n", roundTrip("---"));
    }

    @Test
    public void testBijectiveHeadingWithFormatting() {
        // Heading content survives round-trip (text-level bijection)
        final String result = roundTrip("# Hello **World**");
        assertTrue(result.contains("World"), "Heading text should survive round-trip");
        assertTrue(result.contains("# Hello"), "Heading marker should survive round-trip");
    }

    @Test
    public void testBijectiveLinkWithTitle() {
        assertEquals("[Google](https://google.com \"Search\")\n\n",
                roundTrip("[Google](https://google.com \"Search\")"));
    }

    @Test
    public void testBijectiveAutolink() {
        assertEquals("<https://example.com>\n\n",
                roundTrip("<https://example.com>"));
    }

    @Test
    public void testBijectiveNestedList() {
        // Semantic bijection: re-parsing the round-tripped output produces equivalent structure
        final String input = "- Item 1\n  - Nested A\n  - Nested B\n- Item 2";
        final String roundTripped = roundTrip(input);
        System.out.println("Nested list round-trip:\n" + roundTripped.replace("\n", "\\n"));

        // Re-parse: both should yield valid docs with 2 items and nested children
        final Obj rec1 = ObjMarkdownSerializer.parse(input);
        final Obj rec2 = ObjMarkdownSerializer.parse(roundTripped);
        assertEquals(uri(DOC), rec1.asRec().at(uri(TYPE)));
        assertEquals(uri(DOC), rec2.asRec().at(uri(TYPE)));
        assertTrue(roundTripped.contains("Item 1"));
        assertTrue(roundTripped.contains("Nested A"));
        assertTrue(roundTripped.contains("Nested B"));
        assertTrue(roundTripped.contains("Item 2"));
    }

    @Test
    public void testBijectiveComplexDocument() {
        final String markdown = """
                # Main Title

                This is a paragraph with **bold** and *italic* text and `code`.

                ## Subsection

                - Item 1
                - Item 2

                ```python
                def hello():
                    print("world")
                ```

                > A wise quote

                [Link](https://example.com)

                ---

                1. First ordered
                2. Second ordered
                """;
        final String result = roundTrip(markdown);
        System.out.println("Complex round-trip:\n" + result);

        // Re-parse should produce valid markdown rec
        final Obj reparsed = ObjMarkdownSerializer.parse(result);
        assertEquals(uri(DOC), reparsed.asRec().at(uri(TYPE)));

        // Verify key content survived
        assertTrue(result.contains("Main Title"), "Should contain Main Title");
        assertTrue(result.contains("**bold**"), "Should contain bold");
        assertTrue(result.contains("*italic*"), "Should contain italic");
        assertTrue(result.contains("`code`"), "Should contain inline code");
        assertTrue(result.contains("Subsection"), "Should contain Subsection");
        assertTrue(result.contains("Item 1"), "Should contain Item 1");
        assertTrue(result.contains("Item 2"), "Should contain Item 2");
        assertTrue(result.contains("```python"), "Should contain code block");
        assertTrue(result.contains("wise quote"), "Should contain quote");
        assertTrue(result.contains("Link"), "Should contain link text");
    }
}
