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

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.MARKDOWN_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjMarkdownSerializer extends AbstractObjSerializer<Node> {

    private static final ObjMarkdownSerializer INSTANCE = new ObjMarkdownSerializer();
    private static final Parser parser = Parser.builder().build();

    public static final fURI OBJ_MARKDOWN_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("markdown");

    public static ObjMarkdownSerializer single() {
        return INSTANCE;
    }

    public Obj toHTML(final Node markdown) {
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return ObjHTMLSerializer.parse(renderer.render(markdown));
    }

    public static Obj parse(final String markdown) {
        final Parser parser = Parser.builder().build();
        final Node document = parser.parse(markdown);
        return single().read(document);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return single().read(parser.parse(new String(bytes.array())));
    }

    @Override
    public Node write(final Obj obj) {
        if (!obj.isRec()) {
            return parser.parse(obj.isStr() ? obj.strValue() : obj.toString());
        }

        final StringBuilder markdown = new StringBuilder();
        writeNode(obj.asRec(), markdown);
        return parser.parse(markdown.toString());
    }

    private void writeNode(final studio.phaseshift.metatron.isa.m.type.Rec rec, final StringBuilder markdown) {
        final String type = rec.at(TYPE).orElse(uri("unknown")).uriValue().toString();

        switch (type) {
            case DOC -> writeChildren(rec, markdown);

            case HEAD -> {
                final int level = rec.at(LEVEL).orElse(jnt(1)).intValue().intValue();
                final String text = rec.at(TEXT).orElse(str("")).strValue();
                markdown.append("#".repeat(level)).append(" ").append(text).append("\n\n");
            }

            case P -> {
                writeChildren(rec, markdown);
                markdown.append("\n\n");
            }

            case CODE -> {
                final String language = rec.at(LANG).orElse(str("")).strValue();
                final String code = rec.at(CODE).orElse(str("")).strValue();
                markdown.append("```").append(language).append("\n");
                markdown.append(code);
                if (!code.endsWith("\n")) markdown.append("\n");
                markdown.append("```\n\n");
            }

            case B_LIST -> {
                writeChildren(rec, markdown);
                markdown.append("\n");
            }

            case O_LIST -> {
                final int start = rec.at(START).orElse(jnt(1)).intValue().intValue();
                writeOrderedListChildren(rec, markdown, start);
                markdown.append("\n");
            }

            case ENTRY -> {
                markdown.append("- ");
                // List items contain paragraphs as children, write them inline
                final Obj childrenObj = rec.at(CHILDREN);
                if (!childrenObj.isNoObj() && childrenObj.isLst()) {
                    childrenObj.asLst().elements().forEach(child -> {
                        if (child.isRec()) {
                            final String childType = child.asRec().at(TYPE).orElse(uri("unknown")).uriValue().toString();
                            if (P.equals(childType)) {
                                // For paragraphs inside list items, write children without the paragraph wrapper
                                writeChildren(child.asRec(), markdown);
                            } else {
                                writeNode(child.asRec(), markdown);
                            }
                        }
                    });
                }
                markdown.append("\n");
            }

            case QUOTE -> {
                markdown.append("> ");
                writeChildren(rec, markdown);
                markdown.append("\n\n");
            }

            case "horizontal_rule" -> markdown.append("---\n\n");

            case EDGE -> {
                final Obj urlObj = rec.at(URI).orElse(str(""));
                final String url = urlObj.isUri() ? urlObj.uriValue().toString() : urlObj.strValue();
                final String title = rec.at(TITLE).orElse(str("")).strValue();
                markdown.append("[");
                // Prefer children over text field for formatted content
                final Obj childrenObj = rec.at(CHILDREN);
                if (!childrenObj.isNoObj() && childrenObj.isLst()) {
                    writeChildren(rec, markdown);
                } else {
                    final String text = rec.at(TEXT).orElse(str("")).strValue();
                    markdown.append(text);
                }
                markdown.append("](").append(url);
                if (!title.isEmpty()) {
                    markdown.append(" \"").append(title).append("\"");
                }
                markdown.append(")");
            }

            case "autolink" -> {
                final Obj urlObj = rec.at(URI).orElse(str(""));
                final String url = urlObj.isUri() ? urlObj.uriValue().toString() : urlObj.strValue();
                markdown.append("<").append(url).append(">");
            }

            case "image" -> {
                final String alt = rec.at(ALT).orElse(str("")).strValue();
                final Obj urlObj = rec.at(URI).orElse(str(""));
                final String url = urlObj.isUri() ? urlObj.uriValue().toString() : urlObj.strValue();
                final String title = rec.at(TITLE).orElse(str("")).strValue();
                markdown.append("![").append(alt).append("](").append(url);
                if (!title.isEmpty()) {
                    markdown.append(" \"").append(title).append("\"");
                }
                markdown.append(")");
            }

            case "emphasis" -> {
                markdown.append("*");
                // Prefer children over text field
                final Obj childrenObj = rec.at(CHILDREN);
                if (!childrenObj.isNoObj() && childrenObj.isLst()) {
                    writeChildren(rec, markdown);
                } else {
                    final String text = rec.at(TEXT).orElse(str("")).strValue();
                    markdown.append(text);
                }
                markdown.append("*");
            }

            case "strong" -> {
                markdown.append("**");
                // Prefer children over text field
                final Obj childrenObj = rec.at(CHILDREN);
                if (!childrenObj.isNoObj() && childrenObj.isLst()) {
                    writeChildren(rec, markdown);
                } else {
                    final String text = rec.at(TEXT).orElse(str("")).strValue();
                    markdown.append(text);
                }
                markdown.append("**");
            }

            case "inline_code" -> {
                final String code = rec.at(CODE).orElse(str("")).strValue();
                markdown.append("`").append(code).append("`");
            }

            case "text" -> {
                final String content = rec.at(CONTENT).orElse(str("")).strValue();
                markdown.append(content);
            }

            case "soft_break" -> markdown.append("\n");

            case "hard_break" -> markdown.append("  \n");

            case "html_block" -> {
                final String html = rec.at(HTML).orElse(str("")).strValue();
                markdown.append(html).append("\n\n");
            }

            case "html_inline" -> {
                final String html = rec.at(HTML).orElse(str("")).strValue();
                markdown.append(html);
            }

            case "reference" -> {
                final String label = rec.at(LABEL).orElse(str("")).strValue();
                final Obj urlObj = rec.at(URI).orElse(str(""));
                final String url = urlObj.isUri() ? urlObj.uriValue().toString() : urlObj.strValue();
                final String title = rec.at(TITLE).orElse(str("")).strValue();
                markdown.append("[").append(label).append("]: ").append(url);
                if (!title.isEmpty()) {
                    markdown.append(" \"").append(title).append("\"");
                }
                markdown.append("\n");
            }

            default -> {
                // Unknown type - try to write children or content
                rec.at(CONTENT).ifPresent(content -> markdown.append(content.strValue()));
                if (!rec.has(CONTENT)) {
                    writeChildren(rec, markdown);
                }
            }
        }
    }

    private void writeChildren(final studio.phaseshift.metatron.isa.m.type.Rec rec, final StringBuilder markdown) {
        // Children are stored as a list
        final Obj childrenObj = rec.at(CHILDREN);
        if (childrenObj.isNoObj() || !childrenObj.isLst()) return;

        childrenObj.asLst().elements().forEach(child -> {
            if (child.isRec()) {
                writeNode(child.asRec(), markdown);
            }
        });
    }

    private void writeOrderedListChildren(final studio.phaseshift.metatron.isa.m.type.Rec rec, final StringBuilder markdown, int start) {
        final Obj childrenObj = rec.at(CHILDREN);
        if (childrenObj.isNoObj() || !childrenObj.isLst()) return;

        final AtomicInteger itemNumber = new AtomicInteger(start);
        childrenObj.asLst().elements().forEach(child -> {
            if (child.isRec()) {
                markdown.append(itemNumber.getAndIncrement()).append(". ");
                // List items contain paragraphs as children, write them inline
                final Obj itemChildren = child.asRec().at(CHILDREN);
                if (!itemChildren.isNoObj() && itemChildren.isLst()) {
                    itemChildren.asLst().elements().forEach(itemChild -> {
                        if (itemChild.isRec()) {
                            final String childType = itemChild.asRec().at(TYPE).orElse(uri("unknown")).uriValue().toString();
                            if (P.equals(childType)) {
                                // For paragraphs inside list items, write children without the paragraph wrapper
                                writeChildren(itemChild.asRec(), markdown);
                            } else {
                                writeNode(itemChild.asRec(), markdown);
                            }
                        }
                    });
                }
                markdown.append("\n");
            }
        });
    }

    @Override
    public Obj read(final Node document) {
        return readNode(document);
    }

    private Obj readNode(final Node node) {
        final AtomicReference<Obj> recRef = new AtomicReference<>(rec());
        final List<Obj> children = new ArrayList<>();

        // Process children and add them to the list
        node.getChildren().forEach(child -> {
            final Obj childObj = readNode(child);
            children.add(childObj);
        });

        // Headings
        if (node instanceof Heading heading) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(HEAD)));
            recRef.getAndUpdate(r -> r.asRec().at(LEVEL, jnt(heading.getLevel())));
            if (!heading.getText().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TEXT, str(heading.getText().toString())));
        }
        // Paragraphs
        else if (node instanceof Paragraph paragraph) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(P)));
            if (!paragraph.getContentChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TEXT, str(paragraph.getContentChars().toString())));
        }
        // Code blocks
        else if (node instanceof FencedCodeBlock codeBlock) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(CODE)));
            if (!codeBlock.getInfo().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(LANG, str(codeBlock.getInfo().toString())));
            if (!codeBlock.getContentChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(CODE, str(codeBlock.getContentChars().toString())));
        }
        else if (node instanceof IndentedCodeBlock codeBlock) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(CODE)));
            if (!codeBlock.getContentChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(CODE, str(codeBlock.getContentChars().toString())));
        }
        // Lists
        else if (node instanceof BulletList) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(B_LIST)));
        }
        else if (node instanceof OrderedList orderedList) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(O_LIST)));
            recRef.getAndUpdate(r -> r.asRec().at(START, jnt(orderedList.getStartNumber())));
        }
        else if (node instanceof BulletListItem) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(ENTRY)));
        }
        else if (node instanceof OrderedListItem) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(ENTRY)));
        }
        // Block quotes
        else if (node instanceof BlockQuote) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(QUOTE)));
        }
        // Horizontal rule
        else if (node instanceof ThematicBreak) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("horizontal_rule")));
        }
        // Links
        else if (node instanceof Link link) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(EDGE)));
            if (!link.getUrl().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(URI, uri(link.getUrl().toString())));
            if (!link.getTitle().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TITLE, str(link.getTitle().toString())));
            if (!link.getText().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TEXT, str(link.getText().toString())));
        }
        else if (node instanceof AutoLink autoLink) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("autolink")));
            if (!autoLink.getUrl().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(URI, uri(autoLink.getUrl().toString())));
            if (!autoLink.getText().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TEXT, str(autoLink.getText().toString())));
        }
        // Images
        else if (node instanceof Image image) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("image")));
            if (!image.getUrl().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(URI, uri(image.getUrl().toString())));
            if (!image.getTitle().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TITLE, str(image.getTitle().toString())));
            if (!image.getText().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(ALT, str(image.getText().toString())));
        }
        // Emphasis and strong
        else if (node instanceof Emphasis) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("emphasis")));
            if (!node.getChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TEXT, str(node.getChars().toString())));
        }
        else if (node instanceof StrongEmphasis) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("strong")));
            if (!node.getChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TEXT, str(node.getChars().toString())));
        }
        // Inline code
        else if (node instanceof Code code) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("inline_code")));
            if (!code.getText().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(CODE, str(code.getText().toString())));
        }
        // Text
        else if (node instanceof Text text) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("text")));
            if (!text.getChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(CONTENT, str(text.getChars().toString())));
        }
        // Soft line break
        else if (node instanceof SoftLineBreak) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("soft_break")));
        }
        // Hard line break
        else if (node instanceof HardLineBreak) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("hard_break")));
        }
        // HTML blocks and inline HTML
        else if (node instanceof HtmlBlock htmlBlock) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("html_block")));
            if (!htmlBlock.getContentChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(HTML, str(htmlBlock.getContentChars().toString())));
        }
        else if (node instanceof HtmlInline htmlInline) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("html_inline")));
            if (!htmlInline.getChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(HTML, str(htmlInline.getChars().toString())));
        }
        // Reference (for links and images)
        else if (node instanceof Reference reference) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("reference")));
            if (!reference.getReference().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(LABEL, str(reference.getReference().toString())));
            if (!reference.getUrl().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(URI, uri(reference.getUrl().toString())));
            if (!reference.getTitle().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(TITLE, str(reference.getTitle().toString())));
        }
        // Document (root)
        else if (node.getClass().getSimpleName().equals("Document")) {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri(DOC)));
        }
        // Fallback for unknown node types
        else {
            recRef.getAndUpdate(r -> r.asRec().at(TYPE, uri("unknown")));
            recRef.getAndUpdate(r -> r.asRec().at(uri("class"), str(node.getClass().getSimpleName())));
            if (!node.getChars().isBlank())
                recRef.getAndUpdate(r -> r.asRec().at(CONTENT, str(node.getChars().toString())));
        }

        // Add children as a list if there are any
        if (!children.isEmpty()) {
            recRef.getAndUpdate(r -> r.asRec().at(CHILDREN, lst(children)));
        }

        return recRef.get();
    }

    @Override
    public fURI vid() {
        return OBJ_MARKDOWN_SERIALIZER_VID;
    }
}
