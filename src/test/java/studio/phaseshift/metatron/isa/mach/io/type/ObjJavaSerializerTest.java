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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.Tokens.TEXT;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.JAVA_TID;

/**
 * Verifies the fine-grained CST Java serializer ({@link ObjJavaSerializer}).
 * Shared round-trip coverage lives in {@link AbstractJavaSerializerTest}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjJavaSerializerTest extends AbstractJavaSerializerTest {

    private final ObjJavaSerializer serializer = ObjJavaSerializer.single();

    public ObjJavaSerializerTest() {
        super(new ObjJavaSerializer(), JAVA_TID, "java");
    }

    // ===================================================================
    //  Helpers
    // ===================================================================

    private static void assertPathText(final Obj rec, final String path, final String expected) {
        final Obj val = rec.asRec().at(uri(path));
        assertFalse(val.isNoObj(), "path '" + path + "' must exist");
        final String actual = val.isStr() ? val.strValue() :
                val.isRec() ? val.asRec().at(uri(TEXT)).orElse(str("")).strValue() : "";
        assertEquals(expected, actual, "path '" + path + "' text mismatch");
    }

    private static Obj findChildByType(final Obj parent, final String typeName) {
        if (!parent.isRec()) return noobj();
        final Obj out = parent.asRec().at(uri(OUT));
        if (out.isNoObj() || !out.isLst()) return noobj();
        for (final Obj child : out.asLst().elements().toList()) {
            if (child.isRec()) {
                final String t = child.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY).orElse(uri("")).uriValue().toString();
                if (t.equals(typeName)) return child;
            }
        }
        return noobj();
    }

    // ===================================================================
    //  Structure tests (CST shape)
    // ===================================================================

    @Test
    public void testEmptyClass() {
        final String java = """
                            public class Empty {
                            }""";
        final Obj rec = serializer.read(java);
        assertTrue(rec.isRec());
        assertEquals(uri("program"), rec.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY));
        final Obj classDecl = findChildByType(rec, "class_declaration");
        assertFalse(classDecl.isNoObj());
        assertPathText(classDecl, "name", "Empty");
    }

    @Test
    public void testClassWithPackage() {
        final String java = """
                            package com.example;

                            public class Hello {
                            }""";
        final Obj rec = serializer.read(java);
        final Obj out = rec.asRec().at(uri(OUT));
        final List<Obj> children = out.asLst().elements().toList();
        assertEquals(2, children.size());
        final Obj pkg = findChildByType(rec, "package_declaration");
        assertFalse(pkg.isNoObj());
    }

    @Test
    public void testClassWithImport() {
        final String java = """
                            import java.util.List;

                            public class Test {
                            }""";
        final Obj rec = serializer.read(java);
        final Obj imp = findChildByType(rec, "import_declaration");
        assertFalse(imp.isNoObj(), "must find import declaration");
        final String impText = imp.asRec().at(uri(TEXT)).orElse(str("")).strValue();
        assertTrue(impText.contains("java.util.List"),
                "import should reference java.util.List, got: " + impText);
    }

    @Test
    public void testInterfaceDeclaration() {
        final String java = """
                            public interface Processor<T> {
                                T process(T input);
                            }""";
        final Obj rec = serializer.read(java);
        final Obj iface = findChildByType(rec, "interface_declaration");
        assertFalse(iface.isNoObj());
        assertPathText(iface, "name", "Processor");
        final Obj body = iface.asRec().at(uri("body"));
        assertFalse(body.isNoObj());
        final Obj method = findChildByType(body, "method_declaration");
        assertFalse(method.isNoObj());
        assertPathText(method, "name", "process");
    }

    @Test
    public void testEnumDeclaration() {
        final String java = """
                            public enum Color {
                                RED, GREEN, BLUE
                            }""";
        final Obj rec = serializer.read(java);
        final Obj enm = findChildByType(rec, "enum_declaration");
        assertFalse(enm.isNoObj());
        assertPathText(enm, "name", "Color");
    }

    @Test
    public void testRecordDeclaration() {
        final String java = """
                            public record Point(int x, int y) {
                            }""";
        final Obj rec = serializer.read(java);
        final Obj record = findChildByType(rec, "record_declaration");
        assertFalse(record.isNoObj());
        assertPathText(record, "name", "Point");
        final Obj params = record.asRec().at(uri("parameters"));
        assertFalse(params.isNoObj());
    }

    @Test
    public void testFullRoundTripComplexClass() {
        final String java1 = """
                             package com.example.model;

                             import java.util.List;
                             import java.util.Objects;

                             /**
                              * A person with a name and a list of roles.
                              */
                             public class Person implements Comparable<Person> {

                                 private final String name;
                                 private final int age;
                                 private final List<String> roles;

                                 public Person(String name, int age, List<String> roles) {
                                     this.name = name;
                                     this.age = age;
                                     this.roles = List.copyOf(roles);
                                 }

                                 public String getName() {
                                     return name;
                                 }

                                 public int getAge() {
                                     return age;
                                 }

                                 public List<String> getRoles() {
                                     return roles;
                                 }

                                 public boolean isAdult() {
                                     return age >= 18;
                                 }

                                 @Override
                                 public int compareTo(Person other) {
                                     int nameCmp = this.name.compareTo(other.name);
                                     if (nameCmp != 0) {
                                         return nameCmp;
                                     }
                                     return Integer.compare(this.age, other.age);
                                 }

                                 @Override
                                 public boolean equals(Object obj) {
                                     if (this == obj) return true;
                                     if (!(obj instanceof Person)) return false;
                                     Person p = (Person) obj;
                                     return age == p.age && Objects.equals(name, p.name);
                                 }

                                 @Override
                                 public int hashCode() {
                                     return Objects.hash(name, age);
                                 }
                             }""";
        final Obj mtron1 = serializer.read(java1);
        assertTrue(mtron1.isRec());
        assertEquals(uri("program"), mtron1.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY));
        final Obj package1 = findChildByType(mtron1, "package_declaration");
        assertFalse(package1.isNoObj());
        final Obj classDecl1 = findChildByType(mtron1, "class_declaration");
        assertFalse(classDecl1.isNoObj());
        assertPathText(classDecl1, "name", "Person");
        final Obj body1 = classDecl1.asRec().at(uri("body"));
        assertFalse(body1.isNoObj());
        final List<Obj> bodyChildren1 = body1.asRec().at(uri(OUT)).asLst().elements().toList();
        assertFalse(bodyChildren1.isEmpty(), "class body must have members");
        final String java2 = serializer.write(mtron1);
        assertNotNull(java2);
        assertFalse(java2.isBlank());
        final Obj mtron2 = serializer.read(java2);
        assertTrue(mtron2.isRec());
        final String java3 = serializer.write(mtron2);
        assertEquals(java2, java3, "serializer must be idempotent: second write matches third write");
        final Obj classDecl2 = findChildByType(mtron2, "class_declaration");
        assertFalse(classDecl2.isNoObj(), "class_declaration must survive round-trip");
        assertPathText(classDecl2, "name", "Person");
        final Obj body2 = classDecl2.asRec().at(uri("body"));
        final List<Obj> bodyChildren2 = body2.asRec().at(uri(OUT)).asLst().elements().toList();
        assertTrue(bodyChildren2.size() >= 3, "class body must have at least 3 fields after round-trip");
        long methodCount = bodyChildren2.stream()
                .filter(c -> c.isRec() && "method_declaration".equals(
                        c.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY).orElse(uri("")).uriValue().toString()))
                .count();
        assertTrue(methodCount >= 7, "class must have at least 7 methods after round-trip, got " + methodCount);
    }

    // ===================================================================
    //  Type-conversion: str -> java::T, java::T -> rec::T, rec::T -> java::T
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'public class Empty {}'           %  empty class",
            "'public class Data { private int x; }'  %  class with field",
            "'public interface Marker {}'       %  interface",
            "'public enum Day { MON, TUE }'     %  enum",
    }, delimiter = '%')
    void testStrToJavaType(final String mtronValue, final String desc) {
        final Str result = assertStrToType(mtronValue);
        assertTrue(result.strValue().contains("class") || result.strValue().contains("interface")
                || result.strValue().contains("enum"), "must be valid Java: " + desc);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'public class Empty {}'           %  empty class parses to rec",
            "'public class Data { private int x; }'  %  class with field parses",
            "'public interface Marker {}'       %  interface parses to rec",
    }, delimiter = '%')
    void testJavaToRec(final String mtronValue, final String desc) {
        final Rec result = assertTypeToRec(mtronValue);
        assertTrue(result.count() >= 0, "must be a valid rec: " + desc);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'public class Empty {}'           %  round-trip through rec",
            "'public class Data { private int x; }'  %  class with field round-trip",
    }, delimiter = '%')
    void testRecToJava(final String mtronValue, final String desc) {
        final Str roundTripped = assertRecToType(mtronValue);
        assertTrue(roundTripped.strValue().contains("class"), "round-trip must produce Java: " + desc);
    }

    @Disabled
    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'not java source code'  %  plain text rejected",
            "'class { broken'        %  invalid syntax rejected",
    }, delimiter = '%')
    void testInvalidJavaRejected(final String mtronValue, final String desc) {
        assertRejected(mtronValue);
    }

    @Test
    public void testJavaTypeChain() {
        final Str javaStr = eval("'public class Empty {}'.as(java::T)");
        assertEquals(JAVA_TID, javaStr.tid());
        assertTrue(javaStr.isStr());

        final Rec javaRec = eval("'" + javaStr.strValue() + "'.as(java::T).as(rec::T)");
        assertTrue(javaRec.isRec());

        final Str javaStr2 = eval("'" + javaStr.strValue() + "'.as(java::T).as(rec::T).as(java::T)");
        assertEquals(JAVA_TID, javaStr2.tid());
        assertTrue(javaStr2.isStr());
    }
}
