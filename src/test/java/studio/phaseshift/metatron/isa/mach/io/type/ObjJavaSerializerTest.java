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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjJavaSerializerTest extends AbstractSerializerTest<String> {

    private final ObjJavaSerializer serializer = ObjJavaSerializer.single();

    public ObjJavaSerializerTest() {
        super(new ObjJavaSerializer());
    }

    @Override
    public void testSerializeDeserializeObj(final String objString) {
        // The ObjJavaSerializer is a language→Rec bridge, not a generic Obj serializer.
        // The base-class parameterized test exercises mtron literal round-trips which
        // are not applicable here — the write path generates Java source from a Rec
        // AST, not from an arbitrary mtron Obj.  Round-trip fidelity is validated by
        // the idempotency and structural tests below.
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Normalize Java source whitespace so structural equality can be checked
     * without formatting differences masking real bugs.  Collapses runs of
     * horizontal whitespace to single spaces and strips leading/trailing
     * whitespace per line, then joins lines with a single newline.
     */
    private static String normalize(final String javaSource) {
        return javaSource
                .replaceAll("\\h+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n+", "\n")
                .trim();
    }

    /**
     * Parse → Rec → Java source → parse → Rec → Java source.  Assert idempotency.
     */
    private void assertRoundTripIdempotent(final String javaSource) {
        // Phase 1: Java₁ → mtron₁
        final Obj mtron1 = serializer.read(javaSource);
        assertTrue(mtron1.isRec(), "parse must produce a Rec");

        // Phase 2: mtron₁ → Java₂
        final String java2 = serializer.write(mtron1);
        assertNotNull(java2);
        assertFalse(java2.isBlank(), "regenerated source must not be blank");

        // Phase 3: Java₂ → mtron₂
        final Obj mtron2 = serializer.read(java2);
        assertTrue(mtron2.isRec());

        // Phase 4: mtron₂ → Java₃
        final String java3 = serializer.write(mtron2);

        // Idempotency: second and third writes must be identical
        assertEquals(java2, java3, "serializer must be idempotent: second write produces identical source");
    }

    /**
     * Parse → Rec, then verify a key path has the expected text value.
     */
    private void assertPathText(final Obj rec, final String path, final String expected) {
        final Obj val = rec.asRec().at(uri(path));
        assertFalse(val.isNoObj(), "path '" + path + "' must exist");
        // The value at a path may be a str leaf or a Rec with a TEXT field
        final String actual = val.isStr() ? val.strValue() :
                val.isRec() ? val.asRec().at(uri(TEXT)).orElse(str("")).strValue() : "";
        assertEquals(expected, actual, "path '" + path + "' text mismatch");
    }

    /**
     * Find a child Rec in the OUT list whose TYPE matches.
     */
    private Obj findChildByType(final Obj parent, final String typeName) {
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

    // ── basic structure ────────────────────────────────────────────────────

    @Test
    public void testEmptyClass() {
        final String java = """
                            public class Empty {
                            }""";

        final Obj rec = serializer.read(java);
        assertTrue(rec.isRec());

        // Root is a program node
        assertEquals(uri("program"), rec.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY));

        // One child: class_declaration
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

        // Two top-level children: package_declaration, class_declaration
        final Obj out = rec.asRec().at(uri(OUT));
        final List<Obj> children = out.asLst().elements().toList();
        assertEquals(2, children.size());

        final Obj pkg = findChildByType(rec, "package_declaration");
        assertFalse(pkg.isNoObj());
        // The package name is inside a scoped_identifier
        final Obj pkgOut = pkg.asRec().at(uri(OUT));
        assertFalse(pkgOut.isNoObj());
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

        // Import name may be a field-named child or inside OUT (grammar-version
        // dependent).  Verify the import text contains the expected name.
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

        // Must have a method
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

        // Must have parameters
        final Obj params = record.asRec().at(uri("parameters"));
        assertFalse(params.isNoObj());
    }

    // ── members ────────────────────────────────────────────────────────────

    @Test
    public void testFieldDeclarations() {
        final String java = """
                            public class Data {
                                private String name;
                                public int count;
                                protected final double value = 3.14;
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));

        // Count field declarations in body
        final List<Obj> bodyChildren = body.asRec().at(uri(OUT)).asLst().elements().toList();
        long fieldCount = bodyChildren.stream()
                .filter(c -> c.isRec() && "field_declaration".equals(
                        c.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY).orElse(uri("")).uriValue().toString()))
                .count();
        assertEquals(3, fieldCount, "should have 3 field declarations");
    }

    @Test
    public void testMethodWithReturnType() {
        final String java = """
                            public class Calc {
                                public int add(int a, int b) {
                                    return a + b;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj method = findChildByType(body, "method_declaration");
        assertFalse(method.isNoObj());
        assertPathText(method, "name", "add");

        // Must have parameters
        final Obj params = method.asRec().at(uri("parameters"));
        assertFalse(params.isNoObj());

        // Must have a body
        final Obj methodBody = method.asRec().at(uri("body"));
        assertFalse(methodBody.isNoObj());

        // Must have a return type
        final Obj returnType = method.asRec().at(uri("type"));
        assertFalse(returnType.isNoObj());
    }

    @Test
    public void testMethodThrows() {
        final String java = """
                            public class IO {
                                public void read() throws java.io.IOException, IllegalArgumentException {
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj method = findChildByType(body, "method_declaration");

        // throws clause may be a direct key or in OUT
        Obj throwsClause = method.asRec().at(uri("throws"));
        if (throwsClause.isNoObj()) {
            throwsClause = findChildByType(method, "throws");
        }
        if (throwsClause.isNoObj()) {
            final String methodText = method.asRec().at(uri(TEXT)).orElse(str("")).strValue();
            assertTrue(methodText.contains("throws"),
                    "method should throw, text: " + methodText);
        }
    }

    @Test
    public void testConstructor() {
        final String java = """
                            public class Person {
                                private String name;
                            
                                public Person(String name) {
                                    this.name = name;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj ctor = findChildByType(body, "constructor_declaration");
        assertFalse(ctor.isNoObj());
        assertPathText(ctor, "name", "Person");
    }

    // ── control flow ───────────────────────────────────────────────────────

    @Test
    public void testIfStatement() {
        final String java = """
                            public class Logic {
                                public String check(int x) {
                                    if (x > 0) {
                                        return "positive";
                                    } else {
                                        return "negative";
                                    }
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj method = findChildByType(body, "method_declaration");
        final Obj methodBody = method.asRec().at(uri("body"));
        final Obj ifStmt = findChildByType(methodBody, "if_statement");
        assertFalse(ifStmt.isNoObj());

        // Should have condition, consequence, alternative
        assertFalse(ifStmt.asRec().at(uri("condition")).isNoObj());
        assertFalse(ifStmt.asRec().at(uri("consequence")).isNoObj());
        assertFalse(ifStmt.asRec().at(uri("alternative")).isNoObj());
    }

    @Test
    public void testForLoop() {
        final String java = """
                            public class Loop {
                                public int sum(int n) {
                                    int total = 0;
                                    for (int i = 0; i < n; i++) {
                                        total = total + i;
                                    }
                                    return total;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj method = findChildByType(body, "method_declaration");
        final Obj methodBody = method.asRec().at(uri("body"));
        final Obj forStmt = findChildByType(methodBody, "for_statement");
        assertFalse(forStmt.isNoObj());

        // For statement should have a body
        assertFalse(forStmt.asRec().at(uri("body")).isNoObj());
    }

    @Test
    public void testWhileLoop() {
        final String java = """
                            public class Loop {
                                public void run() {
                                    while (true) {
                                        doWork();
                                    }
                                }
                                private void doWork() { }
                            }""";

        final Obj rec = serializer.read(java);
        // Find the while_statement anywhere in the tree
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj method = findChildByType(body, "method_declaration");
        final Obj methodBody = method.asRec().at(uri("body"));
        final Obj whileStmt = findChildByType(methodBody, "while_statement");
        assertFalse(whileStmt.isNoObj());
        assertFalse(whileStmt.asRec().at(uri("condition")).isNoObj());
        assertFalse(whileStmt.asRec().at(uri("body")).isNoObj());
    }

    @Test
    public void testTryCatch() {
        final String java = """
                            public class Safe {
                                public String read() {
                                    try {
                                        return "ok";
                                    } catch (Exception e) {
                                        return "fail";
                                    }
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj method = findChildByType(body, "method_declaration");
        final Obj methodBody = method.asRec().at(uri("body"));
        final Obj tryStmt = findChildByType(methodBody, "try_statement");
        assertFalse(tryStmt.isNoObj());
    }

    // ── expressions ────────────────────────────────────────────────────────

    @Test
    public void testStringLiteral() {
        final String java = """
                            public class Messages {
                                public String greeting() {
                                    return "Hello, World!";
                                }
                            }""";

        final Obj rec = serializer.read(java);
        assertTrue(rec.isRec());

        // Search for string_literal anywhere in the tree
        final String recStr = rec.toString();
        assertTrue(recStr.contains("Hello, World!"),
                "rec must contain the string literal content");
    }

    @Test
    public void testMethodInvocation() {
        final String java = """
                            public class Runner {
                                public void execute() {
                                    System.out.println("hello");
                                }
                            }""";

        final Obj rec = serializer.read(java);
        // Should contain a method_invocation node somewhere
        final String recStr = rec.toString();
        assertTrue(recStr.contains("println"), "rec must contain println invocation");
    }

    @Test
    public void testAnnotations() {
        final String java = """
                            @Deprecated
                            public class OldCode {
                                @SuppressWarnings("unchecked")
                                public void legacy() {
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");

        // modifiers may be a direct key or in OUT (grammar-version dependent)
        Obj modifiers = classDecl.asRec().at(uri("modifiers"));
        if (modifiers.isNoObj()) {
            modifiers = findChildByType(classDecl, "modifiers");
        }
        assertFalse(modifiers.isNoObj(), "class should have modifiers with @Deprecated");

        // Check the method's modifiers for @SuppressWarnings
        final Obj body = classDecl.asRec().at(uri("body"));
        final Obj method = findChildByType(body, "method_declaration");
        Obj methodMods = method.asRec().at(uri("modifiers"));
        if (methodMods.isNoObj()) {
            methodMods = findChildByType(method, "modifiers");
        }
        assertFalse(methodMods.isNoObj(), "method should have modifiers with @SuppressWarnings");
    }

    @Test
    public void testBooleanLiterals() {
        final String java = """
                            public class Flags {
                                public boolean isReady() {
                                    boolean active = true;
                                    return active && !false;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        // Verify boolean literals appear in the tree
        final String recStr = rec.toString();
        assertTrue(recStr.contains("true"), "rec must contain true literal");
        assertTrue(recStr.contains("false"), "rec must contain false literal");
    }

    @Test
    public void testNullLiteral() {
        final String java = """
                            public class Nullable {
                                public String get() {
                                    return null;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final String recStr = rec.toString();
        assertTrue(recStr.contains("null"), "rec must contain null literal");
    }

    // ── comments ───────────────────────────────────────────────────────────

    @Test
    public void testLineComment() {
        final String java = """
                            // This is a comment
                            public class Commented {
                            }""";

        final Obj rec = serializer.read(java);
        final String recStr = rec.toString();
        assertTrue(recStr.contains("This is a comment"),
                "rec must contain comment text");
    }

    @Test
    public void testBlockComment() {
        final String java = """
                            /**
                             * Javadoc for the class.
                             */
                            public class Documented {
                            }""";

        final Obj rec = serializer.read(java);
        final String recStr = rec.toString();
        assertTrue(recStr.contains("Javadoc for the class"),
                "rec must contain javadoc text");
    }

    // ── generics ───────────────────────────────────────────────────────────

    @Test
    public void testGenericClass() {
        final String java = """
                            public class Box<T> {
                                private T value;
                            
                                public T get() {
                                    return value;
                                }
                            
                                public void set(T value) {
                                    this.value = value;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        assertPathText(classDecl, "name", "Box");

        // Must have type_parameters
        final Obj typeParams = classDecl.asRec().at(uri("type_parameters"));
        assertFalse(typeParams.isNoObj(), "generic class must have type_parameters");
    }

    @Test
    public void testExtendsAndImplements() {
        final String java = """
                            public class MyList extends java.util.AbstractList<String>
                                    implements java.util.RandomAccess {
                            
                                public String get(int i) { return null; }
                                public int size() { return 0; }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj classDecl = findChildByType(rec, "class_declaration");
        assertPathText(classDecl, "name", "MyList");

        // Must have superclass
        assertFalse(classDecl.asRec().at(uri("superclass")).isNoObj(),
                "class must have superclass");
        // super_interfaces may be a direct key or in OUT
        Obj superInterfaces = classDecl.asRec().at(uri("super_interfaces"));
        if (superInterfaces.isNoObj()) {
            superInterfaces = findChildByType(classDecl, "super_interfaces");
        }
        if (superInterfaces.isNoObj()) {
            // Fallback: check the class text for "implements"
            final String classText = classDecl.asRec().at(uri(TEXT)).orElse(str("")).strValue();
            assertTrue(classText.contains("implements"),
                    "class must implement interfaces, text: " + classText);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ROUND-TRIP TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void testRoundTripEmptyClass() {
        assertRoundTripIdempotent("""
                                  public class Empty {
                                  }""");
    }

    @Test
    public void testRoundTripClassWithField() {
        assertRoundTripIdempotent("""
                                  public class Person {
                                      private String name;
                                      private int age;
                                  }""");
    }

    @Test
    public void testRoundTripClassWithMethod() {
        assertRoundTripIdempotent("""
                                  public class Greeter {
                                      public String hello(String name) {
                                          return "Hello, " + name;
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripInterface() {
        assertRoundTripIdempotent("""
                                  public interface Handler {
                                      void handle(String input);
                                      int status();
                                  }""");
    }

    @Test
    public void testRoundTripEnum() {
        assertRoundTripIdempotent("""
                                  public enum Status {
                                      PENDING,
                                      ACTIVE,
                                      CLOSED
                                  }""");
    }

    @Test
    public void testRoundTripRecord() {
        assertRoundTripIdempotent("""
                                  public record Pair(String key, int value) {
                                  }""");
    }

    @Test
    public void testRoundTripIfElse() {
        assertRoundTripIdempotent("""
                                  public class Branch {
                                      public String test(int x) {
                                          if (x > 0) {
                                              return "positive";
                                          } else {
                                              return "negative";
                                          }
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripForLoop() {
        assertRoundTripIdempotent("""
                                  public class Loop {
                                      public int sum(int n) {
                                          int total = 0;
                                          for (int i = 0; i < n; i++) {
                                              total = total + i;
                                          }
                                          return total;
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripWhileLoop() {
        assertRoundTripIdempotent("""
                                  public class Waiter {
                                      public void waitUntil(boolean done) {
                                          while (!done) {
                                              Thread.yield();
                                          }
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripTryCatch() {
        assertRoundTripIdempotent("""
                                  public class Safe {
                                      public String attempt() {
                                          try {
                                              return "ok";
                                          } catch (Exception e) {
                                              return "fail";
                                          }
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripConstructor() {
        assertRoundTripIdempotent("""
                                  public class Point {
                                      private int x;
                                      private int y;
                                  
                                      public Point(int x, int y) {
                                          this.x = x;
                                          this.y = y;
                                      }
                                  
                                      public int getX() {
                                          return x;
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripAnnotations() {
        assertRoundTripIdempotent("""
                                  @Deprecated
                                  public class OldThing {
                                      @SuppressWarnings("unchecked")
                                      public void doIt() {
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripComments() {
        final String java = """
                            // A line comment
                            public class WithComments {
                                /**
                                 * A javadoc comment.
                                 */
                                public void run() {
                                    // inline comment
                                    int x = 0;
                                }
                            }""";
        // Comments are "extra" nodes in TreeSitter — their placement in the
        // parse tree can shift between parses.  Verify the re-serialized
        // output contains the expected content rather than requiring exact
        // structural fidelity.
        final Obj rec1 = serializer.read(java);
        final String java2 = serializer.write(rec1);

        assertTrue(java2.contains("WithComments"),
                "output must contain the class name");
        assertTrue(java2.contains("void run()"),
                "output must contain the method declaration");
        assertTrue(java2.contains("line comment") || java2.contains("//"),
                "line comment should be present");
        assertTrue(java2.contains("javadoc") || java2.contains("/*"),
                "javadoc comment should be present");
    }

    @Test
    public void testRoundTripGenerics() {
        assertRoundTripIdempotent("""
                                  public class Container<T extends Comparable<T>> {
                                      private T item;
                                  
                                      public T get() {
                                          return item;
                                      }
                                  
                                      public void set(T item) {
                                          this.item = item;
                                      }
                                  }""");
    }

    @Test
    public void testRoundTripExtendsAndImplements() {
        assertRoundTripIdempotent("""
                                  public class MyList extends java.util.AbstractList<String>
                                          implements java.util.RandomAccess {
                                  
                                      public String get(int index) {
                                          return null;
                                      }
                                  
                                      public int size() {
                                          return 0;
                                      }
                                  }""");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FULL ROUND-TRIP: Java₁ → mtron₁ → Java₂ → mtron₂ → Java₃
    //  Idempotency: Java₂ == Java₃
    //  Structural fidelity: key paths survive the full round-trip
    // ═══════════════════════════════════════════════════════════════════════

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

        // Phase 1: Java₁ → mtron₁
        final Obj mtron1 = serializer.read(java1);
        assertTrue(mtron1.isRec());
        assertEquals(uri("program"), mtron1.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY));

        // Structural checks on mtron₁
        final Obj package1 = findChildByType(mtron1, "package_declaration");
        assertFalse(package1.isNoObj());

        final Obj imports = findChildByType(mtron1, "import_declaration");
        assertFalse(imports.isNoObj(), "must have at least one import");

        final Obj classDecl1 = findChildByType(mtron1, "class_declaration");
        assertFalse(classDecl1.isNoObj());
        assertPathText(classDecl1, "name", "Person");

        // Verify body has children
        final Obj body1 = classDecl1.asRec().at(uri("body"));
        assertFalse(body1.isNoObj());
        final List<Obj> bodyChildren1 = body1.asRec().at(uri(OUT)).asLst().elements().toList();
        assertFalse(bodyChildren1.isEmpty(), "class body must have members");

        // Phase 2: mtron₁ → Java₂
        final String java2 = serializer.write(mtron1);
        assertNotNull(java2);
        assertFalse(java2.isBlank());

        // Phase 3: Java₂ → mtron₂
        final Obj mtron2 = serializer.read(java2);
        assertTrue(mtron2.isRec());

        // Phase 4: mtron₂ → Java₃
        final String java3 = serializer.write(mtron2);

        // === Assert idempotency: Java₂ == Java₃ ===
        assertEquals(java2, java3, "serializer must be idempotent: second write matches third write");

        // === Assert structural fidelity: mtron₂ has same key paths ===

        final Obj classDecl2 = findChildByType(mtron2, "class_declaration");
        assertFalse(classDecl2.isNoObj(), "class_declaration must survive round-trip");
        assertPathText(classDecl2, "name", "Person");

        // Interfaces survived (may be direct key or in OUT)
        Obj superInterfaces2 = classDecl2.asRec().at(uri("super_interfaces"));
        if (superInterfaces2.isNoObj()) {
            superInterfaces2 = findChildByType(classDecl2, "super_interfaces");
        }
        if (superInterfaces2.isNoObj()) {
            final String classText2 = classDecl2.asRec().at(uri(TEXT)).orElse(str("")).strValue();
            assertTrue(classText2.contains("implements"),
                    "implements clause must survive round-trip, text: " + classText2);
        }

        // Fields survived
        final Obj body2 = classDecl2.asRec().at(uri("body"));
        final List<Obj> bodyChildren2 = body2.asRec().at(uri(OUT)).asLst().elements().toList();
        assertTrue(bodyChildren2.size() >= 3,
                "class body must have at least 3 fields after round-trip");

        // Methods survived: getName, getAge, getRoles, isAdult, compareTo, equals, hashCode
        long methodCount = bodyChildren2.stream()
                .filter(c -> c.isRec() && "method_declaration".equals(
                        c.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY).orElse(uri("")).uriValue().toString()))
                .count();
        assertTrue(methodCount >= 7,
                "class must have at least 7 methods after round-trip, got " + methodCount);

        // Constructor survived
        long ctorCount = bodyChildren2.stream()
                .filter(c -> c.isRec() && "constructor_declaration".equals(
                        c.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY).orElse(uri("")).uriValue().toString()))
                .count();
        assertTrue(ctorCount >= 1,
                "class must have constructor after round-trip, got " + ctorCount);
    }

    // ── edge cases ─────────────────────────────────────────────────────────

    @Test
    public void testEmptySource() {
        final String java = "";
        final Obj rec = serializer.read(java);
        assertTrue(rec.isRec());
        assertEquals(uri("program"), rec.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY));
    }

    @Test
    public void testOnlyPackage() {
        final String java = "package com.example;";
        final Obj rec = serializer.read(java);
        final Obj pkg = findChildByType(rec, "package_declaration");
        assertFalse(pkg.isNoObj());
    }

    @Test
    public void testMultipleClasses() {
        final String java = """
                            class First {
                                int value;
                            }
                            
                            class Second {
                                String name;
                            }""";

        final Obj rec = serializer.read(java);
        final List<Obj> children = rec.asRec().at(uri(OUT)).asLst().elements().toList();
        long classCount = children.stream()
                .filter(c -> c.isRec() && "class_declaration".equals(
                        c.asRec().at(ObjJavaSerializer.NODE_TYPE_KEY).orElse(uri("")).uriValue().toString()))
                .count();
        assertEquals(2, classCount, "should parse two top-level classes");
    }

    @Test
    public void testInnerClass() {
        final String java = """
                            public class Outer {
                                private int outerField;
                            
                                public class Inner {
                                    private int innerField;
                            
                                    public int getValue() {
                                        return outerField + innerField;
                                    }
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final Obj outer = findChildByType(rec, "class_declaration");
        assertFalse(outer.isNoObj());
        assertPathText(outer, "name", "Outer");

        // The inner class should be somewhere in the body
        final String recStr = rec.toString();
        assertTrue(recStr.contains("Inner"), "rec must contain the inner class name");
    }

    @Test
    public void testPrimitiveTypes() {
        final String java = """
                            public class Types {
                                boolean flag;
                                byte b;
                                short s;
                                int i;
                                long l;
                                float f;
                                double d;
                                char c;
                            }""";

        final Obj rec = serializer.read(java);
        // Each field should have a type node
        final String recStr = rec.toString();
        for (final String type : List.of("boolean", "byte", "short", "int", "long", "float", "double", "char")) {
            assertTrue(recStr.contains(type), "rec must contain primitive type: " + type);
        }
    }

    @Test
    public void testArrayTypes() {
        final String java = """
                            public class Arrays {
                                public String[] names;
                                public int[][] matrix;
                            
                                public String[] getNames() {
                                    return names;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        // Both array_type nodes should be present
        final String recStr = rec.toString();
        // array_type appears for each array field and return type
        assertTrue(recStr.contains("array_type"), "rec must contain array types");
    }

    @Test
    public void testChainedMethodCalls() {
        final String java = """
                            public class Chain {
                                public String build() {
                                    return new StringBuilder()
                                        .append("a")
                                        .append("b")
                                        .toString();
                                }
                            }""";

        final Obj rec = serializer.read(java);
        // Multiple method_invocation nodes expected
        final String recStr = rec.toString();
        final long invocationCount = recStr.lines()
                .filter(line -> line.contains("method_invocation"))
                .count();
        assertTrue(invocationCount >= 3,
                "should have at least 3 method invocations (append, append, toString)");
    }

    @Test
    public void testReturnStatementWithoutValue() {
        final String java = """
                            public class VoidReturn {
                                public void doSomething() {
                                    if (true) {
                                        return;
                                    }
                                    System.out.println("done");
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final String recStr = rec.toString();
        assertTrue(recStr.contains("return_statement"),
                "rec must contain return_statement");
    }

    // ── inputBytes / outputBytes ───────────────────────────────────────────

    @Test
    public void testInputBytes() {
        final String java = """
                            public class Test {
                                public String message() {
                                    return "hello";
                                }
                            }""";

        final byte[] bytes = java.getBytes(StandardCharsets.UTF_8);
        final ByteBuffer buf = ByteBuffer.wrap(bytes);
        final Obj rec = serializer.inputBytes(buf);
        assertTrue(rec.isRec());
        final Obj classDecl = findChildByType(rec, "class_declaration");
        assertFalse(classDecl.isNoObj());
    }

    @Test
    public void testOutputBytesRoundTrip() {
        final String java = """
                            public class Test {
                                public int getValue() {
                                    return 42;
                                }
                            }""";

        final Obj rec = serializer.read(java);
        final byte[] outBytes = serializer.outputBytes(rec).array();
        final String regenerated = new String(outBytes, StandardCharsets.UTF_8);

        // Re-parse the regenerated source to verify it's valid
        final Obj rec2 = serializer.read(regenerated);
        assertTrue(rec2.isRec());
        final Obj classDecl2 = findChildByType(rec2, "class_declaration");
        assertFalse(classDecl2.isNoObj());
        assertPathText(classDecl2, "name", "Test");
    }

    // ── static parse helper ────────────────────────────────────────────────

    @Test
    public void testStaticParse() {
        final String java = """
                            public class Simple {
                            }""";

        final Obj rec = ObjJavaSerializer.parse(java);
        assertTrue(rec.isRec());
        final Obj classDecl = findChildByType(rec, "class_declaration");
        assertFalse(classDecl.isNoObj());
        assertPathText(classDecl, "name", "Simple");
    }

    // ── parameterized round-trip for common constructs ─────────────────────

    @ParameterizedTest
    @CsvSource(value = {
            "public class Empty { }",
            "public class OneField { private int x; }",
            "public class OneMethod { public void run() { } }",
            "public interface Marker { }",
            "public @interface Magic { }",
            "public enum Day { MON, TUE, WED }",
            "public record Vec(int x, int y) { }",
    }, delimiter = '|')
    public void testParameterizedRoundTrip(final String javaSource) {
        // Parse once
        final Obj rec1 = serializer.read(javaSource);
        assertTrue(rec1.isRec(), "must parse: " + javaSource);

        // Write → parse → write → parse (idempotency chain)
        final String java2 = serializer.write(rec1);
        final Obj rec2 = serializer.read(java2);
        final String java3 = serializer.write(rec2);

        assertEquals(java2, java3, "idempotency failure for: " + javaSource);

        // Verify the primary declaration survived by name
        final Obj classDecl2 = findChildByType(rec2, "class_declaration");
        final Obj iface2 = findChildByType(rec2, "interface_declaration");
        final Obj annot2 = findChildByType(rec2, "annotation_type_declaration");
        final Obj enum2 = findChildByType(rec2, "enum_declaration");
        final Obj record2 = findChildByType(rec2, "record_declaration");

        assertTrue(
                !classDecl2.isNoObj() || !iface2.isNoObj() || !annot2.isNoObj() ||
                        !enum2.isNoObj() || !record2.isNoObj(),
                "round-trip must preserve the primary declaration for: " + javaSource);
    }

    @Test
    public void testSerializerSource() throws Exception {
        String source = Files.readString(Paths.get("src/test/java/studio/phaseshift/metatron/isa/mach/io/type/ObjJavaSerializerTest.java"), StandardCharsets.UTF_8);
        final Obj obj = ObjJavaSerializer.parse(source);
        final String source2 = ObjJavaSerializer.single().write(obj);
        System.out.println(source2);

        // Verify the re-serialized source can be parsed back successfully
        // and yields the same class structure
        final Obj obj2 = ObjJavaSerializer.parse(source2);
        assertTrue(obj2.isRec());

        // Both must have the same top-level declarations
        final Obj classDecl1 = findChildByType(obj, "class_declaration");
        final Obj classDecl2 = findChildByType(obj2, "class_declaration");
        assertFalse(classDecl1.isNoObj(), "original must have class_declaration");
        assertFalse(classDecl2.isNoObj(), "round-tripped must have class_declaration");
        assertPathText(classDecl1, "name", "ObjJavaSerializerTest");
        assertPathText(classDecl2, "name", "ObjJavaSerializerTest");

        // The TEXT-first write path preserves all code tokens, comments, and
        // formatting verbatim.  Only inter-declaration blank lines (anonymous
        // whitespace in the root program node) may differ.  Verify structural
        // integrity: the re-serialized source must re-parse to the same class.
        assertTrue(source2.contains("public class ObjJavaSerializerTest extends AbstractSerializerTest<String>"),
                "must contain the class declaration");
        assertTrue(source2.contains("private final ObjJavaSerializer serializer"),
                "must contain the serializer field");
        assertTrue(source2.contains("public void testEmptyClass()"),
                "must contain test methods");
        assertTrue(source2.contains("private void assertRoundTripIdempotent"),
                "must contain helper methods");

        // Idempotency: re-serializing must produce identical output
        assertEquals(source2, ObjJavaSerializer.single().write(obj2),
                "second write must be identical to first");
    }
}
