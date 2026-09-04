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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractJavaSerializerTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.IDE_JAVA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Verifies the coarse-schema Java serializer ({@link ObjJavaIDESerializer}).
 * Shared round-trip coverage lives in {@link AbstractJavaSerializerTest}.
 *
 * <p>The schema is defined in {@code docs/design/codespaces/codespace-functor.md §0}:
 * a file parses to {@code rec(preamble, package, imports:lst, classes, postscript)}
 * where {@code classes} is a named rec — each class name maps to the ranked {@code lst}
 * of that class's recs ({@code rec(kind, name, header, members, footer, [sep])}) — and
 * {@code members} is an ordered {@code lst} of single-field wrapper recs
 * ({@code {name => memberRec}}, kind word when nameless): the list position is the
 * print order, the wrapper key is the named slot — e.g.
 * {@code classes/Greeter/0/members/1/apply/text}, with {@code members/+/apply} as the
 * wildcard name query.  The structure IS the address; there is no parallel identifier
 * scheme.
 * Methods/constructors decompose into {@code header}/{@code body}/{@code footer} so the
 * body is the directly-editable unit; fields/comments are complete {@code text} spans.</p>
 *
 * <p>Tests cover: structure parse, exact round-trip (write(parse(src)) == src), and
 * span-edit → save → reload (the "byte offset editing" capability).</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Execution(ExecutionMode.SAME_THREAD)
public class ObjJavaIDESerializerTest extends AbstractJavaSerializerTest {

    private final ObjJavaIDESerializer serializer = ObjJavaIDESerializer.single();

    @BeforeAll
    public static void loadInstSet() {
        InstSet.importInstSet(f("/m/ide"), f("ide"));
        InstSet.importInstSet(f("/m/web"), f("web"));
        //Tracer.enable(Tracer.stack);
    }

    public ObjJavaIDESerializerTest() {
        super(new ObjJavaIDESerializer(), IDE_JAVA_TID, "ide:java");
    }

    /**
     * The coarse schema is lossless — idempotence is not enough (a doubly-braced
     * method would reproduce itself and "pass" while being invalid Java).  Assert
     * the regenerated source is byte-identical to the input.
     */
    @Override
    protected void assertRoundTripIdempotent(final String javaSource) {
        super.assertRoundTripIdempotent(javaSource);
        assertEquals(javaSource, serializer.write(serializer.read(javaSource).asRec()),
                "coarse serializer must be lossless: regenerated source must equal input");
    }

    // ===================================================================
    //  Helpers
    // ===================================================================

    private static Rec firstClass(final Rec root) {
        final Obj classes = root.at(uri("classes"));
        assertFalse(classes.isNoObj(), "root must have classes");
        assertTrue(classes.isRec(), "classes must be a rec of named slots");
        final Rel nameSlot = classes.asRec().elements().toList().get(0);
        return nameSlot.second().asLst().elements().toList().get(0).asRec();
    }

    private static Rec findMember(final Rec cls, final String kind, final String name) {
        final Obj members = cls.at(uri("members"));
        if (members.isNoObj() || !members.isLst()) return null;
        for (final Obj wrap : members.asLst().elements().toList()) {
            if (!wrap.isRec()) continue;
            final Rel slot = wrap.asRec().elements().toList().get(0);
            if (!name.equals(slot.first().uriValue().toString())) continue;
            final Rec mr = slot.second().asRec();
            final String k = mr.at(uri("kind")).orElse(uri("")).uriValue().toString();
            if (kind.equals(k)) return mr;
        }
        return null;
    }

    /**
     * Replace a member (by name; the first match of that kind) within its named
     * slot, returning a new class rec.
     */
    private static Rec replaceMember(final Rec cls, final String name, final Rec newMember) {
        final Obj membersObj = cls.at(uri("members"));
        if (membersObj.isNoObj() || !membersObj.isLst()) return cls;
        boolean replaced = false;
        final List<Obj> list = new ArrayList<>();
        for (final Obj wrap : membersObj.asLst().elements().toList()) {
            if (!replaced && wrap.isRec()) {
                final Rel slot = wrap.asRec().elements().toList().get(0);
                if (name.equals(slot.first().uriValue().toString())) {
                    list.add(rec(uri(name), newMember));
                    replaced = true;
                    continue;
                }
            }
            list.add(wrap);
        }
        return cls.at(uri("members"), lst(list));
    }

    /**
     * Replace a class (by name) within its named slot, returning a new root rec.
     */
    private static Rec replaceClass(final Rec root, final Rec newClass) {
        final Obj classesObj = root.at(uri("classes"));
        if (classesObj.isNoObj() || !classesObj.isRec()) return root;
        final Rec classes = classesObj.asRec();
        final String name = newClass.at(uri("name")).strValue();
        final Obj bucket = classes.at(uri(name));
        if (bucket.isNoObj() || !bucket.isLst()) return root;
        final List<Obj> list = new ArrayList<>();
        for (final Obj c : bucket.asLst().elements().toList()) {
            list.add(c.isRec() && name.equals(c.asRec().at(uri("name")).orElse(str("")).strValue()) ? newClass : c);
        }
        return root.at(uri("classes"), classes.at(uri(name), lst(list)));
    }

    private static final String REPRESENTATIVE = """
                                                 /*
                                                  * license header
                                                  */
                                                 package com.example.model;
                                                 
                                                 import java.util.List;
                                                 import java.util.Objects;
                                                 
                                                 /**
                                                  * A person.
                                                  */
                                                 public class Person implements Comparable<Person> {
                                                 
                                                     private final String name;
                                                     private final int age;
                                                 
                                                     public Person(String name, int age) {
                                                         this.name = name;
                                                         this.age = age;
                                                     }
                                                 
                                                     public String getName() {
                                                         return name;
                                                     }
                                                 
                                                     @Override
                                                     public int compareTo(Person other) {
                                                         return Integer.compare(this.age, other.age);
                                                     }
                                                 }
                                                 """;

    // ===================================================================
    //  Structure parse
    // ===================================================================

    @Test
    public void testParseStructure() {
        final Rec root = serializer.read(REPRESENTATIVE).asRec();
        assertTrue(root.isRec());

        // package + imports (addressing views; also inside preamble verbatim)
        final Obj pkg = root.at(uri("package"));
        assertFalse(pkg.isNoObj());
        assertEquals("package com.example.model;", pkg.strValue());

        final Obj imports = root.at(uri("imports"));
        assertTrue(imports.isLst());
        final List<Obj> impList = imports.asLst().elements().toList();
        assertEquals(2, impList.size());
        assertEquals("import java.util.List;", impList.get(0).strValue());
        assertEquals("import java.util.Objects;", impList.get(1).strValue());

        // preamble is verbatim and contains the license header
        final Obj preamble = root.at(uri("preamble"));
        assertFalse(preamble.isNoObj());
        assertTrue(preamble.strValue().contains("license header"));
        assertTrue(preamble.strValue().contains("package com.example.model;"));

        // class
        final Rec cls = firstClass(root);
        assertEquals("class_declaration", cls.at(uri("kind")).uriValue().toString());
        assertEquals("Person", cls.at(uri("name")).strValue());
        assertFalse(cls.at(uri("superclass")).isNoObj() && cls.at(uri("interfaces")).isNoObj(),
                "class must declare a superclass or interfaces");
        assertEquals("Comparable<Person>", cls.at(uri("interfaces")).strValue(),
                "interfaces must be the bare type name");
        assertFalse(cls.at(uri("header")).isNoObj());
        assertFalse(cls.at(uri("footer")).isNoObj());
        assertTrue(cls.at(uri("header")).strValue().contains("public class Person"));

        // members
        final Rec field = findMember(cls, "field", "name");
        assertNotNull(field, "field 'name' must exist");
        assertTrue(field.at(uri("text")).strValue().contains("private final String name;"));

        final Rec method = findMember(cls, "method", "getName");
        assertNotNull(method, "method 'getName' must exist");
        assertEquals("String getName()", method.at(uri("signature")).strValue());
        assertTrue(method.at(uri("header")).strValue().contains("public String getName()"));
        assertTrue(method.at(uri("body")).strValue().contains("return name;"));
        assertEquals("getName", method.at(uri("name")).strValue());

        final Rec ctor = findMember(cls, "constructor", "Person");
        assertNotNull(ctor, "constructor must exist");
        assertTrue(ctor.at(uri("body")).strValue().contains("this.name = name;"));
    }

    @Test
    public void testSuperclassBareName() {
        final Rec root = serializer.read("""
                                         package com.example;
                                         public class Sub extends java.util.AbstractList<String> {
                                             public int size() { return 0; }
                                         }
                                         """).asRec();
        final Rec cls = firstClass(root);
        assertEquals("java.util.AbstractList<String>", cls.at(uri("superclass")).strValue(),
                "superclass must be the bare type name, without the 'extends' keyword");
    }

    // ===================================================================
    //  Losslessness: exact round-trip
    // ===================================================================

    @Test
    public void testExactRoundTrip() {
        final String out = serializer.write(serializer.read(REPRESENTATIVE).asRec());
        assertEquals(REPRESENTATIVE, out, "write(parse(src)) must reproduce src byte-for-byte");
    }

    @Test
    public void testExactRoundTripNoPackage() {
        final String src = """
                           public class Bare {
                               public void go() {
                               }
                           }
                           """;
        final String out = serializer.write(serializer.read(src));
        assertEquals(src, out, "class-only source must round-trip exactly");
    }

    @Test
    public void testExactRoundTripEnum() {
        final String src = """
                           public enum Color {
                               RED, GREEN, BLUE
                           }
                           """;
        assertEquals(src, serializer.write(serializer.read(src)));
    }

    // ===================================================================
    //  Span-edit → save → reload ("byte offset editing")
    // ===================================================================

    @Test
    public void testMethodBodyEdit() {
        final Rec root = serializer.read(REPRESENTATIVE).asRec();
        final Rec cls = firstClass(root);
        final Rec method = findMember(cls, "method", "getName");
        assertNotNull(method);

        // replace the method body (the editable lineq unit)
        final String newBody = "{\n        return \"renamed-\" + name;\n    }";
        final Rec edited = method.at(uri("body"), str(newBody));
        final Rec clsEdited = replaceMember(cls, "getName", edited);
        final Rec rootEdited = replaceClass(root, clsEdited);

        final String out = serializer.write(rootEdited);
        assertTrue(out.contains("return \"renamed-\" + name;"), "edited body must appear");
        assertTrue(out.contains("public class Person implements Comparable<Person> {"),
                "class header preserved");
        assertTrue(out.contains("public String getName()"), "method signature preserved");
        assertTrue(out.contains("private final String name;"), "unrelated field preserved");

        // reload and verify the edit persisted
        final Rec reloaded = serializer.read(out).asRec();
        final Rec m2 = findMember(firstClass(reloaded), "method", "getName");
        assertNotNull(m2);
        assertEquals(newBody, m2.at(uri("body")).strValue());
        assertEquals("String getName()", m2.at(uri("signature")).strValue());
    }

    @Test
    public void testFieldEdit() {
        final Rec root = serializer.read(REPRESENTATIVE).asRec();
        final Rec cls = firstClass(root);
        final Rec field = findMember(cls, "field", "name");
        assertNotNull(field);

        final String newText = "    private final String displayName;";
        final Rec edited = field.at(uri("text"), str(newText));
        final Rec clsEdited = replaceMember(cls, "name", edited);
        final Rec rootEdited = replaceClass(root, clsEdited);

        final String out = serializer.write(rootEdited);
        assertTrue(out.contains("private final String displayName;"), "edited field must appear");
        assertTrue(out.contains("private final int age;"), "unrelated field preserved");

        final Rec reloaded = serializer.read(out).asRec();
        final Rec f2 = findMember(firstClass(reloaded), "field", "displayName");
        assertNotNull(f2, "edited field must survive reload");
    }

    // ===================================================================
    //  Wrapper addressing: members is an ordered lst of {name => memberRec}
    // ===================================================================

    private static List<Rec> membersNamed(final Rec cls, final String name) {
        final Obj members = cls.at(uri("members"));
        if (members.isNoObj() || !members.isLst()) return List.of();
        final List<Rec> out = new ArrayList<>();
        for (final Obj wrap : members.asLst().elements().toList()) {
            if (!wrap.isRec()) continue;
            final Rel slot = wrap.asRec().elements().toList().get(0);
            if (name.equals(slot.first().uriValue().toString())) out.add(slot.second().asRec());
        }
        return out;
    }

    @Test
    public void testOverloadsNamedWithDocumentOrder() {
        final String src = "public class Greeter { int apply(int a){return a;} int apply(long a){return (int)a;} }";
        final Rec root = serializer.read(src).asRec();
        final List<Rec> applies = membersNamed(firstClass(root), "apply");
        assertEquals(2, applies.size(), "two applies = two wrapped members keyed apply, in document order");
        assertEquals("int apply(int a)", applies.get(0).at(uri("signature")).strValue(),
                "document order preserved: apply(int) first");
        assertEquals("int apply(long a)", applies.get(1).at(uri("signature")).strValue(),
                "apply(long) second");
        assertEquals(src, serializer.write(root), "the named wrappers must write back byte-for-byte");
    }

    @Test
    public void testInterleavedMembersRoundTripByteExact() {
        // the case no name-grouping can hold: same-named members straddling
        // other members — the wrappers keep the source order, so the round-trip
        // is exact instead of regrouped
        final String src = "class Mix { int x; int apply(int a){return a;} int y; int apply(long a){return (int)a;} }";
        final Obj root = serializer.read(src);
        assertEquals(src, serializer.write(root), "interleaved members must not be regrouped on write");
    }

    @Test
    public void testAddressablePathThroughTheStructure() {
        // classes/Greeter/0/members/0/apply/text — the space derefs straight
        // through the structure: the wrapper key IS the named slot
        final String src = "public class Greeter { int apply(int a){return a;} }";
        final Rec root = serializer.read(src).asRec();
        final Rec cls = root
                .at(uri("classes")).asRec().at(uri("Greeter")).asLst()
                .elements().toList().get(0).asRec();
        final Rel slot = cls.at(uri("members")).asLst().elements().toList().get(0)
                .asRec().elements().toList().get(0);
        assertEquals("apply", slot.first().uriValue().toString(), "the wrapper key names the member");
        final Rec viaAddress = slot.second().asRec();
        assertSame(findMember(cls, "method", "apply"), viaAddress,
                "dereferencing the wrapper lands on the very rec the name names");
        assertTrue(viaAddress.at(uri("text")).strValue().contains("int apply(int a)"));
    }

    @Test
    public void testFieldNameMethodSameName() {
        // legal java: a field and a method may share a name — two wrapped
        // members keyed count, and the kind field on each tells them apart
        final String src = "class B { int count; int count(){return 0;} }";
        final Rec root = serializer.read(src).asRec();
        final List<Rec> counts = membersNamed(firstClass(root), "count");
        assertEquals(2, counts.size(), "the shared name wraps both members");
        assertEquals("field", counts.get(0).at(uri("kind")).uriValue().toString());
        assertEquals("method", counts.get(1).at(uri("kind")).uriValue().toString());
        assertEquals(src, serializer.write(root));
    }

    @Test
    public void testNamelessMembersKeyedByKind() {
        // comments and friends carry no name — their kind word is the wrapper
        // key, so they stay named and addressable: members/0/comment, members/1/comment
        final String src = "class C {\n    // note one\n    /* note two */\n    int x;\n}";
        final Rec root = serializer.read(src).asRec();
        assertEquals(2, membersNamed(firstClass(root), "comment").size(), "both comments keyed comment");
        assertEquals(src, serializer.write(root), "kind-keyed wrappers must write back byte-for-byte");
    }

    @Test
    public void testWildcardNameQuery() {
        // members/+/apply — the metatron wildcard pulls every wrapped member
        // keyed apply — the durable reference form.  The read returns the
        // matches as a #{*} set (no order contract; name is the key)
        final String src = "public class WR { int fillA(){return 1;} int apply(int x){return x;} "
                + "int fillB(){return 2;} int apply(long y){return (int)y;} }";
        eval("'" + src + "'.as(web:java::T).as(ide:java::T).to(wr)");
        final Obj found = eval("*wr/classes/WR/0/members/+/apply");
        final List<String> sigs = found.stream()
                .map(o -> o.asRec().at(uri("signature")).strValue())
                .sorted()
                .toList();
        assertEquals(List.of("int apply(int x)", "int apply(long y)"), sigs,
                "the wildcard must resolve exactly the two members named apply");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "*wv/classes/WR/0/kind % class_declaration",
            "*wv/classes/WR/0/members/0/fillA/kind % method",
    }, delimiter = '%')
    public void testIdeJavaStoredValueReads(final String code, final String expected) {
        // the ide:java rec must be stored as a value (to() evaluates before the write)
        // for the deep reads underneath it to resolve.  A list literal around the
        // bare expression ([...as(ide:java::T)]) stores the unevaluated code instead,
        // and unrollPoly only descends into poly elements, so the reads below a
        // code element come back noobj — the agent-ide code list therefore grows
        // through the pull (which stores parsed recs), not through list literals.
        final String parse = "'public class WR { int fillA(){return 1;} }'.as(web:java::T).as(ide:java::T)";
        eval(parse + ".to(wv)");
        checkCodeParseApply(LOG, code, expected);
    }

    @Test
    public void testMultiClassFile() {
        // two classes, one file: each gets its named slot; the gap between
        // them rides along as the second class's sep and must survive
        final String src = "class Alpha {\n    int one(){return 1;}\n}\nclass Beta {\n    int two(){return 2;}\n}";
        final Rec root = serializer.read(src).asRec();
        final Rec classes = root.at(uri("classes")).asRec();
        assertFalse(classes.at(uri("Alpha")).isNoObj(), "first class under its own named slot");
        assertFalse(classes.at(uri("Beta")).isNoObj(), "second class under its own named slot");
        assertEquals(src, serializer.write(root), "multi-class files must round-trip with their gaps");
    }

    // ===================================================================
    //  Type chain: the full chain round-trips byte-for-byte
    // ===================================================================

    @Test
    public void testJavaToJavaIDE() {
        // InstSet.importInstSet(f("/m/web"), f("web"));
        // dereference flow: a java::T str parses into the coarse cs_java rec
        final Rec cs = eval("'public class Empty {}'.as(/m/web/mime/java::T).as(ide:java::T)");
        assertTrue(cs.isRec());
        assertEquals(IDE_JAVA_TID, cs.tid());
        assertFalse(cs.at(uri("classes")).isNoObj(), "must expose classes");
    }

    @Test
    public void testJavaIDEToRec() {
        // InstSet.importInstSet(f("/m/web"), f("web"));
        // ide:java::T downgrades to plain rec::T
        final Rec plain = eval("'public class Empty {}'.as(/m/web/mime/java::T).as(ide:java::T).as(rec::T)");
        assertTrue(plain.isRec());
        assertEquals(REC_TID, plain.tid());
    }

    @Test
    public void testJavaIDETypeChain() {
        // InstSet.importInstSet(f("/m/web"), f("web"));
        // rec::T -> ide:java::T re-tags (cs_java is a rec refinement)
        try {
            final Rec cs = eval("'public class Empty {}'.as(/m/web/mime/java::T).as(ide:java::T).as(rec::T).as(ide:java::T)");
            assertTrue(cs.isRec());
            assertEquals(IDE_JAVA_TID, cs.tid());
            assertFalse(cs.at(uri("classes")).isNoObj());
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testWebJavaViaPrefix() {
        // `java` is a short name shared by the web instset (/m/web/mime/java) and the ide instset
        // (/m/ide/java). The prefix must scope the redirect: web:java::T resolves to the web java
        // type (not the ide one), so the as?str->java step still fires.
        final Rec cs = eval("'public class Empty {}'.as(web:java::T).as(ide:java::T)");
        assertTrue(cs.isRec());
        assertEquals(IDE_JAVA_TID, cs.tid());
        assertFalse(cs.at(uri("classes")).isNoObj(), "must expose classes");
    }

    @Test
    public void testChainRoundTripByteExact() {
        // the full type chain, both directions — str in, same str out:
        // '...'.as(web:java::T).as(ide:java::T) -> the named-slot rec
        //      .as(web:java::T).as(str::T)      -> the source, byte-for-byte
        final String src = "public class Greeter { int apply(int a){return a;} int apply(long a){return (int)a;} }";
        final Obj out = eval("'" + src + "'.as(web:java::T).as(ide:java::T).as(web:java::T).as(str::T)");
        assertEquals(src, out.strValue(), "the type chain must round-trip the named-slot rec byte-for-byte — got: " + out);
    }

    @Test
    public void testIDEJavaWriteDirectionRoundTrip() {
        // the write direction — a coarse rec serializes back to source via as(web:java::T).
        // a surgical body edit must appear in the output and the rest of the source survive.
        eval("'public class WR { int one() { return 1; } }'.as(web:java::T).as(ide:java::T).to(wr)");
        // the member address: list position, then the named slot
        eval("wr/classes/WR/0/members/0/one/body -> 'return 42;'");
        final Obj out = eval("*wr.as(web:java::T)");
        assertTrue(out.isStr(), "as(web:java::T) on a coarse rec must yield the source str — %s".formatted(out));
        assertTrue(out.strValue().contains("return 42;"), "the edited body must appear — %s".formatted(out));
        assertTrue(out.strValue().contains("public class WR"), "the class must survive — %s".formatted(out));
    }
}
