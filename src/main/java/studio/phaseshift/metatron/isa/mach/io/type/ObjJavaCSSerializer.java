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
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.CS_JAVA_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_CS_JAVA_SERIALIZER_TID;

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
 * <p>Child lists ({@code imports}, {@code classes}, {@code members}) are
 * {@code lst::T} — ordered, duplicate-allowed — matching source order and the
 * mtron container semantics ({@code rec::T} is keyed, {@code lst::T} is
 * ordered, {@code #{*}::T} is unordered/bulkable).</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjJavaCSSerializer extends AbstractObjSerializer<String> {

    public static final fURI OBJ_CS_JAVA_SERIALIZER_VID = OBJ_CS_JAVA_SERIALIZER_TID;

    private static final ObjJavaCSSerializer INSTANCE = new ObjJavaCSSerializer();

    private static final Parser PARSER;

    static {
        ObjJavaSerializer.loadNativeLibrary();
        PARSER = Parser.getFor(Language.JAVA);
    }

    public static ObjJavaCSSerializer single() {
        return INSTANCE;
    }

    public ObjJavaCSSerializer() {
        super(OBJ_CS_JAVA_SERIALIZER_TID, OBJ_CS_JAVA_SERIALIZER_TID);
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
            return result.selfTID(CS_JAVA_TID);
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

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        return ByteBuffer.wrap(this.write(obj).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String write(final Obj obj) throws MTronException {
        if (!obj.isRec()) return obj.toString();
        final Rec root = obj.asRec();
        final StringBuilder sb = new StringBuilder();

        final Obj preamble = root.at(uri("preamble"));
        final Obj classes = root.at(uri("classes"));
        final Obj postscript = root.at(uri("postscript"));

        if (!preamble.isNoObj()) {
            sb.append(preamble.strValue());
        } else {
            // class-less fallback: reconstruct package + imports
            final Obj pkg = root.at(uri("package"));
            if (!pkg.isNoObj()) sb.append(pkg.strValue()).append("\n\n");
            final Obj imports = root.at(uri("imports"));
            if (!imports.isNoObj() && imports.isLst()) {
                imports.asLst().elements().forEach(i -> sb.append(i.strValue()).append("\n"));
                sb.append("\n");
            }
        }

        if (!classes.isNoObj() && classes.isLst()) {
            for (final Obj c : classes.asLst().elements().toList()) {
                if (!c.isRec()) continue;
                final Rec cls = c.asRec();
                final Obj sep = cls.at(uri("sep"));
                if (!sep.isNoObj()) sb.append(sep.strValue());
                writeType(cls, sb);
            }
        }

        if (!postscript.isNoObj()) sb.append(postscript.strValue());
        return sb.toString();
    }

    private static void writeType(final Rec type, final StringBuilder sb) {
        final Obj header = type.at(uri("header"));
        final Obj members = type.at(uri("members"));
        final Obj footer = type.at(uri("footer"));
        if (!header.isNoObj()) sb.append(header.strValue());
        if (!members.isNoObj() && members.isLst()) {
            members.asLst().elements().forEach(m -> {
                if (m.isRec()) writeMember(m.asRec(), sb);
            });
        }
        if (!footer.isNoObj()) sb.append(footer.strValue());
    }

    /**
     * Emit a member.  Methods/constructors decompose into header + body + footer
     * (so a body edit writes through without a stale full-text); everything else
     * emits its complete text span directly.
     */
    private static void writeMember(final Rec m, final StringBuilder sb) {
        final Obj header = m.at(uri("header"));
        final Obj body = m.at(uri("body"));
        final Obj footer = m.at(uri("footer"));
        if (!header.isNoObj()) {
            sb.append(header.strValue());
            if (!body.isNoObj()) sb.append(body.strValue());
            if (!footer.isNoObj()) sb.append(footer.strValue());
        } else {
            final Obj text = m.at(uri("text"));
            if (!text.isNoObj()) sb.append(text.strValue());
        }
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
