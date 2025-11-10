/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.net.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Translator;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WebTranslator implements Translator<Obj, Document> {


    private Rec readElement(final Element element) {
        //Graphitty.log(this).warn(element);
        final AtomicReference<Rec> recX = new AtomicReference<>(rec());
        element.attributes().forEach(a -> recX.getAndUpdate(r -> r.put(uri(a.getKey()), str(a.getValue()))));
        element.children().forEach(e -> recX.getAndUpdate(r -> r.put(uri(e.nodeName()), readElement(e))));
        final String text = element.ownText();
        return text.isEmpty() ? recX.get() : recX.updateAndGet(r -> r.put(uri("text"), str(text)));
    }

    private Element writeElement(final Rec rec, final Element element) {
        //Graphitty.log(this).warn(rec);
        rec.<Rel>elements().forEach(e -> {
            final Element newElement = new Element(e.first().uriValue().toString());
            element.appendChild(e.second().isRec() ? writeElement(e.second().as(), newElement) : newElement);
        });
        return element;
    }


    @Override
    public Obj translate(final Document document) {
        return readElement(document);
    }

    @Override
    public Document translate(final Obj obj) {
        if (!obj.isRec())
            throw MTronException.of("only rec can be translated to an html document");
        final Document document = new Document(".");
        return (Document) writeElement(obj.as(), document);
    }

    public Obj translatePage(final File htmlPage) {
        try {
            final Document document = Jsoup.parse(htmlPage, "UTF-8");
            return this.translate(document);
        } catch (final Exception e) {
            throw MTronException.of(e, "%s", htmlPage);
        }
    }
}
