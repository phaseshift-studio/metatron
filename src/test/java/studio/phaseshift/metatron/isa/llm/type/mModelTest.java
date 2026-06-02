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

package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.model.chat.request.json.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.llm.JsonSchemaGenerator;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MODEL_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.noobjRec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mModelTest extends AbstractMetatronTest {

    private static final String MODEL_NAME = "test-model";
    private static final String PROVIDER_NAME = "ollama";
    private static final String PROVIDER_HOST = "http://localhost:11434";
    private static final String PROVIDER_KEY = "test-api-key";

    private Rec fixture;
    private mModel model;

    @BeforeEach
    public void setup() {
        fixture = buildFixture();
        model = mModel.model(fixture);
    }

    private static Rec buildFixture() {
        Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), uri(MODEL_NAME));
        map.put(uri(PROVIDER), rec(Map.of(
                uri(NAME), str(PROVIDER_NAME),
                uri(HOST), uri(PROVIDER_HOST),
                uri(API_KEY), str(PROVIDER_KEY)
        )));
        map.put(uri(API_KEY), str(PROVIDER_KEY));
        map.put(uri(FEATURE), rec(Map.of(
                uri(THINK), (Obj) rec(),
                uri(TOOL), (Obj) lst(uri("/test/tool")),
                uri(SKILL), (Obj) lst(str("a-skill")),
                uri(NOTE), (Obj) lst(str("a-note")),
                uri(PROMPT), (Obj) str("you are helpful"),
                uri(RAG), (Obj) rec(Map.of(
                        uri(PATTERN), uri("/sys/docs/#"),
                        uri(MAX), jnt(5)
                )),
                uri(COST), (Obj) rec(Map.of(
                        uri("input"), real(0.01),
                        uri("output"), real(0.02)
                )),
                uri(MEMORY), (Obj) rec(Map.of(
                        uri("mem"), lst(),
                        uri(MAX), jnt(15)
                )),
                uri(RESPONSE), (Obj) rec(Map.of(
                        uri(FORMAT), rec(Map.of(
                                uri("answer"), str("string")
                        ))
                ))
        )));
        return rec(map, MODEL_TID, null);
    }

    // === mModel accessor tests ===

    @Test
    public void testModelFactory() {
        assertNotNull(model);
        assertEquals(MODEL_TID, model.tid());
        assertEquals(fixture.vid(), model.vid());
    }

    @Test
    public void testModelName() {
        assertEquals(MODEL_NAME, model.model());
    }

    @Test
    public void testProvider() {
        mModel.Provider provider = model.provider();
        assertEquals(PROVIDER_NAME, provider.name());
        assertEquals(PROVIDER_HOST, provider.host().toString());
        assertEquals(PROVIDER_KEY, provider.apiKey());
    }

    @Test
    public void testFeaturePresent() {
        assertTrue(model.feature(THINK).isPresent());
        assertTrue(model.feature(TOOL).isPresent());
        assertTrue(model.feature(SKILL).isPresent());
        assertTrue(model.feature(NOTE).isPresent());
        assertTrue(model.feature(PROMPT).isPresent());
        assertTrue(model.feature(RAG).isPresent());
        assertTrue(model.feature(COST).isPresent());
        assertTrue(model.feature(MEMORY).isPresent());
    }

    @Test
    public void testFeatureAbsent() {
        assertTrue(model.feature("nonexistent").isEmpty());
    }

    @Test
    public void testTools() {
        assertTrue(model.tools().isPresent());
        assertEquals(1, model.tools().get().count());
        assertEquals(uri("/test/tool"), model.tools().get().elements().findFirst().orElse(null));
    }

    @Test
    public void testToolsAbsent() {
        Rec empty = rec(Map.of(uri(NAME), uri("empty")), MODEL_TID, null);
        mModel emptyModel = mModel.model(empty);
        assertTrue(emptyModel.tools().isEmpty());
    }

    @Test
    public void testCost() {
        assertTrue(model.cost().isPresent());
        assertEquals(real(0.01), model.cost().get().at(uri("input")).orElse(null));
        assertEquals(real(0.02), model.cost().get().at(uri("output")).orElse(null));
    }

    @Test
    public void testSkills() {
        assertTrue(model.skills().isPresent());
        assertEquals(1, model.skills().get().count());
    }

    @Test
    public void testNotes() {
        assertTrue(model.notes().isPresent());
        assertEquals(1, model.notes().get().count());
    }

    @Test
    public void testPrompt() {
        assertTrue(model.prompt().isPresent());
        assertEquals("you are helpful", model.prompt().get().strValue());
    }

    @Test
    public void testRag() {
        assertTrue(model.rag().isPresent());
        assertEquals(f("/sys/docs/#"), model.rag().get().at(PATTERN).uriValue());
        assertEquals(5, model.rag().get().at(MAX).intValue().intValue());
    }

    @Test
    public void testMemory() {
        Rec memory = model.memory();
        assertFalse(memory.isNoObj());
        assertEquals(15, memory.at(MAX).intValue().intValue());
    }

    @Test
    public void testMemoryAbsent() {
        Rec empty = rec(Map.of(uri(NAME), uri("empty")), MODEL_TID, null);
        assertTrue(mModel.model(empty).memory().isNoObj());
    }

    @Test
    public void testFeatures() {
        Rec features = model.features();
        assertFalse(features.isNoObj());
        // features should contain the feature sub-rec
        assertTrue(features.at(f(FEATURE).extend(THINK)).orElse(null) != null ||
                features.at(uri(THINK)).orElse(null) != null);
    }

    @Test
    public void testFeaturesAbsent() {
        Rec empty = rec(Map.of(uri(NAME), uri("empty")), MODEL_TID, null);
        assertTrue(mModel.model(empty).features().isNoObj());
    }

    @Test
    public void testAddNote() {
        assertEquals(1, model.notes().get().count());
        model.addNote(str("another-note"));
        assertEquals(2, model.notes().get().count());
    }

    @Test
    public void testResponseFormat() {
        assertTrue(model.responseFormat().isPresent());
        assertTrue(model.responseFormat().get().at(uri("answer")).orElse(null) != null);
    }

    @Test
    public void testResponseFormatAbsent() {
        Rec empty = rec(Map.of(uri(NAME), uri("empty")), MODEL_TID, null);
        assertTrue(mModel.model(empty).responseFormat().isEmpty());
    }

    @Test
    public void testLastResponseAbsent() {
        Rec empty = rec(Map.of(uri(NAME), uri("empty")), MODEL_TID, null);
        assertTrue(mModel.model(empty).lastResponse().isEmpty());
    }

    @Test
    public void testResponseFormatPresent() {
        assertTrue(model.lastResponse().isPresent());
    }

    // === JsonSchemaGenerator tests ===

    @Test
    public void testBoolSchema() {
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(BOOL_TYPE, noobjRec(), "test");
        assertInstanceOf(JsonBooleanSchema.class, schema);
    }

    @Test
    public void testIntSchema() {
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(INT_TYPE, noobjRec(), "test");
        assertInstanceOf(JsonIntegerSchema.class, schema);
    }

    @Test
    public void testRealSchema() {
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(REAL_TYPE, noobjRec(), "test");
        assertInstanceOf(JsonNumberSchema.class, schema);
    }

    @Test
    public void testUriSchema() {
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(URI_TYPE, noobjRec(), "test");
        assertInstanceOf(JsonStringSchema.class, schema);
    }

    @Test
    public void testStrSchema() {
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(STR_TYPE, noobjRec(), "test");
        assertInstanceOf(JsonStringSchema.class, schema);
    }

    @Test
    public void testLstSchema() {
        Lst listWithItems = lst(uri("a"), uri("b"));
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(LST_TYPE, listWithItems, "test");
        assertInstanceOf(JsonArraySchema.class, schema);
    }

    @Test
    public void testRecSchema() {
        Rec recWithFields = rec(uri("field"), STR_TYPE);
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(REC_TYPE, recWithFields, "test");
        assertInstanceOf(JsonObjectSchema.class, schema);
    }

    @Test
    public void testUnknownTypeFallsBackToString() {
        // a type that doesn't match any known branch
        JsonSchemaElement schema = JsonSchemaGenerator.objToSchema(
                Type.Builder.build().tid(f("/sys/temp")).create(),
                noobjRec(), "test");
        assertInstanceOf(JsonStringSchema.class, schema);
    }

    @Test
    public void testLstToSchemaEmptyList() {
        JsonArraySchema schema = JsonSchemaGenerator.lstToSchema(lst(), "test");
        assertNotNull(schema);
        assertNotNull(schema.items());
    }

    @Test
    public void testLstToSchemaWithItems() {
        JsonArraySchema schema = JsonSchemaGenerator.lstToSchema(lst(uri("a")), "test");
        assertNotNull(schema);
    }

    @Test
    public void testRecToSchemaRequiredFields() {
        Rec r = rec(uri("name"), STR_TYPE, uri("age"), INT_TYPE);
        JsonObjectSchema schema = JsonSchemaGenerator.recToSchema(r, "person");
        assertNotNull(schema);
    }
}
