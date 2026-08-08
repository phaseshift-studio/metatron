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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Abstract base class for testing {@link ObjSerializer} implementations.
 * <p>
 * This class provides a comprehensive test suite for verifying that serializers can correctly
 * serialize and deserialize various Metatron {@link Obj} types. Concrete subclasses should
 * provide a specific serializer implementation to be tested.
 * </p>
 * <p>
 * The test suite covers a wide range of object types including:
 * <ul>
 *   <li>Primitive types (integers, reals, booleans)</li>
 *   <li>Strings (single-line and multi-line)</li>
 *   <li>URIs</li>
 *   <li>Collections (lists and sets)</li>
 *   <li>Records (key-value mappings)</li>
 *   <li>Nested and complex structures</li>
 *   <li>Instruction chains</li>
 * </ul>
 * </p>
 *
 * @param <T> the buffer type used by the serializer (e.g., byte array, ByteBuffer, etc.)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractSerializerTest<T> extends AbstractMetatronTest {

    /**
     * The serializer instance being tested.
     */
    protected final ObjSerializer<T> serializer;

    /**
     * The type VID for the str-refinement being tested (e.g., HTML_TID, JSON_TID).
     * Set by subclasses that test string-refinement types via {@code .as(type::T)} chains.
     */
    protected fURI typeVid;

    /**
     * The mtron type name used in {@code .as(name::T)} expressions (e.g., "html", "json").
     */
    protected String typeName;

    /**
     * Constructs a new test instance with the specified serializer and type metadata.
     *
     * @param serializer the {@link ObjSerializer} implementation to test
     * @param typeVid    the type VID (e.g., {@code HTML_TID}) for str-refinement tests;
     *                   may be {@code null} for serializers that don't test type chains
     * @param typeName   the mtron type name (e.g., "html") for {@code .as(name::T)} chains;
     *                   may be {@code null}
     */
    public AbstractSerializerTest(final ObjSerializer<T> serializer, final fURI typeVid, final String typeName) {
        this.serializer = serializer;
        this.typeVid = typeVid;
        this.typeName = typeName;
    }

    /**
     * Determines whether a test failure should be ignored for a specific object string.
     * <p>
     * Subclasses can override this method to skip assertion failures for known problematic
     * cases while still logging the discrepancy. This is useful during development when
     * certain edge cases are not yet fully supported.
     * </p>
     *
     * @param toSerialize the string representation of the object being tested
     * @return {@code true} if test failures should be ignored for this object, {@code false} otherwise
     */
    public boolean ignoreFail(final String toSerialize) {
        return false;
    }

    /**
     * Verifies that a roundtrip is structurally lossless.
     * The result must be equal to the original, and their types (including TID/VID)
     * must be identical.
     */
    protected void assertLosslessRoundtrip(Obj original, Obj result) {
        assertEquals(original, result, "Value mismatch in roundtrip");
        assertEquals(original.type(), result.type(), "Type/TID/VID mismatch in roundtrip");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Type-conversion plumbing (str-refinement types)
    // ═══════════════════════════════════════════════════════════════════

    @BeforeAll
    public static void importWebTypes() {
        InstSet.importInstSet(f("/m/web"));
    }

    /**
     * Parse and apply a mtron expression, returning the result.
     * The return type is inferred from the assignment context (e.g., {@code Str s = eval("...")}).
     */
    @SuppressWarnings("unchecked")
    protected <O extends Obj> O eval(final String mtronExpr) {
        return (O) ObjmtronSerializer.singleNoClip().parse(mtronExpr).apply();
    }

    /**
     * Assert that a mtron value expression converts to the str-refinement type
     * (e.g., {@code '"<html>..."'.as(html::T)}).  Returns the tagged {@link Str}.
     */
    protected Str assertStrToType(final String mtronValueExpr) {
        final String expr = mtronValueExpr + ".as(" + typeName + "::T)";
        final Obj result = eval(expr);
        assertEquals(typeVid, result.tid(), "str→" + typeName + "::T tag: " + mtronValueExpr);
        assertTrue(result.isStr(), "str→" + typeName + "::T must produce a string: " + mtronValueExpr);
        return result.asStr();
    }

    /**
     * Assert that a mtron value expression parses through the str-refinement type
     * into a {@link Rec} (e.g., {@code '"<html>..."'.as(html::T).as(rec::T)}).
     */
    protected Rec assertTypeToRec(final String mtronValueExpr) {
        final String expr = mtronValueExpr + ".as(" + typeName + "::T).as(rec::T)";
        final Obj result = eval(expr);
        assertTrue(result.isRec(), typeName + "::T→rec::T must produce a Rec: " + mtronValueExpr);
        return result.asRec();
    }

    /**
     * Assert that a mtron value expression round-trips through the str-refinement
     * type, rec parse, and back (e.g., {@code '"<html>..."'.as(html::T).as(rec::T).as(html::T)}).
     * Returns the final tagged {@link Str}.
     */
    protected Str assertRecToType(final String mtronValueExpr) {
        final String expr = mtronValueExpr + ".as(" + typeName + "::T).as(rec::T).as(" + typeName + "::T)";
        final Obj result = eval(expr);
        assertEquals(typeVid, result.tid(), "rec::T→" + typeName + "::T tag: " + mtronValueExpr);
        assertTrue(result.isStr(), "rec::T→" + typeName + "::T must produce a string: " + mtronValueExpr);
        return result.asStr();
    }

    /**
     * Assert that a mtron expression throws (predicate rejects invalid input).
     * Uses {@code checkCodeParseApply(LOG, expr, "<ERROR>")}.
     */
    protected void assertRejected(final String mtronValueExpr) {
        final String expr = mtronValueExpr + ".as(" + typeName + "::T)";
        checkCodeParseApply(LOG, expr, "<ERROR>");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Generic Obj round-trip via serializer.write() / serializer.read()
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @CsvSource(value = {
            //obj
            "noobj",
            "int{0}::3",
            "real::2.12",
            "true",
            "false",
            "bool::true",
            "bool::false",
            "1",
            "0",
            "-100",
            "12.25",
            "-12.35",
            "\"this is a string\"",
            "\"\"\"this is a multilinestring\"\"\"",
            "<http://test.uri.com>",
            "<http://test.uri.com?a=b&c=d>",
            "/mtron/test",
            "uri{24}::a/b/c",
            "[<a>,<b>,<c>,<d>]",
            "rec::[a=>b,c=>d]",
            "[<a>=>b,c=><d>]",
            "[a=>b,c=>[b=>d]]",
            "[a=>b,c=>rec::[b=>d]]",
            "[a=>uri::b,c=>rec::[b=>d]]",
            "[a=>uri::b,uri::c=>rec::[b=>d]]",
            //"[a=>uri::b,str::'c'=>rec::[b=>d],uri::d=>rec::[b=>str::'d']]",
            //"addTwentyThree(){?}",
            "plus(2).mult(7)",
            "start(1).plus(2).mult(7)",
            "[=>]",
            "[,]",
            "< >",
            "[a,[b,12,'abc'],[a=>b,c=>[c=>d]]]",
            "rec{0}::[a=>b,c=>[c=>d]]",
            "rec::[a=>b,c=>[c=>d]]",
            "{1,2,3,4,5}",
            "{true, false, 1,0, -100, 12.55, -12.35}",
            "{[1,2],[3,4],[5,6]}",
            "{true, false, {1,0}, {-100, 12.35, -12.35}}",
            "{,}"
    }, delimiter = '|')
    public void testSerializeDeserializeObj(final String objString) {
        final Obj obj = ObjmtronSerializer.parse(objString).apply();
        Obj obj2 = null;
        try {
            final T buffer = this.serializer.write(obj);
            obj2 = serializer.read(buffer);
        } finally {
            LOG.debug("testing {{b}}%s{{/b}} serialized to %s => %s", objString, obj, obj2);
            if (this.ignoreFail(objString)) {
                final boolean areEqual = Objects.equals(obj, obj2);
                if (areEqual)
                    LOG.warn("no need to ignore test %s <=> %s", objString, obj);
                else
                    LOG.debug("ignoring fail for %s <=> %s", objString, obj);
            } else {
                assertEquals(obj, obj2);
                assertEquals(obj.type(), obj2.type());
            }

        }

    }
}