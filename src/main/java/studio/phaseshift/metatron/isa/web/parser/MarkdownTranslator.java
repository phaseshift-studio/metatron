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

import org.commonmark.node.Document;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import studio.phaseshift.metatron.isa.Translator;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MarkdownTranslator implements Translator<Rec, Document> {
    private static final GraphittyLogger LOG = Graphitty.log(MarkdownTranslator.class);

    public MarkdownTranslator() {

    }


    private Node writeElement(final Rec rec, final Node node) {
        //Graphitty.log(this).warn(rec);
        rec.elements().forEach(e -> {
            node.appendChild(e.second().isRec() ? this.writeElement(e.second().asRec(), node) : e.second().as());
        });
        return node;
    }


    @Override
    public Document translate(final Rec rec) {
        //Graphitty.log(this).warn(rec);
        final Document newNode = new Document();
        final List<SourceSpan> sourceSpans = new ArrayList<>();
        rec.elements().forEach(e -> {
            if (e.second().isRec())
                this.writeElement(e.second().asRec(), newNode);
        });
        return newNode;
    }

    @Override
    public Rec translate(final Document root) {
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        renderer.render(root);  // "<p>This is <em>Markdown</em></p>\n"
        return rec();
        //return parse(renderer.render(root));
    }

    public static Obj parse(final String markdown) {
        Parser parser = Parser.builder().build();
        Document document = (Document) parser.parse(markdown);
        return new MarkdownTranslator().translate(document);
    }
}
