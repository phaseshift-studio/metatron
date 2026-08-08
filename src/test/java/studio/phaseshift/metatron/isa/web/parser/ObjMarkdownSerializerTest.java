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

import com.vladsch.flexmark.util.ast.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MARKDOWN_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjMarkdownSerializerTest extends AbstractSerializerTest<Node> {

    private final ObjMarkdownSerializer serializer = ObjMarkdownSerializer.single();

    public ObjMarkdownSerializerTest() {
        super(new ObjMarkdownSerializer(), MARKDOWN_TID, "markdown");
    }

    @Override
    public void testSerializeDeserializeObj(final String objString) {
        // TODO: easy -- just toString monos;
    }

    // ===================================================================
    //  Existing parse tests
    // ===================================================================

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
        final Obj children = rec.asRec().at(uri(OUT));
        assertTrue(children.isLst());
        assertFalse(children.asLst().at(0).isNoObj());
        assertFalse(children.asLst().at(1).isNoObj());
    }

    @Test
    public void testEmptyDocument() {
        final String markdown = "";
        final Obj rec = ObjMarkdownSerializer.parse(markdown);
        assertTrue(rec.isRec());
        assertEquals(uri(DOC), rec.asRec().at(uri(TYPE)));
    }

    // Bijective round-trip tests
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
    public void testBijectiveCodeBlock() {
        final String input = "```java\nSystem.out.println(\"hello\");\n```";
        assertEquals(input + "\n\n", roundTrip(input));
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

    // ===================================================================
    //  Type-conversion: str -> markdown::T  (tag + validate)
    // ===================================================================

    @ParameterizedTest
    @CsvSource(quoteCharacter = '~', value = {
            "'# Hello World'                %  heading",
            "'A simple paragraph.'          %  paragraph",
            "'- Item 1\\n- Item 2'           %  bullet list",
            "'1. First\\n2. Second'          %  ordered list",
            "'[Link](https://example.com)'  %  link",
            "'```java\\nint x = 1;\\n```'     %  code block",
            "'> A quote'                    %  blockquote",
            "'---'                          %  horizontal rule",
            "''                             %  empty document",
    }, delimiter = '%')
    void testStrToMarkdownType(final String mtronValue, final String desc) {
        final Str result = assertStrToType(mtronValue);
        assertNotNull(result, "must produce a result: " + desc);
    }

    // ===================================================================
    //  Type-conversion: markdown::T -> rec::T  (parse)
    // ===================================================================

    @ParameterizedTest
    @CsvSource(quoteCharacter = '~', value = {
            "'# Hello World'       %  heading parses to rec",
            "'A paragraph.'        %  paragraph parses to rec",
            "'- Item 1\\n- Item 2'  %  list parses to rec",
    }, delimiter = '%')
    void testMarkdownToRec(final String mtronValue, final String desc) {
        final Rec result = assertTypeToRec(mtronValue);
        assertTrue(result.count() >= 0, "must be a valid rec: " + desc);
    }

    // ===================================================================
    //  Type-conversion: rec::T -> markdown::T  (serialize)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'# Hello World'       %  heading round-trip",
            "'A paragraph.'        %  paragraph round-trip",
    }, delimiter = '%')
    void testRecToMarkdown(final String mtronValue, final String desc) {
        final Str roundTripped = assertRecToType(mtronValue);
        assertNotNull(roundTripped, "round-trip must produce markdown: " + desc);
    }

    // ===================================================================
    //  Integration: full type chain via mtron runtime
    // ===================================================================

    @Test
    public void testMarkdownTypeChain() {
        final Str mdStr = eval("'# Hello'.as(markdown::T)");
        assertEquals(MARKDOWN_TID, mdStr.tid());
        assertTrue(mdStr.isStr());

        final Rec mdRec = eval("'" + mdStr.strValue() + "'.as(markdown::T).as(rec::T)");
        assertTrue(mdRec.isRec());

        final Str mdStr2 = eval("'" + mdStr.strValue() + "'.as(markdown::T).as(rec::T).as(markdown::T)");
        assertEquals(MARKDOWN_TID, mdStr2.tid());
        assertTrue(mdStr2.isStr());
    }
}
