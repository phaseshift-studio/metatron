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

import ch.usi.si.seart.treesitter.*;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.ORDINAL;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The code schema shared by every language's IDE serializer —
 * {@link ObjJavaIDESerializer} is the first adapter.  The schema knows the
 * shape of a coarse rec and not a single source language: the per-language
 * work (TreeSitter node → coarse rec) stays in the adapter; everything on
 * top of the contract — addressing, locate, span edit, lossless write,
 * error diagnosis — lives here, once, for all languages.
 *
 * <p><b>The contract</b> (what every adapter's {@code read} must produce):
 * <pre>
 * file   =&gt; rec(preamble, classes:lst, postscript, [package, imports])
 * type   =&gt; rec(kind, name, header, members:lst, footer, text?)
 * member =&gt; rec(kind, text, [name, signature, header, body, footer])
 * </pre>
 * A member may itself be a type (a nested class) — it then carries its own
 * {@code members}, and {@link #write} dispatches on that.
 *
 * <h3>Addressing — name + ordinal</h3>
 * Every node carries two <i>derived</i> fields, computed by
 * {@link #decorate} on every parse (never stored, so never stale):
 * <ul>
 *   <li>{@code ordinal} ({@code int}) — the rank of the node within its
 *       same-named group, in document order: a named member groups by
 *       {@code (kind, name)}, a nameless member groups by {@code kind}, a
 *       class groups by {@code name}.  A node with no same-named sibling
 *       is {@code 0}; {@code N &gt; 0} exists only because of a duplicate.</li>
 *   <li>{@code path} ({@code str}) — the node's own address:
 *       {@code classes/{name}/{ordinal}} for a type,
 *       {@code .../members/{kind}/{name}/{ordinal}} for a named member,
 *       {@code .../members/{kind}/{ordinal}} for a nameless one.  The
 *       {@code code/{file}/{ordinal}} slot is prepended by the space that
 *       hosts the code graph — the schema never sees a filename.</li>
 * </ul>
 * {@link #locate} is the inverse: an address string back to the node rec,
 * with the same-named candidates listed in every error so a miss is
 * recoverable, never silent.
 *
 * <h3>Losslessness</h3>
 * {@link #write} is pure concatenation — preamble, then each type
 * ({@code header + members + footer}), then postscript — where a member's
 * {@code text} (which folds in its leading whitespace) is the atom.
 * {@code write(parse(src)) == src} byte-for-byte, nested types included.
 *
 * <h3>Span edit + gate</h3>
 * {@link #spanOf} finds a node's text span inside the source (a
 * document-order walk, exact even for duplicated spans);
 * {@link #firstError} reports the first tree-sitter syntax error with
 * line and column.  An adapter composes them into an edit that fails
 * closed: an unparseable replacement is rejected with its diagnostic,
 * not saved.
 *
 * <p>Precondition for the tree-sitter entry points: the native
 * {@code libjava-tree-sitter} binding is loaded — the adapters do that in
 * their static block (as {@link ObjJavaIDESerializer} does).</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ObjIDESchema {

    private static final String CLASSES = "classes";
    private static final String MEMBERS = "members";
    private static final String PATH = "path";

    private ObjIDESchema() {
        // static provider
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // decorate — derive ordinal + path over the whole tree
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Return the rec with the addressing contract derived on every node:
     * each class gets {@code ordinal} (rank among same-named classes,
     * document order — a Java file's top-level names are structurally
     * distinct, so this is invariant 0 there) and {@code path}; every
     * member (recursively) gets the same within its type.
     */
    public static Rec decorate(final Rec rooted) {
        final Obj classes = rooted.at(uri(CLASSES));
        if (classes.isNoObj() || !classes.isLst())
            return rooted;
        final Map<String, Integer> rank = new HashMap<>();
        final List<Obj> out = new ArrayList<>();
        for (final Obj c : classes.asLst().elements().toList()) {
            if (!c.isRec()) {
                out.add(c);
                continue;
            }
            final Rec cls = c.asRec();
            final String name = nameOf(cls);
            final int ordinal = rank.merge(name, 1, Integer::sum) - 1;
            final String path = CLASSES + "/" + name + "/" + ordinal;
            out.add(decorateMembers(cls, path)
                    .at(uri(ORDINAL), jnt(ordinal))
                    .at(uri(PATH), str(path)));
        }
        return rooted.at(uri(CLASSES), lst(out));
    }

    private static Rec decorateMembers(final Rec type, final String base) {
        final Obj members = type.at(uri(MEMBERS));
        if (members.isNoObj() || !members.isLst())
            return type;
        final Map<String, Integer> rank = new HashMap<>();
        final List<Obj> out = new ArrayList<>();
        for (final Obj mobj : members.asLst().elements().toList()) {
            if (!mobj.isRec()) {
                out.add(mobj);
                continue;
            }
            final Rec m = mobj.asRec();
            final String kind = kindOf(m);
            final String name = nameOf(m);
            // named members group by (kind, name), nameless by kind
            final String group = name.isEmpty() ? kind : kind + "\u0000" + name;
            final int ordinal = rank.merge(group, 1, Integer::sum) - 1;
            // the slot for a name is present when — and only when — the member has a name
            final String path = base + "/" + MEMBERS + "/" + kind + (name.isEmpty() ? "" : "/" + name) + "/" + ordinal;
            // a nested type carries its own members — same contract, one level down
            final Rec built = decorateMembers(m, path)
                    .at(uri(ORDINAL), jnt(ordinal))
                    .at(uri(PATH), str(path));
            out.add(built);
        }
        return type.at(uri(MEMBERS), lst(out));
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // locate — address string → node rec
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Resolve an address to the node rec it names:
     * {@code classes/{name}/{ordinal?}} for a type, then
     * {@code members/{kind}/{name?}/{ordinal?}} per level (a name slot is
     * present when the member has a name; ordinals default to 0).
     * Every miss throws with the candidates that do exist at that level.
     */
    public static Rec locate(final Rec rooted, final String address) {
        if (address == null || address.isBlank())
            throw MTronException.of("locate: an address is required");
        final String[] s = address.split("/");
        if (s.length < 2 || !CLASSES.equals(s[0]))
            throw MTronException.of("locate: a bad address '%s' — expected classes/{name}/{ordinal} [...]", address);
        final int classOrdinal = isNum(s, 2) ? num(s[2]) : 0;
        Rec cur = matchClass(rooted, s[1], classOrdinal);
        int i = isNum(s, 2) ? 3 : 2;
        while (i < s.length) {
            if (!MEMBERS.equals(s[i]))
                throw MTronException.of("locate: expected '%s' where '%s' has '%s'", MEMBERS, address, s[i]);
            i++;
            if (i >= s.length)
                throw MTronException.of("locate: expected a member kind after '%s' in '%s'", MEMBERS, address);
            final String kind = s[i++];
            String name = null;
            int ordinal = 0;
            if (i < s.length && !isNum(s, i)) {
                name = s[i++];
            }
            if (i < s.length && isNum(s, i)) {
                ordinal = num(s[i++]);
            }
            cur = matchMember(cur, kind, name, ordinal);
        }
        return cur;
    }

    private static Rec matchClass(final Rec rooted, final String name, final int ordinal) {
        final Obj classes = rooted.at(uri(CLASSES));
        if (classes.isNoObj() || !classes.isLst())
            throw MTronException.of("locate: no class named '%s' at ordinal %d — this file has no classes", name, ordinal);
        int rank = 0;
        for (final Obj c : classes.asLst().elements().toList()) {
            if (!c.isRec()) {
                continue;
            }
            if (name.equals(nameOf(c.asRec())) && rank++ == ordinal) {
                return c.asRec();
            }
        }
        throw MTronException.of("locate: no class named '%s' at ordinal %d — classes in this file: %s",
                name, ordinal, labels(classes, false));
    }

    private static Rec matchMember(final Rec type, final String kind, final String name, final int ordinal) {
        final Obj members = type.at(uri(MEMBERS));
        if (members.isNoObj() || !members.isLst()) {
            throw missing(kind, name, ordinal, "(no members in this type)");
        }
        int rank = 0;
        for (final Obj mobj : members.asLst().elements().toList()) {
            if (!mobj.isRec()) {
                continue;
            }
            final Rec m = mobj.asRec();
            if (kind.equals(kindOf(m)) && groupMatch(m, name) && rank++ == ordinal) {
                return m;
            }
        }
        throw missing(kind, name, ordinal, labels(members, true));
    }

    private static boolean groupMatch(final Rec m, final String name) {
        // a name slot in the address binds only a member whose name is exactly that name;
        // its absence binds a nameless member (both directions, so a miss never
        // silently grabs a differently-named sibling)
        final String memberName = nameOf(m);
        if (name == null) {
            return memberName.isEmpty();
        }
        return name.equals(memberName);
    }

    private static MTronException missing(final String kind, final String name, final int ordinal, final String candidates) {
        final String what = name == null ? "a '%s' member".formatted(kind)
                : "a '%s' member named '%s'".formatted(kind, name);
        return MTronException.of("locate: no %s at ordinal %d — the candidates here: %s", what, ordinal, candidates);
    }

    private static String labels(final Obj collection, final boolean kinds) {
        if (collection.isNoObj() || !collection.isLst()) {
            return "(none)";
        }
        final StringBuilder sb = new StringBuilder();
        for (final Obj e : collection.asLst().elements().toList()) {
            if (!e.isRec()) {
                continue;
            }
            final Rec r = e.asRec();
            final String name = nameOf(r);
            final String label = name.isEmpty() ? kindOf(r) : (kinds ? kindOf(r) + "/" + name : name);
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(label).append("/").append(ordinalOf(r));
        }
        return sb.isEmpty() ? "(none)" : sb.toString();
    }

    private static boolean isNum(final String[] s, final int i) {
        if (i >= s.length || s[i].isEmpty()) {
            return false;
        }
        for (final char c : s[i].toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static int num(final String token) {
        return Integer.parseInt(token);
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // spanOf — a node's text span inside the source
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The [start, end) span of the target node's text in the source.
     * A document-order walk of each type's own window keeps it exact even
     * when identical spans repeat; a rec whose text no longer matches the
     * source is stale, and reported as such rather than guessed at.
     */
    public static int[] spanOf(final String source, final Rec rooted, final Rec target) {
        final Obj classes = rooted.at(uri(CLASSES));
        if (classes.isNoObj() || !classes.isLst()) {
            throw MTronException.of("span: no classes in this rec to locate the target");
        }
        int cursor = 0;
        for (final Obj c : classes.asLst().elements().toList()) {
            if (!c.isRec()) {
                continue;
            }
            final Rec cls = c.asRec();
            final String text = typeText(cls);
            final int pos = source.indexOf(text, cursor);
            if (pos < 0) {
                throw MTronException.of("span: this class no longer matches the source — the rec is stale, re-parse");
            }
            if (cls == target) {
                return new int[]{pos, pos + text.length()};
            }
            final int[] r = spanInType(source, pos, cls, target);
            if (null != r) {
                return r;
            }
            cursor = pos + text.length();
        }
        throw MTronException.of("span: the target member is not in this source — stale or renamed, re-locate");
    }

    private static int[] spanInType(final String source, final int typeStart, final Rec type, final Rec target) {
        final Obj members = type.at(uri(MEMBERS));
        if (members.isNoObj() || !members.isLst()) {
            return null;
        }
        int cursor = typeStart;
        for (final Obj mobj : members.asLst().elements().toList()) {
            if (!mobj.isRec()) {
                continue;
            }
            final Rec m = mobj.asRec();
            final Obj t = m.at(uri("text"));
            if (t.isNoObj()) {
                continue;
            }
            final String text = t.strValue();
            final int pos = source.indexOf(text, cursor);
            if (pos < 0) {
                throw MTronException.of("span: this member no longer matches the source — the rec is stale, re-parse");
            }
            if (m == target) {
                return new int[]{pos, pos + text.length()};
            }
            // the target may live one level down (a nested type)
            final int[] r = spanInType(source, pos, m, target);
            if (null != r) {
                return r;
            }
            cursor = pos + text.length();
        }
        return null;
    }

    /**
     * A type's span as source text: {@code header + member texts + footer}
     * — the exact composition of {@link #write}, so the two cannot drift.
     */
    private static String typeText(final Rec type) {
        final StringBuilder sb = new StringBuilder();
        final Obj header = type.at(uri("header"));
        if (!header.isNoObj()) {
            sb.append(header.strValue());
        }
        final Obj members = type.at(uri(MEMBERS));
        if (!members.isNoObj() && members.isLst()) {
            for (final Obj m : members.asLst().elements().toList()) {
                if (m.isRec()) {
                    final Obj t = m.asRec().at(uri("text"));
                    if (!t.isNoObj()) {
                        sb.append(t.strValue());
                    }
                }
            }
        }
        final Obj footer = type.at(uri("footer"));
        if (!footer.isNoObj()) {
            sb.append(footer.strValue());
        }
        return sb.toString();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // write — the lossless emit (contract, not language)
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A coarse rec back to source text.  Pure concatenation, byte-exact
     * for any rec that honors the contract; a member carrying its own
     * {@code members} (a nested type) emits through the type shape.
     */
    public static String write(final Obj obj) {
        if (!obj.isRec()) {
            return Str.Helper.cleanString(obj);
        }
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
            if (!pkg.isNoObj()) {
                sb.append(pkg.strValue()).append("\n\n");
            }
            final Obj imports = root.at(uri("imports"));
            if (!imports.isNoObj() && imports.isLst()) {
                imports.asLst().elements().forEach(i -> sb.append(i.strValue()).append("\n"));
                sb.append("\n");
            }
        }

        if (!classes.isNoObj() && classes.isLst()) {
            for (final Obj c : classes.asLst().elements().toList()) {
                if (!c.isRec()) {
                    continue;
                }
                final Rec cls = c.asRec();
                final Obj sep = cls.at(uri("sep"));
                if (!sep.isNoObj()) {
                    sb.append(sep.strValue());
                }
                writeType(cls, sb);
            }
        }

        if (!postscript.isNoObj()) {
            sb.append(postscript.strValue());
        }
        return sb.toString();
    }

    private static void writeType(final Rec type, final StringBuilder sb) {
        final Obj header = type.at(uri("header"));
        final Obj members = type.at(uri(MEMBERS));
        final Obj footer = type.at(uri("footer"));
        if (!header.isNoObj()) {
            sb.append(header.strValue());
        }
        if (!members.isNoObj() && members.isLst()) {
            members.asLst().elements().forEach(m -> {
                if (m.isRec()) {
                    writeMember(m.asRec(), sb);
                }
            });
        }
        if (!footer.isNoObj()) {
            sb.append(footer.strValue());
        }
    }

    /**
     * Emit a member.  A type member (nested class) composes like a type;
     * a method/constructor composes header + body + footer (so a body edit
     * writes through without the derived text going stale); everything
     * else emits its complete {@code text} span.
     */
    private static void writeMember(final Rec m, final StringBuilder sb) {
        final Obj members = m.at(uri(MEMBERS));
        if (!members.isNoObj() && members.isLst()) {
            writeType(m, sb);
            return;
        }
        final Obj header = m.at(uri("header"));
        final Obj body = m.at(uri("body"));
        final Obj footer = m.at(uri("footer"));
        if (!header.isNoObj()) {
            sb.append(header.strValue());
            if (!body.isNoObj()) {
                sb.append(body.strValue());
            }
            if (!footer.isNoObj()) {
                sb.append(footer.strValue());
            }
        } else {
            final Obj text = m.at(uri("text"));
            if (!text.isNoObj()) {
                sb.append(text.strValue());
            }
        }
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // firstError — the parse gate (tree-sitter, language-parameterized)
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The first syntax error in the source — line, column, node type —
     * or null if it parses clean.  Tree-sitter is error-tolerant (a broken
     * source still parses), so this is the gate an edit runs through
     * before committing.
     */
    public static String firstError(final Language lang, final String source) {
        try (final Tree tree = Parser.getFor(lang).parse(source)) {
            final Node root = tree.getRootNode();
            if (!root.hasError()) {
                return null;
            }
            final Node n = firstErrorNode(root);
            final Point p = n.getStartPoint();
            return "parse error at line " + (p.getRow() + 1) + ", col " + (p.getColumn() + 1) + " (" + n.getType() + ")";
        } catch (final Exception e) {
            return "parse error: " + e.getMessage();
        }
    }

    private static Node firstErrorNode(final Node n) {
        if (!n.hasError()) {
            return null;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            final Node r = firstErrorNode(n.getChild(i));
            if (null != r) {
                return r;
            }
        }
        return n;
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // field readers (the contract's shape)

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static String nameOf(final Rec r) {
        final Obj name = r.at(uri("name"));
        return name.isNoObj() ? "" : name.strValue();
    }

    private static String kindOf(final Rec r) {
        final Obj kind = r.at(uri("kind"));
        if (kind.isNoObj()) {
            return "other";
        }
        return kind.isUri() ? kind.uriValue().toString() : kind.strValue();
    }

    private static int ordinalOf(final Rec r) {
        final Obj ordinal = r.at(uri(ORDINAL));
        if (ordinal.isNoObj()) {
            return 0;
        }
        return ordinal.asInt().intValue().intValue();
    }
}
