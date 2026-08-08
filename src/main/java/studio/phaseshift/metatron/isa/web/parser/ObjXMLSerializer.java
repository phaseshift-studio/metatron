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

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_XML_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjXMLSerializer extends AbstractObjSerializer<Document> {

    private static final GraphittyLogger LOG = Graphitty.log(ObjXMLSerializer.class);
    public static final fURI OBJ_XML_SERIALIZER_VID = OBJ_XML_SERIALIZER_TID;

    private static final ObjXMLSerializer INSTANCE = new ObjXMLSerializer();

    public static final ObjXMLSerializer single() {
        return INSTANCE;
    }

    public ObjXMLSerializer() {
        super(OBJ_XML_SERIALIZER_TID, OBJ_XML_SERIALIZER_VID);
    }

    private Rec readElement(final Element element) {
        LOG.debug("processing element: %s", element);
        final Rec recX = rec();
        if (element.hasAttributes()) {
            final Rec attrX = rec();
            for (int i = 0; i < element.getAttributes().getLength(); i++) {
                final Attr attr = (Attr) element.getAttributes().item(i);
                attrX.jvm().put(uri(attr.getName()), str(attr.getValue()));
            }
            recX.jvm().put(uri("attr"), attrX);
        }

        if (element.hasChildNodes()) {
            final Lst nodeX = lst();
            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                final Node child = element.getChildNodes().item(i);
                if (child instanceof Element)
                    nodeX.jvm().add(rel(uri(child.getNodeName()), readElement((Element) child)));
            }
            recX.jvm().put(uri("node"), nodeX);
        }
        final String text = element.getTextContent().trim();
        if (!text.isEmpty())
            recX.jvm().put(uri("text"), str(text));
        LOG.debug("processed element as: %s", recX);
        return recX;
    }

    private Element writeElement(final Rec rec, final Element element) {
        //Graphitty.log(this).warn(rec);
        /*rec.<Rel>elements().forEach(e -> {
            final org.jsoup.nodes.Element newElement = new org.jsoup.nodes.Element(e.first().uriValue().toString());
            element.appendChild(e.second().isRec() ? writeElement(e.second().as(), newElement) : newElement);
        });*/
        return null;
        //return element;
    }


    @Override
    public Rec read(final Document document) {
        return rec(document.getDocumentElement().getNodeName(), readElement(document.getDocumentElement()));
    }

    @Override
    public Obj inputBytes(ByteBuffer bytes) throws MTronException {
        return parse(StandardCharsets.UTF_8.decode(bytes).toString());
    }

    @Override
    public Document write(final Obj obj) {
        try {
            if (!obj.isRec())
                throw MTronException.of("XML write requires a Rec, got: %s", obj.type());
            final Rec rec = obj.asRec();
            // The rec is structured as rec(rootTagName, readElement(rootElement))
            // i.e. a single Rel: uri(tag) => elementRec
            final java.util.Iterator<Rel> iter = rec.elements().iterator();
            if (!iter.hasNext())
                throw MTronException.of("XML Rec must have at least one element");
            final Rel rootRel = iter.next();
            final String rootTag = rootRel.first().isUri() ?
                    rootRel.first().uriValue().toString() : rootRel.first().toString();
            final Rec rootElementRec = rootRel.second().asRec();

            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final Document doc = factory.newDocumentBuilder().newDocument();
            doc.appendChild(writeElement(rootTag, rootElementRec, doc));
            return doc;
        } catch (final MTronException e) {
            throw e;
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to write XML: %s", obj);
        }
    }

    private Element writeElement(final String tagName, final Rec rec, final Document doc) {
        final Element element = doc.createElement(tagName);

        // attributes
        final Obj attrObj = rec.jvm().get(uri("attr"));
        if (null != attrObj && attrObj.isRec()) {
            attrObj.asRec().jvm().forEach((k, v) ->
                    element.setAttribute(k.uriValue().toString(), v.strValue()));
        }

        // child elements — stored as a Lst of Rel(uri(tag), elementRec)
        final Obj nodeObj = rec.jvm().get(uri("node"));
        if (null != nodeObj && nodeObj.isLst()) {
            nodeObj.asLst().elements().forEach(child -> {
                if (child.isRel()) {
                    final Rel childRel = child.asRel();
                    final String childTag = childRel.first().isUri() ?
                            childRel.first().uriValue().toString() : childRel.first().toString();
                    element.appendChild(writeElement(childTag, childRel.second().asRec(), doc));
                }
            });
        }

        // text content
        final Obj textObj = rec.jvm().get(uri("text"));
        if (null != textObj && textObj.isStr()) {
            element.setTextContent(textObj.strValue());
        }

        return element;
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        final Document doc = write(obj);
        try {
            final Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            final StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return ByteBuffer.wrap(writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to serialize XML: %s", obj);
        }
    }

    public static Rec parse(final String xml) {
        try {
            final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setValidating(false);
            builderFactory.setNamespaceAware(false);
            builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            builderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            builderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            builderFactory.setFeature("http://apache.org/xml/features/continue-after-fatal-error", true);
            final DocumentBuilder builder = builderFactory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(final SAXParseException exception) {
                    LOG.warn(exception);
                }

                @Override
                public void error(final SAXParseException exception) {
                    LOG.error(exception);
                }

                @Override
                public void fatalError(final SAXParseException exception) {
                    LOG.error(exception);
                }
            });
            return new ObjXMLSerializer().read(builder.parse(new InputSource(new StringReader(xml.trim()))));
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to parse xml: %s", e);
        }
    }

    @Override
    public fURI vid() {
        return OBJ_XML_SERIALIZER_VID;
    }
}
