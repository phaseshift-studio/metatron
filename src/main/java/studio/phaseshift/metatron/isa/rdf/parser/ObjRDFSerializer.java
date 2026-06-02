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

package studio.phaseshift.metatron.isa.rdf.parser;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserRegistry;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.eclipse.rdf4j.model.util.Values.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjRDFSerializer extends AbstractObjSerializer<Stream<Value>> {


    public static final fURI OBJ_RDF_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend(studio.phaseshift.metatron.Tokens.RDF);

    private static final ObjRDFSerializer INSTANCE = new ObjRDFSerializer();

    public static ObjRDFSerializer single() {
        return INSTANCE;
    }
    
    @Override
    public Obj read(final Stream<Value> values) throws MTronException {
        final List<Value> vs = values.toList();
        if (vs.size() == 1) {
            Value value = vs.getFirst();
            if (value.isLiteral()) {
                Literal literal = (Literal) value;
                final IRI datatype = literal.getDatatype();
                if (datatype.equals(XSD.INTEGER) || datatype.equals(XSD.LONG)) {
                    return jnt(literal.intValue()); // Java int
                } else if (datatype.equals(XSD.BOOLEAN)) {
                    return bool(literal.booleanValue()); // Java boolean
                } else if (datatype.equals(XSD.DOUBLE) || datatype.equals(XSD.FLOAT) || datatype.equals(XSD.DECIMAL)) {
                    return real(literal.decimalValue().doubleValue()); // Java BigDecimal
                } else if (datatype.equals(XSD.STRING)) {
                    return str(literal.stringValue());
                } else if (datatype.equals(XSD.ANYURI)) {
                    return uri(literal.stringValue());
                } else {
                    final String label = literal.getLabel(); // Java String
                    return uri(label);
                }
            } else if (value.isIRI()) {
                return uri(value.stringValue());
            } else if (value.isBNode()) {
                return str(value.stringValue());
            } else if (value.isTriple()) {
                final Triple triple = (Triple) value;
                return lst(this.read(Stream.of(triple.getSubject())), this.read(Stream.of(triple.getPredicate())), this.read(Stream.of(triple.getObject())));
            }
        } else {

        }
        throw MTronException.of("cannot read obj from %s".formatted(values));
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        final RDFParser parser = new RDFParserRegistry().get(RDFFormat.N3).get().getParser();
        final List<Obj> values = new ArrayList<>();
        parser.setRDFHandler(new AbstractRDFHandler() {
            @Override
            public void handleStatement(final Statement triple) throws RDFHandlerException {
                values.add(lst(ObjRDFSerializer.this.read(Stream.of(triple.getSubject())), ObjRDFSerializer.this.read(Stream.of(triple.getPredicate())), ObjRDFSerializer.this.read(Stream.of(triple.getObject()))));
            }
        });
        try {
            parser.parse(new ByteArrayInputStream(bytes.array()), "");
        } catch (final Exception e) {
            throw MTronException.of("cannot parse rdf: %s".formatted(e.getMessage()));
        }
        return objs(values.stream());
    }

    @Override
    public Stream<Value> write(final Obj obj) throws MTronException {
        if (obj.isBool())
            return Stream.of(literal(obj.boolValue()));
        else if (obj.isInt())
            return Stream.of(literal(obj.intValue()));
        else if (obj.isReal())
            return Stream.of(literal(obj.realValue()));
        else if (obj.isStr())
            return Stream.of(literal(obj.strValue()));
        else if (obj.isUri())
            return Stream.of(iri(obj.uriValue().toString()));
        else if (obj.isLst()) {
            List<Value> values = this.lstValue().stream().flatMap(this::write).toList();
            List<Value> triples = new ArrayList<>();
            Resource blankNode = bnode();
            triples.add(SimpleValueFactory.getInstance().createTriple(blankNode, RDF.TYPE, RDF.LIST));
            for (Value value : values) {
                triples.add(SimpleValueFactory.getInstance().createTriple(blankNode, RDF.FIRST, value));
                blankNode = SimpleValueFactory.getInstance().createBNode();
                triples.add(SimpleValueFactory.getInstance().createTriple(blankNode, RDF.REST, blankNode));
            }
            triples.add(SimpleValueFactory.getInstance().createTriple(blankNode, RDF.REST, RDF.NIL));
            return triples.stream();
        } else if (obj.isRec()) {
            Model model = new DynamicModelFactory().createEmptyModel();
            final Resource blankNode = bnode();
            this.recValue().forEach((key, value) -> {
                this.write(value).forEach(v -> model.add(blankNode, iri(key.toString()), v));
            });
            return model.stream().map(s -> SimpleValueFactory.getInstance().createTriple(s.getSubject(), s.getPredicate(), s.getObject()));
        }
        throw MTronException.of("cannot write obj to rdf: %s".formatted(obj));
    }

    @Override
    public fURI vid() {
        return OBJ_RDF_SERIALIZER_VID;
    }
}
