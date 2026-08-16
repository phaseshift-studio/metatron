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

package studio.phaseshift.metatron.isa.mach.io.type;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Tree;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_JAVA_SERIALIZER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_SERIALIZER_TID;

/*
 * A serializer that converts Java source code into a metatron Rec (and back) using
 * the TreeSitter parser for accurate structural decomposition. Each syntax node
 * becomes a Rec with a [__type=>...] field; named children with field names (e.g.,
 * "name", "body", "parameters") are promoted to direct keys, while unnamed named
 * children are collected under [out=>[...]].
 *
 * The internal key {@code __type} is used instead of {@code type} to avoid
 * collisions with the TreeSitter Java grammar field name "type" which appears
 * on method_declaration, field_declaration, formal_parameter, etc.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjJavaSerializer extends AbstractObjSerializer<String> {

    /**
     * Key for the TreeSitter node type — deliberately NOT "type"
     * to avoid colliding with the Java grammar's own "type" field name.
     */
    public static final Obj NODE_TYPE_KEY = uri("type_");

    private static final ObjJavaSerializer INSTANCE = new ObjJavaSerializer();

    private static final Parser PARSER;

    static {
        loadNativeLibrary();
        PARSER = Parser.getFor(Language.JAVA);
    }

    /**
     * Load the tree-sitter native library, patching the musl libc dependency
     * to glibc when running on a glibc-based Linux system.  The JAR ships a
     * musl-compiled {@code libjava-tree-sitter.so} whose NEEDED entry for
     * {@code libc.musl-x86_64.so.1} prevents loading on glibc hosts even
     * though all undefined symbols are standard C functions that glibc provides.
     */
    public static void loadNativeLibrary() {
        final String libName = "libjava-tree-sitter.so";
        try (final InputStream is = ObjJavaSerializer.class.getClassLoader().getResourceAsStream(libName)) {
            if (is == null) {
                throw MTronException.of("tree-sitter native library not found on classpath: " + libName);
            }

            // Extract to a temp file
            final Path tmpDir = Files.createTempDirectory("metatron-treesitter");
            final Path tmpLib = tmpDir.resolve(libName);
            Files.copy(is, tmpLib, StandardCopyOption.REPLACE_EXISTING);

            // Patch the musl NEEDED entry → glibc (best-effort; patchelf may not exist)
            try {
                new ProcessBuilder(
                        "patchelf", "--replace-needed",
                        "libc.musl-x86_64.so.1", "libc.so.6",
                        tmpLib.toString())
                        .inheritIO()
                        .start()
                        .waitFor();
                tmpLib.toFile().deleteOnExit();
            } catch (final Exception e) {
                // patchelf not available — try loading as-is (may work on musl hosts)
                Graphitty.log(ObjJavaSerializer.single()).error("patchelf failed, trying unpatched tree-sitter .so: %s" + e);
            }
            System.load(tmpLib.toAbsolutePath().toString());
        } catch (final Exception e) {
            Graphitty.log(ObjJavaSerializer.class).error("failed to load tree-sitter native library", e);
        }
    }

    public static ObjJavaSerializer single() {
        return INSTANCE;
    }

    public ObjJavaSerializer() {
        super(OBJ_SERIALIZER_TID, OBJ_JAVA_SERIALIZER_TID);
    }

    /**
     * Parse a Java source string into a metatron Rec.
     */
    public static Obj parse(final String javaSource) {
        return INSTANCE.read(javaSource);
    }

    // ── read: Java source → Rec ───────────────────────────────────────────

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return read(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    @Override
    public Obj read(final String source) throws MTronException {
        try (final Tree tree = PARSER.parse(source)) {
            final Node rootNode = tree.getRootNode();
            // The TreeSitter Java grammar root is "program"; even with errors we
            // produce a best-effort tree (TreeSitter is error-resilient).
            return readNode(rootNode);
        } catch (final MTronException e) {
            throw e;
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to parse java source");
        }
    }

    /**
     * Recursively convert a TreeSitter {@link Node} into a metatron Rec.
     * <ul>
     *   <li>{@code TYPE} — the TreeSitter node type (e.g. "class_declaration")</li>
     *   <li>Field-named children (e.g. {@code name}, {@code body}, {@code parameters})
     *       become direct keys on the rec.</li>
     *   <li>Unnamed named children are collected under {@code OUT}.</li>
     *   <li>Leaf nodes (no named children) carry their source text in {@code TEXT}.</li>
     * </ul>
     */
    private Obj readNode(final Node node) {
        final AtomicReference<Rec> recRef = new AtomicReference<>(rec());
        recRef.getAndUpdate(r -> r.at(NODE_TYPE_KEY, uri(node.getType())));

        final List<Obj> outChildren = new ArrayList<>();
        final int childCount = node.getChildCount();
        final boolean hasNamedChildren = node.getNamedChildCount() > 0;

        for (int i = 0; i < childCount; i++) {
            final Node child = node.getChild(i);
            if (!child.isNamed()) {
                // For nodes whose primary content is anonymous keywords (notably
                // "modifiers"), we capture the full source-range TEXT at the parent
                // level below.  Individual anonymous leaf tokens are skipped here.
                continue;
            }

            final String fieldName = node.getFieldNameForChild(i);
            final Obj childObj = readNode(child);

            if (fieldName != null && !fieldName.isEmpty()) {
                // TreeSitter field names may contain URI-invalid chars (e.g. "[")
                // — promote to a direct key only when the name is a valid URI
                // segment, otherwise relegate to OUT.
                if (isValidUriSegment(fieldName)) {
                    recRef.getAndUpdate(r -> r.at(uri(fieldName), childObj));
                } else {
                    // Store with the field name tagged so the write path can find it
                    outChildren.add(rec()
                            .at(NODE_TYPE_KEY, uri("__field"))
                            .at(uri(NAME), str(fieldName))
                            .at(uri(OUT), lst(List.of(childObj))));
                }
            } else {
                outChildren.add(childObj);
            }
        }

        if (!outChildren.isEmpty()) {
            recRef.getAndUpdate(r -> r.at(uri(OUT), lst(outChildren)));
        }

        // Capture source text for leaf nodes and nodes with anonymous
        // children (notably "modifiers" nodes whose keyword tokens are
        // anonymous and can't be reconstructed from named children alone).
        final int anonymousCount = childCount - node.getNamedChildCount();
        if (!hasNamedChildren || anonymousCount > 0) {
            final String content = node.getContent();
            if (content != null && !content.isBlank()) {
                recRef.getAndUpdate(r -> r.at(uri(TEXT), str(content)));
            }
        }

        return recRef.get();
    }

    /**
     * Returns true if the string can safely be used as an fURI segment.
     */
    private static boolean isValidUriSegment(final String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // fURI reserves: [ ] < > { } ( ) " # ? / % @ : space
            if (c == '[' || c == ']' || c == '<' || c == '>' ||
                    c == '{' || c == '}' || c == '(' || c == ')' ||
                    c == '"' || c == '#' || c == '?' || c == '/' ||
                    c == '%' || c == '@' || c == ':' || c == ' ') {
                return false;
            }
        }
        return !s.isEmpty();
    }

    /**
     * Returns true if the token looks like a keyword/identifier/literal
     * rather than structural punctuation.
     */
    private static boolean isKeywordLike(final String s) {
        if (s.isEmpty()) return false;
        final char first = s.charAt(0);
        return Character.isJavaIdentifierStart(first);
    }

    /**
     * Like rec.at(key, value) but silently drops the key if key is noobj.
     */
    private static Rec safeAt(final Rec rec, final Obj key, final Obj value) {
        return key.isNoObj() ? rec : rec.at(key, value);
    }

    // ── write: Rec → Java source ──────────────────────────────────────────

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        return ByteBuffer.wrap(this.write(obj).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String write(final Obj obj) throws MTronException {
        if (!obj.isRec()) {
            return obj.toString();
        }
        final StringBuilder sb = new StringBuilder();
        final Rec rootRec = obj.asRec();

        // If the root rec has an OUT list, write each child (typically
        // top-level declarations from a program node), separated by newlines.
        final Obj outObj = rootRec.at(uri(OUT));
        if (!outObj.isNoObj() && outObj.isLst()) {
            final var children = outObj.asLst().elements().toList();
            for (int i = 0; i < children.size(); i++) {
                final Obj child = children.get(i);
                if (child.isRec()) {
                    writeNode(child.asRec(), sb, 0);
                    // Add newline between top-level declarations so the
                    // output mirrors the original file layout
                    if (i < children.size() - 1) {
                        final String childType = child.asRec()
                                .at(NODE_TYPE_KEY).orElse(uri("")).uriValue().toString();
                        // Don't add extra newline after comments (they carry
                        // their own trailing newline)
                        if (!"line_comment".equals(childType)
                                && !"block_comment".equals(childType)) {
                            sb.append("\n");
                        }
                    }
                }
            }
        } else {
            // Single-node case — write the root node itself
            writeNode(rootRec, sb, 0);
        }
        return sb.toString();
    }

    /**
     * Recursively write a metatron Rec (representing a Java AST node) into a
     * {@link StringBuilder}.  Handles the common declaration and statement
     * types; uses a best-effort fallback for leaf nodes.
     */
    private void writeNode(final Rec rec, final StringBuilder sb, final int indent) {
        final String type = rec.at(NODE_TYPE_KEY).orElse(uri("unknown")).uriValue().toString();
        final String indentStr = "    ".repeat(Math.max(0, indent));

        // ── TEXT-first fast path ───────────────────────────────────────
        // Nodes that carry their full original source text (captured during
        // read for any node with anonymous children) can be emitted directly.
        // Structural reconstruction is only needed for nodes whose TEXT is
        // missing (pure named-children nodes like program, formal_parameters).
        final Obj textField = rec.at(uri(TEXT));
        if (!textField.isNoObj() && !"program".equals(type)
                && !"formal_parameters".equals(type) && !"argument_list".equals(type)
                && !"modifiers".equals(type)) {
            sb.append(textField.strValue());
            return;
        }

        switch (type) {
            // ── top-level ──────────────────────────────────────────────
            case "program" -> {
                writeOutChildren(rec, sb, indent);
            }

            // ── declarations ───────────────────────────────────────────
            case "class_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                sb.append("class ");
                writeNamed(rec, sb, "name");
                writeTypeParameters(rec, sb);
                writeSuperclass(rec, sb);
                writeSuperInterfaces(rec, sb);
                writePermits(rec, sb);
                sb.append(" {\n");
                writeField(rec, sb, "body", indent + 1);
                sb.append(indentStr).append("}\n");
            }
            case "interface_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                sb.append("interface ");
                writeNamed(rec, sb, "name");
                writeTypeParameters(rec, sb);
                writeExtendedInterfaces(rec, sb);
                sb.append(" {\n");
                writeField(rec, sb, "body", indent + 1);
                sb.append(indentStr).append("}\n");
            }
            case "enum_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                sb.append("enum ");
                writeNamed(rec, sb, "name");
                writeImplements(rec, sb);
                sb.append(" {\n");
                writeField(rec, sb, "body", indent + 1);
                sb.append(indentStr).append("}\n");
            }
            case "record_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                sb.append("record ");
                writeNamed(rec, sb, "name");
                writeTypeParameters(rec, sb);
                sb.append("(");
                writeField(rec, sb, "parameters", indent);
                sb.append(")");
                writeImplements(rec, sb);
                sb.append(" {\n");
                writeField(rec, sb, "body", indent + 1);
                sb.append(indentStr).append("}\n");
            }
            case "annotation_type_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                sb.append("@interface ");
                writeNamed(rec, sb, "name");
                sb.append(" {\n");
                writeField(rec, sb, "body", indent + 1);
                sb.append(indentStr).append("}\n");
            }

            // ── members ────────────────────────────────────────────────
            case "method_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                writeFieldType(rec, sb, "type");
                sb.append(" ");
                writeNamed(rec, sb, "name");
                sb.append("(");
                writeField(rec, sb, "parameters", indent);
                sb.append(")");
                writeThrows(rec, sb);
                final Obj body = rec.at(uri("body"));
                if (!body.isNoObj() && body.isRec()) {
                    sb.append(" ");
                    writeBlock(body.asRec(), sb, indent);
                } else {
                    sb.append(";\n");
                }
            }
            case "constructor_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                writeNamed(rec, sb, "name");
                sb.append("(");
                writeField(rec, sb, "parameters", indent);
                sb.append(")");
                writeThrows(rec, sb);
                final Obj body = rec.at(uri("body"));
                if (!body.isNoObj() && body.isRec()) {
                    sb.append(" ");
                    writeBlock(body.asRec(), sb, indent);
                } else {
                    sb.append(";\n");
                }
            }
            case "field_declaration" -> {
                writeModifiers(rec, sb, indentStr);
                writeFieldType(rec, sb, "type");
                sb.append(" ");
                writeField(rec, sb, "declarator", indent);
                sb.append(";\n");
            }

            // ── statements ─────────────────────────────────────────────
            case "block", "class_body", "interface_body",
                 "enum_body", "annotation_type_body",
                 "record_body", "switch_block" -> {
                writeBlock(rec, sb, indent);
            }
            case "expression_statement" -> {
                sb.append(indentStr);
                writeOutChildren(rec, sb, indent);
                sb.append(";\n");
            }
            case "return_statement" -> {
                sb.append(indentStr).append("return");
                final Obj val = rec.at(uri("value"));
                if (!val.isNoObj()) {
                    sb.append(" ");
                    if (val.isRec()) writeNode(val.asRec(), sb, indent);
                    else sb.append(textOf(val));
                }
                sb.append(";\n");
            }
            case "if_statement" -> {
                sb.append(indentStr).append("if (");
                writeField(rec, sb, "condition", indent);
                sb.append(") ");
                writeField(rec, sb, "consequence", indent);
                final Obj alt = rec.at(uri("alternative"));
                if (!alt.isNoObj()) {
                    sb.append(indentStr).append("else ");
                    if (alt.isRec()) writeNode(alt.asRec(), sb, indent);
                }
            }
            case "for_statement" -> {
                sb.append(indentStr).append("for (");
                writeOutChildren(rec, sb, indent);
                sb.append(") ");
                writeField(rec, sb, "body", indent);
            }
            case "while_statement" -> {
                sb.append(indentStr).append("while (");
                writeField(rec, sb, "condition", indent);
                sb.append(") ");
                writeField(rec, sb, "body", indent);
            }
            case "try_statement" -> {
                sb.append(indentStr).append("try ");
                writeField(rec, sb, "body", indent);
                // catch_clauses may be a list or a single catch_clause node
                final Obj catches = rec.at(uri("catch_clauses"));
                if (!catches.isNoObj()) {
                    if (catches.isLst()) {
                        catches.asLst().elements().forEach(c -> {
                            if (c.isRec()) writeNode(c.asRec(), sb, indent);
                        });
                    } else if (catches.isRec()) {
                        writeNode(catches.asRec(), sb, indent);
                    }
                }
                final Obj finallyClause = rec.at(uri("finally_clause"));
                if (!finallyClause.isNoObj() && finallyClause.isRec()) {
                    sb.append(indentStr).append("finally ");
                    writeField(finallyClause.asRec(), sb, "body", indent);
                }
            }
            case "catch_clause" -> {
                sb.append("catch (");
                writeField(rec, sb, "parameter", indent);
                sb.append(") ");
                writeField(rec, sb, "body", indent);
            }
            case "finally_clause" -> {
                sb.append(indentStr).append("finally ");
                writeField(rec, sb, "body", indent);
            }
            case "try_with_resources_statement" -> {
                sb.append(indentStr).append("try (");
                writeField(rec, sb, "resources", indent);
                sb.append(") ");
                writeField(rec, sb, "body", indent);
                final Obj catches = rec.at(uri("catch_clauses"));
                if (!catches.isNoObj()) {
                    if (catches.isLst()) {
                        catches.asLst().elements().forEach(c -> {
                            if (c.isRec()) writeNode(c.asRec(), sb, indent);
                        });
                    } else if (catches.isRec()) {
                        writeNode(catches.asRec(), sb, indent);
                    }
                }
                final Obj finallyClause = rec.at(uri("finally_clause"));
                if (!finallyClause.isNoObj() && finallyClause.isRec()) {
                    sb.append(indentStr).append("finally ");
                    writeField(finallyClause.asRec(), sb, "body", indent);
                }
            }
            case "local_variable_declaration" -> {
                sb.append(indentStr);
                writeFieldType(rec, sb, "type");
                sb.append(" ");
                writeField(rec, sb, "declarator", indent);
                sb.append(";\n");
            }
            case "variable_declarator" -> {
                writeNamed(rec, sb, "name");
                final Obj val = rec.at(uri("value"));
                if (!val.isNoObj()) {
                    sb.append(" = ");
                    if (val.isRec()) writeNode(val.asRec(), sb, indent);
                    else sb.append(textOf(val));
                }
            }
            case "enhanced_for_statement" -> {
                sb.append(indentStr).append("for (");
                writeFieldType(rec, sb, "type");
                sb.append(" ");
                writeNamed(rec, sb, "name");
                sb.append(" : ");
                writeField(rec, sb, "value", indent);
                sb.append(") ");
                writeField(rec, sb, "body", indent);
            }
            case "switch_statement", "switch_expression" -> {
                sb.append(indentStr).append("switch (");
                writeField(rec, sb, "condition", indent);
                sb.append(") {\n");
                writeField(rec, sb, "body", indent + 1);
                sb.append(indentStr).append("}\n");
            }
            case "switch_rule" -> {
                sb.append("    ".repeat(Math.max(0, indent)));
                writeField(rec, sb, "condition", indent);
                sb.append(" -> ");
                writeField(rec, sb, "consequence", indent);
                sb.append(";\n");
            }
            case "do_statement" -> {
                sb.append(indentStr).append("do ");
                writeField(rec, sb, "body", indent);
                sb.append(indentStr).append("while (");
                writeField(rec, sb, "condition", indent);
                sb.append(");\n");
            }
            case "break_statement" -> {
                sb.append(indentStr).append("break");
                final Obj label = rec.at(uri("label"));
                if (!label.isNoObj()) sb.append(" ").append(textOf(label));
                sb.append(";\n");
            }
            case "continue_statement" -> {
                sb.append(indentStr).append("continue");
                final Obj label = rec.at(uri("label"));
                if (!label.isNoObj()) sb.append(" ").append(textOf(label));
                sb.append(";\n");
            }
            case "throw_statement" -> {
                sb.append(indentStr).append("throw ");
                writeOutChildren(rec, sb, indent);
                sb.append(";\n");
            }
            case "assert_statement" -> {
                sb.append(indentStr).append("assert ");
                writeOutChildren(rec, sb, indent);
                sb.append(";\n");
            }
            case "synchronized_statement" -> {
                sb.append(indentStr).append("synchronized (");
                writeField(rec, sb, "lock", indent);
                sb.append(") ");
                writeField(rec, sb, "body", indent);
            }

            // ── expressions ────────────────────────────────────────────
            case "method_invocation" -> {
                final Obj object = rec.at(uri("object"));
                if (!object.isNoObj()) {
                    if (object.isRec()) writeNode(object.asRec(), sb, indent);
                    else sb.append(textOf(object));
                    sb.append(".");
                }
                writeNamed(rec, sb, "name");
                writeTypeArguments(rec, sb);
                sb.append("(");
                writeField(rec, sb, "arguments", indent);
                sb.append(")");
            }
            case "method_reference" -> {
                writeField(rec, sb, "object", indent);
                sb.append("::");
                writeField(rec, sb, "method", indent);
            }
            case "assignment_expression" -> {
                writeField(rec, sb, "left", indent);
                sb.append(" = ");
                writeField(rec, sb, "right", indent);
            }
            case "binary_expression" -> {
                writeField(rec, sb, "left", indent);
                final Obj op = rec.at(uri("operator"));
                if (!op.isNoObj()) sb.append(" ").append(textOf(op)).append(" ");
                writeField(rec, sb, "right", indent);
            }
            case "unary_expression" -> {
                final Obj op = rec.at(uri("operator"));
                if (!op.isNoObj()) sb.append(textOf(op));
                writeField(rec, sb, "operand", indent);
            }
            case "update_expression" -> {
                writeField(rec, sb, "operand", indent);
                final Obj op = rec.at(uri("operator"));
                if (!op.isNoObj()) sb.append(textOf(op));
            }
            case "instanceof_expression" -> {
                writeField(rec, sb, "left", indent);
                sb.append(" instanceof ");
                writeField(rec, sb, "right", indent);
            }
            case "cast_expression" -> {
                sb.append("(");
                writeFieldType(rec, sb, "type");
                sb.append(") ");
                writeField(rec, sb, "value", indent);
            }
            case "ternary_expression" -> {
                writeField(rec, sb, "condition", indent);
                sb.append(" ? ");
                writeField(rec, sb, "consequence", indent);
                sb.append(" : ");
                writeField(rec, sb, "alternative", indent);
            }
            case "parenthesized_expression" -> {
                sb.append("(");
                writeOutChildren(rec, sb, indent);
                sb.append(")");
            }
            case "lambda_expression" -> {
                writeField(rec, sb, "parameters", indent);
                sb.append(" -> ");
                writeField(rec, sb, "body", indent);
            }
            case "field_access" -> {
                final Obj object = rec.at(uri("object"));
                if (!object.isNoObj()) {
                    if (object.isRec()) writeNode(object.asRec(), sb, indent);
                    else sb.append(textOf(object));
                    sb.append(".");
                }
                writeNamed(rec, sb, "name");
            }
            case "object_creation_expression" -> {
                sb.append("new ");
                writeFieldType(rec, sb, "type");
                writeTypeArguments(rec, sb);
                sb.append("(");
                writeField(rec, sb, "arguments", indent);
                sb.append(")");
            }
            case "array_creation_expression" -> {
                sb.append("new ");
                writeFieldType(rec, sb, "type");
                writeField(rec, sb, "dimensions", indent);
                writeField(rec, sb, "value", indent);
            }
            case "array_initializer" -> {
                sb.append("{");
                final Obj outObj = rec.at(uri(OUT));
                if (!outObj.isNoObj() && outObj.isLst()) {
                    final var elems = outObj.asLst().elements().toList();
                    for (int i = 0; i < elems.size(); i++) {
                        if (i > 0) sb.append(", ");
                        final Obj e = elems.get(i);
                        if (e.isRec()) writeNode(e.asRec(), sb, indent);
                        else sb.append(textOf(e));
                    }
                }
                sb.append("}");
            }
            case "this" -> sb.append("this");
            case "super" -> sb.append("super");

            // ── generics ────────────────────────────────────────────────
            case "type_arguments" -> {
                sb.append("<");
                final Obj outObj = rec.at(uri(OUT));
                if (!outObj.isNoObj() && outObj.isLst()) {
                    final var typeArgs = outObj.asLst().elements().toList();
                    for (int i = 0; i < typeArgs.size(); i++) {
                        if (i > 0) sb.append(", ");
                        final Obj ta = typeArgs.get(i);
                        if (ta.isRec()) writeNode(ta.asRec(), sb, indent);
                        else sb.append(textOf(ta));
                    }
                }
                sb.append(">");
            }
            case "type_parameters" -> {
                sb.append("<");
                final Obj outObj = rec.at(uri(OUT));
                if (!outObj.isNoObj() && outObj.isLst()) {
                    final var tparams = outObj.asLst().elements().toList();
                    for (int i = 0; i < tparams.size(); i++) {
                        if (i > 0) sb.append(", ");
                        final Obj tp = tparams.get(i);
                        if (tp.isRec()) writeNode(tp.asRec(), sb, indent);
                        else sb.append(textOf(tp));
                    }
                }
                sb.append(">");
            }
            case "type_parameter" -> {
                writeNamed(rec, sb, "name");
                final Obj bound = rec.at(uri("bound"));
                if (!bound.isNoObj()) {
                    sb.append(" extends ");
                    if (bound.isRec()) writeNode(bound.asRec(), sb, indent);
                    else sb.append(textOf(bound));
                }
            }
            case "wildcard" -> {
                sb.append("?");
                final Obj bound = rec.at(uri("bound"));
                if (!bound.isNoObj()) {
                    if (!bound.isNoObj() && bound.isRec() && "extends".equals(
                            bound.asRec().at(NODE_TYPE_KEY).orElse(uri("")).uriValue().toString())) {
                        sb.append(" extends ");
                    } else {
                        sb.append(" super ");
                    }
                    writeFieldType(rec, sb, "bound");
                }
            }

            // ── parameters / arguments ─────────────────────────────────
            case "formal_parameters", "argument_list" -> {
                final Obj outObj = rec.at(uri(OUT));
                if (!outObj.isNoObj() && outObj.isLst()) {
                    final var children = outObj.asLst().elements().toList();
                    for (int i = 0; i < children.size(); i++) {
                        if (i > 0) sb.append(", ");
                        final Obj child = children.get(i);
                        if (child.isRec()) writeNode(child.asRec(), sb, indent);
                        else sb.append(textOf(child));
                    }
                }
            }
            case "formal_parameter" -> {
                writeModifiers(rec, sb, "");
                writeFieldType(rec, sb, "type");
                sb.append(" ");
                writeNamed(rec, sb, "name");
            }
            case "spread_parameter" -> {
                writeModifiers(rec, sb, "");
                writeFieldType(rec, sb, "type");
                sb.append(" ... ");
                writeNamed(rec, sb, "name");
            }

            // ── type nodes ─────────────────────────────────────────────
            case "generic_type" -> {
                writeFieldType(rec, sb, "type");
                writeTypeArguments(rec, sb);
            }
            case "type_identifier", "array_type",
                 "integral_type", "floating_point_type", "boolean_type",
                 "void_type" -> {
                writeNodeText(rec, sb);
            }
            case "scoped_type_identifier", "scoped_identifier" -> {
                // Emit dotted name segments: java.util.List
                final Obj outObj = rec.at(uri(OUT));
                if (!outObj.isNoObj() && outObj.isLst()) {
                    final var segs = outObj.asLst().elements().toList();
                    for (int i = 0; i < segs.size(); i++) {
                        if (i > 0) sb.append(".");
                        final Obj seg = segs.get(i);
                        if (seg.isRec()) writeNode(seg.asRec(), sb, indent);
                        else sb.append(textOf(seg));
                    }
                } else {
                    writeNodeText(rec, sb);
                }
            }

            // ── literals and identifiers ───────────────────────────────
            case "identifier" -> sb.append(textOf(rec));
            case "string_literal", "character_literal" -> sb.append(textOf(rec));
            case "decimal_integer_literal", "hex_integer_literal",
                 "octal_integer_literal", "binary_integer_literal" -> sb.append(textOf(rec));
            case "decimal_floating_point_literal", "hex_floating_point_literal" -> sb.append(textOf(rec));
            case "boolean_literal" -> sb.append(textOf(rec));
            case "null_literal" -> sb.append("null");
            case "text_block" -> sb.append(textOf(rec));

            // ── modifiers and annotations ──────────────────────────────
            case "modifiers" -> {
                final Obj outObj = rec.at(uri(OUT));
                if (!outObj.isNoObj() && outObj.isLst()) {
                    outObj.asLst().elements().forEach(child -> {
                        if (child.isRec()) {
                            writeNode(child.asRec(), sb, indent);
                        }
                    });
                }
            }
            case "marker_annotation" -> {
                sb.append("@");
                writeNamed(rec, sb, "name");
            }
            case "annotation" -> {
                sb.append("@");
                writeNamed(rec, sb, "name");
                sb.append("(");
                writeField(rec, sb, "arguments", indent);
                sb.append(")");
            }

            // ── comments ───────────────────────────────────────────────
            case "line_comment", "block_comment" -> {
                sb.append(indentStr).append(textOf(rec)).append("\n");
            }

            // ── package / import ───────────────────────────────────────
            case "package_declaration", "import_declaration" -> {
                // These nodes carry full-source TEXT (they have anonymous
                // keyword children).  Emit it directly — structural
                // reconstruction is fragile across grammar versions.
                final String text = textOf(rec);
                if (!text.isEmpty()) {
                    sb.append(text).append("\n");
                } else {
                    // Fallback for when TEXT is absent
                    writeOutChildren(rec, sb, indent);
                    sb.append(";\n");
                }
            }

            // ── internal wrapper for non-URI field-name children ────────
            case "__field" -> {
                // A named child whose TreeSitter field name contained
                // URI-invalid characters.  Recurse into the real child(ren)
                // stored under OUT — the field name itself is just structural
                // metadata and not needed for Java source reconstruction.
                writeOutChildren(rec, sb, indent);
            }

            // ── fallback ───────────────────────────────────────────────
            default -> {
                // Try to emit leaf text; otherwise recurse into children
                final String text = textOf(rec);
                if (!text.isEmpty()) {
                    sb.append(text);
                } else {
                    writeOutChildren(rec, sb, indent);
                }
            }
        }
    }

    // ── write helpers ─────────────────────────────────────────────────────

    /**
     * Write all children in the {@code out} field, separated by newlines.
     */
    private void writeOutChildren(final Rec rec, final StringBuilder sb, final int indent) {
        final Obj outObj = rec.at(uri(OUT));
        if (outObj.isNoObj() || !outObj.isLst()) return;
        outObj.asLst().elements().forEach(child -> {
            if (child.isRec()) {
                writeNode(child.asRec(), sb, indent);
            }
        });
    }

    /**
     * Write the child reachable via a named field key.
     */
    private void writeField(final Rec rec, final StringBuilder sb,
                            final String fieldName, final int indent) {
        final Obj field = rec.at(uri(fieldName));
        if (field.isNoObj()) return;
        if (field.isRec()) {
            writeNode(field.asRec(), sb, indent);
        } else if (field.isLst()) {
            field.asLst().elements().forEach(child -> {
                if (child.isRec()) writeNode(child.asRec(), sb, indent);
            });
        } else {
            sb.append(textOf(field));
        }
    }

    /**
     * Write the {@code name} field of a declaration.
     */
    private void writeNamed(final Rec rec, final StringBuilder sb, final String fieldName) {
        final Obj name = rec.at(uri(fieldName));
        if (name.isNoObj()) return;
        if (name.isRec()) writeNode(name.asRec(), sb, 0);
        else sb.append(textOf(name));
    }

    /**
     * Write the {@code type} field (used for return types, field types, etc.).
     */
    private void writeFieldType(final Rec rec, final StringBuilder sb, final String fieldName) {
        final Obj typeField = rec.at(uri(fieldName));
        if (typeField.isNoObj()) return;
        if (typeField.isRec()) {
            writeNode(typeField.asRec(), sb, 0);
        } else {
            sb.append(textOf(typeField));
        }
    }

    /**
     * Write a block node and its body, handling indentation.
     */
    private void writeBlock(final Rec blockRec, final StringBuilder sb, final int indent) {
        final String indentStr = "    ".repeat(Math.max(0, indent));
        sb.append("{\n");
        writeField(blockRec, sb, "body", indent + 1);
        sb.append(indentStr).append("}\n");
    }

    private void writeModifiers(final Rec rec, final StringBuilder sb, final String indentStr) {
        final Obj mods = rec.at(uri("modifiers"));
        if (mods.isNoObj() || !mods.isRec()) return;
        final Rec modsRec = mods.asRec();

        // Prefer the full source-range TEXT (captured because modifiers
        // nodes mix named annotations with anonymous keyword tokens).
        final Obj text = modsRec.at(uri(TEXT));
        if (!text.isNoObj()) {
            sb.append(indentStr).append(text.strValue()).append(" ");
            return;
        }

        // Fallback: iterate OUT children (annotations on a parameter, etc.)
        sb.append(indentStr);
        final Obj outObj = modsRec.at(uri(OUT));
        if (!outObj.isNoObj() && outObj.isLst()) {
            outObj.asLst().elements().forEach(child -> {
                if (child.isRec()) {
                    writeNode(child.asRec(), sb, 0);
                    if (!sb.toString().endsWith(" ") && !sb.toString().endsWith("\n")) {
                        sb.append(" ");
                    }
                }
            });
        }
        if (!sb.toString().endsWith(" ") && !sb.toString().endsWith("\n")) {
            sb.append(" ");
        }
    }

    private void writeTypeArguments(final Rec rec, final StringBuilder sb) {
        final Obj ta = rec.at(uri("type_arguments"));
        if (!ta.isNoObj() && ta.isRec()) {
            writeNode(ta.asRec(), sb, 0);
        }
    }

    private void writeTypeParameters(final Rec rec, final StringBuilder sb) {
        final Obj tp = rec.at(uri("type_parameters"));
        if (!tp.isNoObj() && tp.isRec()) {
            writeNode(tp.asRec(), sb, 0);
        }
    }

    private void writeSuperclass(final Rec rec, final StringBuilder sb) {
        final Obj sc = rec.at(uri("superclass"));
        if (!sc.isNoObj()) {
            sb.append(" extends ");
            if (sc.isRec()) writeNode(sc.asRec(), sb, 0);
            else sb.append(textOf(sc));
        }
    }

    private void writeSuperInterfaces(final Rec rec, final StringBuilder sb) {
        final Obj si = rec.at(uri("super_interfaces"));
        writeInterfaceList(si, sb, " implements ");
    }

    private void writeExtendedInterfaces(final Rec rec, final StringBuilder sb) {
        final Obj ei = rec.at(uri("super_interfaces"));
        writeInterfaceList(ei, sb, " extends ");
    }

    private void writeInterfaceList(final Obj interfaces, final StringBuilder sb, final String keyword) {
        if (interfaces.isNoObj()) return;
        sb.append(keyword);
        if (interfaces.isLst()) {
            final var list = interfaces.asLst().elements().toList();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                final Obj iface = list.get(i);
                if (iface.isRec()) {
                    writeNode(iface.asRec(), sb, 0);
                } else {
                    sb.append(textOf(iface));
                }
            }
        } else if (interfaces.isRec()) {
            writeNode(interfaces.asRec(), sb, 0);
        }
    }

    private void writeImplements(final Rec rec, final StringBuilder sb) {
        final Obj si = rec.at(uri("super_interfaces"));
        writeInterfaceList(si, sb, " implements ");
    }

    private void writePermits(final Rec rec, final StringBuilder sb) {
        final Obj permits = rec.at(uri("permits"));
        if (permits.isNoObj()) return;
        sb.append(" permits ");
        if (permits.isLst()) {
            final var list = permits.asLst().elements().toList();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(textOf(list.get(i)));
            }
        } else {
            sb.append(textOf(permits));
        }
    }

    private void writeThrows(final Rec rec, final StringBuilder sb) {
        final Obj throwsObj = rec.at(uri("throws"));
        if (throwsObj.isNoObj()) return;
        sb.append(" throws ");
        if (throwsObj.isLst()) {
            final var list = throwsObj.asLst().elements().toList();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(textOf(list.get(i)));
            }
        } else {
            sb.append(textOf(throwsObj));
        }
    }

    /**
     * Output the direct text content of a leaf node.
     */
    private void writeNodeText(final Rec rec, final StringBuilder sb) {
        final String text = textOf(rec);
        if (!text.isEmpty()) sb.append(text);
    }

    /**
     * Extract a best-effort text representation from a Rec or scalar Obj.
     */
    private String textOf(final Obj obj) {
        if (obj.isNoObj()) return "";
        if (obj.isRec()) {
            final Obj text = obj.asRec().at(uri(TEXT));
            if (!text.isNoObj()) return text.strValue();
            // Recurse into OUT to concatenate children (e.g. for qualified names)
            final Obj out = obj.asRec().at(uri(OUT));
            if (!out.isNoObj() && out.isLst()) {
                final StringBuilder sb = new StringBuilder();
                out.asLst().elements().forEach(child -> sb.append(textOf(child)));
                return sb.toString();
            }
            return "";
        }
        if (obj.isStr()) return obj.strValue();
        if (obj.isUri()) return obj.uriValue().toString();
        return obj.toString();
    }

    private String textOf(final Rec rec) {
        return textOf((Obj) rec);
    }

    // ── identity ──────────────────────────────────────────────────────────

    @Override
    public fURI vid() {
        return OBJ_JAVA_SERIALIZER_TID;
    }
}
