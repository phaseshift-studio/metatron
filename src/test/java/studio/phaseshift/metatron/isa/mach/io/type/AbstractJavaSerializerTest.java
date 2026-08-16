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
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.ide.parser.ObjJavaIDESerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared test surface for Java source serializers (fine-grained CST via
 * {@link ObjJavaSerializer} and coarse-schema via {@link ObjJavaIDESerializer}).
 *
 * <p>Both are language→Rec bridges (not generic Obj serializers) and both must
 * round-trip common Java constructs idempotently.  The round-trip tests below
 * run against whichever serializer the subclass supplies.</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractJavaSerializerTest extends AbstractSerializerTest<String> {

    protected AbstractJavaSerializerTest(final ObjSerializer<String> serializer,
                                         final fURI typeVid, final String typeName) {
        super(serializer, typeVid, typeName);
    }

    @Override
    public void testSerializeDeserializeObj(final String objString) {
        // Java serializers are language→Rec bridges, not generic Obj serializers.
    }

    protected static String normalize(final String javaSource) {
        return javaSource
                .replaceAll("\\h+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n+", "\n")
                .trim();
    }

    protected void assertRoundTripIdempotent(final String javaSource) {
        final Obj mtron1 = serializer.read(javaSource);
        assertTrue(mtron1.isRec(), "parse must produce a Rec");
        final String java2 = serializer.write(mtron1);
        assertNotNull(java2);
        assertFalse(java2.isBlank(), "regenerated source must not be blank");
        final Obj mtron2 = serializer.read(java2);
        assertTrue(mtron2.isRec());
        final String java3 = serializer.write(mtron2);
        assertEquals(java2, java3, "serializer must be idempotent: second write produces identical source");
    }

    // ===================================================================
    //  Round-trip tests (shared by all Java serializers)
    // ===================================================================

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
}
