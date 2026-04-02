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
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjHTMLSerializer extends AbstractObjSerializer<Document> {

    private static final String TEXT = "text";
    private static final String DATA = "data";
    private static final fURI FTEXT = f(TEXT);
    private static final fURI FDATA = f(DATA);

    public static final fURI OBJ_HTML_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("html");

    private static final ObjHTMLSerializer INSTANCE = new ObjHTMLSerializer();

    public static ObjHTMLSerializer single() {
        return INSTANCE;
    }

    private Rec readElement(final Element element) {
        //Graphitty.log(this).warn(element);
        final AtomicReference<Rec> recX = new AtomicReference<>(rec());
        element.attributes().forEach(a -> recX.getAndUpdate(r -> r.at(uri(a.getKey()), str(a.getValue()))));
        element.children().forEach(e -> recX.getAndUpdate(r -> r.at(uri(e.nodeName()), readElement(e))));
        if (element.hasText() && !element.text().isBlank())
            recX.getAndUpdate(r -> r.at(uri(TEXT), str(element.text())));
        final String data = element.data();
        if (!data.isBlank())
            recX.getAndUpdate(r -> r.at(uri(DATA), str(data)));
        return recX.get();
    }

    private Element writeElement(final Rec rec, final Element element) {
        rec.at(uri(DATA)).ifPresent(data -> {
            final DataNode dataNode = new DataNode(data.strValue());
            element.appendChild(dataNode);
        });
        rec.at(uri(TEXT)).ifPresent(text -> element.text(text.strValue()));
        rec.elements()
                .filter(e -> !e.first().uriValue().equals(FTEXT) && !e.first().uriValue().equals(FDATA))
                .forEach(e -> {
                    if (!e.second().isRec()) {
                        final String attrValue = e.second().isStr() ? e.second().strValue() : e.second().toString();
                        element.attr(e.first().uriValue().toString(), attrValue);
                    } else {
                        final Element newElement = new Element(e.first().uriValue().toString());
                        element.appendChild(writeElement(e.second().as(), newElement));
                    }
                });
        return element;
    }


    @Override
    public Obj read(final Document document) {
        return readElement(document);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return parse(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    @Override
    public Document write(final Obj obj) {
        if (!obj.isRec())
            throw MTronException.of("only rec can be translated to an html document");
        final Document document = new Document(".");
        return (Document) writeElement(obj.as(), document);
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
            throw MTronException.of(e, "unable to parse html: %s", e);
        }
    }

    @Override
    public fURI vid() {
        return OBJ_HTML_SERIALIZER_VID;
    }
}
