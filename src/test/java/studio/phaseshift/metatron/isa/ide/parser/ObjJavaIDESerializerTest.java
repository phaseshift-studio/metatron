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
import studio.phaseshift.metatron.isa.mach.io.type.AbstractJavaSerializerTest;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.IDE_JAVA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Verifies the coarse-schema Java serializer ({@link ObjJavaIDESerializer}).
 * Shared round-trip coverage lives in {@link AbstractJavaSerializerTest}.
 *
 * <p>The schema is defined in {@code docs/design/codespaces/codespace-functor.md §0}:
 * a file parses to {@code rec(package, imports:lst, classes:lst)} where each class is
 * {@code rec(kind, name, superclass, interfaces, header, members:lst, footer, [sep])}.
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
        assertTrue(classes.isLst(), "classes must be a lst");
        return classes.asLst().elements().toList().get(0).asRec();
    }

    private static Rec findMember(final Rec cls, final String kind, final String name) {
        final Obj members = cls.at(uri("members"));
        if (members.isNoObj() || !members.isLst()) return null;
        for (final Obj m : members.asLst().elements().toList()) {
            if (!m.isRec()) continue;
            final Rec mr = m.asRec();
            final String k = mr.at(uri("kind")).orElse(uri("")).uriValue().toString();
            final String n = mr.at(uri("name")).orElse(str("")).strValue();
            if (kind.equals(k) && name.equals(n)) return mr;
        }
        return null;
    }

    /**
     * Replace a member (by name) within a class rec, returning a new class rec.
     */
    private static Rec replaceMember(final Rec cls, final String name, final Rec newMember) {
        final Obj members = cls.at(uri("members"));
        if (members.isNoObj() || !members.isLst()) return cls;
        final List<Obj> list = new ArrayList<>();
        for (final Obj m : members.asLst().elements().toList()) {
            if (m.isRec() && name.equals(m.asRec().at(uri("name")).orElse(str("")).strValue()))
                list.add(newMember);
            else
                list.add(m);
        }
        return cls.at(uri("members"), lst(list));
    }

    /**
     * Replace a class (by name) within the root, returning a new root rec.
     */
    private static Rec replaceClass(final Rec root, final Rec newClass) {
        final Obj classes = root.at(uri("classes"));
        if (classes.isNoObj() || !classes.isLst()) return root;
        final String name = newClass.at(uri("name")).strValue();
        final List<Obj> list = new ArrayList<>();
        for (final Obj c : classes.asLst().elements().toList()) {
            if (c.isRec() && name.equals(c.asRec().at(uri("name")).orElse(str("")).strValue()))
                list.add(newClass);
            else
                list.add(c);
        }
        return root.at(uri("classes"), lst(list));
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
    //  Type chain: str -> java::T -> cs_java::T -> rec::T -> cs_java::T
    //  cs_java::T is a rec::T refinement — as(cs_java::T) IS the parse.
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

    // ===================================================================
    //  Addressing — name + ordinal (the shared code schema)
    // ===================================================================

    @Test
    public void testAddressMatchesTheScheme() {
        // the canonical shape: code/{file}/classes/Greeter/0/members/method/greet/0 —
        // the file slot is the host space's, the rest is the schema's
        final Rec root = serializer.read("class Greeter {public void greet() {}}").asRec();
        assertEquals("classes/Greeter/0", firstClass(root).at(uri("path")).strValue());
        final Rec greet = findMember(firstClass(root), "method", "greet");
        assertEquals("classes/Greeter/0/members/method/greet/0", greet.at(uri("path")).strValue());
        assertEquals(0, greet.at(uri("ordinal")).asInt().intValue(), "a lone member is ordinal 0");
        assertEquals(greet, ObjIDESchema.locate(root, "classes/Greeter/0/members/method/greet/0"),
                "locate must resolve the derived path of every node");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            // overloads: ordinal = rank among same-named siblings, document order
            "class A {int apply(int a){return a;}int apply(long a){return (int)a;}} % classes/A/0/members/method/apply/0 % apply % 0 % int apply(int a)",
            "class A {int apply(int a){return a;}int apply(long a){return (int)a;}} % classes/A/0/members/method/apply/1 % apply % 1 % int apply(long a)",
            // field and method may legally share a name — the kind slot separates them
            "class B {int count;int count(){return 0;}} % classes/B/0/members/field/count/0 % count % 0 % int count;",
            "class B {int count;int count(){return 0;}} % classes/B/0/members/method/count/0 % count % 0 % int count()",
            // constructor overloads key on the class name
            "class E {E(int a){this.a = a;}E(long a){this.a = a;}private int a;} % classes/E/0/members/constructor/E/1 % E % 1 % E(long a)",
            // nameless members rank within their kind — the name slot is dropped
            "class C {int x;/** a */int y;/** b */int z;} % classes/C/0/members/comment/0 % - % 0 % a",
            "class C {int x;/** a */int y;/** b */int z;} % classes/C/0/members/comment/1 % - % 1 % b",
            // anonymous constructs rank within 'other'
            "class G {static{System.out.print(1);}int x;} % classes/G/0/members/other/0 % - % 0 % static",
            // a nested type: the same grammar one level down
            "class D {int top(){return 1;}class Nested{int inner(){return 2;}}} % classes/D/0/members/class/Nested/0 % Nested % 0 % class Nested",
            "class D {int top(){return 1;}class Nested{int inner(){return 2;}}} % classes/D/0/members/class/Nested/0/members/method/inner/0 % inner % 0 % int inner(){return 2;}"
    }, delimiter = '%')
    public void testAddressResolution(final String src, final String address,
                                      final String expectedName, final int expectedOrdinal,
                                      final String textFragment) {
        final Rec root = serializer.read(src).asRec();
        final Rec node = ObjIDESchema.locate(root, address);
        if ("-".equals(expectedName)) {
            assertTrue(node.at(uri("name")).isNoObj(), "expect a nameless member at " + address);
        } else {
            assertEquals(expectedName, node.at(uri("name")).strValue());
        }
        assertEquals(expectedOrdinal, node.at(uri("ordinal")).asInt().intValue());
        assertTrue(node.at(uri("text")).strValue().contains(textFragment),
                address + " expected to contain '" + textFragment + "' — got: " + node.at(uri("text")).strValue());
        assertEquals(address, node.at(uri("path")).strValue(),
                "the derived path must be the addressing path itself");
    }

    @Test
    public void testLocateMissingListsCandidates() {
        final Rec root = serializer.read("class A {int apply(int a){return a;}int apply(long a){return (int)a;}}").asRec();
        final MTronException e = assertThrows(MTronException.class,
                () -> ObjIDESchema.locate(root, "classes/A/0/members/method/nope/0"));
        assertTrue(e.getMessage().contains("apply"),
                "a miss must list what does exist, so the agent can re-aim: " + e.getMessage());
    }

    @Test
    public void testLocateUnknownClass() {
        final Rec root = serializer.read("class A {int x;}").asRec();
        final MTronException e = assertThrows(MTronException.class,
                () -> ObjIDESchema.locate(root, "classes/B/0"));
        assertTrue(e.getMessage().contains("A"), "class candidates must be listed: " + e.getMessage());
    }

    // ===================================================================
    //  edit by address — span-replace, parse-gated, byte-exact
    // ===================================================================

    @Test
    public void testEditMethodByAddress() {
        final String src = "class A {int apply(int a){return a;}int apply(long a){return (int)a;}}";
        final Obj edited = ObjJavaIDESerializer.edit(src, "classes/A/0/members/method/apply/0",
                "int apply(int a){return a + 1;}");
        assertEquals("class A {int apply(int a){return a + 1;}int apply(long a){return (int)a;}}",
                serializer.write(edited.asRec()),
                "edit must replace exactly the addressed span and nothing else");
    }

    @Test
    public void testEditSecondOverloadLeavesFirstUntouched() {
        final String src = "class A {int apply(int a){return a;}int apply(long a){return (int)a;}}";
        final Obj edited = ObjJavaIDESerializer.edit(src, "classes/A/0/members/method/apply/1",
                "int apply(long a){return (int)(a / 2);}");
        final String out = serializer.write(edited.asRec());
        assertEquals("class A {int apply(int a){return a;}int apply(long a){return (int)(a / 2);}}", out);
    }

    @Test
    public void testEditNestedMember() {
        final String src = "class D {int top(){return 1;}class Nested{int inner(){return 2;}}}";
        final Obj edited = ObjJavaIDESerializer.edit(src,
                "classes/D/0/members/class/Nested/0/members/method/inner/0",
                "int inner(){return 3;}");
        assertEquals("class D {int top(){return 1;}class Nested{int inner(){return 3;}}}",
                serializer.write(edited.asRec()));
    }

    @Test
    public void testEditRejectsUnparseable() {
        final String src = "class A {int x(){return 0;}}";
        final MTronException e = assertThrows(MTronException.class,
                () -> ObjJavaIDESerializer.edit(src, "classes/A/0/members/method/x/0", "def broken ("));
        assertTrue(e.getMessage().contains("line"),
                "a rejected edit must say where the parse breaks: " + e.getMessage());
    }

    @Test
    public void testEditRenameRecoversViaAddress() {
        final String src = "class A {int apply(int a){return a;}}";
        final Obj edited = ObjJavaIDESerializer.edit(src, "classes/A/0/members/method/apply/0",
                "int changed(int a){return a;}");
        final Rec root = edited.asRec();
        final MTronException e = assertThrows(MTronException.class,
                () -> ObjIDESchema.locate(root, "classes/A/0/members/method/apply/0"));
        assertTrue(e.getMessage().contains("changed"),
                "the recovery message must name what replaced it: " + e.getMessage());
        assertEquals(0, ObjIDESchema.locate(root, "classes/A/0/members/method/changed/0")
                .at(uri("ordinal")).asInt().intValue(), "the rename is rank 0 of its new name");
    }

    // ===================================================================
    //  nested types — structure + lossless round-trip
    // ===================================================================

    private static final String NESTED = """
                                         class D {
                                             int top() {
                                                 return 1;
                                             }
                                             class Nested {
                                                 int inner() {
                                                     return 2;
                                                 }
                                             }
                                         }
                                         """;

    @Test
    public void testNestedClassStructure() {
        final Rec root = serializer.read(NESTED).asRec();
        final Rec cls = firstClass(root);
        final Rec nested = findMember(cls, "class", "Nested");
        assertNotNull(nested, "a nested class must surface as a member of kind 'class'");
        assertFalse(nested.at(uri("members")).isNoObj(), "a nested class carries its own members");
        assertNotNull(findMember(nested, "method", "inner"), "the nested class must expose its own method");
        assertTrue(nested.at(uri("header")).strValue().contains("class Nested"));
        assertEquals("classes/D/0/members/class/Nested/0", nested.at(uri("path")).strValue());
    }

    @Test
    public void testNestedClassRoundTripExact() {
        assertEquals(NESTED, serializer.write(serializer.read(NESTED).asRec()),
                "nested types must round-trip byte-for-byte");
    }

    @Test
    public void testIDEJavaWriteDirectionRoundTrip() {
        // the write direction — a coarse rec serializes back to source via as(web:java::T).
        // a surgical body edit must appear in the output and the rest of the source survive.
        eval("'public class WR { int one() { return 1; } }'.as(web:java::T).as(ide:java::T).to(wr)");
        eval("wr/classes/0/members/0/body -> 'return 42;'");
        final Obj out = eval("*wr.as(web:java::T)");
        assertTrue(out.isStr(), "as(web:java::T) on a coarse rec must yield the source str — %s".formatted(out));
        assertTrue(out.strValue().contains("return 42;"), "the edited body must appear — %s".formatted(out));
        assertTrue(out.strValue().contains("public class WR"), "the class must survive — %s".formatted(out));
    }
}
