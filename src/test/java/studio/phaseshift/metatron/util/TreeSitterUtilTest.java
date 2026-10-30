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

package studio.phaseshift.metatron.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.dev.ide.parser.ObjJavaIDESerializer;
import studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.web.type.ObjJavaSerializer;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The UNAVAILABLE path of the tree-sitter probe. A host whose native library cannot load must lose java::T, not
 * the VM: the serializer class must still initialize, {@code MIME} must still answer, and using the PARSER must
 * fail as an {@link MTronException} (which the engine turns into {@code fail::T}) rather than as an {@link Error}.
 * <p>
 * {@code -Dmetatron.treesitter=off} drives that path deterministically — no broken host, no 79 MB library, and the
 * same code the real failure takes.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
class TreeSitterUtilTest extends AbstractMetatronTest {

    @BeforeAll
    static void disableTreeSitter() {
        System.setProperty(TreeSitterUtil.ENABLED_PROPERTY, "off");
        TreeSitterUtil.resetForTest();
    }

    @AfterAll
    static void restoreTreeSitter() {
        System.clearProperty(TreeSitterUtil.ENABLED_PROPERTY);
        TreeSitterUtil.resetForTest();
    }

    @Test
    void testDisabledIsAFactNotAThrow() {
        assertFalse(TreeSitterUtil.available());
        assertTrue(TreeSitterUtil.unavailability().contains(TreeSitterUtil.ENABLED_PROPERTY),
                "the reason must name the switch that caused it: " + TreeSitterUtil.unavailability());
    }

    @Test
    void testSerializerClassesStillInitialize() {
        // the exact regression: this used to throw NoClassDefFoundError, poisoning the class for the whole JVM
        assertNotNull(ObjJavaSerializer.single());
        assertNotNull(ObjJavaIDESerializer.single());
        assertInstanceOf(ObjJavaSerializer.class, MIME.MIMEType.TEXT_JAVA.serializer());
    }

    @Test
    void testUseFailsAsMTronException() {
        final MTronException java = assertThrows(MTronException.class,
                () -> ObjJavaSerializer.single().read("class Metronome { int ticks = 60; }"));
        final String javaMsg = Graphitty.strip(java.getMessage());
        // toShortString() of the type: "<vid>::T is unavailable: <why>"
        assertTrue(javaMsg.contains("::T is unavailable"), javaMsg);
        assertTrue(javaMsg.contains(TreeSitterUtil.ENABLED_PROPERTY), javaMsg);

        final MTronException ide = assertThrows(MTronException.class,
                () -> ObjJavaIDESerializer.parse("class Metronome { int ticks = 60; }"));
        final String ideMsg = Graphitty.strip(ide.getMessage());
        assertTrue(ideMsg.contains("::T is unavailable"), ideMsg);
        assertTrue(ideMsg.contains(TreeSitterUtil.ENABLED_PROPERTY), ideMsg);
    }

    @Test
    void testRepeatedUseKeepsTheSameReason() {
        // the failure is cached: a poisoned native library never recovers, so the reason must not drift
        final String first = TreeSitterUtil.unavailability();
        assertThrows(MTronException.class, () -> ObjJavaSerializer.single().read("class Metronome { }"));
        assertEquals(first, TreeSitterUtil.unavailability());
    }
}
