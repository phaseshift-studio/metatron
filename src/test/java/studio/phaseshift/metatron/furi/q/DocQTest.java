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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.AND_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.Inst.OBJ;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocQTest extends AbstractMetatronTest {

    protected static final GraphittyLogger LOG = Graphitty.log(DocQTest.class);

    private DocQTest() {
        // do nothing
    }

    @Test
    public void testDocStructure() {
        final Inst inst = Router.readFromSpace(AND_INST_TID).asInst();
        final Docs doc = new Docs(Router.readFromSpace(AND_INST_TID.addQ(DOCQ)).asRec());
        assertTrue(doc.test(DOCQ_TYPE));
        assertTrue("and() documentation has latex formatting in its description", doc.description().contains("\\("));
        assertEquals(doc.at(DESC).strValue(), doc.description());
        assertEquals(inst, doc.at(OBJ));
    }

    @Test
    public void testNoDocumentation() {
        final fURI dummyURI = f("/m/inst/NoTAInsT");
        final Inst inst = Router.readFromSpace(dummyURI).asInst();
        final Docs doc = new Docs(Router.readFromSpace(dummyURI.addQ(DOCQ)).asRec());
        assertTrue(doc.test(DOCQ_TYPE));
        assertEquals(NO_DOCS.at(DESC).strValue(), doc.description());
        assertTrue(inst.isNoObj());
    }

    @Test
    public void testWritingDocumentation() {
        final fURI newURI = f("/m/some_obj");
        Router.global().write(newURI, str("some obj"));
        Docs doc = new Docs(Router.readFromSpace(newURI.addQ(DOCQ)).asRec());
        assertEquals(NO_DOCS.at(DESC).strValue(), doc.description());
        /// //
        Router.global().write(newURI.addQ(DOCQ), str("some obj"));
        doc = new Docs(Router.readFromSpace(newURI.addQ(DOCQ)).asRec());
        assertTrue(doc.test(DOCQ_TYPE));
        assertEquals("some obj", doc.description());
        assertEquals("some obj", doc.at(DESC).strValue());
        /// //
        final fURI newURI2 = f("/m/some_obj_2");
        docWrap(str("some obj 2",STR_TID,newURI2), "a test str", "aa", "bb");
        doc = new Docs(Router.readFromSpace(newURI2.addQ(DOCQ)).asRec());
        assertTrue(doc.test(DOCQ_TYPE));
        assertEquals("a test str", doc.description());
        assertTrue(doc.examples().contains("aa"));
        assertTrue(doc.examples().contains("bb"));
        assertEquals(noobj(), doc.at(RNG));
        assertEquals(noobj(), doc.at(DOM));
        assertEquals(str("some obj 2").selfVID(newURI2), doc.at(OBJ));
    }

    public static void testWritingDocs(final Space space) {
        assertTrue(space.qs().elements().anyMatch(q -> q.<Q>as().pattern().equals(f(DOCQ))));
        final fURI baseURI = space.pattern().retractPattern();
        for (final Obj obj : List.of(
                jnt(10),
                real(12.0),
                str("a lonely string"),
                lst(jnt(1), jnt(2), real(12.3), str("hola")),
                rec(uri("a"), uri("b"), uri("c"), jnt(23)))) {
            final Obj writeResult = Router.global().write(baseURI.extend("test" + obj.tid().name()), obj);
            assertEquals(obj, writeResult);
            final Obj docWriteResult = Router.global().write(baseURI.extend("test" + obj.tid().name()).q("docq", null), Docs.doc(obj, null, null, null, "a obj that is a " + obj.tid().name()));
            LOG.debug("\n write result: %s \n write doc result: %s", writeResult, docWriteResult);
            assertEquals(DOCS_TID, docWriteResult.tid());
            assertEquals("a obj that is a " + obj.tid().name(), new Docs(docWriteResult.asRec()).description());
            final Obj readResult = Router.global().read(baseURI.extend("test" + obj.tid().name()));
            assertEquals(writeResult, readResult);
            assertEquals(obj, readResult);
            final Obj docReadResult = Router.global().read(baseURI.extend("test" + obj.tid().name()).q("docq", null));
            LOG.debug("\n read result: %s \n read doc result: %s", readResult, docReadResult);
            assertEquals(DOCS_TID, docReadResult.tid());
            assertEquals(docWriteResult, docReadResult);
            assertEquals("a obj that is a " + obj.tid().name(), new Docs(docReadResult.asRec()).description());
        }
    }

    public static void analyzeDocs(final InstSet instSet) {
        for (final Inst inst : instSet.insts()) {
            Obj doc = instSet.read(inst.tid().noQ().one().q("docq"));
            // LOG.info("HERE %s:", doc.type());
            if (doc.c().equals(cInt.ONE())) {
                LOG.warn("%s has no associated documentation %s", inst, doc.<Docs>as().at(DESC));
            }
        }
    }
}
