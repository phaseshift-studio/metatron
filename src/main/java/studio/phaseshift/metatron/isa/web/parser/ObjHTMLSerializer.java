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

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.HTML_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjHTMLSerializer extends AbstractObjSerializer<Document> {

    public static final fURI OBJ_HTML_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend(HTML);

    private static final ObjHTMLSerializer INSTANCE = new ObjHTMLSerializer();

    public static ObjHTMLSerializer single() {
        return INSTANCE;
    }

    private Rec readElement(final Element element) {
        final String tagName = element.nodeName();
        final AtomicReference<Rec> recX = new AtomicReference<>(rec());

        // Handle singular structural elements (html, head, body, title)
        if (tagName.equals(HTML)) {
            return readHtmlElement(element);
        } else if (tagName.equals(HEAD)) {
            return readHeadElement(element);
        } else if (tagName.equals(BODY)) {
            return readBodyElement(element);
        }

        // For all other elements, use tag + children model
        recX.getAndUpdate(r -> r.at(uri(TAG), uri(tagName)));

        // Store attributes
        element.attributes().forEach(a -> recX.getAndUpdate(r -> r.at(uri(a.getKey()),
                a.getKey().equalsIgnoreCase(SRC) || a.getKey().equalsIgnoreCase(HREF) ?
                        uri(a.getValue()) :
                        str(a.getValue()))));

        // Process children
        final List<Obj> children = new ArrayList<>();
        element.children().forEach(e -> children.add(readElement(e)));

        if (!children.isEmpty()) {
            recX.getAndUpdate(r -> r.at(uri(OUT), lst(children)));
        }

        if (!element.ownText().isBlank())
            recX.getAndUpdate(r -> r.at(uri(TEXT), str(element.ownText())));
        if (!element.data().isBlank())
            recX.getAndUpdate(r -> r.at(uri(DATA), str(element.data())));

        return recX.get();
    }

    private Rec readHtmlElement(final Element htmlElement) {
        Rec rec = rec();

        // Find head and body children
        Element headElement = null;
        Element bodyElement = null;

        for (Element child : htmlElement.children()) {
            if (child.nodeName().equals(HEAD)) {
                headElement = child;
            } else if (child.nodeName().equals(BODY)) {
                bodyElement = child;
            }
        }

        // Add head and body as direct keys
        if (headElement != null) {
            rec = rec.at(uri(HEAD), readHeadElement(headElement));
        }
        if (bodyElement != null) {
            rec = rec.at(uri(BODY), readBodyElement(bodyElement));
        }

        // Store attributes
        for (org.jsoup.nodes.Attribute a : htmlElement.attributes()) {
            rec = rec.at(uri(a.getKey()), str(a.getValue()));
        }

        return rec;
    }

    private Rec readHeadElement(final Element headElement) {
        Rec rec = rec();
        final List<Obj> children = new ArrayList<>();

        for (Element child : headElement.children()) {
            if (child.nodeName().equals(TITLE)) {
                rec = rec.at(uri(TITLE), str(child.text()));
            } else {
                // All other head children (meta, link, style, script, etc.)
                children.add(readElement(child));
            }
        }

        // Add other children as list
        if (!children.isEmpty()) {
            rec = rec.at(uri(OUT), lst(children));
        }

        // Store attributes
        for (org.jsoup.nodes.Attribute a : headElement.attributes()) {
            rec = rec.at(uri(a.getKey()), str(a.getValue()));
        }

        return rec;
    }

    private Rec readBodyElement(final Element bodyElement) {
        Rec rec = rec();
        final List<Obj> outgoing = new ArrayList<>();

        // Process all body children
        bodyElement.children().forEach(e -> outgoing.add(readElement(e)));

        if (!outgoing.isEmpty()) {
            rec = rec.at(uri(OUT), lst(outgoing));
        }

        // Store attributes
        for (org.jsoup.nodes.Attribute a : bodyElement.attributes()) {
            rec = rec.at(uri(a.getKey()), str(a.getValue()));
        }

        if (!bodyElement.ownText().isBlank())
            rec = rec.at(uri(TEXT), str(bodyElement.ownText()));

        return rec;
    }


    private Element writeElement(final Rec rec, final Element element) {
        rec.at(uri(DATA)).ifPresent(data -> {
            final DataNode dataNode = new DataNode(data.strValue());
            element.appendChild(dataNode);
        });
        rec.at(uri(TEXT)).ifPresent(text -> element.text(text.strValue()));

        // Process children list
        final Obj outgoing = rec.at(uri(OUT));
        if (!outgoing.isNoObj() && outgoing.isLst()) {
            outgoing.asLst().elements().forEach(out -> {
                if (out.isRec()) {
                    final Rec outRec = out.asRec();
                    final String tagName = outRec.at(uri(TAG)).orElse(uri(DIV)).uriValue().toString();
                    final Element newElement = new Element(tagName);
                    element.appendChild(writeElement(outRec, newElement));
                }
            });
        }

        // Process attributes (skip special keys: text, data, tag, children)
        rec.elements()
                .filter(e -> !e.first().uriValue().toString().equals(TEXT) &&
                        !e.first().uriValue().toString().equals(DATA) &&
                        !e.first().uriValue().toString().equals(TAG) &&
                        !e.first().uriValue().toString().equals(OUT))
                .forEach(e -> {
                    if (!e.second().isRec() && !e.second().isLst()) {
                        final String attrValue = e.second().isStr() ? e.second().strValue() :
                                (e.second().isUri() ?
                                        e.second().uriValue().toString() :
                                        e.second().toString());
                        element.attr(e.first().uriValue().toString(), attrValue);
                    }
                });
        return element;
    }

    private Element writeHtmlElement(final Rec htmlRec, final Element htmlElement) {
        // Write head if present
        final Obj headObj = htmlRec.at(uri(HEAD));
        if (!headObj.isNoObj() && headObj.isRec()) {
            final Element headElement = htmlElement.appendElement(HEAD);
            writeHeadElement(headObj.asRec(), headElement);
        }

        // Write body if present
        final Obj bodyObj = htmlRec.at(uri(BODY));
        if (!bodyObj.isNoObj() && bodyObj.isRec()) {
            final Element bodyElement = htmlElement.appendElement(BODY);
            writeBodyElement(bodyObj.asRec(), bodyElement);
        }

        // Process attributes (skip head, body)
        htmlRec.elements()
                .filter(e -> !e.first().uriValue().toString().equals(HEAD) &&
                        !e.first().uriValue().toString().equals(BODY))
                .forEach(e -> {
                    if (!e.second().isRec() && !e.second().isLst()) {
                        final String attrValue = e.second().isStr() ? e.second().strValue() :
                                (e.second().isUri() ?
                                        e.second().uriValue().toString() :
                                        e.second().toString());
                        htmlElement.attr(e.first().uriValue().toString(), attrValue);
                    }
                });

        return htmlElement;
    }

    private Element writeHeadElement(final Rec headRec, final Element headElement) {
        // Write title if present
        final Obj titleObj = headRec.at(uri(TITLE));
        if (!titleObj.isNoObj() && titleObj.isRec()) {
            final Element titleElement = headElement.appendElement(TITLE);
            titleElement.text(titleObj.strValue());
        }

        // Write other children (meta, link, style, script, etc.)
        final Obj childrenObj = headRec.at(uri(OUT));
        if (!childrenObj.isNoObj() && childrenObj.isLst()) {
            childrenObj.asLst().elements().forEach(child -> {
                if (child.isRec()) {
                    final Rec childRec = child.asRec();
                    final String tagName = childRec.at(uri(TAG)).orElse(uri("meta")).uriValue().toString();
                    final Element newElement = new Element(tagName);
                    headElement.appendChild(writeElement(childRec, newElement));
                }
            });
        }

        // Process attributes (skip title, children)
        headRec.elements()
                .filter(e -> !e.first().uriValue().toString().equals(TITLE) &&
                        !e.first().uriValue().toString().equals(OUT))
                .forEach(e -> {
                    if (!e.second().isRec() && !e.second().isLst()) {
                        final String attrValue = e.second().isStr() ? e.second().strValue() :
                                (e.second().isUri() ?
                                        e.second().uriValue().toString() :
                                        e.second().toString());
                        headElement.attr(e.first().uriValue().toString(), attrValue);
                    }
                });

        return headElement;
    }

    private Element writeBodyElement(final Rec bodyRec, final Element bodyElement) {
        // Write text if present
        bodyRec.at(uri(TEXT)).ifPresent(text -> bodyElement.text(text.strValue()));

        // Write children
        final Obj childrenObj = bodyRec.at(uri(OUT));
        if (!childrenObj.isNoObj() && childrenObj.isLst()) {
            childrenObj.asLst().elements().forEach(child -> {
                if (child.isRec()) {
                    final Rec childRec = child.asRec();
                    final String tagName = childRec.at(uri(TAG)).orElse(uri(DIV)).uriValue().toString();
                    final Element newElement = new Element(tagName);
                    bodyElement.appendChild(writeElement(childRec, newElement));
                }
            });
        }

        // Process attributes (skip text, children)
        bodyRec.elements()
                .filter(e -> !e.first().uriValue().toString().equals(TEXT) &&
                        !e.first().uriValue().toString().equals(OUT))
                .forEach(e -> {
                    if (!e.second().isRec() && !e.second().isLst()) {
                        final String attrValue = e.second().isStr() ? e.second().strValue() :
                                (e.second().isUri() ?
                                        e.second().uriValue().toString() :
                                        e.second().toString());
                        bodyElement.attr(e.first().uriValue().toString(), attrValue);
                    }
                });

        return bodyElement;
    }


    @Override
    public Obj read(final Document document) {
        // Find the html element in the document
        final Element htmlElement = document.selectFirst(HTML);

        if (htmlElement != null) {
            // Return rec with html as key: [html => [...]]
            return rec().at(uri(HTML), readHtmlElement(htmlElement)).selfTID(HTML_TID);
        }

        // Fallback: empty rec if html not found
        return rec();
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return parse(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        final Document document = this.write(obj);
        // Use outerHtml() to get the complete HTML document
        final String html = document.outerHtml();
        return ByteBuffer.wrap(html.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Document write(final Obj obj) {
        if (!obj.isRec())
            throw MTronException.of("only rec can be translated to an html document");
        final Document document = new Document(".");
        final Rec rootRec = obj.asRec();

        // Check if root has html key: [html => [...]]
        final Obj htmlObj = rootRec.at(uri(HTML));
        if (!htmlObj.isNoObj() && htmlObj.isRec()) {
            // Create the html element and write its content using hybrid structure
            final Element htmlElement = document.appendElement(HTML);
            writeHtmlElement(htmlObj.asRec(), htmlElement);
            return document;
        }

        // Fallback: if it's a regular element with tag field, write it normally
        final Obj tagObj = rootRec.at(uri(TAG));
        if (!tagObj.isNoObj() && tagObj.isUri()) {
            final String tagName = tagObj.uriValue().toString();
            final Element element = document.appendElement(tagName);
            writeElement(rootRec, element);
            return document;
        }

        // Empty document
        return document;
    }

    public Obj translatePage(final File htmlPage) {
        try {
            final Document document = Jsoup.parse(htmlPage, "UTF-8");
            return this.read(document);
        } catch (final Exception e) {
            throw MTronException.of(e, "%s", htmlPage);
        }
    }

    public static Obj parse(final String html) {
        try {
            final Document document = Jsoup.parse(html);
            return new ObjHTMLSerializer().read(document);
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to parse html: %s", html);
        }
    }

    @Override
    public fURI vid() {
        return OBJ_HTML_SERIALIZER_VID;
    }
}
