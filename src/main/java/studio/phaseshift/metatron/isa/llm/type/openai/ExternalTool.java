package studio.phaseshift.metatron.isa.llm.type.openai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.isa.llm.llmInstSet;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A harness-owned tool projected onto the agent's tool bag.  It does not evaluate in
 * metatron: its {@link HarnessToolExecutor} records the call and returns a sentinel, and the
 * transport surfaces the recorded call as OpenAI {@code tool_calls}.  The agent supplies
 * itself via {@code mToolProvider.provideTools}'s {@code .agent(...)} wiring, so this tool
 * carries no agent reference.
 */
public class ExternalTool extends mTool {

    private final String name;
    private final String description;
    private final JsonObjectSchema parameters;

    public ExternalTool(final String name, final String description, final JsonObjectSchema parameters) {
        super(mutableMap(uri(NAME), uri(name)), llmInstSet.LLM_TOOL_TID, null);
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    /**
     * Project one OpenAI function definition ({@code {type, function:{name, description,
     * parameters}}}) onto an {@code ExternalTool}, or null when the element is not a valid
     * function definition.  The {@code parameters} JSON Schema is translated to a LC4j
     * {@code JsonObjectSchema} (properties + required + description).
     */
    public static ExternalTool from(final Obj t) {
        if (!t.isRec())
            return null;
        final Obj function = t.asRec().at(uri("function"));
        if (!function.isRec())
            return null;
        final Rec fn = function.asRec();
        final Obj nameObj = fn.at(uri(NAME));
        if (nameObj.isNoObj())
            return null;
        final String name = nameObj.toCleanString();
        final String description = fn.at(uri("description")).isNoObj() ? "" : fn.at(uri("description")).toCleanString();
        final Obj paramsObj = fn.at(uri("parameters"));
        final JsonObjectSchema parameters = paramsObj.isRec()
                ? jsonSchemaToObjectSchema(paramsObj.asRec())
                : new JsonObjectSchema.Builder().build();
        return new ExternalTool(name, description, parameters);
    }

    @Override
    public Tuple.Pair<ToolSpecification, ToolExecutor> toolSpecification() {
        final ToolSpecification spec = ToolSpecification.builder()
                .name(this.name)
                .description(this.description)
                .parameters(this.parameters)
                .build();
        return Tuple.Pair.with(spec, new HarnessToolExecutor(this.name));
    }

    // ── OpenAI JSON Schema (metatron Rec) → LC4j JsonSchemaElement ──────

    private static JsonObjectSchema jsonSchemaToObjectSchema(final Rec schema) {
        final JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        final Obj properties = schema.at(uri("properties"));
        if (properties.isRec()) {
            for (final Obj entry : properties.asRec().elements().toList()) {
                final Rel kv = entry.asRel();
                if (kv.second().isRec())
                    builder.addProperty(kv.first().uriValue().name(), jsonSchemaToElement(kv.second().asRec()));
            }
        }
        final Obj required = schema.at(uri("required"));
        if (required.isLst()) {
            final List<String> req = new ArrayList<>();
            required.asLst().elements().forEach(r -> req.add(r.toCleanString()));
            builder.required(req);
        }
        return builder.build();
    }

    private static JsonSchemaElement jsonSchemaToElement(final Rec schema) {
        final Obj typeObj = schema.at(uri("type"));
        final String type = typeObj.isNoObj() ? "object" : typeObj.toCleanString();
        final Obj descObj = schema.at(uri("description"));
        final String description = descObj.isNoObj() ? null : descObj.toCleanString();
        return switch (type) {
            case "object" -> jsonSchemaToObjectSchema(schema);
            case "array" -> {
                final JsonArraySchema.Builder b = JsonArraySchema.builder();
                final Obj items = schema.at(uri("items"));
                if (items.isRec())
                    b.items(jsonSchemaToElement(items.asRec()));
                if (null != description)
                    b.description(description);
                yield b.build();
            }
            case "integer" -> {
                final JsonIntegerSchema.Builder b = new JsonIntegerSchema.Builder();
                if (null != description)
                    b.description(description);
                yield b.build();
            }
            case "number" -> {
                final JsonNumberSchema.Builder b = new JsonNumberSchema.Builder();
                if (null != description)
                    b.description(description);
                yield b.build();
            }
            case "boolean" -> {
                final JsonBooleanSchema.Builder b = new JsonBooleanSchema.Builder();
                if (null != description)
                    b.description(description);
                yield b.build();
            }
            default -> {
                final JsonStringSchema.Builder b = new JsonStringSchema.Builder();
                if (null != description)
                    b.description(description);
                yield b.build();
            }
        };
    }
}
