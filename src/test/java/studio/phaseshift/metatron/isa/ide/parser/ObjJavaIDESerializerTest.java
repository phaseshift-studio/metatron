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
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractJavaSerializerTest;
import studio.phaseshift.metatron.isa.mach.type.Router;

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
public class ObjJavaIDESerializerTest extends AbstractJavaSerializerTest {

    private final ObjJavaIDESerializer serializer = ObjJavaIDESerializer.single();

    @BeforeAll
    public static void loadInstSet() {
        InstSet.importInstSet(f("/m/web"), f("web"));
        InstSet.importInstSet(f("/m/ide"), f("ide"));
        Tracer.enable(Tracer.stack);
    }

    public ObjJavaIDESerializerTest() {
        super(new ObjJavaIDESerializer(), IDE_JAVA_TID, "ide_java");
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
        // dereference flow: a java::T str parses into the coarse cs_java rec
        final Rec cs = eval("'public class Empty {}'.as(java::T).as(ide_java::T)");
        assertTrue(cs.isRec());
        assertEquals(IDE_JAVA_TID, cs.tid());
        assertFalse(cs.at(uri("classes")).isNoObj(), "must expose classes");
    }

    @Test
    public void testJavaIDEToRec() {
        // cs_java::T downgrades to plain rec::T
        final Rec plain = eval("'public class Empty {}'.as(java::T).as(ide_java::T).as(rec::T)");
        assertTrue(plain.isRec());
        assertEquals(REC_TID, plain.tid());
    }

    @Test
    public void testJavaIDETypeChain() {
        // rec::T -> cs_java::T re-tags (cs_java is a rec refinement)
        try {
            final Rec cs = eval("'public class Empty {}'.as(java::T).as(ide_java::T).as(rec::T).as(ide_java::T)");
            assertTrue(cs.isRec());
            assertEquals(IDE_JAVA_TID, cs.tid());
            assertFalse(cs.at(uri("classes")).isNoObj());
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
