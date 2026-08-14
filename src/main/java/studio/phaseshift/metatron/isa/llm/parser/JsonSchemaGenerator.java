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

package studio.phaseshift.metatron.isa.llm.parser;

import dev.langchain4j.model.chat.request.json.*;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.union_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {
    }

    public static JsonSchemaElement objToSchema(final Type type, final Poly<?, ?> depth, final String description) {
        if (type.test(BOOL_TYPE))
            return new JsonBooleanSchema.Builder().description(description).build();
        else if (type.test(INT_TYPE))
            return new JsonIntegerSchema.Builder().description(description).build();
        else if (type.test(REAL_TYPE))
            return new JsonNumberSchema.Builder().description(description).build();
        else if (type.test(URI_TYPE))
            return new JsonStringSchema.Builder().description(description).build();
        else if (type.test(STR_TYPE))
            return new JsonStringSchema.Builder().description(description).build();
        else if (type.test(LST_TYPE))
            return lstToSchema(null == depth ? lst() : depth.asLst(), description);
        else if (type.test(REC_TYPE))
            return recToSchema(null == depth ? rec() : depth.asRec(), description);
        else if (type.test(REL_TYPE))
            return recToSchema(rec(depth.asRel().first().type(), depth.asRel().second()), description);
        else
            return new JsonStringSchema.Builder().description(description).build();
    }

    public static JsonArraySchema lstToSchema(final Lst l, final String description) {
        final JsonArraySchema.Builder schema = JsonArraySchema.builder();
        l.elements().forEach(e -> schema.items(objToSchema(e.type(), null, description)));
        if (l.isEmpty())
            schema.items(new JsonStringSchema.Builder().description(description).build());
        return schema.description(description).build();
    }

    public static JsonObjectSchema recToSchema(final Rec r, final String description) {
        final JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
        final List<String> required = new ArrayList<>();
        r.elements().forEach(e -> {
            schema.addProperty(e.first().uriValue().toString(), objToSchema(e.second().type(), null, description));
            if (!e.first().c().isZeroable())
                required.add(e.first().uriValue().toString());
        });

        schema.required(required);
        return schema.build();
    }

    /**
     * The inverse of {@link #objToSchema(Type, Poly, String)}: map a
     * LangChain4j {@link JsonSchemaElement} back to a metatron type.  The
     * forward mapping is lossy — both {@code str::T} and {@code uri::T}
     * become {@code JsonStringSchema} — so the reverse defaults string
     * schemas to {@code str::T} and enum/reference schemas to {@code uri::T}.
     * <p>
     * {@code JsonAnyOfSchema} has no single metatron type; it is reconstructed
     * as a coproduct via the {@code union} instruction.
     *
     * @param element the schema element to reverse
     * @return a {@link Type} (or a union {@code inst} for anyOf)
     */
    public static Obj schemaToType(final JsonSchemaElement element) {
        if (element instanceof JsonBooleanSchema)
            return BOOL_TYPE;
        else if (element instanceof JsonIntegerSchema)
            return INT_TYPE;
        else if (element instanceof JsonNumberSchema)
            return REAL_TYPE;
        else if (element instanceof JsonStringSchema)
            return STR_TYPE;
        else if (element instanceof JsonEnumSchema || element instanceof JsonReferenceSchema)
            return URI_TYPE;
        else if (element instanceof JsonArraySchema)
            return Type.Builder.build().tid(LST_TID).vid(LST_TID)
                    .isaPredicate(lst(schemaToType(((JsonArraySchema) element).items())))
                    .create();
        else if (element instanceof JsonObjectSchema)
            return recToType((JsonObjectSchema) element);
        else if (element instanceof JsonAnyOfSchema)
            return union_(lst(((JsonAnyOfSchema) element).anyOf().stream().map(JsonSchemaGenerator::schemaToType).toList())).tryToInst();
        else
            return STR_TYPE;
    }

    private static Type recToType(final JsonObjectSchema schema) {
        final List<String> required = schema.required();
        final Map<Obj, Obj> fields = new LinkedHashMap<>();
        schema.properties().forEach((name, sub) -> {
            final Obj key = required.contains(name) ? uri(name) : uri(name).maybe();
            fields.put(key, schemaToType(sub));
        });
        return Type.Builder.build().tid(REC_TID).vid(REC_TID).isaPredicate(rec(fields)).create();
    }
}
