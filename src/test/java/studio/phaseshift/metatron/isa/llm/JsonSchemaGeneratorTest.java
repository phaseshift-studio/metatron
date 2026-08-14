package studio.phaseshift.metatron.isa.llm;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.parser.JsonSchemaGenerator;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.noobjRec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tests for {@link JsonSchemaGenerator#schemaToType(JsonSchemaElement)} — the
 * inverse of {@link JsonSchemaGenerator#objToSchema} — and for the tool inverse
 * {@link mTool#toolToMtronDoc(ToolSpecification, ToolExecutor)}.
 */
public class JsonSchemaGeneratorTest extends AbstractMetatronTest {

    @Test
    public void testSchemaToTypePrimitives() {
        assertEquals(BOOL_TYPE, JsonSchemaGenerator.schemaToType(new JsonBooleanSchema.Builder().build()), "bool schema -> bool::T");
        assertEquals(INT_TYPE, JsonSchemaGenerator.schemaToType(new JsonIntegerSchema.Builder().build()), "integer schema -> int::T");
        assertEquals(REAL_TYPE, JsonSchemaGenerator.schemaToType(new JsonNumberSchema.Builder().build()), "number schema -> real::T");
        assertEquals(STR_TYPE, JsonSchemaGenerator.schemaToType(new JsonStringSchema.Builder().build()), "string schema -> str::T");
        assertEquals(URI_TYPE, JsonSchemaGenerator.schemaToType(new JsonEnumSchema.Builder().enumValues(List.of("a", "b")).build()), "enum schema -> uri::T");
        assertEquals(URI_TYPE, JsonSchemaGenerator.schemaToType(new JsonReferenceSchema.Builder().reference("#/defs/x").build()), "reference schema -> uri::T");
    }

    @Test
    public void testSchemaToTypeComposites() {
        final Obj lstType = JsonSchemaGenerator.schemaToType(
                new JsonArraySchema.Builder().items(new JsonStringSchema.Builder().build()).build());
        assertTrue(lstType.test(LST_TYPE), "array schema -> lst::T");

        final Obj recType = JsonSchemaGenerator.schemaToType(JsonObjectSchema.builder()
                .addStringProperty("name")
                .addIntegerProperty("age")
                .required("name")
                .build());
        assertTrue(recType.test(REC_TYPE), "object schema -> rec::T");
    }

    @Test
    public void testToolToMtronDocRoundTripsNameAndDescription() {
        final ToolSpecification spec = ToolSpecification.builder()
                .name("add_numbers")
                .description("adds two numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .required("a", "b")
                        .build())
                .build();
        final ToolExecutor executor = (req, memoryId) -> "5";

        final QCollection.Docs doc = mTool.toolToMtronDoc(spec, executor);
        assertNotNull(doc);
        assertEquals("adds two numbers", doc.description(), "tool description carried on the Docs");
        assertTrue(doc.at(OBJ).isInst(), "Docs.OBJ holds the wrapped inst");

        // round-trip through the forward — name + description are preserved
        // (arg types are not: the forward currently re-derives args from descriptions)
        final Tuple.Pair<ToolSpecification, ToolExecutor> roundTripped = mTool.mtronInstToolSpecification(doc);
        assertEquals("add_numbers", roundTripped.get0().name(), "tool name survives the round-trip");
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
