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

package studio.phaseshift.metatron.isa.ide.parser;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Tree;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjJavaSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.ide.ideInstSet.IDE_JAVA_TID;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.OBJ_IDE_JAVA_SERIALIZER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_SERIALIZER_TID;

/**
 * The coarse-schema Java serializer ("CS").  Parses Java source into the
 * agent-facing rec::T schema defined in
 * {@code docs/design/codespaces/codespace-functor.md §0}: package, imports,
 * classes, and ordered members with complete source spans.  The fine-grained
 * TreeSitter statement/expression tree is collapsed into complete text spans
 * at the declaration level — the skeleton an agent conceptualizes code by.
 *
 * <p>Lossless by construction: members are stored in document order, each
 * member's {@code text} folds in the whitespace preceding it, and the class
 * {@code header} (up to and including the opening brace) and {@code footer}
 * (from after the last member to end) account for every byte.  Concatenating
 * {@code header + members.text + footer} reproduces the class source exactly.</p>
 *
 * <p><b>Addressing and editing — the shared schema:</b> everything
 * language-agnostic about the coarse rec lives in {@link ObjIDESchema}.  On
 * every parse, {@code decorate} derives two fields per node —
 * {@code ordinal} (rank among its same-named siblings, document order: a
 * lone {@code apply} is {@code apply/0}, the second duplicate is
 * {@code apply/1}) and {@code path} (the node's own address,
 * {@code classes/{name}/{ordinal}/members/{kind}/{name}/{ordinal}}, nameless
 * members dropping the name slot).  Never stored, so never stale.
 * {@code locate} turns an address back into the node rec; {@link #edit}
 * replaces a node's span in the source and reparses — an unparseable
 * replacement is rejected with its first syntax error instead of saved;
 * the rec that comes back rewrites byte-for-byte to the edited source.
 * Nested classes parse through the same schema as {@code kind=>class}
 * members carrying their own {@code members}.</p>
 *
 * <p>Child lists ({@code imports}, {@code classes}, {@code members}) are
 * {@code lst::T} — ordered, duplicate-allowed — matching source order and the
 * mtron container semantics ({@code rec::T} is keyed, {@code lst::T} is
 * ordered, {@code #{*}::T} is unordered/bulkable).</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjJavaIDESerializer extends AbstractObjSerializer<String> {

    private static final ObjJavaIDESerializer INSTANCE = new ObjJavaIDESerializer();

    private static final Parser PARSER;

    static {
        ObjJavaSerializer.loadNativeLibrary();
        PARSER = Parser.getFor(Language.JAVA);
    }

    public static ObjJavaIDESerializer single() {
        return INSTANCE;
    }

    public ObjJavaIDESerializer() {
        super(OBJ_SERIALIZER_TID, OBJ_IDE_JAVA_SERIALIZER_TID);
    }

    public static Obj parse(final String javaSource) {
        return INSTANCE.read(javaSource);
    }

    // ── read: Java source → coarse Rec ──────────────────────────────────

    @Override
    public Obj read(final String source) throws MTronException {
        try (final Tree tree = PARSER.parse(source)) {
            final Node root = tree.getRootNode();
            final String programContent = root.getContent();
            Rec result = rec();
            int cursor = 0;
            boolean preambleSet = false;
            int prevClassEnd = -1;
            for (int i = 0; i < root.getChildCount(); i++) {
                final Node child = root.getChild(i);
                if (!child.isNamed()) continue;
                final String type = child.getType();
                final String content = child.getContent();
                final int idx = programContent.indexOf(content, cursor);
                if (idx < 0) continue;
                if (isTypeDeclaration(type)) {
                    Obj classRec = typeDeclaration(child);
                    if (!preambleSet) {
                        // everything before the first class, verbatim
                        result = result.at(uri("preamble"), str(programContent.substring(0, idx)));
                        preambleSet = true;
                    } else {
                        // gap between this class and the previous one
                        classRec = classRec.asRec().at(uri("sep"), str(programContent.substring(prevClassEnd, idx)));
                    }
                    result = appendToList(result, uri("classes"), classRec);
                    prevClassEnd = idx + content.length();
                } else if ("package_declaration".equals(type)) {
                    result = result.at(uri("package"), str(content));
                } else if ("import_declaration".equals(type)) {
                    result = appendToList(result, uri("imports"), str(content));
                }
                cursor = idx + content.length();
            }
            if (preambleSet) {
                result = result.at(uri("postscript"), str(programContent.substring(prevClassEnd)));
            }
            // the shared addressing contract (ordinal + path per node) —
            // language-agnostic, so it lives in the schema, not the adapter
            return ObjIDESchema.decorate(result).selfTID(IDE_JAVA_TID);
        } catch (final MTronException e) {
            throw e;
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to parse java source into coarse schema");
        }
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return read(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    // ── write: coarse Rec → Java source ─────────────────────────────────
    // the emit is pure contract concatenation, shared with every other
    // language adapter — the schema owns it (and extends it with the
    // nested-type dispatch)

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        return ByteBuffer.wrap(this.write(obj).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String write(final Obj obj) throws MTronException {
        return ObjIDESchema.write(obj);
    }

    // ── edit by address (span-replace + parse gate) ────────────────────

    /**
     * Replace the node named by {@code address}
     * ({@code classes/{name}/{ordinal}[/members/{kind}/{name}/{ordinal}...]})
     * with {@code newText} and return the fresh, fully-reparsed rec.
     *
     * <p>The span is located in the source by text (a document-order walk —
     * never index arithmetic), the replacement spliced into that span, and
     * the result must parse before anything is returned: an unparseable
     * replacement is rejected with its first syntax error (line, column),
     * not saved.  The rec that comes back is the parse of the new source,
     * so {@code write(edit(src, a, t))} reproduces the edited source
     * byte-for-byte.</p>
     */
    public static Obj edit(final String source, final String address, final String newText) {
        final Rec rooted = single().read(source).asRec();
        final Rec target = ObjIDESchema.locate(rooted, address);
        final int[] span = ObjIDESchema.spanOf(source, rooted, target);
        final String newSource = source.substring(0, span[0]) + newText + source.substring(span[1]);
        final String error = firstError(newSource);
        if (null != error) {
            throw MTronException.of("edit at %s aborted: %s", address, error);
        }
        return single().read(newSource);
    }

    /**
     * The first tree-sitter syntax error in the source — line and column —
     * or null if it parses clean.
     */
    public static String firstError(final String source) {
        return ObjIDESchema.firstError(Language.JAVA, source);
    }

    // ── structural helpers ──────────────────────────────────────────────

    private static boolean isTypeDeclaration(final String type) {
        return switch (type) {
            case "class_declaration", "interface_declaration", "enum_declaration",
                 "record_declaration", "annotation_type_declaration" -> true;
            default -> false;
        };
    }

    /**
     * Strip a leading {@code extends}/{@code implements} keyword from a type-clause span.
     */
    private static String bareType(final String content) {
        final String trimmed = content.trim();
        if (trimmed.startsWith("extends ")) return trimmed.substring("extends ".length());
        if (trimmed.startsWith("implements ")) return trimmed.substring("implements ".length());
        return trimmed;
    }

    private static Obj typeDeclaration(final Node node) {
        final String kind = node.getType();
        final Rec base = rec().at(uri("kind"), uri(kind));

        final Node name = node.getChildByFieldName("name");
        final Node superclass = node.getChildByFieldName("superclass");
        final Node interfaces = node.getChildByFieldName("interfaces");

        Rec typeRec = base;
        if (name != null) typeRec = typeRec.at(uri("name"), str(name.getContent()));
        // bare type names — strip the 'extends'/'implements' keyword; a future
        // codespace layer may resolve these into !* redirects to the superclass's
        // own cs_{lang}::T rec (needs project context a pure parse lacks).
        if (superclass != null) typeRec = typeRec.at(uri("superclass"), str(bareType(superclass.getContent())));
        if (interfaces != null) typeRec = typeRec.at(uri("interfaces"), str(bareType(interfaces.getContent())));

        final Node body = node.getChildByFieldName("body");
        if (body != null) {
            final String classContent = node.getContent();
            final String bodyContent = body.getContent();
            final int bodyIdx = classContent.indexOf(bodyContent);
            if (bodyIdx >= 0) {
                // header: up to and including the opening brace
                typeRec = typeRec.at(uri("header"), str(classContent.substring(0, bodyIdx + 1)));
                int cursor = bodyIdx + 1;
                final List<Obj> members = new ArrayList<>();
                for (final Node m : body.getNamedChildren()) {
                    final String content = m.getContent();
                    final int relStart = classContent.indexOf(content, cursor);
                    if (relStart < 0) continue; // defensive: never expected
                    final String leadingGap = classContent.substring(cursor, relStart);
                    members.add(member(m, leadingGap));
                    cursor = relStart + content.length();
                }
                if (!members.isEmpty()) typeRec = typeRec.at(uri("members"), lst(members));
                // footer: from after the last member to end of class (the closing brace + trailing)
                typeRec = typeRec.at(uri("footer"), str(classContent.substring(cursor)));
            }
        }
        return typeRec;
    }

    private static Obj member(final Node node, final String leadingGap) {
        final String type = node.getType();
        final String content = node.getContent();
        // the stored text folds in the whitespace/comments preceding this member
        final String text = leadingGap + content;

        return switch (type) {
            case "method_declaration", "constructor_declaration" -> {
                final String kind = "constructor_declaration".equals(type) ? "constructor" : "method";
                Rec m = rec().at(uri("kind"), uri(kind));
                final Node name = node.getChildByFieldName("name");
                if (name != null) m = m.at(uri("name"), str(name.getContent()));
                m = m.at(uri("signature"), str(signature(node, name)));
                final Node bodyNode = node.getChildByFieldName("body");
                if (bodyNode != null) {
                    final String bodyContent = bodyNode.getContent();
                    final int bodyIdx = content.indexOf(bodyContent);
                    if (bodyIdx >= 0) {
                        // header + body + footer — body is the editable lineq unit.
                        // header ends BEFORE the opening brace: the body block carries its own
                        // { ... } braces, so emitting both would double them.
                        m = m.at(uri("header"), str(leadingGap + content.substring(0, bodyIdx)));
                        m = m.at(uri("body"), str(bodyContent));
                        m = m.at(uri("footer"), str(content.substring(bodyIdx + bodyContent.length())));
                        m = m.at(uri("text"), str(text)); // derived read-only concat
                    }
                }
                if (m.at(uri("header")).isNoObj()) {
                    // abstract / interface method — no body, text is the whole span
                    m = m.at(uri("text"), str(text));
                }
                yield m;
            }
            case "field_declaration" -> {
                Rec m = rec().at(uri("kind"), uri("field")).at(uri("text"), str(text));
                final Node declarator = node.getChildByFieldName("declarator");
                final Node fname = declarator != null ? declarator.getChildByFieldName("name") : null;
                if (fname != null) m = m.at(uri("name"), str(fname.getContent()));
                yield m;
            }
            case "class_declaration", "interface_declaration", "enum_declaration",
                 "record_declaration", "annotation_type_declaration" -> {
                // a nested type — the same schema, one level down; kind is
                // normalized to 'class' (the members/ address slot) and the
                // leading gap folds into the header, so the write stays
                // byte-exact
                Rec nested = typeDeclaration(node).asRec().at(uri("kind"), uri("class"));
                final Obj nestedHeader = nested.at(uri("header"));
                if (!nestedHeader.isNoObj()) {
                    nested = nested.at(uri("header"), str(leadingGap + nestedHeader.strValue()));
                }
                yield nested.at(uri("text"), str(text));
            }
            case "line_comment", "block_comment" -> rec().at(uri("kind"), uri("comment"))
                    .at(uri("text"), str(text));
            default -> rec().at(uri("kind"), uri("other")).at(uri("text"), str(text));
        };
    }

    /**
     * Derived signature: {@code type name(parameters)} for methods, {@code name(parameters)} for constructors.
     */
    private static String signature(final Node node, final Node name) {
        final Node type = node.getChildByFieldName("type");
        final Node parameters = node.getChildByFieldName("parameters");
        final StringBuilder sig = new StringBuilder();
        if (type != null) sig.append(type.getContent()).append(" ");
        if (name != null) sig.append(name.getContent());
        if (parameters != null) sig.append(parameters.getContent());
        return sig.toString();
    }

    /**
     * Pure append to a lst field on a rec (rec::T is immutable-style).
     */
    private static Rec appendToList(final Rec rec, final Obj key, final Obj value) {
        final Obj existing = rec.at(key);
        if (existing.isNoObj() || !existing.isLst()) {
            return rec.at(key, lst(List.of(value)));
        }
        final List<Obj> list = new ArrayList<>(existing.asLst().elements().toList());
        list.add(value);
        return rec.at(key, lst(list));
    }
}
